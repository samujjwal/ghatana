/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.pattern;

import com.ghatana.datacloud.DataRecord;
import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Service interface for matching data records against known patterns.
 *
 * <p>Pattern matching evaluates incoming data against the catalog of learned
 * patterns to detect occurrences and trigger appropriate responses.
 *
 * <h2>Matching Strategies</h2>
 * <ul>
 *   <li><b>Exact</b>: All conditions must match exactly</li>
 *   <li><b>Fuzzy</b>: Similarity-based matching with threshold</li>
 *   <li><b>Semantic</b>: Vector similarity matching</li>
 *   <li><b>Hybrid</b>: Combination of multiple strategies</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Pattern matching against data records
 * @doc.layer core
 * @doc.pattern Strategy, Visitor
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface PatternMatcher {

    /**
     * Matches a single record against all applicable patterns.
     *
     * @param record the record to match
     * @param context matching context
     * @return Promise containing all matching patterns with scores
     */
    Promise<List<PatternMatch>> match(DataRecord record, MatchContext context);

    /**
     * Matches multiple records (batch mode).
     *
     * @param records the records to match
     * @param context matching context
     * @return Promise containing matches for each record
     */
    Promise<BatchMatchResult> matchBatch(List<DataRecord> records, MatchContext context);

    /**
     * Checks if a specific pattern matches a record.
     *
     * @param record the record to check
     * @param pattern the pattern to match against
     * @return Promise containing the match result
     */
    Promise<PatternMatch> matchPattern(DataRecord record, PatternRecord pattern);

    /**
     * Finds patterns that might apply to a record type.
     *
     * @param recordType the record type
     * @param tenantId the tenant ID
     * @return Promise containing applicable patterns
     */
    Promise<List<PatternRecord>> findApplicablePatterns(String recordType, String tenantId);

    /**
     * Gets the matching strategy for a pattern type.
     *
     * @param type the pattern type
     * @return the matching strategy
     */
    MatchStrategy getStrategy(PatternType type);

    // ═══════════════════════════════════════════════════════════════════════════
    // Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Context for pattern matching.
     */
    @Value
    @Builder
    class MatchContext {
        /**
         * The tenant ID.
         */
        String tenantId;

        /**
         * Pattern types to match against (empty = all).
         */
        @Builder.Default
        List<PatternType> patternTypes = List.of();

        /**
         * Minimum confidence for pattern consideration.
         */
        @Builder.Default
        float minPatternConfidence = 0.5f;

        /**
         * Minimum match score to include in results.
         */
        @Builder.Default
        float minMatchScore = 0.5f;

        /**
         * Maximum patterns to return.
         */
        @Builder.Default
        int maxMatches = 10;

        /**
         * Whether to include pattern details in results.
         */
        @Builder.Default
        boolean includeDetails = false;

        /**
         * Whether to update pattern statistics on match.
         */
        @Builder.Default
        boolean updateStats = true;

        /**
         * Additional context data.
         */
        @Builder.Default
        Map<String, Object> contextData = Map.of();

        /**
         * Creates a default context for a tenant.
         *
         * @param tenantId the tenant ID
         * @return default context
         */
        public static MatchContext forTenant(String tenantId) {
            return MatchContext.builder().tenantId(tenantId).build();
        }
    }

    /**
     * Result of matching a record against a pattern.
     */
    @Value
    @Builder
    class PatternMatch {
        /**
         * The matched pattern.
         */
        PatternRecord pattern;

        /**
         * The overall match score (0.0 to 1.0).
         */
        float score;

        /**
         * Confidence in this match.
         */
        float confidence;

        /**
         * Individual condition scores.
         */
        @Builder.Default
        Map<String, Float> conditionScores = Map.of();

        /**
         * Which conditions matched.
         */
        @Builder.Default
        List<String> matchedConditions = List.of();

        /**
         * Which conditions failed.
         */
        @Builder.Default
        List<String> failedConditions = List.of();

        /**
         * Explanation of the match.
         */
        String explanation;

        /**
         * Recommended actions based on this match.
         */
        @Builder.Default
        List<String> recommendations = List.of();

        /**
         * Checks if this is a strong match.
         *
         * @param threshold minimum score for strong match
         * @return true if strong match
         */
        public boolean isStrongMatch(float threshold) {
            return score >= threshold && confidence >= threshold;
        }

        /**
         * Creates a no-match result.
         *
         * @param pattern the pattern that didn't match
         * @return no-match result
         */
        public static PatternMatch noMatch(PatternRecord pattern) {
            return PatternMatch.builder()
                    .pattern(pattern)
                    .score(0.0f)
                    .confidence(0.0f)
                    .explanation("Pattern conditions not satisfied")
                    .build();
        }
    }

    /**
     * Result of batch matching.
     */
    @Value
    @Builder
    class BatchMatchResult {
        /**
         * Matches keyed by record ID.
         */
        Map<String, List<PatternMatch>> matchesByRecord;

        /**
         * Total records processed.
         */
        int totalRecords;

        /**
         * Records with at least one match.
         */
        int matchedRecords;

        /**
         * Processing time in milliseconds.
         */
        long processingTimeMs;

        /**
         * Most frequently matched patterns.
         */
        @Builder.Default
        List<PatternFrequency> topPatterns = List.of();
    }

    /**
     * Pattern match frequency.
     */
    @Value
    @Builder
    class PatternFrequency {
        /**
         * The pattern ID.
         */
        String patternId;

        /**
         * Number of matches.
         */
        int matchCount;

        /**
         * Average match score.
         */
        float avgScore;
    }

    /**
     * Strategy for matching patterns.
     */
    interface MatchStrategy {
        /**
         * Gets the strategy name.
         *
         * @return strategy name
         */
        String getName();

        /**
         * Calculates match score.
         *
         * @param record the record to match
         * @param pattern the pattern to match against
         * @param context matching context
         * @return match result
         */
        Promise<PatternMatch> evaluate(
                DataRecord record,
                PatternRecord pattern,
                MatchContext context);
    }
}
