package com.ghatana.pipeline.registry.health;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

import javax.sql.DataSource;

import com.ghatana.core.database.health.DatabaseHealthCheck;
import com.ghatana.core.database.health.HealthDetails;
import com.ghatana.core.database.health.HealthStatus;
import com.ghatana.pipeline.registry.repository.PipelineRepository;
import com.ghatana.platform.observability.health.HealthCheck;
import com.ghatana.platform.observability.health.HealthCheckRegistry;

import io.activej.promise.Promise;

/**
 * Active health check implementations for the Pipeline Registry service.
 *
 * <p>Every check probes an actual dependency: the database via a validation
 * query, the pipeline repository via a count probe, and the gRPC transport
 * via a TCP socket connect. A check that cannot reach its target reports
 * {@code UNHEALTHY}, ensuring readiness endpoints reflect reality rather than
 * assumptions.
 *
 * <h3>Registration</h3>
 * <pre>{@code
 * PipelineRegistryHealthChecks.registerHealthChecks(
 *     registry, dataSource, pipelineRepository, "localhost", 9090);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Active dependency-probing health checks for the Pipeline Registry
 * @doc.layer product
 * @doc.pattern HealthCheck
 * @since 2.1.0
 */
public class PipelineRegistryHealthChecks {

    private PipelineRegistryHealthChecks() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers all health checks using a legacy-compatible signature.
     *
     * <p>The pipeline service check is skipped when no repository is provided.
     * The gRPC check defaults to {@code localhost:9090}.
     *
     * @param registry   health check registry
     * @param dataSource JDBC data source for database probe
     */
    public static void registerHealthChecks(HealthCheckRegistry registry, DataSource dataSource) {
        registerHealthChecks(registry, dataSource, null, "localhost", 9090);
    }

    /**
     * Registers all health checks with active dependency probes.
     *
     * @param registry           health check registry
     * @param dataSource         JDBC data source for database probe
     * @param pipelineRepository pipeline repository for service probe (may be {@code null} to skip)
     * @param grpcHost           hostname where the gRPC server is bound
     * @param grpcPort           port number where the gRPC server is bound
     */
    public static void registerHealthChecks(HealthCheckRegistry registry, DataSource dataSource,
            PipelineRepository pipelineRepository, String grpcHost, int grpcPort) {
        registry.register(new PipelineRegistryDatabaseHealthCheck(dataSource));
        if (pipelineRepository != null) {
            registry.register(new PipelineServiceHealthCheck(pipelineRepository));
        }
        registry.register(new GrpcServiceHealthCheck(grpcHost, grpcPort));
    }

    // =========================================================================
    // Database health check — delegates to platform DatabaseHealthCheck
    // =========================================================================

    /**
     * Adapts the platform {@link DatabaseHealthCheck} to the observability
     * {@link HealthCheck} contract.
     */
    public static class PipelineRegistryDatabaseHealthCheck implements HealthCheck {
        private final DatabaseHealthCheck delegate;

        public PipelineRegistryDatabaseHealthCheck(DataSource dataSource) {
            this.delegate = DatabaseHealthCheck.builder()
                .dataSource(dataSource)
                .build();
        }

        @Override
        public Promise<HealthCheckResult> check() {
            return delegate.checkAsync()
                    .map(PipelineRegistryDatabaseHealthCheck::toHealthCheckResult);
        }

        @Override
        public String getName() {
            return "pipeline-registry-database";
        }

        @Override
        public Duration getTimeout() {
            return delegate.getTimeout();
        }

        @Override
        public boolean isCritical() {
            return false;
        }

        private static HealthCheckResult toHealthCheckResult(HealthStatus status) {
            Map<String, Object> details = extractDetails(status.getDetails());
            Duration duration = status.getResponseTime() != null
                    ? status.getResponseTime() : Duration.ZERO;
            String message = status.getMessage() != null
                    ? status.getMessage() : "Database health check completed";

            return switch (status.getStatus()) {
                case HEALTHY   -> HealthCheckResult.healthy(message, details, duration);
                case UNHEALTHY -> HealthCheckResult.unhealthy(message, details, duration,
                        status.getException());
                case UNKNOWN   -> new HealthCheckResult(Status.UNKNOWN, message, details,
                        duration, status.getException());
            };
        }

        private static Map<String, Object> extractDetails(HealthDetails details) {
            return details != null ? details.getDetails() : Map.of();
        }
    }

    // =========================================================================
    // Pipeline service health check — probes repository with a count query
    // =========================================================================

    /**
     * Verifies that the pipeline repository can execute queries.
     *
     * <p>Issues a {@code countByTenantId} probe against a reserved {@code _health}
     * tenant. A successful response (any count including zero) indicates the
     * storage layer is reachable. An exception or timeout indicates failure.
     */
    public static class PipelineServiceHealthCheck implements HealthCheck {
        private final PipelineRepository repository;

        /**
         * @param repository pipeline repository to probe; must not be {@code null}
         */
        public PipelineServiceHealthCheck(PipelineRepository repository) {
            this.repository = Objects.requireNonNull(repository, "PipelineRepository");
        }

        @Override
        public Promise<HealthCheckResult> check() {
            Instant start = Instant.now();
            return repository.countByTenantId("_health")
                    .map(count -> HealthCheckResult.healthy(
                            "Pipeline registry is responsive",
                            Map.of("probe", "count_query", "tenantProbe", "_health"),
                            Duration.between(start, Instant.now())))
                    .then(Promise::of, e -> Promise.of(
                            HealthCheckResult.unhealthy(
                                    "Pipeline registry probe failed: " + e.getMessage(), e)));
        }

        @Override
        public String getName() {
            return "pipeline-service";
        }

        @Override
        public Duration getTimeout() {
            return Duration.ofSeconds(2);
        }

        @Override
        public boolean isCritical() {
            return true;
        }
    }

    // =========================================================================
    // gRPC health check — probes transport via TCP socket connect
    // =========================================================================

    /**
     * Verifies that the gRPC server is accepting TCP connections.
     *
     * <p>Opens a TCP socket to {@code host:port} with a 1-second timeout.
     * A successful connect indicates the transport is bound and reachable.
     * A refused or timed-out connection reports {@code UNHEALTHY}.
     */
    public static class GrpcServiceHealthCheck implements HealthCheck {
        private static final int SOCKET_TIMEOUT_MS = 1_000;

        private final String host;
        private final int port;

        /**
         * @param host gRPC server hostname
         * @param port gRPC server port
         */
        public GrpcServiceHealthCheck(String host, int port) {
            this.host = Objects.requireNonNull(host, "grpcHost");
            this.port = port;
        }

        @Override
        public Promise<HealthCheckResult> check() {
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
                Instant start = Instant.now();
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MS);
                    return HealthCheckResult.healthy(
                            "gRPC service accepting connections on " + host + ":" + port,
                            Map.of("host", host, "port", port),
                            Duration.between(start, Instant.now()));
                } catch (Exception e) {
                    return HealthCheckResult.unhealthy(
                            "gRPC service unreachable at " + host + ":" + port
                                    + " — " + e.getMessage(), e);
                }
            });
        }

        @Override
        public String getName() {
            return "grpc-service";
        }

        @Override
        public Duration getTimeout() {
            return Duration.ofSeconds(3);
        }

        @Override
        public boolean isCritical() {
            return true;
        }
    }
}
