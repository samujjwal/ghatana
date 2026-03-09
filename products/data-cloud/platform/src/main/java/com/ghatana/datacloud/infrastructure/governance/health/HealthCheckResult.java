package com.ghatana.datacloud.infrastructure.governance.health;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable health check result for a service component.
 *
 * <p><b>Purpose</b><br>
 * Represents the result of a single health check operation, including
 * status (HEALTHY/DEGRADED/UNHEALTHY), timestamp, response time, and
 * diagnostic details. Used by health check endpoints to report component health.
 *
 * <p><b>Status Values</b><br>
 * - HEALTHY: Component fully operational
 * - DEGRADED: Component operational but with reduced capacity
 * - UNHEALTHY: Component non-operational, cannot be used
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * HealthCheckResult result = HealthCheckResult.builder()
 *     .componentName("role-repository")
 *     .status("HEALTHY")
 *     .responseTimeMs(25)
 *     .detail("connections_active", "42")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Health check result value object
 * @doc.layer infrastructure
 * @doc.pattern Value Object
 */
public final class HealthCheckResult {

  private final String componentName;
  private final String status;
  private final Instant timestamp;
  private final long responseTimeMs;
  private final Map<String, String> details;

  /**
   * Constructs HealthCheckResult with all parameters.
   *
   * @param componentName name of component being checked
   * @param status health status (HEALTHY/DEGRADED/UNHEALTHY)
   * @param timestamp when check was performed
   * @param responseTimeMs check execution time in milliseconds
   * @param details diagnostic details map
   * @throws NullPointerException if required parameters are null
   */
  private HealthCheckResult(
      String componentName,
      String status,
      Instant timestamp,
      long responseTimeMs,
      Map<String, String> details) {
    this.componentName = Objects.requireNonNull(componentName, "componentName");
    this.status = Objects.requireNonNull(status, "status");
    this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
    this.responseTimeMs = responseTimeMs;
    this.details = Collections.unmodifiableMap(new HashMap<>(details));

    validateStatus();
  }

  /**
   * Validates status is one of allowed values.
   *
   * @throws IllegalArgumentException if status invalid
   */
  private void validateStatus() {
    if (!status.matches("^(HEALTHY|DEGRADED|UNHEALTHY)$")) {
      throw new IllegalArgumentException("Invalid status: " + status);
    }
  }

  /**
   * Creates a new builder for HealthCheckResult.
   *
   * @return builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets component name.
   *
   * @return component name
   */
  public String getComponentName() {
    return componentName;
  }

  /**
   * Gets health status.
   *
   * @return HEALTHY/DEGRADED/UNHEALTHY
   */
  public String getStatus() {
    return status;
  }

  /**
   * Gets check timestamp.
   *
   * @return instant check was performed
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Gets check response time in milliseconds.
   *
   * @return response time
   */
  public long getResponseTimeMs() {
    return responseTimeMs;
  }

  /**
   * Gets diagnostic details.
   *
   * @return immutable details map
   */
  public Map<String, String> getDetails() {
    return details;
  }

  /**
   * Checks if component is healthy.
   *
   * @return true if HEALTHY status
   */
  public boolean isHealthy() {
    return "HEALTHY".equals(status);
  }

  /**
   * Checks if component is degraded.
   *
   * @return true if DEGRADED status
   */
  public boolean isDegraded() {
    return "DEGRADED".equals(status);
  }

  /**
   * Checks if component is unhealthy.
   *
   * @return true if UNHEALTHY status
   */
  public boolean isUnhealthy() {
    return "UNHEALTHY".equals(status);
  }

  /**
   * Builder for HealthCheckResult.
   *
   * <p>Provides fluent API for constructing health check results.
   * Timestamp defaults to Instant.now() if not specified.
   */
  public static class Builder {

    private String componentName;
    private String status = "HEALTHY";
    private Instant timestamp = Instant.now();
    private long responseTimeMs = 0;
    private Map<String, String> details = new HashMap<>();

    /**
     * Sets component name.
     *
     * @param name component name
     * @return this builder
     */
    public Builder componentName(String name) {
      this.componentName = name;
      return this;
    }

    /**
     * Sets health status.
     *
     * @param stat status value
     * @return this builder
     */
    public Builder status(String stat) {
      this.status = stat;
      return this;
    }

    /**
     * Sets check timestamp.
     *
     * @param time instant check performed
     * @return this builder
     */
    public Builder timestamp(Instant time) {
      this.timestamp = time;
      return this;
    }

    /**
     * Sets response time in milliseconds.
     *
     * @param ms response time
     * @return this builder
     */
    public Builder responseTimeMs(long ms) {
      this.responseTimeMs = ms;
      return this;
    }

    /**
     * Adds a detail key-value pair.
     *
     * @param key detail key
     * @param value detail value
     * @return this builder
     */
    public Builder detail(String key, String value) {
      this.details.put(key, value);
      return this;
    }

    /**
     * Sets all details at once.
     *
     * @param detailsMap details map
     * @return this builder
     */
    public Builder details(Map<String, String> detailsMap) {
      this.details = new HashMap<>(detailsMap);
      return this;
    }

    /**
     * Builds HealthCheckResult instance.
     *
     * @return constructed HealthCheckResult
     * @throws NullPointerException if required fields null
     * @throws IllegalArgumentException if validation fails
     */
    public HealthCheckResult build() {
      if (componentName == null) {
        throw new NullPointerException("componentName required");
      }
      return new HealthCheckResult(componentName, status, timestamp, responseTimeMs, details);
    }
  }

  @Override
  public String toString() {
    return "HealthCheckResult{"
        + "componentName='"
        + componentName
        + '\''
        + ", status='"
        + status
        + '\''
        + ", timestamp="
        + timestamp
        + ", responseTimeMs="
        + responseTimeMs
        + ", details="
        + details
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof HealthCheckResult)) return false;
    HealthCheckResult that = (HealthCheckResult) o;
    return responseTimeMs == that.responseTimeMs
        && componentName.equals(that.componentName)
        && status.equals(that.status)
        && timestamp.equals(that.timestamp)
        && details.equals(that.details);
  }

  @Override
  public int hashCode() {
    return Objects.hash(componentName, status, timestamp, responseTimeMs, details);
  }
}
