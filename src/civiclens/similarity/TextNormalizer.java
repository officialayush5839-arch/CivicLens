package civiclens.similarity;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text normalization, stemming, and synonym mapping for deterministic duplicate detection.
 */
public class TextNormalizer {

    public static final Set<String> STOPWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
            "aren't", "as", "at", "be", "because", "been", "before", "being", "below", "between", "both",
            "but", "by", "can", "cannot", "could", "did", "didn't", "do", "does", "doesn't", "doing",
            "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has",
            "hasn't", "have", "haven't", "having", "he", "her", "here", "hers", "herself", "him", "himself",
            "his", "how", "i", "if", "in", "into", "is", "isn't", "it", "its", "itself", "me", "more",
            "most", "mustn't", "my", "myself", "no", "nor", "not", "of", "off", "on", "once", "only",
            "or", "other", "ought", "our", "ours", "ourselves", "out", "over", "own", "same", "shan't",
            "she", "should", "shouldn't", "so", "some", "such", "than", "that", "the", "their", "theirs",
            "them", "themselves", "then", "there", "these", "they", "this", "those", "through", "to", "too",
            "under", "until", "up", "very", "was", "wasn't", "we", "were", "weren't", "what", "when",
            "where", "which", "while", "who", "whom", "why", "with", "won't", "would", "wouldn't", "you",
            "your", "yours", "yourself", "yourselves", "please", "hello", "hi", "sir", "madam", "complaint",
            "due", "along", "across", "near", "front", "back", "everywhere", "keeps", "occur", "occurs"
    )));

    // Common civic synonym standardizations
    private static final Map<String, String> SYNONYMS = new HashMap<>();
    static {
        SYNONYMS.put("blackout", "outage");
        SYNONYMS.put("blast", "explos");
        SYNONYMS.put("ruptur", "burst");
        SYNONYMS.put("odor", "smell");
        SYNONYMS.put("ave", "avenu");
        SYNONYMS.put("st", "street");
        SYNONYMS.put("junction", "intersect");
        SYNONYMS.put("intersection", "intersect");
        SYNONYMS.put("electricity", "power");
        SYNONYMS.put("trash", "garbag");
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9]{2,}");

    /**
     * Extracts normalized, stemmed, non-stopword tokens.
     */
    public static List<String> extractSalientTokens(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN_PATTERN.matcher(text.toLowerCase());
        while (m.find()) {
            String token = m.group();
            if (STOPWORDS.contains(token)) {
                continue;
            }
            String stem = stemToken(token);
            if (SYNONYMS.containsKey(stem)) {
                stem = SYNONYMS.get(stem);
            }
            if (!STOPWORDS.contains(stem) && stem.length() >= 2) {
                tokens.add(stem);
            }
        }
        return tokens;
    }

    /**
     * Deterministic lightweight suffix stemmer.
     */
    public static String stemToken(String s) {
        if (s.length() <= 3) return s;

        if (s.endsWith("sses")) return s.substring(0, s.length() - 2);
        if (s.endsWith("ies")) return s.substring(0, s.length() - 3) + "y";
        if (s.endsWith("ss")) return s;
        if (s.endsWith("s")) s = s.substring(0, s.length() - 1);

        if (s.length() <= 3) return s;

        if (s.endsWith("eed")) return s.substring(0, s.length() - 1);
        if (s.endsWith("ed") && s.length() > 4) s = s.substring(0, s.length() - 2);
        else if (s.endsWith("ing") && s.length() > 5) s = s.substring(0, s.length() - 3);

        if (s.endsWith("tion") || s.endsWith("sion")) return s.substring(0, s.length() - 3);
        if (s.endsWith("ment") && s.length() > 6) return s.substring(0, s.length() - 4);
        if (s.endsWith("ness") && s.length() > 6) return s.substring(0, s.length() - 4);
        if (s.endsWith("able") && s.length() > 6) return s.substring(0, s.length() - 4);
        if (s.endsWith("ible") && s.length() > 6) return s.substring(0, s.length() - 4);

        return s;
    }

    /**
     * Computes Jaccard token overlap similarity between two collections of tokens.
     */
    public static double computeJaccard(Collection<String> tokens1, Collection<String> tokens2) {
        if (tokens1 == null || tokens2 == null || tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }
        Set<String> set1 = new HashSet<>(tokens1);
        Set<String> set2 = new HashSet<>(tokens2);

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }
}
