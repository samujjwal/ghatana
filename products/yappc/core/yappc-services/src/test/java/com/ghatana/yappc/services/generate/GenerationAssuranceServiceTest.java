package com.ghatana.yappc.services.generate;

import com.ghatana.yappc.domain.generate.Artifact;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import com.ghatana.yappc.domain.shape.DomainModel;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies deterministic Generate assurance checks
 * @doc.layer test
 * @doc.pattern Test
 */
class GenerationAssuranceServiceTest {

    private final GenerationAssuranceService service = new GenerationAssuranceService();

    @Test
    void passesCompleteBackendArtifacts() {
        GenerationAssuranceService.GenerationAssuranceReport report =
                service.assure(validatedSpec(), generatedArtifacts(List.of(
                        artifact("User.java", "code", "java", "src/main/java/domain/User.java", 120),
                        artifact("application.yml", "config", "yaml", "src/main/resources/application.yml", 80),
                        artifact("ci.yml", "pipeline", "yaml", ".github/workflows/ci.yml", 100)
                ), Map.of()));

        assertThat(report.passed()).isTrue();
        assertThat(report.failedCheckIds()).isEmpty();
        assertThat(report.passedCheckIds()).contains("compile", "test", "static", "security", "i18n", "a11y");
    }

    @Test
    void failsUnsafePathAndEmptyArtifact() {
        GenerationAssuranceService.GenerationAssuranceReport report =
                service.assure(validatedSpec(), generatedArtifacts(List.of(
                        artifact("User.java", "code", "java", "../User.java", 0),
                        artifact("ci.yml", "pipeline", "yaml", ".github/workflows/ci.yml", 100)
                ), Map.of()));

        assertThat(report.passed()).isFalse();
        assertThat(report.failedCheckIds()).contains("compile", "security");
    }

    @Test
    void requiresI18nAndA11yMetadataForWebArtifacts() {
        GenerationAssuranceService.GenerationAssuranceReport report =
                service.assure(validatedSpec(), generatedArtifacts(List.of(
                        artifact("TaskCard.tsx", "ui-component", "typescript", "src/components/TaskCard.tsx", 140),
                        artifact("ci.yml", "pipeline", "yaml", ".github/workflows/ci.yml", 100)
                ), Map.of()));

        assertThat(report.passed()).isFalse();
        assertThat(report.failedCheckIds()).contains("i18n", "a11y");
    }

    private static ValidatedSpec validatedSpec() {
        return ValidatedSpec.of(
                ShapeSpec.builder()
                        .id("shape-123")
                        .tenantId("tenant-123")
                        .domainModel(DomainModel.builder()
                                .entities(List.of())
                                .relationships(List.of())
                                .boundedContexts(List.of())
                                .build())
                        .build(),
                LifecycleValidationResult.builder().build());
    }

    private static GeneratedArtifacts generatedArtifacts(List<Artifact> artifacts, Map<String, String> metadata) {
        return GeneratedArtifacts.builder()
                .id("generated-123")
                .specRef("shape-123")
                .artifacts(artifacts)
                .metadata(metadata)
                .build();
    }

    private static Artifact artifact(String name, String type, String language, String path, long sizeBytes) {
        return Artifact.builder()
                .id("artifact-" + name)
                .name(name)
                .type(type)
                .language(language)
                .path(path)
                .contentRef("content-" + name)
                .sizeBytes(sizeBytes)
                .build();
    }
}
