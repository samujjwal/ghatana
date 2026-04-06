package com.ghatana.yappc.ai.requirements.application.requirement;

import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.yappc.ai.requirements.ai.RequirementEmbeddingService;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Detects likely duplicate requirements with semantic similarity search
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DuplicateDetector {
  static final float DUPLICATE_THRESHOLD = 0.9f;
  private static final int DEFAULT_LIMIT = 10;

  private final RequirementEmbeddingService embeddingService;

  public DuplicateDetector(RequirementEmbeddingService embeddingService) {
    this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService");
  }

  public Promise<List<RequirementService.DuplicateWarning>> analyze(
      String requirementId,
      String requirementText,
      String projectId) {
    return embeddingService
        .findSimilarRequirements(requirementText, projectId, DEFAULT_LIMIT, DUPLICATE_THRESHOLD)
        .map(results -> results.stream()
            .filter(result -> !result.getId().equals(requirementId))
            .filter(result -> result.getSimilarity() >= DUPLICATE_THRESHOLD)
            .map(this::toDuplicateWarning)
            .toList());
  }

  private RequirementService.DuplicateWarning toDuplicateWarning(VectorSearchResult result) {
    return new RequirementService.DuplicateWarning(
        result.getId(),
        result.getContent(),
        (float) result.getSimilarity());
  }
}