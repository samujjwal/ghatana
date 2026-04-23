package com.ghatana.datacloud.launcher;

import static org.assertj.core.api.Assertions.assertThat;

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
    void environmentOverridesClientConfigFlags() { // GH-90000
        DataCloud.DataCloudConfig config =
                DataCloudLauncherSettings.parseClientConfig( // GH-90000
                        new String[] {"--instance-id", "cli-instance", "--max-connections", "15"},
                        Map.of( // GH-90000
                                "DATACLOUD_INSTANCE_ID", "env-instance",
                                "DATACLOUD_MAX_CONNECTIONS", "42"));

        assertThat(config.instanceId()).isEqualTo("env-instance");
        assertThat(config.maxConnectionsPerTenant()).isEqualTo(42); // GH-90000
    }

    @Test
    @DisplayName("http server starts from cli flags")
    void httpServerStartsFromCliFlags() { // GH-90000
        boolean startHttp =
                DataCloudLauncherSettings.shouldStartHttpServer( // GH-90000
                        new String[] {"--http"},
                        Map.of()); // GH-90000

        assertThat(startHttp).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("http server stays disabled when env flag is false")
    void httpServerRespectsFalseEnvFlag() { // GH-90000
        boolean startHttp =
                DataCloudLauncherSettings.shouldStartHttpServer( // GH-90000
                        new String[0],
                        Map.of("DATACLOUD_HTTP_ENABLED", "false")); // GH-90000

        assertThat(startHttp).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("grpc server starts when port is configured")
    void grpcServerStartsWhenPortConfigured() { // GH-90000
        boolean startGrpc =
                DataCloudLauncherSettings.shouldStartGrpcServer( // GH-90000
                        new String[0],
                        Map.of("DATACLOUD_GRPC_PORT", "9090")); // GH-90000

        assertThat(startGrpc).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("grpc server stays disabled when enable flag is false and no port is configured")
    void grpcServerRespectsFalseEnvFlag() { // GH-90000
        boolean startGrpc =
                DataCloudLauncherSettings.shouldStartGrpcServer( // GH-90000
                        new String[0],
                        Map.of("DATACLOUD_GRPC_ENABLED", "false")); // GH-90000

        assertThat(startGrpc).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("http server starts when enable flag is true")
    void httpServerStartsWhenEnabledInEnvironment() { // GH-90000
        boolean startHttp =
                DataCloudLauncherSettings.shouldStartHttpServer( // GH-90000
                        new String[0],
                        Map.of("DATACLOUD_HTTP_ENABLED", "true")); // GH-90000

        assertThat(startHttp).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("launcher detects when no transport is enabled")
    void launcherDetectsNoEnabledTransport() { // GH-90000
        boolean hasEnabledTransport =
                DataCloudLauncherSettings.hasEnabledTransport(new String[0], Map.of()); // GH-90000

        assertThat(hasEnabledTransport).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("http port falls back to default when missing")
    void httpPortFallsBackToDefault() { // GH-90000
        int port = DataCloudLauncherSettings.resolveHttpPort(Map.of()); // GH-90000

        assertThat(port).isEqualTo(8082); // GH-90000
    }

    @Test
    @DisplayName("http port is resolved from environment")
    void httpPortResolvesFromEnvironment() { // GH-90000
        int port = DataCloudLauncherSettings.resolveHttpPort( // GH-90000
                Map.of("DATACLOUD_HTTP_PORT", "8181")); // GH-90000

        assertThat(port).isEqualTo(8181); // GH-90000
    }

    @Test
    @DisplayName("rate limit requests falls back to default 200 when env var absent")
    void rateLimitRequestsDefaultsTo200() { // GH-90000
        int requests = DataCloudLauncherSettings.resolveRateLimitRequests(Map.of()); // GH-90000

        assertThat(requests).isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("rate limit requests is resolved from DATACLOUD_RATE_LIMIT_REQUESTS env var")
    void rateLimitRequestsResolvesFromEnvironment() { // GH-90000
        int requests = DataCloudLauncherSettings.resolveRateLimitRequests( // GH-90000
                Map.of("DATACLOUD_RATE_LIMIT_REQUESTS", "500")); // GH-90000

        assertThat(requests).isEqualTo(500); // GH-90000
    }

    @Test
    @DisplayName("rate limit window seconds falls back to default 60 when env var absent")
    void rateLimitWindowSecondsDefaultsTo60() { // GH-90000
        long windowSec = DataCloudLauncherSettings.resolveRateLimitWindowSeconds(Map.of()); // GH-90000

        assertThat(windowSec).isEqualTo(60L); // GH-90000
    }

    @Test
    @DisplayName("rate limit window seconds resolves from DATACLOUD_RATE_LIMIT_WINDOW_SECONDS env var")
    void rateLimitWindowSecondsResolvesFromEnvironment() { // GH-90000
        long windowSec = DataCloudLauncherSettings.resolveRateLimitWindowSeconds( // GH-90000
                Map.of("DATACLOUD_RATE_LIMIT_WINDOW_SECONDS", "120")); // GH-90000

        assertThat(windowSec).isEqualTo(120L); // GH-90000
    }

    @Test
    @DisplayName("rate limit ignores blank env var and falls back to defaults")
    void rateLimitIgnoresBlankEnvVar() { // GH-90000
        int requests = DataCloudLauncherSettings.resolveRateLimitRequests( // GH-90000
                Map.of("DATACLOUD_RATE_LIMIT_REQUESTS", "  ")); // GH-90000
        long windowSec = DataCloudLauncherSettings.resolveRateLimitWindowSeconds( // GH-90000
                Map.of("DATACLOUD_RATE_LIMIT_WINDOW_SECONDS", "  ")); // GH-90000

        assertThat(requests).isEqualTo(200); // GH-90000
        assertThat(windowSec).isEqualTo(60L); // GH-90000
    }

    @Test
    @DisplayName("client config resolves sovereign profile and data dir from env")
    void clientConfigResolvesSovereignProfileAndDataDirFromEnvironment() { // GH-90000
        DataCloud.DataCloudConfig config = DataCloudLauncherSettings.parseClientConfig( // GH-90000
                new String[0],
                Map.of( // GH-90000
                        "DATACLOUD_PROFILE", "sovereign",
                        "DATACLOUD_SOVEREIGN_DATA_DIR", "/tmp/dc-sovereign"));

        assertThat(config.profile()).isEqualTo(DataCloud.DataCloudConfig.DataCloudProfile.SOVEREIGN); // GH-90000
        assertThat(config.customConfig()).containsEntry("sovereign.dataDir", "/tmp/dc-sovereign"); // GH-90000
        assertThat(DataCloudLauncherSettings.isEmbeddedProfile(config.profile())).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("client config resolves sovereign profile from cli flag")
    void clientConfigResolvesSovereignProfileFromCliFlag() { // GH-90000
        DataCloud.DataCloudConfig config = DataCloudLauncherSettings.parseClientConfig( // GH-90000
                new String[] {"--profile=sovereign", "--sovereign-data-dir=/var/lib/datacloud"},
                Map.of()); // GH-90000

        assertThat(config.profile()).isEqualTo(DataCloud.DataCloudConfig.DataCloudProfile.SOVEREIGN); // GH-90000
        assertThat(config.customConfig()).containsEntry("sovereign.dataDir", "/var/lib/datacloud"); // GH-90000
    }

    @Test
    @DisplayName("storage compaction settings fall back to sovereign defaults")
    void storageCompactionSettingsFallBackToDefaults() { // GH-90000
        assertThat(DataCloudLauncherSettings.resolveStorageCompactionThreshold(Map.of())).isEqualTo(25); // GH-90000
        assertThat(DataCloudLauncherSettings.resolveStorageCompactionIntervalSeconds(Map.of())).isEqualTo(300L); // GH-90000
    }
}
