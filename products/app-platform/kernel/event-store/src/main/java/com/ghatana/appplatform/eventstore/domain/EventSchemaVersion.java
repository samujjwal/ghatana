package com.ghatana.appplatform.eventstore.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of one versioned JSON schema for an event type.
 *
 * <p>Schemas progress through a lifecycle: {@link SchemaStatus#DRAFT} →
 * {@link SchemaStatus#ACTIVE} → {@link SchemaStatus#DEPRECATED}. A failed
 * compatibility check transitions a schema to {@link SchemaStatus#BROKEN}.
 * At most one schema per {@code eventType} may be {@code ACTIVE} at a time;
 * the registry enforces this invariant atomically.
 *
 * @param eventType    Event category name, e.g. {@code "com.ghatana.order.OrderPlaced"}.
 * @param version      Monotonically increasing positive integer per event type.
 * @param jsonSchema   Full JSON Schema (Draft-07) string.
 * @param status       Current lifecycle status.
 * @param compatType   Declared compatibility guarantee toward the previous active version.
 * @param description  Optional human-readable summary of changes introduced in this version.
 * @param createdAt    When this schema was registered (UTC).
 * @param activatedAt  When this schema became active (UTC); null until activated.
 *
 * @doc.type class
 * @doc.purpose Immutable domain record for one versioned event schema
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class EventSchemaVersion {

    private final String eventType;
    private final int version;
    private final String jsonSchema;
    private final SchemaStatus status;
    private final CompatibilityType compatType;
    private final String description;
    private final Instant createdAt;
    private final Instant activatedAt;

    private EventSchemaVersion(Builder builder) {
        this.eventType   = Objects.requireNonNull(builder.eventType, "eventType");
        this.version     = builder.version;
        this.jsonSchema  = Objects.requireNonNull(builder.jsonSchema, "jsonSchema");
        this.status      = Objects.requireNonNull(builder.status, "status");
        this.compatType  = Objects.requireNonNull(builder.compatType, "compatType");
        this.description = builder.description;
        this.createdAt   = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.activatedAt = builder.activatedAt;

        if (this.version <= 0) {
            throw new IllegalArgumentException("version must be positive, got: " + this.version);
        }
    }

    public String eventType()       { return eventType; }
    public int version()            { return version; }
    public String jsonSchema()      { return jsonSchema; }
    public SchemaStatus status()    { return status; }
    public CompatibilityType compatType() { return compatType; }
    /** May be null. */
    public String description()     { return description; }
    public Instant createdAt()      { return createdAt; }
    /** May be null until the schema is activated. */
    public Instant activatedAt()    { return activatedAt; }

    public boolean isActive() { return status == SchemaStatus.ACTIVE; }

    /** Returns a copy of this schema with the given status and activatedAt timestamp. */
    public EventSchemaVersion withStatus(SchemaStatus newStatus, Instant newActivatedAt) {
        return builder()
            .eventType(eventType)
            .version(version)
            .jsonSchema(jsonSchema)
            .status(newStatus)
            .compatType(compatType)
            .description(description)
            .createdAt(createdAt)
            .activatedAt(newActivatedAt)
            .build();
    }

    /** Returns a copy of this schema with the given status (activatedAt unchanged). */
    public EventSchemaVersion withStatus(SchemaStatus newStatus) {
        return withStatus(newStatus, activatedAt);
    }

    @Override
    public String toString() {
        return "EventSchemaVersion{eventType='" + eventType + "', version=" + version
            + ", status=" + status + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventSchemaVersion that)) return false;
        return version == that.version && Objects.equals(eventType, that.eventType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, version);
    }

    public static Builder builder() { return new Builder(); }

    /** Fluent builder for {@link EventSchemaVersion}. */
    public static final class Builder {
        private String eventType;
        private int version;
        private String jsonSchema;
        private SchemaStatus status = SchemaStatus.DRAFT;
        private CompatibilityType compatType = CompatibilityType.NONE;
        private String description;
        private Instant createdAt;
        private Instant activatedAt;

        public Builder eventType(String v)        { this.eventType = v;   return this; }
        public Builder version(int v)             { this.version = v;     return this; }
        public Builder jsonSchema(String v)       { this.jsonSchema = v;  return this; }
        public Builder status(SchemaStatus v)     { this.status = v;      return this; }
        public Builder compatType(CompatibilityType v) { this.compatType = v; return this; }
        public Builder description(String v)      { this.description = v; return this; }
        public Builder createdAt(Instant v)       { this.createdAt = v;   return this; }
        public Builder activatedAt(Instant v)     { this.activatedAt = v; return this; }

        public EventSchemaVersion build() { return new EventSchemaVersion(this); }
    }
}
