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
@DisplayName("AepQuickStartTemplates")
class AepQuickStartTemplatesTest {

    private static final PipelineSpecValidator VALIDATOR = new PipelineSpecValidator();

    // ─── fraudDetection ───────────────────────────────────────────────────────

    @Test
    @DisplayName("fraudDetection: builds a valid 4-stage pipeline")
    void fraudDetection_buildsValidSpec() {
        PipelineSpec spec = AepQuickStartTemplates
                .fraudDetection("fraud-pipe", "tenant-alpha")
                .build();

        assertThat(spec.name()).isEqualTo("fraud-pipe");
        assertThat(spec.tenantId()).isEqualTo("tenant-alpha");
        assertThat(spec.stages()).hasSize(4);
        assertThat(VALIDATOR.validate(spec).isValid()).isTrue();
    }

    @Test
    @DisplayName("fraudDetection: stage IDs include expected service names")
    void fraudDetection_stageIds_correct() {
        PipelineSpec spec = AepQuickStartTemplates
                .fraudDetection("fp", "t1")
                .build();

        var ids = spec.stages().stream().map(PipelineStageSpec::stageId).toList();
        assertThat(ids).containsExactly(
                "event-ingest", "feature-extraction", "anomaly-detector", "fraud-alert-sink");
    }

    @Test
    @DisplayName("fraudDetection: null name throws NullPointerException")
    void fraudDetection_nullName_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> AepQuickStartTemplates.fraudDetection(null, "t1"));
    }

    @Test
    @DisplayName("fraudDetection: null tenantId throws NullPointerException")
    void fraudDetection_nullTenantId_throwsNPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> AepQuickStartTemplates.fraudDetection("fp", null));
    }

    // ─── clickstreamAnalytics ─────────────────────────────────────────────────

    @Test
    @DisplayName("clickstreamAnalytics: builds a valid 4-stage pipeline")
    void clickstreamAnalytics_buildsValidSpec() {
        PipelineSpec spec = AepQuickStartTemplates
                .clickstreamAnalytics("cs-pipe", "tenant-beta")
                .build();

        assertThat(spec.name()).isEqualTo("cs-pipe");
        assertThat(spec.stages()).hasSize(4);
        assertThat(VALIDATOR.validate(spec).isValid()).isTrue();
    }

    @Test
    @DisplayName("clickstreamAnalytics: stage types correct order")
    void clickstreamAnalytics_stageTypes_correct() {
        PipelineSpec spec = AepQuickStartTemplates
                .clickstreamAnalytics("cs", "t1")
                .build();

        var types = spec.stages().stream().map(PipelineStageSpec::stageType).toList();
        assertThat(types).containsExactly(
                "KAFKA_SOURCE", "ENRICHMENT", "WINDOWED_AGGREGATION", "SINK");
    }

    // ─── iotTelemetry ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("iotTelemetry: builds a valid 4-stage pipeline")
    void iotTelemetry_buildsValidSpec() {
        PipelineSpec spec = AepQuickStartTemplates
                .iotTelemetry("iot-pipe", "tenant-gamma")
                .build();

        assertThat(spec.stages()).hasSize(4);
        assertThat(VALIDATOR.validate(spec).isValid()).isTrue();
    }

    @Test
    @DisplayName("iotTelemetry: first stage is MQTT_SOURCE")
    void iotTelemetry_firstStage_isMqttSource() {
        PipelineSpec spec = AepQuickStartTemplates
                .iotTelemetry("iot", "t1")
                .build();

        assertThat(spec.stages().get(0).stageType()).isEqualTo("MQTT_SOURCE");
    }

    // ─── auditLogPipeline ─────────────────────────────────────────────────────

    @Test
    @DisplayName("auditLogPipeline: builds a valid 4-stage pipeline")
    void auditLogPipeline_buildsValidSpec() {
        PipelineSpec spec = AepQuickStartTemplates
                .auditLogPipeline("audit-pipe", "tenant-delta")
                .build();

        assertThat(spec.stages()).hasSize(4);
        assertThat(VALIDATOR.validate(spec).isValid()).isTrue();
    }

    @Test
    @DisplayName("auditLogPipeline: uses EXACTLY_ONCE strategy for compliance")
    void auditLogPipeline_usesExactlyOnce() {
        PipelineSpec spec = AepQuickStartTemplates
                .auditLogPipeline("audit", "t1")
                .build();

        long exactlyOnceStages = spec.stages().stream()
                .filter(s -> "EXACTLY_ONCE".equals(s.configuration().executionStrategy()))
                .count();
        assertThat(exactlyOnceStages).isEqualTo(4);
    }

    // ─── multiTenantRouter ────────────────────────────────────────────────────

    @Test
    @DisplayName("multiTenantRouter: builds a valid 4-stage pipeline")
    void multiTenantRouter_buildsValidSpec() {
        PipelineSpec spec = AepQuickStartTemplates
                .multiTenantRouter("mt-router", "platform")
                .build();

        assertThat(spec.stages()).hasSize(4);
        assertThat(VALIDATOR.validate(spec).isValid()).isTrue();
    }

    @Test
    @DisplayName("multiTenantRouter: stage IDs correct")
    void multiTenantRouter_stageIds_correct() {
        PipelineSpec spec = AepQuickStartTemplates
                .multiTenantRouter("mtr", "platform")
                .build();

        var ids = spec.stages().stream().map(PipelineStageSpec::stageId).toList();
        assertThat(ids).containsExactly(
                "shared-ingest", "tenant-classifier", "tenant-router", "per-tenant-sink");
    }

    // ─── all templates pass validator ─────────────────────────────────────────

    static Stream<PipelineSpec> allTemplates() {
        return Stream.of(
                AepQuickStartTemplates.fraudDetection("f", "t").build(),
                AepQuickStartTemplates.clickstreamAnalytics("c", "t").build(),
                AepQuickStartTemplates.iotTelemetry("i", "t").build(),
                AepQuickStartTemplates.auditLogPipeline("a", "t").build(),
                AepQuickStartTemplates.multiTenantRouter("m", "t").build()
        );
    }

    @ParameterizedTest(name = "template ''{0}'' validates without errors")
    @MethodSource("allTemplates")
    @DisplayName("every quick-start template produces a spec that passes static validation")
    void allTemplates_passValidation(PipelineSpec spec) {
        PipelineSpecValidator.ValidationReport report = VALIDATOR.validate(spec);
        assertThat(report.isValid())
                .as("validation errors: %s", report.errors())
                .isTrue();
    }
}
