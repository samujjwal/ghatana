package com.ghatana.aep.domain.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code AgentSpec} defines the declarative specification and configuration for an agent
 * that executes within a pipeline stage, including identity, role, capabilities, operational constraints,
 * and hierarchical composition support.
 *
 * <h2>Purpose</h2>
 * Provides declarative agent configuration enabling:
 * <ul>
 *   <li>Agent identification and type declaration (id, agent class, role)</li>
 *   <li>Data flow specification (inputs/outputs with formats)</li>
 *   <li>Task definitions and acceptance criteria</li>
 *   <li>Dependency and failure escalation declaration</li>
 *   <li>Human-in-the-loop (HITL) configuration for sensitive operations</li>
 *   <li>Hierarchical agent composition (nested agents)</li>
 * </ul>
 * Enables orchestrator to compose, validate, and execute complex multi-agent workflows.
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Part of</b>: {@link PipelineStageSpec} workflow list</li>
 *   <li><b>Defines</b>: Individual processing unit within pipeline stage</li>
 *   <li><b>References</b>: Agent implementation class via {@code agent} field</li>
 *   <li><b>Specifies</b>: Data contracts via {@link AgentIOSpec} for I/O</li>
 *   <li><b>Supports</b>: Hierarchical composition via children agents (composite pattern)</li>
 * </ul>
 *
 * <h2>Core Configuration Fields</h2>
 *
 * <h3>Identity & Classification</h3>
 * <ul>
 *   <li><b>id</b>: Unique identifier within pipeline (e.g., "ingest-1", "validate-2")</li>
 *   <li><b>agent</b>: Agent implementation class name (e.g., "DataIngestionAgent")</li>
 *   <li><b>role</b>: Functional role within pipeline (e.g., "ingester", "validator", "enricher")</li>
 * </ul>
 *
 * <h3>Data Flow Specification</h3>
 * <ul>
 *   <li><b>inputsSpec</b>: List of input ports with format specifications</li>
 *   <li><b>outputsSpec</b>: List of output ports with format specifications</li>
 *   <li>Enables orchestrator to validate data flow between agents</li>
 *   <li>Supports cross-stage data transformation</li>
 * </ul>
 * {@code
 * inputsSpec: [
 *   AgentIOSpec(name="raw_data", format="json"),
 *   AgentIOSpec(name="config", format="yaml")
 * ]
 * outputsSpec: [
 *   AgentIOSpec(name="processed_events", format="protobuf")
 * ]
 * }
 *
 * <h3>Task Definitions & Success Criteria</h3>
 * <ul>
 *   <li><b>agentTasks</b>: List of responsibilities (e.g., "validate schema", "check integrity")</li>
 *   <li><b>acceptanceCriteria</b>: List of measurable success metrics (e.g., "error_rate < 0.1%")</li>
 *   <li>Documents expected behavior for testing and validation</li>
 * </ul>
 * {@code
 * agentTasks: ["validate schema", "check integrity", "log errors"]
 * acceptanceCriteria: [
 *   "error_rate < 0.1%",
 *   "latency < 100ms p99",
 *   "no data loss"
 * ]
 * }
 *
 * <h3>Operational Constraints</h3>
 * <ul>
 *   <li><b>dependencies</b>: External services/resources required (e.g., "schema-service")</li>
 *   <li><b>failureEscalation</b>: Fallback actions on failure (e.g., "retry", "alert-ops")</li>
 *   <li>Enables operational continuity and graceful degradation</li>
 * </ul>
 * {@code
 * dependencies: ["schema-service", "cache-service"]
 * failureEscalation: ["retry", "alert-ops", "pause-pipeline"]
 * }
 *
 * <h3>Human-in-the-Loop Configuration</h3>
 * <ul>
 *   <li><b>hitl</b>: Enable pause-points for human review</li>
 *   <li><b>hitlReason</b>: Human-readable reason for requiring review</li>
 *   <li>Useful for sensitive data access, approval workflows, quality gates</li>
 * </ul>
 * {@code
 * hitl: true
 * hitlReason: "Manual review required for sensitive customer data"
 * }
 *
 * <h3>Hierarchical Composition</h3>
 * <ul>
 *   <li><b>children</b>: Nested agents for composite workflows</li>
 *   <li>Enables recursive agent structures</li>
 *   <li>Each child is itself an AgentSpec (recursive definition)</li>
 * </ul>
 * {@code
 * children: [
 *   AgentSpec(id="sub-agent-1", agent="SubAgent1", ...),
 *   AgentSpec(id="sub-agent-2", agent="SubAgent2", ...)
 * ]
 * }
 *
 * <h2>Nested AgentIOSpec</h2>
 * Specification for input/output ports on agents:
 * {@code
 * public static class AgentIOSpec {
 *     String name;         // I/O port name (e.g., "events", "metrics")
 *     String description;  // Human-readable description
 *     String format;       // Data format (json, protobuf, avro, parquet, etc.)
 * }
 * }
 * <ul>
 *   <li>Enables type-safe data flow validation</li>
 *   <li>Documents data contracts for integration</li>
 *   <li>Supports format-aware serialization/deserialization</li>
 * </ul>
 *
 * <h2>Builder Pattern</h2>
 * Supports fluent declarative construction:
 * {@code
 * AgentSpec agent = AgentSpec.builder()
 *     .id("enricher-1")
 *     .agent("DataEnricherAgent")
 *     .role("enricher")
 *     .inputsSpec(List.of(
 *         AgentIOSpec.builder()
 *             .name("events")
 *             .format("json")
 *             .description("Raw events to enrich")
 *             .build()
 *     ))
 *     .outputsSpec(List.of(
 *         AgentIOSpec.builder()
 *             .name("enriched_events")
 *             .format("json")
 *             .description("Events with additional context")
 *             .build()
 *     ))
 *     .agentTasks(List.of(
 *         "Lookup reference data",
 *         "Combine with event data",
 *         "Cache results"
 *     ))
 *     .acceptanceCriteria(List.of(
 *         "Cache hit rate > 80%",
 *         "Latency < 200ms p99"
 *     ))
 *     .dependencies(List.of("reference-data-service", "cache-service"))
 *     .build();
 * }
 *
 * <h2>JSON/YAML Serialization</h2>
 * Supports declarative definition via configuration files:
 * {@code
 * id: enricher-1
 * agent: DataEnricherAgent
 * role: enricher
 * inputsSpec:
 *   - name: events
 *     format: json
 *     description: Raw events to enrich
 * outputsSpec:
 *   - name: enriched_events
 *     format: json
 *     description: Events with additional context
 * agentTasks:
 *   - Lookup reference data
 *   - Combine with event data
 *   - Cache results
 * acceptanceCriteria:
 *   - Cache hit rate > 80%
 *   - Latency < 200ms p99
 * dependencies:
 *   - reference-data-service
 *   - cache-service
 * }
 *
 * <h2>Typical Agent Roles</h2>
 * <ul>
 *   <li><b>ingester</b>: Reads from external sources (Kafka, API, files)</li>
 *   <li><b>validator</b>: Checks data quality and schema compliance</li>
 *   <li><b>enricher</b>: Adds contextual information (lookups, calculations)</li>
 *   <li><b>transformer</b>: Converts between formats/structures</li>
 *   <li><b>deduplicator</b>: Removes redundant records</li>
 *   <li><b>aggregator</b>: Combines multiple records</li>
 *   <li><b>filter</b>: Selects subset based on criteria</li>
 *   <li><b>exporter</b>: Outputs to sinks (database, warehouse, etc.)</li>
 *   <li><b>reviewer</b>: Human-in-the-loop approval/review</li>
 * </ul>
 *
 * <h2>Data Format Support</h2>
 * Common format specifications:
 * <ul>
 *   <li>Structured: "json", "xml", "protobuf", "avro", "parquet"</li>
 *   <li>Text: "csv", "tsv", "plaintext"</li>
 *   <li>Binary: "msgpack", "thrift", "flatbuffer"</li>
 *   <li>Domain-specific: "yaml", "ini", "properties"</li>
 * </ul>
 *
 * <h2>Failure Escalation Strategies</h2>
 * <ul>
 *   <li><b>retry</b>: Automatically retry (with exponential backoff)</li>
 *   <li><b>alert-ops</b>: Notify operations team</li>
 *   <li><b>pause-pipeline</b>: Halt pipeline execution</li>
 *   <li><b>skip-record</b>: Skip failing record, continue with next</li>
 *   <li><b>escalate-to-dl-queue</b>: Move to dead letter queue for investigation</li>
 *   <li><b>failover</b>: Switch to backup agent/service</li>
 * </ul>
 *
 * @see PipelineStageSpec
 * @see PipelineSpec
 * @see AgentIOSpec
 * @since 1.0.0
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose agent specification and declarative configuration definition
 * @doc.pattern value-object, builder, composition, data-flow-contract
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSpec {

    private String id;
    private String agent;
    private String role;
    private List<AgentIOSpec> inputsSpec;
    private List<AgentIOSpec> outputsSpec;
    private List<String> agentTasks;
    private List<String> acceptanceCriteria;
    private List<String> dependencies;
    private List<String> failureEscalation;
    private List<String> references;
    private Boolean hitl;
    private String hitlReason;
    private List<AgentSpec> children;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgentIOSpec {
        private String name;
        private String description;
        private String format;
    }
}
