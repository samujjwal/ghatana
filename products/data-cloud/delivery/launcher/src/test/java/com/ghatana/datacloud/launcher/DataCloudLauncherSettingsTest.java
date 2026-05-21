package com.ghatana.datacloud.launcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.datacloud.DataCloud;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @doc.type class
 * @doc.purpose Verifies launcher settings resolution from flags and environment
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudLauncherSettings - launcher configuration resolution")
class DataCloudLauncherSettingsTest {

    @Test
    @DisplayName("environment overrides client config flags")
    void environmentOverridesClientConfigFlags() { 
        DataCloud.DataCloudConfig config =
                DataCloudLauncherSettings.parseClientConfig( 
                        new String[] {"--instance-id", "cli-instance", "--max-connections", "15"},
                        Map.of( 
                                "DATACLOUD_INSTANCE_ID", "env-instance",
                                "DATACLOUD_MAX_CONNECTIONS", "42"));

        assertThat(config.instanceId()).isEqualTo("env-instance");
        assertThat(config.maxConnectionsPerTenant()).isEqualTo(42); 
    }

    @Test
    @DisplayName("http server starts from cli flags")
    void httpServerStartsFromCliFlags() { 
        boolean startHttp =
                DataCloudLauncherSettings.shouldStartHttpServer( 
                        new String[] {"--http"},
                        Map.of()); 

        assertThat(startHttp).isTrue(); 
    }

    @Test
    @DisplayName("http server stays disabled when env flag is false")
    void httpServerRespectsFalseEnvFlag() { 
        boolean startHttp =
                DataCloudLauncherSettings.shouldStartHttpServer( 
                        new String[0],
                        Map.of("DATACLOUD_HTTP_ENABLED", "false")); 

        assertThat(startHttp).isFalse(); 
    }

    @Test
    @DisplayName("grpc server starts when port is configured")
    void grpcServerStartsWhenPortConfigured() { 
        boolean startGrpc =
                DataCloudLauncherSettings.shouldStartGrpcServer( 
                        new String[0],
                        Map.of("DATACLOUD_GRPC_PORT", "9090")); 

        assertThat(startGrpc).isTrue(); 
    }

    @Test
    @DisplayName("grpc server stays disabled when enable flag is false and no port is configured")
    void grpcServerRespectsFalseEnvFlag() { 
        boolean startGrpc =
                DataCloudLauncherSettings.shouldStartGrpcServer( 
                        new String[0],
                        Map.of("DATACLOUD_GRPC_ENABLED", "false")); 

        assertThat(startGrpc).isFalse(); 
    }

    @Test
    @DisplayName("http server starts when enable flag is true")
    void httpServerStartsWhenEnabledInEnvironment() { 
        boolean startHttp =
                DataCloudLauncherSettings.shouldStartHttpServer( 
                        new String[0],
                        Map.of("DATACLOUD_HTTP_ENABLED", "true")); 

        assertThat(startHttp).isTrue(); 
    }

    @Test
    @DisplayName("launcher detects when no transport is enabled")
    void launcherDetectsNoEnabledTransport() { 
        boolean hasEnabledTransport =
                DataCloudLauncherSettings.hasEnabledTransport(new String[0], Map.of()); 

        assertThat(hasEnabledTransport).isFalse(); 
    }

    @Test
    @DisplayName("http port falls back to default when missing")
    void httpPortFallsBackToDefault() { 
        int port = DataCloudLauncherSettings.resolveHttpPort(Map.of()); 

        assertThat(port).isEqualTo(8082); 
    }

    @Test
    @DisplayName("production profile resolves as non-embedded mode")
    void productionProfileResolvesAsNonEmbeddedMode() {
        DataCloud.DataCloudConfig.DataCloudProfile profile =
            DataCloudLauncherSettings.resolveProfile(
                new String[0],
                Map.of("DATACLOUD_PROFILE", "production"));

        assertThat(profile).isEqualTo(DataCloud.DataCloudConfig.DataCloudProfile.PRODUCTION);
        assertThat(DataCloudLauncherSettings.isEmbeddedProfile(profile)).isFalse();
    }

    @Test
    @DisplayName("local profile resolves as embedded mode")
    void localProfileResolvesAsEmbeddedMode() {
        DataCloud.DataCloudConfig.DataCloudProfile profile =
            DataCloudLauncherSettings.resolveProfile(
                new String[0],
                Map.of("DATACLOUD_PROFILE", "local"));

        assertThat(profile).isEqualTo(DataCloud.DataCloudConfig.DataCloudProfile.LOCAL);
        assertThat(DataCloudLauncherSettings.isEmbeddedProfile(profile)).isTrue();
    }

    @Test
    @DisplayName("http port is resolved from environment")
    void httpPortResolvesFromEnvironment() { 
        int port = DataCloudLauncherSettings.resolveHttpPort( 
                Map.of("DATACLOUD_HTTP_PORT", "8181")); 

        assertThat(port).isEqualTo(8181); 
    }

    @Test
    @DisplayName("rate limit requests falls back to default 200 when env var absent")
    void rateLimitRequestsDefaultsTo200() { 
        int requests = DataCloudLauncherSettings.resolveRateLimitRequests(Map.of()); 

        assertThat(requests).isEqualTo(200); 
    }

    @Test
    @DisplayName("rate limit requests is resolved from DATACLOUD_RATE_LIMIT_REQUESTS env var")
    void rateLimitRequestsResolvesFromEnvironment() { 
        int requests = DataCloudLauncherSettings.resolveRateLimitRequests( 
                Map.of("DATACLOUD_RATE_LIMIT_REQUESTS", "500")); 

        assertThat(requests).isEqualTo(500); 
    }

    @Test
    @DisplayName("rate limit window seconds falls back to default 60 when env var absent")
    void rateLimitWindowSecondsDefaultsTo60() { 
        long windowSec = DataCloudLauncherSettings.resolveRateLimitWindowSeconds(Map.of()); 

        assertThat(windowSec).isEqualTo(60L); 
    }

    @Test
    @DisplayName("rate limit window seconds resolves from DATACLOUD_RATE_LIMIT_WINDOW_SECONDS env var")
    void rateLimitWindowSecondsResolvesFromEnvironment() { 
        long windowSec = DataCloudLauncherSettings.resolveRateLimitWindowSeconds( 
                Map.of("DATACLOUD_RATE_LIMIT_WINDOW_SECONDS", "120")); 

        assertThat(windowSec).isEqualTo(120L); 
    }

    @Test
    @DisplayName("rate limit ignores blank env var and falls back to defaults")
    void rateLimitIgnoresBlankEnvVar() { 
        int requests = DataCloudLauncherSettings.resolveRateLimitRequests( 
                Map.of("DATACLOUD_RATE_LIMIT_REQUESTS", "  ")); 
        long windowSec = DataCloudLauncherSettings.resolveRateLimitWindowSeconds( 
                Map.of("DATACLOUD_RATE_LIMIT_WINDOW_SECONDS", "  ")); 

        assertThat(requests).isEqualTo(200); 
        assertThat(windowSec).isEqualTo(60L); 
    }

    @Test
    @DisplayName("client config resolves sovereign profile and data dir from env")
    void clientConfigResolvesSovereignProfileAndDataDirFromEnvironment() { 
        DataCloud.DataCloudConfig config = DataCloudLauncherSettings.parseClientConfig( 
                new String[0],
                Map.of( 
                        "DATACLOUD_PROFILE", "sovereign",
                        "DATACLOUD_SOVEREIGN_DATA_DIR", "/tmp/dc-sovereign"));

        assertThat(config.profile()).isEqualTo(DataCloud.DataCloudConfig.DataCloudProfile.SOVEREIGN); 
        assertThat(config.customConfig()).containsEntry("sovereign.dataDir", "/tmp/dc-sovereign"); 
        assertThat(DataCloudLauncherSettings.isEmbeddedProfile(config.profile())).isTrue(); 
    }

    @Test
    @DisplayName("client config resolves sovereign profile from cli flag")
    void clientConfigResolvesSovereignProfileFromCliFlag() { 
        DataCloud.DataCloudConfig config = DataCloudLauncherSettings.parseClientConfig( 
                new String[] {"--profile=sovereign", "--sovereign-data-dir=/var/lib/datacloud"},
                Map.of()); 

        assertThat(config.profile()).isEqualTo(DataCloud.DataCloudConfig.DataCloudProfile.SOVEREIGN); 
        assertThat(config.customConfig()).containsEntry("sovereign.dataDir", "/var/lib/datacloud"); 
    }

    @Test
    @DisplayName("storage compaction settings fall back to sovereign defaults")
    void storageCompactionSettingsFallBackToDefaults() { 
        assertThat(DataCloudLauncherSettings.resolveStorageCompactionThreshold(Map.of())).isEqualTo(25); 
        assertThat(DataCloudLauncherSettings.resolveStorageCompactionIntervalSeconds(Map.of())).isEqualTo(300L); 
    }

    // =========================================================================
    // DC-P0-001: Fail-closed profile resolution in container/Kubernetes context
    // =========================================================================

    @Test
    @DisplayName("resolveProfile throws when DATACLOUD_PROFILE absent in container environment")
    void resolveProfileFailsWhenMissingInContainerEnvironment() {
        assertThatThrownBy(() ->
            DataCloudLauncherSettings.resolveProfile(
                new String[0],
                Map.of("DATACLOUD_CONTAINER", "true"))
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("DATACLOUD_PROFILE must be set explicitly in container/Kubernetes deployments");
    }

    @Test
    @DisplayName("resolveProfile throws when DATACLOUD_PROFILE absent in Kubernetes environment")
    void resolveProfileFailsWhenMissingInKubernetesEnvironment() {
        assertThatThrownBy(() ->
            DataCloudLauncherSettings.resolveProfile(
                new String[0],
                Map.of("KUBERNETES_SERVICE_HOST", "10.0.0.1"))
        ).isInstanceOf(IllegalStateException.class)
         .hasMessageContaining("DATACLOUD_PROFILE must be set explicitly in container/Kubernetes deployments");
    }

    @Test
    @DisplayName("resolveProfile resolves production when DATACLOUD_PROFILE=production in container")
    void resolveProfileReturnsProductionWhenSetInContainerEnvironment() {
        DataCloud.DataCloudConfig.DataCloudProfile profile =
            DataCloudLauncherSettings.resolveProfile(
                new String[0],
                Map.of(
                    "DATACLOUD_CONTAINER", "true",
                    "DATACLOUD_PROFILE", "production"));

        assertThat(profile).isEqualTo(DataCloud.DataCloudConfig.DataCloudProfile.PRODUCTION);
    }

    @Test
    @DisplayName("resolveProfile resolves staging when DATACLOUD_PROFILE=staging in Kubernetes")
    void resolveProfileReturnsStagingWhenSetInKubernetesEnvironment() {
        DataCloud.DataCloudConfig.DataCloudProfile profile =
            DataCloudLauncherSettings.resolveProfile(
                new String[0],
                Map.of(
                    "KUBERNETES_SERVICE_HOST", "10.96.0.1",
                    "DATACLOUD_PROFILE", "staging"));

        assertThat(profile).isEqualTo(DataCloud.DataCloudConfig.DataCloudProfile.STAGING);
    }

    @Test
    @DisplayName("resolveProfile defaults to LOCAL when DATACLOUD_PROFILE absent outside any container")
    void resolveProfileDefaultsToLocalOnDeveloperWorkstation() {
        DataCloud.DataCloudConfig.DataCloudProfile profile =
            DataCloudLauncherSettings.resolveProfile(new String[0], Map.of());

        assertThat(profile).isEqualTo(DataCloud.DataCloudConfig.DataCloudProfile.LOCAL);
    }

    @Test
    @DisplayName("isContainerEnvironment returns true when DATACLOUD_CONTAINER=true")
    void isContainerEnvironmentDetectsDockerContainerFlag() {
        assertThat(DataCloudLauncherSettings.isContainerEnvironment(
            Map.of("DATACLOUD_CONTAINER", "true"))).isTrue();
    }

    @Test
    @DisplayName("isContainerEnvironment returns true when KUBERNETES_SERVICE_HOST is present")
    void isContainerEnvironmentDetectsKubernetesServiceHost() {
        assertThat(DataCloudLauncherSettings.isContainerEnvironment(
            Map.of("KUBERNETES_SERVICE_HOST", "10.96.0.1"))).isTrue();
    }

    @Test
    @DisplayName("isContainerEnvironment returns false when no container signals are present")
    void isContainerEnvironmentReturnsFalseWithNoSignals() {
        assertThat(DataCloudLauncherSettings.isContainerEnvironment(Map.of())).isFalse();
    }
}
