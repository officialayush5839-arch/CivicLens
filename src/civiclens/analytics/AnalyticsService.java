package civiclens.analytics;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Outcome-focused operational analytics service.
 * Computes zero-division safe SLA compliance rates, genuine median resolution times,
 * appeal/reopen rates, priority override rates, and employee caseload distributions.
 */
public class AnalyticsService {

    public static class AnalyticsFilter {
        public final String category;
        public final String priority;
        public final String status;
        public final Instant startDate;
        public final Instant endDate;

        public AnalyticsFilter(String category, String priority, String status, Instant startDate, Instant endDate) {
            this.category = (category != null && !category.equalsIgnoreCase("ALL")) ? category.trim() : null;
            this.priority = (priority != null && !priority.equalsIgnoreCase("ALL")) ? priority.trim() : null;
            this.status = (status != null && !status.equalsIgnoreCase("ALL")) ? status.trim() : null;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public static AnalyticsFilter all() {
            return new AnalyticsFilter(null, null, null, null, null);
        }
    }

    public static class OperationalMetrics {
        public final int totalComplaints;
        public final int resolvedComplaints;
        public final int unresolvedComplaints;
        public final double resolutionRatePercent;

        public final int slaCompliantCount;
        public final int slaBreachedCount;
        public final double trueSlaCompliancePercent;

        public final int appealedCount;
        public final double appealRatePercent;

        public final int overrideCount;
        public final double overrideRatePercent;

        public final double medianResolutionHoursOverall;
        public final Map<String, Double> medianResolutionHoursByCategory;
        public final Map<String, Double> medianResolutionHoursByPriority;

        public final Map<String, Integer> statusDistribution;
        public final Map<String, Integer> priorityDistribution;
        public final Map<String, Integer> categoryDistribution;
        public final Map<String, Integer> caseloadByEmployee;

        public OperationalMetrics(
                int totalComplaints,
                int resolvedComplaints,
                int unresolvedComplaints,
                double resolutionRatePercent,
                int slaCompliantCount,
                int slaBreachedCount,
                double trueSlaCompliancePercent,
                int appealedCount,
                double appealRatePercent,
                int overrideCount,
                double overrideRatePercent,
                double medianResolutionHoursOverall,
                Map<String, Double> medianResolutionHoursByCategory,
                Map<String, Double> medianResolutionHoursByPriority,
                Map<String, Integer> statusDistribution,
                Map<String, Integer> priorityDistribution,
                Map<String, Integer> categoryDistribution,
                Map<String, Integer> caseloadByEmployee) {
            this.totalComplaints = totalComplaints;
            this.resolvedComplaints = resolvedComplaints;
            this.unresolvedComplaints = unresolvedComplaints;
            this.resolutionRatePercent = resolutionRatePercent;
            this.slaCompliantCount = slaCompliantCount;
            this.slaBreachedCount = slaBreachedCount;
            this.trueSlaCompliancePercent = trueSlaCompliancePercent;
            this.appealedCount = appealedCount;
            this.appealRatePercent = appealRatePercent;
            this.overrideCount = overrideCount;
            this.overrideRatePercent = overrideRatePercent;
            this.medianResolutionHoursOverall = medianResolutionHoursOverall;
            this.medianResolutionHoursByCategory = Collections.unmodifiableMap(medianResolutionHoursByCategory);
            this.medianResolutionHoursByPriority = Collections.unmodifiableMap(medianResolutionHoursByPriority);
            this.statusDistribution = Collections.unmodifiableMap(statusDistribution);
            this.priorityDistribution = Collections.unmodifiableMap(priorityDistribution);
            this.categoryDistribution = Collections.unmodifiableMap(categoryDistribution);
            this.caseloadByEmployee = Collections.unmodifiableMap(caseloadByEmployee);
        }
    }

    /**
     * Computes comprehensive, verified operational metrics against the database.
     */
    public static OperationalMetrics computeMetrics(Connection conn, AnalyticsFilter filter) throws SQLException {
        if (filter == null) filter = AnalyticsFilter.all();

        // 1. Fetch complaints matching filter
        StringBuilder sb = new StringBuilder("SELECT c.complaint_id, c.category, c.priority, c.status, " +
                "c.created_at, c.updated_at, c.assigned_to, u.full_name as assignee_name, " +
                "r.resolved_at, d.resolution_deadline, d.is_escalated " +
                "FROM complaints c " +
                "LEFT JOIN users u ON c.assigned_to = u.user_id " +
                "LEFT JOIN resolution_records r ON c.complaint_id = r.complaint_id " +
                "LEFT JOIN sla_deadlines d ON c.complaint_id = d.complaint_id " +
                "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        if (filter.category != null) {
            sb.append("AND c.category = ? ");
            params.add(filter.category);
        }
        if (filter.priority != null) {
            sb.append("AND c.priority = ? ");
            params.add(filter.priority);
        }
        if (filter.status != null) {
            sb.append("AND c.status = ? ");
            params.add(filter.status);
        }
        if (filter.startDate != null) {
            sb.append("AND c.created_at >= ? ");
            params.add(Timestamp.from(filter.startDate));
        }
        if (filter.endDate != null) {
            sb.append("AND c.created_at <= ? ");
            params.add(Timestamp.from(filter.endDate));
        }

        int total = 0;
        int resolved = 0;
        int slaCompliant = 0;
        int slaBreached = 0;

        Map<String, Integer> statusDist = new LinkedHashMap<>();
        Map<String, Integer> priorityDist = new LinkedHashMap<>();
        Map<String, Integer> categoryDist = new LinkedHashMap<>();
        Map<String, Integer> caseload = new LinkedHashMap<>();

        List<Double> allResolutionDurations = new ArrayList<>();
        Map<String, List<Double>> durationsByCategory = new HashMap<>();
        Map<String, List<Double>> durationsByPriority = new HashMap<>();

        Instant now = Instant.now();

        try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    total++;
                    String status = rs.getString("status");
                    String priority = rs.getString("priority");
                    String category = rs.getString("category");
                    String assignee = rs.getString("assignee_name");
                    Timestamp createdTs = rs.getTimestamp("created_at");
                    Timestamp resolvedTs = rs.getTimestamp("resolved_at");
                    Timestamp deadlineTs = rs.getTimestamp("resolution_deadline");
                    boolean isEscalated = rs.getBoolean("is_escalated");

                    statusDist.put(status, statusDist.getOrDefault(status, 0) + 1);
                    priorityDist.put(priority, priorityDist.getOrDefault(priority, 0) + 1);
                    categoryDist.put(category, categoryDist.getOrDefault(category, 0) + 1);

                    boolean isResolved = "RESOLVED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status);
                    if (isResolved) {
                        resolved++;
                        if (createdTs != null && resolvedTs != null) {
                            double hours = (double) Duration.between(createdTs.toInstant(), resolvedTs.toInstant()).toMillis() / 3600000.0;
                            hours = Math.max(0.1, hours);
                            allResolutionDurations.add(hours);

                            durationsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(hours);
                            durationsByPriority.computeIfAbsent(priority, k -> new ArrayList<>()).add(hours);
                        }

                        // SLA compliance check for resolved item
                        if (deadlineTs != null && resolvedTs != null) {
                            if (resolvedTs.before(deadlineTs) || resolvedTs.equals(deadlineTs)) {
                                slaCompliant++;
                            } else {
                                slaBreached++;
                            }
                        } else if (!isEscalated) {
                            slaCompliant++;
                        } else {
                            slaBreached++;
                        }
                    } else {
                        // Active complaint: track caseload
                        if (assignee != null) {
                            caseload.put(assignee, caseload.getOrDefault(assignee, 0) + 1);
                        } else {
                            caseload.put("Unassigned", caseload.getOrDefault("Unassigned", 0) + 1);
                        }

                        // SLA compliance check for active item
                        if (deadlineTs != null) {
                            if (now.isAfter(deadlineTs.toInstant()) || isEscalated) {
                                slaBreached++;
                            } else {
                                slaCompliant++;
                            }
                        }
                    }
                }
            }
        }

        // 2. Fetch appeals count
        int appealCount = 0;
        String appealSql = "SELECT COUNT(DISTINCT complaint_id) FROM complaint_appeals";
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(appealSql)) {
            if (rs.next()) appealCount = rs.getInt(1);
        }

        // 3. Fetch override count
        int overrideCount = 0;
        String overrideSql = "SELECT COUNT(*) FROM priority_overrides";
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(overrideSql)) {
            if (rs.next()) overrideCount = rs.getInt(1);
        }

        // 4. Safe percentage calculations (Division by zero protected)
        int unresolved = total - resolved;
        double resolutionRate = total > 0 ? ((double) resolved / total) * 100.0 : 0.0;
        int totalSlaEvaluated = slaCompliant + slaBreached;
        double trueSlaPercent = totalSlaEvaluated > 0 ? ((double) slaCompliant / totalSlaEvaluated) * 100.0 : 100.0;
        double appealRate = resolved > 0 ? ((double) appealCount / resolved) * 100.0 : 0.0;
        double overrideRate = total > 0 ? ((double) overrideCount / total) * 100.0 : 0.0;

        // 5. Genuine Median Calculations
        double medianOverall = computeMedian(allResolutionDurations);
        Map<String, Double> mediansByCategory = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> e : durationsByCategory.entrySet()) {
            mediansByCategory.put(e.getKey(), computeMedian(e.getValue()));
        }

        Map<String, Double> mediansByPriority = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> e : durationsByPriority.entrySet()) {
            mediansByPriority.put(e.getKey(), computeMedian(e.getValue()));
        }

        return new OperationalMetrics(
                total, resolved, unresolved, Math.round(resolutionRate * 10.0) / 10.0,
                slaCompliant, slaBreached, Math.round(trueSlaPercent * 10.0) / 10.0,
                appealCount, Math.round(appealRate * 10.0) / 10.0,
                overrideCount, Math.round(overrideRate * 10.0) / 10.0,
                Math.round(medianOverall * 10.0) / 10.0,
                mediansByCategory, mediansByPriority,
                statusDist, priorityDist, categoryDist, caseload
        );
    }

    /**
     * Calculates genuine statistical median of a list of values.
     */
    public static double computeMedian(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        } else {
            return (sorted.get((size / 2) - 1) + sorted.get(size / 2)) / 2.0;
        }
    }
}
