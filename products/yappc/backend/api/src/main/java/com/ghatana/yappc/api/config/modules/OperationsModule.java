/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.operations.AlertController;
import com.ghatana.yappc.api.operations.IncidentController;
import com.ghatana.yappc.api.operations.LogController;
import com.ghatana.yappc.api.operations.MetricController;
import com.ghatana.yappc.api.operations.TraceController;
import com.ghatana.yappc.api.repository.AlertRepository;
import com.ghatana.yappc.api.repository.IncidentRepository;
import com.ghatana.yappc.api.repository.LogEntryRepository;
import com.ghatana.yappc.api.repository.MetricRepository;
import com.ghatana.yappc.api.repository.TraceRepository;
import com.ghatana.yappc.api.service.AlertService;
import com.ghatana.yappc.api.service.IncidentService;
import com.ghatana.yappc.api.service.LogService;
import com.ghatana.yappc.api.service.MetricService;
import com.ghatana.yappc.api.service.NotificationService;
import com.ghatana.yappc.api.service.TraceService;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI sub-module for operations domain (metrics, alerts, incidents, logs, traces).
 *
 * @doc.type class
 * @doc.purpose Operations services and controllers DI bindings
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection
 */
public class OperationsModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(OperationsModule.class);

  // ========== Services ==========

  @Provides
  MetricService metricService(MetricRepository repository, AuditService auditService) {
    logger.info("Creating MetricService");
    return new MetricService(repository, auditService);
  }

  @Provides
  AlertService alertService(
      AlertRepository repository,
      AuditService auditService,
      NotificationService notificationService) {
    logger.info("Creating AlertService");
    return new AlertService(repository);
  }

  @Provides
  IncidentService incidentService(
      IncidentRepository repository,
      AuditService auditService,
      NotificationService notificationService) {
    logger.info("Creating IncidentService");
    return new IncidentService(repository);
  }

  @Provides
  LogService logService(LogEntryRepository repository, AuditService auditService) {
    logger.info("Creating LogService");
    return new LogService(repository, auditService);
  }

  @Provides
  TraceService traceService(TraceRepository repository, AuditService auditService) {
    logger.info("Creating TraceService");
    return new TraceService(repository, auditService);
  }

  // ========== Controllers ==========

  @Provides
  MetricController metricController(MetricService metricService, ObjectMapper objectMapper) {
    logger.info("Creating MetricController");
    return new MetricController(metricService, objectMapper);
  }

  @Provides
  AlertController alertController(AlertService alertService, ObjectMapper objectMapper) {
    logger.info("Creating AlertController");
    return new AlertController(alertService, objectMapper);
  }

  @Provides
  IncidentController incidentController(
      IncidentService incidentService, ObjectMapper objectMapper) {
    logger.info("Creating IncidentController");
    return new IncidentController(incidentService, objectMapper);
  }

  @Provides
  LogController logController(LogService logService, ObjectMapper objectMapper) {
    logger.info("Creating LogController");
    return new LogController(logService, objectMapper);
  }

  @Provides
  TraceController traceController(TraceService traceService, ObjectMapper objectMapper) {
    logger.info("Creating TraceController");
    return new TraceController(traceService, objectMapper);
  }
}
