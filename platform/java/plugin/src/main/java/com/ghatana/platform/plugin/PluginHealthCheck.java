package com.ghatana.platform.plugin;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Reusable health check contract for plugins and services.
 * <p>
 * This interface can be implemented by any component that needs to report
 * health status, including plugins, services, and infrastructure components.
 * It separates health checking from the plugin lifecycle, enabling composition
 * of health checks from multiple sources.
 * <p>
 * Example:
 * <pre>
 * PluginHealthCheck dbCheck = () -> checkDatabase();
 * PluginHealthCheck cacheCheck = () -> checkCache();
 * PluginHealthCheck composite = PluginHealthCheck.composite("my-service", dbCheck, cacheCheck);
 * </pre>
 *
 * @doc.type interface
 * @doc.purpose Composable health check contract
 * @doc.layer core
 */
@FunctionalInterface
public interface PluginHealthCheck {

    /**
     * Performs a health check and returns the result.
     *
     * @return a Promise resolving to the health status
     */
    @NotNull
    Promise<HealthStatus> check();

    /**
     * Returns a health check that always reports healthy.
     */
    @NotNull
    static PluginHealthCheck alwaysHealthy() {
        return () -> Promise.of(HealthStatus.ok());
    }

    /**
     * Returns a health check that always reports unhealthy with the given message.
     */
    @NotNull
    static PluginHealthCheck alwaysUnhealthy(@NotNull String message) {
        return () -> Promise.of(HealthStatus.unhealthy(message));
    }

    /**
     * Creates a composite health check that aggregates results from multiple checks.
     * <p>
     * The composite check is healthy only if ALL constituent checks are healthy.
     * If any check fails, the composite reports unhealthy with details from all checks.
     *
     * @param name   the name of this composite check
     * @param checks the individual health checks to aggregate
     * @return a composite health check
     */
    @NotNull
    static PluginHealthCheck composite(@NotNull String name, @NotNull PluginHealthCheck... checks) {
        return () -> {
            if (checks.length == 0) {
                return Promise.of(HealthStatus.ok("No checks configured for " + name));
            }

            @SuppressWarnings("unchecked")
            Promise<HealthStatus>[] promises = new Promise[checks.length];
            for (int i = 0; i < checks.length; i++) {
                promises[i] = checks[i].check();
            }

            return io.activej.promise.Promises.toList(java.util.List.of(promises))
                .map(results -> {
                    boolean allHealthy = results.stream().allMatch(HealthStatus::healthy);
                    java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
                    for (int i = 0; i < results.size(); i++) {
                        HealthStatus result = results.get(i);
                        details.put("check-" + i, java.util.Map.of(
                            "healthy", result.healthy(),
                            "message", result.message() != null ? result.message() : ""
                        ));
                    }
                    if (allHealthy) {
                        return HealthStatus.ok(name + ": all checks passed", details);
                    } else {
                        long unhealthyCount = results.stream()
                            .filter(r -> !r.healthy()).count();
                        return HealthStatus.unhealthy(
                            name + ": " + unhealthyCount + "/" + results.size() + " checks failed",
                            details
                        );
                    }
                });
        };
    }
}
