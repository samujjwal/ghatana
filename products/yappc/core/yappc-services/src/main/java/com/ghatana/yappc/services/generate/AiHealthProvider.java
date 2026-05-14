package com.ghatana.yappc.services.generate;

/**
 * Provides the current AI service health/degradation status.
 *
 * <p>Implementations may query an external health endpoint, check a circuit-breaker
 * state, or read a feature flag. The production wiring should inject a real
 * implementation; test code can use a simple mutable stub.
 *
 * @doc.type interface
 * @doc.purpose Abstracts AI-service degradation status for injectable health checking
 * @doc.layer service
 * @doc.pattern Provider
 */
public interface AiHealthProvider {

    /**
     * Returns {@code true} when the AI completion service is considered degraded and
     * generation should fall back to deterministic artifacts.
     *
     * @return {@code true} if the AI service is unavailable or unhealthy
     */
    boolean isDegraded();

    /**
     * Returns a non-degraded provider suitable for production use when no external
     * health signal is configured.
     */
    static AiHealthProvider alwaysHealthy() {
        return () -> false;
    }
}
