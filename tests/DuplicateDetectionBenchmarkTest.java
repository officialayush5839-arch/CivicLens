// Default package test suite for CivicLens Duplicate Detection
import civiclens.similarity.DuplicateCandidate;
import civiclens.similarity.DuplicateDetectionEngine;
import civiclens.similarity.DuplicateDetectionEngine.ComplaintDocument;
import civiclens.similarity.IncidentManagementService;
import civiclens.similarity.IncidentManagementService.IncidentMember;

import java.sql.Connection;
import java.time.Instant;
import java.util.*;

/**
 * Empirical Benchmark and Integration Test for Duplicate & Incident Detection.
 * Evaluates Precision, Recall, FPR, FNR across matching and non-matching complaint pairs.
 */
public class DuplicateDetectionBenchmarkTest {

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

    public static class ComplaintPair {
        public final String id;
        public final ComplaintDocument doc1;
        public final ComplaintDocument doc2;
        public final boolean isMatch; // Ground truth: true if describing same incident
        public final String rationale;

        public ComplaintPair(String id, ComplaintDocument doc1, ComplaintDocument doc2, boolean isMatch, String rationale) {
            this.id = id;
            this.doc1 = doc1;
            this.doc2 = doc2;
            this.isMatch = isMatch;
            this.rationale = rationale;
        }
    }

    public static List<ComplaintPair> getEvaluationPairs() {
        List<ComplaintPair> list = new ArrayList<>();

        // --- Positive Matching Pairs (Same underlying incident) ---
        list.add(new ComplaintPair("PAIR-01",
                new ComplaintDocument(101, "Main water pipe burst flooding 5th avenue", "Water is pouring from ruptured main line across 5th ave and commerce street", "Infrastructure", "SUBMITTED", Instant.now()),
                new ComplaintDocument(102, "Flooding on 5th Ave due to ruptured water pipe", "Underground water pipe burst near commerce st intersection, street submerged", "Infrastructure", "SUBMITTED", Instant.now()),
                true, "Identical water main break on 5th Ave"));

        list.add(new ComplaintPair("PAIR-02",
                new ComplaintDocument(103, "Downtown power substation explosion and outage", "Substation 4 transformer exploded, power is out across downtown financial district", "Infrastructure", "SUBMITTED", Instant.now()),
                new ComplaintDocument(104, "Total blackout in downtown area after transformer blast", "Electricity cut off everywhere downtown following loud bang at substation", "Infrastructure", "SUBMITTED", Instant.now()),
                true, "Same downtown substation blackout"));

        list.add(new ComplaintPair("PAIR-03",
                new ComplaintDocument(105, "Student portal HTTP 500 error during exam upload", "Unable to submit computer science exam, portal keeps throwing internal server error", "Academic", "SUBMITTED", Instant.now()),
                new ComplaintDocument(106, "Exam submission failed server 500 error portal", "Server error 500 occurs every time when uploading final exam paper on student portal", "Academic", "SUBMITTED", Instant.now()),
                true, "Same student portal exam upload crash"));

        list.add(new ComplaintPair("PAIR-04",
                new ComplaintDocument(107, "Foul chemical odor leaking near municipal pool", "Strong toxic chlorine chemical smell spreading near community center swimming pool", "Sanitation", "SUBMITTED", Instant.now()),
                new ComplaintDocument(108, "Chlorine gas leak at public pool facility", "Hazardous chlorine leak detected by staff at municipal pool building", "Sanitation", "SUBMITTED", Instant.now()),
                true, "Chlorine leak at community pool"));

        list.add(new ComplaintPair("PAIR-05",
                new ComplaintDocument(109, "Traffic signal dead at Broad and Elm junction", "The 4-way traffic lights are completely dark causing near misses", "Infrastructure", "SUBMITTED", Instant.now()),
                new ComplaintDocument(110, "Dead traffic lights broad st and elm street", "Intersection traffic signal failed and is unpowered at broad & elm", "Infrastructure", "SUBMITTED", Instant.now()),
                true, "Traffic light outage at Broad and Elm"));

        list.add(new ComplaintPair("PAIR-06",
                new ComplaintDocument(111, "Raw sewage overflow on Maple lane alleyway", "Manhole lid pushed off by overflowing sewage and dirty wastewater on maple lane", "Sanitation", "SUBMITTED", Instant.now()),
                new ComplaintDocument(112, "Sewage spilling from manhole in Maple Lane", "Sanitary sewer backup spilling waste water along maple lane pedestrian alley", "Sanitation", "SUBMITTED", Instant.now()),
                true, "Sewage overflow on Maple lane"));

        // --- Negative Non-Matching Pairs (Distinct problems, potentially shared words/category) ---
        list.add(new ComplaintPair("PAIR-07",
                new ComplaintDocument(113, "Pothole in front of 422 Oak Street", "Deep road hole in asphalt damaging car rims near oak street", "Infrastructure", "SUBMITTED", Instant.now()),
                new ComplaintDocument(114, "Streetlight broken on 9th Avenue", "The lamp pole is unlit at night on 9th avenue", "Infrastructure", "SUBMITTED", Instant.now()),
                false, "Distinct infrastructure issues in different locations"));

        list.add(new ComplaintPair("PAIR-08",
                new ComplaintDocument(115, "Library book renewal online error", "Could not renew history book on municipal library website", "Administrative", "SUBMITTED", Instant.now()),
                new ComplaintDocument(116, "Water main leak flooding 5th avenue", "Water is pouring from ruptured main line across 5th ave", "Infrastructure", "SUBMITTED", Instant.now()),
                false, "Completely unrelated domains"));

        list.add(new ComplaintPair("PAIR-09",
                new ComplaintDocument(117, "Grading appeal for Math 101 quiz", "Requesting re-check of question 4 on calculus exam", "Academic", "SUBMITTED", Instant.now()),
                new ComplaintDocument(118, "Parking permit decal application rejected", "Submitted vehicle registration but parking permit was denied", "Administrative", "SUBMITTED", Instant.now()),
                false, "Academic grade appeal vs vehicle permit"));

        list.add(new ComplaintPair("PAIR-10",
                new ComplaintDocument(119, "Garbage truck skipped recycling bin on Tuesday", "Recycling bins on West street were not picked up", "Sanitation", "SUBMITTED", Instant.now()),
                new ComplaintDocument(120, "Chlorine chemical leak at public pool facility", "Hazardous chlorine leak detected by staff at municipal pool", "Sanitation", "SUBMITTED", Instant.now()),
                false, "Same category (Sanitation) but completely different incidents"));

        list.add(new ComplaintPair("PAIR-11",
                new ComplaintDocument(121, "Park bench paint peeling in Central park", "Wooden bench needs repainting near duck pond", "Infrastructure", "SUBMITTED", Instant.now()),
                new ComplaintDocument(122, "Downtown power substation explosion and outage", "Substation 4 transformer exploded, power is out", "Infrastructure", "SUBMITTED", Instant.now()),
                false, "Park bench maintenance vs city electrical substation blast"));

        list.add(new ComplaintPair("PAIR-12",
                new ComplaintDocument(123, "Typos in cafeteria lunch menu notice", "Misspelled items on weekly university dining schedule", "Administrative", "SUBMITTED", Instant.now()),
                new ComplaintDocument(124, "Student portal HTTP 500 error during exam upload", "Unable to submit computer science exam, portal keeps throwing internal server error", "Academic", "SUBMITTED", Instant.now()),
                false, "Cafeteria typos vs university exam portal server failure"));

        return list;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================================");
        System.out.println("Duplicate Detection & Incident Management Empirical Evaluation");
        System.out.println("==================================================================\n");

        List<ComplaintPair> pairs = getEvaluationPairs();
        double threshold = DuplicateDetectionEngine.DEFAULT_SIMILARITY_THRESHOLD;

        int tp = 0, fp = 0, tn = 0, fn = 0;

        System.out.println("--- Pairwise Similarity Score Evaluation ---");
        for (ComplaintPair pair : pairs) {
            List<ComplaintDocument> corpus = Collections.singletonList(pair.doc2);
            List<DuplicateCandidate> matches = DuplicateDetectionEngine.evaluateCandidates(pair.doc1, corpus, threshold);

            boolean predictedMatch = !matches.isEmpty();
            double score = !matches.isEmpty() ? matches.get(0).getSimilarityScore() : 0.0;
            String explanation = !matches.isEmpty() ? matches.get(0).getExplanation() : "Below threshold";

            System.out.printf("[%s] Expected: %-5s | Predicted: %-5s | Score: %5.3f | %s%n",
                    pair.id, pair.isMatch ? "MATCH" : "DIFF", predictedMatch ? "MATCH" : "DIFF", score, pair.rationale);
            if (predictedMatch) {
                System.out.println("       Explanation: " + explanation);
            }

            if (pair.isMatch && predictedMatch) tp++;
            else if (!pair.isMatch && predictedMatch) fp++;
            else if (!pair.isMatch && !predictedMatch) tn++;
            else if (pair.isMatch && !predictedMatch) fn++;
        }

        double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
        double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
        double fpr = (fp + tn) > 0 ? (double) fp / (fp + tn) : 0.0;
        double fnr = (tp + fn) > 0 ? (double) fn / (tp + fn) : 0.0;
        double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0.0;

        System.out.println("\n------------------------------------------------------------------");
        System.out.println("EVALUATION METRICS AT THRESHOLD " + threshold + ":");
        System.out.printf("True Positives (TP):   %d%n", tp);
        System.out.printf("True Negatives (TN):   %d%n", tn);
        System.out.printf("False Positives (FP):  %d%n", fp);
        System.out.printf("False Negatives (FN):  %d%n", fn);
        System.out.printf("Precision:             %.2f%%%n", precision * 100.0);
        System.out.printf("Recall:                %.2f%%%n", recall * 100.0);
        System.out.printf("False Positive Rate:   %.2f%%%n", fpr * 100.0);
        System.out.printf("False Negative Rate:   %.2f%%%n", fnr * 100.0);
        System.out.printf("F1-Score:              %.4f%n", f1);
        System.out.println("------------------------------------------------------------------");

        assertTrue("Duplicate detection achieves 100% Precision", precision == 1.0);
        assertTrue("Duplicate detection achieves 100% Recall", recall == 1.0);
        assertTrue("False Positive Rate is 0%", fpr == 0.0);
        assertTrue("False Negative Rate is 0%", fnr == 0.0);

        System.out.println("\n--- Testing Incident Linking, Identity Preservation & Audit Trail ---");
        try (Connection conn = DbConnection.getConnection()) {
            // 1. Create a test incident group
            int incidentId = IncidentManagementService.createIncidentGroup(
                    conn,
                    "North Substation Transformer Fault",
                    "Multiple residential reports of power drop in north quadrant",
                    "Infrastructure",
                    "HIGH",
                    1
            );
            assertTrue("Incident group created with valid ID", incidentId > 0);

            // 2. Insert 2 test complaints to link
            int c1 = -1, c2 = -1;
            String insertCompSql = "INSERT INTO complaints (user_id, title, category, description, priority, status) VALUES (5, ?, ?, ?, 'HIGH', 'SUBMITTED')";
            try (java.sql.PreparedStatement ps = conn.prepareStatement(insertCompSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "Test Substation Report A");
                ps.setString(2, "Infrastructure");
                ps.setString(3, "Substation transformer blown out on Elm");
                ps.executeUpdate();
                try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) c1 = rs.getInt(1);
                }

                ps.setString(1, "Test Substation Report B");
                ps.setString(2, "Infrastructure");
                ps.setString(3, "Blackout near substation after blast");
                ps.executeUpdate();
                try (java.sql.ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) c2 = rs.getInt(1);
                }
            }
            assertTrue("Created test complaint c1", c1 > 0);
            assertTrue("Created test complaint c2", c2 > 0);

            // 3. Link c1
            boolean linked1 = IncidentManagementService.linkComplaintToIncident(
                    conn, incidentId, c1, 0.85, 2, "Confirmed identical substation failure report"
            );
            assertTrue("Linked complaint c1 to incident", linked1);

            // 4. Link c2
            boolean linked2 = IncidentManagementService.linkComplaintToIncident(
                    conn, incidentId, c2, 0.78, 2, "Corroborating blackout report from adjacent street"
            );
            assertTrue("Linked complaint c2 to incident", linked2);

            // 5. Verify independent tracking & separate complaint IDs
            List<IncidentMember> members = IncidentManagementService.getIncidentMembers(conn, incidentId);
            assertTrue("Incident has 2 linked member complaints", members.size() == 2);
            assertTrue("First member complaint ID preserved (c1)", members.get(0).complaintId == c1);
            assertTrue("Second member complaint ID preserved (c2)", members.get(1).complaintId == c2);

            // 6. Verify duplicate link is rejected
            boolean duplicateRejected = false;
            try {
                IncidentManagementService.linkComplaintToIncident(conn, incidentId, c1, 0.85, 2, "Duplicate link attempt");
            } catch (Exception ex) {
                duplicateRejected = true;
            }
            assertTrue("Prevented duplicate link of complaint c1 to same incident", duplicateRejected);

            // 7. Test unlinking
            boolean unlinked = IncidentManagementService.unlinkComplaintFromIncident(
                    conn, incidentId, c2, 1, "Detached after investigation proved separate circuit issue"
            );
            assertTrue("Successfully unlinked complaint c2", unlinked);

            List<IncidentMember> remaining = IncidentManagementService.getIncidentMembers(conn, incidentId);
            assertTrue("Incident now has 1 member after unlink", remaining.size() == 1);
        }

        System.out.println("\n==================================================================");
        System.out.printf("Results: %d passed, %d failed (Total: %d)%n", testsPassed, testsFailed, testsRun);
        System.out.println("==================================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }
}
