/**
 * AI-powered requirement suggestions and recommendations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides intelligent requirement suggestions based on project context,
 * existing requirements, and learned patterns. Uses semantic similarity,
 * collaborative filtering, and content-based recommendation to suggest relevant
 * requirements.
 *
 * <p>
 * <b>Key Components</b><br>
 * <ul>
 * <li>{@link com.ghatana.requirements.ai.suggestions.AISuggestion} - Suggestion
 * value object</li>
 * <li>{@link com.ghatana.requirements.ai.suggestions.SuggestionEngine} -
 * Suggestion generation engine</li>
 * <li>{@link com.ghatana.requirements.ai.suggestions.SuggestionRanker} -
 * Suggestion ranking service</li>
 * <li>{@link com.ghatana.requirements.ai.suggestions.SuggestionStatus} -
 * Suggestion lifecycle status</li>
 * </ul>
 *
 * <p>
 * <b>Suggestion Types</b><br>
 * <ul>
 * <li><b>Similar Requirements</b>: Based on semantic similarity</li>
 * <li><b>Complementary Requirements</b>: Frequently co-occurring
 * requirements</li>
 * <li><b>Missing Requirements</b>: Gap analysis (security, performance,
 * etc.)</li>
 * <li><b>Refinement Suggestions</b>: Improvements to existing requirements</li>
 * </ul>
 *
 * <p>
 * <b>Basic Usage</b><br>
 * <pre>{@code
 * // Generate suggestions
 * String context = "User authentication system";
 * List<Requirement> existingRequirements = // ... existing requirements
 *
 * SuggestionEngine engine = new SuggestionEngine(
 *     embeddingService,
 *     vectorStore,
 *     llmService
 * );
 *
 * Promise<List<AISuggestion>> suggestions = engine.generateSuggestions(
 *     context,
 *     existingRequirements,
 *     10
 * );
 *
 * suggestions.then(suggestionList -> {
 *     for (AISuggestion suggestion : suggestionList) {
 *         logger.info("Suggestion: {} (confidence: {})",
 *             suggestion.getContent(),
 *             suggestion.getConfidenceScore());
 *     }
 * });
 * }</pre>
 *
 * <p>
 * <b>Ranking Strategies</b><br>
 * <pre>{@code
 * SuggestionRanker ranker = new SuggestionRanker();
 * List<AISuggestion> ranked = ranker.rankSuggestions(
 *     suggestions,
 *     RankingStrategy.builder()
 *         .withRelevanceWeight(0.4)
 *         .withNoveltyWeight(0.3)
 *         .withCompletenessWeight(0.3)
 *         .build()
 * );
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * All components are thread-safe and can be safely shared across threads.
 *
 * @since 1.0.0
 * @see com.ghatana.requirements.ai.suggestions.AISuggestion
 * @see com.ghatana.requirements.ai.suggestions.SuggestionEngine
 * @doc.type package
 * @doc.purpose AI-powered requirement suggestions and recommendations
 * @doc.layer product
 * @doc.pattern Service
 */
package com.ghatana.yappc.ai.requirements.ai.suggestions;
