/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.plugins.DataCloudRuntimePluginManager;
import com.ghatana.datacloud.launcher.http.plugins.WorkflowExecutionCapability;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * End-to-end workflow integration tests for Data Cloud.
 *
 * <p>Exercises the collection → pipeline → workflow execution → entity persistence →
 * event publication path using the real workflow runtime plugin instead of an in-test
 * workflow engine double.
 *
 * @doc.type class
 * @doc.purpose End-to-end workflow integration through the real Data Cloud runtime plugin path
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("End-to-End Workflow Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndWorkflowTest extends EventloopTestBase {

    private static final String TENANT_ID = "e2e-tenant";
    private static final String COLLECTION_NAME = "e2e_collection";
    private static final String PIPELINE_ID = "pipeline-e2e";

    private static InMemoryDataCloudClient client;
    private static DataCloudRuntimePluginManager runtimePluginManager;
    private static WorkflowExecutionCapability workflowExecution;
    private static String collectionId;
    private static String executionId;
    private static List<String> publishedEntityIds;

    @BeforeAll
    static void setUpAll() {
        client = new InMemoryDataCloudClient();
        runtimePluginManager = new DataCloudRuntimePluginManager();
        runtimePluginManager.registerWorkflowPlugin(client);
        workflowExecution = runtimePluginManager.findCapability(WorkflowExecutionCapability.class)
            .orElseThrow(() -> new AssertionError("workflow execution capability missing"));
        publishedEntityIds = new ArrayList<>();
    }

    @Test
    @Order(1)
    @DisplayName("1. create collection through Data Cloud storage")
    void step1CreateCollection() {
        DataCloudClient.Entity collection = runPromise(() -> client.save(TENANT_ID, "dc_collections", Map.of(
            "id", COLLECTION_NAME,
            "name", "E2E Collection",
            "format", "json"
        )));

        collectionId = collection.id();

        Optional<DataCloudClient.Entity> persistedCollection = runPromise(() -> client.findById(TENANT_ID, "dc_collections", collectionId));
        assertThat(collectionId).isEqualTo(COLLECTION_NAME);
        assertThat(persistedCollection).isPresent();
    }

    @Test
    @Order(2)
    @DisplayName("2. register pipeline definition through Data Cloud storage")
    void step2RegisterWorkflow() {
        DataCloudClient.Entity pipeline = runPromise(() -> client.save(TENANT_ID, "dc_pipelines", Map.of(
            "id", PIPELINE_ID,
            "name", "Entity Ingestion Pipeline",
            "collectionId", collectionId,
            "nodes", List.of(
                Map.of("id", "extract", "type", "EXTRACT", "label", "Extract"),
                Map.of("id", "validate", "type", "VALIDATE", "label", "Validate"),
                Map.of("id", "publish", "type", "PUBLISH", "label", "Publish")
            )
        )));

        assertThat(pipeline.id()).isEqualTo(PIPELINE_ID);
        assertThat(runPromise(() -> client.findById(TENANT_ID, "dc_pipelines", PIPELINE_ID))).isPresent();
    }

    @Test
    @Order(3)
    @DisplayName("3. execute workflow through the runtime plugin and persist execution state")
    void step3ExecuteWorkflow() {
        WorkflowExecutionCapability.ExecutionSnapshot snapshot = runPromise(() -> workflowExecution.execute(
            TENANT_ID,
            PIPELINE_ID,
            Map.of("collectionId", collectionId, "dryRun", false)
        ));

        executionId = snapshot.id();

        assertThat(snapshot.workflowId()).isEqualTo(PIPELINE_ID);
        assertThat(snapshot.status()).isEqualTo("COMPLETED");
        assertThat(snapshot.nodeStatuses()).hasSize(3);

        Optional<WorkflowExecutionCapability.ExecutionSnapshot> persistedExecution = runPromise(() -> workflowExecution.getExecution(TENANT_ID, executionId));
        List<WorkflowExecutionCapability.ExecutionLogEntry> logs = runPromise(() -> workflowExecution.getExecutionLogs(TENANT_ID, executionId));

        assertThat(persistedExecution).isPresent();
        assertThat(logs).hasSize(5);
        assertThat(logs.get(0).message()).isEqualTo("Workflow execution started");
    }

    @Test
    @Order(4)
    @DisplayName("4. persist workflow output entities and append a completion event")
    void step4PersistEntitiesAndPublishEvent() {
        for (int index = 1; index <= 5; index++) {
            DataCloudClient.Entity entity = runPromise(() -> client.save(TENANT_ID, COLLECTION_NAME, Map.of(
                "index", index,
                "value", "entity-" + index,
                "executionId", executionId
            )));
            publishedEntityIds.add(entity.id());
        }

        DataCloudClient.Offset offset = runPromise(() -> client.appendEvent(TENANT_ID, DataCloudClient.Event.of(
            "workflow.completed",
            Map.of("pipelineId", PIPELINE_ID, "executionId", executionId, "entityCount", publishedEntityIds.size())
        )));

        List<DataCloudClient.Entity> entities = runPromise(() -> client.query(TENANT_ID, COLLECTION_NAME, DataCloudClient.Query.all()));
        List<DataCloudClient.Event> events = runPromise(() -> client.queryEvents(TENANT_ID, DataCloudClient.EventQuery.byType("workflow.completed")));

        assertThat(publishedEntityIds).hasSize(5);
        assertThat(entities).extracting(DataCloudClient.Entity::id).containsAll(publishedEntityIds);
        assertThat(offset.value()).isGreaterThan(0);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).payload()).containsEntry("executionId", executionId);
    }

    @Test
    @Order(5)
    @DisplayName("5. execution detail and logs survive runtime plugin restart")
    void step5ExecutionSurvivesRestart() {
        runtimePluginManager.close();
        runtimePluginManager = new DataCloudRuntimePluginManager();
        runtimePluginManager.registerWorkflowPlugin(client);
        workflowExecution = runtimePluginManager.findCapability(WorkflowExecutionCapability.class)
            .orElseThrow(() -> new AssertionError("workflow execution capability missing after restart"));

        Optional<WorkflowExecutionCapability.ExecutionSnapshot> reloadedExecution = runPromise(() -> workflowExecution.getExecution(TENANT_ID, executionId));
        List<WorkflowExecutionCapability.ExecutionSnapshot> executions = runPromise(() -> workflowExecution.listExecutions(TENANT_ID, PIPELINE_ID));
        List<WorkflowExecutionCapability.ExecutionLogEntry> logs = runPromise(() -> workflowExecution.getExecutionLogs(TENANT_ID, executionId));

        assertThat(reloadedExecution).isPresent();
        assertThat(reloadedExecution.orElseThrow().status()).isEqualTo("COMPLETED");
        assertThat(executions).extracting(WorkflowExecutionCapability.ExecutionSnapshot::id).contains(executionId);
        assertThat(logs).hasSize(5);
    }

    @Test
    @Order(6)
    @DisplayName("6. delete collection entities without losing workflow execution history")
    void step6DeleteCollectionData() {
        for (String entityId : publishedEntityIds) {
            runPromise(() -> client.delete(TENANT_ID, COLLECTION_NAME, entityId));
        }
        runPromise(() -> client.delete(TENANT_ID, "dc_collections", collectionId));

        List<DataCloudClient.Entity> entities = runPromise(() -> client.query(TENANT_ID, COLLECTION_NAME, DataCloudClient.Query.all()));
        Optional<DataCloudClient.Entity> collection = runPromise(() -> client.findById(TENANT_ID, "dc_collections", collectionId));
        Optional<WorkflowExecutionCapability.ExecutionSnapshot> execution = runPromise(() -> workflowExecution.getExecution(TENANT_ID, executionId));

        assertThat(entities).isEmpty();
        assertThat(collection).isEmpty();
        assertThat(execution).isPresent();
    }

    private static final class InMemoryDataCloudClient implements DataCloudClient {

        private final Map<String, Map<String, Entity>> recordsByCollection = new ConcurrentHashMap<>();
        private final Map<String, List<Event>> eventsByTenant = new ConcurrentHashMap<>();
        private final EntityStore entityStore = mock(EntityStore.class);
        private final EventLogStore eventLogStore = mock(EventLogStore.class);
        private final AtomicLong nextOffset = new AtomicLong();

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
            eventsByTenant.computeIfAbsent(tenantId, ignored -> new ArrayList<>()).add(event);
            return Promise.of(Offset.of(nextOffset.incrementAndGet()));
        }

        @Override
        public Promise<List<Event>> queryEvents(String tenantId, EventQuery query) {
            List<Event> events = eventsByTenant.getOrDefault(tenantId, List.of()).stream()
                .filter(event -> query.eventTypes().isEmpty() || query.eventTypes().contains(event.type()))
                .limit(query.limit())
                .toList();
            return Promise.of(events);
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
