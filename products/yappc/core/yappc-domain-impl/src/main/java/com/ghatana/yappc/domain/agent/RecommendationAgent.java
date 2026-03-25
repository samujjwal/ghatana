package com.ghatana.products.yappc.domain.agent;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Recommendation Agent - provides intelligent suggestions and recommendations.
 * <p>
 * Uses collaborative filtering, content-based filtering, and ML models
 * to generate personalized recommendations.
 *
 * @doc.type class
 * @doc.purpose AI-powered recommendations
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class RecommendationAgent extends AbstractAIAgent<RecommendationInput, RecommendationOutput> {

    private static final Logger LOG = LoggerFactory.getLogger(RecommendationAgent.class);

    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "Intelligent recommendation engine for tags, assignees, and actions";
    private static final List<String> CAPABILITIES = List.of(
            "recommendations",
            "suggestions",
            "autocomplete",
            "collaborative-filtering"
    );
    private static final List<String> SUPPORTED_MODELS = List.of(
            "collaborative-filtering",
            "content-based",
            "hybrid"
    );

    private final CollaborativeFilteringService cfService;
    private final UserHistoryService userHistoryService;
    private final SimilarityService similarityService;

    public RecommendationAgent(
            @NotNull MetricsCollector metricsCollector,
            @NotNull CollaborativeFilteringService cfService,
            @NotNull UserHistoryService userHistoryService,
            @NotNull SimilarityService similarityService
    ) {
                super(
                                AgentName.RECOMMENDATION_AGENT,
                                VERSION,
                                DESCRIPTION,
                                CAPABILITIES,
                                SUPPORTED_MODELS,
                                metricsCollector
                );
        this.cfService = cfService;
        this.userHistoryService = userHistoryService;
        this.similarityService = similarityService;
    }

    @Override
        public void validateInput(@NotNull RecommendationInput input) {
        if (input.userId() == null || input.userId().isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (input.type() == null) {
            throw new IllegalArgumentException("type is required");
        }
    }

        @Override
        protected Promise<Map<String, AgentHealth.DependencyStatus>> doHealthCheck() {
                return Promise.of(Map.of(
                                "collaborativeFiltering", AgentHealth.DependencyStatus.HEALTHY,
                                "userHistory", AgentHealth.DependencyStatus.HEALTHY,
                                "similarity", AgentHealth.DependencyStatus.HEALTHY
                ));
        }

    @Override
    protected @NotNull Promise<ProcessResult<RecommendationOutput>> processRequest(
            @NotNull RecommendationInput input,
            @NotNull AIAgentContext context
    ) {
        LOG.debug("Generating {} recommendations for user {}", input.type(), input.userId());
        long startTime = System.currentTimeMillis();

        return switch (input.type()) {
            case ASSIGNEE -> recommendAssignees(input, startTime);
            case TAG -> recommendTags(input, startTime);
            case PRIORITY -> recommendPriority(input, startTime);
            case PHASE -> recommendPhase(input, startTime);
            case SIMILAR_ITEMS -> findSimilarItems(input, startTime);
            case NEXT_ACTION -> recommendNextAction(input, startTime);
            case WORKFLOW_TEMPLATE -> recommendWorkflowTemplate(input, startTime);
            case COLLABORATOR -> recommendCollaborators(input, startTime);
            case RESOURCE -> recommendResources(input, startTime);
            case TIME_ESTIMATE -> recommendTimeEstimate(input, startTime);
        };
    }

    private Promise<ProcessResult<RecommendationOutput>> recommendAssignees(
            RecommendationInput input,
            long startTime
    ) {
        return userHistoryService.getTeamMembers(input.workspaceId())
                .then(members -> cfService.rankByRelevance(members, input.itemId(), input.currentContext()))
                .map(rankedMembers -> {
                    List<RecommendationOutput.Recommendation> recommendations = new ArrayList<>();

                    int count = 0;
                    for (RankedCandidate member : rankedMembers) {
                        if (count >= input.maxResults()) break;

                        recommendations.add(new RecommendationOutput.Recommendation(
                                member.id(),
                                member.id(),
                                member.label(),
                                String.format("%.0f%% available capacity", member.metadata().get("capacity")),
                                member.confidence(),
                                member.score(),
                                new RecommendationOutput.RecommendationReason(
                                        "collaborative-filtering",
                                        "Based on similar task assignments and expertise",
                                        List.of("expertise-match", "availability", "past-performance")
                                ),
                                member.avatarUrl(),
                                member.metadata()
                        ));
                        count++;
                    }

                    return buildResult(recommendations, "collaborative-filtering", startTime, rankedMembers.size());
                });
    }

    private Promise<ProcessResult<RecommendationOutput>> recommendTags(
            RecommendationInput input,
            long startTime
    ) {
        return userHistoryService.getFrequentTags(input.userId(), input.workspaceId())
                .then(frequentTags -> similarityService.findSimilarTags(input.currentContext(), frequentTags))
                .map(rankedTags -> {
                    List<RecommendationOutput.Recommendation> recommendations = new ArrayList<>();

                    int count = 0;
                    for (RankedCandidate tag : rankedTags) {
                        if (count >= input.maxResults()) break;

                        recommendations.add(new RecommendationOutput.Recommendation(
                                tag.id(),
                                tag.id(),
                                tag.label(),
                                String.format("Used %d times", ((Number) tag.metadata().getOrDefault("usageCount", 0)).intValue()),
                                tag.confidence(),
                                tag.score(),
                                new RecommendationOutput.RecommendationReason(
                                        "content-based",
                                        "Matches context and frequently used",
                                        List.of("context-match", "frequency")
                                ),
                                null,
                                null
                        ));
                        count++;
                    }

                    return buildResult(recommendations, "hybrid", startTime, rankedTags.size());
                });
    }

    private Promise<ProcessResult<RecommendationOutput>> recommendPriority(
            RecommendationInput input,
            long startTime
    ) {
        // Priority recommendation based on context analysis
        return similarityService.analyzePriorityContext(input.currentContext(), input.itemId())
                .map(analysis -> {
                    List<RecommendationOutput.Recommendation> recommendations = new ArrayList<>();

                    // Create priority recommendations based on analysis
                    String[] priorities = {"critical", "high", "medium", "low"};
                    double[] scores = analysis.priorityScores();

                    for (int i = 0; i < priorities.length && i < input.maxResults(); i++) {
                        recommendations.add(new RecommendationOutput.Recommendation(
                                priorities[i],
                                priorities[i],
                                priorities[i].substring(0, 1).toUpperCase() + priorities[i].substring(1),
                                analysis.reasons().get(priorities[i]),
                                scores[i],
                                scores[i],
                                new RecommendationOutput.RecommendationReason(
                                        "ml-classification",
                                        "Based on content analysis and historical patterns",
                                        analysis.factors()
                                ),
                                null,
                                null
                        ));
                    }

                    // Sort by score
                    recommendations.sort((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()));

                    return buildResult(recommendations, "ml-classification", startTime, 4);
                });
    }

    private Promise<ProcessResult<RecommendationOutput>> recommendPhase(
            RecommendationInput input,
            long startTime
    ) {
        return cfService.predictPhase(input.itemId(), input.currentContext())
                .map(phasePrediction -> {
                    List<RecommendationOutput.Recommendation> recommendations = new ArrayList<>();

                    for (var entry : phasePrediction.entrySet()) {
                        if (recommendations.size() >= input.maxResults()) break;

                        recommendations.add(new RecommendationOutput.Recommendation(
                                entry.getKey(),
                                entry.getKey(),
                                formatPhaseName(entry.getKey()),
                                "Suggested based on item characteristics",
                                entry.getValue(),
                                entry.getValue(),
                                new RecommendationOutput.RecommendationReason(
                                        "content-based",
                                        "Matches phase criteria",
                                        List.of("item-type", "current-status", "workflow-stage")
                                ),
                                null,
                                null
                        ));
                    }

                    return buildResult(recommendations, "content-based", startTime, phasePrediction.size());
                });
    }

    private Promise<ProcessResult<RecommendationOutput>> findSimilarItems(
            RecommendationInput input,
            long startTime
    ) {
        return similarityService.findSimilarItems(input.itemId(), input.maxResults())
                .map(similarItems -> {
                    List<RecommendationOutput.Recommendation> recommendations = new ArrayList<>();

                    for (SimilarItem item : similarItems) {
                        recommendations.add(new RecommendationOutput.Recommendation(
                                item.itemId(),
                                item.itemId(),
                                item.title(),
                                String.format("%.0f%% similar", item.similarity() * 100),
                                item.similarity(),
                                item.similarity(),
                                new RecommendationOutput.RecommendationReason(
                                        "semantic-similarity",
                                        "Found using vector embeddings",
                                        item.matchingFeatures()
                                ),
                                null,
                                Map.of("phase", item.phase(), "status", item.status())
                        ));
                    }

                    return buildResult(recommendations, "vector-similarity", startTime, similarItems.size());
                });
    }

    private Promise<ProcessResult<RecommendationOutput>> recommendNextAction(
            RecommendationInput input,
            long startTime
    ) {
        return userHistoryService.getRecentActions(input.userId())
                .then(recentActions -> cfService.predictNextAction(recentActions, input.currentContext()))
                .map(predictedActions -> {
                    List<RecommendationOutput.Recommendation> recommendations = new ArrayList<>();

                    for (ActionPrediction action : predictedActions) {
                        if (recommendations.size() >= input.maxResults()) break;

                        recommendations.add(new RecommendationOutput.Recommendation(
                                action.actionId(),
                                action.actionId(),
                                action.label(),
                                action.description(),
                                action.probability(),
                                action.probability(),
                                new RecommendationOutput.RecommendationReason(
                                        "sequence-prediction",
                                        "Based on your recent activity patterns",
                                        List.of("recent-actions", "workflow-stage", "time-of-day")
                                ),
                                null,
                                Map.of("category", action.category())
                        ));
                    }

                    return buildResult(recommendations, "sequence-prediction", startTime, predictedActions.size());
                });
    }

    private Promise<ProcessResult<RecommendationOutput>> recommendWorkflowTemplate(
            RecommendationInput input,
            long startTime
    ) {
        return cfService.matchWorkflowTemplates(input.currentContext(), input.filters())
                .map(templates -> {
                    List<RecommendationOutput.Recommendation> recommendations = new ArrayList<>();

                    for (WorkflowTemplateMatch template : templates) {
                        if (recommendations.size() >= input.maxResults()) break;

                        recommendations.add(new RecommendationOutput.Recommendation(
                                template.templateId(),
                                template.templateId(),
                                template.name(),
                                template.description(),
                                template.matchScore(),
                                template.matchScore(),
                                new RecommendationOutput.RecommendationReason(
                                        "template-matching",
                                        "Matches your requirements",
                                        template.matchingCriteria()
                                ),
                                null,
                                Map.of("steps", template.stepCount(), "avgDuration", template.avgDuration())
                        ));
                    }

                    return buildResult(recommendations, "template-matching", startTime, templates.size());
                });
    }

    private Promise<ProcessResult<RecommendationOutput>> recommendCollaborators(
            RecommendationInput input,
            long startTime
    ) {
        return cfService.findCollaborators(input.userId(), input.itemId())
                .map(collaborators -> {
                    List<RecommendationOutput.Recommendation> recommendations = new ArrayList<>();

                    for (RankedCandidate collaborator : collaborators) {
                        if (recommendations.size() >= input.maxResults()) break;

                        recommendations.add(new RecommendationOutput.Recommendation(
                                collaborator.id(),
                                collaborator.id(),
                                collaborator.label(),
                                "Works on related items",
                                collaborator.confidence(),
                                collaborator.score(),
                                new RecommendationOutput.RecommendationReason(
                                        "graph-analysis",
                                        "Connected through similar work",
                                        List.of("shared-projects", "expertise-overlap", "collaboration-history")
                                ),
                                collaborator.avatarUrl(),
                                collaborator.metadata()
                        ));
                    }

                    return buildResult(recommendations, "graph-analysis", startTime, collaborators.size());
                });
    }

    private Promise<ProcessResult<RecommendationOutput>> recommendResources(
            RecommendationInput input,
            long startTime
    ) {
        return similarityService.findRelevantResources(input.currentContext(), input.maxResults())
                .map(resources -> {
                    List<RecommendationOutput.Recommendation> recommendations = new ArrayList<>();

                    for (ResourceMatch resource : resources) {
                        recommendations.add(new RecommendationOutput.Recommendation(
                                resource.resourceId(),
                                resource.url(),
                                resource.title(),
                                resource.snippet(),
                                resource.relevance(),
                                resource.relevance(),
                                new RecommendationOutput.RecommendationReason(
                                        "semantic-search",
                                        "Related documentation and resources",
                                        resource.matchingTerms()
                                ),
                                null,
                                Map.of("type", resource.type(), "source", resource.source())
                        ));
                    }

                    return buildResult(recommendations, "semantic-search", startTime, resources.size());
                });
    }

    private Promise<ProcessResult<RecommendationOutput>> recommendTimeEstimate(
            RecommendationInput input,
            long startTime
    ) {
        return cfService.estimateTime(input.itemId(), input.currentContext())
                .map(estimates -> {
                    List<RecommendationOutput.Recommendation> recommendations = new ArrayList<>();

                    for (TimeEstimate estimate : estimates) {
                        recommendations.add(new RecommendationOutput.Recommendation(
                                estimate.label(),
                                String.valueOf(estimate.hours()),
                                estimate.label(),
                                String.format("Based on %d similar items", estimate.sampleSize()),
                                estimate.confidence(),
                                estimate.confidence(),
                                new RecommendationOutput.RecommendationReason(
                                        "historical-analysis",
                                        "Estimated from similar completed items",
                                        List.of("complexity", "team-velocity", "historical-data")
                                ),
                                null,
                                Map.of("min", estimate.minHours(), "max", estimate.maxHours())
                        ));
                    }

                    return buildResult(recommendations, "historical-analysis", startTime, estimates.size());
                });
    }

    private ProcessResult<RecommendationOutput> buildResult(
            List<RecommendationOutput.Recommendation> recommendations,
            String algorithm,
            long startTime,
            int candidatesEvaluated
    ) {
        RecommendationOutput output = RecommendationOutput.builder()
                .recommendations(recommendations)
                .metadata(new RecommendationOutput.RecommendationMetadata(
                        algorithm,
                        System.currentTimeMillis() - startTime,
                        candidatesEvaluated,
                        "1.0.0"
                ))
                .build();

        return ProcessResult.of(output);
    }

    private String formatPhaseName(String phaseId) {
        return phaseId.substring(0, 1).toUpperCase() + phaseId.substring(1).replace("-", " ");
    }

    // Service interfaces and data types

    public interface CollaborativeFilteringService {
        Promise<List<RankedCandidate>> rankByRelevance(List<TeamMember> members, String itemId, String context);
        Promise<Map<String, Double>> predictPhase(String itemId, String context);
        Promise<List<ActionPrediction>> predictNextAction(List<String> recentActions, String context);
        Promise<List<WorkflowTemplateMatch>> matchWorkflowTemplates(String context, Map<String, Object> filters);
        Promise<List<RankedCandidate>> findCollaborators(String userId, String itemId);
        Promise<List<TimeEstimate>> estimateTime(String itemId, String context);
    }

    public interface UserHistoryService {
        Promise<List<TeamMember>> getTeamMembers(String workspaceId);
        Promise<List<String>> getFrequentTags(String userId, String workspaceId);
        Promise<List<String>> getRecentActions(String userId);
    }

    public interface SimilarityService {
        Promise<List<RankedCandidate>> findSimilarTags(String context, List<String> candidates);
        Promise<PriorityAnalysis> analyzePriorityContext(String context, String itemId);
        Promise<List<SimilarItem>> findSimilarItems(String itemId, int limit);
        Promise<List<ResourceMatch>> findRelevantResources(String context, int limit);
    }

    public record RankedCandidate(
            String id,
            String label,
            double score,
            double confidence,
            String avatarUrl,
            Map<String, Object> metadata
    ) {}

    public record TeamMember(String id, String name, String email, String avatarUrl) {}

    public record PriorityAnalysis(
            double[] priorityScores,
            Map<String, String> reasons,
            List<String> factors
    ) {}

    public record SimilarItem(
            String itemId,
            String title,
            String phase,
            String status,
            double similarity,
            List<String> matchingFeatures
    ) {}

    public record ActionPrediction(
            String actionId,
            String label,
            String description,
            String category,
            double probability
    ) {}

    public record WorkflowTemplateMatch(
            String templateId,
            String name,
            String description,
            double matchScore,
            List<String> matchingCriteria,
            int stepCount,
            String avgDuration
    ) {}

    public record ResourceMatch(
            String resourceId,
            String url,
            String title,
            String snippet,
            String type,
            String source,
            double relevance,
            List<String> matchingTerms
    ) {}

    public record TimeEstimate(
            String label,
            double hours,
            double minHours,
            double maxHours,
            double confidence,
            int sampleSize
    ) {}
}
