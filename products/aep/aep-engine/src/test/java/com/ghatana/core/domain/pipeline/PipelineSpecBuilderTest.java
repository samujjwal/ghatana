/*
 * Copyright (c) 2026 Ghatana Inc. 
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
        void create_nameOnly_generatesId() { 
            PipelineSpec spec = PipelineSpecBuilder.create("my-pipeline")
                    .forTenant("tenant-1")
                    .build(); 

            assertThat(spec.getName()).isEqualTo("my-pipeline");
            assertThat(spec.getId()).isNotBlank(); 
        }

        @Test
        @DisplayName("creates builder with explicit ID and name")
        void create_explicitId_usesGivenId() { 
            PipelineSpec spec = PipelineSpecBuilder.create("pipe-001", "explicit-name") 
                    .forTenant("tenant-1")
                    .build(); 

            assertThat(spec.getId()).isEqualTo("pipe-001");
            assertThat(spec.getName()).isEqualTo("explicit-name");
        }

        @Test
        @DisplayName("throws when name is null")
        void create_nullName_throws() { 
            assertThatThrownBy(() -> PipelineSpecBuilder.create(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }

    @Nested
    @DisplayName("forTenant() and required fields")
    class RequiredFields {

        @Test
        @DisplayName("build() fails when tenantId is not set")
        void build_noTenant_throws() { 
            assertThatThrownBy(() -> PipelineSpecBuilder.create("no-tenant-pipeline").build())
                    .isInstanceOf(IllegalStateException.class) 
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("sets tenant correctly")
        void forTenant_setsTenantId() { 
            PipelineSpec spec = PipelineSpecBuilder.create("my-pipeline")
                    .forTenant("tenant-alpha")
                    .build(); 

            assertThat(spec.getTenantId()).isEqualTo("tenant-alpha");
        }
    }

    @Nested
    @DisplayName("optional fields")
    class OptionalFields {

        @Test
        @DisplayName("description is optional — defaults to null")
        void build_noDescription_null() { 
            PipelineSpec spec = PipelineSpecBuilder.create("p1")
                    .forTenant("t1")
                    .build(); 

            assertThat(spec.getDescription()).isNull(); 
        }

        @Test
        @DisplayName("describedAs() sets description")
        void describedAs_setsDescription() { 
            PipelineSpec spec = PipelineSpecBuilder.create("p1")
                    .forTenant("t1")
                    .describedAs("A test pipeline")
                    .build(); 

            assertThat(spec.getDescription()).isEqualTo("A test pipeline");
        }

        @Test
        @DisplayName("pipeline is enabled by default")
        void enabled_default() { 
            PipelineSpec spec = PipelineSpecBuilder.create("p1").forTenant("t1").build();

            assertThat(spec.isEnabled()).isTrue(); 
        }

        @Test
        @DisplayName("disabled() marks pipeline as disabled")
        void disabled_setsDisabled() { 
            PipelineSpec spec = PipelineSpecBuilder.create("p1")
                    .forTenant("t1")
                    .disabled() 
                    .build(); 

            assertThat(spec.isEnabled()).isFalse(); 
        }
    }

    @Nested
    @DisplayName("stages")
    class Stages {

        @Test
        @DisplayName("pipeline with no stages has empty stage list")
        void build_noStages_emptyList() { 
            PipelineSpec spec = PipelineSpecBuilder.create("p1").forTenant("t1").build();

            assertThat(spec.getStages()).isEmpty(); 
        }

        @Test
        @DisplayName("addStage() appends stages in order")
        void addStage_appendsInOrder() { 
            PipelineStageSpec stage1 = PipelineStageSpecBuilder.create("s1", "ingest") 
                    .ofType("SOURCE")
                    .build(); 
            PipelineStageSpec stage2 = PipelineStageSpecBuilder.create("s2", "process") 
                    .ofType("ENRICHMENT")
                    .build(); 

            PipelineSpec spec = PipelineSpecBuilder.create("p1")
                    .forTenant("t1")
                    .addStage(stage1) 
                    .addStage(stage2) 
                    .build(); 

            assertThat(spec.getStages()).hasSize(2); 
            assertThat(spec.getStages().get(0).getId()).isEqualTo("s1");
            assertThat(spec.getStages().get(1).getId()).isEqualTo("s2");
        }
    }

    @Nested
    @DisplayName("withConfiguration()")
    class Configuration {

        @Test
        @DisplayName("uses defaults when withConfiguration() is not called")
        void build_defaultConfig() { 
            PipelineSpec spec = PipelineSpecBuilder.create("p1").forTenant("t1").build();

            assertThat(spec.getConfiguration()).isNotNull(); 
            assertThat(spec.getConfiguration().getMaxRetries()).isGreaterThanOrEqualTo(0); 
        }

        @Test
        @DisplayName("applies all configuration fields correctly")
        void withConfiguration_appliesValues() { 
            PipelineSpec spec = PipelineSpecBuilder.create("p1")
                    .forTenant("t1")
                    .withConfiguration(cfg -> cfg 
                            .maxRetries(5) 
                            .timeoutMs(10_000L) 
                            .executionMode("BATCH")
                            .checkpointing(true)) 
                    .build(); 

            PipelineSpec.PipelineConfiguration config = spec.getConfiguration(); 
            assertThat(config.getMaxRetries()).isEqualTo(5); 
            assertThat(config.getTimeoutMs()).isEqualTo(10_000L); 
            assertThat(config.getExecutionMode()).isEqualTo("BATCH");
            assertThat(config.isCheckpointing()).isTrue(); 
        }

        @Test
        @DisplayName("maxRetries < 0 throws")
        void configBuilder_negativeRetries_throws() { 
            assertThatThrownBy(() -> 
                    PipelineSpecBuilder.create("p1")
                            .forTenant("t1")
                            .withConfiguration(cfg -> cfg.maxRetries(-1)) 
                            .build()) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("timeoutMs <= 0 throws")
        void configBuilder_zeroTimeout_throws() { 
            assertThatThrownBy(() -> 
                    PipelineSpecBuilder.create("p1")
                            .forTenant("t1")
                            .withConfiguration(cfg -> cfg.timeoutMs(0)) 
                            .build()) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }
    }

    // ─── PipelineStageSpecBuilder ─────────────────────────────────────────────

    @Nested
    @DisplayName("PipelineStageSpecBuilder")
    class StageBuilder {

        @Test
        @DisplayName("creates stage with name and auto-generated ID")
        void create_nameOnly_generatesId() { 
            PipelineStageSpec stage = PipelineStageSpecBuilder.create("enrich")
                    .ofType("ENRICHMENT")
                    .build(); 

            assertThat(stage.getName()).isEqualTo("enrich");
            assertThat(stage.getId()).isNotBlank(); 
            assertThat(stage.getStageType()).isEqualTo("ENRICHMENT");
        }

        @Test
        @DisplayName("creates stage with explicit ID")
        void create_explicitId() { 
            PipelineStageSpec stage = PipelineStageSpecBuilder.create("stage-99", "sink") 
                    .ofType("SINK")
                    .build(); 

            assertThat(stage.getId()).isEqualTo("stage-99");
        }

        @Test
        @DisplayName("build() fails when stageType is not set")
        void build_noType_throws() { 
            assertThatThrownBy(() -> 
                    PipelineStageSpecBuilder.create("no-type").build())
                    .isInstanceOf(IllegalStateException.class) 
                    .hasMessageContaining("type");
        }

        @Test
        @DisplayName("withConnector() adds connector IDs in order")
        void withConnector_appendsInOrder() { 
            PipelineStageSpec stage = PipelineStageSpecBuilder.create("ingest")
                    .ofType("SOURCE")
                    .withConnector("kafka-1")
                    .withConnector("schema-registry-1")
                    .build(); 

            assertThat(stage.getConnectorIds()).containsExactly("kafka-1", "schema-registry-1"); 
        }

        @Test
        @DisplayName("stage is enabled by default")
        void enabled_default() { 
            PipelineStageSpec stage = PipelineStageSpecBuilder.create("s")
                    .ofType("FILTER")
                    .build(); 

            assertThat(stage.isEnabled()).isTrue(); 
        }

        @Test
        @DisplayName("disabled() marks stage as disabled")
        void disabled_setsDisabled() { 
            PipelineStageSpec stage = PipelineStageSpecBuilder.create("s")
                    .ofType("FILTER")
                    .disabled() 
                    .build(); 

            assertThat(stage.isEnabled()).isFalse(); 
        }

        @Test
        @DisplayName("withConfiguration() applies stage config values")
        void withConfiguration_appliesValues() { 
            PipelineStageSpec stage = PipelineStageSpecBuilder.create("s")
                    .ofType("SOURCE")
                    .withConfiguration(cfg -> cfg 
                            .parallelism(4) 
                            .timeoutMs(2_000L) 
                            .executionStrategy("EXACTLY_ONCE")
                            .faultTolerant(false)) 
                    .build(); 

            PipelineStageSpec.StageConfiguration config = stage.getConfiguration(); 
            assertThat(config.getParallelism()).isEqualTo(4); 
            assertThat(config.getTimeoutMs()).isEqualTo(2_000L); 
            assertThat(config.getExecutionStrategy()).isEqualTo("EXACTLY_ONCE");
            assertThat(config.isFaultTolerant()).isFalse(); 
        }

        @Test
        @DisplayName("parallelism < 1 throws")
        void stageConfigBuilder_zeroParallelism_throws() { 
            assertThatThrownBy(() -> 
                    PipelineStageSpecBuilder.create("s")
                            .ofType("SOURCE")
                            .withConfiguration(cfg -> cfg.parallelism(0)) 
                            .build()) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }
    }
}
