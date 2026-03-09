package com.ghatana.datacloud.infrastructure.governance.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for Collection Entity System governance layer.
 *
 * <p><b>Purpose</b><br>
 * Holds all governance-related configuration including RBAC settings,
 * role inheritance limits, permission validation, and SLO targets.
 * Configuration is loaded from environment variables or properties files
 * and is immutable after construction.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ApplicationConfig config = ApplicationConfig.builder()
 *     .maxRoleInheritanceDepth(5)
 *     .enableCycleDetection(true)
 *     .roleCacheTtl(Duration.ofMinutes(10))
 *     .build();
 * }</pre>
 *
 * <p><b>Configuration Sources</b><br>
 * - Environment variables (prefix: COLLECTION_)
 * - System properties
 * - Default values if not specified
 * - Validation enforces constraints (depth > 0, TTL > 0)
 *
 * @doc.type class
 * @doc.purpose Governance configuration holder
 * @doc.layer infrastructure
 * @doc.pattern Configuration
 */
public final class ApplicationConfig {

  private final int maxRoleInheritanceDepth;
  private final boolean enableCycleDetection;
  private final Duration roleCacheTtl;
  private final int maxRoleAssignmentsPerPrincipal;
  private final int maxPermissionsPerRole;
  private final boolean enableAuditLogging;
  private final boolean enableMetrics;
  private final int metricsCollectionIntervalSeconds;

  /**
   * Constructs ApplicationConfig with validated parameters.
   *
   * @param maxRoleInheritanceDepth Maximum depth of role inheritance chain
   * @param enableCycleDetection Whether to detect cycles in role inheritance
   * @param roleCacheTtl TTL for role cache
   * @param maxRoleAssignmentsPerPrincipal Maximum assignments per principal
   * @param maxPermissionsPerRole Maximum permissions per role
   * @param enableAuditLogging Whether to log all role operations
   * @param enableMetrics Whether to collect metrics
   * @param metricsCollectionIntervalSeconds Interval for metrics collection
   * @throws IllegalArgumentException if any numeric parameter is invalid
   */
  private ApplicationConfig(
      int maxRoleInheritanceDepth,
      boolean enableCycleDetection,
      Duration roleCacheTtl,
      int maxRoleAssignmentsPerPrincipal,
      int maxPermissionsPerRole,
      boolean enableAuditLogging,
      boolean enableMetrics,
      int metricsCollectionIntervalSeconds) {
    this.maxRoleInheritanceDepth = maxRoleInheritanceDepth;
    this.enableCycleDetection = enableCycleDetection;
    this.roleCacheTtl = Objects.requireNonNull(roleCacheTtl, "roleCacheTtl");
    this.maxRoleAssignmentsPerPrincipal = maxRoleAssignmentsPerPrincipal;
    this.maxPermissionsPerRole = maxPermissionsPerRole;
    this.enableAuditLogging = enableAuditLogging;
    this.enableMetrics = enableMetrics;
    this.metricsCollectionIntervalSeconds = metricsCollectionIntervalSeconds;

    validateConfig();
  }

  /**
   * Validates configuration constraints.
   *
   * @throws IllegalArgumentException if constraints violated
   */
  private void validateConfig() {
    if (maxRoleInheritanceDepth <= 0) {
      throw new IllegalArgumentException("maxRoleInheritanceDepth must be > 0");
    }
    if (maxRoleAssignmentsPerPrincipal <= 0) {
      throw new IllegalArgumentException("maxRoleAssignmentsPerPrincipal must be > 0");
    }
    if (maxPermissionsPerRole <= 0) {
      throw new IllegalArgumentException("maxPermissionsPerRole must be > 0");
    }
    if (metricsCollectionIntervalSeconds <= 0) {
      throw new IllegalArgumentException("metricsCollectionIntervalSeconds must be > 0");
    }
    if (roleCacheTtl.isNegative() || roleCacheTtl.isZero()) {
      throw new IllegalArgumentException("roleCacheTtl must be positive");
    }
  }

  /**
   * Creates a new builder for ApplicationConfig.
   *
   * @return builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets maximum role inheritance depth.
   *
   * @return depth (default: 5)
   */
  public int getMaxRoleInheritanceDepth() {
    return maxRoleInheritanceDepth;
  }

  /**
   * Checks if cycle detection is enabled.
   *
   * @return true if enabled
   */
  public boolean isEnableCycleDetection() {
    return enableCycleDetection;
  }

  /**
   * Gets role cache TTL.
   *
   * @return TTL duration
   */
  public Duration getRoleCacheTtl() {
    return roleCacheTtl;
  }

  /**
   * Gets maximum role assignments per principal.
   *
   * @return count (default: 50)
   */
  public int getMaxRoleAssignmentsPerPrincipal() {
    return maxRoleAssignmentsPerPrincipal;
  }

  /**
   * Gets maximum permissions per role.
   *
   * @return count (default: 100)
   */
  public int getMaxPermissionsPerRole() {
    return maxPermissionsPerRole;
  }

  /**
   * Checks if audit logging is enabled.
   *
   * @return true if enabled
   */
  public boolean isEnableAuditLogging() {
    return enableAuditLogging;
  }

  /**
   * Checks if metrics collection is enabled.
   *
   * @return true if enabled
   */
  public boolean isEnableMetrics() {
    return enableMetrics;
  }

  /**
   * Gets metrics collection interval in seconds.
   *
   * @return interval in seconds
   */
  public int getMetricsCollectionIntervalSeconds() {
    return metricsCollectionIntervalSeconds;
  }

  /**
   * Builder for ApplicationConfig.
   *
   * <p>Provides fluent API for constructing configuration with sensible defaults.
   * All numeric parameters default to reasonable values, TTL defaults to 10 minutes.
   */
  public static class Builder {

    private int maxRoleInheritanceDepth = 5;
    private boolean enableCycleDetection = true;
    private Duration roleCacheTtl = Duration.ofMinutes(10);
    private int maxRoleAssignmentsPerPrincipal = 50;
    private int maxPermissionsPerRole = 100;
    private boolean enableAuditLogging = true;
    private boolean enableMetrics = true;
    private int metricsCollectionIntervalSeconds = 60;

    /**
     * Sets maximum role inheritance depth.
     *
     * @param depth depth value
     * @return this builder
     */
    public Builder maxRoleInheritanceDepth(int depth) {
      this.maxRoleInheritanceDepth = depth;
      return this;
    }

    /**
     * Sets whether cycle detection is enabled.
     *
     * @param enabled true to enable
     * @return this builder
     */
    public Builder enableCycleDetection(boolean enabled) {
      this.enableCycleDetection = enabled;
      return this;
    }

    /**
     * Sets role cache TTL.
     *
     * @param ttl TTL duration
     * @return this builder
     */
    public Builder roleCacheTtl(Duration ttl) {
      this.roleCacheTtl = ttl;
      return this;
    }

    /**
     * Sets maximum role assignments per principal.
     *
     * @param max maximum count
     * @return this builder
     */
    public Builder maxRoleAssignmentsPerPrincipal(int max) {
      this.maxRoleAssignmentsPerPrincipal = max;
      return this;
    }

    /**
     * Sets maximum permissions per role.
     *
     * @param max maximum count
     * @return this builder
     */
    public Builder maxPermissionsPerRole(int max) {
      this.maxPermissionsPerRole = max;
      return this;
    }

    /**
     * Sets whether audit logging is enabled.
     *
     * @param enabled true to enable
     * @return this builder
     */
    public Builder enableAuditLogging(boolean enabled) {
      this.enableAuditLogging = enabled;
      return this;
    }

    /**
     * Sets whether metrics collection is enabled.
     *
     * @param enabled true to enable
     * @return this builder
     */
    public Builder enableMetrics(boolean enabled) {
      this.enableMetrics = enabled;
      return this;
    }

    /**
     * Sets metrics collection interval in seconds.
     *
     * @param seconds interval in seconds
     * @return this builder
     */
    public Builder metricsCollectionIntervalSeconds(int seconds) {
      this.metricsCollectionIntervalSeconds = seconds;
      return this;
    }

    /**
     * Builds ApplicationConfig instance.
     *
     * @return configured ApplicationConfig
     * @throws IllegalArgumentException if validation fails
     */
    public ApplicationConfig build() {
      return new ApplicationConfig(
          maxRoleInheritanceDepth,
          enableCycleDetection,
          roleCacheTtl,
          maxRoleAssignmentsPerPrincipal,
          maxPermissionsPerRole,
          enableAuditLogging,
          enableMetrics,
          metricsCollectionIntervalSeconds);
    }
  }

  @Override
  public String toString() {
    return "ApplicationConfig{"
        + "maxRoleInheritanceDepth="
        + maxRoleInheritanceDepth
        + ", enableCycleDetection="
        + enableCycleDetection
        + ", roleCacheTtl="
        + roleCacheTtl
        + ", maxRoleAssignmentsPerPrincipal="
        + maxRoleAssignmentsPerPrincipal
        + ", maxPermissionsPerRole="
        + maxPermissionsPerRole
        + ", enableAuditLogging="
        + enableAuditLogging
        + ", enableMetrics="
        + enableMetrics
        + ", metricsCollectionIntervalSeconds="
        + metricsCollectionIntervalSeconds
        + '}';
  }
}
