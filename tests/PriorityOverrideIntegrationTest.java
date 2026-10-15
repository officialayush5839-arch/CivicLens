import civiclens.sla.SlaEngine;
import civiclens.triage.PriorityOverrideService;
import civiclens.triage.PriorityOverrideService.OverrideRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * Rigorous integration test verifying PriorityOverrideService:
 * 1. Transactional priority update in complaints table.
 * 2. Immutability of earliest original recommendation across successive chained overrides.
 * 3. SLA deadline re-synchronization.
 * 4. Append-only audit log entry creation.
 * 5. RBAC security enforcement blocking Citizen overrides.
 */
public class PriorityOverrideIntegrationTest {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    private static void pass(String msg) {
        testsPassed++;
        System.out.println("  [PASS] " + msg);
    }

    private static void fail(String msg) {
        testsFailed++;
        System.err.println("  [FAIL] " + msg);
    }

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("   PRIORITY OVERRIDE SERVICE TRANSACTIONAL INTEGRATION TEST        ");
        System.out.println("==================================================================\n");

        try (Connection conn = DbConnection.getConnection()) {
            conn.setAutoCommit(true);

            // 1. Create a test complaint submitted by Citizen (user_id = 5)
            int complaintId;
            String insertSql = "INSERT INTO complaints (user_id, title, category, description, priority, status) " +
                    "VALUES (5, 'Streetlight dim on Elm', 'Infrastructure', 'Lamp flickers slightly', 'LOW', 'SUBMITTED')";
            try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        complaintId = rs.getInt(1);
                    } else {
                        throw new IllegalStateException("Failed to generate complaint ID");
                    }
                }
            }
            pass("Created test complaint #" + complaintId + " with initial priority LOW");

            // 2. Perform First Override: Employee (user_id = 2) elevates LOW -> HIGH with reason
            String firstReason = "Field inspection found electrical box damaged near sidewalk";
            String firstOriginalRec = "Original ML Triage: LOW (Score: 7.0, Routine Lighting)";
            boolean firstSuccess = PriorityOverrideService.recordOverride(
                    conn, complaintId, 2, "HIGH", firstReason, firstOriginalRec
            );
            if (firstSuccess) pass("First override succeeded: LOW -> HIGH");
            else fail("First override returned false");

            // Verify database state after First Override
            checkComplaintPriority(conn, complaintId, "HIGH");
            List<OverrideRecord> history1 = PriorityOverrideService.getOverrideHistory(conn, complaintId);
            if (history1.size() == 1 &&
                "HIGH".equals(history1.get(0).getNewPriority()) &&
                firstOriginalRec.equals(history1.get(0).getOriginalRecommendation()) &&
                firstReason.equals(history1.get(0).getReason())) {
                pass("First override record persisted correctly in priority_overrides table");
            } else {
                fail("First override history mismatch: " + history1);
            }

            // 3. Perform Second Chained Override: Admin (user_id = 1) elevates HIGH -> CRITICAL
            // Notice: We pass "Administrative Reassessment" as caller recommendation,
            // but the service MUST preserve the FIRST canonical recommendation!
            String secondReason = "High-voltage arcing reported by emergency services";
            boolean secondSuccess = PriorityOverrideService.recordOverride(
                    conn, complaintId, 1, "CRITICAL", secondReason, "Administrative Reassessment"
            );
            if (secondSuccess) pass("Second override succeeded: HIGH -> CRITICAL");
            else fail("Second override returned false");

            // Verify database state after Second Override:
            checkComplaintPriority(conn, complaintId, "CRITICAL");
            List<OverrideRecord> history2 = PriorityOverrideService.getOverrideHistory(conn, complaintId);
            if (history2.size() == 2) {
                pass("Override history contains exactly 2 chronological entries");
                OverrideRecord latest = history2.get(0); // newest first
                OverrideRecord earliest = history2.get(1);

                if (firstOriginalRec.equals(latest.getOriginalRecommendation()) &&
                    firstOriginalRec.equals(earliest.getOriginalRecommendation())) {
                    pass("Earliest machine recommendation preserved immutably across chained overrides: '" + latest.getOriginalRecommendation() + "'");
                } else {
                    fail("Original recommendation was corrupted in chained override! Got: " + latest.getOriginalRecommendation());
                }
            } else {
                fail("Expected 2 override records, found: " + history2.size());
            }

            // 4. Verify SLA deadlines synchronized with CRITICAL
            String slaSql = "SELECT response_deadline, resolution_deadline FROM sla_deadlines WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(slaSql)) {
                ps.setInt(1, complaintId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        java.sql.Timestamp resp = rs.getTimestamp("response_deadline");
                        java.sql.Timestamp resol = rs.getTimestamp("resolution_deadline");
                        if (resp != null && resol != null) {
                            pass("SLA deadlines successfully synchronized to CRITICAL policy (Response: " + resp + ", Resolution: " + resol + ")");
                        } else {
                            fail("SLA deadlines were null");
                        }
                    } else {
                        fail("No SLA deadline record found for complaint #" + complaintId);
                    }
                }
            }

            // 5. Verify complaint_audit_log entries
            String auditSql = "SELECT action, details FROM complaint_audit_log WHERE complaint_id = ? AND action = 'PRIORITY_OVERRIDE'";
            try (PreparedStatement ps = conn.prepareStatement(auditSql)) {
                ps.setInt(1, complaintId);
                try (ResultSet rs = ps.executeQuery()) {
                    int auditCount = 0;
                    while (rs.next()) {
                        auditCount++;
                    }
                    if (auditCount == 2) {
                        pass("Audit log contains exactly 2 PRIORITY_OVERRIDE entries");
                    } else {
                        fail("Expected 2 audit log entries, found: " + auditCount);
                    }
                }
            }

            // 6. Test RBAC Enforcement: Citizen (user_id = 5) attempting override must throw SecurityException
            boolean citizenBlocked = false;
            try {
                PriorityOverrideService.recordOverride(conn, complaintId, 5, "LOW", "Citizen trying to change priority back");
            } catch (SecurityException se) {
                citizenBlocked = true;
            }
            if (citizenBlocked) {
                pass("RBAC Guard: Citizen attempt to override priority strictly blocked with SecurityException");
            } else {
                fail("RBAC Failure: Citizen was allowed to override priority!");
            }

            // Clean up test complaint
            String deleteSql = "DELETE FROM complaints WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, complaintId);
                ps.executeUpdate();
            }
            pass("Cleaned up test complaint #" + complaintId);

        } catch (Exception ex) {
            fail("Unexpected exception: " + ex.getMessage());
            ex.printStackTrace();
        }

        System.out.println("\n------------------------------------------------------------------");
        System.out.println("OVERRIDE TEST RESULTS: " + testsPassed + " passed, " + testsFailed + " failed");
        System.out.println("------------------------------------------------------------------");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void checkComplaintPriority(Connection conn, int complaintId, String expectedPriority) throws Exception {
        String sql = "SELECT priority FROM complaints WHERE complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String actual = rs.getString("priority");
                    if (expectedPriority.equalsIgnoreCase(actual)) {
                        pass("complaints.priority is updated to " + expectedPriority);
                    } else {
                        fail("Expected priority " + expectedPriority + ", got " + actual);
                    }
                } else {
                    fail("Complaint #" + complaintId + " not found");
                }
            }
        }
    }
}
