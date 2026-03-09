package com.ghatana.core.pipeline;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Declarative event processing pipeline composed of operators with dependencies.
 *
 * <p><b>Purpose</b><br>
 * Represents a complete event processing pipeline as a directed acyclic graph (DAG)
 * of operators. Pipelines are immutable, versioned, and serializable to EventCloud
 * for persistence, versioning, and cross-instance deployment.
 *
 * <p><b>Pipeline Structure</b><br>
 * - **ID**: Unique identifier for the pipeline (e.g., "fraud-detection:1.2.3")
 * - **Name**: Human-readable name (e.g., "Fraud Detection Pipeline")
 * - **Description**: Purpose and behavior documentation
 * - **Stages**: Ordered list of operator stages (DAG nodes)
 * - **Metadata**: Tags, owner, created timestamp, etc.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Build pipeline programmatically
 * Pipeline pipeline = Pipeline.builder("fraud-detection", "1.0.0")
 *     .name("Real-Time Fraud Detection")
 *     .description("Detects fraudulent transactions using pattern matching and ML")
 *     .stage("filter", OperatorId.parse("stream:filter:1.0"))
 *     .stage("enrich", OperatorId.parse("stream:map:1.0"))
 *     .stage("detect", OperatorId.parse("pattern:seq:1.0"))
 *     .stage("alert", OperatorId.parse("stream:map:alert:1.0"))
 *     .edge("filter", "enrich")  // filter → enrich
 *     .edge("enrich", "detect")  // enrich → detect
 *     .edge("detect", "alert")   // detect → alert
 *     .build();
 *
 * // Serialize to EventCloud
 * List<Event> events = pipeline.toEvents();
 *
 * // Deserialize from events
 * Pipeline loaded = Pipeline.fromEvents(events);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Enables declarative pipeline assembly and versioning:
 * - Pipelines stored in EventCloud as operator events
 * - Supports Git-like branching and versioning
 * - Enables dynamic pipeline deployment
 * - Provides audit trail of pipeline changes
 *
 * <p><b>Serialization Format</b><br>
 * Pipelines serialize to GEvent format:
 * - Event type: "pipeline.registered"
 * - Payload: {id, name, version, description, stages: [{stage_id, operator_id}], edges: [{from, to}], metadata}
 * - Headers: {pipelineId, tenantId, version}
 *
 * @see PipelineStage
 * @see PipelineEdge
 * @see com.ghatana.core.operator.OperatorCatalog
 * @doc.type class
 * @doc.purpose Declarative event processing pipeline with operator composition
 * @doc.layer core
 * @doc.pattern Builder
 */
public interface Pipeline {

    /**
     * Gets the unique pipeline identifier.
     *
     * @return pipeline ID (e.g., "fraud-detection:1.2.3")
     */
    String getId();

    /**
     * Gets the pipeline name.
     *
     * @return human-readable name
     */
    String getName();

    /**
     * Gets the pipeline version.
     *
     * @return semantic version (e.g., "1.0.0")
     */
    String getVersion();

    /**
     * Gets the pipeline description.
     *
     * @return purpose and behavior documentation
     */
    String getDescription();

    /**
     * Gets all operator stages in the pipeline.
     *
     * <p>Stages are ordered by dependency: later stages depend on earlier stages.
     *
     * @return immutable list of stages
     */
    List<PipelineStage> getStages();

    /**
     * Gets all edges (dependencies) in the pipeline DAG.
     *
     * <p>Each edge represents "output from 'from' stage feeds into 'to' stage".
     *
     * @return immutable list of edges
     */
    List<PipelineEdge> getEdges();

    /**
     * Gets metadata (tags, owner, timestamps, etc.).
     *
     * @return immutable map of metadata
     */
    Map<String, Object> getMetadata();

    /**
     * Serializes pipeline to EventCloud event format.
     *
     * <p>Returns a single event representing the complete pipeline:
     * - Event type: "pipeline.registered"
     * - Payload contains all stages, edges, and metadata
     * - Includes versioning info in headers
     *
     * @return event representing this pipeline (ready for EventCloud persistence)
     */
    Event toEvent();

    /**
     * Deserializes pipeline from EventCloud events.
     *
     * <p>Reconstructs pipeline from event(s) previously created via {@link #toEvent()}.
     * Validates event structure and operator references.
     *
     * <p>NOTE: Implementation provided by DefaultPipeline after creation.
     *
     * @param events one or more events representing the pipeline
     * @return deserialized pipeline
     * @throws IllegalArgumentException if events are malformed or missing required fields
     */
    static Pipeline fromEvents(List<Event> events) {
        return DefaultPipeline.fromEvents(events);
    }

    /**
     * Creates a new pipeline builder.
     *
     * @param id unique pipeline identifier
     * @param version semantic version
     * @return builder for fluent pipeline construction
     */
    static PipelineBuilder builder(String id, String version) {
        return DefaultPipeline.builder(id, version);
    }

    /**
     * Validates pipeline structure.
     *
     * <p>Checks for:
     * - No cycles (DAG property)
     * - All edge references valid (stages exist)
     * - At least one stage (non-empty pipeline)
     * - No isolated stages (all stages reachable from root)
     *
     * @return validation result with detailed error messages
     */
    PipelineValidationResult validate();

    /**
     * Executes pipeline processing for a single event (async).
     *
     * <p>Routes event through operator stages in dependency order.
     * Applies operators to transform/filter the event through the pipeline.
     * Returns result after final stage processing.
     *
     * @param event input event to process
     * @return promise completing with final pipeline result
     */
    Promise<PipelineExecutionResult> execute(Event event);
}
