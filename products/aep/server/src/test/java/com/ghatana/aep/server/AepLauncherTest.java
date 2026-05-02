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
@DisplayName("AepLauncher")
class AepLauncherTest extends EventloopTestBase {

    @Test
    @DisplayName("production bootstrap fails when database configuration is missing")
    void productionBootstrapFailsWithoutDatabase() { 
        assertThatThrownBy(() -> AepLauncher.createGovernanceInjector(Map.of( 
            "AEP_PROFILE", "production",
            "AEP_JWT_SECRET", "test-secret")))
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("AEP_DB_URL");
    }

    @Test
    @DisplayName("non-production bootstrap allows missing database configuration")
    void nonProductionBootstrapAllowsMissingDatabase() { 
        Injector injector = AepLauncher.createGovernanceInjector(Map.of( 
            "AEP_PROFILE", "development"));

        assertThat(injector).isNotNull(); 
    }

    @Test
    @DisplayName("production gRPC registry bootstrap fails when Data Cloud connection is missing")
    void productionGrpcRegistryBootstrapFailsWithoutDataCloud() { 
        assertThatThrownBy(() -> AepLauncher.createGrpcRegistryRuntime(Map.of( 
            "AEP_PROFILE", "production")))
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("DATACLOUD_URL");
    }

    @Test
    @DisplayName("non-production gRPC registry bootstrap uses durable Data Cloud registry")
    void nonProductionGrpcRegistryBootstrapUsesDurableRegistry() { 
        AepLauncher.GrpcRegistryRuntime runtime = AepLauncher.createGrpcRegistryRuntime(Map.of( 
            "AEP_PROFILE", "development"));

        try {
            assertThat(runtime.agentRegistry()).isInstanceOf(DataCloudAgentRegistry.class); 
            assertThat(runtime.registryDataCloud()).isNotNull(); 
        } finally {
            runtime.close(); 
        }
    }

    @Test
    @DisplayName("production gRPC registry bootstrap accepts explicit Data Cloud URL")
    void productionGrpcRegistryBootstrapAcceptsExplicitDataCloudUrl() { 
        AepLauncher.GrpcRegistryRuntime runtime = AepLauncher.createGrpcRegistryRuntime(Map.of( 
            "AEP_PROFILE", "production",
            "DATACLOUD_URL", "http://localhost:8082"));

        try {
            assertThat(runtime.agentRegistry()).isInstanceOf(DataCloudAgentRegistry.class); 
        } finally {
            runtime.close(); 
        }
    }

    @Test
    @DisplayName("durable registry metadata survives registry recreation on the same Data Cloud backend")
    void registryMetadataSurvivesRegistryRecreation() { 
        AepLauncher.GrpcRegistryRuntime runtime = AepLauncher.createGrpcRegistryRuntime(Map.of( 
            "AEP_PROFILE", "development"));

        DataCloudAgentRegistry firstRegistry = new DataCloudAgentRegistry( 
            runtime.registryDataCloud(), 
            "platform"
        );
        DataCloudAgentRegistry secondRegistry = new DataCloudAgentRegistry( 
            runtime.registryDataCloud(), 
            "platform"
        );

        try {
            TestAgent agent = new TestAgent("durable-agent-1");
            AgentConfig config = AgentConfig.builder() 
                .agentId("durable-agent-1")
                .type(AgentType.DETERMINISTIC) 
                .version("1.0.0")
                .timeout(Duration.ofSeconds(5)) 
                .build(); 

            runPromise(() -> firstRegistry.register(agent, config)); 

            Map<String, Object> stats = runPromise(secondRegistry::getStats); 
            Optional<TypedAgent<String, String>> rehydrated = runPromise(() -> secondRegistry.resolve("durable-agent-1"));

            assertThat(stats.get("persistedAgents")).isEqualTo(1L);
            assertThat(stats.get("registeredAgents")).isEqualTo(0);
            assertThat(rehydrated).isEmpty(); 
        } finally {
            secondRegistry.close(); 
            firstRegistry.close(); 
            runtime.close(); 
        }
    }

    private static final class TestAgent implements TypedAgent<String, String> {
        private final AgentDescriptor descriptor;

        private TestAgent(String agentId) { 
            this.descriptor = AgentDescriptor.builder() 
                .agentId(agentId) 
                .name("Test " + agentId) 
                .description("Launcher durability test agent")
                .type(AgentType.DETERMINISTIC) 
                .version("1.0.0")
                .capabilities(Set.of("registry-test"))
                .build(); 
        }

        @Override
        public AgentDescriptor descriptor() { 
            return descriptor;
        }

        @Override
        public Promise<Void> initialize(AgentConfig config) { 
            return Promise.complete(); 
        }

        @Override
        public Promise<Void> shutdown() { 
            return Promise.complete(); 
        }

        @Override
        public Promise<HealthStatus> healthCheck() { 
            return Promise.of(HealthStatus.healthy("ok"));
        }

        @Override
        public Promise<AgentResult<String>> process(AgentContext ctx, String input) { 
            return Promise.of(AgentResult.success(input, descriptor.getAgentId(), Duration.ZERO)); 
        }
    }
}