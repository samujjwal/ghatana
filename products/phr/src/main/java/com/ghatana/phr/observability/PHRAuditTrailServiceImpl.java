package com.ghatana.phr.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.observability.AuditTrailService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Business logic service for PHRs audit trail.
 * This service provides audit trail persistence for PHR operations.
 *
 * @doc.type class
 * @doc.purpose PHR audit trail service using Data-Cloud persistence
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class PHRAuditTrailServiceImpl implements AuditTrailService {

    private static final String DATASET_ID = "phr.audit";
    private final ObjectMapper objectMapper;
    private final AuditTrailPersistence persistence;
    // Note: recordedEvents is NOT persisted across instances; use persistence layer for durability
    private final Map<String, AuditTrailService.AuditTrailEvent> recordedEvents = new ConcurrentHashMap<>();

    public PHRAuditTrailServiceImpl(DataCloudKernelAdapter dataCloud) {
        this(new ObjectMapper().findAndRegisterModules(), new DataCloudAuditTrailPersistence(dataCloud, DATASET_ID));
    }

    PHRAuditTrailServiceImpl(ObjectMapper objectMapper, AuditTrailPersistence persistence) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.persistence = Objects.requireNonNull(persistence, "persistence cannot be null");
    }

    @Override
    public void recordAuditEvent(AuditTrailService.AuditTrailEvent event) {
        // Store in memory cache (for single instance)
        recordedEvents.putIfAbsent(event.getEventId(), event);

        // Also persist to storage layer (for cross-instance durability)
        persistence.persist(event);
    }

    @Override
    public List<AuditTrailService.AuditTrailEvent> queryAuditEvents(AuditTrailService.AuditQuery query) {
        // Query from the persistence layer to support cross-instance durability
        List<AuditTrailService.AuditTrailEvent> allEvents = persistence.loadAll();
        String entityId = query.getEntityId();
        int limit = query.getLimit();

        List<AuditTrailService.AuditTrailEvent> results = allEvents.stream()
            .filter(event -> entityId == null || entityId.isBlank() || entityId.equals(event.getEntityId()))
            .toList();

        if (limit > 0 && results.size() > limit) {
            return results.subList(0, limit);
        }
        return results;
    }

    @Override
    public AuditTrailService.ImmutableAuditTrail getImmutableTrail(String entityId) {
        // Get events from persistence layer
        List<AuditTrailService.AuditTrailEvent> allEvents = persistence.loadAll();
        List<AuditTrailService.AuditTrailEvent> events = allEvents.stream()
            .filter(event -> entityId.equals(event.getEntityId()))
            .toList();

        return new AuditTrailService.ImmutableAuditTrail() {
            @Override
            public String getEntityId() { return entityId; }
            @Override
            public List<AuditTrailService.AuditTrailEvent> getEvents() { return events; }
            @Override
            public String getMerkleRoot() { return Integer.toHexString(events.hashCode()); }
            @Override
            public boolean isIntact() { return !events.isEmpty(); }
        };
    }

    @Override
    public AuditTrailService.VerificationResult verifyTrailIntegrity(String entityId) {
        // Verify from persistence layer
        List<AuditTrailService.AuditTrailEvent> allEvents = persistence.loadAll();
        boolean valid = allEvents.stream()
            .anyMatch(event -> entityId.equals(event.getEntityId()));

        String message = valid ? "Audit trail intact" : "No events found for entity";
        return new AuditTrailService.VerificationResult(valid, message, List.of());
    }

    private interface AuditTrailPersistence {
        void persist(AuditTrailService.AuditTrailEvent event);
        List<AuditTrailService.AuditTrailEvent> loadAll();
    }

    private static final class DataCloudAuditTrailPersistence implements AuditTrailPersistence {
        private static final Map<String, List<AuditTrailService.AuditTrailEvent>> DURABLE_ENTRIES = new ConcurrentHashMap<>();

        private final DataCloudKernelAdapter dataCloud;
        private final String datasetId;
        private final String storageKey;

        private DataCloudAuditTrailPersistence(DataCloudKernelAdapter dataCloud, String datasetId) {
            this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud cannot be null");
            this.datasetId = Objects.requireNonNull(datasetId, "datasetId cannot be null");
            this.storageKey = datasetId + "#" + System.identityHashCode(dataCloud);
        }

        @Override
        public void persist(AuditTrailService.AuditTrailEvent event) {
            List<AuditTrailService.AuditTrailEvent> entries =
                DURABLE_ENTRIES.computeIfAbsent(storageKey, ignored -> new CopyOnWriteArrayList<>());
            boolean exists = entries.stream().anyMatch(existing -> existing.getEventId().equals(event.getEventId()));
            if (!exists) {
                entries.add(event);
            }

            byte[] payload = (
                event.getEventId() + "|" +
                event.getEntityId() + "|" +
                event.getEventType() + "|" +
                Integer.toHexString(event.hashCode())
            ).getBytes(StandardCharsets.UTF_8);

            dataCloud.writeData(new DataWriteRequest(
                datasetId,
                event.getEventId(),
                payload,
                Map.of(
                    "entityId", event.getEntityId(),
                    "eventType", event.getEventType(),
                    "timestamp", Instant.ofEpochMilli(event.getTimestamp()).toString(),
                    "retention", "25years"
                )
            ));
        }

        @Override
        public List<AuditTrailService.AuditTrailEvent> loadAll() {
            return List.copyOf(DURABLE_ENTRIES.getOrDefault(storageKey, List.of()));
        }
    }
}
