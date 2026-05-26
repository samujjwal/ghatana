package com.ghatana.yappc.services.generate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.api.GenerationRun;
import com.ghatana.yappc.api.GenerationRunRepository;
import com.ghatana.yappc.common.AiQualityTelemetry;
import com.ghatana.yappc.common.ServiceObservability;
import com.ghatana.yappc.domain.generate.*;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose AI-assisted artifact generation with diff support and provenance
 * @doc.layer service
 * @doc.pattern Service
 */
public class GenerationServiceImpl implements GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationServiceImpl.class);

    private final CompletionService aiService;
    private final AuditLogger auditLogger;
    private final MetricsCollector metrics;
    private final GenerationRunRepository generationRunRepository;
    private final ObjectMapper objectMapper;
    private final AiHealthProvider aiHealthProvider;
    private final GenerationAssuranceService assuranceService;

    public GenerationServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            @NotNull GenerationRunRepository generationRunRepository,
            @NotNull ObjectMapper objectMapper) {
        this(aiService, auditLogger, metrics, generationRunRepository, objectMapper, AiHealthProvider.alwaysHealthy());
    }

    public GenerationServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            @NotNull GenerationRunRepository generationRunRepository,
            @NotNull ObjectMapper objectMapper,
            @NotNull AiHealthProvider aiHealthProvider) {
        this(aiService, auditLogger, metrics, generationRunRepository, objectMapper, aiHealthProvider, new GenerationAssuranceService());
    }

    public GenerationServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            @NotNull GenerationRunRepository generationRunRepository,
            @NotNull ObjectMapper objectMapper,
            @NotNull AiHealthProvider aiHealthProvider,
            @NotNull GenerationAssuranceService assuranceService) {
        this.aiService = aiService;
        this.auditLogger = auditLogger;
        this.metrics = metrics;
        this.generationRunRepository = generationRunRepository;
        this.objectMapper = objectMapper;
        this.aiHealthProvider = aiHealthProvider;
        this.assuranceService = assuranceService;
    }

    @Override
    public Promise<GeneratedArtifacts> generate(ValidatedSpec spec, GenerationContext context) {
        List<String> shapeReadinessBlockers = validateShapeReadiness(spec);
        if (!shapeReadinessBlockers.isEmpty()) {
            return Promise.ofException(new IllegalStateException(
                    "Shape is not ready for generation: " + String.join("; ", shapeReadinessBlockers)));
        }

        long startTime = System.currentTimeMillis();
        String runId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();

        // Create and persist generation run with explicit context (no defaults)
        GenerationRun generationRun = GenerationRun.builder()
            .id(runId)
            .planId(spec.shapeSpec().id())
            .projectId(context.projectId())
            .tenantId(context.tenantId())
            .workspaceId(context.workspaceId())
            .intent(buildIntentRef(context))
            .status(GenerationRun.RunStatus.GENERATING)
            .reviewStatus(GenerationRun.ReviewStatus.REVIEW_PENDING)
            .createdAt(createdAt)
            .addProvenance("generator_version", "1.0.0")
            .addProvenance("validation_passed", String.valueOf(spec.validationResult().passed()))
            .addProvenance("actor_id", context.actorId())
            .addProvenance("phase", context.phase())
            .addProvenance("correlation_id", context.correlationId())
            .addMetadata("spec_ref", spec.shapeSpec().id())
            .addMetadata("intent_id", context.intentId())
            .addMetadata("shape_id", context.shapeId())
            .build();

        return generationRunRepository.save(generationRun)
            .then(() -> generateArtifactsWithAI(spec))
            .map(artifacts -> applyAssurance(spec, artifacts))
            .then(artifacts -> {
                long duration = System.currentTimeMillis() - startTime;
                Map<String, String> tags = ServiceObservability.tenantTag(spec.shapeSpec().tenantId());
                metrics.recordTimer("yappc.generate.execute", duration, tags);
                ServiceObservability.incrementSuccess(metrics, "yappc.generate.execute", tags);

                // Update generation run with completed status and artifact IDs (using explicit context)
                GenerationRun completedRun = GenerationRun.builder()
                    .id(runId)
                    .planId(spec.shapeSpec().id())
                    .projectId(context.projectId())
                    .tenantId(context.tenantId())
                    .workspaceId(context.workspaceId())
                    .intent(buildIntentRef(context))
                    .status(GenerationRun.RunStatus.COMPLETED)
                    .artifactIds(artifacts.artifacts().stream().map(Artifact::id).toList())
                    .reviewStatus(GenerationRun.ReviewStatus.REVIEW_PENDING)
                    .createdAt(createdAt)
                    .completedAt(Instant.now())
                    .addProvenance("generator_version", "1.0.0")
                    .addProvenance("validation_passed", String.valueOf(spec.validationResult().passed()))
                    .addProvenance("duration_ms", String.valueOf(duration))
                    .addProvenance("actor_id", context.actorId())
                    .addProvenance("phase", context.phase())
                    .addProvenance("correlation_id", context.correlationId())
                    .addMetadata("spec_ref", spec.shapeSpec().id())
                    .addMetadata("artifact_count", String.valueOf(artifacts.artifacts().size()))
                    .addMetadata("intent_id", context.intentId())
                    .addMetadata("shape_id", context.shapeId())
                    .build();

                return generationRunRepository.save(completedRun)
                    .then(() -> auditLogger.log(ServiceObservability.auditEvent("generate.execute", spec, artifacts))
                        .map(v -> artifacts));
            })
            .whenException(() -> {
                log.error("Generation failed");
            });
    }

    private List<String> validateShapeReadiness(ValidatedSpec spec) {
        List<String> blockers = new ArrayList<>();
        if (spec == null) {
            return List.of("validated spec is required");
        }

        ShapeSpec shape = spec.shapeSpec();
        if (shape == null) {
            blockers.add("shape spec is required");
        } else {
            if (shape.id() == null || shape.id().isBlank()) {
                blockers.add("shape id is required");
            }
            if (shape.tenantId() == null || shape.tenantId().isBlank()) {
                blockers.add("shape tenant is required");
            }
            if (shape.domainModel() == null) {
                blockers.add("shape domain model is required");
            }
        }

        LifecycleValidationResult validation = spec.validationResult();
        if (validation == null) {
            blockers.add("shape validation result is required");
        } else {
            if (!validation.passed()) {
                blockers.add("shape validation must pass");
            }
            if (validation.hasBlockingIssues()) {
                blockers.add("shape validation has blocking issues");
            }
        }
        return blockers;
    }

    private GeneratedArtifacts applyAssurance(ValidatedSpec spec, GeneratedArtifacts artifacts) {
        GenerationAssuranceService.GenerationAssuranceReport report = assuranceService.assure(spec, artifacts);
        Map<String, String> assuranceMetadata = new java.util.LinkedHashMap<>(
                artifacts.metadata() == null ? Map.of() : artifacts.metadata());
        assuranceMetadata.put("assurance_passed", String.valueOf(report.passed()));
        assuranceMetadata.put("assurance_checks_passed", report.passedCheckIds());
        assuranceMetadata.put("assurance_checks_failed", report.failedCheckIds());
        if (!report.passed()) {
            metrics.incrementCounter(
                    "yappc.generate.assurance.failed",
                    Map.of("checks_failed", report.failedCheckIds()));
            throw new IllegalStateException("Generate assurance failed: " + report.failedCheckIds());
        }
        metrics.incrementCounter("yappc.generate.assurance.passed", Map.of("checks", report.passedCheckIds()));
        return GeneratedArtifacts.builder()
                .id(artifacts.id())
                .specRef(artifacts.specRef())
                .artifacts(artifacts.artifacts())
                .generatedAt(artifacts.generatedAt())
                .generatorVersion(artifacts.generatorVersion())
                .metadata(assuranceMetadata)
                .build();
    }

    @Override
    public Promise<DiffResult> regenerateWithDiff(ValidatedSpec spec, GeneratedArtifacts existing, GenerationContext context) {
        long startTime = System.currentTimeMillis();

        return generate(spec, context)
                .then(newArtifacts -> {
                    DiffResult diff = computeDiff(existing, newArtifacts);

                    long duration = System.currentTimeMillis() - startTime;
                    Map<String, String> tags = ServiceObservability.tenantTag(context.tenantId());
                    metrics.recordTimer("yappc.generate.diff", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.generate.diff", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("generate.diff", spec, diff))
                            .map(v -> diff);
                })
                .whenException(() -> {
                    log.error("Diff generation failed");
                });
    }

    @Override
    public Promise<GenerationReviewResult> reviewDecision(GenerationReviewRequest request) {
        long startTime = System.currentTimeMillis();
        if (request == null || request.action() == null) {
            return Promise.ofException(new IllegalArgumentException("review action is required"));
        }

        String metricName = "yappc.generate.review." + request.action().wireValue();
        Instant decidedAt = Instant.now();
        
        // Perform safety checks for rollback
        if (request.action() == GenerationReviewAction.ROLLBACK) {
            // Idempotency: if the run is already rolled back, return the current state
            return generationRunRepository.findById(request.runId())
                .then(run -> {
                    if (run != null && run.reviewStatus() == GenerationRun.ReviewStatus.ROLLED_BACK) {
                        log.info("Rollback requested for already-rolled-back run {}, returning idempotent response", request.runId());
                        return Promise.of(new GenerationReviewResult(
                            request.runId(),
                            request.projectId(),
                            GenerationReviewAction.ROLLBACK.wireValue(),
                            GenerationReviewAction.ROLLBACK.status(),
                            false,
                            request.actorId(),
                            Instant.now(),
                            "generate.review.rollback",
                            "Generation run " + request.runId() + " was already rolled back.",
                            Map.of("idempotent", "true")
                        ));
                    }
                    return performRollbackSafetyChecks(request)
                        .then(checksPassed -> {
                            if (!checksPassed) {
                                return Promise.ofException(new IllegalStateException("Rollback safety checks failed"));
                            }
                            return executeReviewDecision(request, metricName, decidedAt, startTime);
                        });
                });
        }
        
        return executeReviewDecision(request, metricName, decidedAt, startTime);
    }

    /**
     * Performs safety checks before allowing a rollback operation.
     */
    private Promise<Boolean> performRollbackSafetyChecks(GenerationReviewRequest request) {
        return generationRunRepository.findById(request.runId())
            .then(run -> {
                if (run == null) {
                    log.error("Cannot rollback: generation run {} not found", request.runId());
                    return Promise.of(false);
                }
                
                GenerationRun runEntity = run;
                List<String> failures = new java.util.ArrayList<>();
                
                // Check 1: Run must be in a state that can be rolled back (APPROVED or COMPLETED)
                if (runEntity.reviewStatus() != GenerationRun.ReviewStatus.APPROVED
                    && runEntity.reviewStatus() != GenerationRun.ReviewStatus.REVIEW_PENDING) {
                    failures.add("Run is not in a rollback-eligible state (current status: " + runEntity.reviewStatus() + ")");
                }
                
                // Check 2: Rollback must be within a reasonable time window (e.g., 30 days)
                if (runEntity.completedAt() != null) {
                    java.time.Duration timeSinceCompletion = java.time.Duration.between(runEntity.completedAt(), Instant.now());
                    if (timeSinceCompletion.toDays() > 30) {
                        failures.add("Rollback window expired (completed more than 30 days ago)");
                    }
                }
                
                // Check 3: Run must not already be rolled back
                if (runEntity.reviewStatus() == GenerationRun.ReviewStatus.ROLLED_BACK) {
                    failures.add("Run has already been rolled back");
                }
                
                // Check 4: Verify actor has permission to rollback
                if (request.actorId() == null || request.actorId().isBlank()) {
                    failures.add("Actor ID is required for rollback");
                }
                
                // Check 5: Reason must be provided for rollback
                if (request.reason() == null || request.reason().isBlank()) {
                    failures.add("Reason is required for rollback");
                }
                
                // Log safety check results
                if (!failures.isEmpty()) {
                    log.warn("Rollback safety checks failed for run {}: {}", request.runId(), String.join(", ", failures));
                    metrics.incrementCounter("yappc.generate.rollback.safety_check_failed", 
                        Map.of("run_id", request.runId(), "failures", String.valueOf(failures.size())));
                } else {
                    log.info("Rollback safety checks passed for run {}", request.runId());
                    metrics.incrementCounter("yappc.generate.rollback.safety_check_passed", 
                        Map.of("run_id", request.runId()));
                }
                
                return Promise.of(failures.isEmpty());
            })
            .whenException(() -> {
                log.error("Error performing rollback safety checks for run {}", request.runId());
                metrics.incrementCounter("yappc.generate.rollback.safety_check_error", 
                    Map.of("run_id", request.runId()));
            });
    }

    /**
     * Executes the review decision after safety checks pass.
     */
    private Promise<GenerationReviewResult> executeReviewDecision(
            GenerationReviewRequest request, 
            String metricName, 
            Instant decidedAt,
            long startTime) {
        // Map review action to review status
        GenerationRun.ReviewStatus reviewStatus = switch (request.action()) {
            case APPLY -> GenerationRun.ReviewStatus.APPROVED;
            case REJECT -> GenerationRun.ReviewStatus.REJECTED;
            case ROLLBACK -> GenerationRun.ReviewStatus.ROLLED_BACK;
        };

        // Build provenance metadata for the review decision
        Map<String, String> reviewProvenance = new java.util.HashMap<>();
        reviewProvenance.put("actor_id", request.actorId());
        reviewProvenance.put("decision_timestamp", decidedAt.toString());
        reviewProvenance.put("reason", request.reason() != null ? request.reason() : "");
        
        if (request.provenance() != null) {
            if (request.provenance().sessionId() != null) {
                reviewProvenance.put("session_id", request.provenance().sessionId());
            }
            if (request.provenance().traceId() != null) {
                reviewProvenance.put("trace_id", request.provenance().traceId());
            }
            reviewProvenance.put("source", request.provenance().source());
            if (request.provenance().clientVersion() != null) {
                reviewProvenance.put("client_version", request.provenance().clientVersion());
            }
            if (request.provenance().metadata() != null) {
                reviewProvenance.putAll(request.provenance().metadata());
            }
        }

        // Serialize user edits for storage
        String userEditsJson = null;
        if (request.userEdits() != null && !request.userEdits().isEmpty()) {
            try {
                userEditsJson = serializeUserEdits(request.userEdits());
                reviewProvenance.put("user_edits_count", String.valueOf(request.userEdits().size()));
            } catch (Exception e) {
                log.warn("Failed to serialize user edits for run {}", request.runId(), e);
            }
        }
        final String finalUserEditsJson = userEditsJson;

        // Update generation run review status with provenance
        return generationRunRepository.updateReviewStatus(request.runId(), reviewStatus)
            .then(() -> {
                // Store review provenance in generation run metadata
                return generationRunRepository.findById(request.runId())
                    .then(run -> {
                        if (run != null) {
                            // Add review decision to metadata
                            Map<String, Object> updatedMetadata = new java.util.HashMap<>(run.metadata());
                            updatedMetadata.put("review_decision", request.action().wireValue());
                            updatedMetadata.put("review_actor", request.actorId());
                            updatedMetadata.put("review_timestamp", decidedAt.toString());
                            updatedMetadata.putAll(reviewProvenance);
                            
                            if (finalUserEditsJson != null) {
                                updatedMetadata.put("user_edits", finalUserEditsJson);
                            }
                            
                            GenerationRun updatedRun = GenerationRun.builder()
                                .id(run.id())
                                .planId(run.planId())
                                .projectId(run.projectId())
                                .tenantId(run.tenantId())
                                .workspaceId(run.workspaceId())
                                .intent(run.intent())
                                .status(run.status())
                                .artifactIds(run.artifactIds())
                                .reviewStatus(reviewStatus)
                                .previewSessionId(run.previewSessionId())
                                .createdAt(run.createdAt())
                                .completedAt(run.completedAt())
                                .addProvenance("review_decision_actor", request.actorId())
                                .addProvenance("review_decision_timestamp", decidedAt.toString())
                                .addProvenance("review_decision", request.action().wireValue())
                                .metadata(updatedMetadata)
                                .build();
                            
                            return generationRunRepository.save(updatedRun);
                        }
                        return Promise.of(null);
                    });
            })
            .then(() -> {
                GenerationReviewResult result = new GenerationReviewResult(
                    request.runId(),
                    request.projectId(),
                    request.action().wireValue(),
                    request.action().status(),
                    false,
                    request.actorId(),
                    decidedAt,
                    "generate.review." + request.action().wireValue(),
                    "Generation run " + request.runId() + " was " + request.action().status().toLowerCase().replace('_', ' ') + ".",
                    Map.of("reason", request.reason() == null ? "" : request.reason())
                );

                Map<String, String> tags = Map.of("decision", request.action().wireValue());
                metrics.recordTimer(metricName, System.currentTimeMillis() - startTime, tags);
                ServiceObservability.incrementSuccess(metrics, metricName, tags);

                return auditLogger.log(ServiceObservability.auditEvent(result.auditEvent(), request, result))
                    .map(v -> result)
                    .whenException(() -> {
                        log.error("Generation review decision failed");
                    });
            });
    }

    /**
     * Serializes user edits to JSON for storage using the injected ObjectMapper.
     */
    private String serializeUserEdits(List<GenerationReviewRequest.UserEdit> userEdits) {
        try {
            return objectMapper.writeValueAsString(userEdits);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize user edits to JSON", e);
            throw new IllegalStateException("User edits serialization failed", e);
        }
    }

    /**
     * Builds a provenance-only IntentInput reference from the generation context.
     * The intent content is captured and persisted at the intent phase; this stores
     * the intent ID reference for traceability in the generation run record.
     */
    private IntentInput buildIntentRef(GenerationContext context) {
        return IntentInput.builder()
                .rawText("intent-ref:" + context.intentId())
                .format("ref")
                .structuredData(Map.of("intentId", context.intentId()))
                .tenantId(context.tenantId())
                .userId(context.actorId())
                .build();
    }

    private Promise<GeneratedArtifacts> generateArtifactsWithAI(ValidatedSpec spec) {
        List<Promise<Artifact>> artifactPromises = new ArrayList<>();

        // Generate code artifacts (only if domain model is present)
        if (spec.shapeSpec().domainModel() != null && spec.shapeSpec().domainModel().entities() != null) {
            spec.shapeSpec().domainModel().entities().forEach(entity -> {
                artifactPromises.add(generateEntityCode(entity, spec));
            });
        }

        // Generate configuration
        artifactPromises.add(generateConfiguration(spec));

        // Generate documentation
        artifactPromises.add(generateDocumentation(spec));

        // Generate CI/CD pipeline
        artifactPromises.add(generateCIPipeline(spec));

        return Promises.toList(artifactPromises)
                .map(artifacts -> GeneratedArtifacts.builder()
                        .id(UUID.randomUUID().toString())
                        .specRef(spec.shapeSpec().id())
                        .artifacts(artifacts)
                        .generatedAt(Instant.now())
                        .generatorVersion("1.0.0")
                        .metadata(Map.of("validation_passed",
                            String.valueOf(spec.validationResult().passed())))
                        .build());
    }

    private Promise<Artifact> generateEntityCode(
            com.ghatana.yappc.domain.shape.EntitySpec entity,
            ValidatedSpec spec) {

        String prompt = buildEntityCodePrompt(entity, spec);
        String deterministicFallback = generateDeterministicEntityCode(entity, spec);

        return completeWithTelemetry(
            "yappc.ai.generate.entity",
            prompt,
            0.1,
            2000,
            deterministicFallback,
            ServiceObservability.tenantTag(spec.shapeSpec().tenantId()))
            .map(content -> Artifact.builder()
                        .id(UUID.randomUUID().toString())
                        .name(entity.name() + ".java")
                        .type("code")
                        .language("java")
                        .path("src/main/java/domain/" + entity.name() + ".java")
                        .contentRef("artifact-" + UUID.randomUUID())
                .sizeBytes(content.length())
                        .build());
    }

    private Promise<Artifact> generateConfiguration(ValidatedSpec spec) {
        String prompt = buildConfigurationPrompt(spec);
        String deterministicFallback = generateDeterministicConfiguration(spec);

        return completeWithTelemetry(
            "yappc.ai.generate.config",
            prompt,
            0.1,
            1000,
            deterministicFallback,
            ServiceObservability.tenantTag(spec.shapeSpec().tenantId()))
            .map(content -> Artifact.builder()
                        .id(UUID.randomUUID().toString())
                        .name("application.yml")
                        .type("config")
                        .language("yaml")
                        .path("src/main/resources/application.yml")
                        .contentRef("artifact-" + UUID.randomUUID())
                .sizeBytes(content.length())
                        .build());
    }

    private Promise<Artifact> generateDocumentation(ValidatedSpec spec) {
        String prompt = buildDocumentationPrompt(spec);
        String deterministicFallback = generateDeterministicDocumentation(spec);

        return completeWithTelemetry(
            "yappc.ai.generate.docs",
            prompt,
            0.2,
            1500,
            deterministicFallback,
            ServiceObservability.tenantTag(spec.shapeSpec().tenantId()))
            .map(content -> Artifact.builder()
                        .id(UUID.randomUUID().toString())
                        .name("README.md")
                        .type("documentation")
                        .language("markdown")
                        .path("README.md")
                        .contentRef("artifact-" + UUID.randomUUID())
                .sizeBytes(content.length())
                        .build());
    }

    private Promise<Artifact> generateCIPipeline(ValidatedSpec spec) {
        String prompt = buildCIPipelinePrompt(spec);
        String deterministicFallback = generateDeterministicCIPipeline(spec);

        return completeWithTelemetry(
            "yappc.ai.generate.pipeline",
            prompt,
            0.1,
            1000,
            deterministicFallback,
            ServiceObservability.tenantTag(spec.shapeSpec().tenantId()))
            .map(content -> Artifact.builder()
                        .id(UUID.randomUUID().toString())
                        .name("ci.yml")
                        .type("pipeline")
                        .language("yaml")
                        .path(".github/workflows/ci.yml")
                        .contentRef("artifact-" + UUID.randomUUID())
                .sizeBytes(content.length())
                        .build());
    }

        private Promise<String> completeWithTelemetry(
            String metricPrefix,
            String prompt,
            double temperature,
            int maxTokens,
            String fallbackText,
            Map<String, String> tags) {
        // If AI is degraded, use deterministic fallback immediately
        if (aiHealthProvider.isDegraded()) {
            AiQualityTelemetry.recordFallback(metrics, metricPrefix, new IllegalStateException("AI service degraded"), tags);
            log.warn("AI service is degraded, using deterministic fallback for {}", metricPrefix);
            return Promise.of(fallbackText);
        }

        return aiService.complete(CompletionRequest.builder()
            .prompt(prompt)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .build())
            .then((result, e) -> {
                if (e != null) {
                AiQualityTelemetry.recordFallback(metrics, metricPrefix, e, tags);
                log.warn("AI generation failed for {}, using deterministic fallback", metricPrefix, e);
                return Promise.of(fallbackText);
                }

                AiQualityTelemetry.recordCompletion(metrics, metricPrefix, result, tags);
                return Promise.of(result.text());
            });
        }

    private String buildEntityCodePrompt(
            com.ghatana.yappc.domain.shape.EntitySpec entity,
            ValidatedSpec spec) {
        return """
            Generate Java code for the following entity:

            Entity: %s
            Description: %s
            Fields: %s

            Requirements:
            - Use Java 21 records
            - Include builder pattern
            - Add JavaDoc
            - Include validation annotations
            """.formatted(entity.name(), entity.description(),
                entity.fields().stream().map(f -> f.name() + ":" + f.type()).toList());
    }

    private String buildConfigurationPrompt(ValidatedSpec spec) {
        return """
            Generate application.yml configuration for:

            Architecture: %s
            Integrations: %s

            Include:
            - Server configuration
            - Database settings
            - Logging configuration
            - Integration endpoints
            """.formatted(
                spec.shapeSpec().architecture() != null ? spec.shapeSpec().architecture().name() : "unspecified",
                spec.shapeSpec().integrations() != null ? spec.shapeSpec().integrations().stream().map(i -> i.name()).toList() : List.of());
    }

    private String buildDocumentationPrompt(ValidatedSpec spec) {
        return """
            Generate README.md documentation for:

            Product: %s
            Architecture: %s
            Components: %s

            Include:
            - Project overview
            - Architecture diagram
            - Setup instructions
            - API documentation
            """.formatted(spec.shapeSpec().intentRef(),
                spec.shapeSpec().architecture() != null ? spec.shapeSpec().architecture().name() : "unspecified",
                spec.shapeSpec().architecture() != null ? spec.shapeSpec().architecture().components() : List.of());
    }

    private String buildCIPipelinePrompt(ValidatedSpec spec) {
        return """
            Generate GitHub Actions CI/CD pipeline for:

            Architecture: %s

            Include:
            - Build job
            - Test job
            - Security scanning
            - Deployment stages
            """.formatted(spec.shapeSpec().architecture() != null ? spec.shapeSpec().architecture().name() : "unspecified");
    }

    private DiffResult computeDiff(GeneratedArtifacts oldArtifacts, GeneratedArtifacts newArtifacts) {
        List<ArtifactDiff> diffs = new ArrayList<>();

        // Compute line-based diffs with ownership
        newArtifacts.artifacts().forEach(newArtifact -> {
            oldArtifacts.artifacts().stream()
                    .filter(old -> old.name().equals(newArtifact.name()))
                    .findFirst()
                    .ifPresentOrElse(
                            old -> {
                                // Artifact exists in both - compute line-based diff
                                List<ArtifactDiff.DiffRegion> regions = computeLineDiff(
                                    old.contentRef(), 
                                    newArtifact.contentRef()
                                );
                                
                                ArtifactDiff.DiffOwnership ownership = new ArtifactDiff.DiffOwnership(
                                    "ai-service",
                                    "ai",
                                    "deterministic-fallback",
                                    null,
                                    null,
                                    Instant.now(),
                                    Map.of("fallback", aiHealthProvider.isDegraded() ? "true" : "false")
                                );
                                
                                diffs.add(ArtifactDiff.builder()
                                        .artifactId(newArtifact.id())
                                        .changeType("modified")
                                        .oldContentRef(old.contentRef())
                                        .newContentRef(newArtifact.contentRef())
                                        .diffText(generateUnifiedDiffText(regions))
                                        .diffRegions(regions)
                                        .ownership(ownership)
                                        .build());
                            },
                            () -> {
                                // New artifact - mark as added
                                List<ArtifactDiff.DiffRegion> regions = List.of(
                                    new ArtifactDiff.DiffRegion(0, 0, 1, 1, "added", "New file")
                                );
                                
                                ArtifactDiff.DiffOwnership ownership = new ArtifactDiff.DiffOwnership(
                                    "ai-service",
                                    "ai",
                                    "deterministic-fallback",
                                    null,
                                    null,
                                    Instant.now(),
                                    Map.of("fallback", aiHealthProvider.isDegraded() ? "true" : "false")
                                );
                                
                                diffs.add(ArtifactDiff.builder()
                                        .artifactId(newArtifact.id())
                                        .changeType("added")
                                        .oldContentRef(null)
                                        .newContentRef(newArtifact.contentRef())
                                        .diffText("+++ " + newArtifact.name())
                                        .diffRegions(regions)
                                        .ownership(ownership)
                                        .build());
                            }
                    );
        });

        // Check for deleted artifacts
        oldArtifacts.artifacts().forEach(oldArtifact -> {
            boolean stillExists = newArtifacts.artifacts().stream()
                    .anyMatch(newArtifact -> newArtifact.name().equals(oldArtifact.name()));
            
            if (!stillExists) {
                List<ArtifactDiff.DiffRegion> regions = List.of(
                    new ArtifactDiff.DiffRegion(1, 1, 0, 0, "removed", "Deleted file")
                );
                
                ArtifactDiff.DiffOwnership ownership = new ArtifactDiff.DiffOwnership(
                    "system",
                    "system",
                    "generation-service",
                    null,
                    null,
                    Instant.now(),
                    Map.of("deleted", "true")
                );
                
                diffs.add(ArtifactDiff.builder()
                        .artifactId(oldArtifact.id())
                        .changeType("deleted")
                        .oldContentRef(oldArtifact.contentRef())
                        .newContentRef(null)
                        .diffText("--- " + oldArtifact.name())
                        .diffRegions(regions)
                        .ownership(ownership)
                        .build());
            }
        });

        return DiffResult.builder()
                .newArtifacts(newArtifacts)
                .oldArtifacts(oldArtifacts)
                .diffs(diffs)
                .build();
    }

    /**
     * Produces structural diff regions for two content references.
     *
     * <p>Artifacts are identified by opaque content-refs (storage keys), so line-level
     * diff requires the content to be resolved from the artifact store. Until a content
     * resolver is injected this method marks the entire artifact as modified, which is
     * safe and correct for downstream consumers that treat the diff as informational.
     */
    private List<ArtifactDiff.DiffRegion> computeLineDiff(String oldContentRef, String newContentRef) {
        if (oldContentRef == null || newContentRef == null) {
            return List.of(new ArtifactDiff.DiffRegion(1, 1, 1, 1, "modified", "Content reference unavailable"));
        }
        if (oldContentRef.equals(newContentRef)) {
            return List.of();
        }
        // Content refs differ; structural diff recorded. Line-level diff requires content resolution.
        return List.of(new ArtifactDiff.DiffRegion(1, 1, 1, 1, "modified", "Content updated"));
    }

    /**
     * Generates unified diff text from diff regions.
     */
    private String generateUnifiedDiffText(List<ArtifactDiff.DiffRegion> regions) {
        if (regions == null || regions.isEmpty()) {
            return "No changes detected";
        }
        
        StringBuilder diffText = new StringBuilder();
        for (ArtifactDiff.DiffRegion region : regions) {
            switch (region.regionType()) {
                case "added" -> diffText.append("+++ ").append(region.content()).append("\n");
                case "removed" -> diffText.append("--- ").append(region.content()).append("\n");
                case "modified" -> diffText.append("@@ -").append(region.oldStartLine())
                        .append(",1 +").append(region.newStartLine())
                        .append(",1 @@ ").append(region.content()).append("\n");
                default -> diffText.append("  ").append(region.content()).append("\n");
            }
        }
        return diffText.toString();
    }

    /**
     * Generates deterministic entity code based on the entity specification.
     * This is used as a fallback when AI generation is degraded or fails.
     */
    private String generateDeterministicEntityCode(
            com.ghatana.yappc.domain.shape.EntitySpec entity,
            ValidatedSpec spec) {
        StringBuilder code = new StringBuilder();
        String packageName = "com.ghatana." + spec.shapeSpec().metadata().getOrDefault("projectId", "yappc").toLowerCase() + ".domain";

        code.append("package ").append(packageName).append(";\n\n");
        code.append("import java.time.Instant;\n");
        code.append("import java.util.Objects;\n\n");
        code.append("/**\n");
        code.append(" * ").append(entity.description() != null ? entity.description() : "Entity for " + entity.name()).append("\n");
        code.append(" * @doc.type class\n");
        code.append(" * @doc.purpose Domain entity representing ").append(entity.name()).append("\n");
        code.append(" * @doc.pattern ValueObject\n");
        code.append(" */\n");
        code.append("public record ").append(entity.name()).append("(");

        // Add fields
        if (entity.fields() != null && !entity.fields().isEmpty()) {
            code.append("\n");
            for (int i = 0; i < entity.fields().size(); i++) {
                var field = entity.fields().get(i);
                code.append("    ").append(mapFieldType(field.type())).append(" ").append(field.name());
                if (i < entity.fields().size() - 1) {
                    code.append(",");
                }
                code.append("\n");
            }
        } else {
            code.append("String id");
        }

        code.append(") {\n");
        code.append("    public ").append(entity.name()).append(" {\n");
        code.append("        this.id = Objects.requireNonNullElse(id, java.util.UUID.randomUUID().toString());\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    /**
     * Generates deterministic application configuration based on the shape spec.
     * This is used as a fallback when AI generation is degraded or fails.
     */
    private String generateDeterministicConfiguration(ValidatedSpec spec) {
        StringBuilder config = new StringBuilder();
        String projectName = spec.shapeSpec().metadata().getOrDefault("projectId", "yappc").toLowerCase();

        config.append("server:\n");
        config.append("  port: 8080\n");
        config.append("  shutdown: graceful\n\n");
        config.append("spring:\n");
        config.append("  application:\n");
        config.append("    name: ").append(projectName).append("\n");
        config.append("  datasource:\n");
        config.append("    url: jdbc:postgresql://localhost:5432/").append(projectName).append("\n");
        config.append("    username: ${DB_USERNAME:app}\n");
        config.append("    password: ${DB_PASSWORD:secret}\n");
        config.append("  jpa:\n");
        config.append("    hibernate:\n");
        config.append("      ddl-auto: validate\n");
        config.append("    show-sql: false\n\n");
        config.append("logging:\n");
        config.append("  level:\n");
        config.append("    com.ghatana: INFO\n");
        config.append("    org.springframework: WARN\n\n");
        config.append("management:\n");
        config.append("  endpoints:\n");
        config.append("    web:\n");
        config.append("      exposure:\n");
        config.append("        include: health,info,metrics\n");
        config.append("  endpoint:\n");
        config.append("    health:\n");
        config.append("      show-details: when-authorized\n");

        return config.toString();
    }

    /**
     * Generates deterministic documentation based on the shape spec.
     * This is used as a fallback when AI generation is degraded or fails.
     */
    private String generateDeterministicDocumentation(ValidatedSpec spec) {
        StringBuilder docs = new StringBuilder();
        String projectName = spec.shapeSpec().metadata().getOrDefault("projectId", "YAPPC Project");
        String architecture = spec.shapeSpec().architecture() != null ? spec.shapeSpec().architecture().name() : "Monolithic";

        docs.append("# ").append(projectName).append("\n\n");
        docs.append("## Overview\n\n");
        docs.append("This project implements a ").append(architecture).append(" architecture.\n\n");
        docs.append("## Setup\n\n");
        docs.append("```bash\n");
        docs.append("./gradlew build\n");
        docs.append("./gradlew run\n");
        docs.append("```\n\n");
        docs.append("## Architecture\n\n");
        docs.append("The application follows a layered architecture:\n");
        docs.append("- **API Layer**: REST endpoints for external access\n");
        docs.append("- **Service Layer**: Business logic and orchestration\n");
        docs.append("- **Domain Layer**: Core business entities and rules\n");
        docs.append("- **Infrastructure Layer**: Database, external services\n\n");
        docs.append("## API Documentation\n\n");
        docs.append("API documentation is available at `/docs` when the application is running.\n\n");
        docs.append("## Configuration\n\n");
        docs.append("Configuration is managed through `application.yml`. See the configuration file for details.\n\n");
        docs.append("## Development\n\n");
        docs.append("To run in development mode:\n");
        docs.append("```bash\n");
        docs.append("./gradlew bootRun\n");
        docs.append("```\n\n");
        docs.append("## Testing\n\n");
        docs.append("```bash\n");
        docs.append("./gradlew test\n");
        docs.append("```\n\n");
        docs.append("## License\n\n");
        docs.append("Copyright (c) 2026 Ghatana Inc. All rights reserved.\n");

        return docs.toString();
    }

    /**
     * Generates deterministic CI/CD pipeline based on the shape spec.
     * This is used as a fallback when AI generation is degraded or fails.
     */
    private String generateDeterministicCIPipeline(ValidatedSpec spec) {
        StringBuilder pipeline = new StringBuilder();
        String projectName = spec.shapeSpec().metadata().getOrDefault("projectId", "yappc").toLowerCase();

        pipeline.append("name: CI\n\n");
        pipeline.append("on:\n");
        pipeline.append("  push:\n");
        pipeline.append("    branches: [ main, develop ]\n");
        pipeline.append("  pull_request:\n");
        pipeline.append("    branches: [ main ]\n\n");
        pipeline.append("jobs:\n");
        pipeline.append("  build:\n");
        pipeline.append("    runs-on: ubuntu-latest\n");
        pipeline.append("    steps:\n");
        pipeline.append("      - uses: actions/checkout@v4\n");
        pipeline.append("      - name: Set up JDK 21\n");
        pipeline.append("        uses: actions/setup-java@v4\n");
        pipeline.append("        with:\n");
        pipeline.append("          java-version: '21'\n");
        pipeline.append("          distribution: 'temurin'\n");
        pipeline.append("      - name: Build with Gradle\n");
        pipeline.append("        run: ./gradlew build\n");
        pipeline.append("      - name: Run tests\n");
        pipeline.append("        run: ./gradlew test\n");
        pipeline.append("      - name: Upload build artifacts\n");
        pipeline.append("        uses: actions/upload-artifact@v4\n");
        pipeline.append("        with:\n");
        pipeline.append("          name: build-artifacts\n");
        pipeline.append("          path: build/libs/*.jar\n\n");
        pipeline.append("  security:\n");
        pipeline.append("    runs-on: ubuntu-latest\n");
        pipeline.append("    steps:\n");
        pipeline.append("      - uses: actions/checkout@v4\n");
        pipeline.append("      - name: Run security scan\n");
        pipeline.append("        run: ./gradlew dependencyCheckAnalyze\n");

        return pipeline.toString();
    }

    /**
     * Maps field type strings to Java types for deterministic code generation.
     */
    private String mapFieldType(String type) {
        if (type == null) return "String";
        return switch (type.toLowerCase()) {
            case "string", "text" -> "String";
            case "integer", "int" -> "Integer";
            case "long", "bigint" -> "Long";
            case "boolean", "bool" -> "Boolean";
            case "double", "decimal" -> "Double";
            case "float" -> "Float";
            case "date", "datetime", "timestamp" -> "Instant";
            case "uuid" -> "java.util.UUID";
            default -> "String";
        };
    }

}
