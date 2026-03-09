package com.ghatana.core.operator.eventcloud;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;

/**
 * Converts EventCloud records to domain Event objects.
 * <p>
 * Bridges the gap between EventCloud's storage representation and the domain
 * Event interface used by operators.
 * </p>
 *
 * @since 2.0
 */
public final class EventCloudEventConverter {

    /**
     * Converts an EventCloud record to a domain Event.
     *
     * @param record The EventCloud record
     * @return Domain Event instance (GEvent)
     * @throws NullPointerException if record is null
     */
    public static Event convert(EventCloudRecord record) {
        Objects.requireNonNull(record, "EventCloud record must not be null");

        // Create simple event from EventCloud record
        // Map EventCloud record to core GEvent
        java.util.Map<String, Object> payload = new java.util.HashMap<>(record.getPayload());
        java.util.Map<String, String> headers = new java.util.HashMap<>();

        // Add record metadata to headers
        java.util.Map<String, String> recordMetadata = record.getMetadata();
        if (recordMetadata != null) {
            headers.putAll(recordMetadata);
        }

        return GEvent.builder()
                .type(record.getType())
                .payload(payload)
                .headers(headers)
                .build();
    }

    /**
     * Represents an EventCloud record (placeholder - actual impl in
     * event-runtime).
     
 *
 * @doc.type interface
 * @doc.purpose Event cloud record
 * @doc.layer platform
 * @doc.pattern Interface
*/
    public interface EventCloudRecord {

        String getId();

        String getType();

        Instant getTimestamp();

        Map<String, Object> getPayload();

        Map<String, String> getMetadata();

        long offset();
    }
}
