/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.collaboration.CodeReviewController;
import com.ghatana.yappc.api.collaboration.NotificationController;
import com.ghatana.yappc.api.collaboration.TeamController;
import com.ghatana.yappc.api.repository.CodeReviewRepository;
import com.ghatana.yappc.api.repository.NotificationRepository;
import com.ghatana.yappc.api.repository.TeamRepository;
import com.ghatana.yappc.api.service.CodeReviewService;
import com.ghatana.yappc.api.service.NotificationService;
import com.ghatana.yappc.api.service.TeamService;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI sub-module for collaboration domain (teams, code reviews, notifications).
 *
 * @doc.type class
 * @doc.purpose Collaboration services and controllers DI bindings
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection
 */
public class CollaborationModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(CollaborationModule.class);

  // ========== Services ==========

  @Provides
  NotificationService notificationService(NotificationRepository repository) {
    logger.info("Creating NotificationService");
    return new NotificationService(repository);
  }

  @Provides
  TeamService teamService(TeamRepository repository, AuditService auditService) {
    logger.info("Creating TeamService");
    return new TeamService(repository, auditService);
  }

  @Provides
  CodeReviewService codeReviewService(CodeReviewRepository repository, AuditService auditService) {
    logger.info("Creating CodeReviewService");
    return new CodeReviewService(repository, auditService);
  }

  // ========== Controllers ==========

  @Provides
  TeamController teamController(TeamService teamService, ObjectMapper objectMapper) {
    logger.info("Creating TeamController");
    return new TeamController(teamService, objectMapper);
  }

  @Provides
  CodeReviewController codeReviewController(
      CodeReviewService codeReviewService, ObjectMapper objectMapper) {
    logger.info("Creating CodeReviewController");
    return new CodeReviewController(codeReviewService, objectMapper);
  }

  @Provides
  NotificationController notificationController(
      NotificationService notificationService, ObjectMapper objectMapper) {
    logger.info("Creating NotificationController");
    return new NotificationController(notificationService, objectMapper);
  }
}
