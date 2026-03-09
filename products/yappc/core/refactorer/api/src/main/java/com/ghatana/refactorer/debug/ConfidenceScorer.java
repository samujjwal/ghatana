package com.ghatana.refactorer.debug;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scores the confidence of fix suggestions based on various factors including: - Pattern match
 * strength - Historical success rate - Language-specific heuristics - Contextual relevance
 
 * @doc.type class
 * @doc.purpose Handles confidence scorer operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class ConfidenceScorer {
    private static final Logger log = LoggerFactory.getLogger(ConfidenceScorer.class);

    // Weight factors for different confidence components (sum should be 1.0)
    private static final double PATTERN_MATCH_WEIGHT = 0.4;
    private static final double HISTORICAL_WEIGHT = 0.3;
    private static final double CONTEXT_WEIGHT = 0.2;
    private static final double LANGUAGE_WEIGHT = 0.1;

    // Historical success tracking
    private final Map<String, SuccessStats> successStats = new HashMap<>();

    /**
     * Calculates an overall confidence score for a fix suggestion.
     *
     * @param suggestion The fix suggestion to score
     * @param errorMessage The error message being analyzed
     * @param context Additional context for the error (e.g., file type, project structure)
     * @return A confidence score between 0.0 (low) and 1.0 (high)
     */
    public double calculateConfidence(
            FixSuggestion suggestion, String errorMessage, FixContext context) {
        if (suggestion == null || errorMessage == null) {
            return 0.0;
        }

        // First check for language mismatch - this is a strong signal
        double languageScore = calculateLanguageScore(suggestion, context);
        if (languageScore < 0.5) {
            // If there's a language mismatch, cap the total score at 0.5
            // This ensures language mismatches are always penalized
            return Math.min(0.5, calculatePatternMatchScore(suggestion, errorMessage) * 0.5);
        }

        double patternScore = calculatePatternMatchScore(suggestion, errorMessage);
        double historicalScore = calculateHistoricalScore(suggestion.getId());
        double contextScore = calculateContextScore(suggestion, context);

        // Weighted sum of all components
        double score =
                (patternScore * PATTERN_MATCH_WEIGHT)
                        + (historicalScore * HISTORICAL_WEIGHT)
                        + (contextScore * CONTEXT_WEIGHT)
                        + (languageScore * LANGUAGE_WEIGHT);

        // Ensure the score is within bounds
        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * Updates the historical success rate for a fix suggestion.
     *
     * @param suggestionId The ID of the fix suggestion
     * @param wasSuccessful Whether the fix was successfully applied
     */
    public void recordFixOutcome(String suggestionId, boolean wasSuccessful) {
        if (suggestionId == null) {
            return;
        }

        successStats
                .computeIfAbsent(suggestionId, k -> new SuccessStats())
                .recordOutcome(wasSuccessful);
    }

    private double calculatePatternMatchScore(FixSuggestion suggestion, String errorMessage) {
        try {
            if (suggestion.getErrorPattern() != null && !suggestion.getErrorPattern().isEmpty()) {
                // Pre-process the error message and pattern for better matching
                String normalizedError = errorMessage.trim().toLowerCase();
                String patternStr = suggestion.getErrorPattern().trim();

                // Check for direct string contains match first (more reliable for simple patterns)
                if (normalizedError.contains(patternStr.toLowerCase())) {
                    // Higher score if the pattern is a significant part of the error
                    double baseScore = 0.8;

                    // If the pattern is the full error message, give maximum score
                    if (normalizedError.equals(patternStr.toLowerCase())) {
                        baseScore = 1.0;
                    }

                    // If the pattern is at the start of the error, give a small bonus
                    if (normalizedError.startsWith(patternStr.toLowerCase())) {
                        baseScore = Math.min(1.0, baseScore + 0.1);
                    }

                    log.info(String.format("Direct pattern match - pattern: '%s', error: '%s', score: %.2f", patternStr, normalizedError, baseScore));
                    return baseScore;
                }

                // Fall back to regex matching
                Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(errorMessage);

                if (matcher.find()) {
                    // Check for full match vs partial match
                    String matchedText = matcher.group(0);
                    boolean isFullMatch = matchedText.trim().equalsIgnoreCase(errorMessage.trim());

                    // Higher base score for full matches
                    double baseScore = isFullMatch ? 1.0 : 0.7;

                    // Bonus for more specific matches (more capturing groups)
                    int groupCount = Math.min(matcher.groupCount(), 5);
                    double groupBonus = 0.06 * groupCount; // Up to 0.3 bonus

                    // Calculate final score
                    double score = Math.min(1.0, baseScore + groupBonus);

                    // Debug logging
                    log.info("Regex pattern match - isFullMatch: %b, baseScore: %.2f, groupCount:"
                                    + " %d, groupBonus: %.2f, score: %.2f%n",
                            isFullMatch, baseScore, groupCount, groupBonus, score);

                    return score;
                }
                return 0.2; // Return lower score when pattern doesn't match at all
            }

            // Fall back to simple string matching
            if (suggestion.getId() != null && errorMessage.contains(suggestion.getId())) {
                return 0.6;
            }
            if (suggestion.getDescription() != null
                    && errorMessage
                            .toLowerCase()
                            .contains(suggestion.getDescription().toLowerCase())) {
                return 0.5;
            }

            return 0.1; // Very low confidence for no match
        } catch (Exception e) {
            log.warn(
                    "Error calculating pattern match score for suggestion: {}",
                    suggestion.getId(),
                    e);
            return 0.1;
        }
    }

    private double calculateHistoricalScore(String suggestionId) {
        SuccessStats stats = successStats.get(suggestionId);
        if (stats == null || stats.getTotalAttempts() == 0) {
            return 0.5; // Default to medium confidence for no historical data
        }

        // Use a weighted average that becomes more confident with more data
        double successRate = stats.getSuccessRate();
        double weight = Math.min(1.0, stats.getTotalAttempts() / 10.0); // Cap at 10 attempts

        return 0.5 * (1 - weight) + successRate * weight;
    }

    private double calculateContextScore(FixSuggestion suggestion, FixContext context) {
        if (context == null || suggestion.getLanguage() == null) {
            return 0.5; // Neutral score for no context or no language specified
        }

        // Check if the suggestion's language matches the context
        String suggestionLang = suggestion.getLanguage().toLowerCase();
        String contextLang =
                context.getLanguage() != null ? context.getLanguage().toLowerCase() : "";

        // Handle common language aliases
        boolean languageMatches =
                suggestionLang.equals(contextLang)
                        || (suggestionLang.equals("js") && contextLang.equals("javascript"))
                        || (suggestionLang.equals("javascript") && contextLang.equals("js"))
                        || (suggestionLang.equals("ts") && contextLang.equals("typescript"))
                        || (suggestionLang.equals("typescript") && contextLang.equals("ts"));

        if (!languageMatches) {
            return 0.2; // Low score for language mismatch
        }

        // Check if the file extension in context matches the language
        if (context.getFilePath() != null) {
            String fileExt =
                    context.getFilePath()
                            .substring(Math.max(context.getFilePath().lastIndexOf('.'), 0));

            if ((suggestionLang.equals("java") && !fileExt.equals(".java"))
                    || (suggestionLang.equals("python") && !fileExt.equals(".py"))
                    || (suggestionLang.matches("(js|javascript|typescript|ts)")
                            && !fileExt.matches("\\.(js|jsx|ts|tsx|mjs|cjs)"))) {
                return 0.4; // Slightly higher than language mismatch but still low
            }
        }

        // Higher score for matching language and context
        return 0.9;
    }

    private double calculateLanguageScore(FixSuggestion suggestion, FixContext context) {
        if (context == null || suggestion.getLanguage() == null) {
            return 0.5; // Neutral score if no context or language specified
        }

        String suggestionLang = suggestion.getLanguage().toLowerCase();
        String contextLang = context.getLanguage().toLowerCase();

        // Handle common language aliases
        boolean languageMatches =
                suggestionLang.equals(contextLang)
                        || (suggestionLang.equals("js") && contextLang.equals("javascript"))
                        || (suggestionLang.equals("javascript") && contextLang.equals("js"))
                        || (suggestionLang.equals("ts") && contextLang.equals("typescript"))
                        || (suggestionLang.equals("typescript") && contextLang.equals("ts"));

        return languageMatches ? 0.8 : 0.3; // Lower score for language mismatch
    }

    /**
 * Tracks success/failure statistics for fix suggestions. */
    private static class SuccessStats {
        private int successCount = 0;
        private int failureCount = 0;

        void recordOutcome(boolean wasSuccessful) {
            if (wasSuccessful) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        int getTotalAttempts() {
            return successCount + failureCount;
        }

        double getSuccessRate() {
            int total = getTotalAttempts();
            return total > 0 ? (double) successCount / total : 0.5;
        }
    }
}
