/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.repository.ComplianceRepository;
import com.ghatana.yappc.api.repository.SecurityScanRepository;
import com.ghatana.yappc.api.repository.VulnerabilityRepository;
import com.ghatana.yappc.api.security.ComplianceController;
import com.ghatana.yappc.api.security.SecurityScanController;
import com.ghatana.yappc.api.security.VulnerabilityController;
import com.ghatana.yappc.api.service.ComplianceService;
import com.ghatana.yappc.api.service.NotificationService;
import com.ghatana.yappc.api.service.SecurityScanService;
import com.ghatana.yappc.api.service.VulnerabilityService;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI sub-module for security domain (vulnerabilities, scans, compliance).
 *
 * @doc.type class
 * @doc.purpose Security services and controllers DI bindings
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection
 */
public class SecurityModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(SecurityModule.class);

  // ========== Services ==========

  @Provides
  VulnerabilityService vulnerabilityService(
      VulnerabilityRepository repository,
      AuditService auditService,
      NotificationService notificationService) {
    logger.info("Creating VulnerabilityService");
    return new VulnerabilityService(repository, auditService, notificationService);
  }

  @Provides
  SecurityScanService securityScanService(
      SecurityScanRepository repository, AuditService auditService) {
    logger.info("Creating SecurityScanService");
    return new SecurityScanService(repository);
  }

  @Provides
  ComplianceService complianceService(ComplianceRepository repository, AuditService auditService) {
    logger.info("Creating ComplianceService");
    return new ComplianceService(repository);
  }

  // ========== Controllers ==========

  @Provides
  VulnerabilityController vulnerabilityController(
      VulnerabilityService vulnerabilityService, ObjectMapper objectMapper) {
    logger.info("Creating VulnerabilityController");
    return new VulnerabilityController(vulnerabilityService, objectMapper);
  }

  @Provides
  SecurityScanController securityScanController(
      SecurityScanService securityScanService, ObjectMapper objectMapper) {
    logger.info("Creating SecurityScanController");
    return new SecurityScanController(securityScanService, objectMapper);
  }

  @Provides
  ComplianceController complianceController(
      ComplianceService complianceService, ObjectMapper objectMapper) {
    logger.info("Creating ComplianceController");
    return new ComplianceController(complianceService, objectMapper);
  }
}
