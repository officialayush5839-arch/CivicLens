package civiclens.lifecycle;

import civiclens.audit.AuditTrailService;

import java.sql.*;
import java.time.Instant;

/**
 * Service managing accountable resolution, feedback ratings, authenticated citizen appeals,
 * and verified lifecycle state progressions.
 */
public class ResolutionService {

    public static class ResolutionRecord {
        public final int resolutionId;
        public final int complaintId;
        public final int employeeId;
        public final String employeeName;
        public final String actionTaken;
        public final String resolutionCategory;
        public final String evidenceNotes;
        public final String internalNotes;
        public final Instant resolvedAt;

        public ResolutionRecord(int resolutionId, int complaintId, int employeeId, String employeeName,
                                String actionTaken, String resolutionCategory, String evidenceNotes,
                                String internalNotes, Instant resolvedAt) {
            this.resolutionId = resolutionId;
            this.complaintId = complaintId;
            this.employeeId = employeeId;
            this.employeeName = employeeName;
            this.actionTaken = actionTaken;
            this.resolutionCategory = resolutionCategory;
            this.evidenceNotes = evidenceNotes;
            this.internalNotes = internalNotes;
            this.resolvedAt = resolvedAt;
        }
    }

    /**
     * Assigns a submitted complaint to an employee.
     */
    public static boolean assignComplaint(Connection conn, int complaintId, int employeeId, int assignedBy) throws SQLException {
        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            // Fetch current state
            ComplaintState currentState = getComplaintState(conn, complaintId);
            LifecycleStateMachine.validateTransition(currentState, ComplaintState.ASSIGNED);

            // Check employee role
            String roleSql = "SELECT role FROM users WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(roleSql)) {
                ps.setInt(1, employeeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || (!"EMPLOYEE".equalsIgnoreCase(rs.getString("role")) && !"ADMIN".equalsIgnoreCase(rs.getString("role")))) {
                        throw new IllegalArgumentException("Target assignee must be an EMPLOYEE or ADMIN");
                    }
                }
            }

            // Update complaint
            String updateSql = "UPDATE complaints SET assigned_to = ?, status = 'ASSIGNED', updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, employeeId);
                ps.setInt(2, complaintId);
                ps.executeUpdate();
            }

            AuditTrailService.logEvent(conn, complaintId, assignedBy, "STAFF", "COMPLAINT_ASSIGNED",
                    currentState.name(), "ASSIGNED", "Assigned to staff user ID: " + employeeId);

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
     * Staff acknowledges and begins working on an assigned complaint.
     */
    public static boolean startWork(Connection conn, int complaintId, int actorId) throws SQLException {
        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            ComplaintState currentState = getComplaintState(conn, complaintId);
            LifecycleStateMachine.validateTransition(currentState, ComplaintState.IN_PROGRESS);

            String updateSql = "UPDATE complaints SET status = 'IN_PROGRESS', updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, complaintId);
                ps.executeUpdate();
            }

            AuditTrailService.logEvent(conn, complaintId, actorId, "STAFF", "WORK_STARTED",
                    currentState.name(), "IN_PROGRESS", "Investigation and remedial action commenced.");

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
     * Resolves a complaint with mandatory action taken and resolution evidence.
     */
    public static boolean resolveComplaint(
            Connection conn,
            int complaintId,
            int employeeId,
            String actionTaken,
            String resolutionCategory,
            String evidenceNotes,
            String internalNotes) throws SQLException {

        if (actionTaken == null || actionTaken.trim().length() < 5) {
            throw new IllegalArgumentException("Action taken must be documented (at least 5 characters)");
        }
        if (resolutionCategory == null || resolutionCategory.trim().isEmpty()) {
            throw new IllegalArgumentException("Resolution category is mandatory");
        }

        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            ComplaintState currentState = getComplaintState(conn, complaintId);
            // If ASSIGNED, allow progression to IN_PROGRESS then RESOLVED
            if (currentState == ComplaintState.ASSIGNED) {
                currentState = ComplaintState.IN_PROGRESS;
            }
            LifecycleStateMachine.validateTransition(currentState, ComplaintState.RESOLVED);

            // 1. Insert or update structured resolution record
            String checkResSql = "SELECT resolution_id FROM resolution_records WHERE complaint_id = ?";
            boolean hasExisting = false;
            try (PreparedStatement checkPs = conn.prepareStatement(checkResSql)) {
                checkPs.setInt(1, complaintId);
                try (ResultSet rs = checkPs.executeQuery()) {
                    hasExisting = rs.next();
                }
            }

            if (hasExisting) {
                String updateResSql = "UPDATE resolution_records SET employee_id = ?, action_taken = ?, " +
                                      "resolution_category = ?, evidence_notes = ?, internal_notes = ?, " +
                                      "resolved_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateResSql)) {
                    ps.setInt(1, employeeId);
                    ps.setString(2, actionTaken.trim());
                    ps.setString(3, resolutionCategory.trim());
                    ps.setString(4, evidenceNotes != null ? evidenceNotes.trim() : null);
                    ps.setString(5, internalNotes != null ? internalNotes.trim() : null);
                    ps.setInt(6, complaintId);
                    ps.executeUpdate();
                }
            } else {
                String resSql = "INSERT INTO resolution_records (complaint_id, employee_id, action_taken, " +
                                "resolution_category, evidence_notes, internal_notes, resolved_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
                try (PreparedStatement ps = conn.prepareStatement(resSql)) {
                    ps.setInt(1, complaintId);
                    ps.setInt(2, employeeId);
                    ps.setString(3, actionTaken.trim());
                    ps.setString(4, resolutionCategory.trim());
                    ps.setString(5, evidenceNotes != null ? evidenceNotes.trim() : null);
                    ps.setString(6, internalNotes != null ? internalNotes.trim() : null);
                    ps.executeUpdate();
                }
            }

            // 2. Update complaint status
            String updateCompSql = "UPDATE complaints SET status = 'RESOLVED', updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateCompSql)) {
                ps.setInt(1, complaintId);
                ps.executeUpdate();
            }

            // 3. Mark SLA completed
            String updateSlaSql = "UPDATE sla_deadlines SET sla_state = 'COMPLETED', updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSlaSql)) {
                ps.setInt(1, complaintId);
                ps.executeUpdate();
            }

            // 4. Log audit event
            AuditTrailService.logEvent(conn, complaintId, employeeId, "STAFF", "COMPLAINT_RESOLVED",
                    currentState.name(), "RESOLVED", "Action: " + actionTaken.trim() + " | Category: " + resolutionCategory);

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
     * Submits post-resolution citizen satisfaction feedback.
     */
    public static boolean submitFeedback(
            Connection conn,
            int complaintId,
            int citizenId,
            int rating,
            String comments) throws SQLException {

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Feedback rating must be an integer between 1 and 5");
        }

        verifyCitizenOwnership(conn, complaintId, citizenId);

        ComplaintState state = getComplaintState(conn, complaintId);
        if (state != ComplaintState.RESOLVED && state != ComplaintState.CLOSED) {
            throw new IllegalStateException("Feedback can only be submitted for RESOLVED or CLOSED complaints");
        }

        String sql = "INSERT INTO citizen_feedback (complaint_id, citizen_id, rating, comments, created_at) " +
                     "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            ps.setInt(2, citizenId);
            ps.setInt(3, rating);
            ps.setString(4, comments != null ? comments.trim() : null);
            ps.executeUpdate();
        }

        AuditTrailService.logEvent(conn, complaintId, citizenId, "CITIZEN", "FEEDBACK_SUBMITTED",
                state.name(), state.name(), "Rating: " + rating + "/5" + (comments != null ? " - " + comments.trim() : ""));

        return true;
    }

    /**
     * Authenticated citizen appeals or requests to reopen a resolved complaint.
     */
    public static int submitAppeal(
            Connection conn,
            int complaintId,
            int citizenId,
            String appealReason) throws SQLException {

        if (appealReason == null || appealReason.trim().length() < 10) {
            throw new IllegalArgumentException("Appeal reason must provide substantiated detail (at least 10 characters)");
        }

        verifyCitizenOwnership(conn, complaintId, citizenId);

        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            ComplaintState currentState = getComplaintState(conn, complaintId);
            LifecycleStateMachine.validateTransition(currentState, ComplaintState.APPEALED);

            // 1. Insert appeal record
            int appealId = -1;
            String insertSql = "INSERT INTO complaint_appeals (complaint_id, citizen_id, appeal_reason, status, submitted_at) " +
                               "VALUES (?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, complaintId);
                ps.setInt(2, citizenId);
                ps.setString(3, appealReason.trim());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) appealId = rs.getInt(1);
                }
            }

            // 2. Update complaint state
            String updateSql = "UPDATE complaints SET status = 'APPEALED', updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, complaintId);
                ps.executeUpdate();
            }

            // 3. Log audit event
            AuditTrailService.logEvent(conn, complaintId, citizenId, "CITIZEN", "COMPLAINT_APPEALED",
                    currentState.name(), "APPEALED", "Appeal Reason: " + appealReason.trim());

            conn.commit();
            return appealId;
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(prevAutoCommit);
        }
    }

    /**
     * Administrative review of an appeal (Acceptance reopens complaint; rejection closes it).
     */
    public static boolean reviewAppeal(
            Connection conn,
            int appealId,
            int adminId,
            boolean approve,
            String reviewNotes) throws SQLException {

        boolean prevAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);

            int complaintId = -1;
            String selectSql = "SELECT complaint_id, status FROM complaint_appeals WHERE appeal_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setInt(1, appealId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        complaintId = rs.getInt("complaint_id");
                        if (!"PENDING".equalsIgnoreCase(rs.getString("status"))) {
                            throw new IllegalStateException("Appeal #" + appealId + " has already been reviewed");
                        }
                    } else {
                        throw new SQLException("Appeal not found with ID: " + appealId);
                    }
                }
            }

            ComplaintState nextState = approve ? ComplaintState.IN_PROGRESS : ComplaintState.CLOSED;
            LifecycleStateMachine.validateTransition(ComplaintState.APPEALED, nextState);

            // 1. Update appeal record
            String updateAppealSql = "UPDATE complaint_appeals SET status = ?, reviewed_by = ?, review_notes = ?, " +
                                     "reviewed_at = CURRENT_TIMESTAMP WHERE appeal_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateAppealSql)) {
                ps.setString(1, approve ? "ACCEPTED" : "REJECTED");
                ps.setInt(2, adminId);
                ps.setString(3, reviewNotes != null ? reviewNotes.trim() : "");
                ps.setInt(4, appealId);
                ps.executeUpdate();
            }

            // 2. Update complaint status
            String updateCompSql = "UPDATE complaints SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateCompSql)) {
                ps.setString(1, nextState.name());
                ps.setInt(2, complaintId);
                ps.executeUpdate();
            }

            // 3. Log audit event
            String action = approve ? "APPEAL_ACCEPTED_REOPENED" : "APPEAL_REJECTED_CLOSED";
            AuditTrailService.logEvent(conn, complaintId, adminId, "ADMIN", action,
                    "APPEALED", nextState.name(), "Review Decision: " + (approve ? "Accepted" : "Rejected") +
                    " | Notes: " + (reviewNotes != null ? reviewNotes.trim() : "None"));

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
     * Formally closes a resolved complaint.
     */
    public static boolean closeComplaint(Connection conn, int complaintId, int actorId, String actorRole) throws SQLException {
        ComplaintState current = getComplaintState(conn, complaintId);
        LifecycleStateMachine.validateTransition(current, ComplaintState.CLOSED);

        String sql = "UPDATE complaints SET status = 'CLOSED', updated_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            ps.executeUpdate();
        }

        AuditTrailService.logEvent(conn, complaintId, actorId, actorRole, "COMPLAINT_CLOSED",
                current.name(), "CLOSED", "Complaint formally closed.");
        return true;
    }

    private static ComplaintState getComplaintState(Connection conn, int complaintId) throws SQLException {
        String sql = "SELECT status FROM complaints WHERE complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return ComplaintState.fromString(rs.getString("status"));
                } else {
                    throw new SQLException("Complaint not found with ID: " + complaintId);
                }
            }
        }
    }

    private static void verifyCitizenOwnership(Connection conn, int complaintId, int citizenId) throws SQLException {
        String sql = "SELECT user_id FROM complaints WHERE complaint_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, complaintId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int ownerId = rs.getInt("user_id");
                    if (ownerId != citizenId) {
                        throw new SecurityException("Unauthorized: Citizen #" + citizenId +
                                " cannot mutate complaint #" + complaintId + " owned by Citizen #" + ownerId);
                    }
                } else {
                    throw new SQLException("Complaint not found with ID: " + complaintId);
                }
            }
        }
    }
}
