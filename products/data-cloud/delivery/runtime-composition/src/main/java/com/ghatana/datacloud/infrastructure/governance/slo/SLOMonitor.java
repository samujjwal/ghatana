package com.ghatana.datacloud.infrastructure.governance.slo;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable value object representing SLO (Service Level Objective) metrics.
 *
 * <p><b>Purpose</b><br>
 * Captures SLO compliance state for a service, including response time percentiles,
 * error rate, availability, and custom dimensions (operation, endpoint, tenant).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SLOMonitor slo = SLOMonitor.builder()
 *     .serviceName("governance-service")
 *     .operationName("role-create")
 *     .p50ResponseTimeMs(50)
 *     .p99ResponseTimeMs(150)
 *     .p999ResponseTimeMs(250)
 *     .errorRatePercent(0.5)
 *     .availabilityPercent(99.95)
 *     .dimension("tenant", "tenant-1")
 *     .dimension("endpoint", "POST /roles")
 *     .build();
 * }</pre>
 *
 * <p><b>SLO Targets</b><br>
 * - p50 Response Time: < 100ms (HEALTHY)
 * - p99 Response Time: < 500ms (HEALTHY)
 * - p999 Response Time: < 1000ms (HEALTHY)
 * - Error Rate: < 0.1% (HEALTHY)
 * - Availability: > 99.9% (HEALTHY)
 *
 * <p><b>Compliance Status</b><br>
 * - HEALTHY: All SLOs met
 * - DEGRADED: One SLO warning threshold violated
 * - UNHEALTHY: Multiple SLOs violated or critical SLO breached
 *
 * @doc.type record
 * @doc.purpose SLO metrics value object for compliance tracking
 * @doc.layer infrastructure
 * @doc.pattern Value Object
 */
public class SLOMonitor {

  private final String serviceName;
  private final String operationName;
  private final long p50ResponseTimeMs;
  private final long p99ResponseTimeMs;
  private final long p999ResponseTimeMs;
  private final double errorRatePercent;
  private final double availabilityPercent;
  private final Instant timestamp;
  private final Map<String, String> dimensions;

  private SLOMonitor(Builder builder) {
    this.serviceName = Objects.requireNonNull(builder.serviceName, "serviceName cannot be null");
    this.operationName = Objects.requireNonNull(builder.operationName, "operationName cannot be null");
    this.p50ResponseTimeMs = builder.p50ResponseTimeMs;
    this.p99ResponseTimeMs = builder.p99ResponseTimeMs;
    this.p999ResponseTimeMs = builder.p999ResponseTimeMs;
    this.errorRatePercent = builder.errorRatePercent;
    this.availabilityPercent = builder.availabilityPercent;
    this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
    this.dimensions = Collections.unmodifiableMap(new HashMap<>(builder.dimensions));

    validate();
  }

  private void validate() {
    if (p50ResponseTimeMs < 0) {
      throw new IllegalArgumentException("p50ResponseTimeMs cannot be negative");
    }
    if (p99ResponseTimeMs < p50ResponseTimeMs) {
      throw new IllegalArgumentException("p99ResponseTimeMs must be >= p50ResponseTimeMs");
    }
    if (p999ResponseTimeMs < p99ResponseTimeMs) {
      throw new IllegalArgumentException("p999ResponseTimeMs must be >= p99ResponseTimeMs");
    }
    if (errorRatePercent < 0 || errorRatePercent > 100) {
      throw new IllegalArgumentException("errorRatePercent must be between 0 and 100");
    }
    if (availabilityPercent < 0 || availabilityPercent > 100) {
      throw new IllegalArgumentException("availabilityPercent must be between 0 and 100");
    }
  }

  /**
   * Returns SLO compliance status based on metrics.
   *
   * @return HEALTHY if all targets met, DEGRADED if warnings, UNHEALTHY if critical
   */
  public String getComplianceStatus() {
    boolean p50Healthy = p50ResponseTimeMs < 100;
    boolean p99Healthy = p99ResponseTimeMs < 500;
    boolean p999Healthy = p999ResponseTimeMs < 1000;
    boolean errorHealthy = errorRatePercent < 0.1;
    boolean availabilityHealthy = availabilityPercent > 99.9;

    int violationCount = 0;
    if (!p50Healthy) violationCount++;
    if (!p99Healthy) violationCount++;
    if (!p999Healthy) violationCount++;
    if (!errorHealthy) violationCount++;
    if (!availabilityHealthy) violationCount++;

    if (violationCount == 0) {
      return "HEALTHY";
    } else if (violationCount == 1) {
      return "DEGRADED";
    } else {
      return "UNHEALTHY";
    }
  }

  /**
   * Returns whether this SLO is healthy.
   */
  public boolean isHealthy() {
    return "HEALTHY".equals(getComplianceStatus());
  }

  /**
   * Returns whether this SLO is degraded.
   */
  public boolean isDegraded() {
    return "DEGRADED".equals(getComplianceStatus());
  }

  /**
   * Returns whether this SLO is unhealthy.
   */
  public boolean isUnhealthy() {
    return "UNHEALTHY".equals(getComplianceStatus());
  }

  public String getServiceName() {
    return serviceName;
  }

  public String getOperationName() {
    return operationName;
  }

  public long getP50ResponseTimeMs() {
    return p50ResponseTimeMs;
  }

  public long getP99ResponseTimeMs() {
    return p99ResponseTimeMs;
  }

  public long getP999ResponseTimeMs() {
    return p999ResponseTimeMs;
  }

  public double getErrorRatePercent() {
    return errorRatePercent;
  }

  public double getAvailabilityPercent() {
    return availabilityPercent;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public Map<String, String> getDimensions() {
    return dimensions;
  }

  /**
   * Builder for constructing SLOMonitor instances.
   */
  public static class Builder {
    private String serviceName;
    private String operationName;
    private long p50ResponseTimeMs = 0;
    private long p99ResponseTimeMs = 0;
    private long p999ResponseTimeMs = 0;
    private double errorRatePercent = 0.0;
    private double availabilityPercent = 100.0;
    private Instant timestamp;
    private Map<String, String> dimensions = new HashMap<>();

    public Builder serviceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    public Builder operationName(String operationName) {
      this.operationName = operationName;
      return this;
    }

    public Builder p50ResponseTimeMs(long p50ResponseTimeMs) {
      this.p50ResponseTimeMs = p50ResponseTimeMs;
      return this;
    }

    public Builder p99ResponseTimeMs(long p99ResponseTimeMs) {
      this.p99ResponseTimeMs = p99ResponseTimeMs;
      return this;
    }

    public Builder p999ResponseTimeMs(long p999ResponseTimeMs) {
      this.p999ResponseTimeMs = p999ResponseTimeMs;
      return this;
    }

    public Builder errorRatePercent(double errorRatePercent) {
      this.errorRatePercent = errorRatePercent;
      return this;
    }

    public Builder availabilityPercent(double availabilityPercent) {
      this.availabilityPercent = availabilityPercent;
      return this;
    }

    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder dimension(String key, String value) {
      this.dimensions.put(key, value);
      return this;
    }

    public SLOMonitor build() {
      return new SLOMonitor(this);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SLOMonitor that = (SLOMonitor) o;
    return p50ResponseTimeMs == that.p50ResponseTimeMs
        && p99ResponseTimeMs == that.p99ResponseTimeMs
        && p999ResponseTimeMs == that.p999ResponseTimeMs
        && Double.compare(that.errorRatePercent, errorRatePercent) == 0
        && Double.compare(that.availabilityPercent, availabilityPercent) == 0
        && Objects.equals(serviceName, that.serviceName)
        && Objects.equals(operationName, that.operationName)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(dimensions, that.dimensions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        serviceName,
        operationName,
        p50ResponseTimeMs,
        p99ResponseTimeMs,
        p999ResponseTimeMs,
        errorRatePercent,
        availabilityPercent,
        timestamp,
        dimensions);
  }

  @Override
  public String toString() {
    return "SLOMonitor{"
        + "serviceName='"
        + serviceName
        + '\''
        + ", operationName='"
        + operationName
        + '\''
        + ", p50ResponseTimeMs="
        + p50ResponseTimeMs
        + ", p99ResponseTimeMs="
        + p99ResponseTimeMs
        + ", p999ResponseTimeMs="
        + p999ResponseTimeMs
        + ", errorRatePercent="
        + errorRatePercent
        + ", availabilityPercent="
        + availabilityPercent
        + ", timestamp="
        + timestamp
        + ", dimensions="
        + dimensions
        + ", status="
        + getComplianceStatus()
        + '}';
  }
}
