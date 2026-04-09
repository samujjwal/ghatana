/*
 * Copyright (c) 2026 Ghatana Inc.
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

    private final PipelineSpecValidator validator = new PipelineSpecValidator();

    // ─── rawIngest ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("rawIngest() template builds a valid pipeline with 2 stages")
    void rawIngest_validPipeline() {
        PipelineSpec spec = PipelineTemplateLibrary.rawIngest("my-ingest", "tenant-1").build();

        assertThat(validator.validate(spec).isValid()).isTrue();
        assertThat(spec.getStages()).hasSize(2);
        assertThat(spec.getStages().get(0).getStageType()).isEqualTo("KAFKA_SOURCE");
        assertThat(spec.getStages().get(1).getStageType()).isEqualTo("SINK");
    }

    @Test
    @DisplayName("rawIngest() uses the provided tenant")
    void rawIngest_setsTenant() {
        PipelineSpec spec = PipelineTemplateLibrary.rawIngest("pipe", "my-tenant").build();

        assertThat(spec.getTenantId()).isEqualTo("my-tenant");
    }

    @Test
    @DisplayName("rawIngest() template has checkpointing enabled")
    void rawIngest_checkpointingEnabled() {
        PipelineSpec spec = PipelineTemplateLibrary.rawIngest("pipe", "t1").build();

        assertThat(spec.getConfiguration().isCheckpointing()).isTrue();
    }

    // ─── eventEnrichment ──────────────────────────────────────────────────────

    @Test
    @DisplayName("eventEnrichment() template builds a valid 3-stage pipeline")
    void eventEnrichment_validPipeline() {
        PipelineSpec spec = PipelineTemplateLibrary.eventEnrichment("enrich-pipe", "t1").build();

        assertThat(validator.validate(spec).isValid()).isTrue();
        assertThat(spec.getStages()).hasSize(3);
        assertThat(spec.getStages().get(1).getStageType()).isEqualTo("ENRICHMENT");
    }

    @Test
    @DisplayName("eventEnrichment() enrichment stage has highest parallelism")
    void eventEnrichment_enrichmentStageParallelism() {
        PipelineSpec spec = PipelineTemplateLibrary.eventEnrichment("enrich-pipe", "t1").build();

        PipelineStageSpec enrichStage = spec.getStages().get(1);
        assertThat(enrichStage.getConfiguration().getParallelism())
                .isGreaterThan(spec.getStages().get(0).getConfiguration().getParallelism());
    }

    // ─── eventFilter ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("eventFilter() template builds a valid 3-stage pipeline")
    void eventFilter_validPipeline() {
        PipelineSpec spec = PipelineTemplateLibrary.eventFilter("filter-pipe", "t1").build();

        assertThat(validator.validate(spec).isValid()).isTrue();
        assertThat(spec.getStages()).hasSize(3);
        assertThat(spec.getStages().get(1).getStageType()).isEqualTo("FILTER");
    }

    @Test
    @DisplayName("eventFilter() has checkpointing disabled")
    void eventFilter_checkpointingDisabled() {
        PipelineSpec spec = PipelineTemplateLibrary.eventFilter("filter-pipe", "t1").build();

        assertThat(spec.getConfiguration().isCheckpointing()).isFalse();
    }

    // ─── windowedAggregation ──────────────────────────────────────────────────

    @Test
    @DisplayName("windowedAggregation() template builds a valid 3-stage pipeline")
    void windowedAggregation_validPipeline() {
        PipelineSpec spec = PipelineTemplateLibrary
                .windowedAggregation("agg-pipe", "t1").build();

        assertThat(validator.validate(spec).isValid()).isTrue();
        assertThat(spec.getStages()).hasSize(3);
        assertThat(spec.getStages().get(1).getStageType()).isEqualTo("WINDOWED_AGGREGATION");
    }

    @Test
    @DisplayName("windowedAggregation() aggregation and sink stages use EXACTLY_ONCE strategy")
    void windowedAggregation_exactlyOnceDelivery() {
        PipelineSpec spec = PipelineTemplateLibrary
                .windowedAggregation("agg-pipe", "t1").build();

        assertThat(spec.getStages().get(1).getConfiguration().getExecutionStrategy())
                .isEqualTo("EXACTLY_ONCE");
        assertThat(spec.getStages().get(2).getConfiguration().getExecutionStrategy())
                .isEqualTo("EXACTLY_ONCE");
    }

    // ─── customisation after template ─────────────────────────────────────────

    @Test
    @DisplayName("templates can be customised via builder before build()")
    void template_customisable() {
        PipelineSpec spec = PipelineTemplateLibrary.rawIngest("base", "t1")
                .describedAs("Custom description")
                .disabled()
                .build();

        assertThat(spec.getDescription()).isEqualTo("Custom description");
        assertThat(spec.isEnabled()).isFalse();
    }
}
