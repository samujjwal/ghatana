/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.application.version.VersionService;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.aep.AepService;
import com.ghatana.yappc.api.bootstrapping.BootstrappingController;
import com.ghatana.yappc.api.development.SprintController;
import com.ghatana.yappc.api.development.StoryController;
import com.ghatana.yappc.api.initialization.ProjectController;
import com.ghatana.yappc.api.repository.AISuggestionRepository;
import com.ghatana.yappc.api.repository.AgentRegistryRepository;
import com.ghatana.yappc.api.repository.BootstrappingSessionRepository;
import com.ghatana.yappc.api.repository.ProjectRepository;
import com.ghatana.yappc.api.repository.RequirementRepository;
import com.ghatana.yappc.api.repository.SprintRepository;
import com.ghatana.yappc.api.repository.StoryRepository;
import com.ghatana.yappc.api.repository.WorkspaceRepository;
import com.ghatana.yappc.api.service.AISuggestionService;
import com.ghatana.yappc.api.service.AgentRegistryService;
import com.ghatana.yappc.api.service.BootstrappingService;
import com.ghatana.yappc.api.service.ConfigLoader;
import com.ghatana.yappc.api.service.ConfigService;
import com.ghatana.yappc.api.service.DashboardService;
import com.ghatana.yappc.api.service.ProjectService;
import com.ghatana.yappc.api.service.RequirementService;
import com.ghatana.yappc.api.service.SprintService;
import com.ghatana.yappc.api.service.StoryService;
import com.ghatana.yappc.api.service.WorkspaceService;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI sub-module for core service wiring and lifecycle services.
 *
 * <p>Provides business services (requirements, AI suggestions, workspace, bootstrapping, project,
 * sprint, story, config, dashboard, agent registry) and their controllers.
 *
 * @doc.type class
 * @doc.purpose Core service and lifecycle DI bindings
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection
 */
public class ServiceWiringModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(ServiceWiringModule.class);

  // ========== Configuration Services ==========

  @Provides
  ConfigLoader configLoader() {
    logger.info("Creating ConfigLoader for YAPPC configuration");
    Path configPath = Paths.get("config", "yappc").toAbsolutePath();
    logger.info("Config path: {}", configPath);
    return new ConfigLoader(configPath);
  }

  @Provides
  ConfigService configService(ConfigLoader configLoader) {
    logger.info("Creating ConfigService");
    return new ConfigService(configLoader);
  }

  // ========== Core Services ==========

  @Provides
  AISuggestionService aiSuggestionService(
      AISuggestionRepository repository,
      AuditService auditService,
      com.ghatana.ai.llm.LLMGateway llmGateway) {
    logger.info("Creating AISuggestionService with production audit and LLM gateway");
    return new AISuggestionService(repository, auditService, llmGateway);
  }

  @Provides
  RequirementService requirementService(
      RequirementRepository repository, AuditService auditService, VersionService versionService) {
    return new RequirementService(repository, auditService, versionService);
  }

  @Provides
  WorkspaceService workspaceService(WorkspaceRepository repository, AuditService auditService) {
    return new WorkspaceService(repository, auditService);
  }

  @Provides
  DashboardService dashboardService() {
    logger.info("Creating DashboardService");
    return new DashboardService();
  }

  @Provides
  AgentRegistryService agentRegistryService(AgentRegistryRepository agentRegistryRepository) {
    logger.info("Creating AgentRegistryService (persistent PostgreSQL-backed)");
    return new AgentRegistryService(agentRegistryRepository);
  }

  // ========== Lifecycle Services ==========

  @Provides
  BootstrappingService bootstrappingService(
      BootstrappingSessionRepository repository,
      AuditService auditService,
      com.ghatana.ai.llm.LLMGateway llmGateway) {
    logger.info("Creating BootstrappingService with LLM gateway");
    return new BootstrappingService(repository, auditService, llmGateway);
  }

  @Provides
  ProjectService projectService(ProjectRepository repository, AuditService auditService) {
    logger.info("Creating ProjectService");
    return new ProjectService(repository, auditService);
  }

  @Provides
  SprintService sprintService(
      SprintRepository sprintRepository,
      StoryRepository storyRepository,
      AuditService auditService) {
    logger.info("Creating SprintService");
    return new SprintService(sprintRepository, storyRepository, auditService);
  }

  @Provides
  StoryService storyService(
      StoryRepository repository,
      AuditService auditService,
      com.ghatana.ai.llm.LLMGateway llmGateway) {
    logger.info("Creating StoryService with LLM gateway");
    return new StoryService(repository, auditService, llmGateway);
  }

  // ========== Controllers ==========

  @Provides
  com.ghatana.yappc.api.requirements.RequirementsController requirementsController(
      RequirementService requirementService, ConfigService configService) {
    logger.info("Creating RequirementsController with ConfigService integration");
    return new com.ghatana.yappc.api.requirements.RequirementsController(
        requirementService, configService);
  }

  @Provides
  com.ghatana.yappc.api.ai.AISuggestionsController aiSuggestionsController(
      AISuggestionService aiSuggestionService, ConfigService configService, AepService aepService) {
    logger.info("Creating AISuggestionsController with ConfigService integration");
    return new com.ghatana.yappc.api.ai.AISuggestionsController(
        aiSuggestionService, configService, aepService);
  }

  @Provides
  com.ghatana.yappc.api.controller.ConfigController configController(ConfigService configService) {
    logger.info("Creating ConfigController with ConfigService integration");
    return new com.ghatana.yappc.api.controller.ConfigController(configService);
  }

  @Provides
  com.ghatana.yappc.api.workspace.WorkspaceController workspaceController(
      WorkspaceService workspaceService) {
    logger.info("Creating WorkspaceController with WorkspaceService");
    return new com.ghatana.yappc.api.workspace.WorkspaceController(workspaceService);
  }

  @Provides
  com.ghatana.yappc.api.audit.AuditController auditController(AuditService auditService) {
    logger.info("Creating AuditController with AuditService");
    return new com.ghatana.yappc.api.audit.AuditController(auditService);
  }

  @Provides
  com.ghatana.yappc.api.version.VersionController versionController(VersionService versionService) {
    logger.info("Creating VersionController with VersionService");
    return new com.ghatana.yappc.api.version.VersionController(versionService);
  }

  @Provides
  com.ghatana.yappc.api.controller.DashboardController dashboardController(
      DashboardService dashboardService) {
    logger.info("Creating DashboardController with DashboardService");
    return new com.ghatana.yappc.api.controller.DashboardController(dashboardService);
  }

  @Provides
  BootstrappingController bootstrappingController(
      BootstrappingService bootstrappingService, ObjectMapper objectMapper) {
    logger.info("Creating BootstrappingController");
    return new BootstrappingController(bootstrappingService, objectMapper);
  }

  @Provides
  ProjectController projectController(ProjectService projectService, ObjectMapper objectMapper) {
    logger.info("Creating ProjectController");
    return new ProjectController(projectService, objectMapper);
  }

  @Provides
  SprintController sprintController(SprintService sprintService, ObjectMapper objectMapper) {
    logger.info("Creating SprintController");
    return new SprintController(sprintService, objectMapper);
  }

  @Provides
  StoryController storyController(StoryService storyService, ObjectMapper objectMapper) {
    logger.info("Creating StoryController");
    return new StoryController(storyService, objectMapper);
  }
}
