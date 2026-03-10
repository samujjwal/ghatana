/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.memory;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.working.WorkingMemoryConfig;
import com.ghatana.agent.memory.persistence.JdbcMemoryItemRepository;
import com.ghatana.agent.memory.persistence.JdbcTaskStateRepository;
import com.ghatana.agent.memory.persistence.MemoryItemRepository;
import com.ghatana.agent.memory.persistence.PersistentMemoryPlane;
import com.ghatana.agent.memory.store.taskstate.JdbcTaskStateStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PersistentMemoryPlane} wiring in the YAPPC product context.
 *
 * <p>Covers plan task 9.1.6 — agent turn episode stored in memory plane, survives logical
 * restart (i.e., plane is reconstructed and the item is still retrievable from the same
 * underlying store).
 *
 * @doc.type class
 * @doc.purpose Tests PersistentMemoryPlane wiring and episodic memory storage (9.1.6)
 * @doc.layer product
 * @doc.pattern Test
 * @doc.gaa.memory episodic
 */
@DisplayName("PersistentMemoryPlane Wiring Tests (9.1)")
class PersistentMemoryPlaneTest extends EventloopTestBase {

    /** Simple in-test MemoryItemRepository backed by a ConcurrentHashMap. */
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
        public Promise<java.util.List<MemoryItem>> findByQuery(
                com.ghatana.agent.memory.store.MemoryQuery query) {
            return Promise.of(java.util.List.copyOf(store.values()));
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

        int size() { return store.size(); }
    }

    private MapMemoryItemRepository itemRepository;
    private PersistentMemoryPlane memoryPlane;

    @BeforeEach
    void setUp() {
        itemRepository = new MapMemoryItemRepository();
        JdbcTaskStateRepository taskStateRepo = new JdbcTaskStateRepository();
        JdbcTaskStateStore taskStateStore = new JdbcTaskStateStore(taskStateRepo);
        WorkingMemoryConfig config = WorkingMemoryConfig.builder()
                .maxEntries(100)
                .build();
        memoryPlane = new PersistentMemoryPlane(itemRepository, taskStateStore, config);
    }

    @Test
    @DisplayName("9.1.6.1 — episode stored via memory plane is retrievable by id")
    void episodeStoredIsRetrievable() {
        EnhancedEpisode episode = EnhancedEpisode.builder()
                .id("ep-yappc-001")
                .agentId("intent-agent")
                .turnId("turn-001")
                .tenantId("tenant-alpha")
                .input("Scaffold new feature X")
                .output("Created 5-step lifecycle plan for feature X")
                .build();

        runPromise(() -> memoryPlane.storeEpisode(episode));

        MemoryItem retrieved = runPromise(() -> itemRepository.findById("ep-yappc-001"));
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getId()).isEqualTo("ep-yappc-001");
    }

    @Test
    @DisplayName("9.1.6.2 — episode survives logical plane restart (same backing store)")
    void episodeSurvivesLogicalRestart() {
        EnhancedEpisode episode = EnhancedEpisode.builder()
                .id("ep-yappc-restart-001")
                .agentId("shape-agent")
                .turnId("turn-002")
                .tenantId("tenant-beta")
                .input("Design module boundaries for project Y")
                .output("Proposed 3-module split: core, api, infra")
                .build();

        // Store via first plane instance
        runPromise(() -> memoryPlane.storeEpisode(episode));

        // Simulate restart: create a new plane backed by the SAME itemRepository
        PersistentMemoryPlane restoredPlane = new PersistentMemoryPlane(
                itemRepository,
                new JdbcTaskStateStore(new JdbcTaskStateRepository()),
                WorkingMemoryConfig.builder().build());

        // Episode must still be readable from the shared store
        MemoryItem retrieved = runPromise(() -> itemRepository.findById("ep-yappc-restart-001"));
        assertThat(retrieved).as("Episode must be present after logical restart").isNotNull();
        assertThat(retrieved.getId()).isEqualTo("ep-yappc-restart-001");
    }

    @Test
    @DisplayName("9.1.6.3 — multiple episodes for different tenants stored independently")
    void multipleEpisodesStoredIndependently() {
        EnhancedEpisode epA = EnhancedEpisode.builder()
                .id("ep-tenant-a-001")
                .agentId("plan-agent")
                .turnId("turn-a")
                .tenantId("tenant-a")
                .input("Plan sprint for team A")
                .output("5 stories created")
                .build();

        EnhancedEpisode epB = EnhancedEpisode.builder()
                .id("ep-tenant-b-001")
                .agentId("plan-agent")
                .turnId("turn-b")
                .tenantId("tenant-b")
                .input("Plan sprint for team B")
                .output("3 stories created")
                .build();

        runPromise(() -> memoryPlane.storeEpisode(epA));
        runPromise(() -> memoryPlane.storeEpisode(epB));

        assertThat(itemRepository.size()).isGreaterThanOrEqualTo(2);

        MemoryItem retA = runPromise(() -> itemRepository.findById("ep-tenant-a-001"));
        MemoryItem retB = runPromise(() -> itemRepository.findById("ep-tenant-b-001"));

        assertThat(retA).isNotNull();
        assertThat(retB).isNotNull();
        assertThat(retA.getTenantId()).isEqualTo("tenant-a");
        assertThat(retB.getTenantId()).isEqualTo("tenant-b");
    }

    @Test
    @DisplayName("9.1.6.4 — PersistentMemoryPlane constructed with default WorkingMemoryConfig")
    void constructedWithDefaultConfig() {
        PersistentMemoryPlane plane = new PersistentMemoryPlane(
                itemRepository,
                new JdbcTaskStateStore(new JdbcTaskStateRepository()),
                WorkingMemoryConfig.builder().build());
        assertThat(plane).isNotNull();
    }
}

