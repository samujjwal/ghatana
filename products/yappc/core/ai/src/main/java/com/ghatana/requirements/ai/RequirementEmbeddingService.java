package com.ghatana.requirements.ai;

import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.requirements.ai.feedback.FeedbackLearningService;
import com.ghatana.requirements.ai.suggestions.SuggestionEngine;
import com.ghatana.requirements.ai.suggestions.AISuggestion;
import com.ghatana.ai.vectorstore.VectorStore;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrator service for requirement embedding, similarity search, and suggestion generation.
 *
 * <p><b>Purpose:</b> High-level service that coordinates the entire AI requirements workflow:
 * 1. Embedding: Convert requirement text to vectors
 * 2. Storage: Store embeddings for similarity search
 * 3. Search: Find similar requirements by semantic meaning
 * 4. Suggestions: Generate AI suggestions from multiple personas
 * 5. Learning: Improve suggestions via user feedback
 *
 * <p><b>Thread Safety:</b> Thread-safe. All operations return Promise for
 * non-blocking async execution.
 *
 * <p><b>Workflow:</b>
 * <pre>
 * 1. Embed requirement text
 * 2. Store embedding in vector store
 * 3. Search for similar requirements
 * 4. Generate persona-based suggestions
 * 5. Rank suggestions by relevance
 * 6. Display to user
 * 7. Collect feedback
 * 8. Learn and improve
 * </pre>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   RequirementEmbeddingService service = new RequirementEmbeddingService(
 *       embeddingService,
 *       vectorStore,
 *       suggestionEngine,
 *       feedbackLearner
 *   );
 *
 *   // 1. Embed and store
 *   Promise<Void> embedded = service.embedAndStore(
 *       "req-123",
 *       "Add two-factor authentication",
 *       "project-456"
 *   );
 *
 *   // 2. Find similar requirements
 *   Promise<List<VectorSearchResult>> similar =
 *       service.findSimilarRequirements("req-123", projectId, limit=5);
 *
 *   // 3. Generate suggestions
 *   Promise<List<AISuggestion>> suggestions =
 *       service.generateSuggestions("req-123", "Add OAuth2", userId);
 *
 *   // 4. Learn from feedback
 *   Promise<AISuggestion> updated =
 *       service.recordFeedback(suggestion, feedback);
 * }</pre>
 *
 * @see EmbeddingService
 * @see VectorStore
 * @see SuggestionEngine
 * @see FeedbackLearningService
 * @doc.type class
 * @doc.purpose Orchestrator for embedding, search, and suggestion workflows
 * @doc.layer product
 * @doc.pattern Orchestrator/Facade Service
 * @since 1.0.0
 */
public final class RequirementEmbeddingService {
  private static final Logger logger =
      LoggerFactory.getLogger(RequirementEmbeddingService.class);

  private final EmbeddingService embeddingService;
  private final VectorStore vectorStore;
  private final SuggestionEngine suggestionEngine;
  private final FeedbackLearningService feedbackLearner;

  /**
   * Create a requirement embedding service.
   *
   * @param embeddingService for text-to-vector conversion (non-null)
   * @param vectorStore for vector storage and similarity search (non-null)
   * @param suggestionEngine for AI suggestion generation (non-null)
   * @param feedbackLearner for feedback processing and learning (non-null)
   * @throws NullPointerException if any service is null
   */
  public RequirementEmbeddingService(
      EmbeddingService embeddingService,
      VectorStore vectorStore,
      SuggestionEngine suggestionEngine,
      FeedbackLearningService feedbackLearner) {
    this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService");
    this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
    this.suggestionEngine = Objects.requireNonNull(suggestionEngine, "suggestionEngine");
    this.feedbackLearner = Objects.requireNonNull(feedbackLearner, "feedbackLearner");
  }

  /**
   * Embed requirement text and store in vector store.
   *
   * <p>Orchestrates:
   * 1. Generate embedding for requirement text
   * 2. Store vector in database for later search
   * 3. Return completion promise
   *
   * @param requirementId requirement to embed (non-null)
   * @param text requirement text to embed (non-null, non-empty)
   * @param projectId project containing requirement (non-null)
   * @return Promise resolving when embedding is stored
   */
  public Promise<Void> embedAndStore(String requirementId, String text, String projectId) {
    Objects.requireNonNull(requirementId, "requirementId cannot be null");
    Objects.requireNonNull(text, "text cannot be null");
    Objects.requireNonNull(projectId, "projectId cannot be null");

    if (text.trim().isEmpty()) {
      return Promise.ofException(new IllegalArgumentException("text cannot be empty"));
    }

    logger.info("Embedding and storing requirement: {} in project: {}", requirementId, projectId);

    return embeddingService.createEmbedding(text)
        .then(embedding -> {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("projectId", projectId);
            return vectorStore.store(requirementId, text, embedding.getVector(), metadata);
        });
  }

  /**
   * Find semantically similar requirements using vector search.
   *
   * @param queryText text to find similar requirements for (non-null)
   * @param projectId filter to project (non-null)
   * @param limit maximum results (1-1000)
   * @param minSimilarity minimum similarity threshold [-1, 1]
   * @return Promise resolving to list of similar requirements
   */
  public Promise<List<VectorSearchResult>>
      findSimilarRequirements(
          String queryText, String projectId, int limit, float minSimilarity) {
    Objects.requireNonNull(queryText, "queryText cannot be null");
    Objects.requireNonNull(projectId, "projectId cannot be null");

    logger.debug(
        "Finding similar requirements in project: {} with limit: {}",
        projectId,
        limit);

    return embeddingService.createEmbedding(queryText)
        .then(embedding -> {
            Map<String, String> filter = Collections.singletonMap("projectId", projectId);
            // Note: minSimilarity is float, but shared interface expects double.
            return vectorStore.search(embedding.getVector(), limit, (double) minSimilarity, filter);
        });
  }

  /**
   * Generate AI suggestions for a requirement.
   *
   * @param requirementId requirement to generate suggestions for (non-null)
   * @param featureDescription description to base suggestions on (non-null)
   * @param userId user requesting suggestions (may be null)
   * @return Promise resolving to list of ranked suggestions
   */
  public Promise<List<AISuggestion>> generateSuggestions(
      String requirementId, String featureDescription, String userId) {
    Objects.requireNonNull(requirementId, "requirementId cannot be null");
    Objects.requireNonNull(featureDescription, "featureDescription cannot be null");

    logger.info(
        "Generating suggestions for requirement: {} from feature: {}",
        requirementId,
        featureDescription);

    return suggestionEngine.generateSuggestions(featureDescription, requirementId, userId);
  }

  /**
   * Record user feedback on a suggestion.
   *
   * <p>Implements learning loop:
   * 1. Process feedback through learning service
   * 2. Update suggestion scores
   * 3. Calibrate persona weights
   * 4. Return updated suggestion
   *
   * @param suggestion the suggestion being reviewed (non-null)
   * @param feedback the user feedback (non-null)
   * @return Promise resolving to suggestion with updated scores
   */
  public Promise<AISuggestion> recordFeedback(
      AISuggestion suggestion,
      com.ghatana.requirements.ai.feedback.SuggestionFeedback feedback) {
    Objects.requireNonNull(suggestion, "suggestion cannot be null");
    Objects.requireNonNull(feedback, "feedback cannot be null");

    logger.info(
        "Recording feedback for suggestion: {}, type: {}",
        suggestion.requirementId(),
        feedback.type());

    return feedbackLearner.processFeedback(suggestion, feedback);
  }

  /**
   * Update an existing requirement's embedding.
   *
   * <p>Used when requirement text is modified and needs re-embedding.
   *
   * @param requirementId requirement to update (non-null)
   * @param newText updated requirement text (non-null)
   * @return Promise resolving when embedding is updated
   */
  public Promise<Void> updateEmbedding(String requirementId, String newText) {
    Objects.requireNonNull(requirementId, "requirementId cannot be null");
    Objects.requireNonNull(newText, "newText cannot be null");

    logger.info("Updating embedding for requirement: {}", requirementId);

    return vectorStore.getById(requirementId)
        .then(existing -> embeddingService.createEmbedding(newText)
            .then(embedding -> vectorStore.store(
                requirementId, 
                newText, 
                embedding.getVector(), 
                existing.getMetadata()
            )));
  }

  /**
   * Delete an embedded requirement from vector store.
   *
   * <p>Used when requirement is deleted and should no longer appear in searches.
   *
   * @param requirementId requirement to delete (non-null)
   * @return Promise resolving when deleted
   */
  public Promise<Void> deleteEmbedding(String requirementId) {
    Objects.requireNonNull(requirementId, "requirementId cannot be null");

    logger.info("Deleting embedding for requirement: {}", requirementId);

    return vectorStore.delete(requirementId);
  }

  /**
   * Get learning metrics and statistics.
   *
   * @return Promise resolving to metrics map
   */
  public Promise<java.util.Map<String, Object>> getLearningMetrics() {
    return feedbackLearner.getLearningMetrics();
  }
}
