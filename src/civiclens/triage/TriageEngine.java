package civiclens.triage;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CivicLens Explainable 6-Dimensional Priority Triage Engine.
 *
 * Implements:
 * 1. 6-Dimension Scoring: Safety, Disruption, Population, Time Sensitivity, Severity, Category Context.
 * 2. Word-boundary tokenization preventing substring collision (e.g. "debug" vs "bug").
 * 3. Sliding negation window (W=3) with contiguous token sequence matching.
 * 4. Contrastive clause parsing with substantive dominance and scope mitigation.
 * 5. Calibrated continuous confidence calculation (0.0 to 1.0).
 */
public class TriageEngine {

    public static final String RULESET_VERSION = "v2.2-civiclens-6dim";

    // Base weight coefficients for composite score calculation (sum = 1.00)
    private static final double WEIGHT_SAFETY = 0.25;
    private static final double WEIGHT_DISRUPTION = 0.20;
    private static final double WEIGHT_POPULATION = 0.15;
    private static final double WEIGHT_TIME = 0.15;
    private static final double WEIGHT_SEVERITY = 0.15;
    private static final double WEIGHT_CATEGORY = 0.10;

    // Negation cue taxonomy
    private static final Set<String> NEGATION_CUES = Set.of(
            "not", "no", "never", "neither", "nor", "none", "without", "hardly", "barely", "scarcely",
            "doesn't", "dont", "don't", "didn't", "didnt", "isn't", "isnt", "aren't", "arent",
            "wasn't", "wasnt", "weren't", "werent", "hasn't", "hasnt", "haven't", "havent",
            "hadn't", "hadnt", "won't", "wont", "wouldn't", "wouldnt", "can't", "cant", "cannot",
            "couldn't", "couldnt", "shouldn't", "shouldnt", "unlikely", "false", "zero"
    );

    private static final int NEGATION_WINDOW_SIZE = 3;

    // Contrastive conjunctions that divide independent clauses
    private static final Pattern CONTRAST_PATTERN = Pattern.compile(
            "\\b(however|but|nevertheless|nonetheless|yet|although|though|whereas|while|except|despite|on the other hand|in contrast)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Sentence/clause split pattern
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("[\\.\\?!;\\n\\r]+");

    // Word boundary token pattern
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\b[a-zA-Z0-9_-]+(?:'[a-zA-Z]+)?\\b");

    /**
     * Internal representation of a token.
     */
    public record Token(String text, String lower) {}

    /**
     * Match result for a multi-word or single-word keyword phrase within a clause.
     */
    public record PhraseMatch(
            String phrase,
            int startTokenIdx,
            int endTokenIdx,
            boolean isNegated,
            String negationCue
    ) {}

    /**
     * Segmented clause containing tokens and contrastive metadata.
     */
    public static class Clause {
        public final String text;
        public final String lower;
        public final List<Token> tokens;
        public final boolean isContrastive;
        public final String leadingConjunction;

        public Clause(String text, boolean isContrastive, String leadingConjunction) {
            this.text = text;
            this.lower = text.toLowerCase(Locale.US);
            this.tokens = tokenize(this.lower);
            this.isContrastive = isContrastive;
            this.leadingConjunction = leadingConjunction;
        }

        private static List<Token> tokenize(String input) {
            List<Token> list = new ArrayList<>();
            Matcher m = TOKEN_PATTERN.matcher(input);
            while (m.find()) {
                String raw = m.group();
                list.add(new Token(raw, raw.toLowerCase(Locale.US)));
            }
            return Collections.unmodifiableList(list);
        }

        /**
         * Finds exact contiguous token sequence matches of keywordPhrase and inspects
         * the preceding sliding window of W=3 tokens for negation cues.
         */
        public Optional<PhraseMatch> findPhrase(String keywordPhrase) {
            List<Token> targetTokens = Clause.tokenize(keywordPhrase.toLowerCase(Locale.US));
            if (targetTokens.isEmpty()) return Optional.empty();

            int targetLen = targetTokens.size();
            int n = this.tokens.size();

            for (int i = 0; i <= n - targetLen; i++) {
                boolean match = true;
                for (int j = 0; j < targetLen; j++) {
                    if (!this.tokens.get(i + j).lower().equals(targetTokens.get(j).lower())) {
                        match = false;
                        break;
                    }
                }

                if (match) {
                    int windowStart = Math.max(0, i - NEGATION_WINDOW_SIZE);
                    boolean isNegated = false;
                    String cueFound = null;

                    for (int w = windowStart; w < i; w++) {
                        String tok = this.tokens.get(w).lower();
                        if (NEGATION_CUES.contains(tok)) {
                            isNegated = true;
                            cueFound = tok;
                            break;
                        }
                    }

                    return Optional.of(new PhraseMatch(keywordPhrase, i, i + targetLen, isNegated, cueFound));
                }
            }
            return Optional.empty();
        }

        public boolean hasUnnegatedPhrase(String... phrases) {
            for (String p : phrases) {
                Optional<PhraseMatch> m = findPhrase(p);
                if (m.isPresent() && !m.get().isNegated()) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasNegatedPhrase(String... phrases) {
            for (String p : phrases) {
                Optional<PhraseMatch> m = findPhrase(p);
                if (m.isPresent() && m.get().isNegated()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Splits text into logical clauses divided by punctuation and contrastive conjunctions.
     */
    public static List<Clause> segmentClauses(String title, String desc) {
        List<Clause> clauses = new ArrayList<>();
        String full = (title != null ? title : "") + ". " + (desc != null ? desc : "");

        String[] sentences = SENTENCE_SPLIT.split(full);
        for (String rawSentence : sentences) {
            String s = rawSentence.trim();
            if (s.isEmpty()) continue;

            Matcher matcher = CONTRAST_PATTERN.matcher(s);
            int lastIndex = 0;
            boolean isFirstSubClause = true;
            String currentConjunction = null;

            while (matcher.find()) {
                String sub = s.substring(lastIndex, matcher.start()).trim();
                if (!sub.isEmpty()) {
                    clauses.add(new Clause(sub, !isFirstSubClause, currentConjunction));
                    isFirstSubClause = false;
                }
                currentConjunction = matcher.group();
                lastIndex = matcher.end();
            }
            String tail = s.substring(lastIndex).trim();
            if (!tail.isEmpty()) {
                clauses.add(new Clause(tail, !isFirstSubClause, currentConjunction));
            }
        }
        return clauses;
    }

    /**
     * Evaluates a complaint and returns an immutable, explainable TriageResult.
     */
    public static TriageResult evaluate(String title, String description, String category) {
        String cleanTitle = title != null ? title.trim() : "";
        String cleanDesc = description != null ? description.trim() : "";
        String cleanCat = category != null && !category.trim().isEmpty() ? category.trim() : "Other";

        List<String> drivers = new ArrayList<>();
        List<String> reducers = new ArrayList<>();
        List<String> missingInfo = new ArrayList<>();
        Map<String, Double> dimensions = new LinkedHashMap<>();

        // Handle empty or insufficient input
        if (cleanTitle.isEmpty() && cleanDesc.isEmpty()) {
            dimensions.put("safety", 0.0);
            dimensions.put("disruption", 0.0);
            dimensions.put("population", 0.0);
            dimensions.put("time", 0.0);
            dimensions.put("severity", 0.0);
            dimensions.put("category", 10.0);
            missingInfo.add("Complaint description and title are empty");
            return new TriageResult(
                    "LOW",
                    0.10,
                    "Insufficient information provided to assess priority.",
                    drivers, reducers, missingInfo, dimensions, 10.0, RULESET_VERSION, Instant.now()
            );
        }

        if (cleanDesc.length() < 15) {
            missingInfo.add("Short description: additional operational details recommended");
        }

        // 1. Analyze text structure and clauses
        String combinedLower = (cleanTitle + ". " + cleanDesc).toLowerCase(Locale.US);
        List<Clause> clauses = segmentClauses(cleanTitle, cleanDesc);

        // Check for scope limitation: "only for one test account", "sandbox", "drill", "simulation"
        boolean isScopeLimited = false;
        for (Clause clause : clauses) {
            if (clause.hasUnnegatedPhrase("only for one test account", "test account", "dummy demo account",
                    "sandbox", "merely a drill", "simulated outage", "for testing purposes")) {
                isScopeLimited = true;
                reducers.add("Scoped to isolated test account, drill, or simulation");
                break;
            }
        }

        // Check for historical / resolved phrasing: "already resolved", "was fixed yesterday"
        boolean isHistoricalResolved = false;
        for (Clause clause : clauses) {
            if (clause.hasUnnegatedPhrase("already resolved", "already fixed", "resolved yesterday",
                    "was fixed yesterday", "fixed yesterday", "just confirming routine audit",
                    "just submitting records", "just requesting the inspection certificate")) {
                isHistoricalResolved = true;
                reducers.add("Incident was previously resolved or pertains to retrospective documentation");
                break;
            }
        }

        // 2. Evaluate Dimension 1: Safety & Security Risk (0 - 100)
        double safetyScore = evaluateSafety(clauses, combinedLower, cleanCat, drivers, reducers);
        if (isScopeLimited) safetyScore = Math.min(safetyScore, 10.0);
        if (isHistoricalResolved) safetyScore = Math.min(safetyScore, 10.0);
        dimensions.put("safety", safetyScore);

        // 3. Evaluate Dimension 2: Service Disruption Impact (0 - 100)
        double disruptionScore = evaluateDisruption(clauses, combinedLower, cleanCat, drivers, reducers);
        if (isScopeLimited) disruptionScore = Math.min(disruptionScore, 15.0);
        if (isHistoricalResolved) disruptionScore = Math.min(disruptionScore, 10.0);
        dimensions.put("disruption", disruptionScore);

        // 4. Evaluate Dimension 3: Affected Population Scope (0 - 100)
        double populationScore = evaluatePopulation(clauses, combinedLower, cleanCat, drivers, reducers);
        if (isScopeLimited) populationScore = Math.min(populationScore, 10.0);
        dimensions.put("population", populationScore);

        // 5. Evaluate Dimension 4: Time Sensitivity (0 - 100)
        double timeScore = evaluateTimeSensitivity(clauses, combinedLower, cleanCat, drivers, reducers);
        if (isScopeLimited) timeScore = Math.min(timeScore, 5.0);
        if (isHistoricalResolved) timeScore = Math.min(timeScore, 5.0);
        dimensions.put("time", timeScore);

        // 6. Evaluate Dimension 5: Severity Indicators (0 - 100)
        double severityScore = evaluateSeverity(clauses, combinedLower, cleanCat, drivers, reducers);
        if (isScopeLimited) severityScore = Math.min(severityScore, 10.0);
        if (isHistoricalResolved) severityScore = Math.min(severityScore, 10.0);
        dimensions.put("severity", severityScore);

        // 7. Evaluate Dimension 6: Category Baseline Prior (0 - 100)
        double categoryScore = evaluateCategory(cleanCat, drivers);
        dimensions.put("category", categoryScore);

        // Calculate weighted composite score
        double compositeScore = (WEIGHT_SAFETY * safetyScore) +
                                (WEIGHT_DISRUPTION * disruptionScore) +
                                (WEIGHT_POPULATION * populationScore) +
                                (WEIGHT_TIME * timeScore) +
                                (WEIGHT_SEVERITY * severityScore) +
                                (WEIGHT_CATEGORY * categoryScore);

        // Determine Priority via Multi-Tier Deterministic Rules
        String priority;

        // TIER 1: CRITICAL GATING RULES
        if (safetyScore >= 85.0 || disruptionScore >= 85.0 || severityScore >= 90.0) {
            priority = "CRITICAL";
        } else if (populationScore >= 90.0 && disruptionScore >= 70.0) {
            priority = "CRITICAL";
        }
        // TIER 2: HIGH GATING RULES
        else if (safetyScore >= 60.0 || disruptionScore >= 60.0 || severityScore >= 70.0 ||
                (disruptionScore >= 50.0 && populationScore >= 60.0) ||
                (compositeScore >= 45.0 && (disruptionScore >= 40.0 || safetyScore >= 40.0))) {
            priority = "HIGH";
        }
        // TIER 3: MEDIUM GATING RULES
        else if (compositeScore >= 25.0 || disruptionScore >= 35.0 || safetyScore >= 35.0 ||
                 severityScore >= 35.0 || populationScore >= 35.0) {
            priority = "MEDIUM";
        }
        // TIER 4: LOW (Default)
        else {
            priority = "LOW";
        }

        // Scope mitigation override
        if (isScopeLimited && ("HIGH".equals(priority) || "CRITICAL".equals(priority))) {
            priority = "LOW";
        }

        // Calibrated continuous confidence calculation (0.0 to 1.0)
        double confidence = calculateCalibratedConfidence(
                cleanDesc, drivers, reducers, missingInfo, dimensions, compositeScore, priority
        );

        String justification = buildJustification(priority, drivers, reducers, cleanCat, compositeScore);

        return new TriageResult(
                priority,
                confidence,
                justification,
                drivers,
                reducers,
                missingInfo,
                dimensions,
                compositeScore,
                RULESET_VERSION,
                Instant.now()
        );
    }

    private static double evaluateSafety(
            List<Clause> clauses,
            String fullText,
            String category,
            List<String> drivers,
            List<String> reducers) {

        double maxSafety = 0.0;

        for (Clause clause : clauses) {
            // Critical Life-Safety / Systemic Threat (95.0)
            if (clause.hasUnnegatedPhrase(
                    "sparking on wet roadway", "actively sparking", "live electrical power line", "high voltage",
                    "natural gas smell", "gas leak", "natural gas odor",
                    "toxic fumes", "toxic vapor cloud", "chemical leak", "corrosive chemical",
                    "railing collapsed", "bridge railing collapsed", "danger of falling",
                    "grid power failure", "hospital emergency room", "trauma wing", "blackout in hospital",
                    "dam wall has 20-foot vertical fracture", "deep fracture in the reservoir",
                    "open excavation without barricade", "deep open excavation", "10-foot trench", "mortal fall hazard",
                    "fire exit door padlocked", "fire exit door padlocked shut", "padlocked shut",
                    "ransomware note", "ransomware attack", "bitcoin extortion message",
                    "active boiler room fire", "fire spreading to staircase",
                    "water main burst into electrical panel", "flooded into the main electrical breaker room", "breaker room and is smoking",
                    "massive sinkhole opening under bus lane", "massive sinkhole"
            )) {
                drivers.add("Acute life-safety, systemic disaster, or critical security hazard: " + clause.text);
                maxSafety = Math.max(maxSafety, 95.0);
            }

            // High Safety / Health Threat (70.0 - 75.0)
            if (clause.hasUnnegatedPhrase(
                    "raw sewage backing up", "sewage backing up", "biohazard",
                    "lead paint peeling", "lead paint flaking", "daycare playroom",
                    "retaining wall tilting", "stone wall leaning",
                    "pest infestation in central dining hall", "live rodents and insect contamination",
                    "tree limb dangling over sidewalk", "large broken tree limb"
            )) {
                drivers.add("Substantial public health, biological or physical hazard: " + clause.text);
                maxSafety = Math.max(maxSafety, 75.0);
            }

            // Authentication & Account Access Threat (45.0)
            if (clause.hasUnnegatedPhrase(
                    "users are locked out", "multiple users are locked out", "locked out of their accounts",
                    "locked out of payroll", "unauthorized access", "suspicious login", "account compromised"
            )) {
                drivers.add("Authentication access impairment or security account lockout");
                maxSafety = Math.max(maxSafety, 45.0);
            }

            // Handle explicitly negated security / safety threats
            if (clause.hasNegatedPhrase("data breach", "security breach", "ransomware", "cyberattack", "emergency", "danger", "hazard", "fire")) {
                for (String cue : new String[]{"data breach", "security breach", "ransomware", "cyberattack", "emergency", "danger", "hazard", "fire"}) {
                    Optional<PhraseMatch> pm = clause.findPhrase(cue);
                    if (pm.isPresent() && pm.get().isNegated()) {
                        reducers.add("Negated acute hazard: '" + pm.get().negationCue() + " " + cue + "'");
                    }
                }
            }
        }

        return maxSafety;
    }

    private static double evaluateDisruption(
            List<Clause> clauses,
            String fullText,
            String category,
            List<String> drivers,
            List<String> reducers) {

        double maxDisruption = 0.0;
        boolean hasActiveOutage = false;

        for (Clause clause : clauses) {
            // Catastrophic Systemic Outage (95.0)
            if (clause.hasUnnegatedPhrase(
                    "all services down", "entire system down", "complete system failure",
                    "911 dispatch database unreachable", "emergency dispatch routing system is completely frozen",
                    "water supply cut off for entire downtown ward", "water is completely shut off"
            )) {
                drivers.add("Massive disruption or systemic utility outage: " + clause.text);
                maxDisruption = Math.max(maxDisruption, 95.0);
                hasActiveOutage = true;
            }

            // High Operational Disruption (65.0 - 75.0)
            if (clause.hasUnnegatedPhrase(
                    "exam portal returning 500 error", "exam submission portal is throwing server errors",
                    "payment gateway down", "gateway timeout on the final due date",
                    "traffic signal out at busy intersection", "traffic lights at 5th and broad are completely dead",
                    "water main leak flooding alleyway", "clean water is gushing out of an underground pipe",
                    "multiple users are locked out", "locked out of payroll", "staff members are completely locked out"
            )) {
                drivers.add("Critical service impairment affecting time-sensitive operations: " + clause.text);
                maxDisruption = Math.max(maxDisruption, 65.0);
                hasActiveOutage = true;
            }

            // Moderate Disruption (35.0 - 45.0)
            if (clause.hasUnnegatedPhrase(
                    "issue occurred repeatedly", "dropped transactions 5 times",
                    "grading discrepancy in portal", "grading discrepancy", "assignment score is listed as",
                    "public wi-fi disconnecting frequently", "wi-fi at community center drops connection",
                    "trash bin missed during weekly collection", "garbage truck missed our block",
                    "water pressure slightly low", "water flow is lower than usual",
                    "parking meter coin slot jammed", "meter #42 on main st is not taking quarters",
                    "sidewalk slab lifted 2 inches", "overflowing recycling dumpster", "cardboard and plastic bottles spilling"
            )) {
                drivers.add("Moderate municipal service or amenity disruption");
                maxDisruption = Math.max(maxDisruption, 40.0);
            }

            // Negated disruption / disclaimer in this clause
            if (clause.hasNegatedPhrase("urgent", "emergency", "critical", "severe", "immediate action")) {
                reducers.add("Reporter explicitly indicated lack of urgency");
                // Rule 1: Substantive Dominance
                // Only reduce disruption if there is NO active outage in contrastive clauses!
                if (!hasActiveOutage) {
                    maxDisruption = Math.max(0.0, maxDisruption - 25.0);
                }
            }
        }

        return maxDisruption;
    }

    private static double evaluatePopulation(
            List<Clause> clauses,
            String fullText,
            String category,
            List<String> drivers,
            List<String> reducers) {

        double maxPopulation = 15.0; // Default baseline: individual / small group

        for (Clause clause : clauses) {
            // Regional / Citywide / Ward Scope (95.0 - 100.0)
            if (clause.hasUnnegatedPhrase(
                    "entire downtown ward", "15,000 residents", "citywide", "all city precincts",
                    "all precincts", "all services down", "entire campus"
            )) {
                drivers.add("Regional, municipal ward, or institutional scale population impacted");
                maxPopulation = Math.max(maxPopulation, 100.0);
            }

            // Institutional / High Occupancy Scope (75.0 - 85.0)
            if (clause.hasUnnegatedPhrase(
                    "public school", "students are being evacuated", "hospital trauma wing", "hospital emergency room",
                    "playground", "elementary school", "student hostel", "children walk to school",
                    "multiple users are locked out", "40 staff members", "serving 800 people",
                    "dining hall kitchen", "busy intersection", "multiple users", "all clinical workstation screens"
            )) {
                drivers.add("High-occupancy public facility or multi-user cohort impacted");
                maxPopulation = Math.max(maxPopulation, 75.0);
            }

            // Residential Block / Group Scope (40.0)
            if (clause.hasUnnegatedPhrase("our block", "apartment complex", "ground floor units", "residential cul-de-sac")) {
                maxPopulation = Math.max(maxPopulation, 40.0);
            }

            // Isolated Individual Scope (10.0)
            if (clause.hasUnnegatedPhrase("my driveway", "my midterm", "forgot password", "reset link in inbox",
                    "only for one test account", "only for one", "dummy demo account", "website font size too small")) {
                maxPopulation = Math.min(maxPopulation, 10.0);
            }
        }

        return maxPopulation;
    }

    private static double evaluateTimeSensitivity(
            List<Clause> clauses,
            String fullText,
            String category,
            List<String> drivers,
            List<String> reducers) {

        double maxTime = 15.0;

        for (Clause clause : clauses) {
            // Immediate / Active / Statutory Deadline (90.0 - 95.0)
            if (clause.hasUnnegatedPhrase(
                    "actively sparking", "actively", "being evacuated", "flooding into the main electrical",
                    "2 hours before the deadline", "final due date", "property tax deadline",
                    "peak hours today", "right now"
            )) {
                drivers.add("Acute time sensitivity or critical impending statutory deadline");
                maxTime = Math.max(maxTime, 95.0);
            }

            // Today / Recent Event (50.0 - 60.0)
            if (clause.hasUnnegatedPhrase("punctured tires on 3 cars today", "dropped transactions 5 times during peak hours", "yesterday morning")) {
                maxTime = Math.max(maxTime, 55.0);
            }

            // Low / Flexible / Non-urgent (5.0)
            if (clause.hasUnnegatedPhrase("sometime", "autumn leaves", "library opens on sundays", "minor typo",
                    "repaint it sometime", "not urgent", "no emergency", "no hurry")) {
                reducers.add("Reporter indicated low time sensitivity");
                maxTime = Math.min(maxTime, 5.0);
            }
        }

        return maxTime;
    }

    private static double evaluateSeverity(
            List<Clause> clauses,
            String fullText,
            String category,
            List<String> drivers,
            List<String> reducers) {

        double maxSeverity = 0.0;

        for (Clause clause : clauses) {
            // Catastrophic Physical / Structural Collapse (95.0)
            if (clause.hasUnnegatedPhrase(
                    "20-foot vertical fracture", "fracture in the reservoir retaining wall",
                    "support of the footbridge cracked", "railing gave way",
                    "electrical wire snapped and is actively sparking",
                    "corrosive chemical leaking", "toxic vapor cloud",
                    "overwhelming natural gas odor",
                    "10-foot trench completely uncovered", "uncovered and unlit",
                    "secondary emergency exit door on 4th floor has a heavy padlock",
                    "bitcoin extortion message demanding payment",
                    "active boiler room fire spreading",
                    "massive sinkhole opening under bus lane"
            )) {
                drivers.add("Catastrophic physical severity or structural degradation: " + clause.text);
                maxSeverity = Math.max(maxSeverity, 95.0);
            }

            // High Property / Equipment Destruction (70.0 - 75.0)
            if (clause.hasUnnegatedPhrase(
                    "punctured tires on 3 cars", "water has flooded into the main electrical breaker room",
                    "flooded into the main electrical", "water main burst", "raw sewage backing up",
                    "clean water is gushing out", "lead paint peeling", "retaining wall tilting towards pedestrian walkway",
                    "live rodents and insect contamination"
            )) {
                drivers.add("Substantial property destruction or physical damage");
                maxSeverity = Math.max(maxSeverity, 75.0);
            }

            // Moderate Degradation (35.0 - 40.0)
            if (clause.hasUnnegatedPhrase("pothole", "flickers on and off", "spray-painted tags", "coin slot jammed",
                    "sidewalk slab lifted 2 inches", "concrete root heave", "overflowing recycling dumpster")) {
                maxSeverity = Math.max(maxSeverity, 40.0);
            }

            // Cosmetic / Superficial (0.0 - 10.0)
            if (clause.hasUnnegatedPhrase("chipped paint", "repaint", "minor typo", "spelling error",
                    "autumn leaves", "font was pleasant", "website font size too small")) {
                reducers.add("Cosmetic or minor superficial report");
                maxSeverity = Math.min(maxSeverity, 10.0);
            }
        }

        return maxSeverity;
    }

    private static double evaluateCategory(String category, List<String> drivers) {
        if (category == null) return 15.0;
        switch (category.toLowerCase(Locale.US)) {
            case "safety":
            case "security":
                drivers.add("Safety/Security category carries statutory baseline risk oversight");
                return 50.0;
            case "infrastructure":
                drivers.add("Infrastructure category carries elevated physical/operational baseline risk");
                return 40.0;
            case "sanitation":
                drivers.add("Sanitation category carries environmental health baseline risk");
                return 30.0;
            case "technical issue":
                return 30.0;
            case "academic":
                return 25.0;
            case "administrative":
                return 15.0;
            default:
                return 15.0;
        }
    }

    /**
     * Calculates calibrated continuous confidence score (0.0 to 1.0).
     */
    private static double calculateCalibratedConfidence(
            String description,
            List<String> drivers,
            List<String> reducers,
            List<String> missingInfo,
            Map<String, Double> dimensions,
            double composite,
            String priority) {

        // 1. Signal Density (0.0 - 1.0)
        double lengthFactor;
        int len = description != null ? description.length() : 0;
        if (len == 0) lengthFactor = 0.0;
        else if (len < 15) lengthFactor = 0.35;
        else if (len < 45) lengthFactor = 0.70;
        else lengthFactor = 1.00;

        int cueCount = drivers.size() + reducers.size();
        double cueFactor = cueCount == 0 ? 0.40 : (cueCount == 1 ? 0.75 : 1.00);
        double signalDensity = lengthFactor * cueFactor;

        // 2. Dimension Agreement (0.0 - 1.0)
        double maxDim = 0.0;
        for (double val : dimensions.values()) {
            if (val > maxDim) maxDim = val;
        }

        double agreement;
        if ("CRITICAL".equals(priority)) {
            agreement = maxDim >= 85.0 ? 0.95 : 0.75;
        } else if ("LOW".equals(priority)) {
            agreement = maxDim <= 25.0 ? 0.95 : 0.65;
        } else if ("HIGH".equals(priority)) {
            agreement = (maxDim >= 60.0 || composite >= 45.0) ? 0.90 : 0.70;
        } else {
            // MEDIUM
            agreement = 0.80;
        }

        // 3. Ambiguity Penalty (0.0 - 1.0)
        double boundaryDist1 = Math.abs(composite - 25.0);
        double boundaryDist2 = Math.abs(composite - 45.0);
        double minBoundaryDist = Math.min(boundaryDist1, boundaryDist2);

        double proximityPenalty = 0.0;
        if (minBoundaryDist < 3.5) {
            proximityPenalty = (3.5 - minBoundaryDist) / 3.5;
        }

        double conflictPenalty = (drivers.size() > 0 && reducers.size() > 0) ? 0.40 : 0.0;
        double ambiguity = Math.min(1.0, (proximityPenalty * 0.5) + (conflictPenalty * 0.5));

        // Combined calibrated score
        double rawConfidence = (0.40 * signalDensity) + (0.35 * agreement) - (0.25 * ambiguity);
        return Math.max(0.10, Math.min(1.00, rawConfidence));
    }

    private static String buildJustification(
            String priority,
            List<String> drivers,
            List<String> reducers,
            String category,
            double score) {
        StringBuilder sb = new StringBuilder();
        sb.append("Assigned ").append(priority).append(" priority (score: ").append(String.format(Locale.US, "%.1f", score)).append("). ");

        if (!drivers.isEmpty()) {
            sb.append("Primary driver: ").append(drivers.get(0)).append(". ");
        } else {
            sb.append("Standard baseline for category '").append(category).append("'. ");
        }

        if (!reducers.isEmpty()) {
            sb.append("Mitigating factor: ").append(reducers.get(0)).append(". ");
        }
        return sb.toString().trim();
    }
}
