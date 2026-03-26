package com.ghatana.datacloud.infrastructure.governance.health;

import io.activej.promise.Promise;

/**
 * Port interface for health checking services.
 *
 * <p><b>Purpose</b><br>
 * Defines the contract for health check implementations that verify
 * the operational status of different system components (repositories,
 * catalogs, services). Implementations return Promise-based async results.
 *
 * <p><b>Health Check Scope</b><br>
 * Implementations check:
 * - Component connectivity (can communicate with dependencies)
 * - Resource availability (connection pools, caches not exhausted)
 * - Basic functionality (can perform simple operations)
 * - Response time (operations complete within SLO)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * HealthCheckService checkService = // injected
 * Promise<HealthCheckResult> result = checkService.checkHealth();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Health check port
 * @doc.layer infrastructure
 * @doc.pattern Port
 */
public interface HealthCheckService {

  /**
   * Performs health check for this component.
   *
   * <p>Asynchronous operation returning Promise-based result.
   * If check takes longer than 5 seconds, status set to DEGRADED.
   * If check throws exception, status set to UNHEALTHY.
   *
   * @return Promise resolving to HealthCheckResult
   */
  Promise<HealthCheckResult> checkHealth();

  /**
   * Performs readiness check for this component.
   *
   * <p>Readiness indicates component is ready to accept traffic.
   * More stringent than health check (requires full functionality).
   * Used by load balancers for routing decisions.
   *
   * @return Promise<Boolean> true if ready, false if not ready
   */
  Promise<Boolean> checkReadiness();
}
