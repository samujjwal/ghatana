package com.ghatana.yappc.services.intent;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.ai.PromptTemplateRegistry;
import com.ghatana.yappc.ai.PromptTemplateVersion;
import com.ghatana.yappc.common.AiQualityTelemetry;
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
    private final PromptTemplateRegistry promptRegistry;

    public IntentServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        this(aiService, auditLogger, metrics, createDefaultPromptRegistry());
    }

    IntentServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            PromptTemplateRegistry promptRegistry) {
        this.aiService = aiService;
        this.auditLogger = auditLogger;
        this.metrics = metrics;
        this.promptRegistry = promptRegistry;
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
        Map<String, String> tags = ServiceObservability.tenantTag(input.tenantId());

        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.3)
                .maxTokens(2000)
                .build())
                .then((result, e) -> {
                    if (e != null) {
                        AiQualityTelemetry.recordFallback(metrics, "yappc.ai.intent.capture", e, tags);
                        log.warn("Intent capture AI failed, using fallback parser", e);
                        return Promise.of(StructuredOutputParser.parseIntentSpec("", input));
                    }

                    AiQualityTelemetry.recordCompletion(metrics, "yappc.ai.intent.capture", result, tags);
                    return Promise.of(parseIntentFromAIResponse(result, input));
                });
    }

    private Promise<IntentAnalysis> analyzeIntentWithAI(IntentSpec spec) {
        String prompt = buildIntentAnalysisPrompt(spec);
        Map<String, String> tags = ServiceObservability.tenantTag(spec.tenantId());

        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.2)
                .maxTokens(1500)
                .build())
                .then((result, e) -> {
                    if (e != null) {
                        AiQualityTelemetry.recordFallback(metrics, "yappc.ai.intent.analyze", e, tags);
                        log.warn("Intent analysis AI failed, using fallback parser", e);
                        return Promise.of(StructuredOutputParser.parseIntentAnalysis("", spec.id()));
                    }

                    AiQualityTelemetry.recordCompletion(metrics, "yappc.ai.intent.analyze", result, tags);
                    return Promise.of(parseAnalysisFromAIResponse(result, spec));
                });
    }

    private String buildIntentCapturePrompt(IntentInput input) {
        String tenantId = input.tenantId() != null ? input.tenantId() : "default";
        PromptTemplateVersion selected = promptRegistry
            .selectForExperiment("intent.capture", "v1", tenantId, "intent.capture.default")
            .orElseGet(() -> promptRegistry.latest("intent.capture").orElse(null));

        String fallbackTemplate = """
            You are a product planning expert. Analyze the following product idea and extract structured information.

            Product Idea:
            ${rawText}

            Respond with a JSON object containing:
            {
              "productName": "string",
              "description": "string",
              "goals": [{"description": "string", "category": "business|technical|ux", "priority": number, "successMetrics": ["string"]}],
              "personas": [{"name": "string", "description": "string", "needs": ["string"], "painPoints": ["string"]}],
              "constraints": [{"type": "budget|timeline|technical|regulatory", "description": "string", "severity": "hard|soft"}]
            }

            Provide ONLY the JSON object, no additional text.
            """;

        String template = selected == null ? fallbackTemplate : selected.template();
        return template.replace("${rawText}", input.rawText());
    }

    private String buildIntentAnalysisPrompt(IntentSpec spec) {
        String tenantId = spec.tenantId() != null ? spec.tenantId() : "default";
        PromptTemplateVersion selected = promptRegistry
                .selectForExperiment("intent.analyze", "v1", tenantId, "intent.analyze.default")
                .orElseGet(() -> promptRegistry.latest("intent.analyze").orElse(null));

        String fallbackTemplate = """
            Analyze the following product intent for feasibility, risks, and gaps.

            Product: ${productName}
            Description: ${description}
            Goals: ${goals}

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
            """;

        String template = selected == null ? fallbackTemplate : selected.template();
        return template
                .replace("${productName}", spec.productName())
                .replace("${description}", spec.description())
                .replace("${goals}", spec.goals().stream().map(GoalSpec::description).toList().toString());
    }

    private static PromptTemplateRegistry createDefaultPromptRegistry() {
        PromptTemplateRegistry registry = new PromptTemplateRegistry();

        registry.register(PromptTemplateVersion.of(
                "intent.capture",
                "v1",
                "baseline",
                """
                    You are a product planning expert. Analyze the following product idea and extract structured information.

                    Product Idea:
                    ${rawText}

                    Respond with a JSON object containing:
                    {
                      "productName": "string",
                      "description": "string",
                      "goals": [{"description": "string", "category": "business|technical|ux", "priority": number, "successMetrics": ["string"]}],
                      "personas": [{"name": "string", "description": "string", "needs": ["string"], "painPoints": ["string"]}],
                      "constraints": [{"type": "budget|timeline|technical|regulatory", "description": "string", "severity": "hard|soft"}]
                    }

                    Provide ONLY the JSON object, no additional text.
                    """,
                80));

        registry.register(PromptTemplateVersion.of(
                "intent.capture",
                "v1",
                "concise",
                """
                    You are a product planning expert. Convert the idea below into structured JSON only.

                    Idea:
                    ${rawText}

                    Required keys: productName, description, goals, personas, constraints.
                    Return valid JSON only.
                    """,
                20));

        registry.register(PromptTemplateVersion.of(
                "intent.analyze",
                "v1",
                "baseline",
                """
                    Analyze the following product intent for feasibility, risks, and gaps.

                    Product: ${productName}
                    Description: ${description}
                    Goals: ${goals}

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
                    """,
                100));

        return registry;
    }

    private IntentSpec parseIntentFromAIResponse(CompletionResult result, IntentInput input) {
        return StructuredOutputParser.parseIntentSpec(result.text(), input);
    }

    private IntentAnalysis parseAnalysisFromAIResponse(CompletionResult result, IntentSpec spec) {
        return StructuredOutputParser.parseIntentAnalysis(result.text(), spec.id());
    }

}
