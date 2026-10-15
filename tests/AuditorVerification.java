import java.sql.*;
import org.mindrot.jbcrypt.BCrypt;

public class AuditorVerification {

    private static int totalChecks = 0;
    private static int passedChecks = 0;
    private static int failedChecks = 0;

    private static void check(String description, boolean condition, String details) {
        totalChecks++;
        if (condition) {
            passedChecks++;
            System.out.println("  [PASS] " + description + (details != null && !details.isEmpty() ? " (" + details + ")" : ""));
        } else {
            failedChecks++;
            System.err.println("  [FAIL] " + description + (details != null && !details.isEmpty() ? " (" + details + ")" : ""));
        }
    }

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("CivicLens Forensic Auditor Independent Test Suite");
        System.out.println("=================================================\n");

        testAppConfig();
        testUserSession();
        testFrameSecurity();
        testSeedPasswords();
        testSqlitePersistenceAndForeignKeys();

        System.out.println("\n-------------------------------------------------");
        System.out.println("Summary: " + passedChecks + "/" + totalChecks + " checks passed. Failures: " + failedChecks);
        System.out.println("-------------------------------------------------");

        if (failedChecks > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    private static void testAppConfig() {
        System.out.println("Phase A: AppConfig Configuration & Normalization Checks");

        check("AppConfig loaded successfully", AppConfig.getDatabaseType() != null, "type: " + AppConfig.getDatabaseType());
        check("Default or .env database type is sqlite", "sqlite".equalsIgnoreCase(AppConfig.getDatabaseType()), "db.type: " + AppConfig.getDatabaseType());
        check("SQLite db path configured", AppConfig.getSqliteDbPath() != null && !AppConfig.getSqliteDbPath().isEmpty(), "path: " + AppConfig.getSqliteDbPath());

        // Test key normalization
        AppConfig.setOverride("AUDIT_TEST_KEY", "value_123");
        check("Key normalization SNAKE_CASE to dot.case", "value_123".equals(AppConfig.get("audit.test.key")), "audit.test.key=" + AppConfig.get("audit.test.key"));

        // Test programmatic override precedence
        AppConfig.setOverride("db.type", "override_type");
        check("Programmatic override takes precedence", "override_type".equals(AppConfig.get("db.type")), "val: " + AppConfig.get("db.type"));
        AppConfig.clearOverrides();
        check("Override cleanup restored default", !"override_type".equals(AppConfig.get("db.type")), "val: " + AppConfig.get("db.type"));
    }

    private static void testUserSession() {
        System.out.println("\nPhase B: UserSession RBAC & Immutability Invariant Checks");

        UserSession admin = new UserSession(1, "Admin User", "admin@civiclens.gov", UserSession.Role.ADMIN);
        check("Admin role check", admin.isAdmin() && !admin.isCitizen() && !admin.isEmployee(), "isAdmin: " + admin.isAdmin());
        check("Admin userId check", admin.getUserId() == 1, "userId: " + admin.getUserId());
        check("Admin isAuthenticated check", admin.isAuthenticated(), "authenticated: " + admin.isAuthenticated());

        UserSession citizen = new UserSession(5, "Elena", "elena@civiclens.gov", UserSession.Role.CITIZEN);
        check("Citizen role check", citizen.isCitizen() && !citizen.isAdmin() && !citizen.isEmployee(), "isCitizen: " + citizen.isCitizen());

        // Test legacy 'USER' role parsing
        UserSession legacy = new UserSession(6, "Legacy", "legacy@civiclens.gov", "USER");
        check("Legacy 'USER' string maps to CITIZEN", legacy.isCitizen(), "role: " + legacy.getRole());

        // Test invalid arguments validation
        boolean caughtInvalidId = false;
        try {
            new UserSession(-1, "Bad", "bad@civiclens.gov", UserSession.Role.CITIZEN);
        } catch (IllegalArgumentException ex) {
            caughtInvalidId = true;
        }
        check("Rejection of negative userId", caughtInvalidId, "IllegalArgumentException expected");

        boolean caughtNullEmail = false;
        try {
            new UserSession(1, "Bad", null, UserSession.Role.CITIZEN);
        } catch (IllegalArgumentException ex) {
            caughtNullEmail = true;
        }
        check("Rejection of null email", caughtNullEmail, "IllegalArgumentException expected");

        boolean caughtNullRole = false;
        try {
            new UserSession(1, "Bad", "bad@civiclens.gov", (UserSession.Role) null);
        } catch (IllegalArgumentException ex) {
            caughtNullRole = true;
        }
        check("Rejection of null role", caughtNullRole, "IllegalArgumentException expected");

        // Test ambient session holder
        UserSession.setCurrentSession(admin);
        check("Ambient session holder sets context", UserSession.getCurrentSession() == admin, "same instance");
        UserSession.clearCurrentSession();
        check("Ambient session holder clears context", UserSession.getCurrentSession() == null, "cleared");
    }

    private static void testFrameSecurity() {
        System.out.println("\nPhase C: Frame RBAC Security & Constructor Guard Checks");

        UserSession citizen = new UserSession(5, "Elena", "elena@civiclens.gov", UserSession.Role.CITIZEN);
        UserSession employee = new UserSession(2, "Sarah", "sarah@civiclens.gov", UserSession.Role.EMPLOYEE);
        UserSession admin = new UserSession(1, "Admin", "admin@civiclens.gov", UserSession.Role.ADMIN);

        // Citizen trying to access AdminDashboardFrame
        boolean caughtAdminDenial = false;
        try {
            new AdminDashboardFrame(citizen);
        } catch (SecurityException ex) {
            caughtAdminDenial = true;
        } catch (Throwable t) {
            // In headless environment, HeadlessException might occur AFTER security check,
            // but the constructor should throw SecurityException before any UI initialization.
            if (t.getCause() instanceof SecurityException) caughtAdminDenial = true;
        }
        check("AdminDashboardFrame rejects Citizen session", caughtAdminDenial, "SecurityException expected");

        // Employee trying to access AdminDashboardFrame
        boolean caughtEmployeeDenial = false;
        try {
            new AdminDashboardFrame(employee);
        } catch (SecurityException ex) {
            caughtEmployeeDenial = true;
        } catch (Throwable t) {
            if (t.getCause() instanceof SecurityException) caughtEmployeeDenial = true;
        }
        check("AdminDashboardFrame rejects Employee session", caughtEmployeeDenial, "SecurityException expected");

        // Citizen trying to access EmployeeDashboardFrame
        boolean caughtEmpDashboardDenial = false;
        try {
            new EmployeeDashboardFrame(citizen);
        } catch (SecurityException ex) {
            caughtEmpDashboardDenial = true;
        } catch (Throwable t) {
            if (t.getCause() instanceof SecurityException) caughtEmpDashboardDenial = true;
        }
        check("EmployeeDashboardFrame rejects Citizen session", caughtEmpDashboardDenial, "SecurityException expected");

        // Employee trying to access ComplaintFrame (Citizen only)
        boolean caughtCitizenDenial = false;
        try {
            new ComplaintFrame(employee);
        } catch (SecurityException ex) {
            caughtCitizenDenial = true;
        } catch (Throwable t) {
            if (t.getCause() instanceof SecurityException) caughtCitizenDenial = true;
        }
        check("ComplaintFrame rejects Employee session", caughtCitizenDenial, "SecurityException expected");
    }

    private static void testSeedPasswords() {
        System.out.println("\nPhase D: Seed User Accounts & BCrypt Hash Verification");

        try (Connection conn = DbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT full_name, email, password_hash, role FROM users ORDER BY user_id")) {

            int count = 0;
            while (rs.next()) {
                count++;
                String email = rs.getString("email");
                String hash = rs.getString("password_hash");
                String role = rs.getString("role");

                if ("admin@civiclens.gov".equals(email)) {
                    boolean matches = BCrypt.checkpw("Admin@123", hash);
                    check("Admin password verifies with BCrypt ('Admin@123')", matches, email);
                } else if ("employee@civiclens.gov".equals(email)) {
                    boolean matches = BCrypt.checkpw("Employee@123", hash);
                    check("Employee password verifies with BCrypt ('Employee@123')", matches, email);
                } else if ("citizen@civiclens.gov".equals(email)) {
                    boolean matches = BCrypt.checkpw("Citizen@123", hash);
                    check("Citizen password verifies with BCrypt ('Citizen@123')", matches, email);
                }
            }
            check("Verified at least 6 seed users in database", count >= 6, "count: " + count);

        } catch (SQLException ex) {
            check("Querying users table succeeded", false, ex.getMessage());
        }
    }

    private static void testSqlitePersistenceAndForeignKeys() {
        System.out.println("\nPhase E: SQLite Persistence, Foreign Key Constraints & Data Integrity");

        try (Connection conn = DbConnection.getConnection()) {
            check("Active connection is valid", conn != null && !conn.isClosed(), "conn valid");

            // Verify foreign key pragma is enabled
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                int fkEnabled = rs.next() ? rs.getInt(1) : 0;
                check("SQLite PRAGMA foreign_keys is ON", fkEnabled == 1, "pragma value: " + fkEnabled);
            }

            // Test genuine INSERT and SELECT
            int insertedComplaintId = -1;
            String insertSql = "INSERT INTO complaints (user_id, title, category, description, priority, status) " +
                               "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, 5); // Elena Rostova (Citizen)
                pstmt.setString(2, "Forensic Test Pothole On Main St");
                pstmt.setString(3, "Infrastructure");
                pstmt.setString(4, "Deep pothole damaging vehicles near intersection.");
                pstmt.setString(5, "HIGH");
                pstmt.setString(6, "SUBMITTED");
                int rows = pstmt.executeUpdate();
                check("Insert test complaint succeeded", rows == 1, "rows affected: " + rows);

                try (ResultSet gk = pstmt.getGeneratedKeys()) {
                    if (gk.next()) {
                        insertedComplaintId = gk.getInt(1);
                    }
                }
            }

            check("Generated keys returned valid complaint_id", insertedComplaintId > 0, "complaint_id: " + insertedComplaintId);

            // Verify the complaint is queryable
            if (insertedComplaintId > 0) {
                String selectSql = "SELECT user_id, title, category, priority, status FROM complaints WHERE complaint_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                    pstmt.setInt(1, insertedComplaintId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        boolean exists = rs.next();
                        check("Query back inserted complaint", exists, "found: " + exists);
                        if (exists) {
                            check("Verified complaint title", "Forensic Test Pothole On Main St".equals(rs.getString("title")), rs.getString("title"));
                            check("Verified complaint status is SUBMITTED", "SUBMITTED".equals(rs.getString("status")), rs.getString("status"));
                        }
                    }
                }
            }

            // Test Foreign Key enforcement: attempt to insert complaint with non-existent user_id
            boolean caughtFkViolation = false;
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setInt(1, 999999); // Non-existent user
                pstmt.setString(2, "Bad User FK Complaint");
                pstmt.setString(3, "Infrastructure");
                pstmt.setString(4, "Should fail foreign key check");
                pstmt.setString(5, "LOW");
                pstmt.setString(6, "SUBMITTED");
                pstmt.executeUpdate();
            } catch (SQLException ex) {
                caughtFkViolation = true;
            }
            check("Foreign key violation correctly rejected with non-existent user_id", caughtFkViolation, "Foreign key enforced");

            // Clean up test complaint
            if (insertedComplaintId > 0) {
                try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM complaints WHERE complaint_id = ?")) {
                    pstmt.setInt(1, insertedComplaintId);
                    int deleted = pstmt.executeUpdate();
                    check("Cleaned up test complaint", deleted == 1, "deleted rows: " + deleted);
                }
            }

        } catch (SQLException ex) {
            check("Database operations succeeded without unexpected errors", false, ex.getMessage());
        }
    }
}
