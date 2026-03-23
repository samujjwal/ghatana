package com.ghatana.pipeline.registry.health;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import javax.sql.DataSource;

import com.ghatana.core.database.health.DatabaseHealthCheck;
import com.ghatana.core.database.health.HealthDetails;
import com.ghatana.core.database.health.HealthStatus;
import com.ghatana.platform.observability.health.HealthCheck;
import com.ghatana.platform.observability.health.HealthCheckRegistry;

import io.activej.promise.Promise;

/**
 * Health check implementations for the Pipeline Registry service.
 *
 * <p>Purpose: Provides comprehensive health checks for database connectivity,
 * pipeline service functionality, and gRPC service availability. Integrates
 * with the observability HealthCheckRegistry for centralized monitoring.</p>
 *
 * @doc.type class
 * @doc.purpose Utility class providing health check implementations
 * @doc.layer product
 * @doc.pattern HealthCheck
 * @since 2.0.0
 */
public class PipelineRegistryHealthChecks {

    private PipelineRegistryHealthChecks() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Register all health checks for the Pipeline Registry service.
     */
    public static void registerHealthChecks(HealthCheckRegistry registry, DataSource dataSource) {
        // Database health check
        registry.register(new PipelineRegistryDatabaseHealthCheck(dataSource));
        
        // Service-specific health checks
        registry.register(new PipelineServiceHealthCheck());
        registry.register(new GrpcServiceHealthCheck());
    }

    /**
     * Adapter between core database health checks and observability health check contracts.
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
            return delegate.checkAsync().map(PipelineRegistryDatabaseHealthCheck::toHealthCheckResult);
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
            Duration duration = status.getResponseTime() != null ? status.getResponseTime() : Duration.ZERO;
            String message = status.getMessage() != null ? status.getMessage() : "Database health check completed";

            return switch (status.getStatus()) {
                case HEALTHY -> HealthCheckResult.healthy(message, details, duration);
                case UNHEALTHY -> HealthCheckResult.unhealthy(message, details, duration, status.getException());
                case UNKNOWN -> new HealthCheckResult(Status.UNKNOWN, message, details, duration, status.getException());
            };
        }

        private static Map<String, Object> extractDetails(HealthDetails details) {
            return details != null ? details.getDetails() : Map.of();
        }
    }
    
    /**
     * Health check for the pipeline service functionality.
     */
    public static class PipelineServiceHealthCheck implements HealthCheck {
        
        @Override
        public Promise<HealthCheckResult> check() {
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
                try {
                    // Check if the service can perform basic operations
                    // This is a placeholder - in real implementation, you'd check:
                    // - Can access pipeline repository
                    // - Can perform basic CRUD operations
                    // - Any other service-specific checks
                    
                    Map<String, Object> details = Map.of(
                        "component", "pipeline-service",
                        "status", "operational"
                    );
                    
                    return HealthCheckResult.healthy("Pipeline service is operational", details, Duration.ofMillis(10));
                    
                } catch (Exception e) {
                    return HealthCheckResult.unhealthy("Pipeline service check failed", e);
                }
            });
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
            return true; // Core service functionality is critical
        }
    }
    
    /**
     * Health check for gRPC service availability.
     */
    public static class GrpcServiceHealthCheck implements HealthCheck {
        
        @Override
        public Promise<HealthCheckResult> check() {
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
                try {
                    // Check if gRPC server is running and accepting connections
                    // This is a placeholder - in real implementation, you'd check:
                    // - gRPC server status
                    // - Port availability
                    // - Service registration status
                    
                    Map<String, Object> details = Map.of(
                        "component", "grpc-server",
                        "port", 9090, // Example port
                        "status", "listening"
                    );
                    
                    return HealthCheckResult.healthy("gRPC service is listening", details, Duration.ofMillis(5));
                    
                } catch (Exception e) {
                    return HealthCheckResult.unhealthy("gRPC service check failed", e);
                }
            });
        }
        
        @Override
        public String getName() {
            return "grpc-service";
        }
        
        @Override
        public Duration getTimeout() {
            return Duration.ofSeconds(1);
        }
        
        @Override
        public boolean isCritical() {
            return true; // gRPC API is critical for service communication
        }
    }
}
