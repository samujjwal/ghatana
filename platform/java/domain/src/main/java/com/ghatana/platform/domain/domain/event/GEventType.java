package com.ghatana.platform.domain.domain.event;

import com.ghatana.contracts.common.v1.AdaptationPolicyPojo;
import com.ghatana.contracts.common.v1.AuditPolicyPojo;
import com.ghatana.contracts.common.v1.CompatibilityPolicyProto;
import com.ghatana.contracts.common.v1.ProvenancePolicyPojo;
import com.ghatana.contracts.common.v1.StatsCollectionPolicyPojo;
import com.ghatana.contracts.event.v1.EventContextTypeProto;
import com.ghatana.contracts.event.v1.EventStorageHintsPojo;
import com.ghatana.contracts.event.v1.GovernancePojo;
import com.ghatana.contracts.event.v1.LifecycleStatusProto;
import com.ghatana.contracts.event.v1.SemanticVersionPojo;
import com.ghatana.platform.core.exception.EventCreationException;
import com.ghatana.platform.core.exception.SchemaValidationException;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * {@code GEventType} is the canonical immutable implementation of {@link EventType},
 * defining event schema, governance policies, and lifecycle management for events.
 *
 * <h2>Purpose</h2>
 * Provides complete event type definition with:
 * <ul>
 *   <li>Schema definition (headers, payload parameter specifications)</li>
 *   <li>Governance policies (audit, retention, adaptation, provenance)</li>
 *   <li>Lifecycle management (DRAFT, ACTIVE, DEPRECATED, RETIRED)</li>
 *   <li>Storage optimization hints (hot/cold storage, TTL)</li>
 *   <li>Version management and compatibility policies</li>
 *   <li>Multi-tenancy with tenant-scoped type definitions</li>
 * </ul>
 *
 * <h2>Structure</h2>
 * Event type composition:
 * <ul>
 *   <li><b>tenantId</b>: Multi-tenant owner (required)</li>
 *   <li><b>name</b>: Event type name (required, e.g., "ORDER_PLACED")</li>
 *   <li><b>namespace</b>: Organizational namespace (default: "public")</li>
 *   <li><b>category</b>: Logical grouping (e.g., "order", "payment")</li>
 *   <li><b>semanticVersion</b>: SemVer version (MAJOR.MINOR.PATCH)</li>
 *   <li><b>contextType</b>: Event context (TEMPORAL, SPATIAL, CAUSAL)</li>
 *   <li><b>intervalBased</b>: Whether event represents time interval</li>
 *   <li><b>granularity</b>: Time granularity in milliseconds (default: 60000)</li>
 *   <li><b>description</b>: Human-readable type description</li>
 *   <li><b>tags</b>: Free-form tags for discovery</li>
 *   <li><b>examples</b>: Example event payloads (JSON strings)</li>
 *   <li><b>headers</b>: Map of header field specifications (unmodifiable)</li>
 *   <li><b>payload</b>: Map of payload field specifications (unmodifiable)</li>
 *   <li><b>supportsConfidence</b>: Whether events support confidence metadata</li>
 *   <li><b>aliases</b>: Alternative names for type discovery</li>
 *   <li><b>governance</b>: Policy container (audit, retention, adaptation)</li>
 *   <li><b>storageHints</b>: Storage optimization directives</li>
 *   <li><b>status</b>: Lifecycle status (DRAFT → ACTIVE → DEPRECATED → RETIRED)</li>
 *   <li><b>statusMessage</b>: Status explanation</li>
 *   <li><b>compatibilityPolicy</b>: Version compatibility constraints</li>
 *   <li><b>owner</b>: Team/service responsible for type</li>
 *   <li><b>deprecated</b>: Whether type is deprecated</li>
 *   <li><b>deprecationMessage</b>: Deprecation reason and timeline</li>
 *   <li><b>createdAt, updatedAt</b>: Temporal tracking</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Defined by</b>: Schema registry, event type catalog</li>
 *   <li><b>Used by</b>: Ingestion service, event processors, storage systems</li>
 *   <li><b>Validated against</b>: Incoming events for schema compliance</li>
 *   <li><b>Stored in</b>: Schema registry, distributed cache</li>
 *   <li><b>Related to</b>: {@link Event}, {@link EventParameterSpec}, {@link EventId}</li>
 * </ul>
 *
 * <h2>Schema Definition</h2>
 * Event types define two parameter maps:
 * <ul>
 *   <li><b>headers</b>: Transport-level metadata (correlationId, causationId, traceId, etc.)</li>
 *   <li><b>payload</b>: Event-specific data fields</li>
 * </ul>
 * Each field maps to {@link EventParameterSpec} defining type, constraints, and validation.
 *
 * <h2>Lifecycle Management</h2>
 * Event types progress through states:
 * <ul>
 *   <li><b>DRAFT</b>: Type under development, not accepting events</li>
 *   <li><b>ACTIVE</b>: Type accepting events and being processed</li>
 *   <li><b>DEPRECATED</b>: Type accepting events but marked for removal</li>
 *   <li><b>RETIRED</b>: Type no longer accepting events</li>
 * </ul>
 * Transition via {@link #transition(LifecycleStatusProto, String)} creating new immutable instance.
 *
 * <h2>Governance Policies</h2>
 * Comprehensive governance through {@link GovernancePojo}:
 * <ul>
 *   <li><b>Audit Policy</b>: What to audit (creation, modification, access)</li>
 *   <li><b>Retention Policy</b>: How long to retain (default: 30 days)</li>
 *   <li><b>Adaptation Policy</b>: Schema evolution rules</li>
 *   <li><b>Provenance Policy</b>: Tracking origin/transformation</li>
 *   <li><b>Stats Collection Policy</b>: Metrics to capture</li>
 *   <li><b>Legal Hold</b>: Whether legal hold can be applied</li>
 * </ul>
 *
 * <h2>Storage Optimization</h2>
 * {@link EventStorageHintsPojo} provides storage directives:
 * <ul>
 *   <li><b>Hot Storage</b>: Keep in fast storage (else cold storage)</li>
 *   <li><b>TTL (Days)</b>: Time to live in storage</li>
 *   <li>Enables automatic tiering and archival</li>
 * </ul>
 *
 * <h2>Multi-Tenancy</h2>
 * Event type includes {@code tenantId} ensuring:
 * <ul>
 *   <li>Tenant-scoped type definitions</li>
 *   <li>No cross-tenant type visibility</li>
 *   <li>Independent versioning per tenant</li>
 * </ul>
 *
 * <h2>Versioning & Compatibility</h2>
 * Semantic versioning enables:
 * <ul>
 *   <li><b>MAJOR.MINOR.PATCH</b> versioning</li>
 *   <li>Compatibility policies (NONE, BACKWARD, FORWARD, FULL)</li>
 *   <li>Version handling in event routing</li>
 * </ul>
 *
 * <h2>Example: Event Type Definition</h2>
 * {@code
 *   GEventType orderType = GEventType.builder()
 *       .tenantId("ACME-CORP")
 *       .name("ORDER_PLACED")
 *       .namespace("order-service")
 *       .category("orders")
 *       .contextType(EventContextTypeProto.TEMPORAL)
 *       .intervalBased(false)
 *       .description("Fired when a new order is placed in the system")
 *       .tags(Set.of("order", "commerce", "critical"))
 *       .payload(Map.of(
 *           "orderId", EventParameterSpec.builder()
 *               .name("orderId").type(EventParameterType.STRING)
 *               .required(true).indexed(true).build(),
 *           "customerId", EventParameterSpec.builder()
 *               .name("customerId").type(EventParameterType.STRING)
 *               .required(true).indexed(true).build(),
 *           "items", EventParameterSpec.builder()
 *               .name("items").type(EventParameterType.ARRAY)
 *               .itemsSpec(EventParameterSpec.builder()
 *                   .type(EventParameterType.OBJECT)
 *                   .properties(Map.of(...))
 *                   .build())
 *               .build(),
 *           "totalAmount", EventParameterSpec.builder()
 *               .name("totalAmount").type(EventParameterType.DOUBLE)
 *               .required(true).indexed(false).build()
 *       ))
 *       .governance(GovernancePojo.builder()
 *           .retentionDays(90)
 *           .auditPolicy(new AuditPolicyPojo())
 *           .build())
 *       .status(LifecycleStatusProto.ACTIVE)
 *       .build();
 * }
 *
 * <h2>Immutability & Thread-Safety</h2>
 * Lombok {@code @Value} ensures:
 * <ul>
 *   <li>All fields final (compile-time immutability)</li>
 *   <li>Custom equals/hashCode based on tenant, namespace, name, version</li>
 *   <li>Thread-safe for concurrent type lookup</li>
 *   <li>Safe caching in schema registry</li>
 * </ul>
 *
 * @see EventType
 * @see EventParameterSpec
 * @see Event
 * @see EventId
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose event type schema definition
 * @doc.pattern builder, value-object, immutable, state-machine
 * @doc.test-hints lifecycle-transitions, schema-validation, multi-tenancy-isolation
 */
@Value
@Builder()
public class GEventType implements EventType, Serializable {
    private static final long serialVersionUID = 1L;

    public static final String ID_DELIMITER = "/";
    public static final String DEFAULT_NAMESPACE = "public";
    public static final EventContextTypeProto DEFAULT_CONTEXT_TYPE = EventContextTypeProto.TEMPORAL;
    public static final long DEFAULT_GRANULARITY = 60000; // 60 seconds
    public static final SemanticVersionPojo DEFAULT_VERSION = new SemanticVersionPojo();
    public static final GovernancePojo DEFAULT_GOVERNANCE = new GovernancePojo();
    public static final EventStorageHintsPojo DEFAULT_STORAGE_HINTS = new EventStorageHintsPojo();
    static {
        DEFAULT_VERSION.setMajor(1);
        DEFAULT_VERSION.setMinor(0);
        DEFAULT_VERSION.setPatch(0);

        DEFAULT_GOVERNANCE.setAuditPolicy(new AuditPolicyPojo());
        DEFAULT_GOVERNANCE.setRetentionDays(30);
        DEFAULT_GOVERNANCE.setLegalHoldAllowed(false);
        DEFAULT_GOVERNANCE.setAdaptationPolicy(new AdaptationPolicyPojo());
        DEFAULT_GOVERNANCE.setProvenancePolicy(new ProvenancePolicyPojo());
        DEFAULT_GOVERNANCE.setStatsCollectionPolicy(new StatsCollectionPolicyPojo());

        DEFAULT_STORAGE_HINTS.setHotStorage(false);
        DEFAULT_STORAGE_HINTS.setTtlDays(30);
    }


    @NonNull
    private final String tenantId;
    @NonNull private final String name;
    @Builder.Default private final String category = "";
    @Builder.Default private final String namespace = DEFAULT_NAMESPACE;
    @Builder.Default private final SemanticVersionPojo semanticVersion = DEFAULT_VERSION;
    @Builder.Default private final EventContextTypeProto contextType = DEFAULT_CONTEXT_TYPE;
    @Builder.Default private final boolean intervalBased = false;
    @Builder.Default private final long granularity = DEFAULT_GRANULARITY;
    @Builder.Default private final String description = "";
    @Builder.Default private final Set<String> tags = new HashSet<>();
    @Builder.Default private final List<String> examples = new ArrayList<>();
    @Builder.Default private final Map<String, EventParameterSpec> headers = Map.of(); // unmodifiable
    @Builder.Default private final Map<String, EventParameterSpec> payload = Map.of(); // unmodifiable
    @Builder.Default private final Boolean supportsConfidence = true;
    @Builder.Default private final Set<String> aliases = new HashSet<>();
    @Builder.Default private final GovernancePojo governance = DEFAULT_GOVERNANCE;
    @Builder.Default private final EventStorageHintsPojo storageHints = DEFAULT_STORAGE_HINTS;
    @Builder.Default private final LifecycleStatusProto status = LifecycleStatusProto.DRAFT;
    @Builder.Default private final String statusMessage = LifecycleStatusProto.DRAFT.name();
    @Builder.Default private final CompatibilityPolicyProto compatibilityPolicy = CompatibilityPolicyProto.NONE;
    @Builder.Default private final String owner = "";
    @Builder.Default private final boolean deprecated = false;
    @Builder.Default private final String deprecationMessage = "";
    @Builder.Default private final Instant createdAt = Instant.now();
    @Builder.Default private final Instant updatedAt = Instant.now();


    @Override
    public String getVersion() {
        return semanticVersion.getVersion();
    }

    @Override
    public void validate(Event event) throws SchemaValidationException {
        if (event == null) {
            throw new SchemaValidationException("Event cannot be null");
        }
        // Validate required header fields
        for (Map.Entry<String, EventParameterSpec> entry : headers.entrySet()) {
            EventParameterSpec spec = entry.getValue();
            if (spec.isRequired()) {
                String headerValue = event.getHeader(entry.getKey());
                if (headerValue == null || headerValue.isBlank()) {
                    throw new SchemaValidationException(
                            "Missing required header field: " + entry.getKey());
                }
            }
        }
        // Validate required payload fields
        for (Map.Entry<String, EventParameterSpec> entry : payload.entrySet()) {
            EventParameterSpec spec = entry.getValue();
            if (spec.isRequired()) {
                Object value = event.getPayload(entry.getKey());
                if (value == null) {
                    throw new SchemaValidationException(
                            "Missing required payload field: " + entry.getKey());
                }
            }
        }
    }

    @Override
    public EventParameterSpec getHeader(String name) {
        return headers.get(name);
    }

    @Override
    public EventParameterSpec getPayload(String name) {
        return payload.get(name);
    }

    @Override
    public boolean hasAlias(String alias) {
        return aliases.contains(alias);
    }

    @Override
    public Event createEvent(byte[] data) throws SchemaValidationException {
        try {
            // Deserialize the byte array to extract event fields
            String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> typeRef =
                    new com.fasterxml.jackson.core.type.TypeReference<>() {};
            Map<String, Object> eventMap = com.ghatana.platform.core.util.JsonUtils
                    .getDefaultMapper().readValue(json, typeRef);

            // Extract headers
            Map<String, String> eventHeaders = new HashMap<>();
            Object headersObj = eventMap.get("headers");
            if (headersObj instanceof Map<?, ?> rawHeaders) {
                for (Map.Entry<?, ?> entry : rawHeaders.entrySet()) {
                    if (entry.getKey() instanceof String key && entry.getValue() != null) {
                        eventHeaders.put(key, entry.getValue().toString());
                    }
                }
            }

            // Extract payload
            Map<String, Object> eventPayload = new HashMap<>();
            Object payloadObj = eventMap.get("payload");
            if (payloadObj instanceof Map<?, ?> rawPayload) {
                for (Map.Entry<?, ?> entry : rawPayload.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        eventPayload.put(key, entry.getValue());
                    }
                }
            }

            // Build the event using the GEvent builder
            EventId eventId = EventId.create(UUID.randomUUID().toString(),
                    name, getVersion(), tenantId);

            Event event = GEvent.builder()
                    .id(eventId)
                    .headers(eventHeaders)
                    .payload(eventPayload)
                    .time(EventTime.now())
                    .stats(EventStats.builder().build())
                    .relations(EventRelations.empty())
                    .intervalBased(this.intervalBased)
                    .provenance(List.of(getId()))
                    .build();

            // Validate against schema
            validate(event);

            return event;
        } catch (SchemaValidationException e) {
            throw e;
        } catch (ClassCastException e) {
            throw new SchemaValidationException("Invalid event format: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new SchemaValidationException("Failed to parse event data: " + e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return getId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GEventType that = (GEventType) o;
        return Objects.equals(getTenantId(), that.getTenantId()) &&
                Objects.equals(getNamespace(), that.getNamespace()) &&
                Objects.equals(getName(), that.getName()) &&
                Objects.equals(getVersion(), that.getVersion());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTenantId(), getNamespace(), getName(), getVersion());
    }

    /**
     * Transition the event type to a new lifecycle state
     */
    public GEventType transition(LifecycleStatusProto newStatus, String message) {
        return GEventType.builder()
            .tenantId(this.tenantId)
            .name(this.name)
            .category(this.category)
            .namespace(this.namespace)
            .semanticVersion(this.semanticVersion)
            .contextType(this.contextType)
            .intervalBased(this.intervalBased)
            .granularity(this.granularity)
            .description(this.description)
            .tags(this.tags)
            .examples(this.examples)
            .headers(this.headers)
            .payload(this.payload)
            .supportsConfidence(this.supportsConfidence)
            .aliases(this.aliases)
            .governance(this.governance)
            .storageHints(this.storageHints)
            .status(newStatus)
            .statusMessage(message != null ? message : newStatus.name())
            .compatibilityPolicy(this.compatibilityPolicy)
            .owner(this.owner)
            .deprecated(newStatus == LifecycleStatusProto.DEPRECATED || newStatus == LifecycleStatusProto.RETIRED)
            .deprecationMessage(message)
            .createdAt(this.createdAt)
            .updatedAt(Instant.now())
            .build();
    }
}
