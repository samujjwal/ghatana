package com.ghatana.datacloud.event.model;

import com.ghatana.datacloud.StorageTier;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

/**
 * Event type schema extending Collection with event-specific governance.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines the structure, validation rules, and governance for events of this
 * type. EventType extends Collection to reuse:
 * <ul>
 * <li>Name, label, description</li>
 * <li>Field definitions (via validationSchema)</li>
 * <li>RBAC permissions</li>
 * <li>Schema versioning</li>
 * </ul>
 *
 * <p>
 * <b>EventType ADDS</b>:
 * <ul>
 * <li>Namespace (for organization)</li>
 * <li>Lifecycle status (DRAFT → ACTIVE → DEPRECATED → RETIRED)</li>
 * <li>Compatibility policy (BACKWARD, FORWARD, FULL, NONE)</li>
 * <li>Governance metadata (owner, SLA, deprecation date)</li>
 * <li>Storage hints (partitioning keys, compression, tier)</li>
 * <li>Header and payload schema separately</li>
 * <li>Tags and aliases for discovery</li>
 * </ul>
 *
 * <p>
 * <b>Relationship to libs/domain-models</b><br>
 * This JPA entity is for <b>persistence</b>, while
 * {@code com.ghatana.domain.model.EventType} in libs is for
 * <b>processing pipelines</b>. They serve different purposes:
 * <ul>
 * <li>This class: Schema registry with JPA, lifecycle, governance</li>
 * <li>libs/EventType: Lightweight runtime type with validation</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b>:
 * <pre>{@code
 * EventType eventType = EventType.builder()
 *     .tenantId("tenant-123")
 *     .namespace("commerce")
 *     .name("order.created")
 *     .label("Order Created")
 *     .description("Emitted when a new order is created")
 *     .schemaVersion("1.0.0")
 *     .headerSchema(Map.of(
 *         "source", Map.of("type", "string", "required", true),
 *         "correlationId", Map.of("type", "string", "required", false)
 *     ))
 *     .payloadSchema(Map.of(
 *         "orderId", Map.of("type", "string", "required", true),
 *         "amount", Map.of("type", "number", "required", true)
 *     ))
 *     .lifecycleStatus(LifecycleStatus.ACTIVE)
 *     .compatibilityPolicy(CompatibilityPolicy.BACKWARD)
 *     .defaultStorageTier(StorageTier.WARM)
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Event schema definition with lifecycle, governance, and storage
 * hints
 * @doc.layer domain
 * @doc.pattern Domain Entity, Schema Registry, Governance
 *
 * @see Collection
 * @see Event
 * @see EventStream
 * @see StorageTier
 *
 * @author EventCloud Team
 * @since 1.0.0
 */
@jakarta.persistence.Entity
@Table(name = "event_types", indexes = {
    @Index(name = "idx_event_types_tenant", columnList = "tenant_id"),
    @Index(name = "idx_event_types_name", columnList = "tenant_id, name"),
    @Index(name = "idx_event_types_namespace", columnList = "tenant_id, namespace, name"),
    @Index(name = "idx_event_types_status", columnList = "tenant_id, lifecycle_status")
})
@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class EventType extends Collection {

    // ==================== Namespace ====================
    /**
     * Logical namespace for organizing related event types.
     *
     * <p>
     * Used for grouping and discovery.
     * <p>
     * Examples: "commerce", "inventory", "shipping", "user"
     */
    @Column(name = "namespace", length = 255)
    private String namespace;

    // ==================== Schema ====================
    /**
     * Header field schema (metadata fields).
     *
     * <p>
     * JSON Schema format for validating event headers.
     * <p>
     * Typically includes: source, correlationId, priority, region
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "header_schema", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> headerSchema = new HashMap<>();

    /**
     * Payload field schema (business data fields).
     *
     * <p>
     * JSON Schema format for validating event payload.
     * <p>
     * Defines the structure of the actual event data.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_schema", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> payloadSchema = new HashMap<>();

    // ==================== Governance ====================
    /**
     * Current lifecycle status of this event type.
     *
     * <p>
     * Controls whether events can be produced and consumed.
     * <p>
     * Lifecycle: DRAFT → ACTIVE → DEPRECATED → RETIRED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    @Builder.Default
    private LifecycleStatus lifecycleStatus = LifecycleStatus.DRAFT;

    /**
     * Schema compatibility policy for evolution.
     *
     * <p>
     * Controls what schema changes are allowed:
     * <ul>
     * <li>BACKWARD: New schema can read old data</li>
     * <li>FORWARD: Old schema can read new data</li>
     * <li>FULL: Both backward and forward compatible</li>
     * <li>NONE: No compatibility guarantees</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "compatibility_policy", nullable = false, length = 20)
    @Builder.Default
    private CompatibilityPolicy compatibilityPolicy = CompatibilityPolicy.BACKWARD;

    /**
     * Governance metadata: owner, SLA, deprecation info.
     *
     * <p>
     * Flexible JSONB storage for governance attributes:
     * <ul>
     * <li>owner: Team or person responsible</li>
     * <li>sla: Service level agreement</li>
     * <li>deprecationDate: When type will be retired</li>
     * <li>migrationGuide: Link to migration documentation</li>
     * </ul>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "governance", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> governance = new HashMap<>();

    // ==================== Storage Hints ====================
    /**
     * Default storage tier for new events of this type.
     *
     * <p>
     * Controls initial placement in storage hierarchy.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_storage_tier", length = 20)
    @Builder.Default
    private StorageTier defaultStorageTier = StorageTier.WARM;

    /**
     * Storage hints: partitioning keys, compression, etc.
     *
     * <p>
     * Flexible JSONB storage for storage configuration:
     * <ul>
     * <li>partitionKey: Field to use for partitioning</li>
     * <li>compression: Compression algorithm (lz4, zstd)</li>
     * <li>retention: Override default retention</li>
     * <li>indexedFields: Additional fields to index</li>
     * </ul>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "storage_hints", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> storageHints = new HashMap<>();

    // ==================== Discovery Metadata ====================
    /**
     * Tags for categorization and discovery.
     *
     * <p>
     * Free-form tags for filtering and grouping.
     * <p>
     * Examples: "production", "critical", "pii", "realtime"
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    /**
     * Example event payloads for documentation.
     *
     * <p>
     * JSON strings showing valid event examples.
     * <p>
     * Used for documentation and testing.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "examples", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> examples = new ArrayList<>();

    /**
     * Alternative names/aliases for this event type.
     *
     * <p>
     * Allows events to be referenced by multiple names.
     * <p>
     * Useful during migration or for backward compatibility.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "aliases", columnDefinition = "jsonb")
    @Builder.Default
    private Set<String> aliases = new HashSet<>();

    // ==================== Enums ====================
    /**
     * Lifecycle status for event type governance.
     */
    public enum LifecycleStatus {
        /**
         * Under development, not for production use.
         */
        DRAFT,
        /**
         * Production-ready, accepting events.
         */
        ACTIVE,
        /**
         * Will be retired, migrate away.
         */
        DEPRECATED,
        /**
         * No longer accepting events.
         */
        RETIRED
    }

    /**
     * Schema compatibility policy for evolution.
     */
    public enum CompatibilityPolicy {
        /**
         * No compatibility guarantees.
         */
        NONE,
        /**
         * New schema can read old data.
         */
        BACKWARD,
        /**
         * Old schema can read new data.
         */
        FORWARD,
        /**
         * Both backward and forward compatible.
         */
        FULL
    }

    // ==================== Lifecycle Methods ====================
    /**
     * Activates this event type for production use.
     *
     * @throws IllegalStateException if already retired
     */
    public void activate() {
        if (lifecycleStatus == LifecycleStatus.RETIRED) {
            throw new IllegalStateException("Cannot activate a retired event type");
        }
        this.lifecycleStatus = LifecycleStatus.ACTIVE;
    }

    /**
     * Marks this event type as deprecated.
     *
     * <p>
     * Events can still be produced but consumers should migrate.
     *
     * @param deprecationDate when the type will be retired
     * @param migrationGuide link to migration documentation
     */
    public void deprecate(Instant deprecationDate, String migrationGuide) {
        this.lifecycleStatus = LifecycleStatus.DEPRECATED;
        Map<String, Object> gov = getGovernance();
        gov.put("deprecationDate", deprecationDate.toString());
        if (migrationGuide != null) {
            gov.put("migrationGuide", migrationGuide);
        }
    }

    /**
     * Retires this event type.
     *
     * <p>
     * No new events can be produced after retirement.
     */
    public void retire() {
        this.lifecycleStatus = LifecycleStatus.RETIRED;
    }

    /**
     * Checks if this event type accepts new events.
     *
     * @return true if ACTIVE or DEPRECATED
     */
    public boolean acceptsEvents() {
        return lifecycleStatus == LifecycleStatus.ACTIVE
                || lifecycleStatus == LifecycleStatus.DEPRECATED;
    }

    // ==================== Accessors ====================
    /**
     * Gets header schema, ensuring it's never null.
     *
     * @return header schema map (empty if not set)
     */
    public Map<String, Object> getHeaderSchema() {
        if (headerSchema == null) {
            headerSchema = new HashMap<>();
        }
        return headerSchema;
    }

    /**
     * Gets payload schema, ensuring it's never null.
     *
     * @return payload schema map (empty if not set)
     */
    public Map<String, Object> getPayloadSchema() {
        if (payloadSchema == null) {
            payloadSchema = new HashMap<>();
        }
        return payloadSchema;
    }

    /**
     * Gets governance metadata, ensuring it's never null.
     *
     * @return governance map (empty if not set)
     */
    public Map<String, Object> getGovernance() {
        if (governance == null) {
            governance = new HashMap<>();
        }
        return governance;
    }

    /**
     * Gets storage hints, ensuring it's never null.
     *
     * @return storage hints map (empty if not set)
     */
    public Map<String, Object> getStorageHints() {
        if (storageHints == null) {
            storageHints = new HashMap<>();
        }
        return storageHints;
    }

    /**
     * Gets tags, ensuring it's never null.
     *
     * @return tags set (empty if not set)
     */
    public Set<String> getTags() {
        if (tags == null) {
            tags = new HashSet<>();
        }
        return tags;
    }

    /**
     * Gets examples, ensuring it's never null.
     *
     * @return examples list (empty if not set)
     */
    public List<String> getExamples() {
        if (examples == null) {
            examples = new ArrayList<>();
        }
        return examples;
    }

    /**
     * Gets aliases, ensuring it's never null.
     *
     * @return aliases set (empty if not set)
     */
    public Set<String> getAliases() {
        if (aliases == null) {
            aliases = new HashSet<>();
        }
        return aliases;
    }

    /**
     * Gets the full qualified name including namespace.
     *
     * @return namespace.name or just name if no namespace
     */
    public String getFullyQualifiedName() {
        return namespace != null && !namespace.isEmpty()
                ? namespace + "." + getName()
                : getName();
    }

    /**
     * Gets the owner from governance metadata.
     *
     * @return owner string or null if not set
     */
    public String getOwner() {
        return (String) getGovernance().get("owner");
    }

    // ==================== Object Methods ====================
    @Override
    public String toString() {
        return "EventType{"
                + "id=" + getId()
                + ", tenantId='" + getTenantId() + '\''
                + ", namespace='" + namespace + '\''
                + ", name='" + getName() + '\''
                + ", schemaVersion='" + getSchemaVersion() + '\''
                + ", lifecycleStatus=" + lifecycleStatus
                + ", compatibilityPolicy=" + compatibilityPolicy
                + ", defaultStorageTier=" + defaultStorageTier
                + ", active=" + getActive()
                + '}';
    }
}
