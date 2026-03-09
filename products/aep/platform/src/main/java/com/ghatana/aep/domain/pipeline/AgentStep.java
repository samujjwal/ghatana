package com.ghatana.aep.domain.pipeline;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * {@code AgentStep} represents an immutable single execution step within an
 * agent's processing pipeline, including identity, type, parameters, and execution context.
 *
 * <h2>Purpose</h2>
 * Provides immutable snapshot of a discrete work unit:
 * <ul>
 *   <li>Execution step identity and uniqueness</li>
 *   <li>Agent association and step type</li>
 *   <li>Parameterization for step execution</li>
 *   <li>Contextual information for decision-making</li>
 *   <li>Priority-based scheduling</li>
 *   <li>Temporal tracking with creation timestamp</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Created by</b>: Step factory, orchestrator</li>
 *   <li><b>Queued in</b>: Agent work queue, scheduler</li>
 *   <li><b>Processed by</b>: Agent execution engine</li>
 *   <li><b>Tracked in</b>: Audit logs, execution history</li>
 *   <li><b>Related to</b>: {@link AgentSpec}, execution context</li>
 * </ul>
 *
 * <h2>Immutability & Thread Safety</h2>
 * Final class with defensive copying ensures:
 * <ul>
 *   <li>Thread-safe sharing across components</li>
 *   <li>No accidental mutation of work units</li>
 *   <li>Safe for distributed processing</li>
 *   <li>Suitable for event sourcing</li>
 * </ul>
 *
 * Context map is defensively copied: {@code Map.copyOf(builder.context)}
 * to prevent external modifications.
 *
 * <h2>Core Fields</h2>
 *
 * <h3>Identity</h3>
 * {@code
 * id: "step-123"     // Globally unique step identifier
 * agentId: "agent-456"  // Owner agent
 * }
 * {@code id} should be deterministic/traceable for debugging.
 *
 * <h3>Execution Definition</h3>
 * {@code
 * type: "PROCESS_EVENT"  // Step operation type
 * parameters: "{...}"    // JSON-encoded step parameters
 * }
 * Parameters enable generic step handling without type coupling.
 *
 * <h3>Contextual Data</h3>
 * {@code
 * context: {
 *   "tenantId": "tenant-789",
 *   "correlationId": "trace-abc",
 *   "userId": "user-xyz"
 * }
 * }
 * Provides execution context for access control, tracing, and correlation.
 *
 * <h3>Scheduling & Timing</h3>
 * {@code
 * priority: 10        // Higher number = higher priority
 * createdAt: Instant.now()  // Immutable timestamp
 * }
 *
 * <h2>Builder Pattern</h2>
 * {@code
 * AgentStep step = AgentStep.builder()
 *     .id("step-event-123")
 *     .agentId("enricher-1")
 *     .type("ENRICH")
 *     .parameters("{\"source\":\"kafka\"}")
 *     .context(Map.of(
 *         "tenantId", "acme",
 *         "correlationId", "trace-789"
 *     ))
 *     .priority(5)
 *     .createdAt(Instant.now())
 *     .build();
 * }
 *
 * Required fields checked during build:
 * <ul>
 *   <li>{@code id}: Non-null</li>
 *   <li>{@code agentId}: Non-null</li>
 *   <li>{@code type}: Non-null</li>
 * </ul>
 *
 * Optional fields with defaults:
 * <ul>
 *   <li>{@code parameters}: Defaults to "{}"</li>
 *   <li>{@code context}: Defaults to empty map</li>
 *   <li>{@code createdAt}: Defaults to Instant.now()</li>
 *   <li>{@code priority}: Defaults to 0</li>
 * </ul>
 *
 * <h2>Priority Comparison</h2>
 * {@code
 * AgentStep highPriority = AgentStep.builder().id("a").priority(10).build();
 * AgentStep lowPriority = AgentStep.builder().id("b").priority(5).build();
 *
 * if (highPriority.hasHigherPriorityThan(lowPriority)) {
 *     // Process high priority step first
 * }
 * }
 * Enables priority queue scheduling and fair scheduling algorithms.
 *
 * <h2>Context Access Patterns</h2>
 * {@code
 * // Type-safe context access
 * String tenantId = step.getContextValue("tenantId");
 *
 * // Iterate context
 * for (String key : step.getContext().keySet()) {
 *     String value = step.getContextValue(key);
 * }
 * }
 *
 * <h2>Equality & Hashing</h2>
 * Steps are compared by all fields, enabling:
 * <ul>
 *   <li>Duplicate detection</li>
 *   <li>Deduplication in queues</li>
 *   <li>Set-based step collections</li>
 * </ul>
 *
 * <h2>Typical Usage</h2>
 * {@code
 * // Create step for event enrichment
 * AgentStep enrichStep = AgentStep.builder()
 *     .id(UUID.randomUUID().toString())
 *     .agentId("enricher-prod-1")
 *     .type("ENRICH_EVENT")
 *     .parameters(jacksonMapper.writeValueAsString(Map.of(
 *         "dataSourceId", "reference-db",
 *         "timeout", "5000ms"
 *     )))
 *     .context(Map.of(
 *         "tenantId", event.getTenantId(),
 *         "correlationId", event.getCorrelationId(),
 *         "userId", securityContext.getPrincipal()
 *     ))
 *     .priority(8)
 *     .build();
 *
 * // Queue for processing
 * agentQueue.enqueue(enrichStep);
 * }
 *
 * @see AgentSpec
 * @see Priority handling
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose immutable agent work unit specification
 * @doc.pattern value-object, immutable, builder, priority-queue-item
 * @doc.test-hints immutability-verification, priority-comparison, context-passing, equality-testing, defensive-copying
 */
public final class AgentStep {

    private final String id;
    private final String agentId;
    private final String type;
    private final String parameters;
    private final Map<String, String> context;
    private final Instant createdAt;
    private final int priority;

    private AgentStep(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id cannot be null");
        this.agentId = Objects.requireNonNull(builder.agentId, "agentId cannot be null");
        this.type = Objects.requireNonNull(builder.type, "type cannot be null");
        this.parameters = builder.parameters != null ? builder.parameters : "{}";
        this.context = Map.copyOf(builder.context);
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.priority = builder.priority;
    }

    public String getId() {
        return id;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getType() {
        return type;
    }

    public String getParameters() {
        return parameters;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getPriority() {
        return priority;
    }

    public String getContextValue(String key) {
        return context.get(key);
    }

    public boolean hasHigherPriorityThan(AgentStep other) {
        return this.priority > other.priority;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String agentId;
        private String type;
        private String parameters = "{}";
        private Map<String, String> context = Map.of();
        private Instant createdAt;
        private int priority = 0;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder parameters(String parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder context(Map<String, String> context) {
            this.context = context;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public AgentStep build() {
            return new AgentStep(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "AgentStep{id='%s', agentId='%s', type='%s', priority=%d, createdAt=%s}",
            id, agentId, type, priority, createdAt
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentStep)) {
            return false;
        }
        AgentStep agentStep = (AgentStep) o;
        return priority == agentStep.priority
            && Objects.equals(id, agentStep.id)
            && Objects.equals(agentId, agentStep.agentId)
            && Objects.equals(type, agentStep.type)
            && Objects.equals(parameters, agentStep.parameters)
            && Objects.equals(context, agentStep.context)
            && Objects.equals(createdAt, agentStep.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, agentId, type, parameters, context, createdAt, priority);
    }
}
