package com.ghatana.yappc.services.learn;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.common.AiQualityTelemetry;
import com.ghatana.yappc.common.ServiceObservability;
import com.ghatana.yappc.domain.learn.*;
import com.ghatana.yappc.domain.observe.Observation;
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
 * @doc.purpose AI-powered insight extraction from observations
 * @doc.layer service
 * @doc.pattern Service
 */
public class LearningServiceImpl implements LearningService {

    private static final Logger log = LoggerFactory.getLogger(LearningServiceImpl.class);

    private final CompletionService aiService;
    private final AuditLogger auditLogger;
    private final MetricsCollector metrics;
    private final LearningEvidenceRepository evidenceRepository;
    private final boolean persistEvidence;

    public LearningServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        this(aiService, auditLogger, metrics, LearningEvidenceRepository.noop(), false);
    }

    public LearningServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            LearningEvidenceRepository evidenceRepository) {
        this(aiService, auditLogger, metrics, evidenceRepository, true);
    }

    private LearningServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            LearningEvidenceRepository evidenceRepository,
            boolean persistEvidence) {
        this.aiService = aiService;
        this.auditLogger = auditLogger;
        this.metrics = metrics;
        this.evidenceRepository = evidenceRepository;
        this.persistEvidence = persistEvidence;
    }

    @Override
    public Promise<Insights> analyze(Observation observation) {
        return analyzeWithContext(observation, null);
    }

    @Override
    public Promise<Insights> analyzeWithContext(Observation observation, HistoricalContext context) {
        long startTime = System.currentTimeMillis();

        return analyzeWithAI(observation, context)
                .then(insights -> {
                    long duration = System.currentTimeMillis() - startTime;
                    Map<String, String> tags = Map.of("has_context", String.valueOf(context != null));
                    metrics.recordTimer("yappc.learn.analyze", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.learn.analyze", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("learn.analyze", observation, insights))
                            .then(v -> persistEvidence
                                    ? evidenceRepository.save(buildLearningEvidence(observation, insights))
                                            .map(ignored -> insights)
                                    : Promise.of(insights));
                })
                .whenException(e -> {
                    log.error("Learning analysis failed", e);
                    ServiceObservability.incrementFailure(
                        metrics,
                        "yappc.learn.analyze",
                        e,
                        Map.of("has_context", String.valueOf(context != null)));
                });
    }

    private Promise<Insights> analyzeWithAI(Observation observation, HistoricalContext context) {
        String prompt = buildAnalysisPrompt(observation, context);
        Map<String, String> tags = Map.of("has_context", String.valueOf(context != null));

        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.3)
                .maxTokens(2000)
                .build())
                .then((result, e) -> {
                    if (e != null) {
                        AiQualityTelemetry.recordFallback(metrics, "yappc.ai.learn.analyze", e, tags);
                        log.warn("Learning analysis AI failed, using deterministic fallback insights", e);
                        return Promise.of(parseInsightsFromAIResponse(CompletionResult.of(""), observation));
                    }

                    AiQualityTelemetry.recordCompletion(metrics, "yappc.ai.learn.analyze", result, tags);
                    return Promise.of(parseInsightsFromAIResponse(result, observation));
                });
    }

    private LearningEvidenceRepository.LearningEvidence buildLearningEvidence(
            Observation observation,
            Insights insights) {
        String tenantId = resolveTenantId();
        String runRef = stableRef(observation.runRef(), "run-unavailable");
        String projectId = extractProjectRef(runRef);
        return new LearningEvidenceRepository.LearningEvidence(
                "learn-" + insights.id(),
                tenantId,
                projectId,
                runRef,
                observation,
                insights,
                List.of(observation.id(), insights.id(), runRef),
                Map.of(
                        "patternCount", safeSize(insights.patterns()),
                        "anomalyCount", safeSize(insights.anomalies()),
                        "recommendationCount", safeSize(insights.recommendations())
                ),
                Instant.now()
        );
    }

    private static String resolveTenantId() {
        String tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException(
                    "LearningService requires an active tenant context. "
                            + "Ensure ApiKeyAuthFilter or TenantExtractionFilter is applied.");
        }
        if ("default-tenant".equals(tenantId)) {
            throw new SecurityException(
                    "LearningService does not allow default-tenant. "
                            + "A valid tenant ID must be configured in YAPPC_API_KEY_TENANT_MAP.");
        }
        return tenantId;
    }

    private static String stableRef(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String extractProjectRef(String runRef) {
        int separator = runRef.indexOf(':');
        if (separator > 0) {
            return runRef.substring(0, separator);
        }
        return runRef;
    }

    private static int safeSize(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private String buildAnalysisPrompt(Observation observation, HistoricalContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze the following system observations and provide insights:\n\n");

        prompt.append("Metrics:\n");
        observation.metrics().forEach(m ->
            prompt.append(String.format("- %s: %.2f %s\n", m.name(), m.value(), m.unit())));

        prompt.append("\nLogs:\n");
        observation.logs().forEach(l ->
            prompt.append(String.format("- [%s] %s\n", l.level(), l.message())));

        if (context != null) {
            prompt.append("\nHistorical Context:\n");
            prompt.append("Known patterns: ").append(context.knownPatterns().size()).append("\n");
        }

        prompt.append("""

            Provide:
            1. Patterns detected (recurring behaviors or trends)
            2. Anomalies (unusual or unexpected behaviors)
            3. Recommendations (actionable improvements)

            Focus on performance, reliability, and user experience.
            """);

        return prompt.toString();
    }

    private Insights parseInsightsFromAIResponse(CompletionResult result, Observation observation) {
        String text = result.text();

        return Insights.builder()
                .id(UUID.randomUUID().toString())
                .observationRef(observation.id())
                .patterns(extractPatterns(text, observation))
                .anomalies(extractAnomalies(text, observation))
                .recommendations(extractRecommendations(text, observation))
                .generatedAt(Instant.now())
                .build();
    }

    private List<Pattern> extractPatterns(String text, Observation observation) {
        List<Pattern> patterns = new ArrayList<>();
        for (String line : splitLines(text)) {
            String normalized = line.trim();
            String lower = normalized.toLowerCase();
            if (lower.startsWith("pattern") || lower.startsWith("p:") || lower.contains("pattern:")) {
                patterns.add(Pattern.builder()
                    .id(UUID.randomUUID().toString())
                    .type(inferType(normalized, "pattern"))
                    .description(stripPrefix(normalized))
                    .confidence(estimateConfidence(normalized, 0.75))
                    .evidence(observation.metrics().stream().limit(2).map(m -> m.name()).toList())
                    .build());
            }
        }

        if (!patterns.isEmpty()) {
            return patterns;
        }

        return observation.metrics().stream()
            .limit(2)
            .map(metric -> Pattern.builder()
                .id(UUID.randomUUID().toString())
                .type("metric")
                .description("Stable trend detected for metric: " + metric.name())
                .confidence(0.65)
                .evidence(List.of(metric.name()))
                .build())
            .toList();
    }

    private List<Anomaly> extractAnomalies(String text, Observation observation) {
        List<Anomaly> anomalies = new ArrayList<>();
        for (String line : splitLines(text)) {
            String normalized = line.trim();
            if (normalized.toLowerCase().startsWith("anomaly") || normalized.toLowerCase().startsWith("a:")) {
                anomalies.add(Anomaly.builder()
                    .id(UUID.randomUUID().toString())
                    .type(inferType(normalized, "runtime"))
                    .description(stripPrefix(normalized))
                    .severity(inferSeverity(normalized))
                    .affectedComponents(observation.logs().stream().limit(2).map(log -> log.level()).toList())
                    .build());
            }
        }

        if (!anomalies.isEmpty()) {
            return anomalies;
        }

        if (observation.logs().isEmpty()) {
            return List.of();
        }

        return List.of(Anomaly.builder()
            .id(UUID.randomUUID().toString())
            .type("log-signal")
            .description("Potential anomaly inferred from runtime logs")
            .severity("low")
            .affectedComponents(observation.logs().stream().limit(2).map(log -> log.level()).toList())
            .build());
    }

    private List<Recommendation> extractRecommendations(String text, Observation observation) {
        List<Recommendation> recommendations = new ArrayList<>();
        int priority = 1;
        for (String line : splitLines(text)) {
            String normalized = line.trim();
            if (normalized.toLowerCase().startsWith("recommend") || normalized.toLowerCase().startsWith("r:")) {
                recommendations.add(Recommendation.builder()
                    .id(UUID.randomUUID().toString())
                    .type(inferType(normalized, "improvement"))
                    .description(stripPrefix(normalized))
                    .priority(priority++)
                    .estimatedImpact(estimateImpact(normalized))
                    .actionItems(List.of("Implement change", "Verify via metrics", "Roll out safely"))
                    .build());
            }
        }

        if (!recommendations.isEmpty()) {
            return recommendations;
        }

        return List.of(Recommendation.builder()
            .id(UUID.randomUUID().toString())
            .type("stabilize")
            .description("Review top runtime metrics and optimize bottlenecks")
            .priority(1)
            .estimatedImpact(observation.metrics().isEmpty() ? 0.2 : 0.35)
            .actionItems(List.of("Prioritize high-latency components", "Add targeted tests", "Track post-change metrics"))
            .build());
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
        return line;
    }

    private String inferType(String line, String fallback) {
        String lowered = line.toLowerCase();
        if (lowered.contains("latency") || lowered.contains("performance")) {
            return "performance";
        }
        if (lowered.contains("error") || lowered.contains("failure")) {
            return "reliability";
        }
        return fallback;
    }

    private String inferSeverity(String line) {
        String lowered = line.toLowerCase();
        if (lowered.contains("critical") || lowered.contains("high")) {
            return "high";
        }
        if (lowered.contains("medium")) {
            return "medium";
        }
        return "low";
    }

    private double estimateConfidence(String line, double fallback) {
        String lowered = line.toLowerCase();
        if (lowered.contains("certain") || lowered.contains("strong")) {
            return 0.9;
        }
        if (lowered.contains("weak")) {
            return 0.55;
        }
        return fallback;
    }

    private double estimateImpact(String line) {
        String lowered = line.toLowerCase();
        if (lowered.contains("critical") || lowered.contains("high")) {
            return 0.6;
        }
        if (lowered.contains("medium")) {
            return 0.4;
        }
        return 0.25;
    }

}
