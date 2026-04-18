package com.ghatana.yappc.services.evolve;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.common.AiQualityTelemetry;
import com.ghatana.yappc.common.ServiceObservability;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.evolve.EvolutionTask;
import com.ghatana.yappc.domain.intent.ConstraintSpec;
import com.ghatana.yappc.domain.learn.Insights;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
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
                    Map<String, String> tags = Map.of("has_constraints", String.valueOf(constraints != null));
                    metrics.recordTimer("yappc.evolve.propose", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.evolve.propose", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("evolve.propose", insights, plan))
                            .map(v -> plan);
                })
                .whenException(e -> {
                    log.error("Evolution planning failed", e);
                    ServiceObservability.incrementFailure(
                        metrics,
                        "yappc.evolve.propose",
                        e,
                        Map.of("has_constraints", String.valueOf(constraints != null)));
                });
    }

    private Promise<EvolutionPlan> proposeWithAI(Insights insights, ConstraintSpec constraints) {
        String prompt = buildEvolutionPrompt(insights, constraints);
        Map<String, String> tags = Map.of("has_constraints", String.valueOf(constraints != null));

        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.3)
                .maxTokens(2000)
                .build())
                .then((result, e) -> {
                    if (e != null) {
                        AiQualityTelemetry.recordFallback(metrics, "yappc.ai.evolve.propose", e, tags);
                        log.warn("Evolution planning AI failed, using deterministic fallback plan", e);
                        return Promise.of(parseEvolutionPlanFromAIResponse(CompletionResult.of(""), insights));
                    }

                    AiQualityTelemetry.recordCompletion(metrics, "yappc.ai.evolve.propose", result, tags);
                    return Promise.of(parseEvolutionPlanFromAIResponse(result, insights));
                });
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
        String model = result.model() == null ? "unknown" : result.model();
        List<EvolutionTask> tasks = extractEvolutionTasks(result.text(), insights);

        return EvolutionPlan.builder()
                .id(UUID.randomUUID().toString())
                .insightsRef(insights.id())
                .tasks(tasks)
                .newIntentRef(generateNewIntentRef(insights))
                .createdAt(Instant.now())
                .metadata(Map.of("source", "ai-generated", "model", model, "task_count", String.valueOf(tasks.size())))
                .build();
    }

    private List<EvolutionTask> extractEvolutionTasks(String text, Insights insights) {
        List<EvolutionTask> tasks = new ArrayList<>();
        int priority = 1;

        for (String line : splitLines(text)) {
            String normalized = line.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (normalized.toLowerCase().startsWith("task") ||
                normalized.toLowerCase().startsWith("evolve") ||
                normalized.toLowerCase().startsWith("recommend") ||
                normalized.toLowerCase().startsWith("-")) {
                tasks.add(EvolutionTask.builder()
                    .id(UUID.randomUUID().toString())
                    .type(inferTaskType(normalized))
                    .description(stripPrefix(normalized))
                    .priority(priority++)
                    .dependencies(List.of())
                    .details(Map.of(
                        "source", "llm",
                        "estimated_effort", inferEffort(normalized),
                        "risk", inferRisk(normalized)
                    ))
                    .build());
            }
        }

        if (!tasks.isEmpty()) {
            return tasks;
        }

        List<EvolutionTask> fallback = new ArrayList<>();
        if (!insights.recommendations().isEmpty()) {
            int fallbackPriority = 1;
            for (var recommendation : insights.recommendations()) {
                fallback.add(EvolutionTask.builder()
                    .id(UUID.randomUUID().toString())
                    .type("optimize")
                    .description(recommendation.description())
                    .priority(fallbackPriority++)
                    .dependencies(List.of())
                    .details(Map.of(
                        "source", "insights-recommendation",
                        "estimated_effort", recommendation.priority() <= 1 ? "M" : "S",
                        "estimated_impact", recommendation.estimatedImpact()
                    ))
                    .build());
            }
            return fallback;
        }

        return List.of(EvolutionTask.builder()
            .id(UUID.randomUUID().toString())
            .type("stabilize")
            .description("Stabilize critical runtime bottlenecks and improve observability")
            .priority(1)
            .dependencies(List.of())
            .details(Map.of("source", "deterministic-fallback", "estimated_effort", "M", "risk", "low"))
            .build());
    }

    private String generateNewIntentRef(Insights insights) {
        // Generate a new intent based on insights for continuous improvement loop
        return "intent-" + UUID.randomUUID().toString();
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("\\r?\\n"));
    }

    private String stripPrefix(String line) {
        int separatorIndex = line.indexOf(':');
        if (separatorIndex > -1 && separatorIndex + 1 < line.length()) {
            return line.substring(separatorIndex + 1).trim();
        }
        if (line.startsWith("-")) {
            return line.substring(1).trim();
        }
        return line.trim();
    }

    private String inferTaskType(String line) {
        String lowered = line.toLowerCase();
        if (lowered.contains("refactor")) {
            return "refactor";
        }
        if (lowered.contains("fix") || lowered.contains("bug") || lowered.contains("error")) {
            return "fix";
        }
        if (lowered.contains("monitor") || lowered.contains("trace") || lowered.contains("telemetry")) {
            return "observe";
        }
        return "optimize";
    }

    private String inferEffort(String line) {
        String lowered = line.toLowerCase();
        if (lowered.contains("major") || lowered.contains("migration") || lowered.contains("platform")) {
            return "L";
        }
        if (lowered.contains("quick") || lowered.contains("small")) {
            return "S";
        }
        return "M";
    }

    private String inferRisk(String line) {
        String lowered = line.toLowerCase();
        if (lowered.contains("critical") || lowered.contains("prod")) {
            return "high";
        }
        if (lowered.contains("safe") || lowered.contains("low risk")) {
            return "low";
        }
        return "medium";
    }

}
