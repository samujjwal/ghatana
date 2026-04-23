/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.core.domain.pipeline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PipelineTemplateLibrary}.
 *
 * @doc.type class
 * @doc.purpose Tests for pre-built pipeline template correctness
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("PipelineTemplateLibrary")
class PipelineTemplateLibraryTest {

    private final PipelineSpecValidator validator = new PipelineSpecValidator(); // GH-90000

    // ─── rawIngest ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rawIngest() template builds a valid pipeline with 2 stages")
    void rawIngest_validPipeline() { // GH-90000
        PipelineSpec spec = PipelineTemplateLibrary.rawIngest("my-ingest", "tenant-1").build(); // GH-90000

        assertThat(validator.validate(spec).isValid()).isTrue(); // GH-90000
        assertThat(spec.getStages()).hasSize(2); // GH-90000
        assertThat(spec.getStages().get(0).getStageType()).isEqualTo("KAFKA_SOURCE");
        assertThat(spec.getStages().get(1).getStageType()).isEqualTo("SINK");
    }

    @Test
    @DisplayName("rawIngest() uses the provided tenant")
    void rawIngest_setsTenant() { // GH-90000
        PipelineSpec spec = PipelineTemplateLibrary.rawIngest("pipe", "my-tenant").build(); // GH-90000

        assertThat(spec.getTenantId()).isEqualTo("my-tenant");
    }

    @Test
    @DisplayName("rawIngest() template has checkpointing enabled")
    void rawIngest_checkpointingEnabled() { // GH-90000
        PipelineSpec spec = PipelineTemplateLibrary.rawIngest("pipe", "t1").build(); // GH-90000

        assertThat(spec.getConfiguration().isCheckpointing()).isTrue(); // GH-90000
    }

    // ─── eventEnrichment ──────────────────────────────────────────────────────

    @Test
    @DisplayName("eventEnrichment() template builds a valid 3-stage pipeline")
    void eventEnrichment_validPipeline() { // GH-90000
        PipelineSpec spec = PipelineTemplateLibrary.eventEnrichment("enrich-pipe", "t1").build(); // GH-90000

        assertThat(validator.validate(spec).isValid()).isTrue(); // GH-90000
        assertThat(spec.getStages()).hasSize(3); // GH-90000
        assertThat(spec.getStages().get(1).getStageType()).isEqualTo("ENRICHMENT");
    }

    @Test
    @DisplayName("eventEnrichment() enrichment stage has highest parallelism")
    void eventEnrichment_enrichmentStageParallelism() { // GH-90000
        PipelineSpec spec = PipelineTemplateLibrary.eventEnrichment("enrich-pipe", "t1").build(); // GH-90000

        PipelineStageSpec enrichStage = spec.getStages().get(1); // GH-90000
        assertThat(enrichStage.getConfiguration().getParallelism()) // GH-90000
                .isGreaterThan(spec.getStages().get(0).getConfiguration().getParallelism()); // GH-90000
    }

    // ─── eventFilter ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("eventFilter() template builds a valid 3-stage pipeline")
    void eventFilter_validPipeline() { // GH-90000
        PipelineSpec spec = PipelineTemplateLibrary.eventFilter("filter-pipe", "t1").build(); // GH-90000

        assertThat(validator.validate(spec).isValid()).isTrue(); // GH-90000
        assertThat(spec.getStages()).hasSize(3); // GH-90000
        assertThat(spec.getStages().get(1).getStageType()).isEqualTo("FILTER");
    }

    @Test
    @DisplayName("eventFilter() has checkpointing disabled")
    void eventFilter_checkpointingDisabled() { // GH-90000
        PipelineSpec spec = PipelineTemplateLibrary.eventFilter("filter-pipe", "t1").build(); // GH-90000

        assertThat(spec.getConfiguration().isCheckpointing()).isFalse(); // GH-90000
    }

    // ─── windowedAggregation ──────────────────────────────────────────────────

    @Test
    @DisplayName("windowedAggregation() template builds a valid 3-stage pipeline")
    void windowedAggregation_validPipeline() { // GH-90000
        PipelineSpec spec = PipelineTemplateLibrary
                .windowedAggregation("agg-pipe", "t1").build(); // GH-90000

        assertThat(validator.validate(spec).isValid()).isTrue(); // GH-90000
        assertThat(spec.getStages()).hasSize(3); // GH-90000
        assertThat(spec.getStages().get(1).getStageType()).isEqualTo("WINDOWED_AGGREGATION");
    }

    @Test
    @DisplayName("windowedAggregation() aggregation and sink stages use EXACTLY_ONCE strategy")
    void windowedAggregation_exactlyOnceDelivery() { // GH-90000
        PipelineSpec spec = PipelineTemplateLibrary
                .windowedAggregation("agg-pipe", "t1").build(); // GH-90000

        assertThat(spec.getStages().get(1).getConfiguration().getExecutionStrategy()) // GH-90000
                .isEqualTo("EXACTLY_ONCE");
        assertThat(spec.getStages().get(2).getConfiguration().getExecutionStrategy()) // GH-90000
                .isEqualTo("EXACTLY_ONCE");
    }

    // ─── customisation after template ─────────────────────────────────────────

    @Test
    @DisplayName("templates can be customised via builder before build()")
    void template_customisable() { // GH-90000
        PipelineSpec spec = PipelineTemplateLibrary.rawIngest("base", "t1") // GH-90000
                .describedAs("Custom description")
                .disabled() // GH-90000
                .build(); // GH-90000

        assertThat(spec.getDescription()).isEqualTo("Custom description");
        assertThat(spec.isEnabled()).isFalse(); // GH-90000
    }
}
