package com.ghatana.refactorer.server.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Server configuration record using Typesafe Config (HOCON). Immutable configuration for the

 * Polyfix service server.

 *

 * @doc.type record

 * @doc.purpose Expose immutable runtime options for HTTP, gRPC, observability, and tenancy.

 * @doc.layer product

 * @doc.pattern Configuration

 */

public record ServerConfig(
        int httpPort,
        int grpcPort,
        boolean tlsEnabled,
        JwtConfig jwt,
        ObservabilityConfig observability,
        TenancyConfig tenancy) {

    public record JwtConfig(String issuer, String audience, String jwkSetUrl, String sharedSecret) {
        public static JwtConfig fromConfig(Config config) {
            return new JwtConfig(
                    config.getString("issuer"),
                    config.getString("audience"),
                    config.hasPath("jwkSetUrl") ? config.getString("jwkSetUrl") : "",
                    System.getenv("POLYFIX_JWT_SECRET"));
        }
    }

    public record ObservabilityConfig(
            String otlpEndpoint, boolean metricsEnabled, boolean tracingEnabled) {
        public static ObservabilityConfig fromConfig(Config config) {
            return new ObservabilityConfig(
                    config.hasPath("otlp.endpoint") ? config.getString("otlp.endpoint") : "",
                    config.hasPath("metrics.enabled") ? config.getBoolean("metrics.enabled") : true,
                    config.hasPath("tracing.enabled")
                            ? config.getBoolean("tracing.enabled")
                            : true);
        }
    }

    public record TenancyConfig(
            int maxConcurrentJobsPerTenant, int maxEditsPerFile, boolean authRequired) {
        public static TenancyConfig fromConfig(Config config) {
            return new TenancyConfig(
                    config.hasPath("maxConcurrentJobsPerTenant")
                            ? config.getInt("maxConcurrentJobsPerTenant")
                            : 2,
                    config.hasPath("maxEditsPerFile") ? config.getInt("maxEditsPerFile") : 20,
                    config.hasPath("authRequired") ? config.getBoolean("authRequired") : true);
        }
    }

    public static ServerConfig fromConfig(Config config) {
        return new ServerConfig(
                config.hasPath("server.httpPort") ? config.getInt("server.httpPort") : 8080,
                config.hasPath("server.grpcPort") ? config.getInt("server.grpcPort") : 8090,
                config.hasPath("server.tls.enabled")
                        ? config.getBoolean("server.tls.enabled")
                        : false,
                JwtConfig.fromConfig(config.getConfig("auth.jwt")),
                ObservabilityConfig.fromConfig(
                        config.hasPath("observability")
                                ? config.getConfig("observability")
                                : ConfigFactory.empty()),
                TenancyConfig.fromConfig(
                        config.hasPath("tenancy")
                                ? config.getConfig("tenancy")
                                : ConfigFactory.empty()));
    }
}
