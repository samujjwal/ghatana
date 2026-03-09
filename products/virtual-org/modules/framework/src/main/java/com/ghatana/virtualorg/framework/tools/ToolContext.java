package com.ghatana.virtualorg.framework.tools;

import com.ghatana.virtualorg.framework.runtime.AgentContext;

import java.util.Map;
import java.util.Objects;

/**
 * Context for tool execution, providing access to agent context and execution
 * metadata.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides tools with the contextual information they need during execution: -
 * Agent identity and permissions - Organization context - Execution metadata -
 * Trace/correlation IDs for observability
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ToolContext context = ToolContext.builder()
 *     .agentContext(agentContext)
 *     .correlationId("trace-123")
 *     .build();
 *
 * // In tool execution
 * String agentId = context.getAgentId();
 * String traceId = context.getCorrelationId();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Tool execution context
 * @doc.layer product
 * @doc.pattern Context Object
 */
public final class ToolContext {

    private final AgentContext agentContext;
    private final String correlationId;
    private final String requestId;
    private final Map<String, String> headers;
    private final Map<String, Object> metadata;

    private ToolContext(Builder builder) {
        this.agentContext = builder.agentContext;
        this.correlationId = builder.correlationId != null ? builder.correlationId : generateId();
        this.requestId = builder.requestId != null ? builder.requestId : generateId();
        this.headers = builder.headers != null ? Map.copyOf(builder.headers) : Map.of();
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }

    /**
     * Convenience constructor for tests and simple use cases.
     *
     * @param agentId The agent ID
     * @param taskId The task ID (stored in metadata)
     * @param metadata Additional metadata
     */
    public ToolContext(String agentId, String taskId, Map<String, Object> metadata) {
        this.agentContext = null; // No full context in simple mode
        this.correlationId = generateId();
        this.requestId = generateId();
        this.headers = Map.of();
        Map<String, Object> meta = new java.util.HashMap<>(metadata != null ? metadata : Map.of());
        meta.put("agentId", agentId);
        meta.put("taskId", taskId);
        this.metadata = Map.copyOf(meta);
    }

    private static String generateId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    // ========== Agent Context Accessors ==========
    public AgentContext getAgentContext() {
        return agentContext;
    }

    /**
     * Gets the executing agent's ID.
     *
     * @return The agent ID
     */
    public String getAgentId() {
        if (agentContext != null) {
            return agentContext.getAgentId();
        }
        // Fallback to metadata for simple constructor
        Object id = metadata.get("agentId");
        return id != null ? id.toString() : "unknown";
    }

    /**
     * Gets the executing agent's ID (alias for getAgentId).
     *
     * @return The agent ID
     */
    public String agentId() {
        return getAgentId();
    }

    /**
     * Gets the organization ID.
     *
     * @return The organization ID
     */
    public String getOrganizationId() {
        return agentContext != null ? agentContext.getOrganizationId() : null;
    }

    /**
     * Gets the department ID.
     *
     * @return The department ID
     */
    public String getDepartmentId() {
        return agentContext != null ? agentContext.getDepartmentId() : null;
    }

    // ========== Trace/Correlation ==========
    /**
     * Gets the correlation ID for distributed tracing.
     *
     * @return The correlation ID
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Gets the request ID for this specific execution.
     *
     * @return The request ID
     */
    public String getRequestId() {
        return requestId;
    }

    // ========== Headers & Metadata ==========
    /**
     * Gets headers to pass to external services.
     *
     * @return Immutable map of headers
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Gets a specific header value.
     *
     * @param name The header name
     * @return The header value or null
     */
    public String getHeader(String name) {
        return headers.get(name);
    }

    /**
     * Gets execution metadata.
     *
     * @return Immutable map of metadata
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Gets a specific metadata value.
     *
     * @param key The metadata key
     * @param type The expected type
     * @param <T> Type parameter
     * @return The value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    // ========== Builder ==========
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a context from an agent context.
     *
     * @param agentContext The agent context
     * @return A new ToolContext
     */
    public static ToolContext from(AgentContext agentContext) {
        return builder().agentContext(agentContext).build();
    }

    public static final class Builder {

        private AgentContext agentContext;
        private String correlationId;
        private String requestId;
        private Map<String, String> headers;
        private Map<String, Object> metadata;

        private Builder() {
        }

        public Builder agentContext(AgentContext agentContext) {
            this.agentContext = agentContext;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ToolContext build() {
            return new ToolContext(this);
        }
    }

    @Override
    public String toString() {
        return "ToolContext{"
                + "agentId='" + getAgentId() + '\''
                + ", correlationId='" + correlationId + '\''
                + ", requestId='" + requestId + '\''
                + '}';
    }
}
