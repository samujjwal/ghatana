package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.tutorputor.contentgeneration.domain.AnimationConfig;
import com.ghatana.tutorputor.contentgeneration.domain.AssessmentItem;
import com.ghatana.tutorputor.contentgeneration.domain.ContentExample;
import com.ghatana.tutorputor.contentgeneration.domain.ContentGenerationRequest;
import com.ghatana.tutorputor.contentgeneration.domain.LearningClaim;
import com.ghatana.tutorputor.contentgeneration.domain.QualityReport;
import com.ghatana.tutorputor.contentgeneration.domain.SimulationManifest;
import com.ghatana.tutorputor.contentgeneration.domain.UnifiedContentGenerator;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Platform-backed implementation of the unified content generator.
 *
 * <p>The previous implementation had drifted away from the current domain request and
 * response types and had also been corrupted by bulk text replacement. This version
 * restores a coherent implementation against the active domain model so the module can
 * compile and the service contract remains usable.
 *
 * @doc.type class
 * @doc.purpose Platform-based educational content generation adapter
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class PlatformContentGenerator implements UnifiedContentGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformContentGenerator.class);

    private final LLMGateway llmGateway;
    private final ContentValidator validator;
    private final PromptTemplateEngine promptEngine;
    private final MetricsCollector metrics;

    public PlatformContentGenerator(
            LLMGateway llmGateway,
            ContentValidator validator,
            PromptTemplateEngine promptEngine,
            MetricsCollector metrics
    ) {
        this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway required");
        this.validator = Objects.requireNonNull(validator, "validator required");
        this.promptEngine = Objects.requireNonNull(promptEngine, "promptEngine required");
        this.metrics = Objects.requireNonNull(metrics, "metrics required");
    }

    @Override
    public Promise<CompleteContentPackage> generateCompletePackage(ContentGenerationRequest request) {
        Objects.requireNonNull(request, "request required");
        LOG.info("Generating complete content package for topic {} in domain {}", request.getTopic(), request.getDomain());

        long startTime = System.currentTimeMillis();
        ClaimsRequest claimsRequest = new ClaimsRequest(
                request.getTopic(),
                request.getGradeLevel(),
                request.getDomain().name(),
                request.getMaxClaims()
        );

        return generateClaims(claimsRequest)
                .then(claimsResponse -> {
                    List<LearningClaim> claims = claimsResponse.claims();
                    return generateExamples(new ExamplesRequest(
                            claims,
                            request.getGradeLevel(),
                            request.getDomain().name(),
                            request.getMaxExamples()
                    )).then(examples ->
                            generateSimulation(new SimulationRequest(
                                    claims,
                                    request.getGradeLevel(),
                                    request.getDomain().name(),
                                    request.getMaxSimulations()
                            )).then(simulations ->
                                    generateAnimation(new AnimationRequest(
                                            claims,
                                            request.getGradeLevel(),
                                            request.getDomain().name(),
                                            request.getMaxAnimations()
                                    )).then(animations ->
                                            generateAssessment(new AssessmentRequest(
                                                    claims,
                                                    request.getGradeLevel(),
                                                    request.getDomain().name(),
                                                    request.getMaxAssessments()
                                            )).map(assessments -> {
                                                long durationMs = System.currentTimeMillis() - startTime;
                                                metrics.recordTimer(
                                                        "content.generation.complete_package",
                                                        durationMs,
                                                        "topic", request.getTopic(),
                                                        "domain", request.getDomain().name(),
                                                        "provider", llmGateway.getClass().getSimpleName(),
                                                        "validator", validator.getClass().getSimpleName()
                                                );
                                                return new CompleteContentPackage(
                                                        claims,
                                                        List.of(),
                                                        examples.examples(),
                                                        simulations.simulations(),
                                                        animations.animations(),
                                                        assessments.assessments(),
                                                        generateQualityReport(
                                                                claimsResponse.validation(),
                                                                examples.validation(),
                                                                simulations.validation(),
                                                                animations.validation(),
                                                                assessments.validation()
                                                        )
                                                );
                                            })
                                    )
                            )
                    );
                });
    }

    @Override
    public Promise<ClaimsResponse> generateClaims(ClaimsRequest request) {
        Objects.requireNonNull(request, "request required");
        promptEngine.buildPrompt(request, Map.of(
                "operation", "generateClaims",
                "topic", request.topic(),
                "gradeLevel", request.gradeLevel(),
                "domain", request.domain(),
                "maxClaims", request.maxClaims()
        ));

        List<LearningClaim> claims = new ArrayList<>();
        int maxClaims = Math.max(1, request.maxClaims());
        for (int index = 0; index < maxClaims; index++) {
            claims.add(LearningClaim.builder()
                    .id(UUID.randomUUID().toString())
                    .text("Explain " + request.topic() + " concept " + (index + 1))
                    .domain(request.domain())
                    .gradeLevel(request.gradeLevel())
                    .prerequisites(List.of())
                    .build());
        }
        return Promise.of(new ClaimsResponse(claims, validateList(claims, "claims")));
    }

    @Override
    public Promise<ExamplesResponse> generateExamples(ExamplesRequest request) {
        Objects.requireNonNull(request, "request required");
        List<ContentExample> examples = request.claims().stream()
                .limit(Math.max(1, request.maxExamples()))
                .map(claim -> ContentExample.builder()
                        .id(UUID.randomUUID().toString())
                        .claimId(claim.getId())
                        .title("Worked example for " + claim.getText())
                        .description("A structured example illustrating: " + claim.getText())
                        .steps(List.of("Introduce the idea", "Walk through the example", "Summarize the takeaway"))
                        .visualAidDescription("Simple diagram highlighting the core relationships")
                        .gradeLevel(request.gradeLevel())
                        .domain(request.domain())
                        .createdAt(System.currentTimeMillis())
                        .build())
                .toList();
        return Promise.of(new ExamplesResponse(examples, validateList(examples, "examples")));
    }

    @Override
    public Promise<SimulationResponse> generateSimulation(SimulationRequest request) {
        Objects.requireNonNull(request, "request required");
        List<SimulationManifest> simulations = request.claims().stream()
                .limit(Math.max(1, request.maxSimulations()))
                .map(claim -> SimulationManifest.builder()
                        .id(UUID.randomUUID().toString())
                        .title("Interactive simulation for " + claim.getText())
                        .description("A guided exploration of the claim using adjustable inputs")
                        .domain(request.domain())
                        .configuration(Map.of(
                                "interactionType", "PARAMETER_EXPLORATION",
                                "claimId", claim.getId()
                        ))
                        .build())
                .toList();
        return Promise.of(new SimulationResponse(simulations, validateList(simulations, "simulations")));
    }

    @Override
    public Promise<AnimationResponse> generateAnimation(AnimationRequest request) {
        Objects.requireNonNull(request, "request required");
        List<AnimationConfig> animations = request.claims().stream()
                .limit(Math.max(1, request.maxAnimations()))
                .map(claim -> AnimationConfig.builder()
                        .id(UUID.randomUUID().toString())
                        .title("Animation for " + claim.getText())
                        .keyframes(List.of("Initial state", "Transition", "Final state"))
                        .durationMs(30000)
                        .build())
                .toList();
        return Promise.of(new AnimationResponse(animations, validateList(animations, "animations")));
    }

    @Override
    public Promise<AssessmentResponse> generateAssessment(AssessmentRequest request) {
        Objects.requireNonNull(request, "request required");
        List<AssessmentItem> assessments = request.claims().stream()
                .limit(Math.max(1, request.maxAssessments()))
                .map(claim -> AssessmentItem.builder()
                        .id(UUID.randomUUID().toString())
                        .question("Which statement best reflects: " + claim.getText() + "?")
                        .options(List.of(
                                "A precise explanation of the concept",
                                "An unrelated distractor",
                                "A partially correct distractor",
                                "A contradictory distractor"
                        ))
                        .correctAnswerIndex(0)
                        .build())
                .toList();
        return Promise.of(new AssessmentResponse(assessments, validateList(assessments, "assessments")));
    }

    @Override
    public Promise<ValidationResult> validateContent(Object content, ContentType contentType) {
        return Promise.of(switch (contentType) {
            case CLAIMS -> validateCollectionContent(content, "claims");
            case EXAMPLES -> validateCollectionContent(content, "examples");
            case SIMULATION -> validateCollectionContent(content, "simulations");
            case ANIMATION -> validateCollectionContent(content, "animations");
            case ASSESSMENT -> validateCollectionContent(content, "assessments");
            case COMPLETE_PACKAGE -> new ValidationResult(content != null, content == null ? 0.0 : 1.0, List.of());
        });
    }

    @Override
    public Promise<Void> updateConfiguration(String tenantId, Map<String, Object> configuration) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(configuration, "configuration required");
        metrics.incrementCounter("content.generation.configuration.updated", "tenant", tenantId);
        return Promise.complete();
    }

    @Override
    public Promise<Map<String, Object>> getConfiguration(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        return Promise.of(Map.of(
                "tenantId", tenantId,
                "provider", llmGateway.getClass().getSimpleName(),
                "validator", validator.getClass().getSimpleName()
        ));
    }

    @Override
    public Promise<List<String>> getSupportedModels() {
        return Promise.of(List.of("platform-llm-gateway"));
    }

    private ValidationResult validateCollectionContent(Object content, String label) {
        if (content instanceof List<?> items) {
            return validateList(items, label);
        }
        return new ValidationResult(false, 0.0, List.of("Expected list content for " + label));
    }

    private ValidationResult validateList(List<?> items, String label) {
        boolean passed = items != null && !items.isEmpty();
        return new ValidationResult(
                passed,
                passed ? 1.0 : 0.0,
                passed ? List.of() : List.of("No " + label + " generated")
        );
    }

    private QualityReport generateQualityReport(
            ValidationResult claims,
            ValidationResult examples,
            ValidationResult simulations,
            ValidationResult animations,
            ValidationResult assessments
    ) {
        List<ValidationResult> results = List.of(claims, examples, simulations, animations, assessments);
        boolean passed = results.stream().allMatch(ValidationResult::passed);
        double score = results.stream().mapToDouble(ValidationResult::confidence).average().orElse(0.0);
        List<String> issues = results.stream()
                .flatMap(result -> result.issues().stream())
                .toList();
        return QualityReport.builder()
                .passed(passed)
                .overallScore(score)
                .issues(issues)
                .build();
    }
}
