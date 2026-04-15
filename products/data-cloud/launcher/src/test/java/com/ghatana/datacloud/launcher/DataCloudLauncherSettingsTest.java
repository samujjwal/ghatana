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
}
