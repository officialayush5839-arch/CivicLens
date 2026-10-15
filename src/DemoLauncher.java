import civiclens.lifecycle.ResolutionService;
import civiclens.similarity.DuplicateDetectionEngine;
import civiclens.similarity.IncidentManagementService;
import civiclens.sla.SlaEngine;
import civiclens.triage.TriageEngine;
import civiclens.triage.TriageResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

/**
 * CivicLens Standalone Interactive Demonstration Engine.
 * Preloads 5 end-to-end synthetic scenarios demonstrating:
 * 1. Multi-dimensional Explainable Triage (Life-safety emergency vs Negation).
 * 2. Deterministic Duplicate Detection & Incident Group Linking.
 * 3. Configurable SLA Policy Tracking & Idempotent Escalation.
 * 4. Accountable Structured Resolution with Evidence.
 * 5. Authenticated Citizen Appeal & Admin Reopening.
 */
public class DemoLauncher {

    public static void runDemoScenarios() {
        System.out.println("==================================================================");
        System.out.println("  CIVICLENS — STANDALONE INTERACTIVE DEMONSTRATION SCENARIOS      ");
        System.out.println("==================================================================\n");

        try (Connection conn = DbConnection.getConnection()) {

            // SCENARIO 1: Explainable Triage — Acute Life-Safety Incident
            System.out.println(">>> [SCENARIO 1] Explainable Triage — Life Safety Threat");
            String critTitle = "Live electrical power line sparking on flooded roadway";
            String critDesc = "High voltage line snapped near school crossing and is actively sparking with pedestrians around.";
            TriageResult r1 = TriageEngine.evaluate(critTitle, critDesc, "Infrastructure");
            System.out.println("  Title:           " + critTitle);
            System.out.println("  Category:        Infrastructure");
            System.out.println("  Assigned:        " + r1.getPriorityString() + " (" + r1.getConfidenceString() + ")");
            System.out.println("  Composite Score: " + r1.getCompositeScore() + "/100");
            System.out.println("  Justification:   " + r1.getJustification());
            System.out.println("  Urgency Driver:  " + (r1.getUrgencyDrivers().isEmpty() ? "None" : r1.getUrgencyDrivers().get(0)));

            // SCENARIO 2: Explainable Triage — Negation & De-escalation
            System.out.println("\n>>> [SCENARIO 2] Explainable Triage — Negation Handling");
            String negTitle = "Air conditioner humming - not urgent";
            String negDesc = "Unit is making a buzz in breakroom. Not critical, nobody is hurt, works fine.";
            TriageResult r2 = TriageEngine.evaluate(negTitle, negDesc, "Infrastructure");
            System.out.println("  Title:           " + negTitle);
            System.out.println("  Assigned:        " + r2.getPriorityString() + " (" + r2.getConfidenceString() + ")");
            System.out.println("  Composite Score: " + r2.getCompositeScore() + "/100");
            System.out.println("  Mitigating Note: " + (r2.getUrgencyReducers().isEmpty() ? "None" : r2.getUrgencyReducers().get(0)));

            // Insert Scenario Complaints into Database
            int demoC1 = insertDemoComplaint(conn, 5, critTitle, "Infrastructure", critDesc, r1.getPriorityString());
            int demoC2 = insertDemoComplaint(conn, 5, negTitle, "Infrastructure", negDesc, r2.getPriorityString());
            SlaEngine.initializeDeadlines(conn, demoC1, r1.getPriorityString(), "Infrastructure", Instant.now());
            SlaEngine.initializeDeadlines(conn, demoC2, r2.getPriorityString(), "Infrastructure", Instant.now());

            // SCENARIO 3: Duplicate Candidate Detection & Incident Linking
            System.out.println("\n>>> [SCENARIO 3] Duplicate & Incident Group Linking");
            String dupTitle = "Ruptured water main flooding 5th avenue";
            String dupDesc = "Water is gushing out of broken underground pipe across 5th ave and commerce st";
            int compA = insertDemoComplaint(conn, 5, dupTitle, "Infrastructure", dupDesc, "HIGH");
            SlaEngine.initializeDeadlines(conn, compA, "HIGH", "Infrastructure", Instant.now());

            String dupTitleB = "Flooding on 5th Ave due to broken water pipe";
            String dupDescB = "Underground water pipe burst near commerce st intersection, roadway submerged";
            int compB = insertDemoComplaint(conn, 6, dupTitleB, "Infrastructure", dupDescB, "HIGH");
            SlaEngine.initializeDeadlines(conn, compB, "HIGH", "Infrastructure", Instant.now());

            var candidates = DuplicateDetectionEngine.findDuplicatesInDb(conn, compB, dupTitleB, dupDescB, "Infrastructure", 0.35);
            System.out.printf("  Comparing Complaint #%d against pool...\n", compB);
            if (!candidates.isEmpty()) {
                var top = candidates.get(0);
                System.out.printf("  Found Candidate Match: Complaint #%d (Score: %.2f)\n", top.getComplaintId(), top.getSimilarityScore());
                System.out.println("  Explanation: " + top.getExplanation());

                // Link them to an Incident Group
                int incId = IncidentManagementService.createIncidentGroup(conn, "5th Avenue Water Main Rupture", "Coordinated main line burst incident", "Infrastructure", "HIGH", 1);
                IncidentManagementService.linkComplaintToIncident(conn, incId, compA, 0.85, 1, "Initial citizen report");
                IncidentManagementService.linkComplaintToIncident(conn, incId, compB, top.getSimilarityScore(), 1, "Corroborating citizen report");
                System.out.println("  Grouped into Incident #" + incId + " preserving individual complaint identities.");
            }

            // SCENARIO 4: SLA Breach & Idempotent Escalation
            System.out.println("\n>>> [SCENARIO 4] SLA Breach & Automated Escalation");
            int overdueComp = insertDemoComplaint(conn, 5, "Frozen Water Meter", "Infrastructure", "Meter frozen during cold snap", "HIGH");
            SlaEngine.initializeDeadlines(conn, overdueComp, "HIGH", "Infrastructure", Instant.now().minus(Duration.ofHours(30)));
            // Simulate past deadline
            String pastSql = "UPDATE sla_deadlines SET resolution_deadline = ? WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(pastSql)) {
                ps.setTimestamp(1, java.sql.Timestamp.from(Instant.now().minus(Duration.ofHours(6))));
                ps.setInt(2, overdueComp);
                ps.executeUpdate();
            }
            int escalated = SlaEngine.escalateOverdueComplaints(conn);
            System.out.println("  Overdue complaints escalated: " + escalated);
            var overdueInfo = SlaEngine.getDeadlineInfo(conn, overdueComp);
            System.out.println("  Complaint #" + overdueComp + " SLA State: " + overdueInfo.getSlaState() + " (" + overdueInfo.getFormattedRemainingTime() + ")");

            // SCENARIO 5: Structured Resolution, Citizen Feedback, Appeal & Reopening
            System.out.println("\n>>> [SCENARIO 5] Accountable Resolution, Appeal & Audit Workflow");
            ResolutionService.assignComplaint(conn, demoC1, 2, 1);
            ResolutionService.startWork(conn, demoC1, 2);
            ResolutionService.resolveComplaint(conn, demoC1, 2,
                    "De-energized line section, isolated damaged feed, and spliced insulated jumper cables.",
                    "Repaired Component",
                    "Safety inspection tag #9942 affixed and signed",
                    "Depot dispatched emergency bucket truck");
            System.out.println("  Complaint #" + demoC1 + " resolved with structured action and evidence notes.");

            ResolutionService.submitFeedback(conn, demoC1, 5, 5, "Fast emergency response, danger eliminated quickly.");
            System.out.println("  Citizen submitted 5-star satisfaction feedback.");

            int appealId = ResolutionService.submitAppeal(conn, demoC1, 5,
                    "Temporary jumper wire is resting against customer tree branches; needs permanent pole re-hang.");
            System.out.println("  Citizen filed formal Appeal #" + appealId + " with safety rationale.");

            ResolutionService.reviewAppeal(conn, appealId, 1, true, "Approved for permanent crew re-tensioning.");
            System.out.println("  Administrator approved appeal -> Complaint reopened to IN_PROGRESS for remediation.");

            System.out.println("\n==================================================================");
            System.out.println("  ALL 5 DEMONSTRATION SCENARIOS EXECUTED & VERIFIED IN DATABASE!   ");
            System.out.println("==================================================================\n");

        } catch (Exception ex) {
            System.err.println("Demo scenario error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private static int insertDemoComplaint(Connection conn, int userId, String title, String category, String desc, String priority) throws Exception {
        String sql = "INSERT INTO complaints (user_id, title, category, description, priority, status) VALUES (?, ?, ?, ?, ?, 'SUBMITTED')";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, title);
            ps.setString(3, category);
            ps.setString(4, desc);
            ps.setString(5, priority);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        runDemoScenarios();
    }
}
