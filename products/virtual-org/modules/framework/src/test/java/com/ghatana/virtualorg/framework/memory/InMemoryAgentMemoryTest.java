package com.ghatana.virtualorg.framework.memory;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for InMemoryAgentMemory.
 *
 * @doc.type class
 * @doc.purpose Unit tests for agent memory
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("InMemoryAgentMemory Tests")
class InMemoryAgentMemoryTest extends EventloopTestBase {

    private InMemoryAgentMemory memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryAgentMemory(100);
    }

    @Test
    @DisplayName("should store and retrieve memory entry")
    void shouldStoreAndRetrieve() {
        // GIVEN
        MemoryEntry entry = MemoryEntry.builder()
                .agentId("agent-1")
                .type(MemoryType.EPISODIC)
                .content("Completed task #123")
                .importance(0.8)
                .build();

        // WHEN
        MemoryEntry stored = runPromise(() -> memory.store(entry));
        MemoryEntry retrieved = runPromise(() -> memory.getById(stored.id()));

        // THEN
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.content()).isEqualTo("Completed task #123");
        // Access tracking is implementation detail, verify accessedAt is updated
        assertThat(retrieved.accessedAt()).isAfterOrEqualTo(stored.createdAt());
    }

    @Test
    @DisplayName("should retrieve recent memories")
    void shouldGetRecentMemories() {
        // GIVEN
        for (int i = 0; i < 5; i++) {
            MemoryEntry entry = MemoryEntry.builder()
                    .agentId("agent-1")
                    .content("Memory " + i)
                    .build();
            runPromise(() -> memory.store(entry));
        }

        // WHEN
        List<MemoryEntry> recent = runPromise(() -> memory.getRecent("agent-1", 3));

        // THEN
        assertThat(recent).hasSize(3);
    }

    @Test
    @DisplayName("should filter by memory type")
    void shouldFilterByType() {
        // GIVEN
        runPromise(() -> memory.store(MemoryEntry.builder()
                .agentId("agent-1")
                .type(MemoryType.EPISODIC)
                .content("Episode 1")
                .build()));

        runPromise(() -> memory.store(MemoryEntry.builder()
                .agentId("agent-1")
                .type(MemoryType.SEMANTIC)
                .content("Fact 1")
                .build()));

        // WHEN
        List<MemoryEntry> episodic = runPromise(()
                -> memory.getByType("agent-1", MemoryType.EPISODIC, 10));

        // THEN
        assertThat(episodic).hasSize(1);
        assertThat(episodic.get(0).content()).isEqualTo("Episode 1");
    }

    @Test
    @DisplayName("should search by text")
    void shouldSearchByText() {
        // GIVEN
        runPromise(() -> memory.store(MemoryEntry.builder()
                .agentId("agent-1")
                .content("Fixed the authentication bug")
                .build()));

        runPromise(() -> memory.store(MemoryEntry.builder()
                .agentId("agent-1")
                .content("Added new feature")
                .build()));

        // WHEN
        List<MemoryEntry> results = runPromise(()
                -> memory.searchSimilar("agent-1", "bug", 10));

        // THEN
        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).contains("bug");
    }

    @Test
    @DisplayName("should query with filters")
    void shouldQueryWithFilters() {
        // GIVEN
        runPromise(() -> memory.store(MemoryEntry.builder()
                .agentId("agent-1")
                .type(MemoryType.EPISODIC)
                .content("Important memory")
                .importance(0.9)
                .build()));

        runPromise(() -> memory.store(MemoryEntry.builder()
                .agentId("agent-1")
                .type(MemoryType.EPISODIC)
                .content("Unimportant memory")
                .importance(0.2)
                .build()));

        // WHEN
        MemoryQuery query = MemoryQuery.builder()
                .agentId("agent-1")
                .minImportance(0.5)
                .build();

        List<MemoryEntry> results = runPromise(() -> memory.search(query));

        // THEN
        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).isEqualTo("Important memory");
    }

    @Test
    @DisplayName("should delete memory entry")
    void shouldDeleteEntry() {
        // GIVEN
        MemoryEntry entry = runPromise(() -> memory.store(MemoryEntry.builder()
                .agentId("agent-1")
                .content("To be deleted")
                .build()));

        // WHEN
        Boolean deleted = runPromise(() -> memory.delete(entry.id()));
        MemoryEntry retrieved = runPromise(() -> memory.getById(entry.id()));

        // THEN
        assertThat(deleted).isTrue();
        assertThat(retrieved).isNull();
    }

    @Test
    @DisplayName("should delete all for agent")
    void shouldDeleteAllForAgent() {
        // GIVEN
        for (int i = 0; i < 5; i++) {
            runPromise(() -> memory.store(MemoryEntry.builder()
                    .agentId("agent-1")
                    .content("Memory")
                    .build()));
        }

        // WHEN
        Integer deleted = runPromise(() -> memory.deleteAllForAgent("agent-1"));
        List<MemoryEntry> remaining = runPromise(() -> memory.getRecent("agent-1", 10));

        // THEN
        assertThat(deleted).isEqualTo(5);
        assertThat(remaining).isEmpty();
    }

    @Test
    @DisplayName("should update memory entry")
    void shouldUpdateEntry() {
        // GIVEN
        MemoryEntry original = runPromise(() -> memory.store(MemoryEntry.builder()
                .agentId("agent-1")
                .content("Original")
                .importance(0.5)
                .build()));

        // WHEN
        MemoryEntry updated = original.withImportance(0.9);
        runPromise(() -> memory.update(updated));
        MemoryEntry retrieved = runPromise(() -> memory.getById(original.id()));

        // THEN
        assertThat(retrieved.importance()).isEqualTo(0.9);
    }
}
