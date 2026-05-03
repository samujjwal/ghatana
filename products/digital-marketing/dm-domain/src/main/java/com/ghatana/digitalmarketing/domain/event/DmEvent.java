package com.ghatana.digitalmarketing.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical typed event envelope for all DMOS domain events.
 *
 * <p>A {@code DmEvent<T>} carries the full audit metadata required for event
 * sourcing, replay, idempotency, correlation, PII classification, and
 * consent traceability. The generic {@code T} parameter carries the
 * type-safe, version-specific event payload.</p>
 *
 * <p>Required fields:</p>
 * <ul>
 *   <li>{@code eventId} — globally unique UUID for this event instance</li>
 *   <li>{@code eventType} — discriminant determining routing and schema</li>
 *   <li>{@code schemaVersion} — SemVer string for the payload schema</li>
 *   <li>{@code tenantId} — owning tenant (platform isolation boundary)</li>
 *   <li>{@code workspaceId} — owning workspace within the tenant</li>
 *   <li>{@code actor} — principal ID of the actor who caused the event</li>
 *   <li>{@code actorType} — type of actor (USER, AGENT, SYSTEM)</li>
 *   <li>{@code correlationId} — trace correlation ID propagated from the request</li>
 *   <li>{@code causationId} — eventId of the event that caused this one (empty for root)</li>
 *   <li>{@code idempotencyKey} — consumer deduplication key; stable across retries</li>
 *   <li>{@code occurredAt} — wall-clock time the fact occurred</li>
 *   <li>{@code sourceService} — DMOS sub-service that emitted the event</li>
 *   <li>{@code piiClassification} — PII level of the payload for routing/retention</li>
 *   <li>{@code payload} — typed event payload (may be {@code null} for marker events)</li>
 * </ul>
 *
 * <p>Optional fields:</p>
 * <ul>
 *   <li>{@code consentSnapshotId} — consent proof ID when payload contains PII</li>
 *   <li>{@code policySnapshotId} — policy version evaluated at emission time</li>
 *   <li>{@code tags} — arbitrary string key-value metadata for routing/filtering</li>
 *   <li>{@code externalRefs} — external system references (e.g. Google Ads campaign ID)</li>
 * </ul>
 *
 * <p>Instances are immutable. Use {@link Builder} to construct.</p>
 *
 * @param <T> type-safe event payload type
 *
 * @doc.type class
 * @doc.purpose Canonical typed DMOS event envelope for F2 event schema (DMOS-F2-001)
 * @doc.layer product
 * @doc.pattern Value Object, Event Envelope
 */
public final class DmEvent<T> {

    private final String eventId;
    private final DmEventType eventType;
    private final String schemaVersion;
    private final String tenantId;
    private final String workspaceId;
    private final String actor;
    private final ActorType actorType;
    private final String correlationId;
    private final String causationId;
    private final String idempotencyKey;
    private final Instant occurredAt;
    private final String sourceService;
    private final DmPiiClassification piiClassification;
    private final T payload;
    private final String consentSnapshotId;
    private final String policySnapshotId;
    private final Map<String, String> tags;
    private final List<ExternalRef> externalRefs;

    private DmEvent(Builder<T> builder) {
        this.eventId           = requireNonBlank(builder.eventId, "eventId");
        this.eventType         = Objects.requireNonNull(builder.eventType, "eventType must not be null");
        this.schemaVersion     = requireNonBlank(builder.schemaVersion, "schemaVersion");
        this.tenantId          = requireNonBlank(builder.tenantId, "tenantId");
        this.workspaceId       = requireNonBlank(builder.workspaceId, "workspaceId");
        this.actor             = requireNonBlank(builder.actor, "actor");
        this.actorType         = Objects.requireNonNull(builder.actorType, "actorType must not be null");
        this.correlationId     = requireNonBlank(builder.correlationId, "correlationId");
        this.causationId       = builder.causationId != null ? builder.causationId : "";
        this.idempotencyKey    = requireNonBlank(builder.idempotencyKey, "idempotencyKey");
        this.occurredAt        = Objects.requireNonNull(builder.occurredAt, "occurredAt must not be null");
        this.sourceService     = requireNonBlank(builder.sourceService, "sourceService");
        this.piiClassification = Objects.requireNonNull(builder.piiClassification, "piiClassification must not be null");
        this.payload           = builder.payload;
        this.consentSnapshotId = builder.consentSnapshotId;
        this.policySnapshotId  = builder.policySnapshotId;
        this.tags              = builder.tags != null ? Map.copyOf(builder.tags) : Map.of();
        this.externalRefs      = builder.externalRefs != null ? List.copyOf(builder.externalRefs) : List.of();
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
        return value;
    }

    /** Globally unique event instance ID (UUID). */
    public String getEventId() { return eventId; }

    /** Discriminant for routing and schema selection. */
    public DmEventType getEventType() { return eventType; }

    /** SemVer payload schema version (e.g. {@code "1.0.0"}). */
    public String getSchemaVersion() { return schemaVersion; }

    /** Owning tenant ID (platform isolation boundary). */
    public String getTenantId() { return tenantId; }

    /** Owning workspace ID within the tenant. */
    public String getWorkspaceId() { return workspaceId; }

    /** Principal ID of the actor who caused the event. */
    public String getActor() { return actor; }

    /** Classification of the actor type. */
    public ActorType getActorType() { return actorType; }

    /** Trace correlation ID propagated from the originating request. */
    public String getCorrelationId() { return correlationId; }

    /**
     * EventId of the event that caused this one.
     * Empty string for root events with no parent.
     */
    public String getCausationId() { return causationId; }

    /** Consumer-facing deduplication key; stable across retries for the same logical action. */
    public String getIdempotencyKey() { return idempotencyKey; }

    /** Wall-clock time when the domain fact occurred. */
    public Instant getOccurredAt() { return occurredAt; }

    /** DMOS sub-service that emitted this event (e.g. {@code "dm-application"}). */
    public String getSourceService() { return sourceService; }

    /** PII classification of the payload for routing and retention policy. */
    public DmPiiClassification getPiiClassification() { return piiClassification; }

    /** Typed payload. May be {@code null} for marker events. */
    public T getPayload() { return payload; }

    /** Consent proof snapshot ID. Present when payload contains PII fields. */
    public String getConsentSnapshotId() { return consentSnapshotId; }

    /** Policy version evaluated at emission time. May be {@code null}. */
    public String getPolicySnapshotId() { return policySnapshotId; }

    /** Arbitrary routing/filtering metadata tags. Never {@code null}; may be empty. */
    public Map<String, String> getTags() { return tags; }

    /** External system references. Never {@code null}; may be empty. */
    public List<ExternalRef> getExternalRefs() { return externalRefs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmEvent<?> other)) return false;
        return eventId.equals(other.eventId);
    }

    @Override
    public int hashCode() { return Objects.hash(eventId); }

    @Override
    public String toString() {
        return "DmEvent{eventId=" + eventId + ", eventType=" + eventType
                + ", workspaceId=" + workspaceId + ", occurredAt=" + occurredAt + '}';
    }

    /** Returns a new {@link Builder} for the given payload type. */
    public static <T> Builder<T> builder() { return new Builder<>(); }

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * Classification of the actor that caused the event.
     */
    public enum ActorType {
        /** A human user authenticated via SSO or session token. */
        USER,
        /** An autonomous AI agent operating within defined boundaries. */
        AGENT,
        /** An internal DMOS system process (scheduler, connector, watchdog). */
        SYSTEM
    }

    /**
     * A reference to an entity in an external system.
     *
     * @param system     external system name (e.g. {@code "google-ads"}, {@code "hubspot"})
     * @param entityType external entity type (e.g. {@code "campaign"}, {@code "contact"})
     * @param externalId external entity identifier within the named system
     */
    public record ExternalRef(String system, String entityType, String externalId) {
        public ExternalRef {
            Objects.requireNonNull(system, "system must not be null");
            Objects.requireNonNull(entityType, "entityType must not be null");
            Objects.requireNonNull(externalId, "externalId must not be null");
        }
    }

    /**
     * Fluent builder for {@link DmEvent}.
     *
     * @param <T> payload type
     */
    public static final class Builder<T> {
        private String eventId;
        private DmEventType eventType;
        private String schemaVersion;
        private String tenantId;
        private String workspaceId;
        private String actor;
        private ActorType actorType;
        private String correlationId;
        private String causationId;
        private String idempotencyKey;
        private Instant occurredAt;
        private String sourceService;
        private DmPiiClassification piiClassification;
        private T payload;
        private String consentSnapshotId;
        private String policySnapshotId;
        private Map<String, String> tags;
        private List<ExternalRef> externalRefs;

        private Builder() {}

        public Builder<T> eventId(String eventId) { this.eventId = eventId; return this; }
        public Builder<T> eventType(DmEventType eventType) { this.eventType = eventType; return this; }
        public Builder<T> schemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; return this; }
        public Builder<T> tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder<T> workspaceId(String workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder<T> actor(String actor) { this.actor = actor; return this; }
        public Builder<T> actorType(ActorType actorType) { this.actorType = actorType; return this; }
        public Builder<T> correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder<T> causationId(String causationId) { this.causationId = causationId; return this; }
        public Builder<T> idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder<T> occurredAt(Instant occurredAt) { this.occurredAt = occurredAt; return this; }
        public Builder<T> sourceService(String sourceService) { this.sourceService = sourceService; return this; }
        public Builder<T> piiClassification(DmPiiClassification piiClassification) { this.piiClassification = piiClassification; return this; }
        public Builder<T> payload(T payload) { this.payload = payload; return this; }
        public Builder<T> consentSnapshotId(String consentSnapshotId) { this.consentSnapshotId = consentSnapshotId; return this; }
        public Builder<T> policySnapshotId(String policySnapshotId) { this.policySnapshotId = policySnapshotId; return this; }
        public Builder<T> tags(Map<String, String> tags) { this.tags = tags; return this; }
        public Builder<T> externalRefs(List<ExternalRef> externalRefs) { this.externalRefs = externalRefs; return this; }

        /** Builds a {@link DmEvent}. Throws {@link IllegalArgumentException} for invalid fields. */
        public DmEvent<T> build() { return new DmEvent<>(this); }
    }
}
