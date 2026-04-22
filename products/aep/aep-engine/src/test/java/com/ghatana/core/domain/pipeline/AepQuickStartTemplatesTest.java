package com.ghatana.core.domain.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link AepQuickStartTemplates} — AEP-007.4.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AEP quick-start pipeline templates
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("AepQuickStartTemplates [GH-90000]")
class AepQuickStartTemplatesTest {

    private static final PipelineSpecValidator VALIDATOR = new PipelineSpecValidator(); // GH-90000

    // ─── fraudDetection ───────────────────────────────────────────────────────

    @Test
    @DisplayName("fraudDetection: builds a valid 4-stage pipeline [GH-90000]")
    void fraudDetection_buildsValidSpec() { // GH-90000
        PipelineSpec spec = AepQuickStartTemplates
                .fraudDetection("fraud-pipe", "tenant-alpha") // GH-90000
                .build(); // GH-90000

        assertThat(spec.name()).isEqualTo("fraud-pipe [GH-90000]");
        assertThat(spec.tenantId()).isEqualTo("tenant-alpha [GH-90000]");
        assertThat(spec.stages()).hasSize(4); // GH-90000
        assertThat(VALIDATOR.validate(spec).isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("fraudDetection: stage IDs include expected service names [GH-90000]")
    void fraudDetection_stageIds_correct() { // GH-90000
        PipelineSpec spec = AepQuickStartTemplates
                .fraudDetection("fp", "t1") // GH-90000
                .build(); // GH-90000

        var ids = spec.stages().stream().map(PipelineStageSpec::stageId).toList(); // GH-90000
        assertThat(ids).containsExactly( // GH-90000
                "event-ingest", "feature-extraction", "anomaly-detector", "fraud-alert-sink");
    }

    @Test
    @DisplayName("fraudDetection: null name throws NullPointerException [GH-90000]")
    void fraudDetection_nullName_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> AepQuickStartTemplates.fraudDetection(null, "t1")); // GH-90000
    }

    @Test
    @DisplayName("fraudDetection: null tenantId throws NullPointerException [GH-90000]")
    void fraudDetection_nullTenantId_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> AepQuickStartTemplates.fraudDetection("fp", null)); // GH-90000
    }

    // ─── clickstreamAnalytics ─────────────────────────────────────────────────

    @Test
    @DisplayName("clickstreamAnalytics: builds a valid 4-stage pipeline [GH-90000]")
    void clickstreamAnalytics_buildsValidSpec() { // GH-90000
        PipelineSpec spec = AepQuickStartTemplates
                .clickstreamAnalytics("cs-pipe", "tenant-beta") // GH-90000
                .build(); // GH-90000

        assertThat(spec.name()).isEqualTo("cs-pipe [GH-90000]");
        assertThat(spec.stages()).hasSize(4); // GH-90000
        assertThat(VALIDATOR.validate(spec).isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("clickstreamAnalytics: stage types correct order [GH-90000]")
    void clickstreamAnalytics_stageTypes_correct() { // GH-90000
        PipelineSpec spec = AepQuickStartTemplates
                .clickstreamAnalytics("cs", "t1") // GH-90000
                .build(); // GH-90000

        var types = spec.stages().stream().map(PipelineStageSpec::stageType).toList(); // GH-90000
        assertThat(types).containsExactly( // GH-90000
                "KAFKA_SOURCE", "ENRICHMENT", "WINDOWED_AGGREGATION", "SINK");
    }

    // ─── iotTelemetry ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("iotTelemetry: builds a valid 4-stage pipeline [GH-90000]")
    void iotTelemetry_buildsValidSpec() { // GH-90000
        PipelineSpec spec = AepQuickStartTemplates
                .iotTelemetry("iot-pipe", "tenant-gamma") // GH-90000
                .build(); // GH-90000

        assertThat(spec.stages()).hasSize(4); // GH-90000
        assertThat(VALIDATOR.validate(spec).isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("iotTelemetry: first stage is MQTT_SOURCE [GH-90000]")
    void iotTelemetry_firstStage_isMqttSource() { // GH-90000
        PipelineSpec spec = AepQuickStartTemplates
                .iotTelemetry("iot", "t1") // GH-90000
                .build(); // GH-90000

        assertThat(spec.stages().get(0).stageType()).isEqualTo("MQTT_SOURCE [GH-90000]");
    }

    // ─── auditLogPipeline ─────────────────────────────────────────────────────

    @Test
    @DisplayName("auditLogPipeline: builds a valid 4-stage pipeline [GH-90000]")
    void auditLogPipeline_buildsValidSpec() { // GH-90000
        PipelineSpec spec = AepQuickStartTemplates
                .auditLogPipeline("audit-pipe", "tenant-delta") // GH-90000
                .build(); // GH-90000

        assertThat(spec.stages()).hasSize(4); // GH-90000
        assertThat(VALIDATOR.validate(spec).isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("auditLogPipeline: uses EXACTLY_ONCE strategy for compliance [GH-90000]")
    void auditLogPipeline_usesExactlyOnce() { // GH-90000
        PipelineSpec spec = AepQuickStartTemplates
                .auditLogPipeline("audit", "t1") // GH-90000
                .build(); // GH-90000

        long exactlyOnceStages = spec.stages().stream() // GH-90000
                .filter(s -> "EXACTLY_ONCE".equals(s.configuration().executionStrategy())) // GH-90000
                .count(); // GH-90000
        assertThat(exactlyOnceStages).isEqualTo(4); // GH-90000
    }

    // ─── multiTenantRouter ────────────────────────────────────────────────────

    @Test
    @DisplayName("multiTenantRouter: builds a valid 4-stage pipeline [GH-90000]")
    void multiTenantRouter_buildsValidSpec() { // GH-90000
        PipelineSpec spec = AepQuickStartTemplates
                .multiTenantRouter("mt-router", "platform") // GH-90000
                .build(); // GH-90000

        assertThat(spec.stages()).hasSize(4); // GH-90000
        assertThat(VALIDATOR.validate(spec).isValid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("multiTenantRouter: stage IDs correct [GH-90000]")
    void multiTenantRouter_stageIds_correct() { // GH-90000
        PipelineSpec spec = AepQuickStartTemplates
                .multiTenantRouter("mtr", "platform") // GH-90000
                .build(); // GH-90000

        var ids = spec.stages().stream().map(PipelineStageSpec::stageId).toList(); // GH-90000
        assertThat(ids).containsExactly( // GH-90000
                "shared-ingest", "tenant-classifier", "tenant-router", "per-tenant-sink");
    }

    // ─── all templates pass validator ─────────────────────────────────────────

    static Stream<PipelineSpec> allTemplates() { // GH-90000
        return Stream.of( // GH-90000
                AepQuickStartTemplates.fraudDetection("f", "t").build(), // GH-90000
                AepQuickStartTemplates.clickstreamAnalytics("c", "t").build(), // GH-90000
                AepQuickStartTemplates.iotTelemetry("i", "t").build(), // GH-90000
                AepQuickStartTemplates.auditLogPipeline("a", "t").build(), // GH-90000
                AepQuickStartTemplates.multiTenantRouter("m", "t").build() // GH-90000
        );
    }

    @ParameterizedTest(name = "template ''{0}'' validates without errors") // GH-90000
    @MethodSource("allTemplates [GH-90000]")
    @DisplayName("every quick-start template produces a spec that passes static validation [GH-90000]")
    void allTemplates_passValidation(PipelineSpec spec) { // GH-90000
        PipelineSpecValidator.ValidationReport report = VALIDATOR.validate(spec); // GH-90000
        assertThat(report.isValid()) // GH-90000
                .as("validation errors: %s", report.errors()) // GH-90000
                .isTrue(); // GH-90000
    }
}
