package com.ghatana.requirements.ai;

import com.ghatana.requirements.ai.suggestions.AISuggestion;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * HTTP REST API controller for AI requirement generation and analysis.
 *
 * <p>
 * <b>Purpose:</b> Exposes RequirementAIService via HTTP REST endpoints for
 * requirement generation, semantic search, improvement suggestions, acceptance
 * criteria extraction, type classification, and quality validation.
 *
 * <p>
 * <b>Endpoints:</b>
 * <ul>
 * <li>POST /api/requirements/generate - Generate requirements</li>
 * <li>POST /api/requirements/search - Semantic search for similar
 * requirements</li>
 * <li>POST /api/requirements/improve - Get improvement suggestions</li>
 * <li>POST /api/requirements/extract - Extract acceptance criteria</li>
 * <li>POST /api/requirements/classify - Classify requirement type</li>
 * <li>POST /api/requirements/validate - Validate requirement quality</li>
 * <li>GET /api/requirements/health - Health check</li>
 * </ul>
 *
 * <p>
 * <b>Request/Response Format:</b> JSON
 *
 * <p>
 * <b>Error Handling:</b>
 * <ul>
 * <li>400 Bad Request - Invalid input</li>
 * <li>500 Internal Server Error - Service failure</li>
 * <li>503 Service Unavailable - Health check failed</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety:</b> Safe for concurrent use. All Promise operations are
 * non-blocking and execute on ActiveJ Eventloop.
 *
 * @see RequirementAIService
 * @see RequirementGenerationRequest
 * @see RequirementGenerationResponse
 * @doc.type class
 * @doc.purpose HTTP REST API controller for AI requirement service
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 * @since 1.0.0
 */
public class RequirementAIController {

    private static final Logger LOG = LoggerFactory.getLogger(RequirementAIController.class);
    private final RequirementAIService aiService;

    /**
     * Create a new controller with the given service.
     *
     * @param aiService AI requirement service (non-null)
     */
    public RequirementAIController(RequirementAIService aiService) {
        this.aiService = aiService;
    }

    /**
     * Generate requirements for a feature.
     *
     * <p>
     * <b>Usage:</b>
     * <pre>{@code
     * RequirementGenerationRequest request = new RequirementGenerationRequest();
     * request.featureDescription = "User authentication with OAuth2";
     * request.count = 5;
     * request.type = "FUNCTIONAL";
     *
     * Promise<RequirementGenerationResponse> response =
     *     controller.generateRequirements(request);
     * }</pre>
     *
     * @param request generation request parameters
     * @return Promise resolving to generated requirements
     */
    public Promise<RequirementGenerationResponse> generateRequirements(
            RequirementGenerationRequest request) {
        LOG.info("Generating requirements for feature");
        return aiService.generateRequirements(request)
                .whenException(ex -> LOG.error("Failed to generate requirements", ex));
    }

    /**
     * Search for similar requirements using semantic similarity.
     *
     * <p>
     * <b>Usage:</b>
     * <pre>{@code
     * Promise<List<VectorSearchResult>> results =
     *     controller.searchSimilarRequirements(
     *         "OAuth authentication",
     *         "project-123",
     *         10,
     *         0.7f);
     * }</pre>
     *
     * @param queryText search query text
     * @param projectId project identifier for filtering
     * @param limit maximum results to return
     * @param minSimilarity minimum similarity threshold
     * @return Promise resolving to list of search results
     */
    public Promise<List<VectorSearchResult>> searchSimilarRequirements(
            String queryText, String projectId, Integer limit, Float minSimilarity) {
        LOG.info("Searching for requirements similar to: {}", queryText);
        return aiService.findSimilarRequirements(queryText, projectId, limit, minSimilarity)
                .whenException(ex -> LOG.error("Failed to search requirements", ex));
    }

    /**
     * Get improvement suggestions for a requirement.
     *
     * <p>
     * <b>Usage:</b>
     * <pre>{@code
     * Promise<List<AISuggestion>> suggestions =
     *     controller.improveRequirement("User can log in");
     * }</pre>
     *
     * @param requirement requirement text to improve
     * @return Promise resolving to list of improvement suggestions
     */
    public Promise<List<AISuggestion>> improveRequirement(String requirement) {
        LOG.info("Getting improvement suggestions for requirement");
        return aiService.suggestImprovements(requirement)
                .whenException(ex -> LOG.error("Failed to get improvements", ex));
    }

    /**
     * Extract acceptance criteria from a requirement.
     *
     * <p>
     * <b>Usage:</b>
     * <pre>{@code
     * Promise<List<String>> criteria =
     *     controller.extractAcceptanceCriteria(
     *         "User can authenticate with OAuth2 provider");
     * }</pre>
     *
     * @param requirement requirement text to extract criteria from
     * @return Promise resolving to list of acceptance criteria
     */
    public Promise<List<String>> extractAcceptanceCriteria(String requirement) {
        LOG.info("Extracting acceptance criteria from requirement");
        return aiService.extractAcceptanceCriteria(requirement)
                .whenException(ex -> LOG.error("Failed to extract criteria", ex));
    }

    /**
     * Classify requirement type.
     *
     * <p>
     * <b>Usage:</b>
     * <pre>{@code
     * Promise<String> classification =
     *     controller.classifyRequirement(
     *         "System must support 10,000 concurrent users");
     * }</pre>
     *
     * @param requirement requirement text to classify
     * @return Promise resolving to classification result
     */
    public Promise<RequirementType> classifyRequirement(String requirement) {
        LOG.info("Classifying requirement");
        return aiService.classifyRequirement(requirement)
                .whenException(ex -> LOG.error("Failed to classify requirement", ex));
    }

    /**
     * Validate requirement quality.
     *
     * <p>
     * <b>Usage:</b>
     * <pre>{@code
     * Promise<RequirementQualityResult> result =
     *     controller.validateQuality(
     *         "User can authenticate with email and password");
     * }</pre>
     *
     * @param requirement requirement text to validate
     * @return Promise resolving to quality validation result
     */
    public Promise<?> validateQuality(String requirement) {
        LOG.info("Validating requirement quality");
        return aiService.validateQuality(requirement)
                .whenException(ex -> LOG.error("Failed to validate quality", ex));
    }

    /**
     * Perform health check on the requirement service.
     *
     * <p>
     * <b>Usage:</b>
     * <pre>{@code
     * Promise<Boolean> isHealthy = controller.healthCheck();
     * }</pre>
     *
     * @return Promise resolving to true if service is healthy, false otherwise
     */
    public Promise<Boolean> healthCheck() {
        LOG.info("Performing health check");
        return aiService.healthCheck()
                .whenException(ex -> LOG.error("Health check failed", ex));
    }

    // ========================================================================
    // Helper Methods  
    // ========================================================================
    /**
     * Reserved for future HTTP response building.
     */
}
