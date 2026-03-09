package com.ghatana.datacloud.infrastructure.governance.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration manager for Collection Entity System governance.
 *
 * <p><b>Purpose</b><br>
 * Centralizes loading and management of application configuration from
 * environment variables, system properties, and defaults. Provides methods
 * to validate configuration and reload at runtime.
 *
 * <p><b>Configuration Loading Order</b><br>
 * 1. Environment variables (COLLECTION_* prefix)
 * 2. System properties (collection.* prefix)
 * 3. Default values (hardcoded)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ConfigurationManager configMgr = new ConfigurationManager();
 * ApplicationConfig config = configMgr.loadConfiguration();
 * int maxDepth = config.getMaxRoleInheritanceDepth();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Configuration loading and management
 * @doc.layer infrastructure
 * @doc.pattern Configuration
 */
public final class ConfigurationManager {

  private static final String ENV_PREFIX = "COLLECTION_";
  private static final String PROP_PREFIX = "collection.";

  private ApplicationConfig currentConfig;

  /**
   * Constructs ConfigurationManager and loads initial configuration.
   */
  public ConfigurationManager() {
    this.currentConfig = loadConfiguration();
  }

  /**
   * Loads configuration from environment and properties.
   *
   * @return loaded ApplicationConfig
   */
  public ApplicationConfig loadConfiguration() {
    ApplicationConfig.Builder builder = ApplicationConfig.builder();

    // Load max role inheritance depth
    int maxDepth = getIntProperty("MAX_ROLE_INHERITANCE_DEPTH", 5, 1, 100);
    builder.maxRoleInheritanceDepth(maxDepth);

    // Load cycle detection flag
    boolean cycleDetection =
        getBooleanProperty("ENABLE_CYCLE_DETECTION", true);
    builder.enableCycleDetection(cycleDetection);

    // Load role cache TTL
    long ttlSeconds = getLongProperty("ROLE_CACHE_TTL_SECONDS", 600, 60, 3600);
    builder.roleCacheTtl(Duration.ofSeconds(ttlSeconds));

    // Load max role assignments per principal
    int maxAssignments = getIntProperty("MAX_ROLE_ASSIGNMENTS_PER_PRINCIPAL", 50, 1, 500);
    builder.maxRoleAssignmentsPerPrincipal(maxAssignments);

    // Load max permissions per role
    int maxPerms = getIntProperty("MAX_PERMISSIONS_PER_ROLE", 100, 1, 1000);
    builder.maxPermissionsPerRole(maxPerms);

    // Load audit logging flag
    boolean auditLogging = getBooleanProperty("ENABLE_AUDIT_LOGGING", true);
    builder.enableAuditLogging(auditLogging);

    // Load metrics collection flag
    boolean metricsEnabled = getBooleanProperty("ENABLE_METRICS", true);
    builder.enableMetrics(metricsEnabled);

    // Load metrics interval
    int metricsInterval = getIntProperty("METRICS_COLLECTION_INTERVAL_SECONDS", 60, 10, 3600);
    builder.metricsCollectionIntervalSeconds(metricsInterval);

    return builder.build();
  }

  /**
   * Reloads configuration from environment.
   *
   * @return reloaded ApplicationConfig
   */
  public ApplicationConfig reloadConfiguration() {
    this.currentConfig = loadConfiguration();
    return this.currentConfig;
  }

  /**
   * Gets current configuration.
   *
   * @return current ApplicationConfig
   */
  public ApplicationConfig getConfiguration() {
    return currentConfig;
  }

  /**
   * Gets integer property from environment or system properties.
   *
   * @param name property name (without prefix)
   * @param defaultValue default value if not found
   * @param min minimum allowed value
   * @param max maximum allowed value
   * @return property value or default
   */
  private int getIntProperty(String name, int defaultValue, int min, int max) {
    String envVar = System.getenv(ENV_PREFIX + name);
    if (envVar != null && !envVar.isEmpty()) {
      try {
        int value = Integer.parseInt(envVar);
        if (value >= min && value <= max) {
          return value;
        }
      } catch (NumberFormatException e) {
        // Fall through to system property
      }
    }

    String propValue = System.getProperty(PROP_PREFIX + name.toLowerCase());
    if (propValue != null && !propValue.isEmpty()) {
      try {
        int value = Integer.parseInt(propValue);
        if (value >= min && value <= max) {
          return value;
        }
      } catch (NumberFormatException e) {
        // Fall through to default
      }
    }

    return defaultValue;
  }

  /**
   * Gets long property from environment or system properties.
   *
   * @param name property name (without prefix)
   * @param defaultValue default value if not found
   * @param min minimum allowed value
   * @param max maximum allowed value
   * @return property value or default
   */
  private long getLongProperty(String name, long defaultValue, long min, long max) {
    String envVar = System.getenv(ENV_PREFIX + name);
    if (envVar != null && !envVar.isEmpty()) {
      try {
        long value = Long.parseLong(envVar);
        if (value >= min && value <= max) {
          return value;
        }
      } catch (NumberFormatException e) {
        // Fall through to system property
      }
    }

    String propValue = System.getProperty(PROP_PREFIX + name.toLowerCase());
    if (propValue != null && !propValue.isEmpty()) {
      try {
        long value = Long.parseLong(propValue);
        if (value >= min && value <= max) {
          return value;
        }
      } catch (NumberFormatException e) {
        // Fall through to default
      }
    }

    return defaultValue;
  }

  /**
   * Gets boolean property from environment or system properties.
   *
   * @param name property name (without prefix)
   * @param defaultValue default value if not found
   * @return property value or default
   */
  private boolean getBooleanProperty(String name, boolean defaultValue) {
    String envVar = System.getenv(ENV_PREFIX + name);
    if (envVar != null && !envVar.isEmpty()) {
      return Boolean.parseBoolean(envVar);
    }

    String propValue = System.getProperty(PROP_PREFIX + name.toLowerCase());
    if (propValue != null && !propValue.isEmpty()) {
      return Boolean.parseBoolean(propValue);
    }

    return defaultValue;
  }

  @Override
  public String toString() {
    return "ConfigurationManager{" + "currentConfig=" + currentConfig + '}';
  }
}
