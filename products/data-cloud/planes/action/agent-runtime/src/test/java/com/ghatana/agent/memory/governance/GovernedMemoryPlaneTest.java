/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

import com.ghatana.agent.memory.model.*;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.MemoryQuery;
import com.ghatana.agent.memory.store.ScoredMemoryItem;
import com.ghatana.data.governance.DataAccessBroker;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link GovernedMemoryPlane}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for TX-1 privacy gate on memory retrieval
 * @doc.layer agent-memory
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("GovernedMemoryPlane — TX-1 privacy gate")
class GovernedMemoryPlaneTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-1";
    private static final String SUBJECT_ID = "agent-x";

    @Mock
    private MemoryPlane delegate;

    @Mock
    private DataAccessBroker accessBroker;

    private GovernedMemoryPlane governed;

    @BeforeEach
    void setUp() { 
        governed = new GovernedMemoryPlane(delegate, accessBroker, TENANT_ID, SUBJECT_ID); 
        // Default: access is granted
        lenient().when(accessBroker.checkAccess(anyString(), anyString(), anyString(), anyString())) 
                .thenReturn(Promise.of(null)); 
    }

    // ── Read operations gate ────────────────────────────────────────────────

    @Test
    void searchSemantic_checksAccessBefore_delegating() { 
        when(delegate.searchSemantic(anyString(), isNull(), anyInt(), isNull(), isNull())) 
                .thenReturn(Promise.of(List.of())); 

        List<ScoredMemoryItem> result = runPromise(() -> 
                governed.searchSemantic("test query", null, 10, null, null)); 

        assertThat(result).isEmpty(); 
        verify(accessBroker).checkAccess(TENANT_ID, SUBJECT_ID, "agent.memory", "agent.context.hydration"); 
        verify(delegate).searchSemantic("test query", null, 10, null, null); 
    }

    @Test
    void searchSemantic_deniedAccess_propagatesFailure() { 
        when(accessBroker.checkAccess(anyString(), anyString(), anyString(), anyString())) 
                .thenReturn(Promise.ofException(new SecurityException("access denied")));

        assertThatThrownBy(() -> 
                runPromise(() -> governed.searchSemantic("q", null, 5, null, null))) 
                .isInstanceOf(SecurityException.class) 
                .hasMessageContaining("access denied");

        verifyNoInteractions(delegate); 
    }

    @Test
    void queryEpisodes_checksAccessBeforeDelegating() { 
        MemoryQuery query = mock(MemoryQuery.class); 
        when(delegate.queryEpisodes(any(MemoryQuery.class))) 
                .thenReturn(Promise.of(List.of())); 

        List<EnhancedEpisode> result = runPromise(() -> governed.queryEpisodes(query)); 

        assertThat(result).isEmpty(); 
        verify(accessBroker).checkAccess(TENANT_ID, SUBJECT_ID, "agent.memory", "agent.context.hydration"); 
        verify(delegate).queryEpisodes(query); 
    }

    @Test
    void queryFacts_checksAccessBeforeDelegating() { 
        MemoryQuery query = mock(MemoryQuery.class); 
        when(delegate.queryFacts(any(MemoryQuery.class))) 
                .thenReturn(Promise.of(List.of())); 

        List<EnhancedFact> result = runPromise(() -> governed.queryFacts(query)); 

        assertThat(result).isEmpty(); 
        verify(accessBroker).checkAccess(eq(TENANT_ID), eq(SUBJECT_ID), anyString(), anyString()); 
    }

    @Test
    void queryProcedures_checksAccessBeforeDelegating() { 
        MemoryQuery query = mock(MemoryQuery.class); 
        when(delegate.queryProcedures(any(MemoryQuery.class))) 
                .thenReturn(Promise.of(List.of())); 

        runPromise(() -> governed.queryProcedures(query)); 

        verify(accessBroker).checkAccess(eq(TENANT_ID), eq(SUBJECT_ID), anyString(), anyString()); 
        verify(delegate).queryProcedures(query); 
    }

    @Test
    void readItems_checksAccessBeforeDelegating() { 
        MemoryQuery query = mock(MemoryQuery.class); 
        when(delegate.readItems(any(MemoryQuery.class))) 
                .thenReturn(Promise.of(List.of())); 

        runPromise(() -> governed.readItems(query)); 

        verify(accessBroker).checkAccess(eq(TENANT_ID), eq(SUBJECT_ID), anyString(), anyString()); 
        verify(delegate).readItems(query); 
    }

    @Test
    void genericQuery_checksAccessBeforeDelegating() { 
        MemoryQuery query = mock(MemoryQuery.class); 
        when(delegate.query(any(MemoryQuery.class))) 
                .thenReturn(Promise.of(List.of())); 

        runPromise(() -> governed.query(query)); 

        verify(accessBroker).checkAccess(eq(TENANT_ID), eq(SUBJECT_ID), anyString(), anyString()); 
    }

    // ── Write operations gate learned memory policy but not read privacy ────

    @Test
    void storeEpisode_bypassesAccessCheck() { 
        EnhancedEpisode episode = mock(EnhancedEpisode.class); 
        when(delegate.storeEpisode(any())).thenReturn(Promise.of(episode)); 

        runPromise(() -> governed.storeEpisode(episode)); 

        verifyNoInteractions(accessBroker); 
        verify(delegate).storeEpisode(episode); 
    }

    @Test
    void storeFact_bypassesAccessCheck() { 
        EnhancedFact fact = mock(EnhancedFact.class); 
        when(fact.getLabels()).thenReturn(Map.of("validationState", "VALIDATED"));
        when(delegate.storeFact(any())).thenReturn(Promise.of(fact)); 

        runPromise(() -> governed.storeFact(fact)); 

        verifyNoInteractions(accessBroker); 
    }

    @Test
    void storeFact_rejectsUnvalidatedSemanticMemory() {
        EnhancedFact fact = mock(EnhancedFact.class);
        when(fact.getLabels()).thenReturn(Map.of());

        assertThatThrownBy(() -> runPromise(() -> governed.storeFact(fact)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("semantic memory writes require validationState=VALIDATED");

        verifyNoInteractions(delegate);
    }

    @Test
    void storeProcedure_requiresPromotionEvidence() {
        EnhancedProcedure procedure = mock(EnhancedProcedure.class);
        when(procedure.getLabels()).thenReturn(Map.of("promotionState", "DRAFT"));

        assertThatThrownBy(() -> runPromise(() -> governed.storeProcedure(procedure)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("procedural memory writes require active promotion evidence");

        verifyNoInteractions(delegate);
    }

    @Test
    void storeProcedure_allowsActivePromotedProcedure() {
        EnhancedProcedure procedure = mock(EnhancedProcedure.class);
        when(procedure.getLabels()).thenReturn(Map.of(
                "promotionState", "ACTIVE",
                "promotionEvidenceId", "evidence-1"));
        when(delegate.storeProcedure(procedure)).thenReturn(Promise.of(procedure));

        runPromise(() -> governed.storeProcedure(procedure));

        verify(delegate).storeProcedure(procedure);
        verifyNoInteractions(accessBroker);
    }

    // ── Constructor guard ─────────────────────────────────────────────────

    @Test
    void constructor_rejectsBlankTenantId() { 
        assertThatThrownBy(() -> new GovernedMemoryPlane(delegate, accessBroker, "", SUBJECT_ID)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("tenantId");
    }

    @Test
    void constructor_rejectsBlankSubjectId() { 
        assertThatThrownBy(() -> new GovernedMemoryPlane(delegate, accessBroker, TENANT_ID, "")) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("subjectId");
    }
}
