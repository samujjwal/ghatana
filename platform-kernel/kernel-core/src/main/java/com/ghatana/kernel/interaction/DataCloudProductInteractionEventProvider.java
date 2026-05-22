package com.ghatana.kernel.interaction;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Data Cloud-backed persistent implementation of ProductInteractionEventProvider.
 *
 * <p>This implementation provides durable storage for product interaction events using Data Cloud,
 * enabling event replay, Dead Letter Queue (DLQ) management, and idempotency checks. It stores
 * events with their delivery status, allowing for recovery from failures and audit trail maintenance.</p>
 *
 * <p>The provider uses the Data Cloud client to persist events and retrieve them for replay or DLQ
 * inspection. Events are stored with metadata including timestamp, status, and DLQ reason codes when
 * applicable.</p>
 *
 * @doc.type class
 * @doc.purpose Data Cloud-backed persistent event storage for replay/DLQ/idempotency
 * @doc.layer kernel
 * @doc.pattern Repository
 */
public final class DataCloudProductInteractionEventProvider implements ProductInteractionEventProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String EVENT_COLLECTION = "product-interaction-events";
    private static final String DLQ_COLLECTION = "product-interaction-dlq";

    private final DataCloudEventClient dataCloudClient;

    public DataCloudProductInteractionEventProvider(DataCloudEventClient dataCloudClient) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient must not be null");
    }

    @Override
    public boolean store(ProductInteractionEventEnvelope<?> envelope, ProductInteractionStatus status) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(status, "status must not be null");
        
        try {
            Map<String, Object> eventRecord = toEventRecord(envelope, status);
            dataCloudClient.storeEvent(
                envelope.tenantId(),
                envelope.workspaceId(),
                EVENT_COLLECTION,
                envelope.eventId(),
                eventRecord
            );
            return true;
        } catch (Exception error) {
            throw new RuntimeException(
                String.format("Failed to store event %s in Data Cloud for tenant=%s workspace=%s",
                    envelope.eventId(), envelope.tenantId(), envelope.workspaceId()), error);
        }
    }

    @Override
    public Optional<ProductInteractionEventEnvelope<?>> get(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        
        try {
            Map<String, Object> eventRecord = dataCloudClient.getEvent(EVENT_COLLECTION, eventId);
            if (eventRecord == null) {
                return Optional.empty();
            }
            return Optional.of(fromEventRecord(eventRecord));
        } catch (Exception error) {
            throw new RuntimeException(
                String.format("Failed to retrieve event %s from Data Cloud", eventId), error);
        }
    }

    @Override
    public boolean updateStatus(String eventId, ProductInteractionStatus status) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        
        try {
            dataCloudClient.updateEventStatus(EVENT_COLLECTION, eventId, status.name());
            return true;
        } catch (Exception error) {
            throw new RuntimeException(
                String.format("Failed to update status for event %s in Data Cloud", eventId), error);
        }
    }

    @Override
    public boolean isDelivered(String eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        
        try {
            Map<String, Object> eventRecord = dataCloudClient.getEvent(EVENT_COLLECTION, eventId);
            if (eventRecord == null) {
                return false;
            }
            String status = (String) eventRecord.get("status");
            return ProductInteractionStatus.SUCCEEDED.name().equals(status);
        } catch (Exception error) {
            throw new RuntimeException(
                String.format("Failed to check delivery status for event %s in Data Cloud", eventId), error);
        }
    }

    @Override
    public boolean sendToDlq(ProductInteractionEventEnvelope<?> envelope, String reasonCode) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        
        try {
            Map<String, Object> dlqRecord = toDlqRecord(envelope, reasonCode);
            dataCloudClient.storeEvent(
                envelope.tenantId(),
                envelope.workspaceId(),
                DLQ_COLLECTION,
                envelope.eventId(),
                dlqRecord
            );
            return true;
        } catch (Exception error) {
            throw new RuntimeException(
                String.format("Failed to send event %s to DLQ in Data Cloud for tenant=%s workspace=%s",
                    envelope.eventId(), envelope.tenantId(), envelope.workspaceId()), error);
        }
    }

    @Override
    public List<ProductInteractionEventEnvelope<?>> getDlqEvents(String topic, int limit) {
        Objects.requireNonNull(topic, "topic must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        
        try {
            List<Map<String, Object>> dlqRecords = dataCloudClient.queryDlqEvents(
                DLQ_COLLECTION,
                topic,
                limit
            );
            
            List<ProductInteractionEventEnvelope<?>> events = new ArrayList<>();
            for (Map<String, Object> record : dlqRecords) {
                events.add(fromEventRecord(record));
            }
            return events;
        } catch (Exception error) {
            throw new RuntimeException(
                String.format("Failed to retrieve DLQ events for topic %s from Data Cloud", topic), error);
        }
    }

    @Override
    public List<ProductInteractionEventEnvelope<?>> getEventsForReplay(
            String topic,
            long fromTimestampMs,
            long toTimestampMs,
            int limit) {
        Objects.requireNonNull(topic, "topic must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (fromTimestampMs < 0 || toTimestampMs < 0) {
            throw new IllegalArgumentException("timestamps must be non-negative");
        }
        if (fromTimestampMs > toTimestampMs) {
            throw new IllegalArgumentException("fromTimestampMs must be <= toTimestampMs");
        }
        
        try {
            List<Map<String, Object>> eventRecords = dataCloudClient.queryEventsForReplay(
                EVENT_COLLECTION,
                topic,
                fromTimestampMs,
                toTimestampMs,
                limit
            );
            
            List<ProductInteractionEventEnvelope<?>> events = new ArrayList<>();
            for (Map<String, Object> record : eventRecords) {
                events.add(fromEventRecord(record));
            }
            return events;
        } catch (Exception error) {
            throw new RuntimeException(
                String.format("Failed to retrieve events for replay for topic %s from Data Cloud", topic), error);
        }
    }

    @Override
    public long deleteEventsBefore(long beforeTimestampMs) {
        if (beforeTimestampMs < 0) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
        
        try {
            return dataCloudClient.deleteEventsBefore(EVENT_COLLECTION, beforeTimestampMs);
        } catch (Exception error) {
            throw new RuntimeException(
                String.format("Failed to delete events before %d from Data Cloud", beforeTimestampMs), error);
        }
    }

    private Map<String, Object> toEventRecord(
            ProductInteractionEventEnvelope<?> envelope,
            ProductInteractionStatus status) {
        Map<String, Object> record = new java.util.LinkedHashMap<>();
        record.put("eventId", envelope.eventId());
        record.put("schemaVersion", envelope.schemaVersion());
        record.put("contractId", envelope.contractId());
        record.put("contractVersion", envelope.contractVersion());
        record.put("providerProductId", envelope.providerProductId());
        record.put("consumerProductIds", envelope.consumerProductIds());
        record.put("productUnitId", envelope.productUnitId());
        record.put("tenantId", envelope.tenantId());
        record.put("workspaceId", envelope.workspaceId());
        record.put("runId", envelope.runId());
        record.put("correlationId", envelope.correlationId());
        record.put("topic", envelope.topic());
        record.put("publishedAt", envelope.publishedAt().toString());
        record.put("policyContext", envelope.policyContext());
        record.put("payload", envelope.payload());
        record.put("status", status.name());
        record.put("storedAt", Instant.now().toString());
        return record;
    }

    private Map<String, Object> toDlqRecord(
            ProductInteractionEventEnvelope<?> envelope,
            String reasonCode) {
        Map<String, Object> record = new java.util.LinkedHashMap<>();
        record.put("eventId", envelope.eventId());
        record.put("schemaVersion", envelope.schemaVersion());
        record.put("contractId", envelope.contractId());
        record.put("contractVersion", envelope.contractVersion());
        record.put("providerProductId", envelope.providerProductId());
        record.put("consumerProductIds", envelope.consumerProductIds());
        record.put("productUnitId", envelope.productUnitId());
        record.put("tenantId", envelope.tenantId());
        record.put("workspaceId", envelope.workspaceId());
        record.put("runId", envelope.runId());
        record.put("correlationId", envelope.correlationId());
        record.put("topic", envelope.topic());
        record.put("publishedAt", envelope.publishedAt().toString());
        record.put("policyContext", envelope.policyContext());
        record.put("payload", envelope.payload());
        record.put("reasonCode", reasonCode);
        record.put("sentToDlqAt", Instant.now().toString());
        return record;
    }

    @SuppressWarnings("unchecked")
    private ProductInteractionEventEnvelope<?> fromEventRecord(Map<String, Object> record) {
        return new ProductInteractionEventEnvelope<>(
            (String) record.get("schemaVersion"),
            (String) record.get("eventId"),
            (String) record.get("contractId"),
            (String) record.get("contractVersion"),
            (String) record.get("providerProductId"),
            (List<String>) record.get("consumerProductIds"),
            (String) record.get("productUnitId"),
            (String) record.get("tenantId"),
            (String) record.get("workspaceId"),
            (String) record.get("runId"),
            (String) record.get("correlationId"),
            Instant.parse((String) record.get("publishedAt")),
            (Map<String, String>) record.get("policyContext"),
            (String) record.get("topic"),
            record.get("payload")
        );
    }

    /**
     * Data Cloud client interface for event storage operations.
     */
    public interface DataCloudEventClient {
        /**
         * Stores an event record in Data Cloud.
         */
        void storeEvent(
            String tenantId,
            String workspaceId,
            String collection,
            String eventId,
            Map<String, Object> eventRecord
        );

        /**
         * Retrieves an event record by ID.
         */
        Map<String, Object> getEvent(String collection, String eventId);

        /**
         * Updates the status of an event.
         */
        void updateEventStatus(String collection, String eventId, String status);

        /**
         * Queries DLQ events for a topic.
         */
        List<Map<String, Object>> queryDlqEvents(
            String collection,
            String topic,
            int limit
        );

        /**
         * Queries events for replay within a time range.
         */
        List<Map<String, Object>> queryEventsForReplay(
            String collection,
            String topic,
            long fromTimestampMs,
            long toTimestampMs,
            int limit
        );

        /**
         * Deletes events older than a timestamp.
         */
        long deleteEventsBefore(String collection, long beforeTimestampMs);
    }
}
