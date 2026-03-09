package com.ghatana.platform.domain.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Core domain entity representing an immutable event in the system.
 *
 * <p>
 * Events are the fundamental unit of data that flows through the Ghatana
 * platform, representing something that has happened at a specific point in
 * time. Every event contains both system-generated metadata (id, time,
 * location, stats, relations) and application-specific payload data. Events
 * drive the entire event processing pipeline from ingestion through enrichment,
 * correlation, pattern matching, and storage.
 * </p>
 *
 * <h2>Event Structure</h2>
 * <p>
 * Events encapsulate:</p>
 * <dl>
 * <dt><b>Identity</b>: {@link #getId()}</dt>
 * <dd>Unique event identifier with tenant, type, version information</dd>
 *
 * <dt><b>Temporal</b>: {@link #getTime()}</dt>
 * <dd>Occurrence time (when event happened) and detection time (when
 * discovered)</dd>
 *
 * <dt><b>Location</b>: {@link #getLocation()}</dt>
 * <dd>Optional geographic location for geospatially-aware events</dd>
 *
 * <dt><b>Statistics</b>: {@link #getStats()}</dt>
 * <dd>Event metrics (processing time, size, correlation data)</dd>
 *
 * <dt><b>Relations</b>: {@link #getRelations()}</dt>
 * <dd>Causal and semantic relationships to other events (correlation,
 * causation)</dd>
 *
 * <dt><b>Headers</b>: {@link #getHeader(String)}</dt>
 * <dd>System metadata (correlationId, causationId, tracing, security
 * context)</dd>
 *
 * <dt><b>Payload</b>: {@link #getPayload(String)}</dt>
 * <dd>Application-specific business data and event details</dd>
 * </dl>
 *
 * <h2>Architecture Role</h2>
 * <p>
 * Events are central to the platform architecture:
 * <ul>
 * <li><b>Ingestion</b>: Created by ingestion services from external
 * sources</li>
 * <li><b>Enrichment</b>: Enriched with correlation data, geolocation,
 * context</li>
 * <li><b>Processing</b>: Processed by pattern detection, analytics agents</li>
 * <li><b>Correlation</b>: Used to build root cause analysis graphs</li>
 * <li><b>Storage</b>: Persisted in event streams, data lakes, OLAP stores</li>
 * <li><b>Alerting</b>: Trigger alerts and recommendations when patterns
 * detected</li>
 * </ul>
 * </p>
 *
 * <h2>Immutability & Thread Safety</h2>
 * <p>
 * Events are immutable and thread-safe by design:
 * <ul>
 * <li>All fields are logically final (no modification after creation)</li>
 * <li>Implementations must ensure thread-safe access to event data</li>
 * <li>Safe to share across concurrent processing threads without
 * synchronization</li>
 * <li>No setters - modifications create new Event instances (functional
 * approach)</li>
 * </ul>
 * </p>
 *
 * <h2>Event Correlation & Tracing</h2>
 * <p>
 * Events support cross-service tracing and causation tracking:
 * <ul>
 * <li><b>correlationId</b>: Groups logically related events across
 * services</li>
 * <li><b>causationId</b>: References the event that triggered this event</li>
 * <li>Used for end-to-end tracing and root cause analysis</li>
 * </ul>
 * </p>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Accessing core event metadata
 * Event event = events.next();
 * EventId id = event.getId();
 * String eventType = event.getType();  // convenience method
 * Instant occurredAt = event.getTime().getOccurrenceTime();
 *
 * // Accessing payload and headers
 * Object severity = event.getPayload("severity");
 * String correlationId = event.getCorrelationId();  // convenience method
 *
 * // Accessing relationships for correlation
 * EventRelations relations = event.getRelations();
 * List<EventId> causes = relations.getCauses();
 *
 * // Statistics and monitoring
 * EventStats stats = event.getStats();
 * long processingTimeMs = stats.getProcessingTimeMs();
 * }</pre>
 *
 * @doc.type interface (core domain entity contract)
 * @doc.layer domain
 * @doc.purpose immutable event representation for platform data flow
 * @doc.pattern value-object (immutable, effectively final, no modification
 * after creation)
 * @doc.test-hints verify immutability, test header/payload access, validate
 * correlation IDs, test thread safety
 *
 * @see EventId (unique event identifier)
 * @see EventTime (temporal information)
 * @see EventStats (event statistics and metrics)
 * @see EventRelations (causal and semantic relationships)
 * @see Location (optional geographic location)
 */
public interface Event {

    /**
     * Returns the unique identifier of this event. The ID is guaranteed to be
     * unique within the system and contains information about the event's type,
     * version, and tenant.
     *
     * @return The event identifier, never null
     */
    EventId getId();

    /**
     * Returns the temporal information about when this event occurred. This
     * includes both the detection time (when the event was detected) and the
     * occurrence time (when the event actually happened).
     *
     * @return The event time information, never null
     */
    EventTime getTime();

    /**
     * Returns the geographic location information associated with this event.
     * This is optional and may be null if the event is not associated with a
     * specific location.
     *
     * @return The event location information, or null if not available
     */
    Location getLocation();

    /**
     * Returns statistical information about this event. This includes metrics
     * like processing time, size, and other statistics that may be useful for
     * monitoring and debugging.
     *
     * @return The event statistics, never null
     */
    EventStats getStats();

    /**
     * Returns the relations of this event to other events or entities. This can
     * be used to represent causal relationships, references, or other types of
     * connections between events.
     *
     * @return The event relations, never null
     */
    EventRelations getRelations();

    /**
     * Returns the type of this event. This is a convenience method that
     * delegates to {@link EventId#getEventType()}.
     *
     * @return The event type name, never null
     */
    default String getType() {
        return getId().getEventType();
    }

    /**
     * Returns the version of the event type schema. This is a convenience
     * method that delegates to {@link EventId#getVersion()}.
     *
     * @return The schema version, never null
     */
    default String getVersion() {
        return getId().getVersion();
    }

    /**
     * Returns the ID of the tenant that owns this event. This is a convenience
     * method that delegates to {@link EventId#getTenantId()}.
     *
     * @return The tenant ID, never null
     */
    default String getTenantId() {
        return getId().getTenantId();
    }

    /**
     * Returns the correlation ID for this event, if any.
     * <p>
     * The correlation ID is used for tracing related events across services.
     * Events that are part of the same logical operation or transaction should
     * share the same correlation ID.
     *
     * @return The correlation ID, or null if not set
     */
    default String getCorrelationId() {
        return getHeader("correlationId");
    }

    /**
     * Returns the causation ID for this event, if any.
     * <p>
     * The causation ID references the event that caused this event to be
     * created. This is useful for building event chains and understanding the
     * flow of events.
     *
     * @return The causation ID, or null if not set
     */
    default String getCausationId() {
        return getHeader("causationId");
    }

    /**
     * Gets a header value by name.
     * <p>
     * Headers are metadata associated with the event and are typically used for
     * system-level concerns like routing, security, and monitoring.
     *
     * @param name The name of the header (case-sensitive)
     * @return The header value, or null if not found
     */
    String getHeader(String name);

    /**
     * Gets a payload value by name.
     * <p>
     * The payload contains the business data associated with the event. The
     * structure of the payload is defined by the event type schema.
     *
     * @param name The name of the payload field (case-sensitive)
     * @return The payload value, or null if not found
     */
    Object getPayload(String name);

    /**
     * Gets a value from either headers or payload using a dot notation.
     * <p>
     * The name can be prefixed with "header." or "payload." to explicitly
     * specify the source. If no prefix is provided, the payload is checked
     * first, followed by headers.
     *
     * @param name The name of the value to get, optionally prefixed with
     * "header." or "payload."
     * @return The value, or null if not found
     */
    default Object get(String name) {
        if (name.startsWith("header.")) {
            return getHeader(name.substring("header.".length()));
        } else if (name.startsWith("payload.")) {
            return getPayload(name.substring("payload.".length()));
        }
        // Default to checking payload first, then headers
        Object result = getPayload(name);
        return result != null ? result : getHeader(name);
    }

    /**
     * Checks if a header with the given name exists.
     *
     * @param name The name of the header to check (case-sensitive)
     * @return true if the header exists and has a non-null value, false
     * otherwise
     */
    default boolean hasHeader(String name) {
        return getHeader(name) != null;
    }

    /**
     * Checks if a payload field with the given name exists.
     *
     * @param name The name of the payload field to check (case-sensitive)
     * @return true if the field exists and has a non-null value, false
     * otherwise
     */
    default boolean hasPayload(String name) {
        return getPayload(name) != null;
    }

    /**
     * Checks if a value exists in either headers or payload.
     *
     * @param name The name of the value to check, optionally prefixed with
     * "header." or "payload."
     * @return true if the value exists and is non-null, false otherwise
     */
    default boolean has(String name) {
        return get(name) != null;
    }

    /**
     * Gets the timestamp when this event was detected. This is a convenience
     * method that delegates to {@link EventTime#getDetectionTimePoint()}.
     *
     * @return The detection timestamp, never null
     */
    default Instant getTimestamp() {
        return getTime().getDetectionTimePoint().toInstant();
    }

    /**
     * Gets the timestamp when this event was detected in epoch milliseconds.
     * This is a convenience method for use in stream operations.
     *
     * @return The detection timestamp in milliseconds since epoch
     */
    default long getTimestampMillis() {
        return getTimestamp().toEpochMilli();
    }

    /**
     * Gets the name of the stream this event belongs to, if any. This is
     * typically stored in the "stream" header.
     *
     * @return The stream name, or null if not set
     */
    default String getStream() {
        return getHeader("stream");
    }

    /**
     * Checks if this event represents a time interval rather than a point in
     * time.
     *
     * @return true if this event has a duration, false if it represents a point
     * in time
     */
    boolean isIntervalBased();

    /**
     * Checks if this event occurred before the specified time. For
     * interval-based events, this checks the occurrence end time. For
     * point-in-time events, this checks the detection time.
     *
     * @param fromTime The time to compare against
     * @return true if this event occurred before the specified time, false
     * otherwise
     */
    default boolean isBefore(Instant fromTime) {
        Objects.requireNonNull(fromTime, "fromTime cannot be null");
        if (isIntervalBased()) {
            return getTime().getOccurrenceTime().isBefore(fromTime);
        } else {
            return getTimestamp().isBefore(fromTime);
        }
    }

    /**
     * Checks if this event occurred after the specified time. For
     * interval-based events, this checks the occurrence start time. For
     * point-in-time events, this checks the detection time.
     *
     * @param fromTime The time to compare against
     * @return true if this event occurred after the specified time, false
     * otherwise
     */
    default boolean isAfter(Instant fromTime) {
        Objects.requireNonNull(fromTime, "fromTime cannot be null");
        if (isIntervalBased()) {
            return getTime().getOccurrenceTime().isAfter(fromTime);
        } else {
            return getTimestamp().isAfter(fromTime);
        }
    }

    /**
     * Creates a new event builder.
     *
     * @return GEvent builder instance
     */
    static GEvent.GEventBuilder builder() {
        return GEvent.builder();
    }
}
