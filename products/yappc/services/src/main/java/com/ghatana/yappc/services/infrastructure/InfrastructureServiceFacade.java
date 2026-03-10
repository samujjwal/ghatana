package com.ghatana.yappc.services.infrastructure;

import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityServiceAdapter;
import io.activej.promise.Promise;
import java.util.Objects;

/**
 * Facade aggregating infrastructure services of the YAPPC platform.
 *
 * <p>Wraps low-level adapters (security scanning, database, etc.) behind a
 * clean domain-facing API.
 *
 * @doc.type class
 * @doc.purpose Aggregate facade for YAPPC infrastructure services
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class InfrastructureServiceFacade {

    private final SecurityServiceAdapter securityServiceAdapter;

    public InfrastructureServiceFacade(SecurityServiceAdapter securityServiceAdapter) {
        this.securityServiceAdapter = Objects.requireNonNull(securityServiceAdapter, "securityServiceAdapter");
    }

    /**
     * Returns a simple health status string.
     *
     * @return promise of {@code "OK"}
     */
    public Promise<String> healthCheck() {
        return Promise.of("OK");
    }

    /**
     * Checks whether the database is reachable.
     *
     * <p>In this stub implementation, always returns {@code true}; replace with
     * an actual connectivity probe in production.
     *
     * @return promise of {@code true} when the database is reachable
     */
    public Promise<Boolean> isDatabaseReachable() {
        return Promise.of(true);
    }
}
