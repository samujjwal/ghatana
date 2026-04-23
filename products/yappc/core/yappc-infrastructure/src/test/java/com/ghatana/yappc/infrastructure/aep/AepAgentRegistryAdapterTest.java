/*
 * Copyright (c) 2025 Ghatana Technologies // GH-90000
 * YAPPC Infrastructure — AEP Adapter Tests
 */
package com.ghatana.yappc.infrastructure.aep;

import com.ghatana.agent.registry.service.AgentRegistryService;
import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.contracts.agent.v1.MetadataProto;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that {@link AepAgentRegistryAdapter} correctly delegates all
 * {@link com.ghatana.yappc.agent.spi.AgentRegistryPort} calls to the
 * underlying {@link AgentRegistryService}.
 *
 * <p>These tests enforce the adapter-seam contract: YAPPC logic never calls
 * AEP directly; the adapter is the single integration point.
 */
@DisplayName("AepAgentRegistryAdapter — delegation contract")
@ExtendWith(MockitoExtension.class) // GH-90000
class AepAgentRegistryAdapterTest {

    @Mock
    private AgentRegistryService delegate;

    private AepAgentRegistryAdapter adapter;

    private static final TenantId TENANT = TenantId.of("test-tenant");

    private static AgentManifestProto manifest(String id) { // GH-90000
        return AgentManifestProto.newBuilder() // GH-90000
                .setMetadata(MetadataProto.newBuilder().setId(id).setName(id).build()) // GH-90000
                .build(); // GH-90000
    }

    @BeforeEach
    void setUp() { // GH-90000
        adapter = new AepAgentRegistryAdapter(delegate); // GH-90000
    }

    @Test
    @DisplayName("register delegates to AgentRegistryService")
    void register_delegatesToService() { // GH-90000
        AgentManifestProto m = manifest("agent-1");
        when(delegate.register(TENANT, m)).thenReturn(Promise.of(m)); // GH-90000

        Promise<AgentManifestProto> result = adapter.register(TENANT, m); // GH-90000

        verify(delegate).register(TENANT, m); // GH-90000
        assertThat(result).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("getById delegates to AgentRegistryService")
    void getById_delegatesToService() { // GH-90000
        AgentManifestProto m = manifest("agent-1");
        when(delegate.getById(TENANT, "agent-1")).thenReturn(Promise.of(m)); // GH-90000

        Promise<AgentManifestProto> result = adapter.getById(TENANT, "agent-1"); // GH-90000

        verify(delegate).getById(TENANT, "agent-1"); // GH-90000
        assertThat(result).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("listAll delegates to AgentRegistryService")
    void listAll_delegatesToService() { // GH-90000
        List<AgentManifestProto> list = List.of(manifest("a1"), manifest("a2"));
        when(delegate.listAll(TENANT)).thenReturn(Promise.of(list)); // GH-90000

        Promise<List<AgentManifestProto>> result = adapter.listAll(TENANT); // GH-90000

        verify(delegate).listAll(TENANT); // GH-90000
        assertThat(result).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("findByCapabilities delegates to AgentRegistryService")
    void findByCapabilities_delegatesToService() { // GH-90000
        Set<String> caps = Set.of("code-gen");
        when(delegate.findByCapabilities(TENANT, caps)).thenReturn(Promise.of(List.of())); // GH-90000

        adapter.findByCapabilities(TENANT, caps); // GH-90000

        verify(delegate).findByCapabilities(TENANT, caps); // GH-90000
    }

    @Test
    @DisplayName("findByEventType delegates to AgentRegistryService")
    void findByEventType_delegatesToService() { // GH-90000
        when(delegate.findByEventType(TENANT, "code.review")).thenReturn(Promise.of(List.of())); // GH-90000

        adapter.findByEventType(TENANT, "code.review"); // GH-90000

        verify(delegate).findByEventType(TENANT, "code.review"); // GH-90000
    }

    @Test
    @DisplayName("update delegates to AgentRegistryService")
    void update_delegatesToService() { // GH-90000
        AgentManifestProto m = manifest("agent-1");
        when(delegate.update(TENANT, "agent-1", m)).thenReturn(Promise.of(m)); // GH-90000

        adapter.update(TENANT, "agent-1", m); // GH-90000

        verify(delegate).update(TENANT, "agent-1", m); // GH-90000
    }

    @Test
    @DisplayName("delete delegates to AgentRegistryService with hardDelete flag")
    void delete_delegatesToService_withHardDeleteFlag() { // GH-90000
        when(delegate.delete(TENANT, "agent-1", true)).thenReturn(Promise.of(true)); // GH-90000

        adapter.delete(TENANT, "agent-1", true); // GH-90000

        verify(delegate).delete(TENANT, "agent-1", true); // GH-90000
    }

    @Test
    @DisplayName("constructor rejects null delegate")
    void constructor_rejectsNullDelegate() { // GH-90000
        assertThatThrownBy(() -> new AepAgentRegistryAdapter(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }
}
