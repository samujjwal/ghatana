/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.core.domain.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PipelineSpecBuilder} and {@link PipelineStageSpecBuilder} DSLs.
 *
 * @doc.type class
 * @doc.purpose Tests for PipelineSpecBuilder and PipelineStageSpecBuilder fluent DSLs
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("PipelineSpecBuilder")
class PipelineSpecBuilderTest {

    // ─── PipelineSpecBuilder ──────────────────────────────────────────────────

    @Nested
    @DisplayName("create() factory")
    class Create {

        @Test
        @DisplayName("creates builder with given name and auto-generated ID")
        void create_nameOnly_generatesId() { // GH-90000
            PipelineSpec spec = PipelineSpecBuilder.create("my-pipeline")
                    .forTenant("tenant-1")
                    .build(); // GH-90000

            assertThat(spec.getName()).isEqualTo("my-pipeline");
            assertThat(spec.getId()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("creates builder with explicit ID and name")
        void create_explicitId_usesGivenId() { // GH-90000
            PipelineSpec spec = PipelineSpecBuilder.create("pipe-001", "explicit-name") // GH-90000
                    .forTenant("tenant-1")
                    .build(); // GH-90000

            assertThat(spec.getId()).isEqualTo("pipe-001");
            assertThat(spec.getName()).isEqualTo("explicit-name");
        }

        @Test
        @DisplayName("throws when name is null")
        void create_nullName_throws() { // GH-90000
            assertThatThrownBy(() -> PipelineSpecBuilder.create(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("forTenant() and required fields")
    class RequiredFields {

        @Test
        @DisplayName("build() fails when tenantId is not set")
        void build_noTenant_throws() { // GH-90000
            assertThatThrownBy(() -> PipelineSpecBuilder.create("no-tenant-pipeline").build())
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("sets tenant correctly")
        void forTenant_setsTenantId() { // GH-90000
            PipelineSpec spec = PipelineSpecBuilder.create("my-pipeline")
                    .forTenant("tenant-alpha")
                    .build(); // GH-90000

            assertThat(spec.getTenantId()).isEqualTo("tenant-alpha");
        }
    }

    @Nested
    @DisplayName("optional fields")
    class OptionalFields {

        @Test
        @DisplayName("description is optional — defaults to null")
        void build_noDescription_null() { // GH-90000
            PipelineSpec spec = PipelineSpecBuilder.create("p1")
                    .forTenant("t1")
                    .build(); // GH-90000

            assertThat(spec.getDescription()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("describedAs() sets description")
        void describedAs_setsDescription() { // GH-90000
            PipelineSpec spec = PipelineSpecBuilder.create("p1")
                    .forTenant("t1")
                    .describedAs("A test pipeline")
                    .build(); // GH-90000

            assertThat(spec.getDescription()).isEqualTo("A test pipeline");
        }

        @Test
        @DisplayName("pipeline is enabled by default")
        void enabled_default() { // GH-90000
            PipelineSpec spec = PipelineSpecBuilder.create("p1").forTenant("t1").build();

            assertThat(spec.isEnabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("disabled() marks pipeline as disabled")
        void disabled_setsDisabled() { // GH-90000
            PipelineSpec spec = PipelineSpecBuilder.create("p1")
                    .forTenant("t1")
                    .disabled() // GH-90000
                    .build(); // GH-90000

            assertThat(spec.isEnabled()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("stages")
    class Stages {

        @Test
        @DisplayName("pipeline with no stages has empty stage list")
        void build_noStages_emptyList() { // GH-90000
            PipelineSpec spec = PipelineSpecBuilder.create("p1").forTenant("t1").build();

            assertThat(spec.getStages()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("addStage() appends stages in order")
        void addStage_appendsInOrder() { // GH-90000
            PipelineStageSpec stage1 = PipelineStageSpecBuilder.create("s1", "ingest") // GH-90000
                    .ofType("SOURCE")
                    .build(); // GH-90000
            PipelineStageSpec stage2 = PipelineStageSpecBuilder.create("s2", "process") // GH-90000
                    .ofType("ENRICHMENT")
                    .build(); // GH-90000

            PipelineSpec spec = PipelineSpecBuilder.create("p1")
                    .forTenant("t1")
                    .addStage(stage1) // GH-90000
                    .addStage(stage2) // GH-90000
                    .build(); // GH-90000

            assertThat(spec.getStages()).hasSize(2); // GH-90000
            assertThat(spec.getStages().get(0).getId()).isEqualTo("s1");
            assertThat(spec.getStages().get(1).getId()).isEqualTo("s2");
        }
    }

    @Nested
    @DisplayName("withConfiguration()")
    class Configuration {

        @Test
        @DisplayName("uses defaults when withConfiguration() is not called")
        void build_defaultConfig() { // GH-90000
            PipelineSpec spec = PipelineSpecBuilder.create("p1").forTenant("t1").build();

            assertThat(spec.getConfiguration()).isNotNull(); // GH-90000
            assertThat(spec.getConfiguration().getMaxRetries()).isGreaterThanOrEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("applies all configuration fields correctly")
        void withConfiguration_appliesValues() { // GH-90000
            PipelineSpec spec = PipelineSpecBuilder.create("p1")
                    .forTenant("t1")
                    .withConfiguration(cfg -> cfg // GH-90000
                            .maxRetries(5) // GH-90000
                            .timeoutMs(10_000L) // GH-90000
                            .executionMode("BATCH")
                            .checkpointing(true)) // GH-90000
                    .build(); // GH-90000

            PipelineSpec.PipelineConfiguration config = spec.getConfiguration(); // GH-90000
            assertThat(config.getMaxRetries()).isEqualTo(5); // GH-90000
            assertThat(config.getTimeoutMs()).isEqualTo(10_000L); // GH-90000
            assertThat(config.getExecutionMode()).isEqualTo("BATCH");
            assertThat(config.isCheckpointing()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("maxRetries < 0 throws")
        void configBuilder_negativeRetries_throws() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    PipelineSpecBuilder.create("p1")
                            .forTenant("t1")
                            .withConfiguration(cfg -> cfg.maxRetries(-1)) // GH-90000
                            .build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("timeoutMs <= 0 throws")
        void configBuilder_zeroTimeout_throws() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    PipelineSpecBuilder.create("p1")
                            .forTenant("t1")
                            .withConfiguration(cfg -> cfg.timeoutMs(0)) // GH-90000
                            .build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ─── PipelineStageSpecBuilder ─────────────────────────────────────────────

    @Nested
    @DisplayName("PipelineStageSpecBuilder")
    class StageBuilder {

        @Test
        @DisplayName("creates stage with name and auto-generated ID")
        void create_nameOnly_generatesId() { // GH-90000
            PipelineStageSpec stage = PipelineStageSpecBuilder.create("enrich")
                    .ofType("ENRICHMENT")
                    .build(); // GH-90000

            assertThat(stage.getName()).isEqualTo("enrich");
            assertThat(stage.getId()).isNotBlank(); // GH-90000
            assertThat(stage.getStageType()).isEqualTo("ENRICHMENT");
        }

        @Test
        @DisplayName("creates stage with explicit ID")
        void create_explicitId() { // GH-90000
            PipelineStageSpec stage = PipelineStageSpecBuilder.create("stage-99", "sink") // GH-90000
                    .ofType("SINK")
                    .build(); // GH-90000

            assertThat(stage.getId()).isEqualTo("stage-99");
        }

        @Test
        @DisplayName("build() fails when stageType is not set")
        void build_noType_throws() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    PipelineStageSpecBuilder.create("no-type").build())
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("type");
        }

        @Test
        @DisplayName("withConnector() adds connector IDs in order")
        void withConnector_appendsInOrder() { // GH-90000
            PipelineStageSpec stage = PipelineStageSpecBuilder.create("ingest")
                    .ofType("SOURCE")
                    .withConnector("kafka-1")
                    .withConnector("schema-registry-1")
                    .build(); // GH-90000

            assertThat(stage.getConnectorIds()).containsExactly("kafka-1", "schema-registry-1"); // GH-90000
        }

        @Test
        @DisplayName("stage is enabled by default")
        void enabled_default() { // GH-90000
            PipelineStageSpec stage = PipelineStageSpecBuilder.create("s")
                    .ofType("FILTER")
                    .build(); // GH-90000

            assertThat(stage.isEnabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("disabled() marks stage as disabled")
        void disabled_setsDisabled() { // GH-90000
            PipelineStageSpec stage = PipelineStageSpecBuilder.create("s")
                    .ofType("FILTER")
                    .disabled() // GH-90000
                    .build(); // GH-90000

            assertThat(stage.isEnabled()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("withConfiguration() applies stage config values")
        void withConfiguration_appliesValues() { // GH-90000
            PipelineStageSpec stage = PipelineStageSpecBuilder.create("s")
                    .ofType("SOURCE")
                    .withConfiguration(cfg -> cfg // GH-90000
                            .parallelism(4) // GH-90000
                            .timeoutMs(2_000L) // GH-90000
                            .executionStrategy("EXACTLY_ONCE")
                            .faultTolerant(false)) // GH-90000
                    .build(); // GH-90000

            PipelineStageSpec.StageConfiguration config = stage.getConfiguration(); // GH-90000
            assertThat(config.getParallelism()).isEqualTo(4); // GH-90000
            assertThat(config.getTimeoutMs()).isEqualTo(2_000L); // GH-90000
            assertThat(config.getExecutionStrategy()).isEqualTo("EXACTLY_ONCE");
            assertThat(config.isFaultTolerant()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("parallelism < 1 throws")
        void stageConfigBuilder_zeroParallelism_throws() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    PipelineStageSpecBuilder.create("s")
                            .ofType("SOURCE")
                            .withConfiguration(cfg -> cfg.parallelism(0)) // GH-90000
                            .build()) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }
}
