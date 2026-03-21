package com.ghatana.pattern.operator.spi;

import com.ghatana.pattern.api.model.PatternWindowSpec;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;
import java.util.Map;

/**
 * Context for operator validation during pattern compilation.
 *
 * <p>ValidationContext provides compile-time information for operator validation:
 * <ul>
 *   <li><b>Identifiers</b>: Pattern and tenant IDs for multi-tenant isolation</li>
 *   <li><b>Window Spec</b>: Pattern-level window configuration (optional)</li>
 *   <li><b>Event Types</b>: Available event types for the tenant</li>
 *   <li><b>Event Type Registry</b>: Catalog integration for event type validation (optional)</li>
 *   <li><b>Global Parameters</b>: Pattern-level configuration passed to operators</li>
 *   <li><b>Metrics Registry</b>: Micrometer registry for validation metrics</li>
 * </ul>
 * 
 * @doc.pattern Context Object Pattern (validation context), Builder Pattern (construction)
 * @doc.compiler-phase Validation Context (compile-time environment for operator validation)
 * @doc.threading Thread-safe after construction (immutable context)
 * @doc.performance O(1) for field access; O(1) event type lookup (with registry)
 * @doc.memory O(1) for fixed fields + O(e+p) where e=event types, p=parameters
 * @doc.immutability Immutable after build(); use builder for modifications
 * @doc.apiNote Pass to operator.validate() during compilation; provides tenant-scoped validation
 * @doc.limitation No runtime context; compile-time only (no event data access)
 * 
 * <h2>Event Type Registry Integration</h2>
 * <p>The optional {@code EventTypeRegistry} interface enables validation against
 * the event catalog module. If not provided, validation assumes all event types exist
 * (fail-open for backward compatibility).
 * 
 * <pre>
 * ValidationContext context = ValidationContext.builder()
 *   .patternId(patternId)
 *   .tenantId("tenant-123")
 *   .eventTypeRegistry((eventType, tenant) -&gt; 
 *     catalogService.exists(eventType, tenant))
 *   .build();
 * 
 * // Operators can validate event types during compilation
 * if (!context.eventTypeExists("order.created", tenantId)) {
 *   throw new PatternValidationException("Event type not found");
 * }
 * </pre>
 */
public class ValidationContext {

    private final String patternId;
    private final String tenantId;
    private final PatternWindowSpec window;
    private final List<String> availableEventTypes;
    private final Map<String, Object> globalParameters;
    private final MeterRegistry meterRegistry;
    private final EventTypeRegistry eventTypeRegistry;
    
    /**
     * Interface for event type registry operations.
     *
     * <p>Implementations can integrate with catalog module or other event type repositories.
     */
    public interface EventTypeRegistry {
        /**
         * Check if an event type exists in the registry.
         *
         * @param eventType the event type identifier (e.g., "com.ghatana.financial.TransactionEvent")
         * @param tenantId the tenant identifier
         * @return true if the event type exists
         */
        boolean exists(String eventType, String tenantId);

        /**
         * Check if an event type is active (status = ACTIVE).
         *
         * @param eventType the event type identifier
         * @param tenantId the tenant identifier
         * @return true if the event type exists and is active
         */
        boolean isActive(String eventType, String tenantId);
    }

    // Private constructor - use Builder
    private ValidationContext(Builder builder) {
        this.patternId = builder.patternId;
        this.tenantId = builder.tenantId;
        this.window = builder.window;
        this.availableEventTypes = builder.availableEventTypes;
        this.globalParameters = builder.globalParameters;
        this.meterRegistry = builder.meterRegistry;
        this.eventTypeRegistry = builder.eventTypeRegistry;
    }
    
    // Getters
    public String getPatternId() { return patternId; }
    public String getTenantId() { return tenantId; }
    public PatternWindowSpec getWindow() { return window; }
    public List<String> getAvailableEventTypes() { return availableEventTypes; }
    public Map<String, Object> getGlobalParameters() { return globalParameters; }
    public MeterRegistry getMeterRegistry() { return meterRegistry; }
    public EventTypeRegistry getEventTypeRegistry() { return eventTypeRegistry; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String patternId;
        private String tenantId;
        private PatternWindowSpec window;
        private List<String> availableEventTypes;
        private Map<String, Object> globalParameters;
        private MeterRegistry meterRegistry;
        private EventTypeRegistry eventTypeRegistry;

        public Builder patternId(String patternId) { this.patternId = patternId; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder window(PatternWindowSpec window) { this.window = window; return this; }
        public Builder availableEventTypes(List<String> availableEventTypes) { this.availableEventTypes = availableEventTypes; return this; }
        public Builder globalParameters(Map<String, Object> globalParameters) { this.globalParameters = globalParameters; return this; }
        public Builder meterRegistry(MeterRegistry meterRegistry) { this.meterRegistry = meterRegistry; return this; }
        public Builder eventTypeRegistry(EventTypeRegistry eventTypeRegistry) { this.eventTypeRegistry = eventTypeRegistry; return this; }

        public ValidationContext build() {
            return new ValidationContext(this);
        }
    }
    
    /**
     * Check if a specific event type is available.
     * 
     * @param eventType the event type to check
     * @return true if the event type is available
     */
    public boolean isEventTypeAvailable(String eventType) {
        return availableEventTypes != null && availableEventTypes.contains(eventType);
    }
    
    /**
     * Get a global parameter value by key.
     * 
     * @param key the parameter key
     * @return the parameter value, or null if not found
     */
    public Object getGlobalParameter(String key) {
        return globalParameters != null ? globalParameters.get(key) : null;
    }
    
    /**
     * Get a global parameter value by key with a default value.
     *
     * @param key the parameter key
     * @param defaultValue the default value to return if not found
     * @return the parameter value or the default value
     */
    public Object getGlobalParameter(String key, Object defaultValue) {
        Object value = getGlobalParameter(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Check if event type registry is available.
     *
     * @return true if event type registry is configured
     */
    public boolean hasEventTypeRegistry() {
        return eventTypeRegistry != null;
    }

    /**
     * Check if an event type exists in the registry.
     *
     * @param eventType the event type identifier
     * @param tenantId the tenant identifier
     * @return true if the event type exists (or true if no registry configured)
     */
    public boolean eventTypeExists(String eventType, String tenantId) {
        if (eventTypeRegistry == null) {
            return true; // If no registry, assume event type exists (fail-open for backward compatibility)
        }
        return eventTypeRegistry.exists(eventType, tenantId);
    }

    /**
     * Check if an event type is active.
     *
     * @param eventType the event type identifier
     * @param tenantId the tenant identifier
     * @return true if the event type is active (or true if no registry configured)
     */
    public boolean isEventTypeActive(String eventType, String tenantId) {
        if (eventTypeRegistry == null) {
            return true; // If no registry, assume event type is active (fail-open for backward compatibility)
        }
        return eventTypeRegistry.isActive(eventType, tenantId);
    }

    @Override
    public String toString() {
        return "ValidationContext{" +
                "patternId='" + patternId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", availableEventTypes=" + availableEventTypes +
                ", hasEventTypeRegistry=" + hasEventTypeRegistry() +
                '}';
    }
}





