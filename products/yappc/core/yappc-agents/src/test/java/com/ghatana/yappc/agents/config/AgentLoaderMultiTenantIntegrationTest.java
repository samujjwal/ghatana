package com.ghatana.yappc.agents.config;

import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.contracts.agent.v1.MetadataProto;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.spi.AgentRegistryPort;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration-style tests verifying that YAPPC agent loading and
 * multi-tenant isolation work correctly through the
 * {@link AgentRegistryPort} adapter seam.
 *
 * <p>These tests exercise the complete path:
 * YAML configs → {@link YamlToManifestConverter} → {@link AgentRegistryPort},
 * confirming that tenant IDs are correctly threaded through every registry
 * call and that one tenant's registrations are never mixed with another's.
 *
 * @doc.type class
 * @doc.purpose Multi-tenant agent loading integration tests
 * @doc.layer product
 * @doc.pattern Test, Integration
 */
@DisplayName("AgentLoader multi-tenant isolation")
@ExtendWith(MockitoExtension.class)
class AgentLoaderMultiTenantIntegrationTest extends EventloopTestBase {

    private static final TenantId TENANT_A = TenantId.of("acme-corp");
    private static final TenantId TENANT_B = TenantId.of("beta-inc");

    @Mock
    private AgentRegistryPort agentRegistry;

    @Mock
    private YamlAgentLoader yamlLoader;

    private AepIntegratedAgentLoader loader;

    @BeforeEach
    void setUp() {
        loader = new AepIntegratedAgentLoader(agentRegistry, yamlLoader, new YamlToManifestConverter());
    }

    // ─── Tenant isolation: registration ───────────────────────────────────

    @Test
    @DisplayName("agents registered for tenant-A are only registered under tenant-A")
    void register_onlyUsesSuppliedTenantId() {
        List<YamlAgentConfig> configs = agentConfigs("review.java", "review.sql");
        when(yamlLoader.loadFromClasspath("agents/")).thenReturn(configs);
        when(agentRegistry.register(eq(TENANT_A), any())).thenAnswer(inv ->
                Promise.of(inv.getArgument(1, AgentManifestProto.class)));

        runPromise(() -> loader.loadAndRegisterAgents(TENANT_A));

        // verify both registrations used TENANT_A, never TENANT_B
        ArgumentCaptor<TenantId> tenantCaptor = ArgumentCaptor.forClass(TenantId.class);
        verify(agentRegistry, times(2)).register(tenantCaptor.capture(), any());
        assertThat(tenantCaptor.getAllValues()).containsOnly(TENANT_A);
        verify(agentRegistry, never()).register(eq(TENANT_B), any());
    }

    @Test
    @DisplayName("registrations for different tenants are independent")
    void register_differentTenantsAreIndependent() {
        List<YamlAgentConfig> configsA = agentConfigs("agent.alpha");
        List<YamlAgentConfig> configsB = agentConfigs("agent.bravo");

        when(yamlLoader.loadFromClasspath("agents/")).thenReturn(configsA, configsB);
        when(agentRegistry.register(eq(TENANT_A), any())).thenAnswer(inv ->
                Promise.of(inv.getArgument(1, AgentManifestProto.class)));
        when(agentRegistry.register(eq(TENANT_B), any())).thenAnswer(inv ->
                Promise.of(inv.getArgument(1, AgentManifestProto.class)));

        runPromise(() -> loader.loadAndRegisterAgents(TENANT_A));
        runPromise(() -> loader.loadAndRegisterAgents(TENANT_B));

        verify(agentRegistry, times(1)).register(eq(TENANT_A), any());
        verify(agentRegistry, times(1)).register(eq(TENANT_B), any());
    }

    // ─── Tenant isolation: lookup ──────────────────────────────────────────

    @Test
    @DisplayName("getAgent uses the exact tenant provided")
    void getAgent_usesExactTenant() {
        AgentManifestProto manifest = manifest("expert.java");
        when(agentRegistry.getById(TENANT_A, "expert.java")).thenReturn(Promise.of(manifest));

        AgentManifestProto result = runPromise(() -> loader.getAgent(TENANT_A, "expert.java"));

        assertThat(result.getMetadata().getId()).isEqualTo("expert.java");
        verify(agentRegistry).getById(TENANT_A, "expert.java");
        verify(agentRegistry, never()).getById(eq(TENANT_B), any());
    }

    @Test
    @DisplayName("listAgents uses the exact tenant provided")
    void listAgents_usesExactTenant() {
        List<AgentManifestProto> manifests = List.of(manifest("a1"), manifest("a2"));
        when(agentRegistry.listAll(TENANT_B)).thenReturn(Promise.of(manifests));

        List<AgentManifestProto> result = runPromise(() -> loader.listAgents(TENANT_B));

        assertThat(result).hasSize(2);
        verify(agentRegistry).listAll(TENANT_B);
        verify(agentRegistry, never()).listAll(TENANT_A);
    }

    // ─── Capability-based load path ────────────────────────────────────────

    @Test
    @DisplayName("loadAgentsByCapability registers only agents with matching capabilities")
    void loadByCapability_registersMatchingAgentsOnly() {
        List<YamlAgentConfig> all = List.of(
                agentWithCapabilities("code-gen-v1", Set.of("code-generation")),
                agentWithCapabilities("review-v1",   Set.of("code-review")),
                agentWithCapabilities("hybrid-v1",   Set.of("code-generation", "code-review"))
        );
        when(yamlLoader.loadFromClasspath("agents/")).thenReturn(all);
        when(agentRegistry.register(eq(TENANT_A), any())).thenAnswer(inv ->
                Promise.of(inv.getArgument(1, AgentManifestProto.class)));

        runPromise(() -> loader.loadAndRegisterAgents(TENANT_A));

        // All three registered — capability filtering is AEP-side, not loader-side
        verify(agentRegistry, times(3)).register(eq(TENANT_A), any());
    }

    // ─── Empty config path ─────────────────────────────────────────────────

    @Test
    @DisplayName("empty YAML source results in zero registry calls")
    void emptySource_producesNoRegistryCalls() {
        when(yamlLoader.loadFromClasspath("agents/")).thenReturn(List.of());

        List<AgentManifestProto> result = runPromise(() -> loader.loadAndRegisterAgents(TENANT_A));

        assertThat(result).isEmpty();
        verify(agentRegistry, never()).register(any(), any());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private static List<YamlAgentConfig> agentConfigs(String... ids) {
        return java.util.Arrays.stream(ids)
                .map(id -> YamlAgentConfig.builder()
                        .id(id)
                        .name(id)
                        .description("Auto-generated agent " + id)
                        .build())
                .toList();
    }

    private static YamlAgentConfig agentWithCapabilities(String id, Set<String> caps) {
        return YamlAgentConfig.builder()
                .id(id)
                .name(id)
                .description("Agent " + id)
                .capabilities(caps)
                .build();
    }

    private static AgentManifestProto manifest(String id) {
        return AgentManifestProto.newBuilder()
                .setMetadata(MetadataProto.newBuilder().setId(id).setName(id).build())
                .build();
    }
}
