package com.ghatana.appplatform.eventstore.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable domain event record scoped to one aggregate instance.
 *
 * <p>This is the platform model for DDD aggregate event sourcing.
 * It is distinct from the general {@code EventRecord} in {@code :products:aep:platform}
 * (which wraps an Avro/Protobuf envelope for stream processing) and from the
 * Data Cloud {@code events} table (stream-based, partition-offset addressing).
 * This record is addressed by {@code (aggregateId, sequenceNumber)}.
 *
 * @doc.type class
 * @doc.purpose Immutable aggregate-scoped domain event for DDD event sourcing
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class AggregateEventRecord {

    private final UUID eventId;
    private final String eventType;
    private final UUID aggregateId;
    private final String aggregateType;
    private final long sequenceNumber;
    private final Map<String, Object> data;
    private final Map<String, Object> metadata;
    private final Instant createdAtUtc;
    /** BS calendar date string (YYYY-MM-DD) from the calendar-service enricher. Null when enricher is unavailable. */
    private final String createdAtBs;

    private AggregateEventRecord(Builder builder) {
        this.eventId        = Objects.requireNonNull(builder.eventId, "eventId");
        this.eventType      = Objects.requireNonNull(builder.eventType, "eventType");
        this.aggregateId    = Objects.requireNonNull(builder.aggregateId, "aggregateId");
        this.aggregateType  = Objects.requireNonNull(builder.aggregateType, "aggregateType");
        this.sequenceNumber = builder.sequenceNumber;
        this.data           = Map.copyOf(Objects.requireNonNull(builder.data, "data"));
        this.metadata       = Map.copyOf(Objects.requireNonNull(builder.metadata, "metadata"));
        this.createdAtUtc   = builder.createdAtUtc != null ? builder.createdAtUtc : Instant.now();
        this.createdAtBs    = builder.createdAtBs;
    }

    public UUID eventId()        { return eventId; }
    public String eventType()    { return eventType; }
    public UUID aggregateId()    { return aggregateId; }
    public String aggregateType(){ return aggregateType; }
    public long sequenceNumber() { return sequenceNumber; }
    public Map<String, Object> data()     { return data; }
    public Map<String, Object> metadata() { return metadata; }
    public Instant createdAtUtc()  { return createdAtUtc; }
    /** May be null when the calendar-service enricher was unavailable. */
    public String createdAtBs()    { return createdAtBs; }

    /** Whether the BS calendar date is populated (calendar enrichment succeeded). */
    public boolean isCalendarEnriched() { return createdAtBs != null; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID eventId = UUID.randomUUID();
        private String eventType;
        private UUID aggregateId;
        private String aggregateType;
        private long sequenceNumber;
        private Map<String, Object> data = Map.of();
        private Map<String, Object> metadata = Map.of();
        private Instant createdAtUtc;
        private String createdAtBs;

        public Builder eventId(UUID id)          { this.eventId = id; return this; }
        public Builder eventType(String t)       { this.eventType = t; return this; }
        public Builder aggregateId(UUID id)      { this.aggregateId = id; return this; }
        public Builder aggregateType(String t)   { this.aggregateType = t; return this; }
        public Builder sequenceNumber(long n)    { this.sequenceNumber = n; return this; }
        public Builder data(Map<String, Object> d)      { this.data = d; return this; }
        public Builder metadata(Map<String, Object> m)  { this.metadata = m; return this; }
        public Builder createdAtUtc(Instant ts)  { this.createdAtUtc = ts; return this; }
        public Builder createdAtBs(String bs)    { this.createdAtBs = bs; return this; }

        public AggregateEventRecord build() { return new AggregateEventRecord(this); }
    }
}
