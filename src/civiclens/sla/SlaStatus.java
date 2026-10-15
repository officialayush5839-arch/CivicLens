package civiclens.sla;

/**
 * Deterministic SLA evaluation states.
 */
public enum SlaStatus {
    ON_TRACK,
    APPROACHING_DEADLINE,
    OVERDUE,
    ESCALATED,
    PAUSED,
    COMPLETED;

    public static SlaStatus fromString(String s) {
        if (s == null) return ON_TRACK;
        try {
            return SlaStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ON_TRACK;
        }
    }
}
