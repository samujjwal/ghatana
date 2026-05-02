/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@ExtendWith(MockitoExtension.class) 
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
    void setUp() { 
        repo = new DataCloudMemoryNamespaceRepository(dataCloud, TENANT_ID); 
    }

    // ─────────────────── helpers ──────────────────────────────────────────────

    private MemoryNamespace episodicNs(String nsId) { 
        return MemoryNamespace.of(nsId, TENANT_ID, AGENT_ID, MemoryScope.EPISODIC, "Episodic Log", NOW); 
    }

    private Map<String, Object> namespaceDataMap(MemoryNamespace ns) { 
        Map<String, Object> m = new java.util.HashMap<>(); 
        m.put("namespaceId",      ns.namespaceId()); 
        m.put("tenantId",         ns.tenantId()); 
        m.put("agentId",          ns.agentId()); 
        m.put("scope",            ns.scope().name()); 
        m.put("label",            ns.label()); 
        m.put("promotionEnabled", String.valueOf(ns.promotionEnabled())); 
        m.put("createdAt",        ns.createdAt().toString()); 
        m.put("updatedAt",        ns.updatedAt().toString()); 
        return m;
    }

    // ─────────────────── constructor ──────────────────────────────────────────

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("rejects null dataCloud")
        void rejectsNullDataCloud() { 
            assertThatThrownBy(() -> new DataCloudMemoryNamespaceRepository(null, TENANT_ID)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("rejects null tenantId")
        void rejectsNullTenantId() { 
            assertThatThrownBy(() -> new DataCloudMemoryNamespaceRepository(dataCloud, null)) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }

    // ─────────────────── save ─────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("delegates to createEntity and returns the original namespace")
        void delegatesToCreateEntity() { 
            MemoryNamespace ns = episodicNs("ns-1");
            when(mockEntity.getId()).thenReturn(UUID.randomUUID()); 
            when(dataCloud.createEntity(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any())) 
                    .thenReturn(Promise.of(mockEntity)); 

            MemoryNamespace saved = runPromise(() -> repo.save(ns)); 

            assertThat(saved).isSameAs(ns); 
            verify(dataCloud).createEntity(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any()); 
        }

        @Test
        @DisplayName("serialises scope and label into data map")
        void serialisesScopeAndLabel() { 
            MemoryNamespace ns = episodicNs("ns-42");
            when(mockEntity.getId()).thenReturn(UUID.randomUUID()); 
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class); 
            when(dataCloud.createEntity(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), captor.capture())) 
                    .thenReturn(Promise.of(mockEntity)); 

            runPromise(() -> repo.save(ns)); 

            Map<String, Object> data = captor.getValue(); 
            assertThat(data).containsEntry("namespaceId", "ns-42"); 
            assertThat(data).containsEntry("scope", "EPISODIC"); 
            assertThat(data).containsEntry("label", "Episodic Log"); 
        }
    }

    // ─────────────────── findById ─────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns deserialized namespace when entity found")
        void returnsDeserializedNamespace() { 
            MemoryNamespace ns = episodicNs("ns-1");
            when(mockEntity.getData()).thenReturn(namespaceDataMap(ns)); 
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any(QuerySpecInterface.class))) 
                    .thenReturn(Promise.of(List.of(mockEntity))); 

            Optional<MemoryNamespace> found = runPromise(() -> repo.findById("ns-1"));

            assertThat(found).isPresent(); 
            assertThat(found.get().namespaceId()).isEqualTo("ns-1");
            assertThat(found.get().scope()).isEqualTo(MemoryScope.EPISODIC); 
        }

        @Test
        @DisplayName("returns empty when no entity found")
        void returnsEmptyWhenNotFound() { 
            when(dataCloud.queryEntities(any(), any(), any(QuerySpecInterface.class))) 
                    .thenReturn(Promise.of(List.of())); 

            Optional<MemoryNamespace> found = runPromise(() -> repo.findById("missing"));

            assertThat(found).isEmpty(); 
        }
    }

    // ─────────────────── findByAgent ──────────────────────────────────────────

    @Nested
    @DisplayName("findByAgent")
    class FindByAgent {

        @Test
        @DisplayName("returns all namespaces for an agent")
        void returnsAllNamespaces() { 
            MemoryNamespace ns = episodicNs("ns-1");
            EntityInterface e1 = mock(EntityInterface.class); 
            when(e1.getData()).thenReturn(namespaceDataMap(ns)); 
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any())) 
                    .thenReturn(Promise.of(List.of(e1))); 

            List<MemoryNamespace> results = runPromise(() -> repo.findByAgent(AGENT_ID, TENANT_ID)); 

            assertThat(results).hasSize(1); 
            assertThat(results.getFirst().agentId()).isEqualTo(AGENT_ID); 
        }
    }

    // ─────────────────── findByAgentAndScope ──────────────────────────────────

    @Nested
    @DisplayName("findByAgentAndScope")
    class FindByAgentAndScope {

        @Test
        @DisplayName("returns matching namespace for agent and scope")
        void returnsMatchingNamespace() { 
            MemoryNamespace ns = episodicNs("ns-1");
            when(mockEntity.getData()).thenReturn(namespaceDataMap(ns)); 
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any())) 
                    .thenReturn(Promise.of(List.of(mockEntity))); 

            Optional<MemoryNamespace> result = runPromise( 
                    () -> repo.findByAgentAndScope(AGENT_ID, MemoryScope.EPISODIC, TENANT_ID)); 

            assertThat(result).isPresent(); 
            assertThat(result.get().scope()).isEqualTo(MemoryScope.EPISODIC); 
        }
    }

    // ─────────────────── delete ───────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("returns true when entity deleted")
        void returnsTrueOnDelete() { 
            when(mockEntity.getId()).thenReturn(UUID.randomUUID()); 
            when(dataCloud.queryEntities(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any())) 
                    .thenReturn(Promise.of(List.of(mockEntity))); 
            when(dataCloud.deleteEntity(eq(TENANT_ID), eq(DataCloudMemoryNamespaceRepository.COLLECTION), any(UUID.class))) 
                    .thenReturn(Promise.of(null)); 

            boolean deleted = runPromise(() -> repo.delete("ns-1", TENANT_ID)); 

            assertThat(deleted).isTrue(); 
        }

        @Test
        @DisplayName("returns false when entity not found")
        void returnsFalseWhenNotFound() { 
            when(dataCloud.queryEntities(any(), any(), any(QuerySpecInterface.class))) 
                    .thenReturn(Promise.of(List.of())); 

            boolean deleted = runPromise(() -> repo.delete("no-such", TENANT_ID)); 

            assertThat(deleted).isFalse(); 
        }
    }
}
