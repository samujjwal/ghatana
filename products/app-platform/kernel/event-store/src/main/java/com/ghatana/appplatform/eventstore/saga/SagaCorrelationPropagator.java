package com.ghatana.appplatform.eventstore.saga;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Utility for propagating saga correlation identifiers through Kafka message headers.
 *
 * <p>The Kafka event publisher ({@code KafkaEventPublisher}) injects all fields from
 * {@code AggregateEventRecord.metadata()} as Kafka record headers. By including
 * {@code saga_id}, {@code correlation_id}, and {@code tenant_id} in the metadata
 * map, sagas trace identifiers are automatically propagated to every downstream
 * Kafka consumer in the saga step chain (STORY-K05-031).
 *
 * <h2>Header names in Kafka</h2>
 * <ul>
 *   <li>{@code saga_id} — unique saga instance identifier</li>
 *   <li>{@code correlation_id} — business correlation key (e.g. orderId, paymentId)</li>
 *   <li>{@code tenant_id} — multi-tenant isolation identifier</li>
 *   <li>{@code saga_type} — saga definition type name</li>
 *   <li>{@code saga_step} — current step order (0-indexed)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Builds and extracts saga correlation metadata for Kafka header propagation (K05-031)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class SagaCorrelationPropagator {

    /** Header key for the saga instance ID. */
    public static final String HEADER_SAGA_ID        = "saga_id";
    /** Header key for the business correlation ID (e.g. orderId). */
    public static final String HEADER_CORRELATION_ID = "correlation_id";
    /** Header key for the tenant scope. */
    public static final String HEADER_TENANT_ID      = "tenant_id";
    /** Header key for the saga type name. */
    public static final String HEADER_SAGA_TYPE      = "saga_type";
    /** Header key for the current step index. */
    public static final String HEADER_SAGA_STEP      = "saga_step";

    private SagaCorrelationPropagator() {}

    /**
     * Builds a metadata map for an action or compensation event emitted by a saga step.
     *
     * <p>The returned map is used as {@code AggregateEventRecord.metadata()}.
     * The Kafka publisher will inject every entry as a Kafka record header,
     * making saga context available to all downstream consumers without explicit
     * parameter threading.
     *
     * @param instance       the saga instance emitting the event
     * @param stepOrder      the step index being dispatched (0-indexed)
     * @return immutable metadata map containing all saga correlation headers
     */
    public static Map<String, Object> buildMetadata(SagaInstance instance, int stepOrder) {
        Map<String, Object> meta = new HashMap<>(5);
        meta.put(HEADER_SAGA_ID,        instance.sagaId());
        meta.put(HEADER_CORRELATION_ID, instance.correlationId());
        meta.put(HEADER_TENANT_ID,      instance.tenantId());
        meta.put(HEADER_SAGA_TYPE,      instance.sagaType());
        meta.put(HEADER_SAGA_STEP,      String.valueOf(stepOrder));
        return Map.copyOf(meta);
    }

    /**
     * Extracts the saga ID from an event's metadata map (populated by the Kafka consumer).
     *
     * @param metadata the metadata map from {@code AggregateEventRecord.metadata()}
     * @return the saga ID if present, or empty optional
     */
    public static Optional<String> extractSagaId(Map<String, Object> metadata) {
        Object val = metadata.get(HEADER_SAGA_ID);
        return val != null ? Optional.of(val.toString()) : Optional.empty();
    }

    /**
     * Extracts the correlation ID from an event's metadata map.
     *
     * @param metadata the metadata map from {@code AggregateEventRecord.metadata()}
     * @return the correlation ID if present, or empty optional
     */
    public static Optional<String> extractCorrelationId(Map<String, Object> metadata) {
        Object val = metadata.get(HEADER_CORRELATION_ID);
        return val != null ? Optional.of(val.toString()) : Optional.empty();
    }
}
