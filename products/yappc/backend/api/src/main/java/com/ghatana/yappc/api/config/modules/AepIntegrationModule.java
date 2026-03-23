/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.yappc.api.aep.AepClient;
import com.ghatana.yappc.api.aep.AepClientFactory;
import com.ghatana.yappc.api.aep.AepConfig;
import com.ghatana.yappc.api.aep.AepException;
import com.ghatana.yappc.api.aep.AepService;
import com.ghatana.yappc.api.outbox.OutboxRelayService;
import com.ghatana.yappc.api.service.LifecycleEventEmitter;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI sub-module for AEP (Agentic Event Processor) integration.
 *
 * <p>Provides AEP client, service, outbox relay, and lifecycle event emitter.
 *
 * @doc.type class
 * @doc.purpose AEP integration DI bindings
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection
 */
public class AepIntegrationModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(AepIntegrationModule.class);

  @Provides
  AepConfig aepConfig() {
    return AepConfig.fromEnvironment("production");
  }

  @Provides
  AepEngine aepEngine() {
    logger.info("Initialising embedded AEP engine");
    return Aep.embedded();
  }

  @Provides
  AepClient aepClient(AepConfig config, AepEngine engine) throws AepException {
    return AepClientFactory.create(config, engine);
  }

  @Provides
  AepService aepService(AepClient client) {
    return new AepService(client);
  }

  /**
   * Provides and starts the transactional outbox relay service.
   *
   * <p>Polls {@code yappc.event_outbox} every 500 ms for {@code PENDING} entries and forwards each
   * to AEP via {@link AepClient#publishEvent}. Marks entries {@code DELIVERED} on success or {@code
   * FAILED} with exponential back-off on error.
   *
   * @doc.type class
   * @doc.purpose Durable domain-event delivery from outbox to AEP
   * @doc.layer product
   * @doc.pattern Scheduler, Transactional Outbox
   */
  @Provides
  OutboxRelayService outboxRelayService(DataSource dataSource, AepClient aepClient) {
    OutboxRelayService relay = new OutboxRelayService(dataSource, aepClient);
    relay.start();
    logger.info("OutboxRelayService started — domain events forwarded to AEP every 500 ms");
    return relay;
  }

  /**
   * Provides {@link LifecycleEventEmitter} for typed YAPPC domain event publishing via AEP.
   *
   * @doc.layer product
   * @doc.pattern Adapter
   */
  @Provides
  LifecycleEventEmitter lifecycleEventEmitter(AepService aepService, ObjectMapper objectMapper) {
    logger.info("Creating LifecycleEventEmitter backed by AEP");
    return new LifecycleEventEmitter(aepService, objectMapper);
  }
}
