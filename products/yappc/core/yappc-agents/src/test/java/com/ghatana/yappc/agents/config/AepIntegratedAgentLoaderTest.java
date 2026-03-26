package com.ghatana.yappc.agents.config;

import com.ghatana.agent.registry.service.AgentRegistryService;
import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.contracts.agent.v1.MetadataProto;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AepIntegratedAgentLoader} using EventloopTestBase
 * for proper ActiveJ Promise execution.
 *
 * @doc.type class
 * @doc.purpose Verify agent loading and AEP registry integration
 * @doc.layer product
 * @doc.pattern Test, EventSourced
 * @doc.gaa.lifecycle perceive
 */
@DisplayName("AepIntegratedAgentLoader Tests")
@ExtendWith(MockitoExtension.class)
class AepIntegratedAgentLoaderTest extends EventloopTestBase {

    private static final TenantId TENANT = TenantId.of("test-tenant");

    @Mock
    private AgentRegistryService aepRegistry;

    @Mock
    private YamlAgentLoader yamlLoader;

    private YamlToManifestConverter converter;
    private AepIntegratedAgentLoader loader;

    @BeforeEach
    void setUp() {
        converter = new YamlToManifestConverter();
        loader = new AepIntegratedAgentLoader(aepRegistry, yamlLoader, converter);
    }

    @Test
    @DisplayName("should load and register agents from classpath")
    void shouldLoadAndRegisterAgents() {
        // GIVEN
        List<YamlAgentConfig> yamlConfigs = List.of(
            YamlAgentConfig.builder()
                .id("expert.java")
                .name("Java Expert")
                .description("Generates Java code")
                .capabilities(Set.of("code-generation"))
                .build(),
            YamlAgentConfig.builder()
                .id("expert.sql")
                .name("SQL Expert")
                .description("Generates SQL queries")
                .capabilities(Set.of("query-generation"))
                .build()
        );
        when(yamlLoader.loadFromClasspath("agents/")).thenReturn(yamlConfigs);
        when(aepRegistry.register(eq(TENANT), any())).thenAnswer(invocation ->
            Promise.of(invocation.getArgument(1, AgentManifestProto.class))
        );

        // WHEN
        List<AgentManifestProto> registered = runPromise(() ->
            loader.loadAndRegisterAgents(TENANT)
        );

        // THEN
        assertThat(registered).hasSize(2);
        assertThat(registered)
            .extracting(m -> m.getMetadata().getId())
            .containsExactlyInAnyOrder("expert.java", "expert.sql");
        verify(aepRegistry, times(2)).register(eq(TENANT), any());
    }

    @Test
    @DisplayName("should load and register agents from custom resource path")
    void shouldLoadAndRegisterAgentsFromCustomPath() {
        // GIVEN
        String customPath = "agents/custom/";
        List<YamlAgentConfig> yamlConfigs = List.of(
            YamlAgentConfig.builder()
                .id("custom.agent")
                .name("Custom Agent")
                .description("Custom processing agent")
                .build()
        );
        when(yamlLoader.loadFromClasspath(customPath)).thenReturn(yamlConfigs);
        when(aepRegistry.register(eq(TENANT), any())).thenAnswer(invocation ->
            Promise.of(invocation.getArgument(1, AgentManifestProto.class))
        );

        // WHEN
        List<AgentManifestProto> registered = runPromise(() ->
            loader.loadAndRegisterAgents(TENANT, customPath)
        );

        // THEN
        assertThat(registered).hasSize(1);
        assertThat(registered.get(0).getMetadata().getId()).isEqualTo("custom.agent");
        verify(yamlLoader).loadFromClasspath(customPath);
    }

    @Test
    @DisplayName("should return empty list when no configs found")
    void shouldReturnEmptyListWhenNoConfigsFound() {
        // GIVEN
        when(yamlLoader.loadFromClasspath("agents/")).thenReturn(List.of());

        // WHEN
        List<AgentManifestProto> registered = runPromise(() ->
            loader.loadAndRegisterAgents(TENANT)
        );

        // THEN
        assertThat(registered).isEmpty();
        verify(aepRegistry, times(0)).register(any(), any());
    }

    @Test
    @DisplayName("should get agent by ID from AEP registry")
    void shouldGetAgentById() {
        // GIVEN
        AgentManifestProto expected = AgentManifestProto.newBuilder()
            .setMetadata(MetadataProto.newBuilder()
                .setId("expert.java")
                .setName("Java Expert")
                .setDescription("Generates Java code")
                .build())
            .build();
        when(aepRegistry.getById(TENANT, "expert.java")).thenReturn(Promise.of(expected));

        // WHEN
        AgentManifestProto result = runPromise(() -> loader.getAgent(TENANT, "expert.java"));

        // THEN
        assertThat(result.getMetadata().getId()).isEqualTo("expert.java");
        verify(aepRegistry).getById(TENANT, "expert.java");
    }

    @Test
    @DisplayName("should list all agents for a tenant")
    void shouldListAllAgents() {
        // GIVEN
        List<AgentManifestProto> manifests = List.of(
            AgentManifestProto.newBuilder()
                .setMetadata(MetadataProto.newBuilder().setId("agent.one").build())
                .build(),
            AgentManifestProto.newBuilder()
                .setMetadata(MetadataProto.newBuilder().setId("agent.two").build())
                .build()
        );
        when(aepRegistry.listAll(TENANT)).thenReturn(Promise.of(manifests));

        // WHEN
        List<AgentManifestProto> result = runPromise(() -> loader.listAgents(TENANT));

        // THEN
        assertThat(result).hasSize(2);
        verify(aepRegistry).listAll(TENANT);
    }
}
