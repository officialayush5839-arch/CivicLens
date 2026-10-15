import civiclens.triage.TriageEngine;
import civiclens.triage.TriageResult;
import civiclens.triage.TriageResult.Priority;

import java.util.*;

/**
 * Empirical Benchmark comparing the baseline PriorityClassifier against
 * the explainable multi-dimensional CivicLens TriageEngine.
 *
 * Dataset: 56 realistic, diverse municipal complaint scenarios across all 4 priorities
 * (LOW, MEDIUM, HIGH, CRITICAL) and 8 municipal operational domains.
 *
 * Evaluates:
 * - Accuracy
 * - Macro-Averaged F1
 * - Per-Class Precision & Recall (LOW, MEDIUM, HIGH, CRITICAL)
 * - 4x4 Confusion Matrix
 * - Critical False Negatives (Life-safety / severe failure missed)
 */
public class SyntheticBenchmarkRunner {

    public static class TestCase {
        public final String id;
        public final String title;
        public final String description;
        public final String category;
        public final Priority expectedPriority;
        public final String notes;

        public TestCase(String id, String title, String description, String category, Priority expectedPriority, String notes) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.category = category;
            this.expectedPriority = expectedPriority;
            this.notes = notes;
        }
    }

    public static List<TestCase> getDataset() {
        List<TestCase> cases = new ArrayList<>();

        // Category 1: Routine Administrative & Minor Inquiries (LOW)
        cases.add(new TestCase("TC-01", "Inquiry about library hours", "Could someone please let me know if the central library opens on Sundays during holidays?", "Administrative", Priority.LOW, "Routine information request"));
        cases.add(new TestCase("TC-02", "Spelling error on public portal", "There is a minor typo on the contact us page in footer.", "Technical Issue", Priority.LOW, "Cosmetic UI defect"));
        cases.add(new TestCase("TC-03", "Request for parking permit renewal form", "Please send me the updated PDF form for parking permit renewal.", "Administrative", Priority.LOW, "Standard form inquiry"));
        cases.add(new TestCase("TC-04", "Park bench repaint request", "The wooden bench in Maple park has chipped paint. Would be nice to repaint it sometime.", "Infrastructure", Priority.LOW, "Non-hazardous maintenance"));
        cases.add(new TestCase("TC-05", "Feedback on community survey", "I completed the survey and wanted to share that the font was pleasant to read.", "Administrative", Priority.LOW, "General positive feedback"));
        cases.add(new TestCase("TC-06", "Tree leaves shedding on sidewalk", "Autumn leaves are collecting on the sidewalk in front of my driveway.", "Infrastructure", Priority.LOW, "Routine seasonal inconvenience"));
        cases.add(new TestCase("TC-07", "Course syllabus download question", "The syllabus link opened in a new tab instead of downloading directly.", "Academic", Priority.LOW, "Minor aesthetic preference"));
        cases.add(new TestCase("TC-08", "Street sweeping schedule clarification", "Can you confirm what time the street sweeper visits on 4th Avenue?", "Administrative", Priority.LOW, "Schedule inquiry"));

        // Category 2: Moderate Functional Inconvenience (MEDIUM)
        cases.add(new TestCase("TC-09", "Streetlight blinking intermittently", "The streetlight on Elm Street flickers on and off every evening.", "Infrastructure", Priority.MEDIUM, "Minor public infrastructure defect"));
        cases.add(new TestCase("TC-10", "Pothole in residential cul-de-sac", "A pothole has developed near the curb; vehicles have to drive slowly around it.", "Infrastructure", Priority.MEDIUM, "Standard roadway degradation"));
        cases.add(new TestCase("TC-11", "Grading discrepancy in portal", "My midterm assignment score is listed as 78 instead of 88.", "Academic", Priority.MEDIUM, "Individual record discrepancy"));
        cases.add(new TestCase("TC-12", "Trash bin missed during weekly collection", "The municipal garbage truck missed our block yesterday morning.", "Sanitation", Priority.MEDIUM, "Service delay, non-hazardous"));
        cases.add(new TestCase("TC-13", "Water pressure slightly low in evening", "Water flow is lower than usual between 6 PM and 8 PM in our apartment complex.", "Infrastructure", Priority.MEDIUM, "Service degradation, not an outage"));
        cases.add(new TestCase("TC-14", "Public Wi-Fi disconnecting frequently", "The guest Wi-Fi at community center drops connection every 20 minutes.", "Technical Issue", Priority.MEDIUM, "Public amenity disruption"));
        cases.add(new TestCase("TC-15", "Parking meter coin slot jammed", "Meter #42 on Main St is not taking quarters, card reader still functions.", "Administrative", Priority.MEDIUM, "Partial equipment malfunction"));
        cases.add(new TestCase("TC-16", "Graffiti on park pavilion", "Someone spray-painted tags on the park shelter overnight.", "Sanitation", Priority.MEDIUM, "Property vandalism without structural hazard"));

        // Category 3: High Priority Urgent Issues (HIGH)
        cases.add(new TestCase("TC-17", "Deep pothole damaging vehicle tires", "Sharp, deep pothole on Main highway lane has already punctured tires on 3 cars today.", "Infrastructure", Priority.HIGH, "Immediate traffic hazard"));
        cases.add(new TestCase("TC-18", "Water main leak flooding alleyway", "Clean water is gushing out of an underground pipe flooding the service alley.", "Infrastructure", Priority.HIGH, "Substantial municipal water loss"));
        cases.add(new TestCase("TC-19", "Exam portal returning 500 error for students", "The exam submission portal is throwing server errors 2 hours before the deadline.", "Academic", Priority.HIGH, "Time-critical academic platform failure"));
        cases.add(new TestCase("TC-20", "Large broken tree limb dangling over sidewalk", "A heavy broken oak branch is hanging precariously above the pedestrian walkway.", "Infrastructure", Priority.HIGH, "Imminent pedestrian hazard"));
        cases.add(new TestCase("TC-21", "Traffic signal out at busy intersection", "The 4-way traffic lights at 5th and Broad are completely dead, causing gridlock.", "Infrastructure", Priority.HIGH, "Significant traffic collision risk"));
        cases.add(new TestCase("TC-22", "Raw sewage backing up into basement drains", "Foul sewage is backing up through floor drains in several ground floor units.", "Sanitation", Priority.HIGH, "Severe sanitation and biohazard risk"));
        cases.add(new TestCase("TC-23", "Payment gateway down during property tax deadline", "Online payment portal keeps failing with gateway timeout on the final due date.", "Technical Issue", Priority.HIGH, "Time-sensitive financial system impact"));

        // Category 4: Critical & Life-Safety Emergency / Systemic Outages (CRITICAL)
        cases.add(new TestCase("TC-24", "Live electrical power line sparking on wet roadway", "High voltage electrical wire snapped and is actively sparking on flooded street with pedestrians around.", "Infrastructure", Priority.CRITICAL, "Immediate electrocution threat"));
        cases.add(new TestCase("TC-25", "Strong natural gas smell in public school basement", "Overwhelming natural gas odor detected near boilers, students are being evacuated.", "Infrastructure", Priority.CRITICAL, "Explosion risk & school evacuation"));
        cases.add(new TestCase("TC-26", "Pedestrian bridge railing collapsed over river", "The concrete support of the footbridge cracked and railing gave way, multiple people in danger of falling.", "Infrastructure", Priority.CRITICAL, "Structural collapse with life hazard"));
        cases.add(new TestCase("TC-27", "Hospital emergency room grid power failure", "Full electrical blackout in hospital trauma wing, backup generator failed to engage.", "Infrastructure", Priority.CRITICAL, "Direct threat to patient life"));
        cases.add(new TestCase("TC-28", "Chemical leak with toxic fumes near playground", "Unknown corrosive chemical leaking from overturned municipal tank creating toxic vapor cloud.", "Sanitation", Priority.CRITICAL, "Acute public toxicity hazard"));
        cases.add(new TestCase("TC-29", "Citywide 911 dispatch database unreachable", "Emergency dispatch routing system is completely frozen across all city precincts.", "Technical Issue", Priority.CRITICAL, "Public safety dispatch collapse"));

        // Category 5: Negation & De-escalation (Explicitly Not Urgent / Not Critical)
        cases.add(new TestCase("TC-30", "Broken lamp post - not urgent", "The decorative post in garden broke, but this is not urgent as nobody uses that path at night.", "Infrastructure", Priority.LOW, "Explicit negation of urgency"));
        cases.add(new TestCase("TC-31", "Fire alarm test clarification - no emergency", "We heard a beep yesterday; please note there is no emergency, just asking about the test schedule.", "Safety", Priority.LOW, "Negated emergency phrase"));
        cases.add(new TestCase("TC-32", "Security alert resolved yesterday", "There was a security issue earlier this week, but it has already been resolved. Just submitting records.", "Security", Priority.LOW, "Past event already resolved"));
        cases.add(new TestCase("TC-33", "Not a critical problem with air conditioner", "The AC unit in conference room is making a humming sound. Not critical, works fine otherwise.", "Infrastructure", Priority.LOW, "Explicit 'not critical' qualification"));
        cases.add(new TestCase("TC-34", "System test warning message", "Do not panic, this is merely a drill and simulated outage for testing purposes.", "Technical Issue", Priority.LOW, "Explicit drill/simulation"));

        // Category 6: Contrastive Clauses & Adversarial Nuance
        cases.add(new TestCase("TC-35", "Not an emergency, but water main burst into electrical panel", "This started as routine maintenance, but now water has flooded into the main electrical breaker room and is smoking.", "Infrastructure", Priority.CRITICAL, "Contrastive clause: initial denial followed by critical life-safety emergency"));
        cases.add(new TestCase("TC-36", "The service is down, but only for one test account", "Login fails, but only for the dummy demo account in sandbox; production users unaffected.", "Technical Issue", Priority.LOW, "Service down negated to single test account"));
        cases.add(new TestCase("TC-37", "This is not an emergency; however, multiple users are locked out", "Nobody is hurt, but 40 staff members are completely locked out of payroll system during payroll processing.", "Technical Issue", Priority.HIGH, "Negated emergency but significant operational impact"));
        cases.add(new TestCase("TC-38", "The system is working again, but the issue occurred repeatedly", "Platform recovered on its own, but dropped transactions 5 times during peak hours today.", "Technical Issue", Priority.MEDIUM, "Intermittent system instability"));
        cases.add(new TestCase("TC-39", "No immediate danger right now, but dam wall has 20-foot vertical fracture", "Water is not yet overflowing, but engineers observed a deep fracture in the reservoir retaining wall following tremor.", "Infrastructure", Priority.CRITICAL, "Structural catastrophe risk despite temporary calm"));
        cases.add(new TestCase("TC-40", "Gas leak reported yesterday was fixed, just checking certificate", "The gas company resolved the leak yesterday. I am just requesting the inspection certificate for records.", "Administrative", Priority.LOW, "Past resolved incident followed by routine doc request"));

        // Category 7: Category & Population Context
        cases.add(new TestCase("TC-41", "Water supply cut off for entire downtown ward", "Municipal water is completely shut off impacting over 15,000 residents and local hospital.", "Infrastructure", Priority.CRITICAL, "Large affected population + essential infrastructure"));
        cases.add(new TestCase("TC-42", "Account password reset email not received", "I clicked forgot password 10 minutes ago and have not received the reset link in inbox.", "Technical Issue", Priority.LOW, "Individual routine account self-service"));
        cases.add(new TestCase("TC-43", "Student hostel fire exit door padlocked shut", "The secondary emergency exit door on 4th floor has a heavy padlock installed by unknown party.", "Safety", Priority.CRITICAL, "Life-safety code violation with high occupancy"));
        cases.add(new TestCase("TC-44", "Pest infestation in central dining hall kitchen", "Live rodents and insect contamination found in food preparation area serving 800 people.", "Sanitation", Priority.HIGH, "Public health and widespread contamination risk"));
        cases.add(new TestCase("TC-45", "Missing street name sign at intersection", "The green metal sign for Oak Street was knocked down during storm.", "Infrastructure", Priority.LOW, "Minor navigational inconvenience"));
        cases.add(new TestCase("TC-46", "Unsecured deep open excavation without barricade near elementary school", "Construction crew left a 10-foot trench completely uncovered and unlit right where children walk to school.", "Safety", Priority.CRITICAL, "Acute mortal fall hazard for children"));
        cases.add(new TestCase("TC-47", "Refund delay for cancelled community class", "I was promised a $25 refund three weeks ago for the cancelled pottery workshop.", "Administrative", Priority.LOW, "Routine billing follow-up"));
        cases.add(new TestCase("TC-48", "Cybersecurity ransomware note on municipal hospital terminals", "All clinical workstation screens are locked with a bitcoin extortion message demanding payment.", "Technical Issue", Priority.CRITICAL, "Critical ransomware attack disabling hospital"));

        // Additional Cases (TC-49 to TC-56) Expanding Coverage to 56 Municipal Scenarios
        cases.add(new TestCase("TC-49", "Lead paint peeling in municipal daycare playroom", "Toxic lead paint flaking in infant and toddler activity room.", "Sanitation", Priority.HIGH, "Toxic lead paint flaking in infant activity room"));
        cases.add(new TestCase("TC-50", "Active boiler room fire spreading to staircase", "Ignited structural basement fire blocking primary egress stairwell.", "Safety", Priority.CRITICAL, "Ignited structural fire blocking egress"));
        cases.add(new TestCase("TC-51", "Sidewalk slab lifted 2 inches by tree root", "Concrete root heave creating pedestrian trip hazard.", "Infrastructure", Priority.MEDIUM, "Concrete root heave trip hazard"));
        cases.add(new TestCase("TC-52", "Website font size too small for senior citizen", "Citizen accessibility feedback regarding font scaling on portal.", "Administrative", Priority.LOW, "Accessibility feedback"));
        cases.add(new TestCase("TC-53", "Retaining wall tilting towards pedestrian walkway", "6-foot stone wall leaning 15 degrees over active park walkway.", "Infrastructure", Priority.HIGH, "Leaning stone wall over walkway"));
        cases.add(new TestCase("TC-54", "No security breach, just inquiring about 2FA setup", "User explicitly disclaims security breach while asking for 2FA help.", "Technical Issue", Priority.LOW, "Negated security breach"));
        cases.add(new TestCase("TC-55", "Road is not closed, but massive sinkhole opening under bus lane", "Bus route active; contrastive clause reveals 10-foot expanding sinkhole.", "Infrastructure", Priority.CRITICAL, "Expanding sinkhole under active bus route"));
        cases.add(new TestCase("TC-56", "Overflowing recycling dumpster at recreation center", "Cardboard and plastic bottles spilling onto rear gym parking lot.", "Sanitation", Priority.MEDIUM, "Spilling cardboard and plastic bottles"));

        return cases;
    }

    public static class Metrics {
        public final int total;
        public final int correct;
        public final double accuracy;
        public final double macroF1;
        public final Map<Priority, Double> precision;
        public final Map<Priority, Double> recall;
        public final Map<Priority, Double> f1;
        public final int[][] confusionMatrix; // [actual][predicted]
        public final int criticalFalseNegatives;

        public Metrics(int total, int correct, double accuracy, double macroF1,
                       Map<Priority, Double> precision, Map<Priority, Double> recall, Map<Priority, Double> f1,
                       int[][] confusionMatrix, int criticalFalseNegatives) {
            this.total = total;
            this.correct = correct;
            this.accuracy = accuracy;
            this.macroF1 = macroF1;
            this.precision = precision;
            this.recall = recall;
            this.f1 = f1;
            this.confusionMatrix = confusionMatrix;
            this.criticalFalseNegatives = criticalFalseNegatives;
        }
    }

    public static Metrics evaluate(List<TestCase> dataset, boolean useUpgraded) {
        int total = dataset.size();
        int correct = 0;
        int[][] matrix = new int[4][4]; // 0: LOW, 1: MEDIUM, 2: HIGH, 3: CRITICAL

        for (TestCase tc : dataset) {
            Priority predicted;
            if (useUpgraded) {
                TriageResult res = TriageEngine.evaluate(tc.title, tc.description, tc.category);
                predicted = res.getPriority();
            } else {
                String legacyStr = PriorityClassifier.classifyLegacy(tc.title, tc.description, tc.category);
                predicted = Priority.fromString(legacyStr);
            }

            int actualIdx = tc.expectedPriority.ordinal();
            int predIdx = predicted.ordinal();
            matrix[actualIdx][predIdx]++;

            if (actualIdx == predIdx) {
                correct++;
            }
        }

        double accuracy = (double) correct / total;
        Map<Priority, Double> precision = new LinkedHashMap<>();
        Map<Priority, Double> recall = new LinkedHashMap<>();
        Map<Priority, Double> f1 = new LinkedHashMap<>();
        double sumF1 = 0.0;

        for (Priority p : Priority.values()) {
            int idx = p.ordinal();
            int tp = matrix[idx][idx];
            int fn = 0;
            for (int j = 0; j < 4; j++) {
                if (j != idx) fn += matrix[idx][j];
            }
            int fp = 0;
            for (int i = 0; i < 4; i++) {
                if (i != idx) fp += matrix[i][idx];
            }

            double prec = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
            double rec = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
            double f = (prec + rec) > 0 ? (2.0 * prec * rec) / (prec + rec) : 0.0;

            precision.put(p, prec);
            recall.put(p, rec);
            f1.put(p, f);
            sumF1 += f;
        }

        double macroF1 = sumF1 / 4.0;
        // Critical FN: Actual is CRITICAL (idx 3), but predicted was NOT CRITICAL (idx 0, 1, 2)
        int criticalFN = matrix[3][0] + matrix[3][1] + matrix[3][2];

        return new Metrics(total, correct, accuracy, macroF1, precision, recall, f1, matrix, criticalFN);
    }

    public static void printReport(String name, Metrics m) {
        System.out.println("==========================================================================");
        System.out.println("BENCHMARK REPORT: " + name);
        System.out.println("==========================================================================");
        System.out.printf("Total Cases:                 %d%n", m.total);
        System.out.printf("Correct Predictions:         %d%n", m.correct);
        System.out.printf("Overall Accuracy:            %.2f%%%n", m.accuracy * 100.0);
        System.out.printf("Macro-Averaged F1:           %.4f%n", m.macroF1);
        System.out.printf("Critical False Negatives:    %d (Severe life-safety/outage missed)%n", m.criticalFalseNegatives);
        System.out.println("--------------------------------------------------------------------------");
        System.out.printf("%-12s | %-10s | %-10s | %-10s%n", "Class", "Precision", "Recall", "F1-Score");
        System.out.println("-------------+------------+------------+-----------");
        for (Priority p : Priority.values()) {
            System.out.printf("%-12s | %9.2f%% | %9.2f%% | %10.4f%n",
                    p.name(),
                    m.precision.get(p) * 100.0,
                    m.recall.get(p) * 100.0,
                    m.f1.get(p));
        }
        System.out.println("--------------------------------------------------------------------------");
        System.out.println("CONFUSION MATRIX (Rows: Ground Truth, Columns: Predicted):");
        System.out.printf("%-12s | %-8s %-8s %-8s %-8s%n", "Actual \\ Pred", "LOW", "MEDIUM", "HIGH", "CRITICAL");
        System.out.println("-------------+-----------------------------------");
        Priority[] vals = Priority.values();
        for (int i = 0; i < 4; i++) {
            System.out.printf("%-12s | %-8d %-8d %-8d %-8d%n",
                    vals[i].name(),
                    m.confusionMatrix[i][0],
                    m.confusionMatrix[i][1],
                    m.confusionMatrix[i][2],
                    m.confusionMatrix[i][3]);
        }
        System.out.println("==========================================================================\n");
    }

    public static void main(String[] args) {
        List<TestCase> dataset = getDataset();
        System.out.println("Evaluating CivicLens Triage Evaluation Benchmark on " + dataset.size() + " synthetic cases...\n");

        Metrics legacyMetrics = evaluate(dataset, false);
        Metrics upgradedMetrics = evaluate(dataset, true);

        printReport("BASELINE (PriorityClassifier Legacy)", legacyMetrics);
        printReport("UPGRADED (CivicLens Multi-Dimensional TriageEngine)", upgradedMetrics);

        System.out.println("==========================================================================");
        System.out.println("EMPIRICAL COMPARISON SUMMARY:");
        System.out.printf("Accuracy:       Baseline = %5.2f%%  -->  Upgraded = %5.2f%%  (Delta: %+5.2f%%)%n",
                legacyMetrics.accuracy * 100.0, upgradedMetrics.accuracy * 100.0,
                (upgradedMetrics.accuracy - legacyMetrics.accuracy) * 100.0);
        System.out.printf("Macro-F1:       Baseline = %6.4f  -->  Upgraded = %6.4f  (Delta: %+6.4f)%n",
                legacyMetrics.macroF1, upgradedMetrics.macroF1,
                upgradedMetrics.macroF1 - legacyMetrics.macroF1);
        System.out.printf("Critical FNs:   Baseline = %2d      -->  Upgraded = %2d      (Risk Reduction: %d fewer missed emergencies)%n",
                legacyMetrics.criticalFalseNegatives, upgradedMetrics.criticalFalseNegatives,
                legacyMetrics.criticalFalseNegatives - upgradedMetrics.criticalFalseNegatives);
        System.out.println("==========================================================================");

        if (upgradedMetrics.accuracy > legacyMetrics.accuracy &&
            upgradedMetrics.macroF1 > legacyMetrics.macroF1 &&
            upgradedMetrics.criticalFalseNegatives == 0) {
            System.out.println("\n>>> EMPIRICAL BENCHMARK VERIFICATION: PASSED (Upgraded classifier demonstrably superior)");
        } else {
            System.err.println("\n>>> EMPIRICAL BENCHMARK VERIFICATION: FAILED REGRESSION GATE");
            System.exit(1);
        }
    }
}
