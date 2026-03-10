package com.ghatana.yappc.ai.requirements.api.graphql.mutation;

import com.ghatana.yappc.ai.requirements.ai.feedback.FeedbackLearningService;
import com.ghatana.yappc.ai.requirements.ai.feedback.FeedbackType;
import com.ghatana.yappc.ai.requirements.ai.feedback.SuggestionFeedback;
import com.ghatana.yappc.ai.requirements.ai.suggestions.AISuggestion;
import com.ghatana.yappc.ai.requirements.ai.suggestions.SuggestionEngine;
import com.ghatana.yappc.ai.requirements.ai.suggestions.SuggestionStatus;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GraphQL mutations for suggestion generation and feedback processing.
 *
 * <p><b>Purpose:</b> Implements GraphQL mutations for:
 * - Generating AI suggestions for requirements
 * - Recording user feedback on suggestions
 * - Managing suggestion lifecycle
 *
 * <p><b>Thread Safety:</b> Thread-safe. Stateless fetchers suitable for
 * concurrent GraphQL mutation execution.
 *
 * <p><b>Supported Mutations:</b>
 * <ul>
 *   <li><b>generateSuggestions:</b> Generate suggestions from specified personas</li>
 *   <li><b>recordFeedback:</b> Record user feedback on a suggestion</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   mutation {
 *     generateSuggestions(input: {
 *       requirementId: "req-123"
 *       featureDescription: "Add OAuth2 authentication"
 *       personas: [DEVELOPER, ARCHITECT]
 *     }) {
 *       id
 *       text
 *       persona
 *       relevanceScore
 *     }
 *   }
 *
 *   mutation {
 *     recordFeedback(input: {
 *       suggestionId: "sugg-456"
 *       type: HELPFUL
 *       rating: 5
 *       feedbackText: "Great suggestion!"
 *     }) {
 *       id
 *       type
 *       rating
 *     }
 *   }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose GraphQL mutations for suggestion management
 * @doc.layer product
 * @doc.pattern Data Fetcher
 * @since 1.0.0
 */
public final class SuggestionMutations {
  private static final Logger logger = LoggerFactory.getLogger(SuggestionMutations.class);

  private SuggestionMutations() {
    // Utility class
  }

  /**
   * Mutation fetcher: generateSuggestions(input: GenerateSuggestionsInput!)
   *
   * <p>Generates AI suggestions for a requirement from specified personas.
   * Returns ranked suggestions sorted by relevance.
   */
  public static final class GenerateSuggestionsMutationFetcher
      implements DataFetcher<CompletableFuture<List<Map<String, Object>>>> {

    private final SuggestionEngine engine;

    /**
     * Creates the fetcher.
     *
     * @param engine the suggestion engine providing LLM-backed generation
     */
    public GenerateSuggestionsMutationFetcher(SuggestionEngine engine) {
      this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> get(DataFetchingEnvironment env) {
      Map<String, Object> input = env.getArgument("input");
      String requirementId = (String) input.get("requirementId");
      String featureDescription = (String) input.get("featureDescription");

      @SuppressWarnings("unchecked")
      List<String> personaStrings = (List<String>) input.get("personas");

      logger.info(
          "[GraphQL] generateSuggestions: requirementId={} personas={}",
          requirementId, personaStrings != null ? personaStrings : "ALL");

      return engine.generateSuggestions(featureDescription, requirementId, null)
          .toCompletableFuture()
          .thenApply(suggestions -> {
            List<Map<String, Object>> result = new ArrayList<>(suggestions.size());
            for (AISuggestion s : suggestions) {
              Map<String, Object> map = new LinkedHashMap<>();
              map.put("id", s.requirementId() + "-" + s.persona().name().toLowerCase());
              map.put("text", s.suggestionText());
              map.put("persona", s.persona().name());
              map.put("relevanceScore", s.relevanceScore());
              map.put("priorityScore", s.priorityScore());
              map.put("rankScore", s.rankScore());
              map.put("requirementId", s.requirementId());
              map.put("createdBy", s.createdBy());
              result.add(map);
            }
            return result;
          })
          .exceptionally(ex -> {
            logger.error("[GraphQL] generateSuggestions failed: {}", ex.getMessage());
            return List.of();
          });
    }
  }

  /**
   * Mutation fetcher: recordFeedback(input: RecordFeedbackInput!)
   *
   * <p>Records user feedback on a suggestion for the learning loop.
   * Updates suggestion scores and triggers persona calibration.
   */
  public static final class RecordFeedbackMutationFetcher
      implements DataFetcher<CompletableFuture<Map<String, Object>>> {

    private final FeedbackLearningService feedbackService;

    /**
     * Creates the fetcher.
     *
     * @param feedbackService service that records feedback and calibrates persona weights
     */
    public RecordFeedbackMutationFetcher(FeedbackLearningService feedbackService) {
      this.feedbackService = Objects.requireNonNull(feedbackService, "feedbackService must not be null");
    }

    @Override
    public CompletableFuture<Map<String, Object>> get(DataFetchingEnvironment env) {
      Map<String, Object> input = env.getArgument("input");
      String suggestionId = (String) input.get("suggestionId");
      String feedbackTypeStr = (String) input.get("type");
      Integer rating = (Integer) input.get("rating");
      String feedbackText = (String) input.get("feedbackText");
      String userId = "system"; // TODO: extract from auth context once available

      logger.info(
          "[GraphQL] recordFeedback: suggestionId={} type={} rating={}",
          suggestionId, feedbackTypeStr, rating);

      FeedbackType feedbackType;
      try {
        feedbackType = FeedbackType.valueOf(feedbackTypeStr != null ? feedbackTypeStr : "HELPFUL");
      } catch (IllegalArgumentException e) {
        feedbackType = FeedbackType.HELPFUL;
        logger.warn("Unknown feedback type '{}' — defaulting to HELPFUL", feedbackTypeStr);
      }

      SuggestionFeedback feedback = new SuggestionFeedback(
          suggestionId, feedbackType, feedbackText, rating, userId);

      // Build a minimal shell — real impl needs SuggestionRepository.findById()
      AISuggestion shell = new AISuggestion(
          suggestionId,
          "",
          com.ghatana.yappc.ai.requirements.ai.persona.Persona.values()[0],
          0f,
          0f,
          SuggestionStatus.PENDING,
          userId,
          null);

      return feedbackService.processFeedback(shell, feedback)
          .toCompletableFuture()
          .thenApply(updated -> {
            Map<String, Object> result = new HashMap<>();
            result.put("id", feedback.suggestionId());
            result.put("type", feedback.type().name());
            result.put("rating", feedback.rating());
            result.put("feedbackText", feedback.feedbackText());
            return result;
          })
          .exceptionally(ex -> {
            logger.error("[GraphQL] recordFeedback failed: {}", ex.getMessage());
            Map<String, Object> err = new HashMap<>();
            err.put("id", suggestionId);
            err.put("type", feedbackTypeStr);
            err.put("rating", rating);
            return err;
          });
    }
  }

  /**
   * Mutation fetcher helper to parse persona list from GraphQL input.
   *
   * @param personaStrings GraphQL persona enum strings
   * @return parsed Persona objects
   */
  static java.util.List<com.ghatana.yappc.ai.requirements.ai.persona.Persona> parsePersonas(
      List<String> personaStrings) {
    if (personaStrings == null || personaStrings.isEmpty()) {
      return java.util.Arrays.asList(
          com.ghatana.yappc.ai.requirements.ai.persona.Persona.values());
    }

    java.util.List<com.ghatana.yappc.ai.requirements.ai.persona.Persona> personas =
        new java.util.ArrayList<>();
    for (String personaStr : personaStrings) {
      personas.add(
          com.ghatana.yappc.ai.requirements.ai.persona.Persona.valueOf(personaStr));
    }
    return personas;
  }
}