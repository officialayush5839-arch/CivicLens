package civiclens.lifecycle;

import java.util.*;

/**
 * Validates legal complaint lifecycle transitions and enforces role-based state security.
 */
public class LifecycleStateMachine {

    private static final Map<ComplaintState, Set<ComplaintState>> ALLOWED_TRANSITIONS = new EnumMap<>(ComplaintState.class);

    static {
        // SUBMITTED can transition to ASSIGNED or REJECTED
        ALLOWED_TRANSITIONS.put(ComplaintState.SUBMITTED, EnumSet.of(ComplaintState.ASSIGNED, ComplaintState.REJECTED));

        // ASSIGNED can transition to IN_PROGRESS or REJECTED
        ALLOWED_TRANSITIONS.put(ComplaintState.ASSIGNED, EnumSet.of(ComplaintState.IN_PROGRESS, ComplaintState.REJECTED));

        // IN_PROGRESS can transition to RESOLVED or REJECTED
        ALLOWED_TRANSITIONS.put(ComplaintState.IN_PROGRESS, EnumSet.of(ComplaintState.RESOLVED, ComplaintState.REJECTED));

        // RESOLVED can transition to CLOSED or APPEALED
        ALLOWED_TRANSITIONS.put(ComplaintState.RESOLVED, EnumSet.of(ComplaintState.CLOSED, ComplaintState.APPEALED));

        // APPEALED can transition to IN_PROGRESS (if accepted) or CLOSED (if rejected)
        ALLOWED_TRANSITIONS.put(ComplaintState.APPEALED, EnumSet.of(ComplaintState.IN_PROGRESS, ComplaintState.CLOSED));

        // Terminal states: CLOSED and REJECTED
        ALLOWED_TRANSITIONS.put(ComplaintState.CLOSED, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(ComplaintState.REJECTED, Collections.emptySet());
    }

    /**
     * Checks if a transition between two states is valid according to lifecycle rules.
     */
    public static boolean isTransitionAllowed(ComplaintState from, ComplaintState to) {
        if (from == null || to == null) return false;
        if (from == to) return true; // Idempotent no-op allowed

        Set<ComplaintState> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Asserts that a state transition is legal, throwing IllegalStateException if forbidden.
     */
    public static void validateTransition(ComplaintState from, ComplaintState to) {
        if (!isTransitionAllowed(from, to)) {
            throw new IllegalStateException("Invalid lifecycle state transition from " + from + " to " + to);
        }
    }
}
