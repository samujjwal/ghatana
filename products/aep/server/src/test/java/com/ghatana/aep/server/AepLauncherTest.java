package com.ghatana.aep.server;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.datacloud.agent.registry.DataCloudAgentRegistry;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.inject.Injector;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for launcher production bootstrap behavior.
 *
 * @doc.type class
 * @doc.purpose Verify launcher bootstrap fails fast on production governance misconfiguration
 * @doc.layer launcher
 * @doc.pattern Test
 */
@DisplayName("AepLauncher [GH-90000]")
class AepLauncherTest extends EventloopTestBase {

    @Test
    @DisplayName("production bootstrap fails when database configuration is missing [GH-90000]")
    void productionBootstrapFailsWithoutDatabase() { // GH-90000
        assertThatThrownBy(() -> AepLauncher.createGovernanceInjector(Map.of( // GH-90000
            "AEP_PROFILE", "production",
            "AEP_JWT_SECRET", "test-secret")))
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("AEP_DB_URL [GH-90000]");
    }

    @Test
    @DisplayName("non-production bootstrap allows missing database configuration [GH-90000]")
    void nonProductionBootstrapAllowsMissingDatabase() { // GH-90000
        Injector injector = AepLauncher.createGovernanceInjector(Map.of( // GH-90000
            "AEP_PROFILE", "development"));

        assertThat(injector).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("production gRPC registry bootstrap fails when Data Cloud connection is missing [GH-90000]")
    void productionGrpcRegistryBootstrapFailsWithoutDataCloud() { // GH-90000
        assertThatThrownBy(() -> AepLauncher.createGrpcRegistryRuntime(Map.of( // GH-90000
            "AEP_PROFILE", "production")))
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("DATACLOUD_URL [GH-90000]");
    }

    @Test
    @DisplayName("non-production gRPC registry bootstrap uses durable Data Cloud registry [GH-90000]")
    void nonProductionGrpcRegistryBootstrapUsesDurableRegistry() { // GH-90000
        AepLauncher.GrpcRegistryRuntime runtime = AepLauncher.createGrpcRegistryRuntime(Map.of( // GH-90000
            "AEP_PROFILE", "development"));

        try {
            assertThat(runtime.agentRegistry()).isInstanceOf(DataCloudAgentRegistry.class); // GH-90000
            assertThat(runtime.registryDataCloud()).isNotNull(); // GH-90000
        } finally {
            runtime.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("production gRPC registry bootstrap accepts explicit Data Cloud URL [GH-90000]")
    void productionGrpcRegistryBootstrapAcceptsExplicitDataCloudUrl() { // GH-90000
        AepLauncher.GrpcRegistryRuntime runtime = AepLauncher.createGrpcRegistryRuntime(Map.of( // GH-90000
            "AEP_PROFILE", "production",
            "DATACLOUD_URL", "http://localhost:8082"));

        try {
            assertThat(runtime.agentRegistry()).isInstanceOf(DataCloudAgentRegistry.class); // GH-90000
        } finally {
            runtime.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("durable registry metadata survives registry recreation on the same Data Cloud backend [GH-90000]")
    void registryMetadataSurvivesRegistryRecreation() { // GH-90000
        AepLauncher.GrpcRegistryRuntime runtime = AepLauncher.createGrpcRegistryRuntime(Map.of( // GH-90000
            "AEP_PROFILE", "development"));

        DataCloudAgentRegistry firstRegistry = new DataCloudAgentRegistry( // GH-90000
            runtime.registryDataCloud(), // GH-90000
            "platform"
        );
        DataCloudAgentRegistry secondRegistry = new DataCloudAgentRegistry( // GH-90000
            runtime.registryDataCloud(), // GH-90000
            "platform"
        );

        try {
            TestAgent agent = new TestAgent("durable-agent-1 [GH-90000]");
            AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("durable-agent-1 [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .version("1.0.0 [GH-90000]")
                .timeout(Duration.ofSeconds(5)) // GH-90000
                .build(); // GH-90000

            runPromise(() -> firstRegistry.register(agent, config)); // GH-90000

            Map<String, Object> stats = runPromise(secondRegistry::getStats); // GH-90000
            Optional<TypedAgent<String, String>> rehydrated = runPromise(() -> secondRegistry.resolve("durable-agent-1 [GH-90000]"));

            assertThat(stats.get("persistedAgents [GH-90000]")).isEqualTo(1L);
            assertThat(stats.get("registeredAgents [GH-90000]")).isEqualTo(0);
            assertThat(rehydrated).isEmpty(); // GH-90000
        } finally {
            secondRegistry.close(); // GH-90000
            firstRegistry.close(); // GH-90000
            runtime.close(); // GH-90000
        }
    }

    private static final class TestAgent implements TypedAgent<String, String> {
        private final AgentDescriptor descriptor;

        private TestAgent(String agentId) { // GH-90000
            this.descriptor = AgentDescriptor.builder() // GH-90000
                .agentId(agentId) // GH-90000
                .name("Test " + agentId) // GH-90000
                .description("Launcher durability test agent [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .version("1.0.0 [GH-90000]")
                .capabilities(Set.of("registry-test [GH-90000]"))
                .build(); // GH-90000
        }

        @Override
        public AgentDescriptor descriptor() { // GH-90000
            return descriptor;
        }

        @Override
        public Promise<Void> initialize(AgentConfig config) { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<Void> shutdown() { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public Promise<HealthStatus> healthCheck() { // GH-90000
            return Promise.of(HealthStatus.healthy("ok [GH-90000]"));
        }

        @Override
        public Promise<AgentResult<String>> process(AgentContext ctx, String input) { // GH-90000
            return Promise.of(AgentResult.success(input, descriptor.getAgentId(), Duration.ZERO)); // GH-90000
        }
    }
}