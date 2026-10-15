package civiclens.sla;

import java.io.Serializable;

/**
 * Configurable Service Level Agreement (SLA) policy rule.
 */
public class SlaPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int policyId;
    private final String priority;
    private final String category;
    private final int responseTimeHours;
    private final int resolutionTimeHours;
    private final double warningThresholdPercent;
    private final boolean isActive;

    public SlaPolicy(
            int policyId,
            String priority,
            String category,
            int responseTimeHours,
            int resolutionTimeHours,
            double warningThresholdPercent,
            boolean isActive) {
        this.policyId = policyId;
        this.priority = priority != null ? priority.trim().toUpperCase() : "MEDIUM";
        this.category = category != null ? category.trim() : "*";
        this.responseTimeHours = Math.max(1, responseTimeHours);
        this.resolutionTimeHours = Math.max(1, resolutionTimeHours);
        this.warningThresholdPercent = warningThresholdPercent > 0.0 ? warningThresholdPercent : 75.0;
        this.isActive = isActive;
    }

    public int getPolicyId() { return policyId; }
    public String getPriority() { return priority; }
    public String getCategory() { return category; }
    public int getResponseTimeHours() { return responseTimeHours; }
    public int getResolutionTimeHours() { return resolutionTimeHours; }
    public double getWarningThresholdPercent() { return warningThresholdPercent; }
    public boolean isActive() { return isActive; }
}
