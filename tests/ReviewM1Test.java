import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.mindrot.jbcrypt.BCrypt;

public class ReviewM1Test {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    private static void assertTrue(String message, boolean condition) {
        testsRun++;
        if (condition) {
            testsPassed++;
            System.out.println("  [PASS] " + message);
        } else {
            testsFailed++;
            System.err.println("  [FAIL] " + message);
        }
    }

    private static void assertEquals(String message, Object expected, Object actual) {
        testsRun++;
        if (expected == null && actual == null) {
            testsPassed++;
            System.out.println("  [PASS] " + message);
        } else if (expected != null && expected.equals(actual)) {
            testsPassed++;
            System.out.println("  [PASS] " + message);
        } else {
            testsFailed++;
            System.err.println("  [FAIL] " + message + " (Expected: " + expected + ", Actual: " + actual + ")");
        }
    }

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   M1 INDEPENDENT ADVERSARIAL VERIFICATION SUITE  ");
        System.out.println("=================================================");

        testAppConfig();
        testUserSession();
        testDbConnectionAndSchema();
        testPasswordHashingAndSeeds();
        testFrameRbacGuards();

        System.out.println("\n-------------------------------------------------");
        System.out.println("Results: " + testsPassed + " passed, " + testsFailed + " failed (Total: " + testsRun + ")");
        System.out.println("-------------------------------------------------");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void testAppConfig() {
        System.out.println("\n--- Testing AppConfig ---");

        // 1. Basic get and default
        assertEquals("get with default on missing key", "fallback", AppConfig.get("NON_EXISTENT_KEY_123", "fallback"));
        assertEquals("get with null key", "fallback", AppConfig.get(null, "fallback"));

        // 2. Case normalization
        String dbType = AppConfig.getDatabaseType();
        assertTrue("getDatabaseType returns sqlite or mysql", "sqlite".equals(dbType) || "mysql".equals(dbType));

        // 3. Overrides precedence
        AppConfig.setOverride("test.custom.key", "overridden_value");
        assertEquals("Tier 1 Override precedence works", "overridden_value", AppConfig.get("test.custom.key"));
        assertEquals("Tier 1 Override with snake case key", "overridden_value", AppConfig.get("TEST_CUSTOM_KEY"));

        AppConfig.setOverride("test.custom.key", null);
        assertEquals("Override removal works", "default", AppConfig.get("test.custom.key", "default"));

        // 4. Demo mode toggle
        boolean originalDemo = AppConfig.isDemoMode();
        AppConfig.setDemoMode(true);
        assertTrue("setDemoMode(true) works", AppConfig.isDemoMode());
        AppConfig.setDemoMode(false);
        assertTrue("setDemoMode(false) works", !AppConfig.isDemoMode());
        AppConfig.clearOverrides();

        // 5. Types
        assertEquals("getInt with valid value", 12, AppConfig.getBcryptRounds());
        assertEquals("getInt with fallback", 42, AppConfig.getInt("NON_EXISTING_INT", 42));
        assertEquals("getBoolean with fallback", true, AppConfig.getBoolean("NON_EXISTING_BOOL", true));
        assertEquals("getDouble with fallback", 3.14, AppConfig.getDouble("NON_EXISTING_DOUBLE", 3.14));
    }

    private static void testUserSession() {
        System.out.println("\n--- Testing UserSession ---");

        // 1. Valid construction
        UserSession admin = new UserSession(1, "Admin User", "admin@civiclens.gov", UserSession.Role.ADMIN);
        assertEquals("userId preserved", 1, admin.getUserId());
        assertEquals("fullName preserved", "Admin User", admin.getFullName());
        assertEquals("email preserved", "admin@civiclens.gov", admin.getEmail());
        assertEquals("role preserved", UserSession.Role.ADMIN, admin.getRole());
        assertTrue("isAdmin predicate", admin.isAdmin());
        assertTrue("!isCitizen predicate", !admin.isCitizen());
        assertTrue("!isEmployee predicate", !admin.isEmployee());
        assertTrue("isAuthenticated predicate", admin.isAuthenticated());

        // 2. Backward compatibility 'USER' -> CITIZEN
        UserSession legacy = new UserSession(5, "Legacy User", "user@civiclens.gov", "USER");
        assertEquals("Role.USER mapped to CITIZEN", UserSession.Role.CITIZEN, legacy.getRole());
        assertTrue("isCitizen for legacy USER", legacy.isCitizen());

        // 3. Invariant enforcement (Adversarial edge cases)
        assertThrows("Negative userId rejected", IllegalArgumentException.class, () ->
            new UserSession(-1, "Name", "email@civiclens.gov", UserSession.Role.CITIZEN)
        );
        assertThrows("Zero userId rejected", IllegalArgumentException.class, () ->
            new UserSession(0, "Name", "email@civiclens.gov", UserSession.Role.CITIZEN)
        );
        assertThrows("Null fullName rejected", IllegalArgumentException.class, () ->
            new UserSession(1, null, "email@civiclens.gov", UserSession.Role.CITIZEN)
        );
        assertThrows("Empty fullName rejected", IllegalArgumentException.class, () ->
            new UserSession(1, "   ", "email@civiclens.gov", UserSession.Role.CITIZEN)
        );
        assertThrows("Null email rejected", IllegalArgumentException.class, () ->
            new UserSession(1, "Name", null, UserSession.Role.CITIZEN)
        );
        assertThrows("Empty email rejected", IllegalArgumentException.class, () ->
            new UserSession(1, "Name", "  ", UserSession.Role.CITIZEN)
        );
        assertThrows("Null role rejected", IllegalArgumentException.class, () ->
            new UserSession(1, "Name", "email@civiclens.gov", (UserSession.Role) null)
        );
        assertThrows("Invalid role string rejected", IllegalArgumentException.class, () ->
            new UserSession(1, "Name", "email@civiclens.gov", "SUPER_ADMIN_INVALID")
        );

        // 4. Value equality and immutability
        UserSession adminCopy = new UserSession(1, "Admin User", "admin@civiclens.gov", UserSession.Role.ADMIN);
        assertEquals("Session equality", admin, adminCopy);
        assertEquals("Session hashCode equality", admin.hashCode(), adminCopy.hashCode());

        // 5. Static ambient session
        UserSession.setCurrentSession(admin);
        assertEquals("Ambient session set", admin, UserSession.getCurrentSession());
        UserSession.clearCurrentSession();
        assertEquals("Ambient session cleared", null, UserSession.getCurrentSession());
    }

    private static void testDbConnectionAndSchema() {
        System.out.println("\n--- Testing DbConnection & SQLite Persistence ---");

        try (Connection conn = DbConnection.getConnection()) {
            assertTrue("Connection is not null", conn != null);
            assertTrue("Connection is open", !conn.isClosed());

            // Verify Foreign Key enforcement in SQLite
            if ("sqlite".equalsIgnoreCase(DbConnection.getActiveDatabaseType())) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                    if (rs.next()) {
                        assertEquals("Foreign keys PRAGMA is ON", 1, rs.getInt(1));
                    }
                }
            }

            // Verify that inserting a complaint with non-existent user_id fails foreign key constraint
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO complaints (user_id, title, category, description, priority, status) " +
                    "VALUES (999999, 'Test Ghost User', 'Technical Issue', 'Should fail FK', 'LOW', 'SUBMITTED')")) {
                boolean threw = false;
                try {
                    pstmt.executeUpdate();
                } catch (SQLException fkEx) {
                    threw = true;
                }
                assertTrue("Foreign key violation on non-existent user_id throws SQLException", threw);
            }

            // Test Idempotent schema initialization
            try {
                DbConnection.initializeSchema(conn);
                assertTrue("Re-running initializeSchema() is idempotent without crashing", true);
            } catch (Exception ex) {
                assertTrue("Re-running initializeSchema() crashed: " + ex.getMessage(), false);
            }

        } catch (SQLException e) {
            assertTrue("DbConnection failed: " + e.getMessage(), false);
        }

        // Test Fresh Database Initialization in isolation
        String tempDbPath = "test_clean_init.db";
        File tempDbFile = new File(tempDbPath);
        if (tempDbFile.exists()) {
            tempDbFile.delete();
        }

        AppConfig.setOverride("sqlite.db.path", tempDbPath);
        DbConnection.resetActiveDatabaseType();

        try (Connection freshConn = DbConnection.getConnection()) {
            assertTrue("Fresh DB connection established", freshConn != null);
            try (Statement stmt = freshConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue("Fresh DB has users table populated", rs.next() && rs.getInt(1) == 6);
            }
            try (Statement stmt = freshConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sla_policies")) {
                assertTrue("Fresh DB has sla_policies table populated", rs.next() && rs.getInt(1) == 16);
            }
        } catch (SQLException ex) {
            assertTrue("Fresh DB initialization failed: " + ex.getMessage(), false);
        } finally {
            AppConfig.setOverride("sqlite.db.path", null);
            DbConnection.resetActiveDatabaseType();
            if (tempDbFile.exists()) {
                tempDbFile.delete();
            }
        }

        // Test closeQuietly
        DbConnection.closeQuietly((AutoCloseable) null);
        assertTrue("closeQuietly handles null without error", true);
    }

    private static void testPasswordHashingAndSeeds() {
        System.out.println("\n--- Testing BCrypt & Seed Account Authentication ---");

        String[][] seedCredentials = {
            {"admin@civiclens.gov", "Admin@123", "ADMIN"},
            {"employee@civiclens.gov", "Employee@123", "EMPLOYEE"},
            {"officer@civiclens.gov", "Employee@123", "EMPLOYEE"},
            {"tech.field@civiclens.gov", "Employee@123", "EMPLOYEE"},
            {"citizen@civiclens.gov", "Citizen@123", "CITIZEN"},
            {"user@civiclens.gov", "Citizen@123", "CITIZEN"}
        };

        try (Connection conn = DbConnection.getConnection()) {
            for (String[] cred : seedCredentials) {
                String email = cred[0];
                String password = cred[1];
                String expectedRole = cred[2];

                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT password_hash, role FROM users WHERE email = ?")) {
                    stmt.setString(1, email);
                    try (ResultSet rs = stmt.executeQuery()) {
                        assertTrue("Seed user exists: " + email, rs.next());
                        String hash = rs.getString("password_hash");
                        String role = rs.getString("role");
                        assertEquals("Role matches for " + email, expectedRole, role);

                        boolean passwordMatches = BCrypt.checkpw(password, hash);
                        assertTrue("BCrypt password matches for " + email, passwordMatches);

                        boolean wrongPasswordFails = !BCrypt.checkpw("InvalidPassword_999", hash);
                        assertTrue("Invalid password fails for " + email, wrongPasswordFails);
                    }
                }
            }
        } catch (SQLException e) {
            assertTrue("Seed verification failed: " + e.getMessage(), false);
        }
    }

    private static void testFrameRbacGuards() {
        System.out.println("\n--- Testing Frame RBAC Security Guards ---");

        UserSession citizenSession = new UserSession(5, "Elena Rostova", "citizen@civiclens.gov", UserSession.Role.CITIZEN);
        UserSession employeeSession = new UserSession(2, "Sarah Jenkins", "employee@civiclens.gov", UserSession.Role.EMPLOYEE);
        UserSession adminSession = new UserSession(1, "Admin", "admin@civiclens.gov", UserSession.Role.ADMIN);

        // 1. AdminDashboardFrame
        assertThrows("AdminDashboard rejects citizen session", SecurityException.class, () ->
            new AdminDashboardFrame(citizenSession)
        );
        assertThrows("AdminDashboard rejects employee session", SecurityException.class, () ->
            new AdminDashboardFrame(employeeSession)
        );
        assertThrows("AdminDashboard rejects null session", SecurityException.class, () ->
            new AdminDashboardFrame(null)
        );

        // 2. EmployeeDashboardFrame
        assertThrows("EmployeeDashboard rejects citizen session", SecurityException.class, () ->
            new EmployeeDashboardFrame(citizenSession)
        );
        assertThrows("EmployeeDashboard rejects admin session", SecurityException.class, () ->
            new EmployeeDashboardFrame(adminSession)
        );
        assertThrows("EmployeeDashboard rejects null session", SecurityException.class, () ->
            new EmployeeDashboardFrame(null)
        );

        // 3. ComplaintFrame & DashboardFrame (Citizen only)
        assertThrows("ComplaintFrame rejects employee session", SecurityException.class, () ->
            new ComplaintFrame(employeeSession)
        );
        assertThrows("ComplaintFrame rejects admin session", SecurityException.class, () ->
            new ComplaintFrame(adminSession)
        );
        assertThrows("ComplaintFrame rejects null session", SecurityException.class, () ->
            new ComplaintFrame(null)
        );

        assertThrows("DashboardFrame rejects employee session", SecurityException.class, () ->
            new DashboardFrame(employeeSession)
        );
        assertThrows("DashboardFrame rejects admin session", SecurityException.class, () ->
            new DashboardFrame(adminSession)
        );

        // 4. EmployeeManagementFrame & ReportsDashboardFrame (Admin only)
        assertThrows("EmployeeManagementFrame rejects citizen session", SecurityException.class, () ->
            new EmployeeManagementFrame(citizenSession)
        );
        assertThrows("EmployeeManagementFrame rejects employee session", SecurityException.class, () ->
            new EmployeeManagementFrame(employeeSession)
        );

        assertThrows("ReportsDashboardFrame rejects citizen session", SecurityException.class, () ->
            new ReportsDashboardFrame(citizenSession)
        );
        assertThrows("ReportsDashboardFrame rejects employee session", SecurityException.class, () ->
            new ReportsDashboardFrame(employeeSession)
        );
    }

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private static void assertThrows(String message, Class<? extends Throwable> expected, ThrowingRunnable runnable) {
        testsRun++;
        try {
            runnable.run();
            testsFailed++;
            System.err.println("  [FAIL] " + message + " (Expected " + expected.getSimpleName() + " but nothing was thrown)");
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                testsPassed++;
                System.out.println("  [PASS] " + message);
            } else {
                testsFailed++;
                System.err.println("  [FAIL] " + message + " (Expected " + expected.getSimpleName() + " but got " + actual.getClass().getSimpleName() + ": " + actual.getMessage() + ")");
            }
        }
    }
}
