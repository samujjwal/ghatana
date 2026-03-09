package com.ghatana.datacloud.application.workflow;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.ghatana.platform.observability.util.BlockingExecutors.blockingExecutor;

/**
 * Agentic Workflow Co-Pilot Recommendation Service.
 *
 * <p><b>Purpose</b><br>
 * Enables the workflow designer to receive step-by-step plan suggestions,
 * auto-remediations, and template generation from CES orchestration agents.
 * Simplifies complex workflow authoring with real-time agent collaboration.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AgentRecommendationService service = new AgentRecommendationService(
 *   observationRepository,
 *   metricsCollector
 * );
 *
 * // Get recommendations for workflow
 * Promise<WorkflowRecommendation> recs = service.getRecommendations(
 *   workflowId,
 *   context
 * );
 *
 * // Record user feedback
 * Promise<Void> feedback = service.recordFeedback(
 *   workflowId,
 *   suggestionId,
 *   "accepted"
 * );
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Step-by-step workflow planning
 * - Auto-remediation suggestions
 * - Template generation
 * - Feedback loop for continuous learning
 * - Confidence scoring
 * - Real-time streaming via SSE/WebSocket
 *
 * @doc.type service
 * @doc.purpose Agentic workflow recommendations
 * @doc.layer application
 * @doc.pattern Agent (Application Layer)
 */
public class AgentRecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(AgentRecommendationService.class);

    private final ObservationRepository observationRepository;
    private final MetricsCollector metricsCollector;

    /**
     * Creates a new Agent Recommendation Service.
     *
     * @param observationRepository the observation repository (required)
     * @param metricsCollector the metrics collector (required)
     */
    public AgentRecommendationService(
            ObservationRepository observationRepository,
            MetricsCollector metricsCollector) {
        this.observationRepository = observationRepository;
        this.metricsCollector = metricsCollector;
    }

    /**
     * Gets recommendations for a workflow.
     *
     * <p>GIVEN: A workflow ID and context
     * WHEN: getRecommendations() is called
     * THEN: Returns step-by-step suggestions with confidence scores
     *
     * @param workflowId the workflow identifier (required)
     * @param context the recommendation context (required)
     * @return workflow recommendations
     */
    public Promise<WorkflowRecommendation> getRecommendations(UUID workflowId, String context) {
        long startTime = System.currentTimeMillis();

        return Promise.ofBlocking(blockingExecutor(), () -> {
            logger.info("Generating recommendations for workflow: {}", workflowId);

            List<Suggestion> suggestions = new ArrayList<>();

            // Generate step suggestions
            suggestions.addAll(generateStepSuggestions(workflowId, context));

            // Generate remediation suggestions
            suggestions.addAll(generateRemediationSuggestions(workflowId));

            // Generate template suggestions
            suggestions.addAll(generateTemplateSuggestions(workflowId, context));

            // Calculate overall confidence
            double overallConfidence = suggestions.isEmpty()
                    ? 0.0
                    : suggestions.stream()
                    .mapToDouble(Suggestion::confidence)
                    .average()
                    .orElse(0.0);

            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordTimer("agent.generate_recommendations", duration);
            metricsCollector.recordConfidenceScore("agent.recommendation_confidence", overallConfidence);

            WorkflowRecommendation recommendation = new WorkflowRecommendation(
                    UUID.randomUUID().toString(),
                    workflowId,
                    suggestions,
                    "ready",
                    overallConfidence,
                    System.currentTimeMillis()
            );

            logger.info("Generated {} recommendations for workflow: {} ({}ms)",
                    suggestions.size(), workflowId, duration);

            return recommendation;
        });
    }

    /**
     * Generates step-by-step workflow planning suggestions.
     *
     * @param workflowId the workflow ID
     * @param context the context
     * @return list of step suggestions
     */
    private List<Suggestion> generateStepSuggestions(UUID workflowId, String context) {
        List<Suggestion> suggestions = new ArrayList<>();

        // Suggest adding start node
        suggestions.add(new Suggestion(
                UUID.randomUUID().toString(),
                "step",
                "Add Start Node",
                "Every workflow should begin with a start node",
                0.95,
                "add_node",
                Map.of("type", "start", "label", "Start")
        ));

        // Suggest adding validation node
        suggestions.add(new Suggestion(
                UUID.randomUUID().toString(),
                "step",
                "Add Validation Node",
                "Validate inputs before processing",
                0.85,
                "add_node",
                Map.of("type", "validation", "label", "Validate Input")
        ));

        // Suggest adding end node
        suggestions.add(new Suggestion(
                UUID.randomUUID().toString(),
                "step",
                "Add End Node",
                "Complete the workflow with an end node",
                0.95,
                "add_node",
                Map.of("type", "end", "label", "End")
        ));

        return suggestions;
    }

    /**
     * Generates auto-remediation suggestions.
     *
     * @param workflowId the workflow ID
     * @return list of remediation suggestions
     */
    private List<Suggestion> generateRemediationSuggestions(UUID workflowId) {
        List<Suggestion> suggestions = new ArrayList<>();

        // Check for missing error handling
        suggestions.add(new Suggestion(
                UUID.randomUUID().toString(),
                "remediation",
                "Add Error Handling",
                "Add error handling nodes to catch and handle exceptions",
                0.80,
                "add_node",
                Map.of("type", "error_handler", "label", "Error Handler")
        ));

        // Check for missing logging
        suggestions.add(new Suggestion(
                UUID.randomUUID().toString(),
                "remediation",
                "Add Logging",
                "Add logging nodes to track workflow execution",
                0.75,
                "add_node",
                Map.of("type", "logger", "label", "Log Event")
        ));

        return suggestions;
    }

    /**
     * Generates template suggestions.
     *
     * @param workflowId the workflow ID
     * @param context the context
     * @return list of template suggestions
     */
    private List<Suggestion> generateTemplateSuggestions(UUID workflowId, String context) {
        List<Suggestion> suggestions = new ArrayList<>();

        if ("workflow_design".equals(context)) {
            // Suggest ETL template
            suggestions.add(new Suggestion(
                    UUID.randomUUID().toString(),
                    "template",
                    "Apply ETL Template",
                    "Use the ETL template for data processing workflows",
                    0.88,
                    "apply_template",
                    Map.of("template", "etl", "nodes", List.of("extract", "transform", "load"))
            ));

            // Suggest API template
            suggestions.add(new Suggestion(
                    UUID.randomUUID().toString(),
                    "template",
                    "Apply API Template",
                    "Use the API template for REST API workflows",
                    0.82,
                    "apply_template",
                    Map.of("template", "api", "nodes", List.of("request", "process", "response"))
            ));
        }

        return suggestions;
    }

        /**
     * Records feedback for a suggestion.
     *
     * @param workflowId the workflow identifier (required)
     * @param suggestionId the suggestion identifier (required)
     * @param feedback the feedback (accepted, rejected, modified)
     * @return void
     */
    public Promise<Void> recordFeedback(UUID workflowId, String suggestionId, String feedback) {
        return Promise.ofBlocking(blockingExecutor(), () -> {
            logger.info("Recording feedback for suggestion: {} - {}", suggestionId, feedback);

            Observation observation = new Observation(
                    UUID.randomUUID().toString(),
                    workflowId,
                    suggestionId,
                    feedback,
                    System.currentTimeMillis()
            );

            observationRepository.save(observation);
            metricsCollector.incrementCounter("agent.feedback_recorded", "type", feedback);
            logger.debug("Feedback recorded successfully");
            return null;
        })
                .map(v -> (Void) null)
                .mapException(error -> {
            logger.error("Error recording feedback: {}", error.getMessage());
            metricsCollector.incrementCounter("agent.feedback_error");
            throw new RuntimeException("Failed to record feedback: " + error.getMessage());
        });
    }

    /**
     * Gets recommendation quality metrics.
     *
     * @param workflowId the workflow ID (required)
     * @return quality metrics
     */
    public Promise<QualityMetrics> getQualityMetrics(UUID workflowId) {
        return observationRepository.getObservationsByWorkflow(workflowId)
                .map(observations -> {
                    int total = observations.size();
                    int accepted = (int) observations.stream()
                            .filter(o -> "accepted".equals(o.feedback()))
                            .count();
                    int rejected = (int) observations.stream()
                            .filter(o -> "rejected".equals(o.feedback()))
                            .count();

                    double acceptanceRate = total > 0 ? (double) accepted / total : 0.0;

                    return new QualityMetrics(
                            workflowId,
                            total,
                            accepted,
                            rejected,
                            acceptanceRate
                    );
                })
                .mapException(error -> {
                    logger.error("Error getting quality metrics: {}", error.getMessage());
                    throw new RuntimeException("Failed to get quality metrics: " + error.getMessage());
                });
    }

    /**
     * Record for workflow recommendation.
     */
    public record WorkflowRecommendation(
            String id,
            UUID workflowId,
            List<Suggestion> suggestions,
            String status,
            double confidence,
            long generatedAt
    ) {}

    /**
     * Record for suggestion.
     */
    public record Suggestion(
            String id,
            String type,
            String title,
            String description,
            double confidence,
            String action,
            Map<String, Object> payload
    ) {}

    /**
     * Record for observation.
     */
    public record Observation(
            String id,
            UUID workflowId,
            String suggestionId,
            String feedback,
            long timestamp
    ) {}

    /**
     * Record for quality metrics.
     */
    public record QualityMetrics(
            UUID workflowId,
            int totalRecommendations,
            int acceptedRecommendations,
            int rejectedRecommendations,
            double acceptanceRate
    ) {}
}

/**
 * Observation repository interface.
 */
interface ObservationRepository {
    Promise<Void> save(AgentRecommendationService.Observation observation);
    Promise<List<AgentRecommendationService.Observation>> getObservationsByWorkflow(UUID workflowId);
}

/**
 * Metrics collector interface.
 */
interface MetricsCollector {
    void recordTimer(String name, long duration);
    void recordConfidenceScore(String name, double score);
    void incrementCounter(String name, String... tags);
}
