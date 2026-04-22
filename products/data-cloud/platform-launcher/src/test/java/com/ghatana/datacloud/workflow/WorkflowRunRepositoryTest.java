package com.ghatana.datacloud.workflow;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRunRepositoryTest {

    @Test
    @DisplayName("startRun stores generic executionId in the event payload and status view [GH-90000]")
    void startRunStoresExecutionId() { // GH-90000
        InMemoryEventLogStore store = new InMemoryEventLogStore(); // GH-90000
        WorkflowRunRepository repository = new WorkflowRunRepository(store); // GH-90000

        String runId = repository.startRun("tenant-a", "wf-1", "exec-42", Map.of("source", "test")).getResult(); // GH-90000
        WorkflowRunRepository.WorkflowRunStatus status = repository.getRunStatus("tenant-a", runId) // GH-90000
                .getResult() // GH-90000
                .orElseThrow(); // GH-90000

        assertThat(status.executionId()).isEqualTo("exec-42 [GH-90000]");
        assertThat(readPayload(store.entries().getFirst()).contains("\"executionId\":\"exec-42\"")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("getRunStatus remains backward compatible with legacy pipelineId payloads [GH-90000]")
    void getRunStatusSupportsLegacyPipelineIdPayloads() { // GH-90000
        InMemoryEventLogStore store = new InMemoryEventLogStore(); // GH-90000
        WorkflowRunRepository repository = new WorkflowRunRepository(store); // GH-90000

        store.seed("tenant-a", WorkflowRunRepository.EVENT_RUN_STARTED, // GH-90000
                """
                {"runId":"run-legacy","workflowId":"wf-legacy","pipelineId":"pipe-9","startedAt":"2026-04-03T00:00:00Z"}
                """);

        WorkflowRunRepository.WorkflowRunStatus status = repository.getRunStatus("tenant-a", "run-legacy") // GH-90000
                .getResult() // GH-90000
                .orElseThrow(); // GH-90000

        assertThat(status.executionId()).isEqualTo("pipe-9 [GH-90000]");
    }

    private static String readPayload(EventLogStore.EventEntry entry) { // GH-90000
        ByteBuffer duplicate = entry.payload().duplicate(); // GH-90000
        byte[] bytes = new byte[duplicate.remaining()]; // GH-90000
        duplicate.get(bytes); // GH-90000
        return new String(bytes, StandardCharsets.UTF_8); // GH-90000
    }

    private static final class InMemoryEventLogStore implements EventLogStore {
        private final List<StoredEvent> entries = new ArrayList<>(); // GH-90000
        private final AtomicLong nextOffset = new AtomicLong(); // GH-90000

        List<EventEntry> entries() { // GH-90000
            return entries.stream().map(StoredEvent::entry).toList(); // GH-90000
        }

        void seed(String tenantId, String eventType, String payloadJson) { // GH-90000
            append(TenantContext.of(tenantId), EventEntry.builder() // GH-90000
                    .eventType(eventType) // GH-90000
                    .timestamp(Instant.now()) // GH-90000
                    .payload(payloadJson) // GH-90000
                    .contentType("application/json [GH-90000]")
                    .build()); // GH-90000
        }

        @Override
        public Promise<Offset> append(TenantContext tenant, EventEntry entry) { // GH-90000
            Offset offset = Offset.of(nextOffset.getAndIncrement()); // GH-90000
            entries.add(new StoredEvent(tenant.tenantId(), offset, entry)); // GH-90000
            return Promise.of(offset); // GH-90000
        }

        @Override
        public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) { // GH-90000
            List<Offset> offsets = new ArrayList<>(entries.size()); // GH-90000
            for (EventEntry entry : entries) { // GH-90000
                offsets.add(append(tenant, entry).getResult()); // GH-90000
            }
            return Promise.of(offsets); // GH-90000
        }

        @Override
        public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) { // GH-90000
            return Promise.of(entries.stream() // GH-90000
                    .filter(e -> e.tenantId().equals(tenant.tenantId())) // GH-90000
                    .filter(e -> offsetValue(e.offset()) >= offsetValue(from)) // GH-90000
                    .sorted((left, right) -> Long.compare(offsetValue(left.offset()), offsetValue(right.offset()))) // GH-90000
                    .limit(limit) // GH-90000
                    .map(StoredEvent::entry) // GH-90000
                    .toList()); // GH-90000
        }

        @Override
        public Promise<List<EventEntry>> readByTimeRange(TenantContext tenant, Instant startTime, Instant endTime, int limit) { // GH-90000
            return Promise.of(entries.stream() // GH-90000
                    .filter(e -> e.tenantId().equals(tenant.tenantId())) // GH-90000
                    .map(StoredEvent::entry) // GH-90000
                    .filter(e -> !e.timestamp().isBefore(startTime) && e.timestamp().isBefore(endTime)) // GH-90000
                    .limit(limit) // GH-90000
                    .toList()); // GH-90000
        }

        @Override
        public Promise<List<EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) { // GH-90000
            return Promise.of(entries.stream() // GH-90000
                    .filter(e -> e.tenantId().equals(tenant.tenantId())) // GH-90000
                    .filter(e -> offsetValue(e.offset()) >= offsetValue(from)) // GH-90000
                    .map(StoredEvent::entry) // GH-90000
                    .filter(e -> e.eventType().equals(eventType)) // GH-90000
                    .limit(limit) // GH-90000
                    .toList()); // GH-90000
        }

        @Override
        public Promise<Offset> getLatestOffset(TenantContext tenant) { // GH-90000
            return Promise.of(Offset.of(Math.max(0, nextOffset.get() - 1))); // GH-90000
        }

        @Override
        public Promise<Offset> getEarliestOffset(TenantContext tenant) { // GH-90000
            return Promise.of(Offset.zero()); // GH-90000
        }

        @Override
        public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) { // GH-90000
            return Promise.of(new Subscription() { // GH-90000
                @Override
                public void cancel() { // GH-90000
                }

                @Override
                public boolean isCancelled() { // GH-90000
                    return false;
                }
            });
        }

        private record StoredEvent(String tenantId, Offset offset, EventEntry entry) { // GH-90000
        }

        private long offsetValue(Offset offset) { // GH-90000
            return Long.parseLong(offset.value()); // GH-90000
        }
    }
}
