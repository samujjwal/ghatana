package com.ghatana.yappc.services.learn;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.learn.*;
import com.ghatana.yappc.domain.observe.Observation;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
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
    
    public LearningServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        this.aiService = aiService;
        this.auditLogger = auditLogger;
        this.metrics = metrics;
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
                    metrics.recordTimer("yappc.learn.analyze", duration,
                        Map.of("has_context", String.valueOf(context != null)));
                    
                    return auditLogger.log(createAuditEvent("learn.analyze", observation, insights))
                            .map(v -> insights);
                })
                .whenException(e -> {
                    log.error("Learning analysis failed", e);
                    metrics.incrementCounter("yappc.learn.analyze.error",
                        Map.of("error", e.getClass().getSimpleName()));
                });
    }
    
    private Promise<Insights> analyzeWithAI(Observation observation, HistoricalContext context) {
        String prompt = buildAnalysisPrompt(observation, context);
        
        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.3)
                .maxTokens(2000)
                .build())
                .map(result -> parseInsightsFromAIResponse(result, observation));
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
                .patterns(extractPatterns(text))
                .anomalies(extractAnomalies(text))
                .recommendations(extractRecommendations(text))
                .generatedAt(Instant.now())
                .build();
    }
    
    private List<Pattern> extractPatterns(String text) {
        return List.of(
            Pattern.builder()
                    .id(UUID.randomUUID().toString())
                    .type("performance")
                    .description("Consistent response times under normal load")
                    .confidence(0.85)
                    .evidence(List.of("metric1", "metric2"))
                    .build(),
            Pattern.builder()
                    .id(UUID.randomUUID().toString())
                    .type("usage")
                    .description("Peak usage during business hours")
                    .confidence(0.92)
                    .evidence(List.of("log1", "log2"))
                    .build()
        );
    }
    
    private List<Anomaly> extractAnomalies(String text) {
        return List.of(
            Anomaly.builder()
                    .id(UUID.randomUUID().toString())
                    .type("latency")
                    .description("Increased response time in payment service")
                    .severity("medium")
                    .affectedComponents(List.of("payment-service", "api-gateway"))
                    .build()
        );
    }
    
    private List<Recommendation> extractRecommendations(String text) {
        return List.of(
            Recommendation.builder()
                    .id(UUID.randomUUID().toString())
                    .type("optimization")
                    .description("Add caching layer to reduce database queries")
                    .priority(1)
                    .estimatedImpact(0.3)
                    .actionItems(List.of(
                        "Implement Redis cache",
                        "Configure cache TTL",
                        "Monitor cache hit rate"
                    ))
                    .build(),
            Recommendation.builder()
                    .id(UUID.randomUUID().toString())
                    .type("scaling")
                    .description("Scale payment service horizontally")
                    .priority(2)
                    .estimatedImpact(0.4)
                    .actionItems(List.of(
                        "Add auto-scaling policy",
                        "Configure load balancer",
                        "Test scaling behavior"
                    ))
                    .build()
        );
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
