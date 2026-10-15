import civiclens.triage.TriageEngine;
import civiclens.triage.TriageResult;

/**
 * CivicLens Priority Classifier & Triage Dispatcher.
 *
 * Provides:
 * 1. Contractual evaluate(title, desc, cat) returning explainable TriageResult.
 * 2. Backward-compatible classify(title, desc, cat) returning priority string.
 * 3. Backward-compatible classifyWithExplanation(title, desc, cat) returning TriageResult.
 * 4. Verbatim classifyLegacy(title, desc, cat) baseline for empirical benchmarking.
 */
public class PriorityClassifier {

    /**
     * Upgraded classification returning priority string (LOW, MEDIUM, HIGH, CRITICAL),
     * evaluated through the explainable 6-dimensional TriageEngine.
     * Preserves 100% backward compatibility with legacy caller signatures.
     */
    public static String classify(
            String title,
            String description,
            String category) {
        TriageResult result = evaluate(title, description, category);
        return result.getPriorityString();
    }

    /**
     * Contractual entry point specified in PROJECT.md:
     * returns structured immutable TriageResult with 6 dimensions, calibrated confidence,
     * primary reason, and urgency factors.
     */
    public static TriageResult evaluate(
            String title,
            String description,
            String category) {
        return TriageEngine.evaluate(title, description, category);
    }

    /**
     * Caller compatibility bridge for existing ComplaintFrame and UI classes.
     */
    public static TriageResult classifyWithExplanation(
            String title,
            String description,
            String category) {
        return evaluate(title, description, category);
    }

    /**
     * Preserved verbatim original classifier implementation for rigorous baseline
     * comparison and empirical regression benchmarking in evaluation suites.
     */
    public static String classifyLegacy(
            String title,
            String description,
            String category) {

        String text = normalize(title) + " "
                    + normalize(description);

        // Remove phrases that explicitly deny urgency
        String[] negatedPhrases = {
            "not urgent",
            "no urgent",
            "not an emergency",
            "no emergency",
            "not critical",
            "not severe",
            "no urgency",
            "no immediate action",
            "no immediate action required",
            "does not require immediate action",
            "doesn't require immediate action",
            "not a serious issue",
            "no serious issue"
        };

        for (String phrase : negatedPhrases) {
            text = text.replace(phrase, " ");
        }

        text = text.replaceAll("\\s+", " ").trim();

        // CRITICAL: major outages and serious security incidents
        String[] criticalKeywords = {
            "entire system down",
            "complete system failure",
            "all services down",
            "critical server outage",
            "data breach",
            "ransomware",
            "security breach",
            "unauthorized access",
            "major data loss",
            "all services unavailable",
            "all users affected"
        };

        if (containsAny(text, criticalKeywords)) {
            return "CRITICAL";
        }

        // HIGH: significant disruptions or explicit urgency
        String[] highKeywords = {
            "server down",
            "system down",
            "system outage",
            "service unavailable",
            "application crash",
            "all users unable",
            "cyberattack",
            "emergency",
            "urgent",
            "immediate intervention",
            "immediate action required",
            "multiple students cannot access",
            "multiple users cannot access"
        };

        if (containsAny(text, highKeywords)) {
            return "HIGH";
        }

        // HIGH: specific security problems
        String[] securityKeywords = {
            "suspicious login",
            "account compromised",
            "password stolen",
            "unauthorized login",
            "security vulnerability"
        };

        if (containsAny(text, securityKeywords)) {
            return "HIGH";
        }

        // MEDIUM: ordinary functionality problems
        String[] mediumKeywords = {
            "not working",
            "unable to",
            "cannot access",
            "error",
            "bug",
            "failed",
            "failure",
            "slow performance",
            "page not loading",
            "button not working",
            "form submission failed"
        };

        if (containsAny(text, mediumKeywords)) {
            return "MEDIUM";
        }

        // Default priority
        return "LOW";
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().trim();
    }

    private static boolean containsAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        System.out.println("=== CivicLens Upgraded Priority Classifier Diagnostic ===");

        String[][] testCases = {
            {"Test 1 (Explicit Not Urgent)", "Broken lamp post - not urgent", "The decorative post in garden broke, but this is not urgent as nobody uses that path at night.", "Infrastructure", "LOW"},
            {"Test 2 (Isolated Test Account)", "The service is down, but only for one test account", "Login fails, but only for the dummy demo account in sandbox; production users unaffected.", "Technical Issue", "LOW"},
            {"Test 3 (Contrastive Lockout)", "This is not an emergency; however, multiple users are locked out", "Nobody is hurt, but 40 staff members are completely locked out of payroll system during payroll processing.", "Technical Issue", "HIGH"},
            {"Test 4 (No Security Breach)", "There is no security breach, just a question", "Just wanted to ask about password requirements. There is no security breach, just a question.", "Technical Issue", "LOW"},
            {"Test 5 (Negated Data Breach)", "Security audit notice", "No data breach occurred on our servers, just confirming routine audit.", "Security", "LOW"},
            {"Test 6 (Word Boundary 'debug')", "Please debug the system", "Please debug the system when you have time.", "Administrative", "LOW"}
        };

        boolean allPassed = true;
        for (String[] tc : testCases) {
            String label = tc[0];
            String title = tc[1];
            String desc = tc[2];
            String cat = tc[3];
            String expected = tc[4];

            TriageResult res = evaluate(title, desc, cat);
            String priority = res.getPriorityString();
            boolean match = priority.equalsIgnoreCase(expected) ||
                    (expected.contains("/") && expected.contains(priority));

            System.out.println("\n[" + label + "]");
            System.out.println("  Input:        \"" + title + "\" | \"" + desc + "\"");
            System.out.println("  Expected:     " + expected);
            System.out.println("  Assigned:     " + priority + " [" + res.getConfidenceString() + "]");
            System.out.println("  Reason:       " + res.getJustification());
            System.out.println("  Dimensions:   " + res.getDimensionScores());
            System.out.println("  Verification: " + (match ? "PASS" : "FAIL"));

            if (!match) allPassed = false;
        }

        if (allPassed) {
            System.out.println("\n>>> ALL 6 TARGET LINGUISTIC EDGE CASES PASSED VERIFICATION!");
        } else {
            System.err.println("\n>>> ONE OR MORE TARGET EDGE CASES FAILED!");
            System.exit(1);
        }
    }
}
