/*
 * Copyright (c) 2025 Ghatana Technologies
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
@ExtendWith(MockitoExtension.class)
class AepAgentRegistryAdapterTest {

    @Mock
    private AgentRegistryService delegate;

    private AepAgentRegistryAdapter adapter;

    private static final TenantId TENANT = TenantId.of("test-tenant");

    private static AgentManifestProto manifest(String id) {
        return AgentManifestProto.newBuilder()
                .setMetadata(MetadataProto.newBuilder().setId(id).setName(id).build())
                .build();
    }

    @BeforeEach
    void setUp() {
        adapter = new AepAgentRegistryAdapter(delegate);
    }

    @Test
    @DisplayName("register delegates to AgentRegistryService")
    void register_delegatesToService() {
        AgentManifestProto m = manifest("agent-1");
        when(delegate.register(TENANT, m)).thenReturn(Promise.of(m));

        Promise<AgentManifestProto> result = adapter.register(TENANT, m);

        verify(delegate).register(TENANT, m);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("getById delegates to AgentRegistryService")
    void getById_delegatesToService() {
        AgentManifestProto m = manifest("agent-1");
        when(delegate.getById(TENANT, "agent-1")).thenReturn(Promise.of(m));

        Promise<AgentManifestProto> result = adapter.getById(TENANT, "agent-1");

        verify(delegate).getById(TENANT, "agent-1");
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("listAll delegates to AgentRegistryService")
    void listAll_delegatesToService() {
        List<AgentManifestProto> list = List.of(manifest("a1"), manifest("a2"));
        when(delegate.listAll(TENANT)).thenReturn(Promise.of(list));

        Promise<List<AgentManifestProto>> result = adapter.listAll(TENANT);

        verify(delegate).listAll(TENANT);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("findByCapabilities delegates to AgentRegistryService")
    void findByCapabilities_delegatesToService() {
        Set<String> caps = Set.of("code-gen");
        when(delegate.findByCapabilities(TENANT, caps)).thenReturn(Promise.of(List.of()));

        adapter.findByCapabilities(TENANT, caps);

        verify(delegate).findByCapabilities(TENANT, caps);
    }

    @Test
    @DisplayName("findByEventType delegates to AgentRegistryService")
    void findByEventType_delegatesToService() {
        when(delegate.findByEventType(TENANT, "code.review")).thenReturn(Promise.of(List.of()));

        adapter.findByEventType(TENANT, "code.review");

        verify(delegate).findByEventType(TENANT, "code.review");
    }

    @Test
    @DisplayName("update delegates to AgentRegistryService")
    void update_delegatesToService() {
        AgentManifestProto m = manifest("agent-1");
        when(delegate.update(TENANT, "agent-1", m)).thenReturn(Promise.of(m));

        adapter.update(TENANT, "agent-1", m);

        verify(delegate).update(TENANT, "agent-1", m);
    }

    @Test
    @DisplayName("delete delegates to AgentRegistryService with hardDelete flag")
    void delete_delegatesToService_withHardDeleteFlag() {
        when(delegate.delete(TENANT, "agent-1", true)).thenReturn(Promise.of(true));

        adapter.delete(TENANT, "agent-1", true);

        verify(delegate).delete(TENANT, "agent-1", true);
    }

    @Test
    @DisplayName("constructor rejects null delegate")
    void constructor_rejectsNullDelegate() {
        assertThatThrownBy(() -> new AepAgentRegistryAdapter(null))
                .isInstanceOf(NullPointerException.class);
    }
}
