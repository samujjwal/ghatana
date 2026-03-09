package com.ghatana.requirements.ai.service;

import com.ghatana.requirements.ai.dto.*;
import io.activej.promise.Promise;
import java.util.List;

/**
 * Service interface for AI-powered requirement operations.
 
 * @doc.type interface
 * @doc.purpose Defines the contract for requirement ai service
 * @doc.layer core
 * @doc.pattern Service
*/
public interface RequirementAIService {
    Promise<RequirementGenerationResponse> generateRequirements(RequirementGenerationRequest request);
    Promise<List<VectorSearchResult>> searchSimilarRequirements(String query, int limit);
    Promise<List<AISuggestion>> improveRequirement(String requirement);
    Promise<List<String>> extractAcceptanceCriteria(String requirement);
    Promise<RequirementType> classifyRequirement(String requirement);
    Promise<com.ghatana.requirements.ai.dto.RequirementQualityResult> validateQuality(String requirement);
    Promise<Boolean> healthCheck();
}
