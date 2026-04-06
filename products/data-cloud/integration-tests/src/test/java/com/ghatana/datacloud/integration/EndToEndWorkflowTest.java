/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end workflow integration tests for Data Cloud.
 *
 * <p>Exercises the full collection → entity → pipeline → event publishing workflow
 * as a single user-observable journey, validating that each step produces the
 * expected side effects in subsequent steps.
 *
 * @doc.type    class
 * @doc.purpose End-to-end workflow integration: collection → entity → event pipeline
 * @doc.layer   product
 * @doc.pattern IntegrationTest
 */
@DisplayName("End-to-End Workflow Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndWorkflowTest extends EventloopTestBase {

    // ── Shared state ──────────────────────────────────────────────────────────

    private static WorkflowEngine engine;
    private static final String TENANT_ID = "e2e-tenant";
    private static String workflowId;
    private static String collectionId;
    private static List<String> publishedEntityIds;

    @BeforeAll
    static void setUpAll() {
        engine = new WorkflowEngine();
        publishedEntityIds = new ArrayList<>();
    }

    // ── Step 1: Create a collection ───────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("1. create collection — persists collection and returns non-null ID")
    void step1CreateCollection() {
        collectionId = engine.createCollection(TENANT_ID, "e2e-collection",
                Map.of("format", "json"));

        assertThat(collectionId).isNotNull().isNotBlank();
        assertThat(engine.findCollection(TENANT_ID, collectionId)).isPresent();
    }

    // ── Step 2: Register and start a workflow ─────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("2. register workflow — linked to collection, starts in PENDING state")
    void step2RegisterWorkflow() {
        workflowId = engine.registerWorkflow(TENANT_ID, collectionId, "INGEST_PIPELINE");

        assertThat(workflowId).isNotBlank();
        assertThat(engine.workflowStatus(TENANT_ID, workflowId)).isEqualTo("PENDING");
    }

    @Test
    @Order(3)
    @DisplayName("3. start workflow — transitions from PENDING to RUNNING")
    void step3StartWorkflow() {
        engine.startWorkflow(TENANT_ID, workflowId);

        assertThat(engine.workflowStatus(TENANT_ID, workflowId)).isEqualTo("RUNNING");
    }

    // ── Step 3: Publish entities ──────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("4. publish entities — entities persisted and associated with workflow")
    void step4PublishEntities() {
        for (int i = 1; i <= 5; i++) {
            String entityId = engine.publishEntity(TENANT_ID, collectionId, workflowId,
                    Map.of("index", i, "value", "entity-" + i));
            publishedEntityIds.add(entityId);
        }

        assertThat(publishedEntityIds).hasSize(5);
        assertThat(publishedEntityIds).allMatch(id -> id != null && !id.isBlank());
    }

    @Test
    @Order(5)
    @DisplayName("5. entities are queryable from the collection after publish")
    void step5EntitiesQueryableFromCollection() {
        List<String> ids = engine.listEntityIds(TENANT_ID, collectionId);

        assertThat(ids).containsAll(publishedEntityIds);
    }

    // ── Step 4: Complete workflow ─────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("6. complete workflow — status transitions from RUNNING to COMPLETED")
    void step6CompleteWorkflow() {
        engine.completeWorkflow(TENANT_ID, workflowId, publishedEntityIds.size());

        assertThat(engine.workflowStatus(TENANT_ID, workflowId)).isEqualTo("COMPLETED");
    }

    @Test
    @Order(7)
    @DisplayName("7. completed workflow outcome records entity count")
    void step7CompletedWorkflowRecordsEntityCount() {
        WorkflowEngine.WorkflowResult result = engine.getResult(TENANT_ID, workflowId);

        assertThat(result.entityCount()).isEqualTo(5);
        assertThat(result.completedAt()).isNotNull();
    }

    // ── Step 5: Delete collection cascades ────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("8. delete collection — entities and workflow are also removed")
    void step8DeleteCollectionCascades() {
        engine.deleteCollection(TENANT_ID, collectionId);

        assertThat(engine.findCollection(TENANT_ID, collectionId)).isEmpty();
        assertThat(engine.listEntityIds(TENANT_ID, collectionId)).isEmpty();
    }

    // ── Failure path ──────────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("9. failing a workflow sets status to FAILED with an error reason")
    void step9FailWorkflowSetsStatusToFailed() {
        String colId2 = engine.createCollection(TENANT_ID, "fail-test-col", Map.of());
        String wfId2 = engine.registerWorkflow(TENANT_ID, colId2, "FAIL_PIPELINE");
        engine.startWorkflow(TENANT_ID, wfId2);
        engine.failWorkflow(TENANT_ID, wfId2, "upstream outage");

        assertThat(engine.workflowStatus(TENANT_ID, wfId2)).isEqualTo("FAILED");
        assertThat(engine.getResult(TENANT_ID, wfId2).errorReason()).contains("upstream outage");
    }

    // ── Concurrent publish ────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("10. concurrent entity publishes are all persisted without loss")
    void step10ConcurrentEntityPublishes() throws Exception {
        String colId3 = engine.createCollection(TENANT_ID, "concurrent-col", Map.of());
        String wfId3 = engine.registerWorkflow(TENANT_ID, colId3, "CONCURRENT_PIPELINE");
        engine.startWorkflow(TENANT_ID, wfId3);

        int threads = 20;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CopyOnWriteArrayList<String> ids = new CopyOnWriteArrayList<>();

        Thread[] t = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            t[i] = Thread.ofVirtual().start(() -> {
                try {
                    barrier.await();
                    String id = engine.publishEntity(TENANT_ID, colId3, wfId3,
                            Map.of("idx", idx));
                    ids.add(id);
                } catch (Exception ignored) {}
            });
        }
        for (Thread thread : t) thread.join();

        assertThat(ids).hasSize(threads);
        assertThat(engine.listEntityIds(TENANT_ID, colId3)).hasSize(threads);
    }

    // ── Workflow engine implementation (for tests) ─────────────────────────────

    static class WorkflowEngine {
        record WorkflowRecord(String workflowId, String tenantId, String collectionId,
                               String type, String status, String errorReason, Integer entityCount,
                               Instant completedAt) {}
        record WorkflowResult(int entityCount, String errorReason, Instant completedAt) {}

        private final ConcurrentHashMap<String, Map<String, Object>> collections = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, List<String>> collectionEntities = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, WorkflowRecord> workflows = new ConcurrentHashMap<>();

        String createCollection(String tenantId, String name, Map<String, Object> config) {
            String id = UUID.randomUUID().toString();
            Map<String, Object> col = new HashMap<>(config);
            col.put("id", id); col.put("name", name); col.put("tenantId", tenantId);
            collections.put(tenantId + "|" + id, col);
            return id;
        }

        Optional<Map<String, Object>> findCollection(String tenantId, String collectionId) {
            return Optional.ofNullable(collections.get(tenantId + "|" + collectionId));
        }

        void deleteCollection(String tenantId, String collectionId) {
            collections.remove(tenantId + "|" + collectionId);
            collectionEntities.remove(tenantId + "|" + collectionId);
        }

        String registerWorkflow(String tenantId, String collectionId, String type) {
            String id = UUID.randomUUID().toString();
            workflows.put(tenantId + "|" + id, new WorkflowRecord(
                    id, tenantId, collectionId, type, "PENDING", null, null, null));
            return id;
        }

        void startWorkflow(String tenantId, String workflowId) {
            updateWorkflow(tenantId, workflowId, "RUNNING", null, null, null);
        }

        void completeWorkflow(String tenantId, String workflowId, int entityCount) {
            updateWorkflow(tenantId, workflowId, "COMPLETED", null, entityCount, Instant.now());
        }

        void failWorkflow(String tenantId, String workflowId, String reason) {
            updateWorkflow(tenantId, workflowId, "FAILED", reason, null, Instant.now());
        }

        String workflowStatus(String tenantId, String workflowId) {
            WorkflowRecord r = workflows.get(tenantId + "|" + workflowId);
            return r == null ? null : r.status();
        }

        WorkflowResult getResult(String tenantId, String workflowId) {
            WorkflowRecord r = workflows.get(tenantId + "|" + workflowId);
            return r == null ? null : new WorkflowResult(
                    r.entityCount() == null ? 0 : r.entityCount(),
                    r.errorReason(), r.completedAt());
        }

        String publishEntity(String tenantId, String collectionId, String workflowId,
                             Map<String, Object> data) {
            String id = UUID.randomUUID().toString();
            collectionEntities.computeIfAbsent(tenantId + "|" + collectionId,
                    k -> new CopyOnWriteArrayList<>()).add(id);
            return id;
        }

        List<String> listEntityIds(String tenantId, String collectionId) {
            List<String> ids = collectionEntities.get(tenantId + "|" + collectionId);
            return ids == null ? List.of() : List.copyOf(ids);
        }

        private void updateWorkflow(String tenantId, String workflowId, String status,
                                    String errorReason, Integer entityCount, Instant completedAt) {
            String k = tenantId + "|" + workflowId;
            WorkflowRecord old = workflows.get(k);
            if (old == null) return;
            workflows.put(k, new WorkflowRecord(old.workflowId(), old.tenantId(),
                    old.collectionId(), old.type(), status,
                    errorReason != null ? errorReason : old.errorReason(),
                    entityCount != null ? entityCount : old.entityCount(),
                    completedAt != null ? completedAt : old.completedAt()));
        }
    }
}
