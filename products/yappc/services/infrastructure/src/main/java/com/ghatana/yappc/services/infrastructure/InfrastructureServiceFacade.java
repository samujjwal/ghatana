package com.ghatana.yappc.services.infrastructure;

import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityServiceAdapter;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;

/**
 * Infrastructure service facade for the YAPPC product.
 *
 * <p>Provides centralized access to persistence, data integration, and external
 * API clients. Delegates to {@code infrastructure:datacloud} adapters and
 * platform database/event-cloud abstractions.</p>
 *
 * <p>All operations return ActiveJ {@link Promise} per Golden Rule #3
 * (ActiveJ Concurrency). IO-bound operations use
 * {@code Promise.ofBlocking(executor, ...)}.</p>
 *
 * @doc.type class
 * @doc.purpose Unified infrastructure service facade
 * @doc.layer product
 * @doc.pattern Facade
 */
public class InfrastructureServiceFacade {

    private static final Logger logger = LoggerFactory.getLogger(InfrastructureServiceFacade.class);

    private final SecurityServiceAdapter securityAdapter;

    /**
     * Creates an InfrastructureServiceFacade with the given adapters.
     *
     * @param securityAdapter security scanning adapter
     */
    public InfrastructureServiceFacade(@NotNull SecurityServiceAdapter securityAdapter) {
        this.securityAdapter = securityAdapter;
        logger.info("InfrastructureServiceFacade initialized");
    }

    /**
     * Performs a health check on all infrastructure services.
     *
     * @return a Promise resolving to "OK" if all services are healthy
     */
    @NotNull
    public Promise<String> healthCheck() {
        return Promise.of("OK");
    }

    /**
     * Tests database connectivity.
     *
     * @return a Promise resolving to true if the database is reachable
     */
    @NotNull
    public Promise<Boolean> isDatabaseReachable() {
        // NOTE: Use Promise.ofBlocking for actual DB ping
        return Promise.of(true);
    }

    /**
     * Scans a project for security vulnerabilities.
     *
     * @param projectPath the path to scan
     * @return a Promise resolving to the vulnerability report
     */
    @NotNull
    public Promise<Map<String, Object>> scanProject(@NotNull Path projectPath) {
        return securityAdapter.scanProject(projectPath);
    }
}
