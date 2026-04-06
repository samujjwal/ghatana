package com.ghatana.yappc.ai.requirements.application.requirement;

import com.ghatana.yappc.ai.requirements.ai.RequirementEmbeddingService;
import com.ghatana.yappc.ai.requirements.ai.suggestions.AISuggestion;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Produces AI-backed requirement enrichment suggestions during write flows
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AIEnricher {
  private final RequirementEmbeddingService embeddingService;

  public AIEnricher(RequirementEmbeddingService embeddingService) {
    this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService");
  }

  public Promise<List<AISuggestion>> enrich(String requirementId, String requirementText, String userId) {
    return embeddingService.generateSuggestions(requirementId, requirementText, userId);
  }
}