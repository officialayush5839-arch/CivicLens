package civiclens.similarity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to manage incident groups and multi-complaint incident linking.
 * Preserves independent complaint identities, citizen status tracking,
 * and maintains complete accountability for link and unlink actions.
 */
public class IncidentManagementService {

    public static class IncidentGroup {
        public final int incidentId;
        public final String incidentCode;
        public final String title;
        public final String description;
        public final String category;
        public final String severity;
        public final String status;
        public final int createdBy;
        public final Instant createdAt;
        public final int memberCount;

        public IncidentGroup(int incidentId, String incidentCode, String title, String description,
                             String category, String severity, String status, int createdBy,
                             Instant createdAt, int memberCount) {
            this.incidentId = incidentId;
            this.incidentCode = incidentCode;
            this.title = title;
            this.description = description;
            this.category = category;
            this.severity = severity;
            this.status = status;
            this.createdBy = createdBy;
            this.createdAt = createdAt;
            this.memberCount = memberCount;
        }
    }

    public static class IncidentMember {
        public final int memberId;
        public final int incidentId;
        public final int complaintId;
        public final String complaintTitle;
        public final String complaintStatus;
        public final String complaintPriority;
        public final double similarityScore;
        public final int linkedBy;
        public final String linkerName;
        public final String reason;
        public final Instant linkedAt;

        public IncidentMember(int memberId, int incidentId, int complaintId, String complaintTitle,
                              String complaintStatus, String complaintPriority, double similarityScore,
                              int linkedBy, String linkerName, String reason, Instant linkedAt) {
            this.memberId = memberId;
            this.incidentId = incidentId;
            this.complaintId = complaintId;
            this.complaintTitle = complaintTitle;
            this.complaintStatus = complaintStatus;
            this.complaintPriority = complaintPriority;
            this.similarityScore = similarityScore;
            this.linkedBy = linkedBy;
            this.linkerName = linkerName;
            this.reason = reason;
            this.linkedAt = linkedAt;
        }
    }

    /**
     * Creates a new central incident group.
     */
    public static int createIncidentGroup(
            Connection conn,
            String title,
            String description,
            String category,
            String severity,
            int createdBy) throws SQLException {

        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Incident title cannot be empty");
        }
        String incidentCode = "INC-" + System.currentTimeMillis() % 1000000;

        String sql = "INSERT INTO incident_groups (incident_code, title, description, category, severity, status, created_by, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, 'OPEN', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, incidentCode);
            ps.setString(2, title.trim());
            ps.setString(3, description != null ? description.trim() : "");
            ps.setString(4, category != null ? category.trim() : "General");
            ps.setString(5, severity != null ? severity.trim().toUpperCase() : "MEDIUM");
            ps.setInt(6, createdBy);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    /**
     * Links a complaint into an incident group without merging or deleting the complaint.
     */
    public static boolean linkComplaintToIncident(
            Connection conn,
            int incidentId,
            int complaintId,
            double similarityScore,
            int linkedBy,
            String reason) throws SQLException {

        if (reason == null || reason.trim().length() < 3) {
            throw new IllegalArgumentException("Linking reason must be documented (at least 3 characters)");
        }

        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            // 1. Check if already linked to this incident
            String checkSql = "SELECT member_id FROM incident_members WHERE incident_id = ? AND complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, incidentId);
                ps.setInt(2, complaintId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new SQLException("Complaint #" + complaintId + " is already linked to Incident #" + incidentId);
                    }
                }
            }

            // 2. Insert member record
            String insertSql = "INSERT INTO incident_members (incident_id, complaint_id, similarity_score, linked_by, reason, linked_at) " +
                               "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, incidentId);
                ps.setInt(2, complaintId);
                ps.setDouble(3, similarityScore);
                ps.setInt(4, linkedBy);
                ps.setString(5, reason.trim());
                ps.executeUpdate();
            }

            // 3. Record audit trail
            String auditSql = "INSERT INTO complaint_audit_log (complaint_id, actor_id, actor_role, action, details, created_at) " +
                              "VALUES (?, ?, 'STAFF', 'INCIDENT_LINKED', ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement ps = conn.prepareStatement(auditSql)) {
                ps.setInt(1, complaintId);
                ps.setInt(2, linkedBy);
                ps.setString(3, "Linked to Incident #" + incidentId + " (Score: " + String.format("%.2f", similarityScore) + "). Reason: " + reason.trim());
                ps.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    /**
     * Unlinks a complaint from an incident group with audit documentation.
     */
    public static boolean unlinkComplaintFromIncident(
            Connection conn,
            int incidentId,
            int complaintId,
            int unlinkedBy,
            String reason) throws SQLException {

        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            String deleteSql = "DELETE FROM incident_members WHERE incident_id = ? AND complaint_id = ?";
            int affected;
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, incidentId);
                ps.setInt(2, complaintId);
                affected = ps.executeUpdate();
            }

            if (affected > 0) {
                String auditSql = "INSERT INTO complaint_audit_log (complaint_id, actor_id, actor_role, action, details, created_at) " +
                                  "VALUES (?, ?, 'STAFF', 'INCIDENT_UNLINKED', ?, CURRENT_TIMESTAMP)";
                try (PreparedStatement ps = conn.prepareStatement(auditSql)) {
                    ps.setInt(1, complaintId);
                    ps.setInt(2, unlinkedBy);
                    ps.setString(3, "Unlinked from Incident #" + incidentId + ". Rationale: " + (reason != null ? reason : "Administrative detachment"));
                    ps.executeUpdate();
                }
            }

            conn.commit();
            return affected > 0;
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    /**
     * Retrieves all members associated with an incident group.
     */
    public static List<IncidentMember> getIncidentMembers(Connection conn, int incidentId) throws SQLException {
        List<IncidentMember> list = new ArrayList<>();
        String sql = "SELECT m.member_id, m.incident_id, m.complaint_id, c.title, c.status, c.priority, " +
                     "m.similarity_score, m.linked_by, u.full_name as linker_name, m.reason, m.linked_at " +
                     "FROM incident_members m " +
                     "JOIN complaints c ON m.complaint_id = c.complaint_id " +
                     "LEFT JOIN users u ON m.linked_by = u.user_id " +
                     "WHERE m.incident_id = ? ORDER BY m.linked_at ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, incidentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new IncidentMember(
                            rs.getInt("member_id"),
                            rs.getInt("incident_id"),
                            rs.getInt("complaint_id"),
                            rs.getString("title"),
                            rs.getString("status"),
                            rs.getString("priority"),
                            rs.getDouble("similarity_score"),
                            rs.getInt("linked_by"),
                            rs.getString("linker_name"),
                            rs.getString("reason"),
                            rs.getTimestamp("linked_at") != null ? rs.getTimestamp("linked_at").toInstant() : Instant.now()
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Retrieves all incident groups.
     */
    public static List<IncidentGroup> getAllIncidentGroups(Connection conn) throws SQLException {
        List<IncidentGroup> list = new ArrayList<>();
        String sql = "SELECT g.incident_id, g.incident_code, g.title, g.description, g.category, " +
                     "g.severity, g.status, g.created_by, g.created_at, " +
                     "COUNT(m.member_id) as member_count " +
                     "FROM incident_groups g " +
                     "LEFT JOIN incident_members m ON g.incident_id = m.incident_id " +
                     "GROUP BY g.incident_id, g.incident_code, g.title, g.description, g.category, g.severity, g.status, g.created_by, g.created_at " +
                     "ORDER BY g.created_at DESC";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new IncidentGroup(
                        rs.getInt("incident_id"),
                        rs.getString("incident_code"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("category"),
                        rs.getString("severity"),
                        rs.getString("status"),
                        rs.getInt("created_by"),
                        rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now(),
                        rs.getInt("member_count")
                ));
            }
        }
        return list;
    }
}
