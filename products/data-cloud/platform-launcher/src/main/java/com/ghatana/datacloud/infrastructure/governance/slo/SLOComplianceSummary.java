package com.ghatana.datacloud.infrastructure.governance.slo;

import java.util.Objects;

/**
 * Immutable value object representing SLO compliance summary.
 *
 * <p><b>Purpose</b><br>
 * Captures counts of SLOs by compliance status (HEALTHY, DEGRADED, UNHEALTHY)
 * to provide high-level overview of service SLO health.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SLOComplianceSummary summary = SLOComplianceSummary.builder()
 *     .healthyCount(8)
 *     .degradedCount(1)
 *     .unhealthyCount(0)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose SLO compliance summary for service health overview
 * @doc.layer infrastructure
 * @doc.pattern Value Object
 */
public class SLOComplianceSummary {

  private final int healthyCount;
  private final int degradedCount;
  private final int unhealthyCount;

  private SLOComplianceSummary(Builder builder) {
    this.healthyCount = builder.healthyCount;
    this.degradedCount = builder.degradedCount;
    this.unhealthyCount = builder.unhealthyCount;
  }

  public int getHealthyCount() {
    return healthyCount;
  }

  public int getDegradedCount() {
    return degradedCount;
  }

  public int getUnhealthyCount() {
    return unhealthyCount;
  }

  public int getTotalCount() {
    return healthyCount + degradedCount + unhealthyCount;
  }

  public double getHealthyPercentage() {
    int total = getTotalCount();
    return total == 0 ? 0.0 : (healthyCount * 100.0) / total;
  }

  /**
   * Builder for constructing SLOComplianceSummary instances.
   */
  public static class Builder {
    private int healthyCount = 0;
    private int degradedCount = 0;
    private int unhealthyCount = 0;

    public Builder healthyCount(int healthyCount) {
      this.healthyCount = healthyCount;
      return this;
    }

    public Builder degradedCount(int degradedCount) {
      this.degradedCount = degradedCount;
      return this;
    }

    public Builder unhealthyCount(int unhealthyCount) {
      this.unhealthyCount = unhealthyCount;
      return this;
    }

    public SLOComplianceSummary build() {
      return new SLOComplianceSummary(this);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SLOComplianceSummary that = (SLOComplianceSummary) o;
    return healthyCount == that.healthyCount
        && degradedCount == that.degradedCount
        && unhealthyCount == that.unhealthyCount;
  }

  @Override
  public int hashCode() {
    return Objects.hash(healthyCount, degradedCount, unhealthyCount);
  }

  @Override
  public String toString() {
    return "SLOComplianceSummary{"
        + "healthy="
        + healthyCount
        + ", degraded="
        + degradedCount
        + ", unhealthy="
        + unhealthyCount
        + ", total="
        + getTotalCount()
        + ", healthyPercentage="
        + String.format("%.2f", getHealthyPercentage())
        + "%}";
  }
}
