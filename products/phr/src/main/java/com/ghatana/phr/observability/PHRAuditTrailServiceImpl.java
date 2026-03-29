package com.ghatana.phr.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataWriteRequest;
import com.ghatana.kernel.observability.AuditTrailPersistence;
import com.ghatana.kernel.observability.DefaultAuditTrailService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Business logic service for PHRAuditTrailService
 *
 * @doc.type class
 * @doc.purpose PHR audit trail built on the shared kernel default implementation
 * @doc.layer product
 * @doc.pattern Service
 */
public class PHRAuditTrailServiceImpl extends DefaultAuditTrailService {

    private static final String DATASET_ID = "phr.audit";

    public PHRAuditTrailServiceImpl() {
        this(new ObjectMapper().findAndRegisterModules(), new InMemoryAuditTrailPersistence());
    }

    public PHRAuditTrailServiceImpl(DataCloudKernelAdapter dataCloud) {
        this(new ObjectMapper().findAndRegisterModules(), new DataCloudAuditTrailPersistence(dataCloud, DATASET_ID));
    }

    PHRAuditTrailServiceImpl(ObjectMapper objectMapper, AuditTrailPersistence persistence) {
        super(objectMapper, persistence);
    }

    private static final class InMemoryAuditTrailPersistence implements AuditTrailPersistence {
        private final List<StoredAuditEvent> entries = new CopyOnWriteArrayList<>();

        @Override
        public void persist(StoredAuditEvent event) {
            entries.add(event);
        }

        @Override
        public List<StoredAuditEvent> loadAll() {
            return List.copyOf(entries);
        }
    }

    private static final class DataCloudAuditTrailPersistence implements AuditTrailPersistence {
        private static final Map<String, List<StoredAuditEvent>> DURABLE_ENTRIES = new ConcurrentHashMap<>();

        private final DataCloudKernelAdapter dataCloud;
        private final String datasetId;

        private DataCloudAuditTrailPersistence(DataCloudKernelAdapter dataCloud, String datasetId) {
            this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud cannot be null");
            this.datasetId = Objects.requireNonNull(datasetId, "datasetId cannot be null");
        }

        @Override
        public void persist(StoredAuditEvent event) {
            DURABLE_ENTRIES.computeIfAbsent(datasetId, ignored -> new CopyOnWriteArrayList<>()).add(event);

            byte[] payload = (
                event.event().getEventId() + "|" +
                event.event().getEntityId() + "|" +
                event.event().getEventType() + "|" +
                event.hash()
            ).getBytes(StandardCharsets.UTF_8);

            dataCloud.writeData(new DataWriteRequest(
                datasetId,
                event.event().getEventId(),
                payload,
                Map.of(
                    "entityId", event.event().getEntityId(),
                    "eventType", event.event().getEventType(),
                    "hash", event.hash(),
                    "timestamp", Instant.ofEpochMilli(event.event().getTimestamp()).toString(),
                    "retention", "25years"
                )
            ));
        }

        @Override
        public List<StoredAuditEvent> loadAll() {
            return List.copyOf(DURABLE_ENTRIES.getOrDefault(datasetId, List.of()));
        }
    }
}
