package com.ghatana.products.yappc.domain.agent;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Workflow Router Agent - intelligently routes and orchestrates workflows.
 * <p>
 * Uses ML models to determine optimal workflow paths, predict bottlenecks,
 * and suggest workflow optimizations.
 *
 * @doc.type class
 * @doc.purpose AI-powered workflow routing
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class WorkflowRouterAgent extends AbstractAIAgent<WorkflowRouterAgent.RouterInput, WorkflowRouterAgent.RouterOutput> {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowRouterAgent.class);

    private static final String VERSION = "1.0.0";
    private static final String DESCRIPTION = "Intelligent workflow routing, path optimization, and bottleneck prediction";
    private static final List<String> CAPABILITIES = List.of(
            "workflow-routing",
            "path-optimization",
            "bottleneck-prediction",
            "resource-allocation"
    );
    private static final List<String> SUPPORTED_MODELS = List.of(
            "decision-tree",
            "reinforcement-learning"
    );

    private final WorkflowAnalyzer workflowAnalyzer;
    private final ResourceOptimizer resourceOptimizer;
    private final HistoricalDataService historicalDataService;

    public WorkflowRouterAgent(
            @NotNull MetricsCollector metricsCollector,
            @NotNull WorkflowAnalyzer workflowAnalyzer,
            @NotNull ResourceOptimizer resourceOptimizer,
            @NotNull HistoricalDataService historicalDataService
    ) {
                super(
                                AgentName.WORKFLOW_ROUTER_AGENT,
                                VERSION,
                                DESCRIPTION,
                                CAPABILITIES,
                                SUPPORTED_MODELS,
                                metricsCollector
                );
        this.workflowAnalyzer = workflowAnalyzer;
        this.resourceOptimizer = resourceOptimizer;
        this.historicalDataService = historicalDataService;
    }

        @Override
        public void validateInput(@NotNull RouterInput input) {
        if (input.routingType() == null) {
            throw new IllegalArgumentException("routingType is required");
        }
        if (input.workflowId() == null && input.routingType() != RoutingType.SUGGEST_WORKFLOW) {
            throw new IllegalArgumentException("workflowId is required for this routing type");
        }
    }

        @Override
        protected Promise<Map<String, AgentHealth.DependencyStatus>> doHealthCheck() {
                return Promise.of(Map.of(
                                "workflowAnalyzer", AgentHealth.DependencyStatus.HEALTHY,
                                "resourceOptimizer", AgentHealth.DependencyStatus.HEALTHY,
                                "historicalData", AgentHealth.DependencyStatus.HEALTHY
                ));
        }

    @Override
    protected @NotNull Promise<ProcessResult<RouterOutput>> processRequest(
            @NotNull RouterInput input,
            @NotNull AIAgentContext context
    ) {
        LOG.info("Routing workflow: {} with type {}", input.workflowId(), input.routingType());
        long startTime = System.currentTimeMillis();

        return switch (input.routingType()) {
            case NEXT_STEP -> determineNextStep(input, startTime);
            case OPTIMAL_PATH -> findOptimalPath(input, startTime);
            case BOTTLENECK_PREDICTION -> predictBottlenecks(input, startTime);
            case RESOURCE_ALLOCATION -> optimizeResourceAllocation(input, startTime);
            case PARALLEL_OPPORTUNITIES -> findParallelOpportunities(input, startTime);
            case SUGGEST_WORKFLOW -> suggestWorkflowType(input, startTime);
            case STEP_VALIDATION -> validateStepTransition(input, startTime);
        };
    }

    private Promise<ProcessResult<RouterOutput>> determineNextStep(
            RouterInput input,
            long startTime
    ) {
        return workflowAnalyzer.analyzeCurrentState(input.workflowId())
                .then(state -> workflowAnalyzer.predictNextSteps(state, input.currentStep()))
                .map(predictions -> {
                    List<RoutingDecision> decisions = new ArrayList<>();

                    for (StepPrediction prediction : predictions) {
                        decisions.add(new RoutingDecision(
                                prediction.stepId(),
                                prediction.stepName(),
                                prediction.confidence(),
                                prediction.reason(),
                                Map.of(
                                        "estimatedDuration", prediction.estimatedDuration(),
                                        "prerequisites", prediction.prerequisites(),
                                        "riskLevel", prediction.riskLevel()
                                )
                        ));
                    }

                    return buildResult(decisions, RoutingType.NEXT_STEP, startTime, null, null);
                });
    }

    private Promise<ProcessResult<RouterOutput>> findOptimalPath(
            RouterInput input,
            long startTime
    ) {
        return workflowAnalyzer.analyzeCurrentState(input.workflowId())
                .then(state -> historicalDataService.getSimilarWorkflows(state))
                .then(similarWorkflows -> workflowAnalyzer.computeOptimalPath(
                        input.workflowId(),
                        similarWorkflows,
                        input.constraints()
                ))
                .map(optimalPath -> {
                    List<RoutingDecision> decisions = new ArrayList<>();

                    for (PathStep step : optimalPath.steps()) {
                        decisions.add(new RoutingDecision(
                                step.stepId(),
                                step.stepName(),
                                step.probability(),
                                step.rationale(),
                                Map.of(
                                        "order", step.order(),
                                        "parallel", step.canRunParallel(),
                                        "dependencies", step.dependencies()
                                )
                        ));
                    }

                    return buildResult(
                            decisions,
                            RoutingType.OPTIMAL_PATH,
                            startTime,
                            optimalPath.estimatedDuration(),
                            optimalPath.successProbability()
                    );
                });
    }

    private Promise<ProcessResult<RouterOutput>> predictBottlenecks(
            RouterInput input,
            long startTime
    ) {
        return workflowAnalyzer.analyzeCurrentState(input.workflowId())
                .then(state -> workflowAnalyzer.predictBottlenecks(state))
                .map(bottlenecks -> {
                    List<RoutingDecision> decisions = new ArrayList<>();
                    List<BottleneckWarning> warnings = new ArrayList<>();

                    for (BottleneckPrediction bottleneck : bottlenecks) {
                        decisions.add(new RoutingDecision(
                                bottleneck.stepId(),
                                bottleneck.stepName(),
                                bottleneck.probability(),
                                bottleneck.reason(),
                                Map.of(
                                        "type", bottleneck.type(),
                                        "impact", bottleneck.impact(),
                                        "mitigation", bottleneck.mitigation()
                                )
                        ));

                        if (bottleneck.probability() > 0.5) {
                            warnings.add(new BottleneckWarning(
                                    bottleneck.stepId(),
                                    bottleneck.type().toString(),
                                    bottleneck.impact(),
                                    bottleneck.mitigation()
                            ));
                        }
                    }

                    RouterOutput output = RouterOutput.builder()
                            .decisions(decisions)
                            .bottleneckWarnings(warnings)
                            .metadata(new RoutingMetadata(
                                    RoutingType.BOTTLENECK_PREDICTION,
                                    System.currentTimeMillis() - startTime,
                                    "ml-prediction"
                            ))
                            .build();

                    return ProcessResult.of(output);
                });
    }

    private Promise<ProcessResult<RouterOutput>> optimizeResourceAllocation(
            RouterInput input,
            long startTime
    ) {
        return workflowAnalyzer.analyzeCurrentState(input.workflowId())
                .then(state -> resourceOptimizer.optimizeAllocation(state, input.constraints()))
                .map(allocation -> {
                    List<RoutingDecision> decisions = new ArrayList<>();
                    List<ResourceRecommendation> recommendations = new ArrayList<>();

                    for (ResourceAllocation alloc : allocation.allocations()) {
                        decisions.add(new RoutingDecision(
                                alloc.stepId(),
                                alloc.stepName(),
                                alloc.confidence(),
                                alloc.rationale(),
                                Map.of(
                                        "assignee", alloc.assigneeId(),
                                        "capacity", alloc.requiredCapacity(),
                                        "skills", alloc.requiredSkills()
                                )
                        ));

                        recommendations.add(new ResourceRecommendation(
                                alloc.stepId(),
                                alloc.assigneeId(),
                                alloc.assigneeName(),
                                alloc.requiredCapacity(),
                                alloc.confidence()
                        ));
                    }

                    RouterOutput output = RouterOutput.builder()
                            .decisions(decisions)
                            .resourceRecommendations(recommendations)
                            .estimatedDuration(allocation.totalDuration())
                            .metadata(new RoutingMetadata(
                                    RoutingType.RESOURCE_ALLOCATION,
                                    System.currentTimeMillis() - startTime,
                                    "optimization"
                            ))
                            .build();

                    return ProcessResult.of(output);
                });
    }

    private Promise<ProcessResult<RouterOutput>> findParallelOpportunities(
            RouterInput input,
            long startTime
    ) {
        return workflowAnalyzer.analyzeCurrentState(input.workflowId())
                .then(state -> workflowAnalyzer.findParallelOpportunities(state))
                .map(opportunities -> {
                    List<RoutingDecision> decisions = new ArrayList<>();
                    List<ParallelGroup> parallelGroups = new ArrayList<>();

                    for (ParallelOpportunity opp : opportunities) {
                        decisions.add(new RoutingDecision(
                                opp.groupId(),
                                "Parallel Group: " + String.join(", ", opp.stepNames()),
                                opp.confidence(),
                                opp.rationale(),
                                Map.of(
                                        "steps", opp.stepIds(),
                                        "timeSaved", opp.estimatedTimeSaved()
                                )
                        ));

                        parallelGroups.add(new ParallelGroup(
                                opp.groupId(),
                                opp.stepIds(),
                                opp.estimatedTimeSaved()
                        ));
                    }

                    RouterOutput output = RouterOutput.builder()
                            .decisions(decisions)
                            .parallelGroups(parallelGroups)
                            .metadata(new RoutingMetadata(
                                    RoutingType.PARALLEL_OPPORTUNITIES,
                                    System.currentTimeMillis() - startTime,
                                    "dependency-analysis"
                            ))
                            .build();

                    return ProcessResult.of(output);
                });
    }

    private Promise<ProcessResult<RouterOutput>> suggestWorkflowType(
            RouterInput input,
            long startTime
    ) {
        return workflowAnalyzer.analyzeIntent(input.intent(), input.context())
                .map(suggestions -> {
                    List<RoutingDecision> decisions = new ArrayList<>();

                    for (WorkflowTypeSuggestion suggestion : suggestions) {
                        decisions.add(new RoutingDecision(
                                suggestion.workflowType(),
                                suggestion.displayName(),
                                suggestion.confidence(),
                                suggestion.reason(),
                                Map.of(
                                        "templateId", suggestion.suggestedTemplateId(),
                                        "estimatedSteps", suggestion.estimatedSteps(),
                                        "avgDuration", suggestion.avgDuration()
                                )
                        ));
                    }

                    return buildResult(decisions, RoutingType.SUGGEST_WORKFLOW, startTime, null, null);
                });
    }

    private Promise<ProcessResult<RouterOutput>> validateStepTransition(
            RouterInput input,
            long startTime
    ) {
        return workflowAnalyzer.validateTransition(
                input.workflowId(),
                input.currentStep(),
                input.targetStep()
        ).map(validation -> {
            List<RoutingDecision> decisions = new ArrayList<>();

            decisions.add(new RoutingDecision(
                    input.targetStep(),
                    validation.isValid() ? "Valid Transition" : "Invalid Transition",
                    validation.confidence(),
                    validation.reason(),
                    Map.of(
                            "valid", validation.isValid(),
                            "missingPrerequisites", validation.missingPrerequisites(),
                            "warnings", validation.warnings()
                    )
            ));

            return buildResult(decisions, RoutingType.STEP_VALIDATION, startTime, null, null);
        });
    }

    private ProcessResult<RouterOutput> buildResult(
            List<RoutingDecision> decisions,
            RoutingType type,
            long startTime,
            String estimatedDuration,
            Double successProbability
    ) {
        RouterOutput output = RouterOutput.builder()
                .decisions(decisions)
                .estimatedDuration(estimatedDuration)
                .successProbability(successProbability)
                .metadata(new RoutingMetadata(
                        type,
                        System.currentTimeMillis() - startTime,
                        "ml-routing"
                ))
                .build();

        return ProcessResult.of(output);
    }

    // Input/Output types

    public record RouterInput(
            @NotNull RoutingType routingType,
            @Nullable String workflowId,
            @Nullable String currentStep,
            @Nullable String targetStep,
            @Nullable String intent,
            @Nullable Map<String, Object> context,
            @Nullable Map<String, Object> constraints
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private RoutingType routingType;
            private String workflowId;
            private String currentStep;
            private String targetStep;
            private String intent;
            private Map<String, Object> context;
            private Map<String, Object> constraints;

            public Builder routingType(RoutingType routingType) {
                this.routingType = routingType;
                return this;
            }

            public Builder workflowId(String workflowId) {
                this.workflowId = workflowId;
                return this;
            }

            public Builder currentStep(String currentStep) {
                this.currentStep = currentStep;
                return this;
            }

            public Builder targetStep(String targetStep) {
                this.targetStep = targetStep;
                return this;
            }

            public Builder intent(String intent) {
                this.intent = intent;
                return this;
            }

            public Builder context(Map<String, Object> context) {
                this.context = context;
                return this;
            }

            public Builder constraints(Map<String, Object> constraints) {
                this.constraints = constraints;
                return this;
            }

            public RouterInput build() {
                return new RouterInput(
                        routingType, workflowId, currentStep, targetStep,
                        intent, context, constraints
                );
            }
        }
    }

    public record RouterOutput(
            @NotNull List<RoutingDecision> decisions,
            @Nullable String estimatedDuration,
            @Nullable Double successProbability,
            @Nullable List<BottleneckWarning> bottleneckWarnings,
            @Nullable List<ResourceRecommendation> resourceRecommendations,
            @Nullable List<ParallelGroup> parallelGroups,
            @NotNull RoutingMetadata metadata
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private List<RoutingDecision> decisions = List.of();
            private String estimatedDuration;
            private Double successProbability;
            private List<BottleneckWarning> bottleneckWarnings;
            private List<ResourceRecommendation> resourceRecommendations;
            private List<ParallelGroup> parallelGroups;
            private RoutingMetadata metadata;

            public Builder decisions(List<RoutingDecision> decisions) {
                this.decisions = decisions;
                return this;
            }

            public Builder estimatedDuration(String estimatedDuration) {
                this.estimatedDuration = estimatedDuration;
                return this;
            }

            public Builder successProbability(Double successProbability) {
                this.successProbability = successProbability;
                return this;
            }

            public Builder bottleneckWarnings(List<BottleneckWarning> warnings) {
                this.bottleneckWarnings = warnings;
                return this;
            }

            public Builder resourceRecommendations(List<ResourceRecommendation> recommendations) {
                this.resourceRecommendations = recommendations;
                return this;
            }

            public Builder parallelGroups(List<ParallelGroup> groups) {
                this.parallelGroups = groups;
                return this;
            }

            public Builder metadata(RoutingMetadata metadata) {
                this.metadata = metadata;
                return this;
            }

            public RouterOutput build() {
                if (metadata == null) {
                    throw new IllegalStateException("metadata is required");
                }
                return new RouterOutput(
                        decisions, estimatedDuration, successProbability,
                        bottleneckWarnings, resourceRecommendations, parallelGroups, metadata
                );
            }
        }
    }

    public enum RoutingType {
        NEXT_STEP,
        OPTIMAL_PATH,
        BOTTLENECK_PREDICTION,
        RESOURCE_ALLOCATION,
        PARALLEL_OPPORTUNITIES,
        SUGGEST_WORKFLOW,
        STEP_VALIDATION
    }

    public record RoutingDecision(
            @NotNull String id,
            @NotNull String name,
            double confidence,
            @NotNull String reason,
            @Nullable Map<String, Object> metadata
    ) {}

    public record BottleneckWarning(
            @NotNull String stepId,
            @NotNull String type,
            @NotNull String impact,
            @NotNull String mitigation
    ) {}

    public record ResourceRecommendation(
            @NotNull String stepId,
            @NotNull String assigneeId,
            @NotNull String assigneeName,
            double requiredCapacity,
            double confidence
    ) {}

    public record ParallelGroup(
            @NotNull String groupId,
            @NotNull List<String> stepIds,
            @NotNull String estimatedTimeSaved
    ) {}

    public record RoutingMetadata(
            @NotNull RoutingType routingType,
            long processingTimeMs,
            @NotNull String algorithm
    ) {}

    // Service interfaces

    public interface WorkflowAnalyzer {
        Promise<WorkflowState> analyzeCurrentState(String workflowId);
        Promise<List<StepPrediction>> predictNextSteps(WorkflowState state, String currentStep);
        Promise<OptimalPath> computeOptimalPath(String workflowId, List<String> similarWorkflows, Map<String, Object> constraints);
        Promise<List<BottleneckPrediction>> predictBottlenecks(WorkflowState state);
        Promise<List<ParallelOpportunity>> findParallelOpportunities(WorkflowState state);
        Promise<List<WorkflowTypeSuggestion>> analyzeIntent(String intent, Map<String, Object> context);
        Promise<TransitionValidation> validateTransition(String workflowId, String fromStep, String toStep);
    }

    public interface ResourceOptimizer {
        Promise<AllocationResult> optimizeAllocation(WorkflowState state, Map<String, Object> constraints);
    }

    public interface HistoricalDataService {
        Promise<List<String>> getSimilarWorkflows(WorkflowState state);
    }

    // Data types for services

    public record WorkflowState(
            String workflowId,
            String currentStep,
            Map<String, Object> stepStatuses,
            List<String> completedSteps,
            Map<String, Object> metrics
    ) {}

    public record StepPrediction(
            String stepId,
            String stepName,
            double confidence,
            String reason,
            String estimatedDuration,
            List<String> prerequisites,
            String riskLevel
    ) {}

    public record OptimalPath(
            List<PathStep> steps,
            String estimatedDuration,
            double successProbability
    ) {}

    public record PathStep(
            String stepId,
            String stepName,
            int order,
            boolean canRunParallel,
            List<String> dependencies,
            double probability,
            String rationale
    ) {}

    public record BottleneckPrediction(
            String stepId,
            String stepName,
            BottleneckType type,
            double probability,
            String reason,
            String impact,
            String mitigation
    ) {
        public enum BottleneckType {
            RESOURCE, DEPENDENCY, QUALITY, EXTERNAL, CAPACITY
        }
    }

    public record ParallelOpportunity(
            String groupId,
            List<String> stepIds,
            List<String> stepNames,
            double confidence,
            String rationale,
            String estimatedTimeSaved
    ) {}

    public record WorkflowTypeSuggestion(
            String workflowType,
            String displayName,
            double confidence,
            String reason,
            String suggestedTemplateId,
            int estimatedSteps,
            String avgDuration
    ) {}

    public record TransitionValidation(
            boolean isValid,
            double confidence,
            String reason,
            List<String> missingPrerequisites,
            List<String> warnings
    ) {}

    public record AllocationResult(
            List<ResourceAllocation> allocations,
            String totalDuration
    ) {}

    public record ResourceAllocation(
            String stepId,
            String stepName,
            String assigneeId,
            String assigneeName,
            double requiredCapacity,
            List<String> requiredSkills,
            double confidence,
            String rationale
    ) {}
}
