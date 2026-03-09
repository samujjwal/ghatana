package com.ghatana.platform.domain.domain.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code PipelineStageSpec} defines a processing stage within a pipeline,
 * representing a logical grouping of agents that execute together on the same data.
 *
 * <h2>Purpose</h2>
 * Encapsulates a distinct processing phase with:
 * <ul>
 *   <li>Stage identity and naming</li>
 *   <li>List of agents executing in the stage (workflow)</li>
 *   <li>Logical grouping of related processing tasks</li>
 *   <li>YAML serialization support via Lombok and Jackson</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Contained in</b>: {@link PipelineSpec} as stages list</li>
 *   <li><b>Contains</b>: Workflow of {@link AgentSpec} instances</li>
 *   <li><b>Can be</b>: Included from external YAML via PipelineSpecFactory</li>
 *   <li><b>Defines</b>: Phase of processing in event workflow</li>
 *   <li><b>Related to</b>: {@link PipelineSpec}, {@link AgentSpec}</li>
 * </ul>
 *
 * <h2>Stage Execution Semantics</h2>
 * A stage represents a processing phase where:
 * <ul>
 *   <li><b>name</b>: Human-readable stage identifier (e.g., "validation", "enrichment")</li>
 *   <li><b>workflow</b>: List of agents comprising this stage</li>
 *   <li>All agents in workflow receive same input events</li>
 *   <li>Agents may process sequentially or in parallel (orchestrator decides)</li>
 *   <li>Stage output feeds into next pipeline stage</li>
 * </ul>
 *
 * <h2>Workflow Patterns</h2>
 *
 * <h3>Sequential Processing</h3>
 * {@code
 * workflow = [Agent-A, Agent-B, Agent-C]
 * Input → A → B → C → Output
 * }
 * Each agent processes output of previous agent.
 *
 * <h3>Parallel Processing</h3>
 * {@code
 * workflow = [Agent-A, Agent-B, Agent-C]
 * Input → ┌─→ A ─┐
 *         ├─→ B ─┼→ Merged Output
 *         └─→ C ─┘
 * }
 * All agents process same input, outputs merged/aggregated.
 *
 * <h3>Mixed Processing</h3>
 * Orchestrator determines execution model based on:
 * <ul>
 *   <li>Agent capabilities and dependencies</li>
 *   <li>Data flow requirements</li>
 *   <li>Performance optimization hints</li>
 * </ul>
 *
 * <h2>Common Stage Types</h2>
 * <table border="1">
 *   <tr><th>Stage Name</th><th>Purpose</th><th>Example Agents</th></tr>
 *   <tr><td>ingestion</td><td>Receive external data</td><td>KafkaIngestionAgent, HTTPListenerAgent</td></tr>
 *   <tr><td>validation</td><td>Check data against schema</td><td>SchemaValidatorAgent, TypeValidatorAgent</td></tr>
 *   <tr><td>transformation</td><td>Convert data format</td><td>MapperAgent, NormalizerAgent</td></tr>
 *   <tr><td>enrichment</td><td>Add context/metadata</td><td>EnricherAgent, LookupAgent</td></tr>
 *   <tr><td>analysis</td><td>Extract insights</td><td>AnalyzerAgent, AggregatorAgent</td></tr>
 *   <tr><td>persistence</td><td>Store processed data</td><td>DatabaseWriterAgent, CacheAgent</td></tr>
 * </table>
 *
 * <h2>Typical Usage</h2>
 * {@code
 * // Define a validation stage
 * PipelineStageSpec validationStage = PipelineStageSpec.builder()
 *     .name("validation")
 *     .workflow(List.of(
 *         AgentSpec.builder()
 *             .id("schema-validator")
 *             .agent("SchemaValidatorAgent")
 *             .role("validator")
 *             .build(),
 *         AgentSpec.builder()
 *             .id("semantic-checker")
 *             .agent("SemanticCheckerAgent")
 *             .role("checker")
 *             .build()
 *     ))
 *     .build();
 *
 * // Include in pipeline
 * PipelineSpec pipeline = PipelineSpec.builder()
 *     .stages(List.of(ingestionStage, validationStage, enrichmentStage))
 *     .build();
 * }
 *
 * <h2>YAML Example</h2>
 * {@code
 * # Stage definition with transformation
 * name: transformation
 * workflow:
 *   - id: mapper-1
 *     agent: DataMapperAgent
 *     role: mapper
 *     inputsSpec:
 *       - name: raw_event
 *         format: json
 *     outputsSpec:
 *       - name: transformed_event
 *         format: json
 *   - id: deduplicator
 *     agent: DeduplicationAgent
 *     role: deduplicator
 * }
 *
 * @see PipelineSpec
 * @see AgentSpec
 * @since 1.0.0
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose pipeline stage specification and workflow definition
 * @doc.pattern value-object, builder, composition, serializable
 * @doc.test-hints stage-composition, workflow-execution, agent-sequencing, parallel-processing
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineStageSpec {
    public enum StageType {
        STREAM,
        PATTERN,
        CONNECTOR
    }

    private String name;

    private StageType type;

    /**
     * Optional reference to a {@link ConnectorSpec#getId()} that should be
     * used as the source or sink for this stage.
     */
    private String connectorId;

    @JsonProperty("workflow")
    private List<AgentSpec> workflow;
}
