package com.ghatana.platform.domain.domain.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * {@code PipelineSpec} defines a complete declarative pipeline configuration consisting of
 * multiple sequential stages, each containing agents that process events/data through transformations.
 *
 * <h2>Purpose</h2>
 * Provides top-level container and orchestration specification for multi-stage event processing:
 * <ul>
 *   <li>Top-level container for entire pipeline configuration</li>
 *   <li>Defines ordered sequence of processing stages</li>
 *   <li>Enables declarative YAML/JSON serialization for configuration-driven pipelines</li>
 *   <li>Supports dynamic pipeline composition via PipelineSpecFactory</li>
 *   <li>Integrates with orchestrators for execution and monitoring</li>
 * </ul>
 * Enables complex multi-stage workflows for event processing, data transformation, and integration.
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Loaded from</b>: YAML/JSON files via PipelineSpecFactory</li>
 *   <li><b>Composed of</b>: Ordered list of {@link PipelineStageSpec} definitions</li>
 *   <li><b>Parsed by</b>: Pipeline builder, orchestrator, configuration manager</li>
 *   <li><b>Executed by</b>: Pipeline runtime engine, distributed executor</li>
 *   <li><b>Related to</b>: {@link PipelineStageSpec}, {@link AgentSpec}, orchestrator</li>
 * </ul>
 *
 * <h2>Pipeline Execution Model</h2>
 * Stages execute sequentially in list order (stage ordering is significant):
 * <ol>
 *   <li>Stage 1 (e.g., ingestion) processes all input events from source</li>
 *   <li>Stage 2 (e.g., validation) receives Stage 1 output as input</li>
 *   <li>Stage 3 (e.g., enrichment) receives Stage 2 output as input</li>
 *   <li>Final stage produces pipeline output to sink(s)</li>
 * </ol>
 *
 * Within each stage, agents in the workflow may execute:
 * <ul>
 *   <li>Sequentially: Output of agent N becomes input of agent N+1</li>
 *   <li>In parallel: Multiple agents process same input independently</li>
 *   <li>Mixed: Orchestrator determines execution strategy based on agent I/O specs</li>
 * </ul>
 *
 * <h2>Pipeline Composition Structure</h2>
 * {@code
 * PipelineSpec (root container)
 *   └── stages: List<PipelineStageSpec> (ordered)
 *       ├── Stage 1: PipelineStageSpec (e.g., "ingestion")
 *       │   └── workflow: List<AgentSpec> (agents in stage)
 *       │       ├── Agent 1: DataIngestionAgent
 *       │       └── Agent 2: SchemaValidator
 *       ├── Stage 2: PipelineStageSpec (e.g., "enrichment")
 *       │       ├── Agent 1A
 *       │       └── Agent 1B
 *       ├── Stage 2: PipelineStageSpec
 *       │   └── workflow: List<AgentSpec>
 *       │       └── Agent 2A
 *       └── Stage N: ...
 * }
 *
 * <h2>Serialization</h2>
 * Lombok annotations (@Data, @Builder) enable:
 * <ul>
 *   <li>Automatic getter/setter generation</li>
 *   <li>Builder pattern for fluent construction</li>
 *   <li>JSON serialization via Jackson</li>
 *   <li>YAML serialization via Jackson YAMLFactory</li>
 * </ul>
 *
 * {@code @JsonInclude(NON_NULL)} omits null fields from serialization,
 * reducing YAML file size and improving readability.
 *
 * <h2>Typical Usage</h2>
 * {@code
 * // Load pipeline from YAML
 * PipelineSpecFactory factory = new PipelineSpecFactory();
 * PipelineSpec pipeline = factory.getPipeline("event-processing");
 *
 * // Programmatic construction
 * PipelineSpec customPipeline = PipelineSpec.builder()
 *     .stages(List.of(
 *         PipelineStageSpec.builder()
 *             .name("data-ingestion")
 *             .workflow(List.of(
 *                 AgentSpec.builder()
 *                     .id("ingest-1")
 *                     .agent("DataIngestionAgent")
 *                     .build()
 *             ))
 *             .build(),
 *         PipelineStageSpec.builder()
 *             .name("data-processing")
 *             .workflow(List.of(
 *                 AgentSpec.builder()
 *                     .id("process-1")
 *                     .agent("DataProcessingAgent")
 *                     .build()
 *             ))
 *             .build()
 *     ))
 *     .build();
 * }
 *
 * <h2>YAML Example</h2>
 * {@code
 * # pipeline.yaml - Event processing pipeline
 * stages:
 *   - name: ingestion
 *     workflow:
 *       - id: kafka-ingest
 *         agent: KafkaIngestionAgent
 *         role: ingester
 *   - name: validation
 *     workflow:
 *       - id: schema-validator
 *         agent: SchemaValidatorAgent
 *         role: validator
 *   - name: enrichment
 *     workflow:
 *       - id: enricher
 *         agent: DataEnricherAgent
 *         role: enricher
 * }
 *
 * @see PipelineStageSpec
 * @see AgentSpec
 * @see PipelineSpecFactory
 * @since 1.0.0
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose pipeline configuration and structure definition
 * @doc.pattern value-object, builder, serializable, configuration
 * @doc.test-hints pipeline-composition, stage-sequencing, agent-grouping, YAML-serialization
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineSpec {

    private List<PipelineStageSpec> stages;

    private String tenantId;

    private String environment;

    private List<String> tags;

    private Map<String, String> agentHints;

    private List<PipelineEdgeSpec> edges;

    private List<ConnectorSpec> connectors;
}
