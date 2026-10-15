import civiclens.triage.PriorityOverrideService.OverrideRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Default package service and DAO for recording priority overrides.
 * Delegates to civiclens.triage.PriorityOverrideService.
 */
public class PriorityOverrideService {

    public static boolean recordOverride(
            Connection conn,
            int complaintId,
            int userId,
            String newPriority,
            String reason,
            String originalRecommendation) throws SQLException {
        return civiclens.triage.PriorityOverrideService.recordOverride(
                conn, complaintId, userId, newPriority, reason, originalRecommendation
        );
    }

    public static boolean recordOverride(
            Connection conn,
            int complaintId,
            int userId,
            String newPriority,
            String reason) throws SQLException {
        return civiclens.triage.PriorityOverrideService.recordOverride(
                conn, complaintId, userId, newPriority, reason
        );
    }

    public static List<OverrideRecord> getOverrideHistory(Connection conn, int complaintId) throws SQLException {
        return civiclens.triage.PriorityOverrideService.getOverrideHistory(conn, complaintId);
    }

    public static List<OverrideRecord> getOverridesForComplaint(Connection conn, int complaintId) throws SQLException {
        return civiclens.triage.PriorityOverrideService.getOverridesForComplaint(conn, complaintId);
    }
}
