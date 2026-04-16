package com.ghatana.yappc.infrastructure.datacloud.health;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.observability.health.HealthCheck;
import com.ghatana.platform.observability.health.HealthCheck.HealthCheckResult;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Data Cloud health check implementation.
 * Tests Data Cloud connectivity using a lightweight query operation.
 *
 * <p>Performs lightweight Data Cloud operations to verify:
 * <ul>
 *   <li>Data Cloud client can connect</li>
 *   <li>Query operations execute successfully</li>
 *   <li>Response latency is acceptable</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * DataCloudClient client = DataCloudClient.create(config);
 * DataCloudHealthCheck check = new DataCloudHealthCheck(client, "data-cloud");
 *
 * check.check()
 *     .whenResult(result -> {
 *         if (result.isHealthy()) {
 *             logger.info("Data Cloud healthy: {}", result.getMessage());
 *         } else {
 *             logger.error("Data Cloud unhealthy: {}", result.getMessage());
 *         }
 *     });
 *
 * // Register for readiness probe (non-critical)
 * HealthCheckRegistry registry = HealthCheckRegistry.getInstance();
 * registry.register(check); // isCritical=false -> readiness only
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Data Cloud health check implementation for connectivity verification
 * @doc.layer infrastructure
 * @doc.pattern Health Check, Component Verification
 *
 * <h2>Health Check Operations:</h2>
 * <pre>
 * 1. Execute a lightweight query with limit=1
 * 2. Verify query completes without error
 * 3. Measure response latency
 * 4. Return health status with details
 * </pre>
 *
 * <h2>Result Details:</h2>
 * Check result includes metadata:
 * - {@code queryExecuted}: Query was executed successfully
 * - {@code latencyMs}: Query execution time in milliseconds
 * - {@code tenantId}: Tenant ID used for check
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe (DataCloudClient handles concurrent operations).
 *
 * <h2>Performance:</h2>
 * - Typical latency: <100ms (local Data Cloud)
 * - Remote latency: <500ms (network overhead)
 * - Timeout: 5 seconds (default)
 *
 * @since 1.0.0
 */
public class DataCloudHealthCheck implements HealthCheck {

    private final DataCloudClient client;
    private final String name;
    private final Duration timeout;
    private final String tenantId;

    public DataCloudHealthCheck(@NotNull DataCloudClient client, @NotNull String name) {
        this(client, name, Duration.ofSeconds(5), "health-check-tenant");
    }

    public DataCloudHealthCheck(
            @NotNull DataCloudClient client,
            @NotNull String name,
            @NotNull Duration timeout,
            @NotNull String tenantId) {
        this.client = client;
        this.name = name;
        this.timeout = timeout;
        this.tenantId = tenantId;
    }

    @Override
    public Promise<HealthCheckResult> check() {
        Instant start = Instant.now();

        // Execute a lightweight query to test connectivity
        return client.query(tenantId, "health-check", DataCloudClient.Query.limit(1))
                .map(
                    entities -> {
                        Duration duration = Duration.between(start, Instant.now());
                        Map<String, Object> details = Map.of(
                                "queryExecuted", true,
                                "latencyMs", duration.toMillis(),
                                "tenantId", tenantId,
                                "resultCount", entities.size()
                        );
                        return HealthCheckResult.healthy(
                                "Data Cloud connection successful",
                                details,
                                duration
                        );
                    },
                    error -> {
                        Duration duration = Duration.between(start, Instant.now());
                        Map<String, Object> details = Map.of(
                                "error", error.getClass().getSimpleName(),
                                "message", error.getMessage() != null ? error.getMessage() : "Unknown error",
                                "latencyMs", duration.toMillis(),
                                "tenantId", tenantId
                        );
                        return HealthCheckResult.unhealthy(
                                "Data Cloud health check failed",
                                details,
                                duration,
                                error
                        );
                    }
                );
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public boolean isCritical() {
        return false; // Data Cloud is typically not critical for liveness, but important for readiness
    }
}
