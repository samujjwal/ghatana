package com.ghatana.agent.memory.store.graphrag;

import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Tests for GraphRagSemanticStore using real ActiveJ components.
 * @doc.layer agent-memory
 * @doc.pattern Test
 * @doc.gaa.memory semantic
 */
class GraphRagSemanticStoreTest extends EventloopTestBase {

    private GraphRagSemanticStore store;

    @BeforeEach
    void setUp() {
        store = new GraphRagSemanticStore(Executors.newSingleThreadExecutor());
    }

    @Test
    void shouldStoreAndQuerySemanticFacts() {
        TenantId tenantId = TenantId.of("T-456");
        
        EnhancedFact fact = EnhancedFact.builder()
                .id("F-123")
                .agentId("A-999")
                .subject("User")
                .predicate("prefers")
                .object("Java over Python")
                .build();
                
        // Store
        EnhancedFact stored = runPromise(() -> store.storeFact(tenantId, fact));
        assertNotNull(stored);
        assertEquals("F-123", stored.getId());
        
        // Query
        MemoryQuery query = MemoryQuery.builder()
                .limit(5)
                .build();
        
        List<EnhancedFact> results = runPromise(() -> store.queryGraphRag(tenantId, "prefers Java", query));
        assertNotNull(results);
        assertEquals(0, results.size(), "Currently emulated as returning empty list");
    }
}
