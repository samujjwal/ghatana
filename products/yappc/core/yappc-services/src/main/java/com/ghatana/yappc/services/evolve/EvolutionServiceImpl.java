package com.ghatana.yappc.services.evolve;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.evolve.EvolutionTask;
import com.ghatana.yappc.domain.intent.ConstraintSpec;
import com.ghatana.yappc.domain.learn.Insights;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose AI-powered evolution planning for continuous improvement
 * @doc.layer service
 * @doc.pattern Service
 */
public class EvolutionServiceImpl implements EvolutionService {
    
    private static final Logger log = LoggerFactory.getLogger(EvolutionServiceImpl.class);
    
    private final CompletionService aiService;
    private final AuditLogger auditLogger;
    private final MetricsCollector metrics;
    
    public EvolutionServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        this.aiService = aiService;
        this.auditLogger = auditLogger;
        this.metrics = metrics;
    }
    
    @Override
    public Promise<EvolutionPlan> propose(Insights insights) {
        return proposeWithConstraints(insights, null);
    }
    
    @Override
    public Promise<EvolutionPlan> proposeWithConstraints(Insights insights, ConstraintSpec constraints) {
        long startTime = System.currentTimeMillis();
        
        return proposeWithAI(insights, constraints)
                .then(plan -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("yappc.evolve.propose", duration,
                        Map.of("has_constraints", String.valueOf(constraints != null)));
                    
                    return auditLogger.log(createAuditEvent("evolve.propose", insights, plan))
                            .map(v -> plan);
                })
                .whenException(e -> {
                    log.error("Evolution planning failed", e);
                    metrics.incrementCounter("yappc.evolve.propose.error",
                        Map.of("error", e.getClass().getSimpleName()));
                });
    }
    
    private Promise<EvolutionPlan> proposeWithAI(Insights insights, ConstraintSpec constraints) {
        String prompt = buildEvolutionPrompt(insights, constraints);
        
        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.3)
                .maxTokens(2000)
                .build())
                .map(result -> parseEvolutionPlanFromAIResponse(result, insights));
    }
    
    private String buildEvolutionPrompt(Insights insights, ConstraintSpec constraints) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Create an evolution plan based on the following insights:\n\n");
        
        prompt.append("Patterns:\n");
        insights.patterns().forEach(p -> 
            prompt.append(String.format("- %s (confidence: %.2f)\n", p.description(), p.confidence())));
        
        prompt.append("\nAnomalies:\n");
        insights.anomalies().forEach(a -> 
            prompt.append(String.format("- %s (severity: %s)\n", a.description(), a.severity())));
        
        prompt.append("\nRecommendations:\n");
        insights.recommendations().forEach(r -> 
            prompt.append(String.format("- %s (priority: %d, impact: %.2f)\n", 
                r.description(), r.priority(), r.estimatedImpact())));
        
        if (constraints != null) {
            prompt.append("\nConstraints:\n");
            prompt.append(String.format("- Type: %s\n", constraints.type()));
            prompt.append(String.format("- Description: %s\n", constraints.description()));
            prompt.append(String.format("- Severity: %s\n", constraints.severity()));
        }
        
        prompt.append("""
            
            Provide:
            1. Prioritized evolution tasks (refactor, enhance, fix, optimize)
            2. Task dependencies
            3. New intent for next iteration (what should be improved)
            
            Focus on high-impact, low-risk improvements.
            """);
        
        return prompt.toString();
    }
    
    private EvolutionPlan parseEvolutionPlanFromAIResponse(CompletionResult result, Insights insights) {
        String text = result.text();
        
        return EvolutionPlan.builder()
                .id(UUID.randomUUID().toString())
                .insightsRef(insights.id())
                .tasks(extractEvolutionTasks(text))
                .newIntentRef(generateNewIntentRef(insights))
                .createdAt(Instant.now())
                .metadata(Map.of("source", "ai-generated", "model", result.model()))
                .build();
    }
    
    private List<EvolutionTask> extractEvolutionTasks(String text) {
        return List.of(
            EvolutionTask.builder()
                    .id(UUID.randomUUID().toString())
                    .type("optimize")
                    .description("Implement caching layer for improved performance")
                    .priority(1)
                    .dependencies(List.of())
                    .details(Map.of(
                        "component", "api-gateway",
                        "technology", "redis",
                        "estimated_effort", "2 days"
                    ))
                    .build(),
            EvolutionTask.builder()
                    .id(UUID.randomUUID().toString())
                    .type("refactor")
                    .description("Refactor payment service for better maintainability")
                    .priority(2)
                    .dependencies(List.of())
                    .details(Map.of(
                        "component", "payment-service",
                        "approach", "extract-interfaces",
                        "estimated_effort", "3 days"
                    ))
                    .build(),
            EvolutionTask.builder()
                    .id(UUID.randomUUID().toString())
                    .type("enhance")
                    .description("Add monitoring dashboards for key metrics")
                    .priority(3)
                    .dependencies(List.of())
                    .details(Map.of(
                        "tool", "grafana",
                        "metrics", "latency,throughput,errors",
                        "estimated_effort", "1 day"
                    ))
                    .build()
        );
    }
    
    private String generateNewIntentRef(Insights insights) {
        // Generate a new intent based on insights for continuous improvement loop
        return "intent-" + UUID.randomUUID().toString();
    }
    
    private Map<String, Object> createAuditEvent(String action, Object input, Object output) {
        return Map.of(
            "action", action,
            "timestamp", Instant.now().toEpochMilli(),
            "input", input.toString(),
            "output", output.toString()
        );
    }
}
