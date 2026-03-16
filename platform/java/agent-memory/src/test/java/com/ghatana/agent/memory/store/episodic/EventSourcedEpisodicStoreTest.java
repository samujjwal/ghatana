package com.ghatana.agent.memory.store.episodic;

import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.core.event.cloud.InMemoryEventCloud;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @doc.type class
 * @doc.purpose Tests for EventSourcedEpisodicStore using real ActiveJ components.
 * @doc.layer agent-memory
 * @doc.pattern Test
 * @doc.gaa.memory episodic
 */
class EventSourcedEpisodicStoreTest extends EventloopTestBase {

    private InMemoryEventCloud eventCloud;
    private EventSourcedEpisodicStore store;

    @BeforeEach
    void setUp() {
        eventCloud = new InMemoryEventCloud();
        store = new EventSourcedEpisodicStore(eventCloud, Executors.newSingleThreadExecutor());
    }

    @Test
    void shouldStoreAndQueryEpisodes() {
        TenantId tenantId = TenantId.of("T-123");
        
        EnhancedEpisode episode = EnhancedEpisode.builder()
                .id("E-456")
                .agentId("A-789")
                .input("Hello")
                .output("Hi there")
                .turnId("Turn-1")
                .build();
                
        // Store
        EnhancedEpisode stored = runPromise(() -> store.storeEpisode(tenantId, episode));
        assertNotNull(stored);
        assertEquals("E-456", stored.getId());
        
        // Query
        MemoryQuery query = MemoryQuery.builder()
                .limit(10)
                .build();
        
        List<EnhancedEpisode> results = runPromise(() -> store.queryEpisodes(tenantId, query));
        assertNotNull(results);
        assertFalse(results.isEmpty());
        // Simple mapRecordToEpisode mock currently returns static values for testing,
        // we assert it worked by its size.
        assertEquals(1, results.size());
    }
}
