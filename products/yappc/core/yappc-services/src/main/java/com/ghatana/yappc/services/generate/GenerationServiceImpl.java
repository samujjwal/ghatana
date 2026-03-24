package com.ghatana.yappc.services.generate;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
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
                    metrics.recordTimer("yappc.generate.execute", duration,
                        Map.of("tenant", spec.shapeSpec().tenantId() != null ? 
                            spec.shapeSpec().tenantId() : "unknown"));
                    
                    return auditLogger.log(createAuditEvent("generate.execute", spec, artifacts))
                            .map(v -> artifacts);
                })
                .whenException(e -> {
                    log.error("Generation failed", e);
                    metrics.incrementCounter("yappc.generate.error",
                        Map.of("error", e.getClass().getSimpleName()));
                });
    }
    
    @Override
    public Promise<DiffResult> regenerateWithDiff(ValidatedSpec spec, GeneratedArtifacts existing) {
        long startTime = System.currentTimeMillis();
        
        return generate(spec)
                .then(newArtifacts -> {
                    DiffResult diff = computeDiff(existing, newArtifacts);
                    
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("yappc.generate.diff", duration,
                        Map.of("tenant", spec.shapeSpec().tenantId() != null ? 
                            spec.shapeSpec().tenantId() : "unknown"));
                    
                    return auditLogger.log(createAuditEvent("generate.diff", spec, diff))
                            .map(v -> diff);
                })
                .whenException(e -> {
                    log.error("Diff generation failed", e);
                    metrics.incrementCounter("yappc.generate.diff.error",
                        Map.of("error", e.getClass().getSimpleName()));
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
        
        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.1)
                .maxTokens(2000)
                .build())
                .map(result -> Artifact.builder()
                        .id(UUID.randomUUID().toString())
                        .name(entity.name() + ".java")
                        .type("code")
                        .language("java")
                        .path("src/main/java/domain/" + entity.name() + ".java")
                        .contentRef("artifact-" + UUID.randomUUID())
                        .sizeBytes(result.text().length())
                        .build());
    }
    
    private Promise<Artifact> generateConfiguration(ValidatedSpec spec) {
        String prompt = buildConfigurationPrompt(spec);
        
        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.1)
                .maxTokens(1000)
                .build())
                .map(result -> Artifact.builder()
                        .id(UUID.randomUUID().toString())
                        .name("application.yml")
                        .type("config")
                        .language("yaml")
                        .path("src/main/resources/application.yml")
                        .contentRef("artifact-" + UUID.randomUUID())
                        .sizeBytes(result.text().length())
                        .build());
    }
    
    private Promise<Artifact> generateDocumentation(ValidatedSpec spec) {
        String prompt = buildDocumentationPrompt(spec);
        
        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.2)
                .maxTokens(1500)
                .build())
                .map(result -> Artifact.builder()
                        .id(UUID.randomUUID().toString())
                        .name("README.md")
                        .type("documentation")
                        .language("markdown")
                        .path("README.md")
                        .contentRef("artifact-" + UUID.randomUUID())
                        .sizeBytes(result.text().length())
                        .build());
    }
    
    private Promise<Artifact> generateCIPipeline(ValidatedSpec spec) {
        String prompt = buildCIPipelinePrompt(spec);
        
        return aiService.complete(CompletionRequest.builder()
                .prompt(prompt)
                .temperature(0.1)
                .maxTokens(1000)
                .build())
                .map(result -> Artifact.builder()
                        .id(UUID.randomUUID().toString())
                        .name("ci.yml")
                        .type("pipeline")
                        .language("yaml")
                        .path(".github/workflows/ci.yml")
                        .contentRef("artifact-" + UUID.randomUUID())
                        .sizeBytes(result.text().length())
                        .build());
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
    
    private Map<String, Object> createAuditEvent(String action, Object input, Object output) {
        return Map.of(
            "action", action,
            "timestamp", Instant.now().toEpochMilli(),
            "input", input.toString(),
            "output", output.toString()
        );
    }
}
