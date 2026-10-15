import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class DbConcurrencyRemediationTest {

    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean condition, String details) {
        if (condition) {
            System.out.println("  [PASS] " + name + " | " + details);
            passed++;
        } else {
            System.err.println("  [FAIL] " + name + " | " + details);
            failed++;
        }
    }

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("DbConnection Remediation Targeted Behavioral Tests");
        System.out.println("=================================================");

        testPragmaSettings();
        testMultiTableCanarySelfHealing();
        testConcurrentStartupNoRace(20);

        System.out.println("\n-------------------------------------------------");
        System.out.println("Results: " + passed + " passed, " + failed + " failed (Total: " + (passed + failed) + ")");
        System.out.println("-------------------------------------------------");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testPragmaSettings() {
        System.out.println("\n--- Testing SQLite PRAGMA Concurrency Tuning ---");
        Path testDb = Paths.get("out", "pragma_test_" + System.currentTimeMillis() + ".db");
        try {
            AppConfig.clearOverrides();
            AppConfig.setOverride("sqlite.db.path", testDb.toString());
            AppConfig.setOverride("db.type", "sqlite");
            DbConnection.resetActiveDatabaseType();

            try (Connection conn = DbConnection.getConnection();
                 Statement stmt = conn.createStatement()) {

                int foreignKeys = -1;
                try (ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                    if (rs.next()) foreignKeys = rs.getInt(1);
                }
                check("PRAGMA foreign_keys = ON", foreignKeys == 1, "foreign_keys=" + foreignKeys);

                int busyTimeout = -1;
                try (ResultSet rs = stmt.executeQuery("PRAGMA busy_timeout")) {
                    if (rs.next()) busyTimeout = rs.getInt(1);
                }
                check("PRAGMA busy_timeout = 5000", busyTimeout == 5000, "busy_timeout=" + busyTimeout);

                String journalMode = "";
                try (ResultSet rs = stmt.executeQuery("PRAGMA journal_mode")) {
                    if (rs.next()) journalMode = rs.getString(1);
                }
                check("PRAGMA journal_mode = WAL", "wal".equalsIgnoreCase(journalMode), "journal_mode=" + journalMode);

                int synchronous = -1;
                try (ResultSet rs = stmt.executeQuery("PRAGMA synchronous")) {
                    if (rs.next()) synchronous = rs.getInt(1);
                }
                check("PRAGMA synchronous = NORMAL (1)", synchronous == 1, "synchronous=" + synchronous);
            }
        } catch (Exception e) {
            check("PRAGMA configuration threw unexpected exception", false, e.getMessage());
        } finally {
            AppConfig.clearOverrides();
            DbConnection.resetActiveDatabaseType();
            try { Files.deleteIfExists(testDb); } catch (IOException ignored) {}
        }
    }

    private static void testMultiTableCanarySelfHealing() {
        System.out.println("\n--- Testing Multi-Table Canary Self-Healing ---");
        Path testDb = Paths.get("out", "canary_test_" + System.currentTimeMillis() + ".db");
        try {
            // Create a corrupt/partial database containing only 'users' table
            String url = "jdbc:sqlite:" + testDb.toString();
            try (Connection rawConn = DriverManager.getConnection(url);
                 Statement stmt = rawConn.createStatement()) {
                stmt.execute("CREATE TABLE users (user_id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT UNIQUE, full_name TEXT, role TEXT, password_hash TEXT);");
                stmt.execute("INSERT INTO users (user_id, email, full_name, role, password_hash) VALUES (1, 'admin@civiclens.gov', 'Admin User', 'ADMIN', 'dummy_hash');");
                stmt.execute("INSERT INTO users (user_id, email, full_name, role, password_hash) VALUES (99, 'partial@civiclens.gov', 'Partial User', 'CITIZEN', 'dummy_hash');");
            }

            AppConfig.clearOverrides();
            AppConfig.setOverride("sqlite.db.path", testDb.toString());
            AppConfig.setOverride("db.type", "sqlite");
            DbConnection.resetActiveDatabaseType();

            // Calling getConnection() should detect missing canaries (sla_policies, complaints, incident_groups)
            // and trigger self-healing initializeSchema() without dropping the existing user 99!
            try (Connection conn = DbConnection.getConnection();
                 Statement stmt = conn.createStatement()) {

                boolean partialUserSurvived = false;
                try (ResultSet rs = stmt.executeQuery("SELECT full_name FROM users WHERE user_id = 99")) {
                    if (rs.next() && "Partial User".equals(rs.getString("full_name"))) {
                        partialUserSurvived = true;
                    }
                }
                check("Existing data preserved during self-heal", partialUserSurvived, "user_id=99 survived");

                int slaCount = -1;
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sla_policies")) {
                    if (rs.next()) slaCount = rs.getInt(1);
                }
                check("Missing canary 'sla_policies' created & seeded", slaCount == 16, "sla_policies count=" + slaCount);

                int complaintsCount = -1;
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM complaints")) {
                    if (rs.next()) complaintsCount = rs.getInt(1);
                }
                check("Missing canary 'complaints' table created", complaintsCount >= 0, "complaints count=" + complaintsCount);

                int incidentGroupsCount = -1;
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM incident_groups")) {
                    if (rs.next()) incidentGroupsCount = rs.getInt(1);
                }
                check("Missing canary 'incident_groups' created & seeded", incidentGroupsCount == 1, "incident_groups count=" + incidentGroupsCount);
            }
        } catch (Exception e) {
            check("Canary self-healing test exception", false, e.getMessage());
        } finally {
            AppConfig.clearOverrides();
            DbConnection.resetActiveDatabaseType();
            try { Files.deleteIfExists(testDb); } catch (IOException ignored) {}
        }
    }

    private static void testConcurrentStartupNoRace(int numThreads) {
        System.out.println("\n--- Testing Concurrent First Initialization Race Gate ---");
        Path testDb = Paths.get("out", "gate_test_" + System.currentTimeMillis() + ".db");
        try {
            AppConfig.clearOverrides();
            AppConfig.setOverride("sqlite.db.path", testDb.toString());
            AppConfig.setOverride("db.type", "sqlite");
            DbConnection.resetActiveDatabaseType();

            ExecutorService pool = Executors.newFixedThreadPool(numThreads);
            CountDownLatch readyLatch = new CountDownLatch(numThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numThreads);

            AtomicInteger success = new AtomicInteger(0);
            AtomicInteger failures = new AtomicInteger(0);
            List<String> errors = new CopyOnWriteArrayList<>();

            for (int i = 0; i < numThreads; i++) {
                final int tid = i;
                pool.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        try (Connection conn = DbConnection.getConnection();
                             Statement stmt = conn.createStatement();
                             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sla_policies")) {
                            if (rs.next() && rs.getInt(1) == 16) {
                                success.incrementAndGet();
                            } else {
                                failures.incrementAndGet();
                                errors.add("T" + tid + " count mismatch");
                            }
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        errors.add("T" + tid + ": " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
            pool.shutdownNow();

            boolean pass = finished && (success.get() == numThreads) && (failures.get() == 0);
            check("All " + numThreads + " threads acquire connection after schema ready", pass,
                "success=" + success.get() + "/" + numThreads + ", failures=" + failures.get() +
                (errors.isEmpty() ? "" : " | " + errors.get(0)));
        } catch (Exception e) {
            check("Concurrent startup exception", false, e.getMessage());
        } finally {
            AppConfig.clearOverrides();
            DbConnection.resetActiveDatabaseType();
            try { Files.deleteIfExists(testDb); } catch (IOException ignored) {}
        }
    }
}
