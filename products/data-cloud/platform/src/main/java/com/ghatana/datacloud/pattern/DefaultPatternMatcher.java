package com.ghatana.datacloud.pattern;

import com.ghatana.datacloud.DataRecord;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of PatternMatcher.
 *
 * @doc.type class
 * @doc.purpose Default pattern matching implementation
 * @doc.layer core
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

    private static class StructuralMatchStrategy implements MatchStrategy {
        @Override
        public String getName() { return "Structural"; }

        @Override
        public Promise<PatternMatch> evaluate(DataRecord record, PatternRecord pattern, MatchContext context) {
            // Placeholder logic
            return Promise.of(PatternMatch.builder()
                    .pattern(pattern)
                    .score(0.8f)
                    .confidence(0.7f)
                    .explanation("Structural match based on schema")
                    .build());
        }
    }

    private static class TemporalMatchStrategy implements MatchStrategy {
        @Override
        public String getName() { return "Temporal"; }

        @Override
        public Promise<PatternMatch> evaluate(DataRecord record, PatternRecord pattern, MatchContext context) {
             // Placeholder logic
            return Promise.of(PatternMatch.builder()
                    .pattern(pattern)
                    .score(0.6f)
                    .confidence(0.5f)
                    .explanation("Temporal match")
                    .build());
        }
    }

    private static class BehavioralMatchStrategy implements MatchStrategy {
        @Override
        public String getName() { return "Behavioral"; }

        @Override
        public Promise<PatternMatch> evaluate(DataRecord record, PatternRecord pattern, MatchContext context) {
             // Placeholder logic
            return Promise.of(PatternMatch.builder()
                    .pattern(pattern)
                    .score(0.5f)
                    .confidence(0.4f)
                    .explanation("Behavioral match")
                    .build());
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
}

