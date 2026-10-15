package civiclens.sla;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Detailed real-time SLA status, deadlines, and remaining time calculation.
 */
public class SlaDeadlineInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int complaintId;
    private final String priority;
    private final String category;
    private final Instant createdAt;
    private final Instant responseDeadline;
    private final Instant resolutionDeadline;
    private final SlaStatus slaState;
    private final boolean isEscalated;
    private final Instant escalatedAt;
    private final Duration timeRemaining;
    private final double percentElapsed;

    public SlaDeadlineInfo(
            int complaintId,
            String priority,
            String category,
            Instant createdAt,
            Instant responseDeadline,
            Instant resolutionDeadline,
            SlaStatus slaState,
            boolean isEscalated,
            Instant escalatedAt,
            Duration timeRemaining,
            double percentElapsed) {
        this.complaintId = complaintId;
        this.priority = priority;
        this.category = category;
        this.createdAt = createdAt;
        this.responseDeadline = responseDeadline;
        this.resolutionDeadline = resolutionDeadline;
        this.slaState = slaState;
        this.isEscalated = isEscalated;
        this.escalatedAt = escalatedAt;
        this.timeRemaining = timeRemaining;
        this.percentElapsed = percentElapsed;
    }

    public int getComplaintId() { return complaintId; }
    public String getPriority() { return priority; }
    public String getCategory() { return category; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResponseDeadline() { return responseDeadline; }
    public Instant getResolutionDeadline() { return resolutionDeadline; }
    public SlaStatus getSlaState() { return slaState; }
    public boolean isEscalated() { return isEscalated; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public Duration getTimeRemaining() { return timeRemaining; }
    public double getPercentElapsed() { return percentElapsed; }

    public String getFormattedRemainingTime() {
        if (slaState == SlaStatus.COMPLETED) {
            return "Resolved";
        }
        if (timeRemaining.isNegative()) {
            Duration overdue = timeRemaining.abs();
            long hours = overdue.toHours();
            long minutes = overdue.toMinutesPart();
            return String.format("Overdue by %dh %dm", hours, minutes);
        } else {
            long hours = timeRemaining.toHours();
            long minutes = timeRemaining.toMinutesPart();
            return String.format("%dh %dm remaining", hours, minutes);
        }
    }
}
