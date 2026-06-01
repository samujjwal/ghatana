package com.ghatana.datacloud.launcher.http.plugins;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.entity.storage.FilterCriteria;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginCapability;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    @DisplayName("cancelExecution persists CANCELLED status and cancellation log")
    void cancelExecutionPersistsCancelledState() { 
        InMemoryDataCloudClient client = new InMemoryDataCloudClient(); 
        String executionId = "exec-running-001";
        Instant startedAt = Instant.now().minusSeconds(5); 
        Map<String, Object> runningNode = new LinkedHashMap<>(); 
        runningNode.put("id", "extract"); 
        runningNode.put("name", "Extract"); 
        runningNode.put("state", "RUNNING"); 
        runningNode.put("startedAt", startedAt.toString()); 
        runningNode.put("duration", 0); 
        Map<String, Object> runningExecution = new LinkedHashMap<>(); 
        runningExecution.put("id", executionId); 
        runningExecution.put("tenantId", TENANT_ID); 
        runningExecution.put("workflowId", PIPELINE_ID); 
        runningExecution.put("workflowName", "Order Ingestion"); 
        runningExecution.put("status", "RUNNING"); 
        runningExecution.put("progress", 50); 
        runningExecution.put("startedAt", startedAt.toString()); 
        runningExecution.put("nodeStatuses", List.of(runningNode)); 
        runningExecution.put("output", Map.of()); 
        Map<String, Object> initialLogEntry = new LinkedHashMap<>(); 
        initialLogEntry.put("timestamp", startedAt.toString()); 
        initialLogEntry.put("level", "info"); 
        initialLogEntry.put("message", "Workflow execution started"); 
        initialLogEntry.put("metadata", Map.of("workflowId", PIPELINE_ID)); 

        runPromise(() -> client.save(TENANT_ID, "dc_workflow_executions", runningExecution)); 
        runPromise(() -> client.save(TENANT_ID, "dc_workflow_execution_logs", Map.of( 
            "id", executionId,
            "tenantId", TENANT_ID,
            "executionId", executionId,
            "workflowId", PIPELINE_ID,
            "entries", List.of(initialLogEntry) 
        )));

        DataCloudRuntimePluginManager manager = new DataCloudRuntimePluginManager(); 
        manager.registerWorkflowPlugin(client); 

        WorkflowExecutionCapability capability = manager.findCapability(WorkflowExecutionCapability.class) 
            .orElseThrow(() -> new AssertionError("workflow capability missing"));

        WorkflowExecutionCapability.ExecutionSnapshot cancelled = runPromise(() -> capability.cancelExecution(TENANT_ID, executionId)); 
        List<WorkflowExecutionCapability.ExecutionLogEntry> logs = runPromise(() -> capability.getExecutionLogs(TENANT_ID, executionId)); 

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.completedAt()).isNotNull(); 
        assertThat(logs).extracting(WorkflowExecutionCapability.ExecutionLogEntry::message) 
            .contains("Execution cancelled");

        manager.close(); 
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

    @Test
    @DisplayName("hotSwapPlugin restores previous plugin when replacement initialization fails")
    void hotSwapPluginRestoresPreviousPluginOnFailure() {
        DataCloudRuntimePluginManager manager = new DataCloudRuntimePluginManager();
        AtomicBoolean activeFlag = new AtomicBoolean(true);

        manager.registerProvider(new DataCloudRuntimePluginManager.ManagedRuntimePluginProvider() {
            @Override
            public String pluginId() {
                return "safe-hot-swap-plugin";
            }

            @Override
            public Plugin create(Map<String, Object> config) {
                boolean failInit = Boolean.TRUE.equals(config.get("failInit"));
                return new ToggleLifecyclePlugin(activeFlag, failInit);
            }
        });

        assertThat(manager.getPlugin("safe-hot-swap-plugin")).isPresent();
        assertThat(activeFlag.get()).isTrue();

        Throwable failure = null;
        try {
            runPromise(() -> manager.hotSwapPlugin("safe-hot-swap-plugin", Map.of("failInit", true)));
        } catch (Throwable throwable) {
            failure = throwable;
        }

        assertThat(failure).isNotNull();
        assertThat(failure).hasMessageContaining("forced initialize failure");
        assertThat(manager.getPlugin("safe-hot-swap-plugin")).isPresent();
        assertThat(activeFlag.get()).isTrue();

        manager.close();
    }

    private static final class ToggleLifecyclePlugin implements Plugin {
        private final AtomicBoolean activeFlag;
        private final boolean failInit;
        private volatile PluginState state = PluginState.UNLOADED;

        private ToggleLifecyclePlugin(AtomicBoolean activeFlag, boolean failInit) {
            this.activeFlag = activeFlag;
            this.failInit = failInit;
        }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder()
                .id("safe-hot-swap-plugin")
                .name("Safe Hot Swap Plugin")
                .version("1.0.0")
                .description("Test plugin for rollback-safe hot swap")
                .type(PluginType.CUSTOM)
                .capabilities(Set.of("test"))
                .build();
        }

        @Override
        public PluginState getState() {
            return state;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            if (failInit) {
                activeFlag.set(false);
                return Promise.ofException(new IllegalStateException("forced initialize failure"));
            }
            state = PluginState.INITIALIZED;
            return Promise.complete();
        }

        @Override
        public Promise<Void> start() {
            state = PluginState.RUNNING;
            activeFlag.set(true);
            return Promise.complete();
        }

        @Override
        public Promise<Void> stop() {
            state = PluginState.STOPPED;
            activeFlag.set(false);
            return Promise.complete();
        }

        @Override
        public Promise<Void> shutdown() {
            state = PluginState.STOPPED;
            activeFlag.set(false);
            return Promise.complete();
        }

        @Override
        public Promise<HealthStatus> healthCheck() {
            return Promise.of(HealthStatus.ok("ok"));
        }

        @Override
        public Set<PluginCapability> getCapabilities() {
            return Set.of();
        }
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
                if (filter.operator() != FilterCriteria.Operator.EQ) {
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
