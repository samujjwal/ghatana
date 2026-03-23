/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.aep;

import com.ghatana.aep.AepEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating AEP clients based on configuration mode.
 *
 * <p><b>Purpose</b><br>
 * Creates appropriate AEP client implementation based on configured mode: - LIBRARY mode →
 * AepLibraryClient (in-process) - SERVICE mode → AepServiceClient (HTTP-based)
 *
 * <p><b>Usage</b><br>
 *
 * <pre>
 * AepConfig config = AepConfig.fromEnvironment(environment);
 * AepEngine engine = Aep.embedded(); // provided by DI composition root
 * AepClient client = AepClientFactory.create(config, engine);
 * client.publishEvent("shape.created", eventJson);
 * </pre>
 *
 * @see AepConfig
 * @see AepClient
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
   * @param engine The AEP engine (used for LIBRARY mode; provided by DI composition root)
   * @return AEP client instance (library or service mode)
   * @throws AepException if client creation fails
   */
  public static AepClient create(AepConfig config, AepEngine engine) throws AepException {
    LOG.info("Creating AEP client: {}", config);

    if (config.getMode() == AepMode.LIBRARY) {
      return createLibraryClient(engine);
    } else {
      return createServiceClient(config);
    }
  }

  /**
   * Creates an in-process library client backed by the provided engine.
   *
   * @param engine the AEP engine
   * @return AEP library client
   * @throws AepException if client creation fails
   */
  private static AepClient createLibraryClient(AepEngine engine) throws AepException {
    try {
      return new AepLibraryClient(engine);
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
