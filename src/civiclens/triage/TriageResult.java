package civiclens.triage;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

/**
 * Immutable Java 21 record representing the explainable multi-dimensional triage result.
 * Fulfills PROJECT.md Interface Contract (§ 2) while maintaining 100% backward compatibility
 * with all existing accessors and callers.
 */
public record TriageResult(
    String priority,                      // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    double confidence,                    // Calibrated continuous score: 0.0 to 1.0
    String primaryReason,                 // Human-readable primary justification
    List<String> urgencyDrivers,          // Factors increasing urgency
    Map<String, Double> dimensionScores,  // Scores for all 6 dimensions (0.0 to 100.0)
    String rulesetVersion,                // Ruleset version tag
    Instant evaluatedAt                   // Evaluation timestamp
) implements Serializable {

    private static final long serialVersionUID = 2L;

    /**
     * Compact canonical constructor enforcing immutability and valid bounds.
     */
    public TriageResult {
        Objects.requireNonNull(priority, "priority cannot be null");
        Objects.requireNonNull(primaryReason, "primaryReason cannot be null");
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        urgencyDrivers = Collections.unmodifiableList(new ArrayList<>(urgencyDrivers != null ? urgencyDrivers : Collections.emptyList()));
        dimensionScores = Collections.unmodifiableMap(new LinkedHashMap<>(dimensionScores != null ? dimensionScores : Collections.emptyMap()));
        rulesetVersion = rulesetVersion != null ? rulesetVersion : "v2.2-civiclens-6dim";
        evaluatedAt = evaluatedAt != null ? evaluatedAt : Instant.now();
    }

    /**
     * Extended constructor supporting auxiliary reducers, missing information, and composite score.
     */
    public TriageResult(
            String priority,
            double confidence,
            String primaryReason,
            List<String> urgencyDrivers,
            List<String> urgencyReducers,
            List<String> missingInformation,
            Map<String, Double> dimensionScores,
            double compositeScore,
            String rulesetVersion,
            Instant evaluatedAt) {
        this(
            priority,
            confidence,
            enrichReason(primaryReason, urgencyReducers, missingInformation),
            urgencyDrivers,
            enrichDimensions(dimensionScores, compositeScore),
            rulesetVersion,
            evaluatedAt
        );
    }

    private static String enrichReason(String baseReason, List<String> reducers, List<String> missing) {
        StringBuilder sb = new StringBuilder(baseReason != null ? baseReason.trim() : "Standard assessment.");
        if (reducers != null && !reducers.isEmpty()) {
            if (!sb.toString().contains("Mitigating factor:")) {
                sb.append(" Mitigating factor: ").append(String.join("; ", reducers)).append(".");
            }
        }
        if (missing != null && !missing.isEmpty()) {
            if (!sb.toString().contains("Missing Information:")) {
                sb.append(" Missing Information: ").append(String.join("; ", missing)).append(".");
            }
        }
        return sb.toString();
    }

    private static Map<String, Double> enrichDimensions(Map<String, Double> dims, double compositeScore) {
        Map<String, Double> map = new LinkedHashMap<>();
        if (dims != null) {
            map.putAll(dims);
        }
        map.put("composite", compositeScore);
        return map;
    }

    // --- Backward Compatibility Accessors & Bridge Methods ---

    public String getPriorityString() {
        return priority;
    }

    public Priority getPriority() {
        return Priority.fromString(priority);
    }

    public double getConfidence() {
        return confidence;
    }

    public String getConfidenceString() {
        if (confidence >= 0.75) return "HIGH_CONFIDENCE";
        if (confidence >= 0.45) return "NEEDS_REVIEW";
        return "INSUFFICIENT_INFORMATION";
    }

    public String getJustification() {
        return primaryReason;
    }

    public List<String> getUrgencyDrivers() {
        return urgencyDrivers;
    }

    public List<String> getUrgencyReducers() {
        if (primaryReason == null) return Collections.emptyList();
        int idx = primaryReason.indexOf("Mitigating factor: ");
        if (idx != -1) {
            String sub = primaryReason.substring(idx + "Mitigating factor: ".length()).trim();
            int endIdx = sub.indexOf(". Missing Information:");
            if (endIdx != -1) {
                sub = sub.substring(0, endIdx);
            } else if (sub.endsWith(".")) {
                sub = sub.substring(0, sub.length() - 1);
            }
            String[] parts = sub.split(";\\s*");
            List<String> list = new ArrayList<>();
            for (String p : parts) {
                if (!p.trim().isEmpty()) list.add(p.trim());
            }
            return Collections.unmodifiableList(list);
        }
        return Collections.emptyList();
    }

    public List<String> getMissingInformation() {
        if (primaryReason == null) return Collections.emptyList();
        int idx = primaryReason.indexOf("Missing Information: ");
        if (idx != -1) {
            String sub = primaryReason.substring(idx + "Missing Information: ".length()).trim();
            if (sub.endsWith(".")) sub = sub.substring(0, sub.length() - 1);
            String[] parts = sub.split(";\\s*");
            List<String> list = new ArrayList<>();
            for (String p : parts) {
                if (!p.trim().isEmpty()) list.add(p.trim());
            }
            return Collections.unmodifiableList(list);
        }
        return Collections.emptyList();
    }

    public Map<String, Double> getDimensionScores() {
        return dimensionScores;
    }

    public double getCompositeScore() {
        if (dimensionScores.containsKey("composite")) {
            return dimensionScores.get("composite");
        }
        double safety = dimensionScores.getOrDefault("safety", 0.0);
        double disruption = dimensionScores.getOrDefault("disruption", 0.0);
        double population = dimensionScores.getOrDefault("population", 0.0);
        double time = dimensionScores.getOrDefault("time", dimensionScores.getOrDefault("timeSensitivity", 0.0));
        double severity = dimensionScores.getOrDefault("severity", 0.0);
        double category = dimensionScores.getOrDefault("category", dimensionScores.getOrDefault("categoryRisk", 0.0));
        return (0.25 * safety) + (0.20 * disruption) + (0.15 * population) + (0.15 * time) + (0.15 * severity) + (0.10 * category);
    }

    public String getRulesetVersion() {
        return rulesetVersion;
    }

    public Instant getTimestamp() {
        return evaluatedAt;
    }

    public String toExplanationReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Triage Assessment: ").append(priority)
          .append(" [").append(getConfidenceString()).append(" (")
          .append(String.format(Locale.US, "%.2f", confidence)).append(")]\n");
        sb.append("Justification: ").append(primaryReason).append("\n");
        if (!urgencyDrivers.isEmpty()) {
            sb.append("Urgency Drivers (+): ").append(String.join("; ", urgencyDrivers)).append("\n");
        }
        List<String> reducers = getUrgencyReducers();
        if (!reducers.isEmpty()) {
            sb.append("Urgency Reducers (-): ").append(String.join("; ", reducers)).append("\n");
        }
        List<String> missing = getMissingInformation();
        if (!missing.isEmpty()) {
            sb.append("Missing Information: ").append(String.join("; ", missing)).append("\n");
        }
        sb.append("Dimensions (6D): [")
          .append("Safety=").append(dimensionScores.getOrDefault("safety", 0.0))
          .append(", Disruption=").append(dimensionScores.getOrDefault("disruption", 0.0))
          .append(", Population=").append(dimensionScores.getOrDefault("population", 0.0))
          .append(", Time=").append(dimensionScores.getOrDefault("time", dimensionScores.getOrDefault("timeSensitivity", 0.0)))
          .append(", Severity=").append(dimensionScores.getOrDefault("severity", 0.0))
          .append(", Category=").append(dimensionScores.getOrDefault("category", dimensionScores.getOrDefault("categoryRisk", 0.0)))
          .append("]\n");
        sb.append("Composite Score: ").append(String.format(Locale.US, "%.1f", getCompositeScore()))
          .append(" | Ruleset: ").append(rulesetVersion)
          .append(" | Evaluated: ").append(evaluatedAt);
        return sb.toString();
    }

    @Override
    public String toString() {
        return "TriageResult{" +
                "priority='" + priority + '\'' +
                ", confidence=" + String.format(Locale.US, "%.2f", confidence) +
                ", score=" + String.format(Locale.US, "%.1f", getCompositeScore()) +
                ", justification='" + primaryReason + '\'' +
                '}';
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL;

        public static Priority fromString(String p) {
            if (p == null) return LOW;
            try {
                return Priority.valueOf(p.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return LOW;
            }
        }
    }

    public enum Confidence {
        HIGH_CONFIDENCE,
        NEEDS_REVIEW,
        INSUFFICIENT_INFORMATION
    }
}
