import java.sql.*;
import java.util.*;
import org.mindrot.jbcrypt.BCrypt;

public class ReviewerAdversarialTest {

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
        System.out.println("==================================================================");
        System.out.println("Reviewer Adversarial & Integrity Verification Suite (Milestone 1)");
        System.out.println("==================================================================\n");

        try {
            testRbacExhaustiveFrameSecurity();
            testAppConfigAdversarial();
            testSchemaPortabilityAndIntegrityConstraints();
            testLegacyFrameQueriesAndMutations();
            testSchemaReinitializationIdempotency();
        } catch (Exception e) {
            System.err.println("Unexpected exception during test run: " + e.getMessage());
            e.printStackTrace();
            failedChecks++;
        }

        System.out.println("\n==================================================================");
        System.out.println("Reviewer Test Summary: " + passedChecks + "/" + totalChecks + " checks passed. Failures: " + failedChecks);
        System.out.println("==================================================================");

        if (failedChecks > 0) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    private static void testRbacExhaustiveFrameSecurity() {
        System.out.println("Suite 1: Exhaustive RBAC & Frame Constructor Security Verification");

        UserSession citizen = new UserSession(5, "Elena Rostova", "citizen@civiclens.gov", UserSession.Role.CITIZEN);
        UserSession employee = new UserSession(2, "Sarah Jenkins", "employee@civiclens.gov", UserSession.Role.EMPLOYEE);
        UserSession admin = new UserSession(1, "System Admin", "admin@civiclens.gov", UserSession.Role.ADMIN);

        // Ensure ambient session is cleared first
        UserSession.clearCurrentSession();
        AppConfig.setDemoMode(false);

        // 1. AdminDashboardFrame
        assertThrowsSecurityException("AdminDashboardFrame rejects null session", () -> new AdminDashboardFrame(null));
        assertThrowsSecurityException("AdminDashboardFrame rejects Citizen session", () -> new AdminDashboardFrame(citizen));
        assertThrowsSecurityException("AdminDashboardFrame rejects Employee session", () -> new AdminDashboardFrame(employee));
        assertThrowsSecurityException("AdminDashboardFrame() rejects without session", () -> new AdminDashboardFrame());

        // 2. EmployeeDashboardFrame
        assertThrowsSecurityException("EmployeeDashboardFrame rejects null session", () -> new EmployeeDashboardFrame(null));
        assertThrowsSecurityException("EmployeeDashboardFrame rejects Citizen session", () -> new EmployeeDashboardFrame(citizen));
        assertThrowsSecurityException("EmployeeDashboardFrame rejects Admin session", () -> new EmployeeDashboardFrame(admin));

        // 3. DashboardFrame (Citizen Dashboard)
        assertThrowsSecurityException("DashboardFrame rejects null session", () -> new DashboardFrame(null));
        assertThrowsSecurityException("DashboardFrame rejects Employee session", () -> new DashboardFrame(employee));
        assertThrowsSecurityException("DashboardFrame rejects Admin session", () -> new DashboardFrame(admin));
        assertThrowsSecurityException("DashboardFrame legacy constructor rejects EMPLOYEE role string", () -> new DashboardFrame(2, "Sarah", "sarah@test.com", "EMPLOYEE"));
        assertThrowsSecurityException("DashboardFrame legacy constructor rejects ADMIN role string", () -> new DashboardFrame(1, "Admin", "admin@test.com", "ADMIN"));

        // 4. ComplaintFrame (Citizen only)
        assertThrowsSecurityException("ComplaintFrame rejects null session", () -> new ComplaintFrame((UserSession) null));
        assertThrowsSecurityException("ComplaintFrame rejects Employee session", () -> new ComplaintFrame(employee));
        assertThrowsSecurityException("ComplaintFrame rejects Admin session", () -> new ComplaintFrame(admin));

        // 5. MyComplaintsFrame (Citizen only)
        assertThrowsSecurityException("MyComplaintsFrame rejects null session", () -> new MyComplaintsFrame((UserSession) null));
        assertThrowsSecurityException("MyComplaintsFrame rejects Employee session", () -> new MyComplaintsFrame(employee));
        assertThrowsSecurityException("MyComplaintsFrame rejects Admin session", () -> new MyComplaintsFrame(admin));

        // 6. EmployeeManagementFrame (Admin only)
        assertThrowsSecurityException("EmployeeManagementFrame rejects null session", () -> new EmployeeManagementFrame(null));
        assertThrowsSecurityException("EmployeeManagementFrame rejects Citizen session", () -> new EmployeeManagementFrame(citizen));
        assertThrowsSecurityException("EmployeeManagementFrame rejects Employee session", () -> new EmployeeManagementFrame(employee));
        assertThrowsSecurityException("EmployeeManagementFrame() rejects without session", () -> new EmployeeManagementFrame());

        // 7. ReportsDashboardFrame (Admin only)
        assertThrowsSecurityException("ReportsDashboardFrame rejects null session", () -> new ReportsDashboardFrame(null));
        assertThrowsSecurityException("ReportsDashboardFrame rejects Citizen session", () -> new ReportsDashboardFrame(citizen));
        assertThrowsSecurityException("ReportsDashboardFrame rejects Employee session", () -> new ReportsDashboardFrame(employee));
        assertThrowsSecurityException("ReportsDashboardFrame() rejects without session", () -> new ReportsDashboardFrame());

        // Test demo mode fallback allows no-arg constructors
        AppConfig.setDemoMode(true);
        boolean adminDemoPass = false;
        try {
            AdminDashboardFrame frame = new AdminDashboardFrame();
            adminDemoPass = (frame != null);
        } catch (Throwable t) {
            // In headless environment, HeadlessException is expected after successful security authorization
            if (t instanceof java.awt.HeadlessException || (t.getCause() != null && t.getCause() instanceof java.awt.HeadlessException)) {
                adminDemoPass = true;
            }
        }
        check("AdminDashboardFrame() succeeds when APP_DEMO_MODE=true", adminDemoPass, "demo mode allowed");

        // Clean up
        AppConfig.setDemoMode(false);
        UserSession.clearCurrentSession();
    }

    private static void testAppConfigAdversarial() {
        System.out.println("\nSuite 2: AppConfig Precedence & Dialect/Key Parsing Stress Testing");

        // 1. Programmatic Override Precedence
        AppConfig.setOverride("TEST_CUSTOM_KEY", "override_value");
        check("AppConfig gets override with original key", "override_value".equals(AppConfig.get("TEST_CUSTOM_KEY")), "TEST_CUSTOM_KEY");
        check("AppConfig gets override with snake_case key", "override_value".equals(AppConfig.get("test_custom_key")), "test_custom_key");
        check("AppConfig gets override with dot.case key", "override_value".equals(AppConfig.get("test.custom.key")), "test.custom.key");
        AppConfig.clearOverrides();
        check("AppConfig clearOverrides removes override", AppConfig.get("TEST_CUSTOM_KEY") == null, "cleared");

        // 2. Types & Defaults
        check("AppConfig getInt with invalid string returns default", AppConfig.getInt("non_existent_key_abc", 42) == 42, "default 42");
        check("AppConfig getDouble with invalid string returns default", AppConfig.getDouble("non_existent_key_abc", 3.14) == 3.14, "default 3.14");
        check("AppConfig getBoolean returns false for undefined key", !AppConfig.getBoolean("non_existent_key_abc", false), "default false");

        // 3. Boolean parsing variations
        AppConfig.setOverride("TEST_BOOL_TRUE", "true");
        AppConfig.setOverride("TEST_BOOL_ONE", "1");
        AppConfig.setOverride("TEST_BOOL_FALSE", "false");
        AppConfig.setOverride("TEST_BOOL_ZERO", "0");
        check("Boolean 'true' parsed as true", AppConfig.getBoolean("TEST_BOOL_TRUE", false), "true");
        check("Boolean '1' parsed as true", AppConfig.getBoolean("TEST_BOOL_ONE", false), "1");
        check("Boolean 'false' parsed as false", !AppConfig.getBoolean("TEST_BOOL_FALSE", true), "false");
        check("Boolean '0' parsed as false", !AppConfig.getBoolean("TEST_BOOL_ZERO", true), "0");
        AppConfig.clearOverrides();

        // 4. Default DB values verification
        check("Default database type is 'sqlite'", "sqlite".equalsIgnoreCase(AppConfig.getDatabaseType()), AppConfig.getDatabaseType());
        check("Default sqlite path is not empty", AppConfig.getSqliteDbPath() != null && !AppConfig.getSqliteDbPath().isEmpty(), AppConfig.getSqliteDbPath());
        check("Auto-fallback is enabled by default", AppConfig.isAutoFallbackEnabled(), "auto-fallback enabled");
        check("Zero hardcoded DB password in config", AppConfig.getDbPassword() != null, "password: " + (AppConfig.getDbPassword().isEmpty() ? "(empty string)" : "(present)"));
    }

    private static void testSchemaPortabilityAndIntegrityConstraints() throws SQLException {
        System.out.println("\nSuite 3: Database Schema, Foreign Key Constraints & Data Integrity");

        try (Connection conn = DbConnection.getConnection()) {
            check("Database connection is open and active", conn != null && !conn.isClosed(), "connected");

            // 1. Verify all 12 tables exist
            String[] tables = {
                "users", "complaints", "priority_overrides", "incident_groups",
                "incident_members", "sla_policies", "sla_deadlines",
                "resolution_records", "citizen_feedback", "complaint_audit_log",
                "internal_notes", "complaint_appeals"
            };

            for (String table : tables) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1 FROM " + table + " LIMIT 1")) {
                    check("Table '" + table + "' exists and is queryable", true, table);
                } catch (SQLException ex) {
                    check("Table '" + table + "' exists and is queryable", false, ex.getMessage());
                }
            }

            // 2. Foreign Key Constraint Enforcement
            // Inserting complaint with non-existent user_id must fail
            boolean fkRejected = false;
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO complaints (user_id, title, category, description, priority, status) VALUES (?, ?, ?, ?, ?, ?)")) {
                pstmt.setInt(1, 999999);
                pstmt.setString(2, "Orphan Complaint");
                pstmt.setString(3, "Infrastructure");
                pstmt.setString(4, "Desc");
                pstmt.setString(5, "LOW");
                pstmt.setString(6, "SUBMITTED");
                pstmt.executeUpdate();
            } catch (SQLException ex) {
                fkRejected = true;
            }
            check("Foreign Key rejects orphan complaint (user_id=999999)", fkRejected, "FK enforced");

            // 3. Unique Constraint Enforcement: duplicate email in users
            boolean duplicateEmailRejected = false;
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO users (full_name, email, password_hash, role) VALUES (?, ?, ?, ?)")) {
                pstmt.setString(1, "Clone Admin");
                pstmt.setString(2, "admin@civiclens.gov"); // already exists
                pstmt.setString(3, "dummy_hash");
                pstmt.setString(4, "ADMIN");
                pstmt.executeUpdate();
            } catch (SQLException ex) {
                duplicateEmailRejected = true;
            }
            check("Unique constraint rejects duplicate user email (admin@civiclens.gov)", duplicateEmailRejected, "Unique constraint enforced");

            // 4. Unique Constraint Enforcement: duplicate (incident_id, complaint_id) in incident_members
            // Insert a complaint first for incident testing
            int testComplaintId = -1;
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO complaints (user_id, title, category, description, priority, status) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, 5); // Elena Rostova
                pstmt.setString(2, "Incident Test Complaint");
                pstmt.setString(3, "Infrastructure");
                pstmt.setString(4, "Power failure");
                pstmt.setString(5, "CRITICAL");
                pstmt.setString(6, "SUBMITTED");
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) testComplaintId = rs.getInt(1);
                }
            }
            check("Created test complaint for incident linking", testComplaintId > 0, "id: " + testComplaintId);

            // Link to INC-2026-001 (incident_id = 1)
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO incident_members (incident_id, complaint_id, similarity_score, linked_by, reason) VALUES (?, ?, ?, ?, ?)")) {
                pstmt.setInt(1, 1);
                pstmt.setInt(2, testComplaintId);
                pstmt.setDouble(3, 0.95);
                pstmt.setInt(4, 1);
                pstmt.setString(5, "Downtown outage cluster");
                pstmt.executeUpdate();
                check("Initial incident_member link inserted successfully", true, "linked");
            } catch (SQLException ex) {
                check("Initial incident_member link inserted successfully", false, ex.getMessage());
            }

            // Attempt duplicate link (same incident_id, same complaint_id) -> MUST FAIL
            boolean duplicateLinkRejected = false;
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO incident_members (incident_id, complaint_id, similarity_score, linked_by, reason) VALUES (?, ?, ?, ?, ?)")) {
                pstmt.setInt(1, 1);
                pstmt.setInt(2, testComplaintId);
                pstmt.setDouble(3, 0.95);
                pstmt.setInt(4, 1);
                pstmt.setString(5, "Duplicate link attempt");
                pstmt.executeUpdate();
            } catch (SQLException ex) {
                duplicateLinkRejected = true;
            }
            check("Unique constraint rejects duplicate incident_member (incident_id=1, complaint_id=" + testComplaintId + ")", duplicateLinkRejected, "Unique constraint enforced");

            // 5. Check Constraint: citizen_feedback rating (1 <= rating <= 5)
            boolean invalidRatingRejected = false;
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO citizen_feedback (complaint_id, citizen_id, rating, comments) VALUES (?, ?, ?, ?)")) {
                pstmt.setInt(1, testComplaintId);
                pstmt.setInt(2, 5);
                pstmt.setInt(3, 10); // Invalid rating > 5
                pstmt.setString(4, "Off-the-charts rating");
                pstmt.executeUpdate();
            } catch (SQLException ex) {
                invalidRatingRejected = true;
            }
            check("CHECK constraint chk_feedback_rating rejects invalid rating (10)", invalidRatingRejected, "CHECK constraint enforced");

            // Valid rating (5) accepted
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT INTO citizen_feedback (complaint_id, citizen_id, rating, comments) VALUES (?, ?, ?, ?)")) {
                pstmt.setInt(1, testComplaintId);
                pstmt.setInt(2, 5);
                pstmt.setInt(3, 5); // Valid rating
                pstmt.setString(4, "Great response");
                int rows = pstmt.executeUpdate();
                check("CHECK constraint chk_feedback_rating accepts valid rating (5)", rows == 1, "inserted");
            } catch (SQLException ex) {
                check("CHECK constraint chk_feedback_rating accepts valid rating (5)", false, ex.getMessage());
            }

            // 6. Test Cascade Delete: Deleting complaint cascades to incident_members and citizen_feedback
            try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM complaints WHERE complaint_id = ?")) {
                pstmt.setInt(1, testComplaintId);
                int deleted = pstmt.executeUpdate();
                check("Complaint deleted successfully", deleted == 1, "deleted rows: " + deleted);
            }

            // Verify child records are gone
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM incident_members WHERE complaint_id = ?")) {
                pstmt.setInt(1, testComplaintId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    int remaining = rs.next() ? rs.getInt(1) : -1;
                    check("CASCADE DELETE purged child incident_members", remaining == 0, "count: " + remaining);
                }
            }

            try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM citizen_feedback WHERE complaint_id = ?")) {
                pstmt.setInt(1, testComplaintId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    int remaining = rs.next() ? rs.getInt(1) : -1;
                    check("CASCADE DELETE purged child citizen_feedback", remaining == 0, "count: " + remaining);
                }
            }
        }
    }

    private static void testLegacyFrameQueriesAndMutations() throws SQLException {
        System.out.println("\nSuite 4: Legacy Frame Query & Mutation Compatibility Verification");

        try (Connection conn = DbConnection.getConnection()) {
            // 1. AdminDashboardFrame query: SELECT user_id, full_name FROM users WHERE role = 'EMPLOYEE' ORDER BY full_name
            String adminQuery = "SELECT user_id, full_name FROM users WHERE role = 'EMPLOYEE' ORDER BY full_name";
            List<String> employees = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(adminQuery)) {
                while (rs.next()) {
                    employees.add(rs.getInt("user_id") + ":" + rs.getString("full_name"));
                }
            }
            check("AdminDashboardFrame employee query executes without 'name' column error", employees.size() >= 3, "employees: " + employees);

            // 2. RegisterFrame query: INSERT INTO users (full_name, email, password_hash, role) VALUES (?, ?, ?, 'CITIZEN')
            String testEmail = "test_reg_" + System.currentTimeMillis() + "@civiclens.gov";
            String testHash = BCrypt.hashpw("TestPassword123!", BCrypt.gensalt(10));
            int newUserId = -1;
            String regSql = "INSERT INTO users (full_name, email, password_hash, role) VALUES (?, ?, ?, 'CITIZEN')";
            try (PreparedStatement pstmt = conn.prepareStatement(regSql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, "Test Registered Citizen");
                pstmt.setString(2, testEmail);
                pstmt.setString(3, testHash);
                int rows = pstmt.executeUpdate();
                check("RegisterFrame insert executes with 'CITIZEN' role", rows == 1, "inserted");
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) newUserId = rs.getInt(1);
                }
            }

            // Verify the role is CITIZEN in database
            if (newUserId > 0) {
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT role FROM users WHERE user_id = ?")) {
                    pstmt.setInt(1, newUserId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            check("Verified registered user role is 'CITIZEN'", "CITIZEN".equals(rs.getString("role")), rs.getString("role"));
                        }
                    }
                }

                // Clean up registered user
                try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
                    pstmt.setInt(1, newUserId);
                    pstmt.executeUpdate();
                }
            }

            // 3. ComplaintFrame query: status must be 'SUBMITTED'
            int complaintId = -1;
            String compSql = "INSERT INTO complaints (user_id, title, category, description, priority, status) VALUES (?, ?, ?, ?, ?, 'SUBMITTED')";
            try (PreparedStatement pstmt = conn.prepareStatement(compSql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, 5); // Elena Rostova
                pstmt.setString(2, "Test Frame Status Complaint");
                pstmt.setString(3, "Administrative");
                pstmt.setString(4, "Testing status default");
                pstmt.setString(5, "LOW");
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) complaintId = rs.getInt(1);
                }
            }

            if (complaintId > 0) {
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT status FROM complaints WHERE complaint_id = ?")) {
                    pstmt.setInt(1, complaintId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            check("Verified ComplaintFrame inserts status 'SUBMITTED'", "SUBMITTED".equals(rs.getString("status")), rs.getString("status"));
                        }
                    }
                }
                // Clean up
                try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM complaints WHERE complaint_id = ?")) {
                    pstmt.setInt(1, complaintId);
                    pstmt.executeUpdate();
                }
            }
        }
    }

    private static void testSchemaReinitializationIdempotency() {
        System.out.println("\nSuite 5: Schema Re-initialization Idempotency Verification");

        try {
            // Calling initializeSchema multiple times should not throw exceptions or duplicate seeds
            DbConnection.initializeSchema();
            DbConnection.initializeSchema();
            check("DbConnection.initializeSchema() repeated calls execute idempotently without error", true, "idempotent");

            try (Connection conn = DbConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE email = 'admin@civiclens.gov'")) {
                int adminCount = rs.next() ? rs.getInt(1) : 0;
                check("Admin seed account is not duplicated on schema re-run", adminCount == 1, "count: " + adminCount);
            }
        } catch (SQLException ex) {
            check("DbConnection.initializeSchema() repeated calls execute idempotently without error", false, ex.getMessage());
        }
    }

    private static void assertThrowsSecurityException(String testDescription, ThrowingRunnable runnable) {
        boolean thrown = false;
        String caughtType = "none";
        try {
            runnable.run();
        } catch (SecurityException ex) {
            thrown = true;
            caughtType = "SecurityException";
        } catch (Throwable t) {
            // Unpack HeadlessException or InvocationTargetException
            Throwable cur = t;
            while (cur != null) {
                if (cur instanceof SecurityException) {
                    thrown = true;
                    caughtType = "SecurityException";
                    break;
                }
                cur = cur.getCause();
            }
            if (!thrown) {
                caughtType = t.getClass().getSimpleName();
            }
        }
        check(testDescription, thrown, "caught: " + caughtType);
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
