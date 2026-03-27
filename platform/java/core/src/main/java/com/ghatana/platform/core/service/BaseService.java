package com.ghatana.platform.core.service;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Common lifecycle and observability interface for platform services.
 *
 * <p>All platform-level services that expose lifecycle management, health status,
 * and runtime metrics should implement this interface. It provides a consistent
 * contract for:
 * <ul>
 *   <li>Lifecycle: {@link #initialize()} and {@link #shutdown()}</li>
 *   <li>Health: {@link #getHealthStatus()}</li>
 *   <li>Info: {@link #getServiceInfo()}</li>
 * </ul>
 *
 * <p>Async operations return {@code Promise<T>} (ActiveJ) to stay compatible with
 * the platform's non-blocking eventloop model. Synchronous defaults are provided
 * where applicable.
 *
 * <p>Usage pattern:
 * <pre>{@code
 * public class MyService implements BaseService {
 *
 *     {@literal @}Override
 *     public Promise<Void> initialize() {
 *         return Promise.ofBlocking(executor, this::doInit);
 *     }
 *
 *     {@literal @}Override
 *     public ServiceHealth getHealthStatus() {
 *         return ServiceHealth.healthy("MyService is ready");
 *     }
 * }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Common lifecycle and health contract for platform services
 * @doc.layer core
 * @doc.pattern Service, LifecycleManagement
 *
 * @since 2026-03-27
 */
public interface BaseService extends AutoCloseable {

    /**
     * Initializes the service asynchronously.
     *
     * <p>Called once at startup, before any business methods are invoked.
     * Implementations should allocate resources, establish connections, and
     * warm up caches here.
     *
     * @return a promise that completes when initialization is done
     */
    default Promise<Void> initialize() {
        return Promise.complete();
    }

    /**
     * Shuts down the service asynchronously, releasing all resources.
     *
     * <p>Implementations should close connections, flush pending writes, and
     * stop background threads. The returned promise completes when shutdown
     * is cleanly finished.
     *
     * @return a promise that completes when shutdown is done
     */
    default Promise<Void> shutdown() {
        return Promise.complete();
    }

    /**
     * Synchronous close used by {@link AutoCloseable#close()}.
     * Delegates to {@link #shutdown()} — override if explicit blocking behavior is needed.
     */
    @Override
    default void close() {
        // Fire-and-forget: callers that need ordered shutdown should use shutdown()
        shutdown();
    }

    /**
     * Returns the health status of this service.
     * Called by health-check endpoints and monitoring systems.
     *
     * @return current health status (never null)
     */
    ServiceHealth getHealthStatus();

    /**
     * Returns read-only metadata about this service.
     * Examples: name, version, configuration summary.
     *
     * @return immutable map of service metadata (never null)
     */
    default Map<String, Object> getServiceInfo() {
        return Map.of(
            "serviceClass", getClass().getSimpleName(),
            "status", getHealthStatus().status().name()
        );
    }

    // ── ServiceHealth ─────────────────────────────────────────────────────────

    /**
     * Represents the runtime health of a service.
     */
    record ServiceHealth(Status status, String message, Map<String, Object> details) {

        public ServiceHealth {
            if (status == null) throw new IllegalArgumentException("status is required");
            message = message != null ? message : "";
            details = details != null ? Map.copyOf(details) : Map.of();
        }

        /**
         * Creates a healthy response with an informational message.
         */
        public static ServiceHealth healthy(String message) {
            return new ServiceHealth(Status.HEALTHY, message, Map.of());
        }

        /**
         * Creates a degraded response — service is operating but with reduced capacity.
         */
        public static ServiceHealth degraded(String message) {
            return new ServiceHealth(Status.DEGRADED, message, Map.of());
        }

        /**
         * Creates an unhealthy response with an optional detail map.
         */
        public static ServiceHealth unhealthy(String message, Map<String, Object> details) {
            return new ServiceHealth(Status.UNHEALTHY, message, details != null ? details : Map.of());
        }

        /**
         * Returns {@code true} if the service is HEALTHY.
         */
        public boolean isHealthy() {
            return status == Status.HEALTHY;
        }

        /**
         * Health status levels for a service.
         */
        public enum Status {
            /** Service is fully operational. */
            HEALTHY,
            /** Service is partially operational (degraded performance or limited feature set). */
            DEGRADED,
            /** Service is unavailable or failing. */
            UNHEALTHY
        }
    }
}
