package com.ghatana.virtualorg.framework.flow;

import com.ghatana.virtualorg.framework.event.EventBuilder;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Generic engine for executing cross-department event flows.
 *
 * <p>
 * <b>Purpose</b><br>
 * Routes events between departments based on configurable flow definitions.
 * Supports event transformation, conditional routing, and AI-assisted decisions.
 * Flows are defined in YAML configuration and executed at runtime.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * FlowEngine engine = new FlowEngine(publisher);
 *
 * // Register a flow
 * engine.registerFlow(FlowDefinition.builder()
 *     .id("engineering-to-qa")
 *     .sourceDepartment("engineering")
 *     .targetDepartment("qa")
 *     .sourceEventType("FeatureRequestCreated")
 *     .targetEventType("TestSuiteCreated")
 *     .addTransformation("test_type", "functional")
 *     .build());
 *
 * // Execute flow when event arrives
 * engine.routeEvent("FeatureRequestCreated", payload, "tenant-123");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Generic cross-department event flow engine
 * @doc.layer product
 * @doc.pattern Router
 */
public class FlowEngine {

    private static final Logger logger = LoggerFactory.getLogger(FlowEngine.class);

    private final EventPublisher publisher;
    private final Map<String, List<FlowDefinition>> flowsBySourceEvent;
    private final Map<String, FlowDefinition> flowsById;

    /**
     * Creates a new FlowEngine.
     *
     * @param publisher event publisher for emitting transformed events
     */
    public FlowEngine(EventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.flowsBySourceEvent = new HashMap<>();
        this.flowsById = new HashMap<>();
    }

    /**
     * Registers a flow definition.
     *
     * @param flow flow definition to register
     */
    public void registerFlow(FlowDefinition flow) {
        Objects.requireNonNull(flow, "flow must not be null");

        flowsById.put(flow.id(), flow);
        flowsBySourceEvent
                .computeIfAbsent(flow.sourceEventType(), k -> new ArrayList<>())
                .add(flow);

        logger.info("Registered flow: {} ({} -> {})",
                flow.id(), flow.sourceDepartment(), flow.targetDepartment());
    }

    /**
     * Unregisters a flow by ID.
     *
     * @param flowId flow ID to unregister
     */
    public void unregisterFlow(String flowId) {
        FlowDefinition removed = flowsById.remove(flowId);
        if (removed != null) {
            List<FlowDefinition> flows = flowsBySourceEvent.get(removed.sourceEventType());
            if (flows != null) {
                flows.removeIf(f -> f.id().equals(flowId));
            }
            logger.info("Unregistered flow: {}", flowId);
        }
    }

    /**
     * Routes an event through matching flows.
     *
     * @param sourceEventType source event type
     * @param payload         event payload
     * @param tenantId        tenant identifier
     * @return list of flow execution results
     */
    public List<FlowResult> routeEvent(
            String sourceEventType,
            Map<String, Object> payload,
            String tenantId) {

        List<FlowDefinition> matchingFlows = flowsBySourceEvent.get(sourceEventType);
        if (matchingFlows == null || matchingFlows.isEmpty()) {
            logger.debug("No flows registered for event type: {}", sourceEventType);
            return List.of();
        }

        List<FlowResult> results = new ArrayList<>();
        for (FlowDefinition flow : matchingFlows) {
            try {
                FlowResult result = executeFlow(flow, payload, tenantId);
                results.add(result);
            } catch (Exception e) {
                logger.error("Flow execution failed: {}", flow.id(), e);
                results.add(new FlowResult(flow.id(), false, e.getMessage()));
            }
        }

        return results;
    }

    /**
     * Executes a single flow.
     */
    private FlowResult executeFlow(
            FlowDefinition flow,
            Map<String, Object> sourcePayload,
            String tenantId) {

        // Check conditions
        if (!evaluateConditions(flow.conditions(), sourcePayload)) {
            logger.debug("Flow conditions not met: {}", flow.id());
            return new FlowResult(flow.id(), true, "Conditions not met - skipped");
        }

        // Transform payload
        Map<String, Object> transformedPayload = transformPayload(flow, sourcePayload);

        // Add flow metadata
        transformedPayload.put("_source_department", flow.sourceDepartment());
        transformedPayload.put("_target_department", flow.targetDepartment());
        transformedPayload.put("_flow_id", flow.id());

        // Publish transformed event using EventBuilder
        EventBuilder.publishEvent(publisher, flow.targetEventType(), transformedPayload, tenantId);

        logger.info("Flow executed: {} -> {} ({})",
                flow.sourceEventType(), flow.targetEventType(), flow.id());

        return new FlowResult(flow.id(), true, "Success");
    }

    /**
     * Transforms payload according to flow definition.
     */
    private Map<String, Object> transformPayload(
            FlowDefinition flow,
            Map<String, Object> sourcePayload) {

        Map<String, Object> transformed = new HashMap<>(sourcePayload);

        // Apply field mappings
        for (Map.Entry<String, String> mapping : flow.fieldMappings().entrySet()) {
            String sourceField = mapping.getKey();
            String targetField = mapping.getValue();
            if (sourcePayload.containsKey(sourceField)) {
                transformed.put(targetField, sourcePayload.get(sourceField));
            }
        }

        // Apply static transformations
        transformed.putAll(flow.transformations());

        return transformed;
    }

    /**
     * Evaluates flow conditions.
     */
    private boolean evaluateConditions(
            Map<String, Object> conditions,
            Map<String, Object> payload) {

        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, Object> condition : conditions.entrySet()) {
            String field = condition.getKey();
            Object expectedValue = condition.getValue();
            Object actualValue = payload.get(field);

            if (!Objects.equals(expectedValue, actualValue)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets all registered flows.
     *
     * @return unmodifiable map of flows by ID
     */
    public Map<String, FlowDefinition> getFlows() {
        return Collections.unmodifiableMap(flowsById);
    }

    /**
     * Gets a flow by ID.
     *
     * @param flowId flow ID
     * @return flow definition or null
     */
    public FlowDefinition getFlow(String flowId) {
        return flowsById.get(flowId);
    }

    /**
     * Flow definition record.
     */
    public record FlowDefinition(
            String id,
            String sourceDepartment,
            String targetDepartment,
            String sourceEventType,
            String targetEventType,
            Map<String, String> fieldMappings,
            Map<String, Object> transformations,
            Map<String, Object> conditions) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String id;
            private String sourceDepartment;
            private String targetDepartment;
            private String sourceEventType;
            private String targetEventType;
            private final Map<String, String> fieldMappings = new HashMap<>();
            private final Map<String, Object> transformations = new HashMap<>();
            private final Map<String, Object> conditions = new HashMap<>();

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder sourceDepartment(String sourceDepartment) {
                this.sourceDepartment = sourceDepartment;
                return this;
            }

            public Builder targetDepartment(String targetDepartment) {
                this.targetDepartment = targetDepartment;
                return this;
            }

            public Builder sourceEventType(String sourceEventType) {
                this.sourceEventType = sourceEventType;
                return this;
            }

            public Builder targetEventType(String targetEventType) {
                this.targetEventType = targetEventType;
                return this;
            }

            public Builder addFieldMapping(String sourceField, String targetField) {
                this.fieldMappings.put(sourceField, targetField);
                return this;
            }

            public Builder addTransformation(String field, Object value) {
                this.transformations.put(field, value);
                return this;
            }

            public Builder addCondition(String field, Object expectedValue) {
                this.conditions.put(field, expectedValue);
                return this;
            }

            public FlowDefinition build() {
                Objects.requireNonNull(id, "id must not be null");
                Objects.requireNonNull(sourceDepartment, "sourceDepartment must not be null");
                Objects.requireNonNull(targetDepartment, "targetDepartment must not be null");
                Objects.requireNonNull(sourceEventType, "sourceEventType must not be null");
                Objects.requireNonNull(targetEventType, "targetEventType must not be null");

                return new FlowDefinition(
                        id,
                        sourceDepartment,
                        targetDepartment,
                        sourceEventType,
                        targetEventType,
                        Map.copyOf(fieldMappings),
                        Map.copyOf(transformations),
                        Map.copyOf(conditions));
            }
        }
    }

    /**
     * Flow execution result.
     */
    public record FlowResult(String flowId, boolean success, String message) {
    }
}
