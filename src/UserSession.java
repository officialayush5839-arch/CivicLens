import java.util.Objects;

/**
 * Immutable security and session context representing an authenticated user.
 * Enforces role-based access control (RBAC) across Citizen, Employee, and Admin domains.
 */
public final class UserSession {

    public enum Role {
        CITIZEN,
        EMPLOYEE,
        ADMIN;

        /**
         * Resolves a role string safely and case-insensitively.
         * Maps legacy 'USER' records to CITIZEN for 100% backward compatibility.
         */
        public static Role fromString(String roleStr) {
            if (roleStr == null) {
                return null;
            }
            String normalized = roleStr.trim().toUpperCase();
            if ("USER".equals(normalized)) {
                return CITIZEN;
            }
            try {
                return Role.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private final int userId;
    private final String fullName;
    private final String email;
    private final Role role;
    private final long loginTimestamp;

    // Static ambient session holder for cross-frame access and testing
    private static volatile UserSession currentSession;

    /**
     * Canonical constructor validating all session invariants.
     */
    public UserSession(int userId, String fullName, String email, Role role) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive: " + userId);
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("fullName cannot be null or empty");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("email cannot be null or empty");
        }
        if (role == null) {
            throw new IllegalArgumentException("role cannot be null");
        }

        this.userId = userId;
        this.fullName = fullName.trim();
        this.email = email.trim();
        this.role = role;
        this.loginTimestamp = System.currentTimeMillis();
    }

    /**
     * Convenience constructor parsing role from String.
     */
    public UserSession(int userId, String fullName, String email, String roleStr) {
        this(userId, fullName, email, Role.fromString(roleStr));
    }

    // Accessors
    public int getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public long getLoginTimestamp() {
        return loginTimestamp;
    }

    // RBAC & Authentication Predicates
    public boolean isAuthenticated() {
        return userId > 0 && role != null;
    }

    public boolean hasRole(Role targetRole) {
        return this.role != null && this.role == targetRole;
    }

    public boolean hasRole(String roleName) {
        if (roleName == null) {
            return false;
        }
        Role parsed = Role.fromString(roleName);
        return parsed != null && this.role == parsed;
    }

    public boolean isCitizen() {
        return this.role == Role.CITIZEN;
    }

    public boolean isEmployee() {
        return this.role == Role.EMPLOYEE;
    }

    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    // Static Session Management
    public static UserSession getCurrentSession() {
        return currentSession;
    }

    public static void setCurrentSession(UserSession session) {
        currentSession = session;
    }

    public static void clearCurrentSession() {
        currentSession = null;
    }

    public static void clearSession() {
        clearCurrentSession();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserSession that = (UserSession) o;
        return userId == that.userId &&
                Objects.equals(fullName, that.fullName) &&
                Objects.equals(email, that.email) &&
                role == that.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, fullName, email, role);
    }

    @Override
    public String toString() {
        return "UserSession{" +
                "userId=" + userId +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", role=" + role +
                '}';
    }
}
