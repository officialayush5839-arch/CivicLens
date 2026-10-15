package civiclens.lifecycle;

/**
 * Validated civic complaint lifecycle states.
 */
public enum ComplaintState {
    SUBMITTED,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    REJECTED,
    APPEALED;

    public static ComplaintState fromString(String state) {
        if (state == null) return SUBMITTED;
        try {
            return ComplaintState.valueOf(state.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SUBMITTED;
        }
    }
}
