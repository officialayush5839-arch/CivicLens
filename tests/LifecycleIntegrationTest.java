// Default package test suite for CivicLens Lifecycle & Resolution
import civiclens.audit.AuditTrailService;
import civiclens.audit.AuditTrailService.AuditEntry;
import civiclens.lifecycle.ComplaintState;
import civiclens.lifecycle.LifecycleStateMachine;
import civiclens.lifecycle.ResolutionService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * End-to-end integration and verification suite for CivicLens Lifecycle,
 * Resolution records, Citizen Feedback, authenticated Appeals, and Audit trails.
 */
public class LifecycleIntegrationTest {

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
        System.out.println("CivicLens Lifecycle, Resolution, Appeal & Audit Test Suite");
        System.out.println("==================================================================\n");

        try (Connection conn = DbConnection.getConnection()) {

            int citizenOwner = 5; // Elena Rostova (Citizen)
            int citizenAttacker = 6; // Jane Citizen (Different citizen)
            int employee = 2; // Sarah Jenkins (Employee)
            int admin = 1; // System Administrator

            System.out.println("--- Test 1: Legal Lifecycle Flow (SUBMITTED -> ASSIGNED -> IN_PROGRESS -> RESOLVED) ---");
            int complaintId = -1;
            String insertComp = "INSERT INTO complaints (user_id, title, category, description, priority, status) " +
                                "VALUES (?, 'Flickering Streetlight Near Park', 'Infrastructure', 'Lamp posts on 4th ave flicker', 'MEDIUM', 'SUBMITTED')";
            try (PreparedStatement ps = conn.prepareStatement(insertComp, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, citizenOwner);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) complaintId = rs.getInt(1);
                }
            }
            assertTrue("Created initial complaint in SUBMITTED state", complaintId > 0);

            // Step 1: Assign to employee
            boolean assigned = ResolutionService.assignComplaint(conn, complaintId, employee, admin);
            assertTrue("Admin successfully assigned complaint to employee", assigned);

            // Step 2: Employee starts work
            boolean workStarted = ResolutionService.startWork(conn, complaintId, employee);
            assertTrue("Employee successfully commenced work (IN_PROGRESS)", workStarted);

            // Step 3: Employee resolves complaint
            boolean resolved = ResolutionService.resolveComplaint(
                    conn,
                    complaintId,
                    employee,
                    "Replaced faulty ballast capacitor and tested voltage output across lamp post circuit.",
                    "Repaired Component",
                    "Photo of replaced ballast uploaded to depot records",
                    "Circuit inspected; no feeder faults found"
            );
            assertTrue("Employee resolved complaint with structured evidence record", resolved);

            System.out.println("\n--- Test 2: Complainant Feedback & Ownership Security ---");
            // Citizen owner submits 5-star rating
            boolean feedbackSubmitted = ResolutionService.submitFeedback(
                    conn,
                    complaintId,
                    citizenOwner,
                    5,
                    "Great work, streetlight is bright and steady again!"
            );
            assertTrue("Citizen owner submitted feedback rating", feedbackSubmitted);

            // Cross-user tampering: Citizen B attempts to submit feedback for Citizen A's complaint
            boolean unauthorizedFeedbackBlocked = false;
            try {
                ResolutionService.submitFeedback(conn, complaintId, citizenAttacker, 1, "Malicious feedback attempt");
            } catch (SecurityException se) {
                unauthorizedFeedbackBlocked = true;
            }
            assertTrue("Cross-citizen feedback submission rejected with SecurityException", unauthorizedFeedbackBlocked);

            System.out.println("\n--- Test 3: Authenticated Citizen Appeal Workflow ---");
            // Cross-user appeal attempt blocked
            boolean unauthorizedAppealBlocked = false;
            try {
                ResolutionService.submitAppeal(conn, complaintId, citizenAttacker, "Unauthorized appeal attempt by other citizen");
            } catch (SecurityException se) {
                unauthorizedAppealBlocked = true;
            }
            assertTrue("Cross-citizen appeal attempt rejected with SecurityException", unauthorizedAppealBlocked);

            // Citizen owner submits appeal
            int appealId = ResolutionService.submitAppeal(
                    conn,
                    complaintId,
                    citizenOwner,
                    "The lamp flickered again when the evening wind picked up. Needs structural wire check."
            );
            assertTrue("Citizen owner successfully submitted formal appeal", appealId > 0);

            // Admin reviews and accepts appeal (reopening complaint to IN_PROGRESS)
            boolean appealApproved = ResolutionService.reviewAppeal(
                    conn,
                    appealId,
                    admin,
                    true,
                    "Approved for field re-inspection: check wire connections in pole housing."
            );
            assertTrue("Admin approved appeal and reopened complaint to IN_PROGRESS", appealApproved);

            // Second resolution after remediation
            boolean reResolved = ResolutionService.resolveComplaint(
                    conn,
                    complaintId,
                    employee,
                    "Tightened terminal block lugs and weather-sealed access hatch.",
                    "Secondary Remediation",
                    "Thermal scan confirmed 0mV drop",
                    null
            );
            assertTrue("Employee successfully re-resolved complaint after appeal", reResolved);

            // Formal closure
            boolean closed = ResolutionService.closeComplaint(conn, complaintId, admin, "ADMIN");
            assertTrue("Complaint successfully transitioned to CLOSED terminal state", closed);

            System.out.println("\n--- Test 4: Forbidden Lifecycle State Jumps ---");
            // Closed cannot transition to Submitted or In Progress
            boolean illegalJumpBlocked = false;
            try {
                LifecycleStateMachine.validateTransition(ComplaintState.CLOSED, ComplaintState.IN_PROGRESS);
            } catch (IllegalStateException ise) {
                illegalJumpBlocked = true;
            }
            assertTrue("Prevented invalid state jump from CLOSED to IN_PROGRESS", illegalJumpBlocked);

            // Direct jump from SUBMITTED to RESOLVED without assignment
            boolean directJumpBlocked = false;
            try {
                LifecycleStateMachine.validateTransition(ComplaintState.SUBMITTED, ComplaintState.RESOLVED);
            } catch (IllegalStateException ise) {
                directJumpBlocked = true;
            }
            assertTrue("Prevented illegal direct jump from SUBMITTED to RESOLVED", directJumpBlocked);

            System.out.println("\n--- Test 5: Comprehensive Append-Only Audit Trail Inspection ---");
            List<AuditEntry> auditList = AuditTrailService.getAuditHistory(conn, complaintId);
            assertTrue("Audit log recorded multiple chronological entries", auditList.size() >= 5);

            System.out.println("Recorded Audit History for Complaint #" + complaintId + ":");
            for (AuditEntry entry : auditList) {
                System.out.printf("  [%s] Action: %-25s | State: %-11s -> %-11s | Actor: %s (%s)%n",
                        entry.createdAt, entry.action, entry.previousState, entry.newState, entry.actorName, entry.actorRole);
            }

            assertTrue("First audit action was COMPLAINT_ASSIGNED", auditList.get(0).action.equals("COMPLAINT_ASSIGNED"));
            assertTrue("Second audit action was WORK_STARTED", auditList.get(1).action.equals("WORK_STARTED"));
            assertTrue("Third audit action was COMPLAINT_RESOLVED", auditList.get(2).action.equals("COMPLAINT_RESOLVED"));
        }

        System.out.println("\n==================================================================");
        System.out.printf("Results: %d passed, %d failed (Total: %d)%n", testsPassed, testsFailed, testsRun);
        System.out.println("==================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }
}
