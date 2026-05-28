package com.ghatana.yappc.services.generate;

/**
 * Production AI health provider that reads explicit degradation signals from the runtime.
 *
 * <p>The provider treats the following configuration sources as authoritative:
 * <ul>
 *   <li>System property {@code yappc.ai.degraded}</li>
 *   <li>Environment variable {@code YAPPC_AI_DEGRADED}</li>
 *   <li>Environment variable {@code GHATANA_AI_DEGRADED}</li>
 * </ul>
 *
 * <p>Any truthy value such as {@code true}, {@code 1}, {@code yes}, or {@code on}
 * marks the AI service as degraded and triggers deterministic fallback generation.
 *
 * @doc.type class
 * @doc.purpose Reads explicit runtime signals to determine whether AI generation is degraded
 * @doc.layer product
 * @doc.pattern Provider
 */
public final class EnvironmentAiHealthProvider implements AiHealthProvider {

    private static final String SYSTEM_PROPERTY_KEY = "yappc.ai.degraded";
    private static final String PRIMARY_ENV_KEY = "YAPPC_AI_DEGRADED";
    private static final String LEGACY_ENV_KEY = "GHATANA_AI_DEGRADED";

    @Override
    public boolean isDegraded() {
        return isTruthy(System.getProperty(SYSTEM_PROPERTY_KEY))
                || isTruthy(System.getenv(PRIMARY_ENV_KEY))
                || isTruthy(System.getenv(LEGACY_ENV_KEY));
    }

    private static boolean isTruthy(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "1", "yes", "on", "degraded", "unhealthy" -> true;
            default -> false;
        };
    }
}