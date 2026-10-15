// Default package test suite for CivicLens SLA Engine
import civiclens.sla.SlaDeadlineInfo;
import civiclens.sla.SlaEngine;
import civiclens.sla.SlaPolicy;
import civiclens.sla.SlaStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Integration and Verification Suite for SlaEngine.
 * Tests configurable policies, deterministic state calculations,
 * idempotent escalation, and downtime reconciliation.
 */
public class SlaEngineIntegrationTest {

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

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================================");
        System.out.println("CivicLens SLA Engine & Escalation Verification Suite");
        System.out.println("==================================================================\n");

        try (Connection conn = DbConnection.getConnection()) {

            System.out.println("--- Test 1: Configurable SLA Policy Hierarchy ---");
            SlaPolicy infraCrit = SlaEngine.getPolicy(conn, "CRITICAL", "Infrastructure");
            assertTrue("Infrastructure CRITICAL policy resolution is 2 hours", infraCrit.getResolutionTimeHours() == 2);
            assertTrue("Infrastructure CRITICAL policy response is 1 hour", infraCrit.getResponseTimeHours() == 1);

            SlaPolicy generalCrit = SlaEngine.getPolicy(conn, "CRITICAL", "UnlistedCategory");
            assertTrue("General wildcard CRITICAL resolution is 4 hours", generalCrit.getResolutionTimeHours() == 4);

            SlaPolicy generalLow = SlaEngine.getPolicy(conn, "LOW", "Administrative");
            assertTrue("Administrative LOW resolution is 240 hours", generalLow.getResolutionTimeHours() == 240);

            System.out.println("\n--- Test 2: Deadline Initialization and Deterministic State ---");
            int testCompId = -1;
            String insertComp = "INSERT INTO complaints (user_id, title, category, description, priority, status) " +
                                "VALUES (5, 'Streetlight Pole Broken', 'Infrastructure', 'Pole collapsed on curb', 'HIGH', 'SUBMITTED')";
            try (PreparedStatement ps = conn.prepareStatement(insertComp, Statement.RETURN_GENERATED_KEYS)) {
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) testCompId = rs.getInt(1);
                }
            }
            assertTrue("Created test complaint for SLA testing", testCompId > 0);

            Instant now = Instant.now();
            SlaEngine.initializeDeadlines(conn, testCompId, "HIGH", "Infrastructure", now);

            SlaDeadlineInfo info = SlaEngine.getDeadlineInfo(conn, testCompId);
            assertTrue("Deadline info retrieved", info != null);
            assertTrue("Initial SLA state is ON_TRACK", info.getSlaState() == SlaStatus.ON_TRACK);
            assertTrue("Not escalated initially", !info.isEscalated());

            System.out.println("\n--- Test 3: Idempotent Escalation of Breached Complaints ---");
            // Set resolution_deadline to 2 hours in the past to simulate an overdue complaint
            Instant pastDeadline = now.minus(Duration.ofHours(2));
            String updateDeadlineSql = "UPDATE sla_deadlines SET resolution_deadline = ?, is_escalated = 0, sla_state = 'ON_TRACK' WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateDeadlineSql)) {
                ps.setTimestamp(1, Timestamp.from(pastDeadline));
                ps.setInt(2, testCompId);
                ps.executeUpdate();
            }

            // Verify state is recognized as OVERDUE before escalation trigger
            SlaDeadlineInfo overdueInfo = SlaEngine.getDeadlineInfo(conn, testCompId);
            assertTrue("Expired complaint identified as OVERDUE", overdueInfo.getSlaState() == SlaStatus.OVERDUE);

            // Trigger escalation scanner
            int escalatedCount = SlaEngine.escalateOverdueComplaints(conn);
            assertTrue("First escalation pass escalated breached complaint", escalatedCount >= 1);

            // Re-fetch info
            SlaDeadlineInfo postEscalation = SlaEngine.getDeadlineInfo(conn, testCompId);
            assertTrue("Complaint state transitioned to ESCALATED", postEscalation.getSlaState() == SlaStatus.ESCALATED);
            assertTrue("Complaint marked is_escalated = true", postEscalation.isEscalated());
            assertTrue("EscalatedAt timestamp is recorded", postEscalation.getEscalatedAt() != null);

            // Verify audit log entry
            String checkAudit = "SELECT COUNT(*) FROM complaint_audit_log WHERE complaint_id = ? AND action = 'SLA_BREACH_ESCALATED'";
            int auditEntries = 0;
            try (PreparedStatement ps = conn.prepareStatement(checkAudit)) {
                ps.setInt(1, testCompId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) auditEntries = rs.getInt(1);
                }
            }
            assertTrue("Audit log recorded exactly 1 escalation event", auditEntries == 1);

            // Trigger escalation scanner a SECOND time (Idempotency test)
            int secondPassCount = SlaEngine.escalateOverdueComplaints(conn);
            assertTrue("Second escalation pass returned 0 (Idempotent - no repeated triggers)", secondPassCount == 0);

            // Verify audit entries did NOT duplicate
            try (PreparedStatement ps = conn.prepareStatement(checkAudit)) {
                ps.setInt(1, testCompId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) auditEntries = rs.getInt(1);
                }
            }
            assertTrue("Audit log did NOT duplicate on second run", auditEntries == 1);

            System.out.println("\n--- Test 4: Application Downtime Reconciliation ---");
            // Reset complaint to simulate expired while system was shut down
            String resetSql = "UPDATE sla_deadlines SET is_escalated = 0, sla_state = 'ON_TRACK', resolution_deadline = ? WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(resetSql)) {
                ps.setTimestamp(1, Timestamp.from(pastDeadline));
                ps.setInt(2, testCompId);
                ps.executeUpdate();
            }

            int reconciled = SlaEngine.reconcileDowntime(conn);
            assertTrue("Downtime reconciliation picked up missed deadline", reconciled >= 1);

            String checkDowntimeAudit = "SELECT COUNT(*) FROM complaint_audit_log WHERE complaint_id = ? AND action = 'DOWNTIME_SLA_RECONCILIATION'";
            int downtimeAudits = 0;
            try (PreparedStatement ps = conn.prepareStatement(checkDowntimeAudit)) {
                ps.setInt(1, testCompId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) downtimeAudits = rs.getInt(1);
                }
            }
            assertTrue("Downtime reconciliation logged explicit recovery audit event", downtimeAudits >= 1);

            System.out.println("\n--- Test 5: Completed Complaint State Transition ---");
            String resolveCompSql = "UPDATE complaints SET status = 'RESOLVED' WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(resolveCompSql)) {
                ps.setInt(1, testCompId);
                ps.executeUpdate();
            }

            SlaDeadlineInfo resolvedInfo = SlaEngine.getDeadlineInfo(conn, testCompId);
            assertTrue("Resolved complaint evaluates to COMPLETED status", resolvedInfo.getSlaState() == SlaStatus.COMPLETED);
        }

        System.out.println("\n==================================================================");
        System.out.printf("Results: %d passed, %d failed (Total: %d)%n", testsPassed, testsFailed, testsRun);
        System.out.println("==================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }
}
