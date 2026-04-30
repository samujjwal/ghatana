package com.ghatana.datacloud.launcher.http.plugins;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EventLogStore;
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
    void workflowExecutionsSurvivePluginManagerRestart() { // GH-90000
        InMemoryDataCloudClient client = new InMemoryDataCloudClient(); // GH-90000
        runPromise(() -> client.save(TENANT_ID, "dc_pipelines", Map.of( // GH-90000
            "id", PIPELINE_ID,
            "name", "Order Ingestion",
            "nodes", List.of( // GH-90000
                Map.of("id", "extract", "type", "EXTRACT", "label", "Extract"), // GH-90000
                Map.of("id", "publish", "type", "PUBLISH", "label", "Publish") // GH-90000
            )
        )));

        DataCloudRuntimePluginManager manager = new DataCloudRuntimePluginManager(); // GH-90000
        manager.registerWorkflowPlugin(client); // GH-90000

        WorkflowExecutionCapability capability = manager.findCapability(WorkflowExecutionCapability.class) // GH-90000
            .orElseThrow(() -> new AssertionError("workflow capability missing"));

        WorkflowExecutionCapability.ExecutionSnapshot created = runPromise(() -> capability.execute( // GH-90000
            TENANT_ID,
            PIPELINE_ID,
            Map.of("dryRun", true) // GH-90000
        ));
        List<WorkflowExecutionCapability.ExecutionLogEntry> initialLogs = runPromise(() -> capability.getExecutionLogs(TENANT_ID, created.id())); // GH-90000

        assertThat(created.status()).isEqualTo("COMPLETED");
        assertThat(initialLogs).hasSize(4); // GH-90000

        manager.close(); // GH-90000

        DataCloudRuntimePluginManager restartedManager = new DataCloudRuntimePluginManager(); // GH-90000
        restartedManager.registerWorkflowPlugin(client); // GH-90000

        WorkflowExecutionCapability restartedCapability = restartedManager.findCapability(WorkflowExecutionCapability.class) // GH-90000
            .orElseThrow(() -> new AssertionError("workflow capability missing after restart"));

        Optional<WorkflowExecutionCapability.ExecutionSnapshot> reloaded = runPromise(() -> restartedCapability.getExecution(TENANT_ID, created.id())); // GH-90000
        List<WorkflowExecutionCapability.ExecutionLogEntry> reloadedLogs = runPromise(() -> restartedCapability.getExecutionLogs(TENANT_ID, created.id())); // GH-90000
        List<WorkflowExecutionCapability.ExecutionSnapshot> workflowExecutions = runPromise(() -> restartedCapability.listExecutions(TENANT_ID, PIPELINE_ID)); // GH-90000

        assertThat(reloaded).isPresent(); // GH-90000
        assertThat(reloaded.orElseThrow().workflowId()).isEqualTo(PIPELINE_ID); // GH-90000
        assertThat(reloaded.orElseThrow().status()).isEqualTo("COMPLETED");
        assertThat(reloadedLogs).hasSameSizeAs(initialLogs); // GH-90000
        assertThat(workflowExecutions).extracting(WorkflowExecutionCapability.ExecutionSnapshot::id) // GH-90000
            .contains(created.id()); // GH-90000

        restartedManager.close(); // GH-90000
    }

    @Test
    @DisplayName("cancelExecution persists CANCELLED status and cancellation log")
    void cancelExecutionPersistsCancelledState() { // GH-90000
        InMemoryDataCloudClient client = new InMemoryDataCloudClient(); // GH-90000
        String executionId = "exec-running-001";
        Instant startedAt = Instant.now().minusSeconds(5); // GH-90000
        Map<String, Object> runningNode = new LinkedHashMap<>(); // GH-90000
        runningNode.put("id", "extract"); // GH-90000
        runningNode.put("name", "Extract"); // GH-90000
        runningNode.put("state", "RUNNING"); // GH-90000
        runningNode.put("startedAt", startedAt.toString()); // GH-90000
        runningNode.put("duration", 0); // GH-90000
        Map<String, Object> runningExecution = new LinkedHashMap<>(); // GH-90000
        runningExecution.put("id", executionId); // GH-90000
        runningExecution.put("tenantId", TENANT_ID); // GH-90000
        runningExecution.put("workflowId", PIPELINE_ID); // GH-90000
        runningExecution.put("workflowName", "Order Ingestion"); // GH-90000
        runningExecution.put("status", "RUNNING"); // GH-90000
        runningExecution.put("progress", 50); // GH-90000
        runningExecution.put("startedAt", startedAt.toString()); // GH-90000
        runningExecution.put("nodeStatuses", List.of(runningNode)); // GH-90000
        runningExecution.put("output", Map.of()); // GH-90000
        Map<String, Object> initialLogEntry = new LinkedHashMap<>(); // GH-90000
        initialLogEntry.put("timestamp", startedAt.toString()); // GH-90000
        initialLogEntry.put("level", "info"); // GH-90000
        initialLogEntry.put("message", "Workflow execution started"); // GH-90000
        initialLogEntry.put("metadata", Map.of("workflowId", PIPELINE_ID)); // GH-90000

        runPromise(() -> client.save(TENANT_ID, "dc_workflow_executions", runningExecution)); // GH-90000
        runPromise(() -> client.save(TENANT_ID, "dc_workflow_execution_logs", Map.of( // GH-90000
            "id", executionId,
            "tenantId", TENANT_ID,
            "executionId", executionId,
            "workflowId", PIPELINE_ID,
            "entries", List.of(initialLogEntry) // GH-90000
        )));

        DataCloudRuntimePluginManager manager = new DataCloudRuntimePluginManager(); // GH-90000
        manager.registerWorkflowPlugin(client); // GH-90000

        WorkflowExecutionCapability capability = manager.findCapability(WorkflowExecutionCapability.class) // GH-90000
            .orElseThrow(() -> new AssertionError("workflow capability missing"));

        WorkflowExecutionCapability.ExecutionSnapshot cancelled = runPromise(() -> capability.cancelExecution(TENANT_ID, executionId)); // GH-90000
        List<WorkflowExecutionCapability.ExecutionLogEntry> logs = runPromise(() -> capability.getExecutionLogs(TENANT_ID, executionId)); // GH-90000

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.completedAt()).isNotNull(); // GH-90000
        assertThat(logs).extracting(WorkflowExecutionCapability.ExecutionLogEntry::message) // GH-90000
            .contains("Execution cancelled");

        manager.close(); // GH-90000
    }

    @Test
    @DisplayName("retryExecution creates a new execution and persists retry log")
    void retryExecutionCreatesNewExecutionAndRetryLog() {
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
        WorkflowExecutionCapability.ExecutionSnapshot retried = runPromise(() -> capability.retryExecution(TENANT_ID, created.id()));

        List<WorkflowExecutionCapability.ExecutionLogEntry> retryLogs = runPromise(() -> capability.getExecutionLogs(TENANT_ID, retried.id()));
        List<WorkflowExecutionCapability.ExecutionSnapshot> executions = runPromise(() -> capability.listExecutions(TENANT_ID, PIPELINE_ID));

        assertThat(retried.id()).isNotEqualTo(created.id());
        assertThat(retried.workflowId()).isEqualTo(created.workflowId());
        assertThat(retryLogs).extracting(WorkflowExecutionCapability.ExecutionLogEntry::message)
            .contains("Execution retried");
        assertThat(executions).extracting(WorkflowExecutionCapability.ExecutionSnapshot::id)
            .contains(created.id(), retried.id());

        manager.close();
    }

    private static final class InMemoryDataCloudClient implements DataCloudClient {

        private final Map<String, Map<String, Entity>> recordsByCollection = new ConcurrentHashMap<>(); // GH-90000
        private final EntityStore entityStore = mock(EntityStore.class); // GH-90000
        private final EventLogStore eventLogStore = mock(EventLogStore.class); // GH-90000

        @Override
        public Promise<Entity> save(String tenantId, String collection, Map<String, Object> data) { // GH-90000
            String recordId = data.get("id") instanceof String id && !id.isBlank()
                ? id
                : UUID.randomUUID().toString(); // GH-90000
            String bucketKey = bucketKey(tenantId, collection); // GH-90000
            Map<String, Entity> bucket = recordsByCollection.computeIfAbsent(bucketKey, ignored -> new ConcurrentHashMap<>()); // GH-90000
            Entity existing = bucket.get(recordId); // GH-90000
            Map<String, Object> normalized = new LinkedHashMap<>(data); // GH-90000
            normalized.put("id", recordId); // GH-90000
            normalized.putIfAbsent("tenantId", tenantId); // GH-90000
            Instant now = Instant.now(); // GH-90000
            Entity entity = new Entity( // GH-90000
                recordId,
                collection,
                normalized,
                existing != null ? existing.createdAt() : now, // GH-90000
                now,
                existing != null ? existing.version() + 1 : 1 // GH-90000
            );
            bucket.put(recordId, entity); // GH-90000
            return Promise.of(entity); // GH-90000
        }

        @Override
        public Promise<Optional<Entity>> findById(String tenantId, String collection, String id) { // GH-90000
            return Promise.of(Optional.ofNullable(recordsByCollection // GH-90000
                .getOrDefault(bucketKey(tenantId, collection), Map.of()) // GH-90000
                .get(id))); // GH-90000
        }

        @Override
        public Promise<List<Entity>> query(String tenantId, String collection, Query query) { // GH-90000
            List<Entity> items = new ArrayList<>(recordsByCollection // GH-90000
                .getOrDefault(bucketKey(tenantId, collection), Map.of()) // GH-90000
                .values()); // GH-90000

            List<Entity> filtered = items.stream() // GH-90000
                .filter(entity -> matchesFilters(entity, query.filters())) // GH-90000
                .sorted(buildComparator(query.sorts())) // GH-90000
                .toList(); // GH-90000

            int fromIndex = Math.min(query.offset(), filtered.size()); // GH-90000
            int toIndex = Math.min(fromIndex + query.limit(), filtered.size()); // GH-90000
            return Promise.of(filtered.subList(fromIndex, toIndex)); // GH-90000
        }

        @Override
        public Promise<Void> delete(String tenantId, String collection, String id) { // GH-90000
            Map<String, Entity> bucket = recordsByCollection.get(bucketKey(tenantId, collection)); // GH-90000
            if (bucket != null) { // GH-90000
                bucket.remove(id); // GH-90000
            }
            return Promise.of(null); // GH-90000
        }

        @Override
        public Promise<Offset> appendEvent(String tenantId, Event event) { // GH-90000
            return Promise.ofException(new UnsupportedOperationException("appendEvent not used in this test"));
        }

        @Override
        public Promise<List<Event>> queryEvents(String tenantId, EventQuery query) { // GH-90000
            return Promise.of(List.of()); // GH-90000
        }

        @Override
        public Subscription tailEvents(String tenantId, TailRequest request, Consumer<Event> handler) { // GH-90000
            return new Subscription() { // GH-90000
                @Override
                public void cancel() { // GH-90000
                }

                @Override
                public boolean isCancelled() { // GH-90000
                    return true;
                }
            };
        }

        @Override
        public void close() { // GH-90000
        }

        @Override
        public EntityStore entityStore() { // GH-90000
            return entityStore;
        }

        @Override
        public EventLogStore eventLogStore() { // GH-90000
            return eventLogStore;
        }

        private boolean matchesFilters(Entity entity, List<Filter> filters) { // GH-90000
            return filters.stream().allMatch(filter -> { // GH-90000
                Object value = "id".equals(filter.field()) ? entity.id() : entity.data().get(filter.field()); // GH-90000
                if (!"eq".equals(filter.operator())) { // GH-90000
                    return true;
                }
                return value != null && value.equals(filter.value()); // GH-90000
            });
        }

        private Comparator<Entity> buildComparator(List<Sort> sorts) { // GH-90000
            Comparator<Entity> comparator = Comparator.comparing(Entity::id); // GH-90000
            for (Sort sort : sorts) { // GH-90000
                Comparator<Entity> nextComparator = Comparator.comparing(entity -> String.valueOf(entity.data().get(sort.field()))); // GH-90000
                comparator = sort.ascending() ? nextComparator.thenComparing(comparator) : nextComparator.reversed().thenComparing(comparator); // GH-90000
            }
            return comparator;
        }

        private String bucketKey(String tenantId, String collection) { // GH-90000
            return tenantId + "|" + collection;
        }
    }
}
