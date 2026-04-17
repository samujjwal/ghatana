package com.ghatana.datacloud.launcher.http.plugins;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @doc.type class
 * @doc.purpose Regression tests for durable workflow execution plugin persistence
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudRuntimePluginManager")
class DataCloudRuntimePluginManagerTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-default";
    private static final String PIPELINE_ID = "pipeline-001";

    @Test
    @DisplayName("workflow executions and logs survive plugin manager restart")
    void workflowExecutionsSurvivePluginManagerRestart() {
        InMemoryDataCloudClient client = new InMemoryDataCloudClient();
        runPromise(() -> client.save(TENANT_ID, "dc_pipelines", Map.of(
            "id", PIPELINE_ID,
            "name", "Order Ingestion",
            "nodes", List.of(
                Map.of("id", "extract", "type", "EXTRACT", "label", "Extract"),
                Map.of("id", "publish", "type", "PUBLISH", "label", "Publish")
            )
        )));

        DataCloudRuntimePluginManager manager = new DataCloudRuntimePluginManager();
        manager.registerWorkflowPlugin(client);

        WorkflowExecutionCapability capability = manager.findCapability(WorkflowExecutionCapability.class)
            .orElseThrow(() -> new AssertionError("workflow capability missing"));

        WorkflowExecutionCapability.ExecutionSnapshot created = runPromise(() -> capability.execute(
            TENANT_ID,
            PIPELINE_ID,
            Map.of("dryRun", true)
        ));
        List<WorkflowExecutionCapability.ExecutionLogEntry> initialLogs = runPromise(() -> capability.getExecutionLogs(TENANT_ID, created.id()));

        assertThat(created.status()).isEqualTo("COMPLETED");
        assertThat(initialLogs).hasSize(4);

        manager.close();

        DataCloudRuntimePluginManager restartedManager = new DataCloudRuntimePluginManager();
        restartedManager.registerWorkflowPlugin(client);

        WorkflowExecutionCapability restartedCapability = restartedManager.findCapability(WorkflowExecutionCapability.class)
            .orElseThrow(() -> new AssertionError("workflow capability missing after restart"));

        Optional<WorkflowExecutionCapability.ExecutionSnapshot> reloaded = runPromise(() -> restartedCapability.getExecution(TENANT_ID, created.id()));
        List<WorkflowExecutionCapability.ExecutionLogEntry> reloadedLogs = runPromise(() -> restartedCapability.getExecutionLogs(TENANT_ID, created.id()));
        List<WorkflowExecutionCapability.ExecutionSnapshot> workflowExecutions = runPromise(() -> restartedCapability.listExecutions(TENANT_ID, PIPELINE_ID));

        assertThat(reloaded).isPresent();
        assertThat(reloaded.orElseThrow().workflowId()).isEqualTo(PIPELINE_ID);
        assertThat(reloaded.orElseThrow().status()).isEqualTo("COMPLETED");
        assertThat(reloadedLogs).hasSameSizeAs(initialLogs);
        assertThat(workflowExecutions).extracting(WorkflowExecutionCapability.ExecutionSnapshot::id)
            .contains(created.id());

        restartedManager.close();
    }

    private static final class InMemoryDataCloudClient implements DataCloudClient {

        private final Map<String, Map<String, Entity>> recordsByCollection = new ConcurrentHashMap<>();
        private final EntityStore entityStore = mock(EntityStore.class);
        private final EventLogStore eventLogStore = mock(EventLogStore.class);

        @Override
        public Promise<Entity> save(String tenantId, String collection, Map<String, Object> data) {
            String recordId = data.get("id") instanceof String id && !id.isBlank()
                ? id
                : UUID.randomUUID().toString();
            String bucketKey = bucketKey(tenantId, collection);
            Map<String, Entity> bucket = recordsByCollection.computeIfAbsent(bucketKey, ignored -> new ConcurrentHashMap<>());
            Entity existing = bucket.get(recordId);
            Map<String, Object> normalized = new LinkedHashMap<>(data);
            normalized.put("id", recordId);
            normalized.putIfAbsent("tenantId", tenantId);
            Instant now = Instant.now();
            Entity entity = new Entity(
                recordId,
                collection,
                normalized,
                existing != null ? existing.createdAt() : now,
                now,
                existing != null ? existing.version() + 1 : 1
            );
            bucket.put(recordId, entity);
            return Promise.of(entity);
        }

        @Override
        public Promise<Optional<Entity>> findById(String tenantId, String collection, String id) {
            return Promise.of(Optional.ofNullable(recordsByCollection
                .getOrDefault(bucketKey(tenantId, collection), Map.of())
                .get(id)));
        }

        @Override
        public Promise<List<Entity>> query(String tenantId, String collection, Query query) {
            List<Entity> items = new ArrayList<>(recordsByCollection
                .getOrDefault(bucketKey(tenantId, collection), Map.of())
                .values());

            List<Entity> filtered = items.stream()
                .filter(entity -> matchesFilters(entity, query.filters()))
                .sorted(buildComparator(query.sorts()))
                .toList();

            int fromIndex = Math.min(query.offset(), filtered.size());
            int toIndex = Math.min(fromIndex + query.limit(), filtered.size());
            return Promise.of(filtered.subList(fromIndex, toIndex));
        }

        @Override
        public Promise<Void> delete(String tenantId, String collection, String id) {
            Map<String, Entity> bucket = recordsByCollection.get(bucketKey(tenantId, collection));
            if (bucket != null) {
                bucket.remove(id);
            }
            return Promise.of(null);
        }

        @Override
        public Promise<Offset> appendEvent(String tenantId, Event event) {
            return Promise.ofException(new UnsupportedOperationException("appendEvent not used in this test"));
        }

        @Override
        public Promise<List<Event>> queryEvents(String tenantId, EventQuery query) {
            return Promise.of(List.of());
        }

        @Override
        public Subscription tailEvents(String tenantId, TailRequest request, Consumer<Event> handler) {
            return new Subscription() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return true;
                }
            };
        }

        @Override
        public void close() {
        }

        @Override
        public EntityStore entityStore() {
            return entityStore;
        }

        @Override
        public EventLogStore eventLogStore() {
            return eventLogStore;
        }

        private boolean matchesFilters(Entity entity, List<Filter> filters) {
            return filters.stream().allMatch(filter -> {
                Object value = "id".equals(filter.field()) ? entity.id() : entity.data().get(filter.field());
                if (!"eq".equals(filter.operator())) {
                    return true;
                }
                return value != null && value.equals(filter.value());
            });
        }

        private Comparator<Entity> buildComparator(List<Sort> sorts) {
            Comparator<Entity> comparator = Comparator.comparing(Entity::id);
            for (Sort sort : sorts) {
                Comparator<Entity> nextComparator = Comparator.comparing(entity -> String.valueOf(entity.data().get(sort.field())));
                comparator = sort.ascending() ? nextComparator.thenComparing(comparator) : nextComparator.reversed().thenComparing(comparator);
            }
            return comparator;
        }

        private String bucketKey(String tenantId, String collection) {
            return tenantId + "|" + collection;
        }
    }
}
