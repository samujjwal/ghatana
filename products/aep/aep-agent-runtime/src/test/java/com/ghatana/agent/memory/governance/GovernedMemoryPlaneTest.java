/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

import com.ghatana.agent.memory.model.*;
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
@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        governed = new GovernedMemoryPlane(delegate, accessBroker, TENANT_ID, SUBJECT_ID); // GH-90000
        // Default: access is granted
        lenient().when(accessBroker.checkAccess(anyString(), anyString(), anyString(), anyString())) // GH-90000
                .thenReturn(Promise.of(null)); // GH-90000
    }

    // ── Read operations gate ────────────────────────────────────────────────

    @Test
    void searchSemantic_checksAccessBefore_delegating() { // GH-90000
        when(delegate.searchSemantic(anyString(), isNull(), anyInt(), isNull(), isNull())) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

        List<ScoredMemoryItem> result = runPromise(() -> // GH-90000
                governed.searchSemantic("test query", null, 10, null, null)); // GH-90000

        assertThat(result).isEmpty(); // GH-90000
        verify(accessBroker).checkAccess(TENANT_ID, SUBJECT_ID, "agent.memory", "agent.context.hydration"); // GH-90000
        verify(delegate).searchSemantic("test query", null, 10, null, null); // GH-90000
    }

    @Test
    void searchSemantic_deniedAccess_propagatesFailure() { // GH-90000
        when(accessBroker.checkAccess(anyString(), anyString(), anyString(), anyString())) // GH-90000
                .thenReturn(Promise.ofException(new SecurityException("access denied")));

        assertThatThrownBy(() -> // GH-90000
                runPromise(() -> governed.searchSemantic("q", null, 5, null, null))) // GH-90000
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("access denied");

        verifyNoInteractions(delegate); // GH-90000
    }

    @Test
    void queryEpisodes_checksAccessBeforeDelegating() { // GH-90000
        MemoryQuery query = mock(MemoryQuery.class); // GH-90000
        when(delegate.queryEpisodes(any(MemoryQuery.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

        List<EnhancedEpisode> result = runPromise(() -> governed.queryEpisodes(query)); // GH-90000

        assertThat(result).isEmpty(); // GH-90000
        verify(accessBroker).checkAccess(TENANT_ID, SUBJECT_ID, "agent.memory", "agent.context.hydration"); // GH-90000
        verify(delegate).queryEpisodes(query); // GH-90000
    }

    @Test
    void queryFacts_checksAccessBeforeDelegating() { // GH-90000
        MemoryQuery query = mock(MemoryQuery.class); // GH-90000
        when(delegate.queryFacts(any(MemoryQuery.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

        List<EnhancedFact> result = runPromise(() -> governed.queryFacts(query)); // GH-90000

        assertThat(result).isEmpty(); // GH-90000
        verify(accessBroker).checkAccess(eq(TENANT_ID), eq(SUBJECT_ID), anyString(), anyString()); // GH-90000
    }

    @Test
    void queryProcedures_checksAccessBeforeDelegating() { // GH-90000
        MemoryQuery query = mock(MemoryQuery.class); // GH-90000
        when(delegate.queryProcedures(any(MemoryQuery.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

        runPromise(() -> governed.queryProcedures(query)); // GH-90000

        verify(accessBroker).checkAccess(eq(TENANT_ID), eq(SUBJECT_ID), anyString(), anyString()); // GH-90000
        verify(delegate).queryProcedures(query); // GH-90000
    }

    @Test
    void readItems_checksAccessBeforeDelegating() { // GH-90000
        MemoryQuery query = mock(MemoryQuery.class); // GH-90000
        when(delegate.readItems(any(MemoryQuery.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

        runPromise(() -> governed.readItems(query)); // GH-90000

        verify(accessBroker).checkAccess(eq(TENANT_ID), eq(SUBJECT_ID), anyString(), anyString()); // GH-90000
        verify(delegate).readItems(query); // GH-90000
    }

    @Test
    void genericQuery_checksAccessBeforeDelegating() { // GH-90000
        MemoryQuery query = mock(MemoryQuery.class); // GH-90000
        when(delegate.query(any(MemoryQuery.class))) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

        runPromise(() -> governed.query(query)); // GH-90000

        verify(accessBroker).checkAccess(eq(TENANT_ID), eq(SUBJECT_ID), anyString(), anyString()); // GH-90000
    }

    // ── Write operations bypass gate ──────────────────────────────────────

    @Test
    void storeEpisode_bypassesAccessCheck() { // GH-90000
        EnhancedEpisode episode = mock(EnhancedEpisode.class); // GH-90000
        when(delegate.storeEpisode(any())).thenReturn(Promise.of(episode)); // GH-90000

        runPromise(() -> governed.storeEpisode(episode)); // GH-90000

        verifyNoInteractions(accessBroker); // GH-90000
        verify(delegate).storeEpisode(episode); // GH-90000
    }

    @Test
    void storeFact_bypassesAccessCheck() { // GH-90000
        EnhancedFact fact = mock(EnhancedFact.class); // GH-90000
        when(delegate.storeFact(any())).thenReturn(Promise.of(fact)); // GH-90000

        runPromise(() -> governed.storeFact(fact)); // GH-90000

        verifyNoInteractions(accessBroker); // GH-90000
    }

    // ── Constructor guard ─────────────────────────────────────────────────

    @Test
    void constructor_rejectsBlankTenantId() { // GH-90000
        assertThatThrownBy(() -> new GovernedMemoryPlane(delegate, accessBroker, "", SUBJECT_ID)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("tenantId");
    }

    @Test
    void constructor_rejectsBlankSubjectId() { // GH-90000
        assertThatThrownBy(() -> new GovernedMemoryPlane(delegate, accessBroker, TENANT_ID, "")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("subjectId");
    }
}
