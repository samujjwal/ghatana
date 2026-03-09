package com.ghatana.platform.domain.domain.event;

import com.ghatana.contracts.common.v1.CompatibilityPolicyProto;
import com.ghatana.contracts.event.v1.EventContextTypeProto;
import com.ghatana.contracts.event.v1.EventStorageHintsPojo;
import com.ghatana.contracts.event.v1.GovernancePojo;
import com.ghatana.contracts.event.v1.LifecycleStatusProto;
import com.ghatana.contracts.event.v1.SemanticVersionPojo;
import com.ghatana.platform.core.exception.EventCreationException;
import com.ghatana.platform.core.exception.SchemaValidationException;

import java.util.Map;

/**
 * Event type schema definition with validation rules and metadata.
 * 
 * <p>
 * Defines the structure, validation rules, and metadata for a specific event type.
 * Event types act as schemas that validate incoming events and describe their
 * structure to consumers. Implemented by {@link GEventType} which provides the
 * canonical implementation with full governance, storage hints, and lifecycle support.
 * </p>
 *
 * <h2>Type Definition Components</h2>
 * <dl>
 *   <dt><b>Identification</b></dt>
 *   <dd>getName(), getNamespace(), getVersion() - fully qualified event type</dd>
 *
 *   <dt><b>Structure</b></dt>
 *   <dd>getHeaders(), getPayload(), getEventParameterSpec() - field definitions</dd>
 *
 *   <dt><b>Validation</b></dt>
 *   <dd>validate(Event) - schema validation against event structure</dd>
 *
 *   <dt><b>Governance</b></dt>
 *   <dd>getGovernance(), getStatus(), getCompatibilityPolicy() - lifecycle and evolution</dd>
 *
 *   <dt><b>Storage & Context</b></dt>
 *   <dd>getStorageHints(), getContextType() - optimization and semantic information</dd>
 *
 *   <dt><b>Metadata</b></dt>
 *   <dd>getDescription(), getTags(), getExamples() - documentation and discovery</dd>
 * </dl>
 *
 * <h2>Architecture Role</h2>
 * <p>
 * Event types are central to the schema and governance layer:
 * <ul>
 *   <li><b>Schema Definition</b>: Defines event structure and field semantics</li>
 *   <li><b>Validation</b>: Validates incoming events against schema definition</li>
 *   <li><b>Event Creation</b>: Factory method for creating typed events from byte data</li>
 *   <li><b>Type Discovery</b>: Published in event catalogs for consumer discovery</li>
 *   <li><b>Governance</b>: Tracks lifecycle, deprecation, compatibility policies</li>
 *   <li><b>Evolution</b>: Supports schema versioning for backward compatibility</li>
 *   <li><b>Routing</b>: Event routers use type information for pattern matching</li>
 * </ul>
 * </p>
 *
 * <h2>Event Type Dimensions</h2>
 * <p>
 * Event types can define multiple dimensions:
 * <ul>
 *   <li><b>Interval-Based</b>: isIntervalBased() - for time-windowed aggregations</li>
 *   <li><b>Granularity</b>: getGranularity() - time unit (seconds, minutes, hours)</li>
 *   <li><b>Confidence</b>: getSupportsConfidence() - can events carry confidence scores</li>
 *   <li><b>Aliases</b>: getAliases() - alternative names for backward compatibility</li>
 * </ul>
 * </p>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * EventType type = registry.getEventType("InfrastructureAlert", "v1");
 *
 * // Validate event structure
 * try {
 *   type.validate(event);  // Throws SchemaValidationException if invalid
 * } catch (SchemaValidationException e) {
 *   // Handle validation error
 * }
 *
 * // Access schema information
 * Map<String, EventParameterSpec> headers = type.getHeaders();
 * Map<String, EventParameterSpec> payload = type.getPayload();
 *
 * // Query governance and lifecycle
 * LifecycleStatusProto status = type.getStatus();
 * if (status == LifecycleStatusProto.DEPRECATED) {
 *   logger.warn("Event type {} is deprecated", type.getId());
 * }
 *
 * // Create typed event from raw data
 * Event event = type.createEvent(rawBytes);
 * }</pre>
 *
 * @doc.type interface
 * @doc.layer domain
 * @doc.purpose event schema definition with validation and governance
 * @doc.pattern strategy (each event type encapsulates distinct validation strategy)
 * @doc.test-hints verify validation rules, test schema enforcement, validate event creation, test lifecycle transitions
 *
 * @see GEventType (canonical implementation with full feature set)
 * @see com.ghatana.platform.domain.domain.event.Event (events validated against EventType)
 * @see EventParameterSpec (field-level parameter definitions)
 * @see GovernancePojo (governance and lifecycle information)
 */
public interface EventType {
    String getVersion();

    void validate(Event event) throws SchemaValidationException;

    EventParameterSpec getHeader(String name);

    EventParameterSpec getPayload(String name);

    boolean hasAlias(String alias);

    Event createEvent(byte[] data) throws SchemaValidationException;

    String getTenantId();

    String getName();

    String getCategory();

    String getNamespace();

    SemanticVersionPojo getSemanticVersion();

    EventContextTypeProto getContextType();

    boolean isIntervalBased();

    long getGranularity();

    String getDescription();

    java.util.Set<String> getTags();

    java.util.List<String> getExamples();

    Map<String, EventParameterSpec> getHeaders();

    Map<String, EventParameterSpec> getPayload();

    Boolean getSupportsConfidence();

    java.util.Set<String> getAliases();

    GovernancePojo getGovernance();

    EventStorageHintsPojo getStorageHints();

    LifecycleStatusProto getStatus();

    String getStatusMessage();

    CompatibilityPolicyProto getCompatibilityPolicy();
    /**
     * Gets the unique identifier for this event type.
     * @return The event type ID in the format "name-version"
     */
    default String getId() {
        return getTenantId() + "/" + getNamespace() + "/" + getName() + ":" + getVersion();
    }
}
