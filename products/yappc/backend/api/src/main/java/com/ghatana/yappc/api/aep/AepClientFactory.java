/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */
package com.ghatana.yappc.api.aep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating AEP clients based on configuration mode.
 *
 * <p><b>Purpose</b><br>
 * Creates appropriate AEP client implementation based on configured mode:
 * - LIBRARY mode → AepLibraryClient (in-process)
 * - SERVICE mode → AepServiceClient (HTTP-based)
 *
 * <p><b>Usage</b><br>
 *
 * <pre>
 * AepConfig config = AepConfig.fromEnvironment(environment);
 * AepClient client = AepClientFactory.create(config);
 * client.publishEvent("shape.created", eventJson);
 * </pre>
 *
 * @see AepConfig
 * @see AepClient
  *
 * @doc.type class
 * @doc.purpose aep client factory
 * @doc.layer product
 * @doc.pattern Factory
 */
public class AepClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(AepClientFactory.class);

  private AepClientFactory() {
    // Factory class, no instantiation
  }

  /**
   * Creates an AEP client based on configuration.
   *
   * @param config The AEP configuration
   * @return AEP client instance (library or service mode)
   * @throws AepException if client creation fails
   */
  public static AepClient create(AepConfig config) throws AepException {
    LOG.info("Creating AEP client: {}", config);

    if (config.getMode() == AepMode.LIBRARY) {
      return createLibraryClient(config);
    } else {
      return createServiceClient(config);
    }
  }

  /**
   * Creates an in-process library client.
   *
   * @param config The AEP configuration
   * @return AEP library client
   * @throws AepException if library loading fails
   */
  private static AepClient createLibraryClient(AepConfig config) throws AepException {
    LOG.info("Initializing AEP in LIBRARY mode from: {}", config.getLibraryPath());

    try {
      return new AepLibraryClient(config.getLibraryPath());
    } catch (Exception e) {
      throw new AepException("Failed to initialize AEP library client", e);
    }
  }

  /**
   * Creates an HTTP-based service client.
   *
   * @param config The AEP configuration
   * @return AEP service client
   * @throws AepException if service connection fails
   */
  private static AepClient createServiceClient(AepConfig config) throws AepException {
    LOG.info("Initializing AEP in SERVICE mode at: {}", config.getServiceUrl());

    try {
      return new AepServiceClient(config);
    } catch (Exception e) {
      throw new AepException("Failed to initialize AEP service client", e);
    }
  }
}
