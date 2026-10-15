
public class PriorityClassifier {

    public static String classify(
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
            "error message",
            "login issue",
            "slow performance",
            "unable to submit",
            "repeated error",
            "frequent error",
            "bug",
            "failed",
            "failure",
            "delay",
            "incorrect result",
            "cannot log in",
            "unable to login",
            "cannot access",
            "unable to access",
            "portal is failing"
        };

        if (containsAny(text, mediumKeywords)) {
            return "MEDIUM";
        }

        // Default: routine requests and other issues
        return "LOW";
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase()
                    .replaceAll("[^a-z0-9\\s'-]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
    }

    private static boolean containsAny(
            String text, String[] keywords) {

        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    // Run this class to test sample complaints
    public static void main(String[] args) {

        System.out.println("Test A: " + classify(
            "Request for a profile picture update",
            "I'd like to update my profile picture when convenient. "
                + "There is no urgent issue.",
            "Other"
        ));

        System.out.println("Test B: " + classify(
            "Multiple students cannot access the portal",
            "The portal is failing for multiple students, "
                + "and important academic work is delayed.",
            "Technical Issue"
        ));

        System.out.println("Test C: " + classify(
            "Critical server outage",
            "The main server is completely down. "
                + "All services down and immediate intervention is required.",
            "Technical Issue"
        ));
    }
}
