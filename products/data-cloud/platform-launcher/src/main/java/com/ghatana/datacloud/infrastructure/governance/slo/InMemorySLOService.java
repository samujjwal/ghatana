package com.ghatana.datacloud.infrastructure.governance.slo;

import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory implementation of SLOService for testing and single-instance deployments.
 *
 * <p><b>Purpose</b><br>
 * Provides fast, in-memory SLO tracking without external dependencies.
 * Not suitable for distributed deployments or durability requirements.
 *
 * <p><b>Thread Safety</b><br>
 * All operations are synchronized for thread-safe concurrent access.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SLOService sloService = new InMemorySLOService();
 *
 * // Record metrics
 * sloService.recordMetric("governance-service", "role-create", 45, 0.0, 99.99).getResult();
 *
 * // Retrieve SLOs
 * List<SLOMonitor> all = sloService.getAllSLOs().getResult();
 * Optional<SLOMonitor> one = sloService.getSLO("governance-service", "role-create").getResult();
 *
 * // Get compliance summary
 * SLOComplianceSummary summary = sloService.getComplianceSummary(
 *     "governance-service"
 * ).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose In-memory SLO service implementation
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class InMemorySLOService implements SLOService {

  private static final Logger LOG = LoggerFactory.getLogger(InMemorySLOService.class);

  private final Map<String, Map<String, SLOMonitor>> slosByServiceAndOperation;

  public InMemorySLOService() {
    this.slosByServiceAndOperation = new HashMap<>();
  }

  /**
   * Records SLO metrics for a service operation.
   *
   * <p>GIVEN: serviceName, operationName, response time, error rate, availability
   * WHEN: recordMetric is called
   * THEN: SLOMonitor is created and stored in memory
   */
  @Override
  public Promise<Void> recordMetric(
      String serviceName,
      String operationName,
      long responseTimeMs,
      double errorRatePercent,
      double availabilityPercent) {
    Objects.requireNonNull(serviceName, "serviceName cannot be null");
    Objects.requireNonNull(operationName, "operationName cannot be null");

    SLOMonitor slo =
        SLOMonitor.builder()
            .serviceName(serviceName)
            .operationName(operationName)
            .p50ResponseTimeMs(responseTimeMs)
            .p99ResponseTimeMs(responseTimeMs * 2)
            .p999ResponseTimeMs(responseTimeMs * 3)
            .errorRatePercent(errorRatePercent)
            .availabilityPercent(availabilityPercent)
            .build();

    return recordSLO(slo);
  }

  /**
   * Records comprehensive SLO metrics including all percentiles.
   *
   * <p>GIVEN: Complete SLOMonitor object
   * WHEN: recordSLO is called
   * THEN: SLO is stored in memory under service and operation keys
   */
  @Override
  public Promise<Void> recordSLO(SLOMonitor slo) {
    Objects.requireNonNull(slo, "slo cannot be null");

    synchronized (slosByServiceAndOperation) {
      slosByServiceAndOperation
          .computeIfAbsent(slo.getServiceName(), k -> new HashMap<>())
          .put(slo.getOperationName(), slo);
    }

    return Promise.complete();
  }

  /**
   * Retrieves SLO metrics for a specific service operation.
   *
   * <p>GIVEN: serviceName and operationName
   * WHEN: getSLO is called
   * THEN: Returns Optional with SLOMonitor if found, empty Optional otherwise
   */
  @Override
  public Promise<Optional<SLOMonitor>> getSLO(String serviceName, String operationName) {
    Objects.requireNonNull(serviceName, "serviceName cannot be null");
    Objects.requireNonNull(operationName, "operationName cannot be null");

    synchronized (slosByServiceAndOperation) {
      return Promise.of(
          Optional.ofNullable(
              slosByServiceAndOperation
                  .getOrDefault(serviceName, new HashMap<>())
                  .get(operationName)));
    }
  }

  /**
   * Retrieves all SLO metrics for a service.
   *
   * <p>GIVEN: serviceName
   * WHEN: getServiceSLOs is called
   * THEN: Returns list of all SLOMonitor for that service
   */
  @Override
  public Promise<List<SLOMonitor>> getServiceSLOs(String serviceName) {
    Objects.requireNonNull(serviceName, "serviceName cannot be null");

    synchronized (slosByServiceAndOperation) {
      return Promise.of(
          new ArrayList<>(slosByServiceAndOperation.getOrDefault(serviceName, new HashMap<>()).values()));
    }
  }

  /**
   * Retrieves all SLO metrics across all services.
   *
   * <p>GIVEN: All stored SLOMonitor entries
   * WHEN: getAllSLOs is called
   * THEN: Returns flattened list of all SLOMonitor
   */
  @Override
  public Promise<List<SLOMonitor>> getAllSLOs() {
    synchronized (slosByServiceAndOperation) {
      return Promise.of(
          slosByServiceAndOperation.values().stream()
              .flatMap(map -> map.values().stream())
              .collect(Collectors.toList()));
    }
  }

  /**
   * Retrieves all SLOs with HEALTHY compliance status.
   *
   * <p>GIVEN: All stored SLOMonitor entries
   * WHEN: listHealthySLOs is called
   * THEN: Returns only SLOs with HEALTHY status
   */
  @Override
  public Promise<List<SLOMonitor>> listHealthySLOs() {
    synchronized (slosByServiceAndOperation) {
      return Promise.of(
          slosByServiceAndOperation.values().stream()
              .flatMap(map -> map.values().stream())
              .filter(SLOMonitor::isHealthy)
              .collect(Collectors.toList()));
    }
  }

  /**
   * Retrieves all SLOs with DEGRADED compliance status.
   *
   * <p>GIVEN: All stored SLOMonitor entries
   * WHEN: listDegradedSLOs is called
   * THEN: Returns only SLOs with DEGRADED status
   */
  @Override
  public Promise<List<SLOMonitor>> listDegradedSLOs() {
    synchronized (slosByServiceAndOperation) {
      return Promise.of(
          slosByServiceAndOperation.values().stream()
              .flatMap(map -> map.values().stream())
              .filter(SLOMonitor::isDegraded)
              .collect(Collectors.toList()));
    }
  }

  /**
   * Retrieves all SLOs with UNHEALTHY compliance status.
   *
   * <p>GIVEN: All stored SLOMonitor entries
   * WHEN: listUnhealthySLOs is called
   * THEN: Returns only SLOs with UNHEALTHY status
   */
  @Override
  public Promise<List<SLOMonitor>> listUnhealthySLOs() {
    synchronized (slosByServiceAndOperation) {
      return Promise.of(
          slosByServiceAndOperation.values().stream()
              .flatMap(map -> map.values().stream())
              .filter(SLOMonitor::isUnhealthy)
              .collect(Collectors.toList()));
    }
  }

  /**
   * Gets compliance summary for a service.
   *
   * <p>GIVEN: serviceName
   * WHEN: getComplianceSummary is called
   * THEN: Returns SLOComplianceSummary with counts of HEALTHY/DEGRADED/UNHEALTHY
   */
  @Override
  public Promise<SLOComplianceSummary> getComplianceSummary(String serviceName) {
    Objects.requireNonNull(serviceName, "serviceName cannot be null");

    synchronized (slosByServiceAndOperation) {
      List<SLOMonitor> serviceSLOs =
          new ArrayList<>(slosByServiceAndOperation.getOrDefault(serviceName, new HashMap<>()).values());

      long healthyCount = serviceSLOs.stream().filter(SLOMonitor::isHealthy).count();
      long degradedCount = serviceSLOs.stream().filter(SLOMonitor::isDegraded).count();
      long unhealthyCount = serviceSLOs.stream().filter(SLOMonitor::isUnhealthy).count();

      return Promise.of(
          SLOComplianceSummary.builder()
              .healthyCount((int) healthyCount)
              .degradedCount((int) degradedCount)
              .unhealthyCount((int) unhealthyCount)
              .build());
    }
  }

  /**
   * Clears all SLO metrics.
   *
   * <p>GIVEN: All stored SLOMonitor entries
   * WHEN: clearAll is called
   * THEN: All data is removed from memory
   */
  @Override
  public Promise<Void> clearAll() {
    LOG.warn("DESTRUCTIVE OPERATION: clearAll() called — removing ALL SLO metrics "
            + "across all services. This affects monitoring for every tenant.");

    synchronized (slosByServiceAndOperation) {
      slosByServiceAndOperation.clear();
    }

    return Promise.complete();
  }
}
