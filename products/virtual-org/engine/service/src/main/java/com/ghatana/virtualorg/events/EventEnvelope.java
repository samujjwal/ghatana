package com.ghatana.virtualorg.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical event envelope for Virtual Organization system.
 * 
 * <p>All events emitted by agents MUST use this envelope for consistent routing,
 * correlation, and observability. This implements REQ-001 (Event Envelope v1)
 * from the requirements checklist.
 * 
 * <p><b>Design Principles:</b>
 * <ul>
 *   <li>Immutable value object for thread safety</li>
 *   <li>Correlation ID for distributed tracing</li>
 *   <li>Causation ID for causal event chains</li>
 *   <li>Tenant isolation support</li>
 *   <li>Metadata extensibility</li>
 * </ul>
 * 
 * <p><b>Event Flow:</b>
 * <pre>{@code
 * // Agent emits decision event
 * EventEnvelope decision = EventEnvelope.builder()
 *     .eventType("agent.decision.made")
 *     .eventId(UUID.randomUUID().toString())
 *     .correlationId(context.correlationId())
 *     .source("ceo-agent-001")
 *     .payload(decisionData)
 *     .build();
 *     
 * eventBus.publish(decision);
 * 
 * // Another agent receives and creates child event
 * EventEnvelope task = EventEnvelope.builder()
 *     .eventType("task.created")
 *     .correlationId(decision.correlationId())  // Same correlation
 *     .causationId(decision.eventId())          // Parent event ID
 *     .source("cto-agent-001")
 *     .payload(taskData)
 *     .build();
 * }</pre>
 * 
 * <p>Per WORLD_CLASS_DESIGN_MASTER.md Section III.E (Event-Driven Architecture)
 * and copilot-instructions.md (event envelope requirements).
 * 
 * @see com.ghatana.virtualorg.events.EventBus
 * @see com.ghatana.virtualorg.events.EventType
 * @doc.type record
 * @doc.purpose Canonical event envelope with correlation and causation tracking
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class EventEnvelope {
    
    @NotNull
    @JsonProperty("event_id")
    private final String eventId;
    
    @NotNull
    @JsonProperty("event_type")
    private final String eventType;
    
    @NotNull
    @JsonProperty("correlation_id")
    private final String correlationId;
    
    @Nullable
    @JsonProperty("causation_id")
    private final String causationId;
    
    @NotNull
    @JsonProperty("source")
    private final String source;
    
    @NotNull
    @JsonProperty("timestamp")
    private final Instant timestamp;
    
    @Nullable
    @JsonProperty("tenant_id")
    private final String tenantId;
    
    @NotNull
    @JsonProperty("payload")
    private final Map<String, Object> payload;
    
    @NotNull
    @JsonProperty("metadata")
    private final Map<String, String> metadata;
    
    private EventEnvelope(Builder builder) {
        this.eventId = Objects.requireNonNull(builder.eventId, "eventId cannot be null");
        this.eventType = Objects.requireNonNull(builder.eventType, "eventType cannot be null");
        this.correlationId = Objects.requireNonNull(builder.correlationId, "correlationId cannot be null");
        this.causationId = builder.causationId;
        this.source = Objects.requireNonNull(builder.source, "source cannot be null");
        this.timestamp = Objects.requireNonNull(builder.timestamp, "timestamp cannot be null");
        this.tenantId = builder.tenantId;
        this.payload = Map.copyOf(Objects.requireNonNull(builder.payload, "payload cannot be null"));
        this.metadata = Map.copyOf(builder.metadata);
    }
    
    // Getters
    @NotNull public String eventId() { return eventId; }
    @NotNull public String eventType() { return eventType; }
    @NotNull public String correlationId() { return correlationId; }
    @Nullable public String causationId() { return causationId; }
    @NotNull public String source() { return source; }
    @NotNull public Instant timestamp() { return timestamp; }
    @Nullable public String tenantId() { return tenantId; }
    @NotNull public Map<String, Object> payload() { return payload; }
    @NotNull public Map<String, String> metadata() { return metadata; }
    
    /**
     * Creates a new builder for constructing event envelopes.
     * 
     * @return New builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a child event builder with correlation and causation tracking.
     * 
     * <p>The child event will have:
     * <ul>
     *   <li>Same correlationId as parent (trace entire flow)</li>
     *   <li>Parent's eventId as causationId (direct causation)</li>
     *   <li>New eventId (unique identity)</li>
     *   <li>Same tenantId as parent (tenant isolation)</li>
     * </ul>
     * 
     * @return Builder pre-configured with parent correlation
     */
    public Builder childEvent() {
        return builder()
                .correlationId(this.correlationId)
                .causationId(this.eventId)
                .tenantId(this.tenantId);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventEnvelope that = (EventEnvelope) o;
        return eventId.equals(that.eventId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
    
    @Override
    public String toString() {
        return "EventEnvelope{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", causationId='" + causationId + '\'' +
                ", source='" + source + '\'' +
                ", timestamp=" + timestamp +
                ", tenantId='" + tenantId + '\'' +
                '}';
    }
    
    /**
     * Builder for EventEnvelope with fluent API.
     * 
     * <p>Automatically generates eventId, correlationId (if not provided),
     * and timestamp to reduce boilerplate.
     */
    public static final class Builder {
        private String eventId = UUID.randomUUID().toString();
        private String eventType;
        private String correlationId = UUID.randomUUID().toString();
        private String causationId;
        private String source;
        private Instant timestamp = Instant.now();
        private String tenantId;
        private Map<String, Object> payload = new HashMap<>();
        private Map<String, String> metadata = new HashMap<>();
        
        private Builder() {}
        
        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }
        
        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder causationId(String causationId) {
            this.causationId = causationId;
            return this;
        }
        
        public Builder source(String source) {
            this.source = source;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder payload(Map<String, Object> payload) {
            this.payload = new HashMap<>(payload);
            return this;
        }
        
        public Builder addPayload(String key, Object value) {
            this.payload.put(key, value);
            return this;
        }
        
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }
        
        public Builder addMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }
        
        /**
         * Builds the immutable EventEnvelope.
         * 
         * @return Validated EventEnvelope instance
         * @throws NullPointerException if required fields are null
         */
        public EventEnvelope build() {
            return new EventEnvelope(this);
        }
    }
}
