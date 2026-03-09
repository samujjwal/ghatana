package com.ghatana.requirements.ai.controller;

import com.ghatana.requirements.ai.dto.*;
import com.ghatana.requirements.ai.service.RequirementAIService;
import io.activej.promise.Promise;
import java.util.List;

/**
 * REST controller for AI-powered requirement operations.
 
 * @doc.type class
 * @doc.purpose Handles requirement ai controller operations
 * @doc.layer core
 * @doc.pattern Controller
*/
public class RequirementAIController {
    private final RequirementAIService service;

    public RequirementAIController(RequirementAIService service) {
        this.service = service;
    }

    public Promise<RequirementGenerationResponse> generateRequirements(RequirementGenerationRequest request) {
        return service.generateRequirements(request);
    }

    public Promise<List<VectorSearchResult>> searchSimilarRequirements(String query, int limit) {
        return service.searchSimilarRequirements(query, limit);
    }

    public Promise<List<AISuggestion>> improveRequirement(String requirement) {
        return service.improveRequirement(requirement);
    }

    public Promise<List<String>> extractAcceptanceCriteria(String requirement) {
        return service.extractAcceptanceCriteria(requirement);
    }

    public Promise<RequirementType> classifyRequirement(String requirement) {
        return service.classifyRequirement(requirement);
    }

    public Promise<RequirementQualityResult> validateQuality(String requirement) {
        return service.validateQuality(requirement);
    }

    public Promise<Boolean> healthCheck() {
        return service.healthCheck();
    }
}
