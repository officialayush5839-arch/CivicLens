package civiclens.triage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Backward compatibility facade delegating to PriorityOverrideService.
 */
public class TriageOverrideService {

    public static class OverrideRecord extends PriorityOverrideService.OverrideRecord {
        public OverrideRecord(int overrideId, int complaintId, int userId, String userName,
                              String previousPriority, String newPriority,
                              String originalRecommendation, String reason, java.time.Instant createdAt) {
            super(overrideId, complaintId, userId, userName, previousPriority, newPriority,
                  originalRecommendation, reason, createdAt);
        }
    }

    public static boolean recordOverride(
            Connection conn,
            int complaintId,
            int userId,
            String newPriority,
            String reason,
            String originalRecommendation) throws SQLException {
        return PriorityOverrideService.recordOverride(conn, complaintId, userId, newPriority, reason, originalRecommendation);
    }

    public static boolean recordOverride(
            Connection conn,
            int complaintId,
            int userId,
            String newPriority,
            String reason) throws SQLException {
        return PriorityOverrideService.recordOverride(conn, complaintId, userId, newPriority, reason);
    }

    public static List<PriorityOverrideService.OverrideRecord> getOverridesForComplaint(Connection conn, int complaintId) throws SQLException {
        return PriorityOverrideService.getOverridesForComplaint(conn, complaintId);
    }

    public static List<PriorityOverrideService.OverrideRecord> getOverrideHistory(Connection conn, int complaintId) throws SQLException {
        return PriorityOverrideService.getOverrideHistory(conn, complaintId);
    }
}
