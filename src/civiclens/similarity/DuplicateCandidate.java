package civiclens.similarity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Represents an explainable candidate complaint for duplicate/related incident detection.
 */
public class DuplicateCandidate implements Serializable, Comparable<DuplicateCandidate> {

    private static final long serialVersionUID = 1L;

    private final int complaintId;
    private final String title;
    private final String category;
    private final String status;
    private final Instant createdAt;
    private final double similarityScore; // 0.0 to 1.0
    private final String explanation;
    private final List<String> sharedSalientTerms;

    public DuplicateCandidate(
            int complaintId,
            String title,
            String category,
            String status,
            Instant createdAt,
            double similarityScore,
            String explanation,
            List<String> sharedSalientTerms) {
        this.complaintId = complaintId;
        this.title = title != null ? title : "";
        this.category = category != null ? category : "";
        this.status = status != null ? status : "";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.similarityScore = similarityScore;
        this.explanation = explanation != null ? explanation : "";
        this.sharedSalientTerms = sharedSalientTerms != null ?
                Collections.unmodifiableList(sharedSalientTerms) : Collections.emptyList();
    }

    public int getComplaintId() { return complaintId; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public double getSimilarityScore() { return similarityScore; }
    public String getExplanation() { return explanation; }
    public List<String> getSharedSalientTerms() { return sharedSalientTerms; }

    @Override
    public int compareTo(DuplicateCandidate o) {
        // Sort descending by similarity score
        return Double.compare(o.similarityScore, this.similarityScore);
    }

    @Override
    public String toString() {
        return String.format("Candidate #%d (Score: %.2f) [%s] %s - %s",
                complaintId, similarityScore, category, title, explanation);
    }
}
