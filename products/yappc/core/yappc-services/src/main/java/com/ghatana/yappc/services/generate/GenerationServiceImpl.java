package com.ghatana.yappc.services.generate;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.common.AiQualityTelemetry;
import com.ghatana.yappc.common.ServiceObservability;
import com.ghatana.yappc.domain.generate.*;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose AI-assisted artifact generation with diff support
 * @doc.layer service
 * @doc.pattern Service
 */
public class GenerationServiceImpl implements GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationServiceImpl.class);

    private final CompletionService aiService;
    private final AuditLogger auditLogger;
    private final MetricsCollector metrics;

    public GenerationServiceImpl(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        this.aiService = aiService;
        this.auditLogger = auditLogger;
        this.metrics = metrics;
    }

    @Override
    public Promise<GeneratedArtifacts> generate(ValidatedSpec spec) {
        long startTime = System.currentTimeMillis();

        return generateArtifactsWithAI(spec)
                .then(artifacts -> {
                    long duration = System.currentTimeMillis() - startTime;
                    Map<String, String> tags = ServiceObservability.tenantTag(spec.shapeSpec().tenantId());
                    metrics.recordTimer("yappc.generate.execute", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.generate.execute", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("generate.execute", spec, artifacts))
                            .map(v -> artifacts);
                })
                .whenException(e -> {
                    log.error("Generation failed", e);
                    ServiceObservability.incrementFailure(
                        metrics,
                        "yappc.generate.execute",
                        e,
                        ServiceObservability.tenantTag(spec.shapeSpec().tenantId()));
                });
    }

    @Override
    public Promise<DiffResult> regenerateWithDiff(ValidatedSpec spec, GeneratedArtifacts existing) {
        long startTime = System.currentTimeMillis();

        return generate(spec)
                .then(newArtifacts -> {
                    DiffResult diff = computeDiff(existing, newArtifacts);

                    long duration = System.currentTimeMillis() - startTime;
                    Map<String, String> tags = ServiceObservability.tenantTag(spec.shapeSpec().tenantId());
                    metrics.recordTimer("yappc.generate.diff", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.generate.diff", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("generate.diff", spec, diff))
                            .map(v -> diff);
                })
                .whenException(e -> {
                    log.error("Diff generation failed", e);
                    ServiceObservability.incrementFailure(
                        metrics,
                        "yappc.generate.diff",
                        e,
                        ServiceObservability.tenantTag(spec.shapeSpec().tenantId()));
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
            .whenException(e -> {
                log.error("Generation review decision failed", e);
                ServiceObservability.incrementFailure(metrics, metricName, e, tags);
            });
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

        return completeWithTelemetry(
            "yappc.ai.generate.entity",
            prompt,
            0.1,
            2000,
            "public class " + entity.name() + " {}",
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

        return completeWithTelemetry(
            "yappc.ai.generate.config",
            prompt,
            0.1,
            1000,
            "server:\n  port: 8080\n",
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

        return completeWithTelemetry(
            "yappc.ai.generate.docs",
            prompt,
            0.2,
            1500,
            "# README\n\nGenerated documentation placeholder.\n",
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

        return completeWithTelemetry(
            "yappc.ai.generate.pipeline",
            prompt,
            0.1,
            1000,
            "name: ci\n\non: [push]\n",
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

        // Simple diff computation - in production, use proper diff algorithm
        newArtifacts.artifacts().forEach(newArtifact -> {
            oldArtifacts.artifacts().stream()
                    .filter(old -> old.name().equals(newArtifact.name()))
                    .findFirst()
                    .ifPresentOrElse(
                            old -> diffs.add(ArtifactDiff.builder()
                                    .artifactId(newArtifact.id())
                                    .changeType("modified")
                                    .oldContentRef(old.contentRef())
                                    .newContentRef(newArtifact.contentRef())
                                    .diffText("Content changed")
                                    .build()),
                            () -> diffs.add(ArtifactDiff.builder()
                                    .artifactId(newArtifact.id())
                                    .changeType("added")
                                    .oldContentRef(null)
                                    .newContentRef(newArtifact.contentRef())
                                    .diffText("New artifact")
                                    .build())
                    );
        });

        return DiffResult.builder()
                .newArtifacts(newArtifacts)
                .oldArtifacts(oldArtifacts)
                .diffs(diffs)
                .build();
    }

}
