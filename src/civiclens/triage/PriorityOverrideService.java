package civiclens.triage;

import civiclens.sla.SlaEngine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service and DAO to manage human-in-the-loop priority overrides.
 *
 * Guarantees:
 * 1. Immutably preserves the original machine triage recommendation across arbitrary successive overrides.
 * 2. Enforces Role-Based Access Control (rejects CITIZEN role with SecurityException).
 * 3. Atomically updates complaints.priority, re-synchronizes SLA deadlines, and records complaint_audit_log.
 * 4. Provides query methods for complete override history.
 */
public class PriorityOverrideService {

    public static class OverrideRecord {
        private final int overrideId;
        private final int complaintId;
        private final int userId;
        private final String userName;
        private final String previousPriority;
        private final String newPriority;
        private final String originalRecommendation;
        private final String reason;
        private final Instant createdAt;

        public OverrideRecord(int overrideId, int complaintId, int userId, String userName,
                              String previousPriority, String newPriority,
                              String originalRecommendation, String reason, Instant createdAt) {
            this.overrideId = overrideId;
            this.complaintId = complaintId;
            this.userId = userId;
            this.userName = userName;
            this.previousPriority = previousPriority;
            this.newPriority = newPriority;
            this.originalRecommendation = originalRecommendation;
            this.reason = reason;
            this.createdAt = createdAt;
        }

        public int getOverrideId() { return overrideId; }
        public int getComplaintId() { return complaintId; }
        public int getUserId() { return userId; }
        public String getUserName() { return userName; }
        public String getPreviousPriority() { return previousPriority; }
        public String getNewPriority() { return newPriority; }
        public String getOriginalRecommendation() { return originalRecommendation; }
        public String getReason() { return reason; }
        public Instant getCreatedAt() { return createdAt; }
    }

    /**
     * Applies an auditable priority override on a complaint with full transactional atomicity.
     *
     * @param conn Database connection (participates in transaction)
     * @param complaintId Target complaint ID
     * @param userId Acting user ID
     * @param newPriority Target priority (LOW, MEDIUM, HIGH, CRITICAL)
     * @param reason Mandatory reason for override (>= 5 chars)
     * @param originalRecommendation Optional explicit original recommendation; if null, auto-resolved
     * @return true on success
     * @throws SQLException on database error
     * @throws SecurityException if acting user is a CITIZEN
     * @throws IllegalArgumentException on invalid arguments
     */
    public static boolean recordOverride(
            Connection conn,
            int complaintId,
            int userId,
            String newPriority,
            String reason,
            String originalRecommendation) throws SQLException {

        if (complaintId <= 0) {
            throw new IllegalArgumentException("Invalid complaintId: " + complaintId);
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid userId: " + userId);
        }
        if (newPriority == null || newPriority.trim().isEmpty()) {
            throw new IllegalArgumentException("New priority cannot be empty");
        }
        if (reason == null || reason.trim().length() < 5) {
            throw new IllegalArgumentException("Override reason must be at least 5 characters long");
        }

        String validPriority = newPriority.trim().toUpperCase();
        if (!validPriority.equals("LOW") && !validPriority.equals("MEDIUM") &&
            !validPriority.equals("HIGH") && !validPriority.equals("CRITICAL")) {
            throw new IllegalArgumentException("Invalid priority value: " + newPriority);
        }

        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            // 1. RBAC Guard: Query user role (Citizens are blocked from overriding priority)
            String actorRole = "STAFF";
            String userRoleSql = "SELECT role, full_name FROM users WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(userRoleSql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String r = rs.getString("role");
                        if ("CITIZEN".equalsIgnoreCase(r)) {
                            throw new SecurityException("Unauthorized: Citizens are not permitted to override complaint priorities.");
                        }
                        actorRole = r != null ? r.toUpperCase() : "STAFF";
                    } else {
                        throw new SQLException("User not found with ID: " + userId);
                    }
                }
            }

            // 2. Fetch current complaint details (priority, category, title, description)
            String currentPriority = null;
            String category = "*";
            String title = "";
            String description = "";
            String selectSql = "SELECT priority, category, title, description FROM complaints WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, complaintId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentPriority = rs.getString("priority");
                        category = rs.getString("category");
                        title = rs.getString("title");
                        description = rs.getString("description");
                    } else {
                        throw new SQLException("Complaint not found with ID: " + complaintId);
                    }
                }
            }

            // 3. Resolve Original Recommendation Immutability
            // If prior overrides exist, strictly inherit the initial machine recommendation so it is NEVER lost.
            String resolvedOriginalRecommendation = originalRecommendation;
            String earliestOverrideSql = "SELECT original_recommendation FROM priority_overrides WHERE complaint_id = ? ORDER BY override_id ASC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(earliestOverrideSql)) {
                ps.setInt(1, complaintId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        resolvedOriginalRecommendation = rs.getString("original_recommendation");
                    }
                }
            }

            // If still unresolved, evaluate or use current priority
            if (resolvedOriginalRecommendation == null || resolvedOriginalRecommendation.trim().isEmpty() ||
                "Administrator Manual Adjustment".equalsIgnoreCase(resolvedOriginalRecommendation)) {
                if (title != null && !title.isEmpty()) {
                    TriageResult res = TriageEngine.evaluate(title, description, category);
                    resolvedOriginalRecommendation = res.toExplanationReport();
                } else {
                    resolvedOriginalRecommendation = currentPriority != null ? currentPriority : "Unspecified";
                }
            }

            // 4. Insert into priority_overrides
            String insertSql = "INSERT INTO priority_overrides (complaint_id, user_id, previous_priority, " +
                    "new_priority, original_recommendation, reason, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, complaintId);
                ps.setInt(2, userId);
                ps.setString(3, currentPriority != null ? currentPriority : "UNKNOWN");
                ps.setString(4, validPriority);
                ps.setString(5, resolvedOriginalRecommendation);
                ps.setString(6, reason.trim());
                ps.executeUpdate();
            }

            // 5. Update complaints table
            String updateSql = "UPDATE complaints SET priority = ?, updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, validPriority);
                ps.setInt(2, complaintId);
                ps.executeUpdate();
            }

            // 6. Synchronize SLA Deadlines with new priority atomically
            SlaEngine.initializeDeadlines(conn, complaintId, validPriority, category != null ? category : "*", Instant.now());

            // 7. Log in complaint_audit_log
            String auditSql = "INSERT INTO complaint_audit_log (complaint_id, actor_id, actor_role, action, " +
                    "previous_state, new_state, details, created_at) " +
                    "VALUES (?, ?, ?, 'PRIORITY_OVERRIDE', ?, ?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement ps = conn.prepareStatement(auditSql)) {
                ps.setInt(1, complaintId);
                ps.setInt(2, userId);
                ps.setString(3, actorRole);
                ps.setString(4, currentPriority);
                ps.setString(5, validPriority);
                ps.setString(6, "Reason: " + reason.trim() + " | Original recommendation: " + resolvedOriginalRecommendation);
                ps.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException | SecurityException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    /**
     * Overload auto-resolving the original recommendation.
     */
    public static boolean recordOverride(
            Connection conn,
            int complaintId,
            int userId,
            String newPriority,
            String reason) throws SQLException {
        return recordOverride(conn, complaintId, userId, newPriority, reason, null);
    }

    /**
     * Retrieves the complete chronological history of overrides for a complaint.
     */
    public static List<OverrideRecord> getOverrideHistory(Connection conn, int complaintId) throws SQLException {
        List<OverrideRecord> list = new ArrayList<>();
        String sql = "SELECT o.override_id, o.complaint_id, o.user_id, u.full_name, " +
                "o.previous_priority, o.new_priority, o.original_recommendation, o.reason, o.created_at " +
                "FROM priority_overrides o " +
                "LEFT JOIN users u ON o.user_id = u.user_id " +
                "WHERE o.complaint_id = ? ORDER BY o.created_at DESC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new OverrideRecord(
                            rs.getInt("override_id"),
                            rs.getInt("complaint_id"),
                            rs.getInt("user_id"),
                            rs.getString("full_name"),
                            rs.getString("previous_priority"),
                            rs.getString("new_priority"),
                            rs.getString("original_recommendation"),
                            rs.getString("reason"),
                            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Backward-compatible alias for getOverrideHistory.
     */
    public static List<OverrideRecord> getOverridesForComplaint(Connection conn, int complaintId) throws SQLException {
        return getOverrideHistory(conn, complaintId);
    }
}
