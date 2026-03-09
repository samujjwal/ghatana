package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Represents the health status of a plugin.
 *
 * @param healthy Whether the plugin is healthy
 * @param message Status message or error description
 * @param details Additional health metrics
 *
 * @doc.type record
 * @doc.purpose Health reporting
 * @doc.layer core
 */
public record HealthStatus(
    boolean healthy,
    @Nullable String message,
    @NotNull Map<String, Object> details
) {
    public static HealthStatus ok() {
        return new HealthStatus(true, "OK", Map.of());
    }

    public static HealthStatus ok(@NotNull String message) {
        return new HealthStatus(true, message, Map.of());
    }

    public static HealthStatus ok(@NotNull String message, @NotNull Map<String, Object> details) {
        return new HealthStatus(true, message, details);
    }

    public static HealthStatus unhealthy(@NotNull String message) {
        return new HealthStatus(false, message, Map.of());
    }

    public static HealthStatus unhealthy(@NotNull String message, @NotNull Map<String, Object> details) {
        return new HealthStatus(false, message, details);
    }

    public static HealthStatus error(@NotNull String message) {
        return new HealthStatus(false, message, Map.of());
    }

    public static HealthStatus error(@NotNull String message, @NotNull Throwable cause) {
        return new HealthStatus(false, message, Map.of(
            "error", cause.getClass().getSimpleName(),
            "errorMessage", cause.getMessage() != null ? cause.getMessage() : "unknown"
        ));
    }
}
