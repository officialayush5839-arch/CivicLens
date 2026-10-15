package civiclens.similarity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * Offline, deterministic duplicate complaint and incident candidate detection engine.
 * Computes hybrid similarity combining Jaccard token overlap, TF-IDF Cosine vector distance,
 * and asymmetric containment overlap, with category contextualization and human-interpretable explanations.
 */
public class DuplicateDetectionEngine {

    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.35;

    public static class ComplaintDocument {
        public final int complaintId;
        public final String title;
        public final String description;
        public final String category;
        public final String status;
        public final Instant createdAt;
        public final List<String> tokens;
        public final Map<String, Integer> termFreqs;

        public ComplaintDocument(int complaintId, String title, String description, String category, String status, Instant createdAt) {
            this.complaintId = complaintId;
            this.title = title != null ? title : "";
            this.description = description != null ? description : "";
            this.category = category != null ? category : "";
            this.status = status != null ? status : "";
            this.createdAt = createdAt != null ? createdAt : Instant.now();

            // Extract tokens from combined text (title tokens weighted 2x)
            List<String> tTokens = TextNormalizer.extractSalientTokens(this.title);
            List<String> dTokens = TextNormalizer.extractSalientTokens(this.description);
            this.tokens = new ArrayList<>(tTokens);
            this.tokens.addAll(tTokens);
            this.tokens.addAll(dTokens);

            this.termFreqs = new HashMap<>();
            for (String tok : this.tokens) {
                this.termFreqs.put(tok, this.termFreqs.getOrDefault(tok, 0) + 1);
            }
        }
    }

    /**
     * Finds potential duplicate complaints in database for a new or existing complaint.
     */
    public static List<DuplicateCandidate> findDuplicatesInDb(
            Connection conn,
            int targetComplaintId,
            String title,
            String description,
            String category,
            double threshold) throws SQLException {

        List<ComplaintDocument> corpus = new ArrayList<>();
        String sql = "SELECT complaint_id, title, description, category, status, created_at " +
                     "FROM complaints " +
                     "WHERE complaint_id != ? AND status != 'REJECTED' " +
                     "ORDER BY created_at DESC LIMIT 200";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, targetComplaintId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    corpus.add(new ComplaintDocument(
                            rs.getInt("complaint_id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getString("category"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
                    ));
                }
            }
        }

        ComplaintDocument queryDoc = new ComplaintDocument(targetComplaintId, title, description, category, "SUBMITTED", Instant.now());
        return evaluateCandidates(queryDoc, corpus, threshold);
    }

    /**
     * Evaluates a query document against a corpus of candidate documents.
     */
    public static List<DuplicateCandidate> evaluateCandidates(
            ComplaintDocument query,
            List<ComplaintDocument> corpus,
            double threshold) {

        List<DuplicateCandidate> results = new ArrayList<>();
        if (corpus.isEmpty() || query.tokens.isEmpty()) {
            return results;
        }

        // 1. Calculate Document Frequencies (DF) across (corpus + query)
        Map<String, Integer> docFreq = new HashMap<>();
        int N = corpus.size() + 1;

        for (String term : query.termFreqs.keySet()) {
            docFreq.put(term, 1);
        }
        for (ComplaintDocument doc : corpus) {
            for (String term : doc.termFreqs.keySet()) {
                docFreq.put(term, docFreq.getOrDefault(term, 0) + 1);
            }
        }

        // 2. Build TF-IDF vector for query
        Map<String, Double> queryVector = computeTfIdf(query, docFreq, N);
        double queryNorm = computeVectorNorm(queryVector);

        // 3. Score each corpus document
        for (ComplaintDocument doc : corpus) {
            if (doc.tokens.isEmpty()) continue;

            // Compute Jaccard token overlap
            double jaccard = TextNormalizer.computeJaccard(query.tokens, doc.tokens);

            // Compute Cosine vector distance
            Map<String, Double> docVector = computeTfIdf(doc, docFreq, N);
            double docNorm = computeVectorNorm(docVector);
            double cosine = 0.0;
            if (queryNorm > 0.0 && docNorm > 0.0) {
                double dot = 0.0;
                for (Map.Entry<String, Double> entry : queryVector.entrySet()) {
                    String term = entry.getKey();
                    if (docVector.containsKey(term)) {
                        dot += entry.getValue() * docVector.get(term);
                    }
                }
                cosine = dot / (queryNorm * docNorm);
            }

            // Shared salient terms
            Set<String> shared = new TreeSet<>(query.termFreqs.keySet());
            shared.retainAll(doc.termFreqs.keySet());

            // Containment overlap ratio (shared / min unique tokens)
            double overlap = (double) shared.size() / Math.max(1, Math.min(query.termFreqs.size(), doc.termFreqs.size()));

            // Category matching factor
            boolean categoryMatch = query.category.equalsIgnoreCase(doc.category);
            double categoryBonus = 0.0;
            if (categoryMatch) {
                categoryBonus = 0.08;
            } else {
                categoryBonus = -0.15;
            }

            // If there are zero shared terms, score is strictly zero (prevent category-only false positives)
            double score;
            if (shared.isEmpty()) {
                score = 0.0;
            } else {
                score = (0.25 * jaccard) + (0.35 * cosine) + (0.32 * overlap) + categoryBonus;
                score = Math.max(0.0, Math.min(1.0, score));
            }

            if (score >= threshold) {
                String explanation = buildExplanation(score, shared, categoryMatch, query.category, doc.category);
                results.add(new DuplicateCandidate(
                        doc.complaintId,
                        doc.title,
                        doc.category,
                        doc.status,
                        doc.createdAt,
                        Math.round(score * 1000.0) / 1000.0,
                        explanation,
                        new ArrayList<>(shared)
                ));
            }
        }

        Collections.sort(results);
        return results;
    }

    private static Map<String, Double> computeTfIdf(ComplaintDocument doc, Map<String, Integer> docFreq, int N) {
        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : doc.termFreqs.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();
            int df = docFreq.getOrDefault(term, 1);
            double idf = 1.0 + Math.log(1.0 + ((double) N / df));
            vector.put(term, (double) tf * idf);
        }
        return vector;
    }

    private static double computeVectorNorm(Map<String, Double> vector) {
        double sumSq = 0.0;
        for (double val : vector.values()) {
            sumSq += val * val;
        }
        return Math.sqrt(sumSq);
    }

    private static String buildExplanation(
            double score,
            Set<String> sharedTerms,
            boolean categoryMatch,
            String qCat,
            String docCat) {
        StringBuilder sb = new StringBuilder();
        if (score >= 0.65) {
            sb.append("High probability duplicate: ");
        } else if (score >= 0.45) {
            sb.append("Related incident candidate: ");
        } else {
            sb.append("Potential thematic overlap: ");
        }

        if (!sharedTerms.isEmpty()) {
            sb.append("Shared ").append(sharedTerms.size()).append(" salient terms [");
            int count = 0;
            for (String t : sharedTerms) {
                if (count++ > 0) sb.append(", ");
                sb.append(t);
                if (count >= 5) {
                    if (sharedTerms.size() > 5) sb.append(", ...");
                    break;
                }
            }
            sb.append("]. ");
        }

        if (categoryMatch) {
            sb.append("Exact category match ('").append(qCat).append("').");
        } else {
            sb.append("Different categories ('").append(qCat).append("' vs '").append(docCat).append("').");
        }
        return sb.toString();
    }
}
