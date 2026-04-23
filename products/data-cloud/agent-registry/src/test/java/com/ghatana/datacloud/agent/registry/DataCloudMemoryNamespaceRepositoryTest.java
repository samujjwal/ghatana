/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry;

import com.ghatana.agent.framework.memory.MemoryNamespace;
import com.ghatana.agent.framework.memory.MemoryScope;
import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DataCloudMemoryNamespaceRepository}.
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloudMemoryNamespaceRepository delegation and serialization
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudMemoryNamespaceRepository")
@ExtendWith(MockitoExtension.class) // GH-90000
class DataCloudMemoryNamespaceRepositoryTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-ns-test";
    private static final String AGENT_ID  = "agent-ns-001";
    private static final Instant NOW      = Instant.parse("2026-04-01T10:00:00Z");

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private EntityInterface mockEntity;

    private DataCloudMemoryNamespaceRepository repo;

    @BeforeEach
    void setUp() { // GH-90000
        repo = new DataCloudMemoryNamespaceRepository(dataCloud, TENANT_ID); // GH-90000
    }

    // ─────────────────── helpers ──────────────────────────────────────────────

    private MemoryNamespace episodicNs(String nsId) { // GH-90000
        return MemoryNamespace.of(nsId, TENANT_ID, AGENT_ID, MemoryScope.EPISODIC, "Episodic Log", NOW); // GH-90000
    }

    private Map<String, Object> namespaceDataMap(MemoryNamespace ns) { // GH-90000
        Map<String, Object> m = new java.util.HashMap<>(); // GH-90000
        m.put("namespaceId",      ns.namespaceId()); // GH-90000
        m.put("tenantId",         ns.tenantId()); // GH-90000
        m.put("agentId",          ns.agentId()); // GH-90000
        m.put("scope",            ns.scope().name()); // GH-90000
        m.put("label",            ns.label()); // GH-90000
        m.put("promotionEnabled", String.valueOf(ns.promotionEnabled())); // GH-90000
        m.put("createdAt",        ns.createdAt().toString()); // GH-90000
        m.put("updatedAt",        ns.updatedAt().toString()); // GH-90000
        return m;
    }

    // ─────────────────── constructor ──────────────────────────────────────────

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("rejects null dataCloud")
        void rejectsNullDataCloud() { // GH-90000
            assertThatThrownBy(() -> new DataCloudMemoryNamespaceRepository(null, TENANT_ID)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("rejects null tenantId")
        void rejectsNullTenantId() { // GH-90000
            assertThatThrownBy(() -> new DataCloudMemoryNamespaceRepository(dataCloud, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ─────────────────── save ─────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("delegates to createEntity and returns the original namespace")
        void delegatesToCreateEntity() { // GH-90000
            MemoryNamespace ns = episodicNs("ns-1");
            when(mockEntity.getId()).thenReturn(UUID.randomUUID()); // GH-90000
            when(dataCloud.createEntity(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(mockEntity)); // GH-90000

            MemoryNamespace saved = runPromise(() -> repo.save(ns)); // GH-90000

            assertThat(saved).isSameAs(ns); // GH-90000
            verify(dataCloud).createEntity(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any()); // GH-90000
        }

        @Test
        @DisplayName("serialises scope and label into data map")
        void serialisesScopeAndLabel() { // GH-90000
            MemoryNamespace ns = episodicNs("ns-42");
            when(mockEntity.getId()).thenReturn(UUID.randomUUID()); // GH-90000
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class); // GH-90000
            when(dataCloud.createEntity(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), captor.capture())) // GH-90000
                    .thenReturn(Promise.of(mockEntity)); // GH-90000

            runPromise(() -> repo.save(ns)); // GH-90000

            Map<String, Object> data = captor.getValue(); // GH-90000
            assertThat(data).containsEntry("namespaceId", "ns-42"); // GH-90000
            assertThat(data).containsEntry("scope", "EPISODIC"); // GH-90000
            assertThat(data).containsEntry("label", "Episodic Log"); // GH-90000
        }
    }

    // ─────────────────── findById ─────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns deserialized namespace when entity found")
        void returnsDeserializedNamespace() { // GH-90000
            MemoryNamespace ns = episodicNs("ns-1");
            when(mockEntity.getData()).thenReturn(namespaceDataMap(ns)); // GH-90000
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any(QuerySpecInterface.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(mockEntity))); // GH-90000

            Optional<MemoryNamespace> found = runPromise(() -> repo.findById("ns-1"));

            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().namespaceId()).isEqualTo("ns-1");
            assertThat(found.get().scope()).isEqualTo(MemoryScope.EPISODIC); // GH-90000
        }

        @Test
        @DisplayName("returns empty when no entity found")
        void returnsEmptyWhenNotFound() { // GH-90000
            when(dataCloud.queryEntities(any(), any(), any(QuerySpecInterface.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            Optional<MemoryNamespace> found = runPromise(() -> repo.findById("missing"));

            assertThat(found).isEmpty(); // GH-90000
        }
    }

    // ─────────────────── findByAgent ──────────────────────────────────────────

    @Nested
    @DisplayName("findByAgent")
    class FindByAgent {

        @Test
        @DisplayName("returns all namespaces for an agent")
        void returnsAllNamespaces() { // GH-90000
            MemoryNamespace ns = episodicNs("ns-1");
            EntityInterface e1 = mock(EntityInterface.class); // GH-90000
            when(e1.getData()).thenReturn(namespaceDataMap(ns)); // GH-90000
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(e1))); // GH-90000

            List<MemoryNamespace> results = runPromise(() -> repo.findByAgent(AGENT_ID, TENANT_ID)); // GH-90000

            assertThat(results).hasSize(1); // GH-90000
            assertThat(results.getFirst().agentId()).isEqualTo(AGENT_ID); // GH-90000
        }
    }

    // ─────────────────── findByAgentAndScope ──────────────────────────────────

    @Nested
    @DisplayName("findByAgentAndScope")
    class FindByAgentAndScope {

        @Test
        @DisplayName("returns matching namespace for agent and scope")
        void returnsMatchingNamespace() { // GH-90000
            MemoryNamespace ns = episodicNs("ns-1");
            when(mockEntity.getData()).thenReturn(namespaceDataMap(ns)); // GH-90000
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(mockEntity))); // GH-90000

            Optional<MemoryNamespace> result = runPromise( // GH-90000
                    () -> repo.findByAgentAndScope(AGENT_ID, MemoryScope.EPISODIC, TENANT_ID)); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().scope()).isEqualTo(MemoryScope.EPISODIC); // GH-90000
        }
    }

    // ─────────────────── delete ───────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("returns true when entity deleted")
        void returnsTrueOnDelete() { // GH-90000
            when(mockEntity.getId()).thenReturn(UUID.randomUUID()); // GH-90000
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any())) // GH-90000
                    .thenReturn(Promise.of(List.of(mockEntity))); // GH-90000
            when(dataCloud.deleteEntity(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any(UUID.class))) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            boolean deleted = runPromise(() -> repo.delete("ns-1", TENANT_ID)); // GH-90000

            assertThat(deleted).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns false when entity not found")
        void returnsFalseWhenNotFound() { // GH-90000
            when(dataCloud.queryEntities(any(), any(), any(QuerySpecInterface.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            boolean deleted = runPromise(() -> repo.delete("no-such", TENANT_ID)); // GH-90000

            assertThat(deleted).isFalse(); // GH-90000
        }
    }
}
