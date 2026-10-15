package civiclens.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only immutable audit trail service.
 * Records all mutations, lifecycle state shifts, priority overrides, and security events.
 */
public class AuditTrailService {

    public static class AuditEntry {
        public final int auditId;
        public final int complaintId;
        public final Integer actorId;
        public final String actorName;
        public final String actorRole;
        public final String action;
        public final String previousState;
        public final String newState;
        public final String details;
        public final Instant createdAt;

        public AuditEntry(int auditId, int complaintId, Integer actorId, String actorName,
                          String actorRole, String action, String previousState, String newState,
                          String details, Instant createdAt) {
            this.auditId = auditId;
            this.complaintId = complaintId;
            this.actorId = actorId;
            this.actorName = actorName != null ? actorName : "System";
            this.actorRole = actorRole;
            this.action = action;
            this.previousState = previousState;
            this.newState = newState;
            this.details = details;
            this.createdAt = createdAt;
        }
    }

    /**
     * Appends an immutable audit entry to the audit log.
     */
    public static void logEvent(
            Connection conn,
            int complaintId,
            Integer actorId,
            String actorRole,
            String action,
            String prevState,
            String newState,
            String details) throws SQLException {

        String sql = "INSERT INTO complaint_audit_log (complaint_id, actor_id, actor_role, action, " +
                     "previous_state, new_state, details, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            if (actorId != null && actorId > 0) {
                ps.setInt(2, actorId);
            } else {
                ps.setNull(2, java.sql.Types.INTEGER);
            }
            ps.setString(3, actorRole != null ? actorRole.toUpperCase() : "SYSTEM");
            ps.setString(4, action);
            ps.setString(5, prevState);
            ps.setString(6, newState);
            ps.setString(7, details);
            ps.executeUpdate();
        }
    }

    /**
     * Retrieves the chronological audit history for a specific complaint.
     */
    public static List<AuditEntry> getAuditHistory(Connection conn, int complaintId) throws SQLException {
        List<AuditEntry> list = new ArrayList<>();
        String sql = "SELECT a.audit_id, a.complaint_id, a.actor_id, u.full_name as actor_name, " +
                     "a.actor_role, a.action, a.previous_state, a.new_state, a.details, a.created_at " +
                     "FROM complaint_audit_log a " +
                     "LEFT JOIN users u ON a.actor_id = u.user_id " +
                     "WHERE a.complaint_id = ? " +
                     "ORDER BY a.created_at ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AuditEntry(
                            rs.getInt("audit_id"),
                            rs.getInt("complaint_id"),
                            (Integer) rs.getObject("actor_id"),
                            rs.getString("actor_name"),
                            rs.getString("actor_role"),
                            rs.getString("action"),
                            rs.getString("previous_state"),
                            rs.getString("new_state"),
                            rs.getString("details"),
                            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
                    ));
                }
            }
        }
        return list;
    }
}
