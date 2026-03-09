/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */
package com.ghatana.yappc.api.aep;

import java.util.Objects;

/**
 * AEP (Agentic Event Processor) Configuration.
 *
 * <p><b>Purpose</b><br>
 * Manages AEP integration settings for the backend. Supports both library and service modes.
 *
 * <p><b>Configuration Priority</b><br>
 * Environment variables override defaults:
 *
 * <ul>
 *   <li>AEP_MODE - Set to "library" or "service"
 *   <li>AEP_SERVICE_HOST - Hostname/IP of AEP service (SERVICE mode only)
 *   <li>AEP_SERVICE_PORT - Port of AEP service (SERVICE mode only)
 *   <li>AEP_SERVICE_TIMEOUT_MS - Request timeout to AEP service (SERVICE mode)
 *   <li>AEP_LIBRARY_PATH - Path to AEP library JAR (LIBRARY mode only)
 * </ul>
 *
 * @see AepMode
  *
 * @doc.type class
 * @doc.purpose aep config
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class AepConfig {
  private final AepMode mode;
  private final String libraryPath;
  private final String serviceHost;
  private final int servicePort;
  private final long serviceTimeoutMs;

  private AepConfig(Builder builder) {
    this.mode = builder.mode;
    this.libraryPath = builder.libraryPath;
    this.serviceHost = builder.serviceHost;
    this.servicePort = builder.servicePort;
    this.serviceTimeoutMs = builder.serviceTimeoutMs;
  }

  /**
   * Creates AEP config from environment variables or defaults based on environment.
   *
   * @param environment The execution environment (development, staging, production)
   * @return Configured AepConfig instance
   */
  public static AepConfig fromEnvironment(String environment) {
    String modeEnv = System.getenv("AEP_MODE");
    AepMode mode = AepMode.LIBRARY; // Default for development

    if (environment.equals("production") || environment.equals("staging")) {
      mode = AepMode.SERVICE; // Default to service mode for prod/staging
    }

    if (modeEnv != null) {
      mode =
          modeEnv.equalsIgnoreCase("service")
              ? AepMode.SERVICE
              : AepMode.LIBRARY;
    }

    Builder builder = new Builder().mode(mode);

    if (mode == AepMode.LIBRARY) {
      String libraryPath = System.getenv("AEP_LIBRARY_PATH");
      if (libraryPath != null) {
        builder.libraryPath(libraryPath);
      } else {
        builder.libraryPath("./aep-lib.jar");
      }
    } else {
      String serviceHost = System.getenv("AEP_SERVICE_HOST");
      String servicePort = System.getenv("AEP_SERVICE_PORT");
      String serviceTimeout = System.getenv("AEP_SERVICE_TIMEOUT_MS");

      if (serviceHost == null) {
        serviceHost = "localhost";
      }
      if (servicePort == null) {
        servicePort = "7004"; // Default AEP service port
      }
      if (serviceTimeout == null) {
        serviceTimeout = "10000"; // 10s default
      }

      builder
          .serviceHost(serviceHost)
          .servicePort(Integer.parseInt(servicePort))
          .serviceTimeoutMs(Long.parseLong(serviceTimeout));
    }

    return builder.build();
  }

  /**
   * Gets the AEP execution mode.
   *
   * @return LIBRARY or SERVICE
   */
  public AepMode getMode() {
    return mode;
  }

  /**
   * Gets the path to AEP library JAR (LIBRARY mode only).
   *
   * @return Path to AEP library
   */
  public String getLibraryPath() {
    return libraryPath;
  }

  /**
   * Gets the AEP service hostname/IP (SERVICE mode only).
   *
   * @return Hostname or IP address
   */
  public String getServiceHost() {
    return serviceHost;
  }

  /**
   * Gets the AEP service port (SERVICE mode only).
   *
   * @return Port number
   */
  public int getServicePort() {
    return servicePort;
  }

  /**
   * Gets the request timeout for AEP service calls (SERVICE mode only).
   *
   * @return Timeout in milliseconds
   */
  public long getServiceTimeoutMs() {
    return serviceTimeoutMs;
  }

  /**
   * Gets the AEP service URL (SERVICE mode only).
   *
   * @return Full URL like "http://localhost:7004"
   */
  public String getServiceUrl() {
    return String.format("http://%s:%d", serviceHost, servicePort);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AepConfig)) return false;
    AepConfig that = (AepConfig) o;
    return servicePort == that.servicePort
        && serviceTimeoutMs == that.serviceTimeoutMs
        && mode == that.mode
        && Objects.equals(libraryPath, that.libraryPath)
        && Objects.equals(serviceHost, that.serviceHost);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mode, libraryPath, serviceHost, servicePort, serviceTimeoutMs);
  }

  @Override
  public String toString() {
    if (mode == AepMode.LIBRARY) {
      return String.format("AepConfig{mode=LIBRARY, libraryPath=%s}", libraryPath);
    } else {
      return String.format(
          "AepConfig{mode=SERVICE, host=%s:%d, timeout=%dms}",
          serviceHost, servicePort, serviceTimeoutMs);
    }
  }

  // Builder pattern
  public static class Builder {
    private AepMode mode = AepMode.LIBRARY;
    private String libraryPath = "./aep-lib.jar";
    private String serviceHost = "localhost";
    private int servicePort = 7004;
    private long serviceTimeoutMs = 10000;

    public Builder mode(AepMode mode) {
      this.mode = mode;
      return this;
    }

    public Builder libraryPath(String libraryPath) {
      this.libraryPath = libraryPath;
      return this;
    }

    public Builder serviceHost(String serviceHost) {
      this.serviceHost = serviceHost;
      return this;
    }

    public Builder servicePort(int servicePort) {
      this.servicePort = servicePort;
      return this;
    }

    public Builder serviceTimeoutMs(long timeoutMs) {
      this.serviceTimeoutMs = timeoutMs;
      return this;
    }

    public AepConfig build() {
      return new AepConfig(this);
    }
  }
}
