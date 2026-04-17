package com.ghatana.aep.server;

import com.ghatana.datacloud.agent.registry.DataCloudAgentRegistry;
import io.activej.inject.Injector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
class AepLauncherTest {

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
}