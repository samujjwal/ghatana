package com.ghatana.yappc.services.intent;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.common.ServiceObservability;
import com.ghatana.yappc.ai.StructuredOutputParser;
import com.ghatana.yappc.domain.intent.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose AI-assisted intent capture and analysis implementation
 * @doc.layer service
 * @doc.pattern Service
 */
public class IntentServiceImpl implements IntentService {

    private static final Logger log = LoggerFactory.getLogger(IntentServiceImpl.class);

    private final CompletionService aiService;
    private final AuditLogger auditLogger;
    private final MetricsCollector metrics;

    public IntentServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        this.aiService = aiService;
        this.auditLogger = auditLogger;
        this.metrics = metrics;
    }

    @Override
    public Promise<IntentSpec> capture(IntentInput input) {
        long startTime = System.currentTimeMillis();

        return parseIntentWithAI(input)
                .then(spec -> {
                    long duration = System.currentTimeMillis() - startTime;
                    Map<String, String> tags = ServiceObservability.tenantTag(input.tenantId());
                    metrics.recordTimer("yappc.intent.capture", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.intent.capture", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("intent.capture", input, spec))
                            .map(v -> spec);
                })
                .whenException(e -> {
                    log.error("Failed to capture intent", e);
                    ServiceObservability.incrementFailure(
                        metrics,
                        "yappc.intent.capture",
                        e,
                        ServiceObservability.tenantTag(input.tenantId()));
                });
    }

    @Override
    public Promise<IntentAnalysis> analyze(IntentSpec spec) {
        long startTime = System.currentTimeMillis();

        return analyzeIntentWithAI(spec)
                .then(analysis -> {
                    long duration = System.currentTimeMillis() - startTime;
                    Map<String, String> tags = ServiceObservability.tenantTag(spec.tenantId());
                    metrics.recordTimer("yappc.intent.analyze", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.intent.analyze", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("intent.analyze", spec, analysis))
                            .map(v -> analysis);
                })
                .whenException(e -> {
                    log.error("Failed to analyze intent", e);
                    ServiceObservability.incrementFailure(
                        metrics,
                        "yappc.intent.analyze",
                        e,
                        ServiceObservability.tenantTag(spec.tenantId()));
                });
    }

    private Promise<IntentSpec> parseIntentWithAI(IntentInput input) {
        String prompt = buildIntentCapturePrompt(input);

        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.3)
                .maxTokens(2000)
                .build())
                .map(result -> parseIntentFromAIResponse(result, input));
    }

    private Promise<IntentAnalysis> analyzeIntentWithAI(IntentSpec spec) {
        String prompt = buildIntentAnalysisPrompt(spec);

        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.2)
                .maxTokens(1500)
                .build())
                .map(result -> parseAnalysisFromAIResponse(result, spec));
    }

    private String buildIntentCapturePrompt(IntentInput input) {
        return """
            You are a product planning expert. Analyze the following product idea and extract structured information.

            Product Idea:
            %s

            Respond with a JSON object containing:
            {
              "productName": "string",
              "description": "string",
              "goals": [{"description": "string", "category": "business|technical|ux", "priority": number, "successMetrics": ["string"]}],
              "personas": [{"name": "string", "description": "string", "needs": ["string"], "painPoints": ["string"]}],
              "constraints": [{"type": "budget|timeline|technical|regulatory", "description": "string", "severity": "hard|soft"}]
            }

            Provide ONLY the JSON object, no additional text.
            """.formatted(input.rawText());
    }

    private String buildIntentAnalysisPrompt(IntentSpec spec) {
        return """
            Analyze the following product intent for feasibility, risks, and gaps.

            Product: %s
            Description: %s
            Goals: %s

            Respond with a JSON object:
            {
              "feasible": boolean,
              "risks": ["string"],
              "gaps": ["string"],
              "assumptions": ["string"],
              "scores": {"complexity": 0.0-1.0, "feasibility": 0.0-1.0, "innovation": 0.0-1.0},
              "summary": "string"
            }

            Provide ONLY the JSON object.
            """.formatted(spec.productName(), spec.description(),
                spec.goals().stream().map(GoalSpec::description).toList());
    }

    private IntentSpec parseIntentFromAIResponse(CompletionResult result, IntentInput input) {
        return StructuredOutputParser.parseIntentSpec(result.text(), input);
    }

    private IntentAnalysis parseAnalysisFromAIResponse(CompletionResult result, IntentSpec spec) {
        return StructuredOutputParser.parseIntentAnalysis(result.text(), spec.id());
    }

}
