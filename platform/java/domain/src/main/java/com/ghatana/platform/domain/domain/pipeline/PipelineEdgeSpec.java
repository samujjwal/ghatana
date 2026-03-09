package com.ghatana.platform.domain.domain.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code PipelineEdgeSpec} defines a declarative edge between two pipeline stages
 * in the pipeline specification DAG.
 *
 * <p>It captures logical data-flow dependencies without binding to a particular
 * runtime implementation. Adapters in runtime modules are responsible for
 * translating {@link PipelineEdgeSpec} instances into concrete {@code PipelineEdge}
 * objects.
 *
 * <p>Typical usage in YAML:
 * {@code
 * edges:
 *   - fromStageId: "validation"
 *     toStageId: "enrichment"
 *     label: "primary"
 * }
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose pipeline edge specification linking stages in the declarative DAG
 * @doc.pattern value-object, serializable, configuration
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineEdgeSpec {

    /**
     * Source stage identifier. Must correspond to a {@link PipelineStageSpec#getName()} value.
     */
    private String fromStageId;

    /**
     * Target stage identifier. Must correspond to a {@link PipelineStageSpec#getName()} value.
     */
    private String toStageId;

    /**
     * Optional edge label (for example, "primary", "error", "fallback").
     * Semantics are interpreted by the runtime adapter.
     */
    private String label;
}
