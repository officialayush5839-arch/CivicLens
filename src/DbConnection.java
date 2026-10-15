import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * Multi-database connection manager and schema initializer for CivicLens.
 *
 * Supports production MySQL 8+ and zero-setup embedded SQLite with seamless
 * automatic fallback when MySQL is unreachable.
 * Credentials and database configurations are externalized via AppConfig (.env / config.properties).
 */
public class DbConnection {

    private static volatile String activeDatabaseType = null;
    private static volatile boolean schemaInitialized = false;
    private static final Object SCHEMA_LOCK = new Object();

    static {
        // Register drivers
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
        }
    }

    /**
     * Obtains a Connection based on configuration and active fallback status.
     */
    public static Connection getConnection() throws SQLException {
        // Fast path: if schema is already fully initialized and type is resolved, acquire lock-free
        if (schemaInitialized && activeDatabaseType != null) {
            if ("sqlite".equalsIgnoreCase(activeDatabaseType)) {
                return getSqliteConnection();
            } else {
                return getMysqlConnection();
            }
        }

        // Slow path: synchronize one-time database type resolution and schema initialization
        synchronized (SCHEMA_LOCK) {
            // Double-check under lock
            if (schemaInitialized && activeDatabaseType != null) {
                if ("sqlite".equalsIgnoreCase(activeDatabaseType)) {
                    return getSqliteConnection();
                } else {
                    return getMysqlConnection();
                }
            }

            String configuredType = AppConfig.getDatabaseType();

            if ("mysql".equalsIgnoreCase(configuredType)) {
                try {
                    Connection mysqlConn = getMysqlConnection();
                    ensureSchema(mysqlConn);
                    activeDatabaseType = "mysql";
                    return mysqlConn;
                } catch (SQLException ex) {
                    if (AppConfig.isAutoFallbackEnabled()) {
                        System.out.println("[DbConnection] MySQL server unavailable (" + ex.getMessage() + ").");
                        System.out.println("[DbConnection] Automatically falling back to embedded SQLite database (" + AppConfig.getSqliteDbPath() + ").");
                        AppConfig.setOverride("db.type", "sqlite");
                        Connection sqliteConn = getSqliteConnection();
                        ensureSchema(sqliteConn);
                        activeDatabaseType = "sqlite";
                        return sqliteConn;
                    } else {
                        throw ex;
                    }
                }
            } else {
                Connection sqliteConn = getSqliteConnection();
                ensureSchema(sqliteConn);
                activeDatabaseType = "sqlite";
                return sqliteConn;
            }
        }
    }

    private static Connection getMysqlConnection() throws SQLException {
        String url = AppConfig.getDbUrl();
        String user = AppConfig.getDbUser();
        String password = AppConfig.getDbPassword();
        DriverManager.setLoginTimeout(3);
        return DriverManager.getConnection(url, user, password);
    }

    private static Connection getSqliteConnection() throws SQLException {
        String dbPath = AppConfig.getSqliteDbPath();
        String url = "jdbc:sqlite:" + dbPath;
        Connection conn = DriverManager.getConnection(url);

        // Configure foreign keys, WAL mode, and busy timeout for high-concurrency safety
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
        }
        return conn;
    }

    private static void ensureSchema(Connection conn) throws SQLException {
        if (schemaInitialized) {
            return;
        }
        synchronized (SCHEMA_LOCK) {
            if (schemaInitialized) {
                return;
            }
            boolean allCanariesExist = true;
            String[] canaryTables = {"users", "sla_policies", "complaints", "incident_groups"};
            for (String table : canaryTables) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1 FROM " + table + " LIMIT 1")) {
                    // table exists and query succeeded
                } catch (SQLException missing) {
                    allCanariesExist = false;
                    break;
                }
            }

            if (!allCanariesExist) {
                initializeSchema(conn);
            }
            schemaInitialized = true;
        }
    }

    /**
     * Initializes database schema using the authoritative schema.sql file.
     */
    public static void initializeSchema() throws SQLException {
        synchronized (SCHEMA_LOCK) {
            try (Connection conn = getConnection()) {
                initializeSchema(conn);
            }
        }
    }

    /**
     * Initializes database schema on the given active connection.
     */
    public static void initializeSchema(Connection conn) throws SQLException {
        synchronized (SCHEMA_LOCK) {
            String schemaSql = loadSchemaSql();
            if (schemaSql == null || schemaSql.trim().isEmpty()) {
                throw new SQLException("Could not locate or read schema.sql");
            }

            boolean isSqlite = isConnectionSqlite(conn);

            // Split schema into individual SQL statements by semicolon
            List<String> statements = parseSqlStatements(schemaSql);

            boolean initialAutoCommit = conn.getAutoCommit();
            try {
                if (isSqlite) {
                    conn.setAutoCommit(false);
                }

                try (Statement stmt = conn.createStatement()) {
                    for (String rawSql : statements) {
                        String sql = rawSql.trim();
                        if (sql.isEmpty()) {
                            continue;
                        }

                        if (isSqlite) {
                            sql = adaptSqlForSqlite(sql);
                        }

                        try {
                            stmt.execute(sql);
                        } catch (SQLException ex) {
                            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
                            // Tolerate duplicate index or unique seed constraint violations during idempotent re-runs
                            if (msg.contains("duplicate") ||
                                msg.contains("already exists") ||
                                msg.contains("unique constraint")) {
                                // Suppress benign idempotent failure
                                continue;
                            }
                            // If index creation fails because table already has index, suppress
                            if (sql.toUpperCase().startsWith("CREATE INDEX")) {
                                continue;
                            }
                            System.err.println("[DbConnection] SQL Warning executing: " + sql.substring(0, Math.min(60, sql.length())) + "... -> " + ex.getMessage());
                            // Rethrow if table creation itself fails
                            if (sql.toUpperCase().startsWith("CREATE TABLE")) {
                                throw ex;
                            }
                        }
                    }
                }

                if (isSqlite) {
                    conn.commit();
                }
                schemaInitialized = true;
            } catch (SQLException ex) {
                if (isSqlite) {
                    try { conn.rollback(); } catch (SQLException ignored) {}
                }
                throw ex;
            } finally {
                try { conn.setAutoCommit(initialAutoCommit); } catch (SQLException ignored) {}
            }
        }
    }

    private static String adaptSqlForSqlite(String sql) {
        // SQLite uses AUTOINCREMENT instead of AUTO_INCREMENT
        String adapted = sql.replaceAll("(?i)\\bAUTO_INCREMENT\\b", "AUTOINCREMENT");
        // SQLite does not support ON UPDATE CURRENT_TIMESTAMP inline
        adapted = adapted.replaceAll("(?i)\\bON\\s+UPDATE\\s+CURRENT_TIMESTAMP\\b", "");
        return adapted;
    }

    private static boolean isConnectionSqlite(Connection conn) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            return meta.getDatabaseProductName().toLowerCase().contains("sqlite");
        } catch (SQLException ex) {
            return "sqlite".equalsIgnoreCase(activeDatabaseType) || "sqlite".equalsIgnoreCase(AppConfig.getDatabaseType());
        }
    }

    private static List<String> parseSqlStatements(String script) {
        List<String> list = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new StringReader(script))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("--") || trimmed.startsWith("//")) {
                    continue; // Skip comments
                }
                current.append(line).append("\n");
                if (trimmed.endsWith(";")) {
                    String stmt = current.toString().trim();
                    // Remove trailing semicolon
                    if (stmt.endsWith(";")) {
                        stmt = stmt.substring(0, stmt.length() - 1).trim();
                    }
                    if (!stmt.isEmpty()) {
                        list.add(stmt);
                    }
                    current.setLength(0);
                }
            }
        } catch (IOException ignored) {
        }

        if (current.length() > 0) {
            String stmt = current.toString().trim();
            if (stmt.endsWith(";")) {
                stmt = stmt.substring(0, stmt.length() - 1).trim();
            }
            if (!stmt.isEmpty()) {
                list.add(stmt);
            }
        }
        return list;
    }

    private static String loadSchemaSql() {
        // 1. Check local directory
        Path localPath = Paths.get("schema.sql");
        if (Files.exists(localPath)) {
            try {
                return Files.readString(localPath, StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }

        // 2. Check parent directory
        Path parentPath = Paths.get("..", "schema.sql");
        if (Files.exists(parentPath)) {
            try {
                return Files.readString(parentPath, StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }

        // 3. Check classpath
        try (InputStream in = DbConnection.class.getResourceAsStream("/schema.sql")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {}

        return null;
    }

    public static String getActiveDatabaseType() {
        return activeDatabaseType != null ? activeDatabaseType : AppConfig.getDatabaseType();
    }

    public static void resetActiveDatabaseType() {
        synchronized (SCHEMA_LOCK) {
            activeDatabaseType = null;
            schemaInitialized = false;
        }
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException ex) {
            return false;
        }
    }

    public static void closeQuietly(AutoCloseable... resources) {
        for (AutoCloseable res : resources) {
            if (res != null) {
                try {
                    res.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("=== CivicLens Database Connection Test ===");
        try (Connection connection = getConnection()) {
            System.out.println("Database connected successfully!");
            System.out.println("Active Database Type: " + getActiveDatabaseType());

            // Verify tables
            System.out.println("\nVerifying database tables:");
            String[] expectedTables = {
                "users", "complaints", "priority_overrides", "incident_groups",
                "incident_members", "sla_policies", "sla_deadlines",
                "resolution_records", "citizen_feedback", "complaint_audit_log",
                "internal_notes", "complaint_appeals"
            };

            int verifiedCount = 0;
            for (String tbl : expectedTables) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tbl)) {
                    if (rs.next()) {
                        System.out.println("  [OK] Table '" + tbl + "' exists (rows: " + rs.getInt(1) + ")");
                        verifiedCount++;
                    }
                } catch (SQLException ex) {
                    System.out.println("  [FAIL] Table '" + tbl + "' missing or query failed: " + ex.getMessage());
                }
            }

            System.out.println("\nTable verification result: " + verifiedCount + " of " + expectedTables.length + " tables verified.");

            // Verify seed users
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT user_id, full_name, email, role FROM users")) {
                System.out.println("\nSeed Users:");
                while (rs.next()) {
                    System.out.println("  - #" + rs.getInt("user_id") + ": " +
                            rs.getString("full_name") + " (" +
                            rs.getString("email") + ") [" +
                            rs.getString("role") + "]");
                }
            }

        } catch (SQLException e) {
            System.out.println("Database connection failed.");
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
