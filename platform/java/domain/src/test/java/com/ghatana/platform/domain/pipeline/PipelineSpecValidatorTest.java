package com.ghatana.platform.domain.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineSpecValidatorTest {

    private final PipelineSpecValidator validator = new PipelineSpecValidator(); // GH-90000

    @Test
    void shouldAcceptValidLinearPipeline() { // GH-90000
        PipelineStageSpec stage1 = PipelineStageSpec.builder() // GH-90000
            .name("stage-1 [GH-90000]")
            .type(PipelineStageSpec.StageType.STREAM) // GH-90000
            .build(); // GH-90000
        PipelineStageSpec stage2 = PipelineStageSpec.builder() // GH-90000
            .name("stage-2 [GH-90000]")
            .type(PipelineStageSpec.StageType.PATTERN) // GH-90000
            .build(); // GH-90000

        PipelineEdgeSpec edge = PipelineEdgeSpec.builder() // GH-90000
            .fromStageId("stage-1 [GH-90000]")
            .toStageId("stage-2 [GH-90000]")
            .label("primary [GH-90000]")
            .build(); // GH-90000

        PipelineSpec spec = PipelineSpec.builder() // GH-90000
            .stages(List.of(stage1, stage2)) // GH-90000
            .edges(List.of(edge)) // GH-90000
            .build(); // GH-90000

        PipelineSpecValidationResult result = validator.validate(spec); // GH-90000

        assertThat(result.valid()).isTrue(); // GH-90000
        assertThat(result.errors()).isEmpty(); // GH-90000
    }

    @Test
    void shouldDetectDuplicateStageNames() { // GH-90000
        PipelineStageSpec stage1 = PipelineStageSpec.builder() // GH-90000
            .name("dup-stage [GH-90000]")
            .type(PipelineStageSpec.StageType.STREAM) // GH-90000
            .build(); // GH-90000
        PipelineStageSpec stage2 = PipelineStageSpec.builder() // GH-90000
            .name("dup-stage [GH-90000]")
            .type(PipelineStageSpec.StageType.PATTERN) // GH-90000
            .build(); // GH-90000

        PipelineSpec spec = PipelineSpec.builder() // GH-90000
            .stages(List.of(stage1, stage2)) // GH-90000
            .build(); // GH-90000

        PipelineSpecValidationResult result = validator.validate(spec); // GH-90000

        assertThat(result.valid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("duplicate stage name [GH-90000]"));
    }

    @Test
    void shouldDetectEdgesReferencingUnknownStages() { // GH-90000
        PipelineStageSpec stage1 = PipelineStageSpec.builder() // GH-90000
            .name("stage-1 [GH-90000]")
            .type(PipelineStageSpec.StageType.STREAM) // GH-90000
            .build(); // GH-90000

        PipelineEdgeSpec edge = PipelineEdgeSpec.builder() // GH-90000
            .fromStageId("stage-1 [GH-90000]")
            .toStageId("missing-stage [GH-90000]")
            .label("primary [GH-90000]")
            .build(); // GH-90000

        PipelineSpec spec = PipelineSpec.builder() // GH-90000
            .stages(List.of(stage1)) // GH-90000
            .edges(List.of(edge)) // GH-90000
            .build(); // GH-90000

        PipelineSpecValidationResult result = validator.validate(spec); // GH-90000

        assertThat(result.valid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("unknown toStageId [GH-90000]"));
    }

    @Test
    void shouldDetectCyclesInEdges() { // GH-90000
        PipelineStageSpec stage1 = PipelineStageSpec.builder() // GH-90000
            .name("stage-1 [GH-90000]")
            .type(PipelineStageSpec.StageType.STREAM) // GH-90000
            .build(); // GH-90000
        PipelineStageSpec stage2 = PipelineStageSpec.builder() // GH-90000
            .name("stage-2 [GH-90000]")
            .type(PipelineStageSpec.StageType.PATTERN) // GH-90000
            .build(); // GH-90000

        PipelineEdgeSpec edge1 = PipelineEdgeSpec.builder() // GH-90000
            .fromStageId("stage-1 [GH-90000]")
            .toStageId("stage-2 [GH-90000]")
            .label("primary [GH-90000]")
            .build(); // GH-90000
        PipelineEdgeSpec edge2 = PipelineEdgeSpec.builder() // GH-90000
            .fromStageId("stage-2 [GH-90000]")
            .toStageId("stage-1 [GH-90000]")
            .label("primary [GH-90000]")
            .build(); // GH-90000

        PipelineSpec spec = PipelineSpec.builder() // GH-90000
            .stages(List.of(stage1, stage2)) // GH-90000
            .edges(List.of(edge1, edge2)) // GH-90000
            .build(); // GH-90000

        PipelineSpecValidationResult result = validator.validate(spec); // GH-90000

        assertThat(result.valid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("cycle detected [GH-90000]"));
    }

    @Test
    void shouldRequireAtLeastOneStage() { // GH-90000
        PipelineSpec spec = PipelineSpec.builder().build(); // GH-90000

        PipelineSpecValidationResult result = validator.validate(spec); // GH-90000

        assertThat(result.valid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("at least one stage [GH-90000]"));
    }

    @Test
    void shouldDetectStagesReferencingUnknownConnectors() { // GH-90000
        PipelineStageSpec stage = PipelineStageSpec.builder() // GH-90000
            .name("stage-1 [GH-90000]")
            .type(PipelineStageSpec.StageType.STREAM) // GH-90000
            .connectorId("missing-connector [GH-90000]")
            .build(); // GH-90000

        PipelineSpec spec = PipelineSpec.builder() // GH-90000
            .stages(List.of(stage)) // GH-90000
            // No connectors defined
            .build(); // GH-90000

        PipelineSpecValidationResult result = validator.validate(spec); // GH-90000

        assertThat(result.valid()).isFalse(); // GH-90000
        assertThat(result.errors()).anyMatch(e -> e.contains("unknown connectorId [GH-90000]"));
    }
}
