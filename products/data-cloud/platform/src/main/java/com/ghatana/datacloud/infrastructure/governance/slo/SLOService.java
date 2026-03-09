package com.ghatana.datacloud.infrastructure.governance.slo;

import io.activej.promise.Promise;
import java.util.List;
import java.util.Optional;

/**
 * Port interface for SLO tracking and monitoring.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for SLO management operations, including recording metrics,
 * retrieving SLO data, and calculating compliance status. Enables decoupling
 * of domain logic from specific SLO storage implementations.
 *
 * <p><b>Implementations</b><br>
 * - InMemorySLOService: In-memory tracking (testing, single-instance)
 * - DatabaseSLOService: Persistent PostgreSQL storage (production multi-instance)
 *
 * @see SLOMonitor
 * @doc.type interface
 * @doc.purpose Port for SLO monitoring and compliance tracking
 * @doc.layer infrastructure
 * @doc.pattern Port
 */
public interface SLOService {

  /**
   * Records SLO metrics for a service operation.
   *
   * @param serviceName service identifier (e.g., "governance-service")
   * @param operationName operation identifier (e.g., "role-create")
   * @param responseTimeMs response time in milliseconds
   * @param errorRatePercent error rate as percentage (0.0-100.0)
   * @param availabilityPercent availability as percentage (0.0-100.0)
   * @return Promise that completes when metric is recorded
   */
  Promise<Void> recordMetric(
      String serviceName,
      String operationName,
      long responseTimeMs,
      double errorRatePercent,
      double availabilityPercent);

  /**
   * Records comprehensive SLO metrics including all percentiles.
   *
   * @param slo SLOMonitor with all metrics pre-calculated
   * @return Promise that completes when SLO is recorded
   */
  Promise<Void> recordSLO(SLOMonitor slo);

  /**
   * Retrieves SLO metrics for a specific service operation.
   *
   * @param serviceName service identifier
   * @param operationName operation identifier
   * @return Promise with SLOMonitor if found, empty Optional if not recorded
   */
  Promise<Optional<SLOMonitor>> getSLO(String serviceName, String operationName);

  /**
   * Retrieves all SLO metrics for a service.
   *
   * @param serviceName service identifier
   * @return Promise with list of SLOMonitor
   */
  Promise<List<SLOMonitor>> getServiceSLOs(String serviceName);

  /**
   * Retrieves all SLO metrics across all services.
   *
   * @return Promise with list of all SLOMonitor
   */
  Promise<List<SLOMonitor>> getAllSLOs();

  /**
   * Retrieves all SLOs with HEALTHY compliance status.
   *
   * @return Promise with list of HEALTHY SLOMonitor
   */
  Promise<List<SLOMonitor>> listHealthySLOs();

  /**
   * Retrieves all SLOs with DEGRADED compliance status.
   *
   * @return Promise with list of DEGRADED SLOMonitor
   */
  Promise<List<SLOMonitor>> listDegradedSLOs();

  /**
   * Retrieves all SLOs with UNHEALTHY compliance status.
   *
   * @return Promise with list of UNHEALTHY SLOMonitor
   */
  Promise<List<SLOMonitor>> listUnhealthySLOs();

  /**
   * Gets compliance summary for a service.
   *
   * @param serviceName service identifier
   * @return Promise with SLO compliance summary
   */
  Promise<SLOComplianceSummary> getComplianceSummary(String serviceName);

  /**
   * Clears all SLO metrics.
   *
   * @return Promise that completes when all SLOs are cleared
   */
  Promise<Void> clearAll();
}
