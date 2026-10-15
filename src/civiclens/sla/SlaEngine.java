package civiclens.sla;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * CivicLens SLA Management & Escalation Engine.
 * Provides configurable policies, real-time deterministic status evaluation,
 * idempotent escalation, and system startup reconciliation for offline downtime.
 */
public class SlaEngine {

    /**
     * Resolves the active SLA policy for a given priority and category.
     * Evaluates category-specific policy first, falling back to wildcard default.
     */
    public static SlaPolicy getPolicy(Connection conn, String priority, String category) throws SQLException {
        String cleanPriority = priority != null ? priority.trim().toUpperCase() : "MEDIUM";
        String cleanCategory = category != null ? category.trim() : "*";

        String sql = "SELECT policy_id, priority, category, response_time_hours, resolution_time_hours, " +
                     "warning_threshold_percent, is_active " +
                     "FROM sla_policies " +
                     "WHERE priority = ? AND (category = ? OR category = '*') AND is_active = 1 " +
                     "ORDER BY CASE WHEN category = '*' THEN 2 ELSE 1 END LIMIT 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cleanPriority);
            ps.setString(2, cleanCategory);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SlaPolicy(
                            rs.getInt("policy_id"),
                            rs.getString("priority"),
                            rs.getString("category"),
                            rs.getInt("response_time_hours"),
                            rs.getInt("resolution_time_hours"),
                            rs.getDouble("warning_threshold_percent"),
                            rs.getBoolean("is_active")
                    );
                }
            }
        }

        // Fallback default policies if database is unseeded
        int responseHours = 12;
        int resolutionHours = 72;
        if ("CRITICAL".equals(cleanPriority)) { responseHours = 1; resolutionHours = 4; }
        else if ("HIGH".equals(cleanPriority)) { responseHours = 4; resolutionHours = 24; }
        else if ("LOW".equals(cleanPriority)) { responseHours = 24; resolutionHours = 168; }

        return new SlaPolicy(0, cleanPriority, "*", responseHours, resolutionHours, 75.0, true);
    }

    /**
     * Initializes or updates deterministic response & resolution deadlines for a complaint.
     */
    public static void initializeDeadlines(
            Connection conn,
            int complaintId,
            String priority,
            String category,
            Instant createdAt) throws SQLException {

        SlaPolicy policy = getPolicy(conn, priority, category);
        Instant responseDeadline = createdAt.plus(Duration.ofHours(policy.getResponseTimeHours()));
        Instant resolutionDeadline = createdAt.plus(Duration.ofHours(policy.getResolutionTimeHours()));

        // Check if deadline record exists
        String checkSql = "SELECT deadline_id FROM sla_deadlines WHERE complaint_id = ?";
        boolean exists = false;
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                exists = rs.next();
            }
        }

        if (exists) {
            String updateSql = "UPDATE sla_deadlines SET response_deadline = ?, resolution_deadline = ?, " +
                               "updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setTimestamp(1, Timestamp.from(responseDeadline));
                ps.setTimestamp(2, Timestamp.from(resolutionDeadline));
                ps.setInt(3, complaintId);
                ps.executeUpdate();
            }
        } else {
            String insertSql = "INSERT INTO sla_deadlines (complaint_id, response_deadline, resolution_deadline, " +
                               "sla_state, is_escalated, created_at, updated_at) " +
                               "VALUES (?, ?, ?, 'ON_TRACK', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, complaintId);
                ps.setTimestamp(2, Timestamp.from(responseDeadline));
                ps.setTimestamp(3, Timestamp.from(resolutionDeadline));
                ps.executeUpdate();
            }
        }
    }

    /**
     * Retrieves current real-time SLA deadline info and deterministic state for a complaint.
     */
    public static SlaDeadlineInfo getDeadlineInfo(Connection conn, int complaintId) throws SQLException {
        String sql = "SELECT c.priority, c.category, c.status, c.created_at, " +
                     "d.response_deadline, d.resolution_deadline, d.sla_state, d.is_escalated, d.escalated_at " +
                     "FROM complaints c " +
                     "LEFT JOIN sla_deadlines d ON c.complaint_id = d.complaint_id " +
                     "WHERE c.complaint_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String priority = rs.getString("priority");
                    String category = rs.getString("category");
                    String status = rs.getString("status");
                    Timestamp createdTs = rs.getTimestamp("created_at");
                    Instant createdAt = createdTs != null ? createdTs.toInstant() : Instant.now();

                    Timestamp respTs = rs.getTimestamp("response_deadline");
                    Timestamp resolTs = rs.getTimestamp("resolution_deadline");

                    // If deadlines were not previously initialized, initialize them now
                    if (resolTs == null) {
                        initializeDeadlines(conn, complaintId, priority, category, createdAt);
                        return getDeadlineInfo(conn, complaintId);
                    }

                    Instant responseDeadline = respTs.toInstant();
                    Instant resolutionDeadline = resolTs.toInstant();
                    boolean isEscalated = rs.getBoolean("is_escalated");
                    Timestamp escTs = rs.getTimestamp("escalated_at");
                    Instant escalatedAt = escTs != null ? escTs.toInstant() : null;

                    Instant now = Instant.now();
                    Duration totalDuration = Duration.between(createdAt, resolutionDeadline);
                    Duration elapsedDuration = Duration.between(createdAt, now);
                    Duration remaining = Duration.between(now, resolutionDeadline);

                    double percentElapsed = 0.0;
                    if (totalDuration.toMillis() > 0) {
                        percentElapsed = ((double) elapsedDuration.toMillis() / totalDuration.toMillis()) * 100.0;
                    }

                    // Compute deterministic SLA state
                    SlaStatus state;
                    if ("RESOLVED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)) {
                        state = SlaStatus.COMPLETED;
                    } else if ("REJECTED".equalsIgnoreCase(status)) {
                        state = SlaStatus.PAUSED;
                    } else if (isEscalated) {
                        state = SlaStatus.ESCALATED;
                    } else if (now.isAfter(resolutionDeadline)) {
                        state = SlaStatus.OVERDUE;
                    } else if (percentElapsed >= 75.0) {
                        state = SlaStatus.APPROACHING_DEADLINE;
                    } else {
                        state = SlaStatus.ON_TRACK;
                    }

                    return new SlaDeadlineInfo(
                            complaintId, priority, category, createdAt,
                            responseDeadline, resolutionDeadline,
                            state, isEscalated, escalatedAt,
                            remaining, percentElapsed
                    );
                }
            }
        }
        return null;
    }

    /**
     * Scans for breached complaints and escalates them idempotently.
     * Prevents repeated duplicate audit events for the same breach condition.
     *
     * @return Number of newly escalated complaints
     */
    public static int escalateOverdueComplaints(Connection conn) throws SQLException {
        String query = "SELECT c.complaint_id, c.title, c.status, c.priority, c.assigned_to, u.full_name as assignee_name, " +
                       "d.resolution_deadline, c.created_at " +
                       "FROM complaints c " +
                       "JOIN sla_deadlines d ON c.complaint_id = d.complaint_id " +
                       "LEFT JOIN users u ON c.assigned_to = u.user_id " +
                       "WHERE c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') " +
                       "AND d.resolution_deadline < CURRENT_TIMESTAMP " +
                       "AND d.is_escalated = 0";

        List<Integer> toEscalate = new ArrayList<>();
        List<String> auditLogs = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int cid = rs.getInt("complaint_id");
                String assignee = rs.getString("assignee_name");
                Timestamp deadline = rs.getTimestamp("resolution_deadline");
                toEscalate.add(cid);

                String details = "SLA Target Breached: Resolution deadline was " + deadline +
                                 ". Assigned staff at breach: " + (assignee != null ? assignee : "Unassigned") +
                                 ". System escalated complaint for supervisory intervention.";
                auditLogs.add(details);
            }
        }

        if (toEscalate.isEmpty()) {
            return 0;
        }

        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            String updateSql = "UPDATE sla_deadlines SET sla_state = 'ESCALATED', is_escalated = 1, " +
                               "escalated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                               "WHERE complaint_id = ?";
            String auditSql = "INSERT INTO complaint_audit_log (complaint_id, actor_id, actor_role, action, " +
                              "previous_state, new_state, details, created_at) " +
                              "VALUES (?, NULL, 'SYSTEM', 'SLA_BREACH_ESCALATED', 'ACTIVE', 'ESCALATED', ?, CURRENT_TIMESTAMP)";

            try (PreparedStatement updatePs = conn.prepareStatement(updateSql);
                 PreparedStatement auditPs = conn.prepareStatement(auditSql)) {

                for (int i = 0; i < toEscalate.size(); i++) {
                    int cid = toEscalate.get(i);
                    String details = auditLogs.get(i);

                    updatePs.setInt(1, cid);
                    updatePs.executeUpdate();

                    auditPs.setInt(1, cid);
                    auditPs.setString(2, details);
                    auditPs.executeUpdate();
                }
            }

            conn.commit();
            return toEscalate.size();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    /**
     * Application startup downtime reconciliation.
     * Evaluates whether deadlines passed while the application was offline,
     * recording recovery audit events without corrupting state.
     */
    public static int reconcileDowntime(Connection conn) throws SQLException {
        String query = "SELECT c.complaint_id, d.resolution_deadline " +
                       "FROM complaints c " +
                       "JOIN sla_deadlines d ON c.complaint_id = d.complaint_id " +
                       "WHERE c.status NOT IN ('RESOLVED', 'CLOSED', 'REJECTED') " +
                       "AND d.resolution_deadline < CURRENT_TIMESTAMP " +
                       "AND d.is_escalated = 0";

        List<Integer> offlineBreaches = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                offlineBreaches.add(rs.getInt("complaint_id"));
            }
        }

        if (offlineBreaches.isEmpty()) {
            return 0;
        }

        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            String updateSql = "UPDATE sla_deadlines SET sla_state = 'ESCALATED', is_escalated = 1, " +
                               "escalated_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                               "WHERE complaint_id = ?";
            String auditSql = "INSERT INTO complaint_audit_log (complaint_id, actor_id, actor_role, action, " +
                              "previous_state, new_state, details, created_at) " +
                              "VALUES (?, NULL, 'SYSTEM', 'DOWNTIME_SLA_RECONCILIATION', 'OFFLINE', 'ESCALATED', ?, CURRENT_TIMESTAMP)";

            try (PreparedStatement updatePs = conn.prepareStatement(updateSql);
                 PreparedStatement auditPs = conn.prepareStatement(auditSql)) {

                for (int cid : offlineBreaches) {
                    updatePs.setInt(1, cid);
                    updatePs.executeUpdate();

                    auditPs.setInt(1, cid);
                    auditPs.setString(2, "Downtime Reconciliation: SLA deadline elapsed while system was offline. Reconciled upon startup.");
                    auditPs.executeUpdate();
                }
            }

            conn.commit();
            return offlineBreaches.size();
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }
}
