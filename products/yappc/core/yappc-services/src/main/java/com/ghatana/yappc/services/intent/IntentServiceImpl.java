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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
    private final IntentRepository intentRepository;
    private final IntentEvidenceService intentEvidenceService;

    public IntentServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            IntentRepository intentRepository,
            IntentEvidenceService intentEvidenceService) {
        this(aiService, auditLogger, metrics, createDefaultPromptRegistry(), intentRepository, intentEvidenceService);
    }

    IntentServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            PromptTemplateRegistry promptRegistry) {
        this(aiService, auditLogger, metrics, promptRegistry, null);
    }

    IntentServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            PromptTemplateRegistry promptRegistry,
            IntentRepository intentRepository) {
        this(aiService, auditLogger, metrics, promptRegistry, intentRepository, null);
    }

    IntentServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            PromptTemplateRegistry promptRegistry,
            IntentRepository intentRepository,
            IntentEvidenceService intentEvidenceService) {
        this.aiService = Objects.requireNonNull(aiService, "aiService");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.promptRegistry = Objects.requireNonNull(promptRegistry, "promptRegistry");
        this.intentRepository = Objects.requireNonNull(intentRepository, "intentRepository");
        this.intentEvidenceService = Objects.requireNonNull(intentEvidenceService, "intentEvidenceService");
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
                            .then(v -> recordCaptureEvidence(input, spec))
                            .then(evidenceId -> persistCapturedIntent(input, spec, evidenceId))
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
                .then(enrichedAnalysis -> {
                    IntentAnalysis analysis = enrichedAnalysis.analysis();
                    long duration = System.currentTimeMillis() - startTime;
                    Map<String, String> tags = ServiceObservability.tenantTag(spec.tenantId());
                    metrics.recordTimer("yappc.intent.analyze", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.intent.analyze", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("intent.analyze", spec, analysis))
                            .then(v -> recordAnalysisEvidence(spec, analysis, enrichedAnalysis.grounding().asMap()))
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

    @Override
    public Promise<Optional<IntentVersionRecord>> findLatest(
            String tenantId,
            String workspaceId,
            String projectId,
            String intentId) {
        return intentRepository.findLatest(tenantId, workspaceId, projectId, intentId);
    }

    @Override
    public Promise<List<IntentVersionRecord>> history(
            String tenantId,
            String workspaceId,
            String projectId,
            String intentId) {
        return intentRepository.history(tenantId, workspaceId, projectId, intentId);
    }

    @Override
    public Promise<Long> count() {
        return Promise.ofException(new IllegalStateException(
                "Intent count requires a tenant-scoped repository call; use IntentRepository.count(tenantId)."));
    }

    private Promise<String> recordCaptureEvidence(IntentInput input, IntentSpec spec) {
        return intentEvidenceService.recordCapture(input, spec);
    }

    private Promise<String> recordAnalysisEvidence(
            IntentSpec spec,
            IntentAnalysis analysis,
            Map<String, Object> groundingMetadata) {
        return intentEvidenceService.recordAnalysis(spec, analysis, groundingMetadata);
    }

    private Promise<IntentVersionRecord> persistCapturedIntent(IntentInput input, IntentSpec spec, String evidenceId) {
        List<String> evidenceIds = evidenceId == null || evidenceId.isBlank() ? List.of() : List.of(evidenceId);
        IntentPersistenceContext context = new IntentPersistenceContext(
                input.tenantId(),
                input.workspaceId(),
                input.projectId(),
                input.userId(),
                null,
                evidenceIds,
                Map.of("source", "intent.capture"));
        return intentRepository.saveVersion(spec, context);
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

    private Promise<AnalyzedIntent> analyzeIntentWithAI(IntentSpec spec) {
        PromptBuildResult prompt = buildIntentAnalysisPrompt(spec);
        Map<String, String> tags = ServiceObservability.tenantTag(spec.tenantId());

        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt.prompt())
                .temperature(0.2)
                .maxTokens(1500)
                .build())
                .then((result, e) -> {
                    if (e != null) {
                        AiQualityTelemetry.recordFallback(metrics, "yappc.ai.intent.analyze", e, tags);
                        log.warn("Intent analysis AI failed, using fallback parser", e);
                        IntentAnalysis fallback = StructuredOutputParser.parseIntentAnalysis("", spec.id());
                        return Promise.of(new AnalyzedIntent(fallback, AiGroundingMetadata.fallback(prompt, e)));
                    }

                    AiQualityTelemetry.recordCompletion(metrics, "yappc.ai.intent.analyze", result, tags);
                    IntentAnalysis analysis = parseAnalysisFromAIResponse(result, spec);
                    return Promise.of(new AnalyzedIntent(
                            analysis,
                            AiGroundingMetadata.success(prompt, result, confidence(analysis))));
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

    private PromptBuildResult buildIntentAnalysisPrompt(IntentSpec spec) {
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
        String prompt = template
                .replace("${productName}", spec.productName())
                .replace("${description}", spec.description())
                .replace("${goals}", spec.goals().stream().map(GoalSpec::description).toList().toString());
        return new PromptBuildResult(
                prompt,
                selected == null ? "intent.analyze" : selected.key(),
                selected == null ? "fallback" : selected.version(),
                selected == null ? "fallback" : selected.variant(),
                sha256(prompt));
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

    private static double confidence(IntentAnalysis analysis) {
        if (analysis == null || analysis.scores() == null || analysis.scores().isEmpty()) {
            return analysis != null && analysis.feasible() ? 0.75 : 0.45;
        }
        return analysis.scores().values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(analysis.feasible() ? 0.75 : 0.45);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }

    private record PromptBuildResult(
            String prompt,
            String promptKey,
            String promptVersion,
            String promptVariant,
            String promptHash
    ) {
    }

    private record AiGroundingMetadata(
            String promptKey,
            String promptVersion,
            String promptVariant,
            String promptHash,
            String modelUsed,
            String finishReason,
            int tokensUsed,
            double confidence,
            String errorType
    ) {
        static AiGroundingMetadata success(
                PromptBuildResult prompt,
                CompletionResult result,
                double confidence) {
            return new AiGroundingMetadata(
                    prompt.promptKey(),
                    prompt.promptVersion(),
                    prompt.promptVariant(),
                    prompt.promptHash(),
                    result.model(),
                    result.getFinishReason(),
                    result.getTokensUsed(),
                    confidence,
                    "");
        }

        static AiGroundingMetadata fallback(PromptBuildResult prompt, Throwable error) {
            return new AiGroundingMetadata(
                    prompt.promptKey(),
                    prompt.promptVersion(),
                    prompt.promptVariant(),
                    prompt.promptHash(),
                    "fallback-parser",
                    "ai_error",
                    0,
                    0.35,
                    error.getClass().getSimpleName());
        }

        Map<String, Object> asMap() {
            return Map.of(
                    "promptKey", promptKey,
                    "promptVersion", promptVersion,
                    "promptVariant", promptVariant,
                    "promptHash", promptHash,
                    "modelUsed", modelUsed == null ? "" : modelUsed,
                    "finishReason", finishReason == null ? "" : finishReason,
                    "tokensUsed", tokensUsed,
                    "confidence", confidence,
                    "errorType", errorType == null ? "" : errorType);
        }
    }

    private record AnalyzedIntent(IntentAnalysis analysis, AiGroundingMetadata grounding) {
    }

}
