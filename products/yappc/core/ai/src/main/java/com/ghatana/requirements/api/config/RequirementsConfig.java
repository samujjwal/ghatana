package com.ghatana.requirements.api.config;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Configuration for the AI Requirements application.
 *
 * <p><b>Purpose:</b> Centralizes all configuration values from environment
 * variables, system properties, and defaults.
 *
 * <p><b>Configuration Sources (in order of precedence):</b>
 * <ol>
 *   <li>Environment variables (REQUIREMENTS_*)
 *   <li>System properties (requirements.*)
 *   <li>Defaults
 * </ol>
 *
 * <p><b>Key Settings:</b>
 * <ul>
 *   <li>{@code SERVER_PORT} - HTTP server port (default: 8080)
 *   <li>{@code SERVER_HOST} - Bind address (default: 0.0.0.0)
 *   <li>{@code DB_URL} - PostgreSQL JDBC URL
 *   <li>{@code OPENAI_API_KEY} - OpenAI API key
 *   <li>{@code JWT_SECRET} - JWT signing secret
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Application configuration
 * @doc.layer product
 * @doc.pattern Configuration
 * @since 1.0.0
 */
public final class RequirementsConfig {
  private static final String ENV_PREFIX = "REQUIREMENTS_";
  private static final String PROP_PREFIX = "requirements.";

  // Server Configuration
  private final int serverPort;
  private final String serverHost;
  private final InetAddress serverAddress;

  // Database Configuration
  private final String dbUrl;
  private final String dbUser;
  private final String dbPassword;

  // AI Configuration
  private final String openaiApiKey;
  private final String openaiModel;

  // Security Configuration
  private final String jwtSecret;
  private final long jwtExpirationMs;
  private final long jwtRefreshExpirationMs;

  // Feature Flags
  private final boolean corsEnabled;
  private final boolean graphqlEnabled;
  private final String graphqlPlaygroundPath;

  /**
   * Create configuration from environment and system properties.
   */
  public RequirementsConfig() {
    this.serverPort = getIntConfig("SERVER_PORT", 8080);
    this.serverHost = getStringConfig("SERVER_HOST", "0.0.0.0");
    this.serverAddress = resolveAddress(serverHost);

    this.dbUrl = getStringConfig("DB_URL", "jdbc:postgresql://localhost:5432/ai_requirements");
    this.dbUser = getStringConfig("DB_USER", "postgres");
    this.dbPassword = getStringConfig("DB_PASSWORD", "");

    this.openaiApiKey = getStringConfig("OPENAI_API_KEY", "");
    this.openaiModel = getStringConfig("OPENAI_MODEL", "gpt-4");

    this.jwtSecret = getStringConfig("JWT_SECRET", "dev-secret-key-change-in-production");
    this.jwtExpirationMs = getLongConfig("JWT_EXPIRATION_MS", 3600000); // 1 hour
    this.jwtRefreshExpirationMs = getLongConfig("JWT_REFRESH_EXPIRATION_MS", 604800000); // 7 days

    this.corsEnabled = getBoolConfig("CORS_ENABLED", true);
    this.graphqlEnabled = getBoolConfig("GRAPHQL_ENABLED", true);
    this.graphqlPlaygroundPath =
        getStringConfig("GRAPHQL_PLAYGROUND_PATH", "/graphql-playground");
  }

  /**
   * Get string configuration value.
   *
   * @param key configuration key
   * @param defaultValue default if not found
   * @return configuration value
   */
  private static String getStringConfig(String key, String defaultValue) {
    // Check environment variable
    String envValue = System.getenv(ENV_PREFIX + key);
    if (envValue != null && !envValue.isEmpty()) {
      return envValue;
    }

    // Check system property
    String propValue = System.getProperty(PROP_PREFIX + key.toLowerCase());
    if (propValue != null && !propValue.isEmpty()) {
      return propValue;
    }

    return defaultValue;
  }

  /**
   * Get integer configuration value.
   *
   * @param key configuration key
   * @param defaultValue default if not found
   * @return configuration value
   */
  private static int getIntConfig(String key, int defaultValue) {
    String value = getStringConfig(key, null);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  /**
   * Get long configuration value.
   *
   * @param key configuration key
   * @param defaultValue default if not found
   * @return configuration value
   */
  private static long getLongConfig(String key, long defaultValue) {
    String value = getStringConfig(key, null);
    if (value != null) {
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  /**
   * Get boolean configuration value.
   *
   * @param key configuration key
   * @param defaultValue default if not found
   * @return configuration value
   */
  private static boolean getBoolConfig(String key, boolean defaultValue) {
    String value = getStringConfig(key, null);
    if (value != null) {
      return "true".equalsIgnoreCase(value) || "1".equals(value);
    }
    return defaultValue;
  }

  /**
   * Resolve hostname to InetAddress.
   *
   * @param host hostname or IP address
   * @return resolved address
   */
  private static InetAddress resolveAddress(String host) {
    try {
      return InetAddress.getByName(host);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Invalid server host: " + host, e);
    }
  }

  // Getters

  public int getServerPort() {
    return serverPort;
  }

  public String getServerHost() {
    return serverHost;
  }

  public InetAddress getServerAddress() {
    return serverAddress;
  }

  public String getDbUrl() {
    return dbUrl;
  }

  public String getDbUser() {
    return dbUser;
  }

  public String getDbPassword() {
    return dbPassword;
  }

  public String getOpenaiApiKey() {
    return openaiApiKey;
  }

  public String getOpenaiModel() {
    return openaiModel;
  }

  public String getJwtSecret() {
    return jwtSecret;
  }

  public long getJwtExpirationMs() {
    return jwtExpirationMs;
  }

  public long getJwtRefreshExpirationMs() {
    return jwtRefreshExpirationMs;
  }

  public boolean isCorsEnabled() {
    return corsEnabled;
  }

  public boolean isGraphqlEnabled() {
    return graphqlEnabled;
  }

  public String getGraphqlPlaygroundPath() {
    return graphqlPlaygroundPath;
  }
}