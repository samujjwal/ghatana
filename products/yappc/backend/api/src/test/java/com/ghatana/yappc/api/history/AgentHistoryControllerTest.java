/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.taskstate.TaskLifecycleStatus;
import com.ghatana.agent.memory.model.taskstate.TaskState;
import com.ghatana.agent.memory.model.working.WorkingMemoryConfig;
import com.ghatana.agent.memory.persistence.JdbcTaskStateRepository;
import com.ghatana.agent.memory.persistence.MemoryItemRepository;
import com.ghatana.agent.memory.persistence.PersistentMemoryPlane;
import com.ghatana.agent.memory.persistence.TaskStateRepository;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.agent.memory.store.taskstate.JdbcTaskStateStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AgentHistoryController}.
 *
 * <p>Covers plan task 6.4.4 — execute agent 3 times, query history, assert 3 records.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AgentHistoryController history and rationale endpoints
 * @doc.layer api
 * @doc.pattern Test
 * @doc.gaa.lifecycle capture
 */
@DisplayName("AgentHistoryController Tests (6.4)")
class AgentHistoryControllerTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-alpha";
    private static final String AGENT_ID  = "requirements-analyst-v2";

    /**
     * In-memory TaskStateRepository that actually stores tasks for test purposes.
     */
    private static class MapTaskStateRepository implements TaskStateRepository {
        private final ConcurrentHashMap<String, TaskState> store = new ConcurrentHashMap<>();

        @Override
        public Promise<TaskState> save(TaskState task) {
            store.put(task.getTaskId(), task);
            return Promise.of(task);
        }

        @Override
        public Promise<TaskState> findById(String taskId) {
            return Promise.of(store.get(taskId));
        }

        @Override
        public Promise<List<TaskState>> findActiveByAgent(String agentId) {
            List<TaskState> result = store.values().stream()
                    .filter(t -> agentId.equals(t.getAgentId()))
                    .collect(Collectors.toList());
            return Promise.of(result);
        }

        @Override
        public Promise<Void> updateStatus(String taskId, String status) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> archive(String taskId) {
            store.remove(taskId);
            return Promise.complete();
        }

        @Override
        public Promise<Integer> archiveInactiveSince(Instant since) {
            return Promise.of(0);
        }
    }

    /**
     * In-memory MemoryItemRepository for episodic memory.
     */
    private static class MapMemoryItemRepository implements MemoryItemRepository {
        private final ConcurrentHashMap<String, MemoryItem> store = new ConcurrentHashMap<>();

        @Override
        public Promise<MemoryItem> save(MemoryItem item) {
            store.put(item.getId(), item);
            return Promise.of(item);
        }

        @Override
        public Promise<MemoryItem> findById(String id) {
            return Promise.of(store.get(id));
        }

        @Override
        public Promise<List<MemoryItem>> findByQuery(MemoryQuery query) {
            List<MemoryItem> all = new ArrayList<>(store.values());
            if (query.getAgentId() != null) {
                all = all.stream()
                        .filter(i -> query.getAgentId().equals(i.getLabels().get("agentId"))
                                || (i instanceof EnhancedEpisode ep
                                    && query.getAgentId().equals(ep.getAgentId())))
                        .collect(Collectors.toList());
            }
            if (query.getTenantId() != null) {
                all = all.stream()
                        .filter(i -> query.getTenantId().equals(i.getTenantId()))
                        .collect(Collectors.toList());
            }
            return Promise.of(all);
        }

        @Override
        public Promise<Void> delete(String id) {
            store.remove(id);
            return Promise.complete();
        }

        @Override
        public Promise<Void> softDelete(String id) {
            return Promise.complete();
        }
    }

    private MapTaskStateRepository taskStateRepo;
    private MapMemoryItemRepository itemRepository;
    private PersistentMemoryPlane memoryPlane;
    private AgentHistoryController controller;

    @BeforeEach
    void setUp() {
        taskStateRepo  = new MapTaskStateRepository();
        itemRepository = new MapMemoryItemRepository();

        JdbcTaskStateStore taskStateStore = new JdbcTaskStateStore(taskStateRepo);
        WorkingMemoryConfig config = WorkingMemoryConfig.builder().maxEntries(100).build();
        memoryPlane = new PersistentMemoryPlane(itemRepository, taskStateStore, config);
        controller  = new AgentHistoryController(memoryPlane);
    }

    // =========================================================================
    // History endpoint
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/agents/{id}/history")
    class HistoryEndpoint {

        @Test
        @DisplayName("6.4.4 — 3 agent turns stored → history returns exactly 3 records")
        void threeAgentTurnsReturnedInHistory() {
            // Store 3 task state records for the same agent
            storeTask("task-001", "Implement login feature",   TaskLifecycleStatus.COMPLETED);
            storeTask("task-002", "Add role-based access",     TaskLifecycleStatus.IN_PROGRESS);
            storeTask("task-003", "Write integration tests",   TaskLifecycleStatus.COMPLETED);

            HttpResponse response = runPromise(() ->
                    controller.getHistory(devRequest("/api/v1/agents/" + AGENT_ID + "/history"), AGENT_ID));

            assertThat(response.getCode()).isEqualTo(200);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = parseJson(response, Map.class);
            assertThat(body.get("agentId")).isEqualTo(AGENT_ID);
            assertThat(body.get("tenantId")).isEqualTo(TENANT_ID);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
            assertThat(items).hasSize(3);
        }

        @Test
        @DisplayName("history is tenant-scoped: tasks for other tenant excluded")
        void otherTenantTasksExcluded() {
            storeTask("task-own",   "Own tenant task",  TaskLifecycleStatus.COMPLETED);
            storeTaskForTenant("task-other", "Other tenant task",
                    TaskLifecycleStatus.COMPLETED, "tenant-beta");

            HttpResponse response = runPromise(() ->
                    controller.getHistory(devRequest("/api/v1/agents/" + AGENT_ID + "/history"), AGENT_ID));

            assertThat(response.getCode()).isEqualTo(200);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = parseJson(response, Map.class);
            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) body.get("items");
            assertThat(items).hasSize(1);
        }

        @Test
        @DisplayName("unauthenticated request returns 401")
        void unauthenticatedReturns401() {
            HttpResponse response = runPromise(() ->
                    controller.getHistory(
                            HttpRequest.builder(HttpMethod.GET, "/api/v1/agents/" + AGENT_ID + "/history")
                                    .build(),
                            AGENT_ID));

            assertThat(response.getCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("history for unknown agent returns empty list")
        void unknownAgentReturnsEmpty() {
            HttpResponse response = runPromise(() ->
                    controller.getHistory(devRequest("/api/v1/agents/unknown-agent/history"), "unknown-agent"));

            assertThat(response.getCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = parseJson(response, Map.class);
            @SuppressWarnings("unchecked")
            List<?> items = (List<?>) body.get("items");
            assertThat(items).isEmpty();
        }
    }

    // =========================================================================
    // Rationale endpoint
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/agents/{id}/rationale/{turnId}")
    class RationaleEndpoint {

        @Test
        @DisplayName("rationale for known turn returns episodes")
        void rationaleForKnownTurnReturnsEpisodes() {
            String turnId = "turn-abc-001";
            storeEpisode("ep-001", turnId, "Analyzed requirements", "Found 3 gaps");
            storeEpisode("ep-002", turnId, "Prioritized gaps",      "Prioritized: gap-1 is P0");

            HttpResponse response = runPromise(() ->
                    controller.getRationale(
                            devRequest("/api/v1/agents/" + AGENT_ID + "/rationale/" + turnId),
                            AGENT_ID, turnId));

            assertThat(response.getCode()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = parseJson(response, Map.class);
            assertThat(body.get("turnId")).isEqualTo(turnId);
            @SuppressWarnings("unchecked")
            List<?> episodes = (List<?>) body.get("episodes");
            assertThat(episodes).hasSize(2);
        }

        @Test
        @DisplayName("rationale for unknown turn returns 404")
        void rationaleForUnknownTurnReturns404() {
            storeEpisode("ep-003", "turn-known", "Some input", "Some output");

            HttpResponse response = runPromise(() ->
                    controller.getRationale(
                            devRequest("/api/v1/agents/" + AGENT_ID + "/rationale/turn-unknown"),
                            AGENT_ID, "turn-unknown"));

            assertThat(response.getCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("unauthenticated rationale request returns 401")
        void unauthenticatedRationaleReturns401() {
            HttpResponse response = runPromise(() ->
                    controller.getRationale(
                            HttpRequest.builder(HttpMethod.GET,
                                    "/api/v1/agents/" + AGENT_ID + "/rationale/turn-x").build(),
                            AGENT_ID, "turn-x"));

            assertThat(response.getCode()).isEqualTo(401);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void storeTask(String taskId, String name, TaskLifecycleStatus status) {
        storeTaskForTenant(taskId, name, status, TENANT_ID);
    }

    private void storeTaskForTenant(String taskId, String name,
                                     TaskLifecycleStatus status, String tenantId) {
        TaskState task = TaskState.builder()
                .taskId(taskId)
                .name(name)
                .agentId(AGENT_ID)
                .tenantId(tenantId)
                .status(status)
                .build();
        runPromise(() -> memoryPlane.getTaskStateStore().createTask(task));
    }

    private void storeEpisode(String episodeId, String turnId, String input, String output) {
        EnhancedEpisode episode = EnhancedEpisode.builder()
                .id(episodeId)
                .agentId(AGENT_ID)
                .turnId(turnId)
                .tenantId(TENANT_ID)
                .input(input)
                .output(output)
                .build();
        runPromise(() -> memoryPlane.storeEpisode(episode));
    }

    /** Creates an HttpRequest with dev-mode X-Tenant-Id header (simulates authentication). */
    private HttpRequest devRequest(String url) {
        return HttpRequest.builder(HttpMethod.GET, url)
                .withHeader(HttpHeaders.of("X-Tenant-Id"), TENANT_ID)
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T parseJson(HttpResponse response, Class<T> type) {
        try {
            byte[] body = response.getBody().asArray();
            return new ObjectMapper().readValue(body, type);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse JSON response", e);
        }
    }
}
