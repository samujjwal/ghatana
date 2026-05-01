package com.ghatana.datacloud.pattern;

import com.ghatana.datacloud.DataRecord;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of PatternMatcher.
 *
 * @doc.type class
 * @doc.purpose Default pattern matching implementation
 * @doc.layer core
 * @doc.pattern Strategy
 */
@Slf4j
public class DefaultPatternMatcher implements PatternMatcher {

    private final PatternCatalog catalog;
    private final Map<PatternType, MatchStrategy> strategies = new ConcurrentHashMap<>();

    public DefaultPatternMatcher(PatternCatalog catalog) {
        this.catalog = catalog;
        // Register default strategies
        strategies.put(PatternType.STRUCTURAL, new StructuralMatchStrategy());
        strategies.put(PatternType.TEMPORAL, new TemporalMatchStrategy());
        strategies.put(PatternType.BEHAVIORAL, new BehavioralMatchStrategy());
        // Fallback for others
        MatchStrategy defaultStrategy = new DefaultMatchStrategy();
        for (PatternType type : PatternType.values()) {
            strategies.putIfAbsent(type, defaultStrategy);
        }
    }

    @Override
    public Promise<List<PatternMatch>> match(DataRecord record, MatchContext context) {
        return findApplicablePatterns(record.getRecordType().name(), context.getTenantId())
                .then(patterns -> {
                    List<Promise<PatternMatch>> matchPromises = patterns.stream()
                            .map(p -> matchPattern(record, p))
                            .collect(Collectors.toList());

                    return Promises.toList(matchPromises)
                            .map(matches -> matches.stream()
                                    .filter(m -> m.getScore() >= context.getMinMatchScore())
                                    .collect(Collectors.toList()));
                });
    }

    @Override
    public Promise<BatchMatchResult> matchBatch(List<DataRecord> records, MatchContext context) {
        long startTime = System.currentTimeMillis();
        List<Promise<List<PatternMatch>>> promises = records.stream()
                .map(r -> match(r, context))
                .collect(Collectors.toList());

        return Promises.toList(promises)
                .map(results -> {
                    Map<String, List<PatternMatch>> matchesByRecord = new ConcurrentHashMap<>();
                    int matchedRecords = 0;
                    for (int i = 0; i < records.size(); i++) {
                        List<PatternMatch> matches = results.get(i);
                        if (!matches.isEmpty()) {
                            matchesByRecord.put(records.get(i).getId().toString(), matches);
                            matchedRecords++;
                        }
                    }

                    return BatchMatchResult.builder()
                            .matchesByRecord(matchesByRecord)
                            .totalRecords(records.size())
                            .matchedRecords(matchedRecords)
                            .processingTimeMs(System.currentTimeMillis() - startTime)
                            .build();
                });
    }

    @Override
    public Promise<PatternMatch> matchPattern(DataRecord record, PatternRecord pattern) {
        MatchStrategy strategy = getStrategy(pattern.getType());
        if (strategy == null) {
            return Promise.of(PatternMatch.builder()
                    .pattern(pattern)
                    .score(0.0f)
                    .confidence(0.0f)
                    .explanation("No strategy for type: " + pattern.getType())
                    .build());
        }
        return strategy.evaluate(record, pattern, MatchContext.forTenant(record.getTenantId()));
    }

    @Override
    public Promise<List<PatternRecord>> findApplicablePatterns(String recordType, String tenantId) {
        return catalog.search(PatternCatalog.PatternQuery.builder()
                .tags(List.of("type:" + recordType))
                .tenantId(tenantId)
                .statuses(List.of(PatternRecord.PatternStatus.ACTIVE))
                .build())
                .map(PatternCatalog.PatternSearchResult::getPatterns);
    }

    @Override
    public MatchStrategy getStrategy(PatternType type) {
        return strategies.get(type);
    }

    // Strategies

    /**
     * Evaluates structural patterns by comparing the record's data field set against the
     * expected schema defined in the pattern signature.
     *
     * <p>The pattern {@code signature} map may carry a {@code "requiredFields"} list of
     * expected field names. Presence of each required field in the record's data map is
     * scored individually; the aggregate score is the fraction of required fields found.
     * {@link PatternRecord.PatternCondition} items on the pattern are then evaluated and
     * factored into the final confidence via a weighted mean.
     */
    private static class StructuralMatchStrategy implements MatchStrategy {

        @Override
        public String getName() { return "Structural"; }

        @Override
        @SuppressWarnings("unchecked")
        public Promise<PatternMatch> evaluate(DataRecord record, PatternRecord pattern, MatchContext context) {
            Map<String, Object> data = record.getData() != null ? record.getData() : Map.of();
            Map<String, Object> signature = pattern.getSignature() != null ? pattern.getSignature() : Map.of();

            // Score required-field presence from signature
            List<String> required = new ArrayList<>();
            Object reqObj = signature.get("requiredFields");
            if (reqObj instanceof List<?> reqList) {
                reqList.forEach(f -> required.add(String.valueOf(f)));
            }
            if (required.isEmpty()) {
                // Fall back: treat every signature key (excluding meta keys) as an expected field
                signature.keySet().stream()
                        .filter(k -> !k.equals("requiredFields") && !k.equals("optionalFields"))
                        .forEach(required::add);
            }

            List<String> matched = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            Map<String, Float> conditionScores = new HashMap<>();

            for (String field : required) {
                if (data.containsKey(field)) {
                    matched.add(field);
                    conditionScores.put("field:" + field, 1.0f);
                } else {
                    missing.add(field);
                    conditionScores.put("field:" + field, 0.0f);
                }
            }

            float fieldScore = required.isEmpty() ? 0.5f : (float) matched.size() / required.size();

            // Evaluate explicit PatternCondition items
            ConditionEvalResult condResult = evaluateConditions(pattern.getConditions(), data);
            conditionScores.putAll(condResult.scores());

            // Blend field-presence score with condition score; weight conditions more when present
            float score;
            float confidence;
            if (!pattern.getConditions().isEmpty()) {
                score = 0.4f * fieldScore + 0.6f * condResult.score();
                confidence = condResult.confidence() * pattern.getConfidence();
            } else {
                score = fieldScore;
                confidence = required.isEmpty() ? 0.3f : Math.min(0.9f, fieldScore * pattern.getConfidence());
            }

            String explanation = buildStructuralExplanation(matched, missing, condResult);
            return Promise.of(PatternMatch.builder()
                    .pattern(pattern)
                    .score(score)
                    .confidence(confidence)
                    .conditionScores(conditionScores)
                    .matchedConditions(matched)
                    .failedConditions(missing)
                    .explanation(explanation)
                    .build());
        }

        private String buildStructuralExplanation(
                List<String> matched, List<String> missing, ConditionEvalResult condResult) {
            StringBuilder sb = new StringBuilder("Structural match: ");
            sb.append(matched.size()).append(" of ").append(matched.size() + missing.size()).append(" required fields present");
            if (!missing.isEmpty()) {
                sb.append("; missing: ").append(String.join(", ", missing));
            }
            if (condResult.evaluated() > 0) {
                sb.append("; ").append(condResult.passed()).append("/").append(condResult.evaluated()).append(" conditions passed");
            }
            return sb.toString();
        }
    }

    /**
     * Evaluates temporal patterns by inspecting timestamp fields in the record's data.
     *
     * <p>The pattern {@code signature} map may carry:
     * <ul>
     *   <li>{@code "timestampField"} — name of the ISO-8601 timestamp field in record data</li>
     *   <li>{@code "expectedHourOfDay"} — expected UTC hour (0–23) when the event occurs</li>
     *   <li>{@code "expectedDayOfWeek"} — expected ISO day-of-week (1=Mon … 7=Sun)</li>
     *   <li>{@code "maxAgeSeconds"} — maximum age in seconds for the record to be relevant</li>
     * </ul>
     * When no signature hints are present, the strategy detects common timestamp field names
     * and scores the match based on the timestamp's age relative to the default window.
     */
    private static class TemporalMatchStrategy implements MatchStrategy {

        private static final List<String> TIMESTAMP_FIELD_NAMES = List.of(
                "timestamp", "createdAt", "created_at", "eventAt", "event_at",
                "occurredAt", "occurred_at", "recordedAt", "recorded_at", "ts");

        private static final long DEFAULT_RECENCY_WINDOW_SECONDS = 86_400L; // 24 h

        @Override
        public String getName() { return "Temporal"; }

        @Override
        public Promise<PatternMatch> evaluate(DataRecord record, PatternRecord pattern, MatchContext context) {
            Map<String, Object> data = record.getData() != null ? record.getData() : Map.of();
            Map<String, Object> signature = pattern.getSignature() != null ? pattern.getSignature() : Map.of();

            // Locate the timestamp field
            String tsField = signature.containsKey("timestampField")
                    ? String.valueOf(signature.get("timestampField"))
                    : detectTimestampField(data);

            Instant recordTime = null;
            if (tsField != null) {
                Object rawTs = data.get(tsField);
                recordTime = parseInstant(rawTs);
            }

            if (recordTime == null) {
                // No usable timestamp — low-confidence near-miss
                return Promise.of(PatternMatch.builder()
                        .pattern(pattern)
                        .score(0.1f)
                        .confidence(0.1f)
                        .explanation("Temporal match skipped: no parseable timestamp field in record data")
                        .build());
            }

            Instant now = Instant.now();
            long ageSeconds = ChronoUnit.SECONDS.between(recordTime, now);

            // Recency score: 1.0 if within window, decaying linearly to 0 at 2× window
            long windowSeconds = signature.containsKey("maxAgeSeconds")
                    ? ((Number) signature.get("maxAgeSeconds")).longValue()
                    : DEFAULT_RECENCY_WINDOW_SECONDS;
            float recencyScore = ageSeconds <= 0 ? 1.0f
                    : Math.max(0.0f, 1.0f - (float) ageSeconds / (2.0f * windowSeconds));

            // Optional: hour-of-day alignment
            float hourScore = 1.0f;
            if (signature.containsKey("expectedHourOfDay")) {
                int expected = ((Number) signature.get("expectedHourOfDay")).intValue();
                int actual = recordTime.atZone(java.time.ZoneOffset.UTC).getHour();
                int diff = Math.abs(actual - expected);
                diff = Math.min(diff, 24 - diff); // wrap around midnight
                hourScore = Math.max(0.0f, 1.0f - diff / 6.0f); // tolerate ±6 h
            }

            // Optional: day-of-week alignment
            float dowScore = 1.0f;
            if (signature.containsKey("expectedDayOfWeek")) {
                int expected = ((Number) signature.get("expectedDayOfWeek")).intValue();
                int actual = recordTime.atZone(java.time.ZoneOffset.UTC)
                        .getDayOfWeek().getValue(); // 1=Mon
                dowScore = (actual == expected) ? 1.0f : 0.2f;
            }

            // Evaluate explicit conditions
            ConditionEvalResult condResult = evaluateConditions(pattern.getConditions(), data);

            float score = blend(recencyScore, hourScore, dowScore,
                    pattern.getConditions().isEmpty() ? recencyScore : condResult.score());
            float confidence = Math.min(0.95f, score * pattern.getConfidence());

            String explanation = String.format(
                    "Temporal match: recency=%.2f, hourAlignment=%.2f, dowAlignment=%.2f, age=%ds",
                    recencyScore, hourScore, dowScore, ageSeconds);

            return Promise.of(PatternMatch.builder()
                    .pattern(pattern)
                    .score(score)
                    .confidence(confidence)
                    .explanation(explanation)
                    .build());
        }

        private static String detectTimestampField(Map<String, Object> data) {
            for (String candidate : TIMESTAMP_FIELD_NAMES) {
                if (data.containsKey(candidate)) return candidate;
            }
            return null;
        }

        private static Instant parseInstant(Object value) {
            if (value == null) return null;
            if (value instanceof Instant i) return i;
            if (value instanceof Long l) return Instant.ofEpochMilli(l);
            if (value instanceof Number n) return Instant.ofEpochMilli(n.longValue());
            try {
                return Instant.parse(String.valueOf(value));
            } catch (Exception ignored) {
                return null;
            }
        }

        private static float blend(float recency, float hour, float dow, float condScore) {
            return 0.4f * recency + 0.25f * hour + 0.1f * dow + 0.25f * condScore;
        }
    }

    /**
     * Evaluates behavioral patterns by comparing the record's data against expected
     * actions, states, or event types defined in the pattern signature.
     *
     * <p>The pattern {@code signature} map may carry:
     * <ul>
     *   <li>{@code "expectedActions"} — list of action or event-type strings expected</li>
     *   <li>{@code "actionField"} — field name carrying the action/event type in record data</li>
     *   <li>{@code "expectedStates"} — list of state values expected</li>
     *   <li>{@code "stateField"} — field name carrying the state in record data</li>
     * </ul>
     */
    private static class BehavioralMatchStrategy implements MatchStrategy {

        @Override
        public String getName() { return "Behavioral"; }

        @Override
        @SuppressWarnings("unchecked")
        public Promise<PatternMatch> evaluate(DataRecord record, PatternRecord pattern, MatchContext context) {
            Map<String, Object> data = record.getData() != null ? record.getData() : Map.of();
            Map<String, Object> signature = pattern.getSignature() != null ? pattern.getSignature() : Map.of();

            List<String> matchedBehaviors = new ArrayList<>();
            List<String> missingBehaviors = new ArrayList<>();
            int checks = 0;

            // Action/event-type matching
            Object rawActions = signature.get("expectedActions");
            if (rawActions instanceof List<?> actionList) {
                String actionField = signature.containsKey("actionField")
                        ? String.valueOf(signature.get("actionField"))
                        : detectActionField(data);
                String recordAction = actionField != null
                        ? normalise(data.get(actionField))
                        : null;
                for (Object expected : actionList) {
                    String expectedStr = normalise(expected);
                    checks++;
                    if (recordAction != null && recordAction.equals(expectedStr)) {
                        matchedBehaviors.add("action:" + expectedStr);
                    } else {
                        missingBehaviors.add("action:" + expectedStr);
                    }
                }
            }

            // State matching
            Object rawStates = signature.get("expectedStates");
            if (rawStates instanceof List<?> stateList) {
                String stateField = signature.containsKey("stateField")
                        ? String.valueOf(signature.get("stateField"))
                        : detectStateField(data);
                String recordState = stateField != null
                        ? normalise(data.get(stateField))
                        : null;
                for (Object expected : stateList) {
                    String expectedStr = normalise(expected);
                    checks++;
                    if (recordState != null && recordState.equals(expectedStr)) {
                        matchedBehaviors.add("state:" + expectedStr);
                    } else {
                        missingBehaviors.add("state:" + expectedStr);
                    }
                }
            }

            // Evaluate explicit conditions
            ConditionEvalResult condResult = evaluateConditions(pattern.getConditions(), data);

            float behaviorScore = checks == 0 ? 0.3f : (float) matchedBehaviors.size() / checks;
            float score;
            float confidence;
            if (!pattern.getConditions().isEmpty()) {
                score = 0.5f * behaviorScore + 0.5f * condResult.score();
                confidence = Math.min(0.9f, score * pattern.getConfidence());
            } else {
                score = behaviorScore;
                confidence = checks == 0 ? 0.2f : Math.min(0.85f, score * pattern.getConfidence());
            }

            String explanation = String.format(
                    "Behavioral match: %d/%d behaviors matched", matchedBehaviors.size(), checks);

            return Promise.of(PatternMatch.builder()
                    .pattern(pattern)
                    .score(score)
                    .confidence(confidence)
                    .matchedConditions(matchedBehaviors)
                    .failedConditions(missingBehaviors)
                    .explanation(explanation)
                    .build());
        }

        private static String detectActionField(Map<String, Object> data) {
            for (String candidate : List.of("action", "eventType", "event_type", "type", "operation")) {
                if (data.containsKey(candidate)) return candidate;
            }
            return null;
        }

        private static String detectStateField(Map<String, Object> data) {
            for (String candidate : List.of("state", "status", "phase", "stage")) {
                if (data.containsKey(candidate)) return candidate;
            }
            return null;
        }

        private static String normalise(Object value) {
            return value == null ? null : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        }
    }

    private static class DefaultMatchStrategy implements MatchStrategy {
        @Override
        public String getName() { return "Default"; }

        @Override
        public Promise<PatternMatch> evaluate(DataRecord record, PatternRecord pattern, MatchContext context) {
            return Promise.of(PatternMatch.builder()
                    .pattern(pattern)
                    .score(0.1f)
                    .confidence(0.1f)
                    .explanation("Default match (low confidence)")
                    .build());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared condition evaluation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Evaluates a list of {@link PatternRecord.PatternCondition} items against a record's
     * data map.
     *
     * <p>Supported operators: {@code eq}, {@code neq}, {@code gt}, {@code gte}, {@code lt},
     * {@code lte}, {@code contains}, {@code exists}, {@code notExists}.
     *
     * <p>Each condition contributes its {@code weight} to the weighted mean; required
     * conditions that fail reduce the overall confidence.
     *
     * @param conditions list of conditions to evaluate
     * @param data       record data map
     * @return {@link ConditionEvalResult} with aggregate score, confidence, and per-field scores
     */
    static ConditionEvalResult evaluateConditions(
            List<PatternRecord.PatternCondition> conditions, Map<String, Object> data) {
        if (conditions == null || conditions.isEmpty()) {
            return new ConditionEvalResult(1.0f, 1.0f, 0, 0, Map.of());
        }
        float totalWeight = 0f;
        float weightedScore = 0f;
        int passed = 0;
        int evaluated = conditions.size();
        boolean requiredFailed = false;
        Map<String, Float> scores = new HashMap<>();

        for (PatternRecord.PatternCondition cond : conditions) {
            float condScore = evaluateSingleCondition(cond, data);
            float weight = cond.getWeight();
            totalWeight += weight;
            weightedScore += condScore * weight;
            scores.put(cond.getField() + ":" + cond.getOperator(), condScore);
            if (condScore >= 1.0f) {
                passed++;
            } else if (cond.isRequired()) {
                requiredFailed = true;
            }
        }

        float score = totalWeight == 0f ? 0f : weightedScore / totalWeight;
        float confidence = requiredFailed ? Math.min(score, 0.3f) : score;
        return new ConditionEvalResult(score, confidence, evaluated, passed, Map.copyOf(scores));
    }

    @SuppressWarnings("unchecked")
    private static float evaluateSingleCondition(
            PatternRecord.PatternCondition cond, Map<String, Object> data) {
        Object actual = data.get(cond.getField());
        Object expected = cond.getValue();
        String op = cond.getOperator() == null ? "exists" : cond.getOperator().toLowerCase(Locale.ROOT);
        return switch (op) {
            case "exists"    -> actual != null ? 1.0f : 0.0f;
            case "notexists" -> actual == null ? 1.0f : 0.0f;
            case "eq"        -> actual != null && String.valueOf(actual).equals(String.valueOf(expected)) ? 1.0f : 0.0f;
            case "neq"       -> actual == null || !String.valueOf(actual).equals(String.valueOf(expected)) ? 1.0f : 0.0f;
            case "contains"  -> actual != null && String.valueOf(actual)
                                    .toLowerCase(Locale.ROOT)
                                    .contains(String.valueOf(expected).toLowerCase(Locale.ROOT))
                                ? 1.0f : 0.0f;
            case "gt"        -> compareNumeric(actual, expected) > 0 ? 1.0f : 0.0f;
            case "gte"       -> compareNumeric(actual, expected) >= 0 ? 1.0f : 0.0f;
            case "lt"        -> compareNumeric(actual, expected) < 0 ? 1.0f : 0.0f;
            case "lte"       -> compareNumeric(actual, expected) <= 0 ? 1.0f : 0.0f;
            default -> {
                log.debug("Unknown condition operator '{}' for field '{}', treating as no-match", op, cond.getField());
                yield 0.0f;
            }
        };
    }

    private static int compareNumeric(Object actual, Object expected) {
        try {
            double a = Double.parseDouble(String.valueOf(actual));
            double b = Double.parseDouble(String.valueOf(expected));
            return Double.compare(a, b);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Result of evaluating a condition list.
     *
     * @param score      weighted mean score across all conditions (0.0–1.0)
     * @param confidence adjusted confidence — degraded when required conditions fail
     * @param evaluated  total number of conditions evaluated
     * @param passed     number of conditions that fully matched
     * @param scores     per-condition scores keyed by {@code "field:operator"}
     */
    record ConditionEvalResult(
            float score,
            float confidence,
            int evaluated,
            int passed,
            Map<String, Float> scores) {}
}
