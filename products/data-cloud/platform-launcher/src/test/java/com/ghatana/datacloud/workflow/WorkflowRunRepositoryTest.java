package com.ghatana.datacloud.workflow;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRunRepositoryTest {

    @Test
    @DisplayName("startRun stores generic executionId in the event payload and status view")
    void startRunStoresExecutionId() {
        InMemoryEventLogStore store = new InMemoryEventLogStore();
        WorkflowRunRepository repository = new WorkflowRunRepository(store);

        String runId = repository.startRun("tenant-a", "wf-1", "exec-42", Map.of("source", "test")).getResult();
        WorkflowRunRepository.WorkflowRunStatus status = repository.getRunStatus("tenant-a", runId)
                .getResult()
                .orElseThrow();

        assertThat(status.executionId()).isEqualTo("exec-42");
        assertThat(readPayload(store.entries().getFirst()).contains("\"executionId\":\"exec-42\"")).isTrue();
    }

    @Test
    @DisplayName("getRunStatus remains backward compatible with legacy pipelineId payloads")
    void getRunStatusSupportsLegacyPipelineIdPayloads() {
        InMemoryEventLogStore store = new InMemoryEventLogStore();
        WorkflowRunRepository repository = new WorkflowRunRepository(store);

        store.seed("tenant-a", WorkflowRunRepository.EVENT_RUN_STARTED,
                """
                {"runId":"run-legacy","workflowId":"wf-legacy","pipelineId":"pipe-9","startedAt":"2026-04-03T00:00:00Z"}
                """);

        WorkflowRunRepository.WorkflowRunStatus status = repository.getRunStatus("tenant-a", "run-legacy")
                .getResult()
                .orElseThrow();

        assertThat(status.executionId()).isEqualTo("pipe-9");
    }

    private static String readPayload(EventLogStore.EventEntry entry) {
        ByteBuffer duplicate = entry.payload().duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static final class InMemoryEventLogStore implements EventLogStore {
        private final List<StoredEvent> entries = new ArrayList<>();
        private final AtomicLong nextOffset = new AtomicLong();

        List<EventEntry> entries() {
            return entries.stream().map(StoredEvent::entry).toList();
        }

        void seed(String tenantId, String eventType, String payloadJson) {
            append(TenantContext.of(tenantId), EventEntry.builder()
                    .eventType(eventType)
                    .timestamp(Instant.now())
                    .payload(payloadJson)
                    .contentType("application/json")
                    .build());
        }

        @Override
        public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
            Offset offset = Offset.of(nextOffset.getAndIncrement());
            entries.add(new StoredEvent(tenant.tenantId(), offset, entry));
            return Promise.of(offset);
        }

        @Override
        public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
            List<Offset> offsets = new ArrayList<>(entries.size());
            for (EventEntry entry : entries) {
                offsets.add(append(tenant, entry).getResult());
            }
            return Promise.of(offsets);
        }

        @Override
        public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
            return Promise.of(entries.stream()
                    .filter(e -> e.tenantId().equals(tenant.tenantId()))
                    .filter(e -> offsetValue(e.offset()) >= offsetValue(from))
                    .sorted((left, right) -> Long.compare(offsetValue(left.offset()), offsetValue(right.offset())))
                    .limit(limit)
                    .map(StoredEvent::entry)
                    .toList());
        }

        @Override
        public Promise<List<EventEntry>> readByTimeRange(TenantContext tenant, Instant startTime, Instant endTime, int limit) {
            return Promise.of(entries.stream()
                    .filter(e -> e.tenantId().equals(tenant.tenantId()))
                    .map(StoredEvent::entry)
                    .filter(e -> !e.timestamp().isBefore(startTime) && e.timestamp().isBefore(endTime))
                    .limit(limit)
                    .toList());
        }

        @Override
        public Promise<List<EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) {
            return Promise.of(entries.stream()
                    .filter(e -> e.tenantId().equals(tenant.tenantId()))
                    .filter(e -> offsetValue(e.offset()) >= offsetValue(from))
                    .map(StoredEvent::entry)
                    .filter(e -> e.eventType().equals(eventType))
                    .limit(limit)
                    .toList());
        }

        @Override
        public Promise<Offset> getLatestOffset(TenantContext tenant) {
            return Promise.of(Offset.of(Math.max(0, nextOffset.get() - 1)));
        }

        @Override
        public Promise<Offset> getEarliestOffset(TenantContext tenant) {
            return Promise.of(Offset.zero());
        }

        @Override
        public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
            return Promise.of(new Subscription() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            });
        }

        private record StoredEvent(String tenantId, Offset offset, EventEntry entry) {
        }

        private long offsetValue(Offset offset) {
            return Long.parseLong(offset.value());
        }
    }
}
