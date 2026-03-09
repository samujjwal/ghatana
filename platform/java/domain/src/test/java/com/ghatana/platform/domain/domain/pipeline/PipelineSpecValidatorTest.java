package com.ghatana.platform.domain.domain.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineSpecValidatorTest {

    private final PipelineSpecValidator validator = new PipelineSpecValidator();

    @Test
    void shouldAcceptValidLinearPipeline() {
        PipelineStageSpec stage1 = PipelineStageSpec.builder()
            .name("stage-1")
            .type(PipelineStageSpec.StageType.STREAM)
            .build();
        PipelineStageSpec stage2 = PipelineStageSpec.builder()
            .name("stage-2")
            .type(PipelineStageSpec.StageType.PATTERN)
            .build();

        PipelineEdgeSpec edge = PipelineEdgeSpec.builder()
            .fromStageId("stage-1")
            .toStageId("stage-2")
            .label("primary")
            .build();

        PipelineSpec spec = PipelineSpec.builder()
            .stages(List.of(stage1, stage2))
            .edges(List.of(edge))
            .build();

        PipelineSpecValidationResult result = validator.validate(spec);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void shouldDetectDuplicateStageNames() {
        PipelineStageSpec stage1 = PipelineStageSpec.builder()
            .name("dup-stage")
            .type(PipelineStageSpec.StageType.STREAM)
            .build();
        PipelineStageSpec stage2 = PipelineStageSpec.builder()
            .name("dup-stage")
            .type(PipelineStageSpec.StageType.PATTERN)
            .build();

        PipelineSpec spec = PipelineSpec.builder()
            .stages(List.of(stage1, stage2))
            .build();

        PipelineSpecValidationResult result = validator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("duplicate stage name"));
    }

    @Test
    void shouldDetectEdgesReferencingUnknownStages() {
        PipelineStageSpec stage1 = PipelineStageSpec.builder()
            .name("stage-1")
            .type(PipelineStageSpec.StageType.STREAM)
            .build();

        PipelineEdgeSpec edge = PipelineEdgeSpec.builder()
            .fromStageId("stage-1")
            .toStageId("missing-stage")
            .label("primary")
            .build();

        PipelineSpec spec = PipelineSpec.builder()
            .stages(List.of(stage1))
            .edges(List.of(edge))
            .build();

        PipelineSpecValidationResult result = validator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("unknown toStageId"));
    }

    @Test
    void shouldDetectCyclesInEdges() {
        PipelineStageSpec stage1 = PipelineStageSpec.builder()
            .name("stage-1")
            .type(PipelineStageSpec.StageType.STREAM)
            .build();
        PipelineStageSpec stage2 = PipelineStageSpec.builder()
            .name("stage-2")
            .type(PipelineStageSpec.StageType.PATTERN)
            .build();

        PipelineEdgeSpec edge1 = PipelineEdgeSpec.builder()
            .fromStageId("stage-1")
            .toStageId("stage-2")
            .label("primary")
            .build();
        PipelineEdgeSpec edge2 = PipelineEdgeSpec.builder()
            .fromStageId("stage-2")
            .toStageId("stage-1")
            .label("primary")
            .build();

        PipelineSpec spec = PipelineSpec.builder()
            .stages(List.of(stage1, stage2))
            .edges(List.of(edge1, edge2))
            .build();

        PipelineSpecValidationResult result = validator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("cycle detected"));
    }

    @Test
    void shouldRequireAtLeastOneStage() {
        PipelineSpec spec = PipelineSpec.builder().build();

        PipelineSpecValidationResult result = validator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("at least one stage"));
    }

    @Test
    void shouldDetectStagesReferencingUnknownConnectors() {
        PipelineStageSpec stage = PipelineStageSpec.builder()
            .name("stage-1")
            .type(PipelineStageSpec.StageType.STREAM)
            .connectorId("missing-connector")
            .build();

        PipelineSpec spec = PipelineSpec.builder()
            .stages(List.of(stage))
            // No connectors defined
            .build();

        PipelineSpecValidationResult result = validator.validate(spec);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("unknown connectorId"));
    }
}
