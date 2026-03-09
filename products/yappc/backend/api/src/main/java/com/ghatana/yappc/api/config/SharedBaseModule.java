/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config;

import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.datacloud.application.version.VersionComparator;
import com.ghatana.datacloud.application.version.VersionService;
import com.ghatana.datacloud.infrastructure.persistence.version.InMemoryVersionRecord;
import com.ghatana.yappc.api.approval.ApprovalController;
import com.ghatana.yappc.api.architecture.ArchitectureController;
import com.ghatana.yappc.api.audit.AuditController;
import com.ghatana.yappc.api.auth.AuthorizationController;
import com.ghatana.yappc.api.build.BuildController;
import com.ghatana.yappc.api.build.BuildExecutorService;
import com.ghatana.yappc.api.controller.RailController;
import com.ghatana.yappc.api.repository.AISuggestionRepository;
import com.ghatana.yappc.api.repository.InMemoryAISuggestionRepository;
import com.ghatana.yappc.api.repository.InMemoryRequirementRepository;
import com.ghatana.yappc.api.repository.InMemoryWorkspaceRepository;
import com.ghatana.yappc.api.repository.RequirementRepository;
import com.ghatana.yappc.api.repository.WorkspaceRepository;
import com.ghatana.yappc.api.service.ArchitectureAnalysisService;
import com.ghatana.yappc.api.service.RailService;
import com.ghatana.yappc.api.version.VersionController;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared Base Module for YAPPC API.
 *
 * <p><b>Purpose</b><br>
 * Eliminates duplication by providing common services/repositories that are identical between
 * ProductionModule and DevelopmentModule. Follows "reuse first, avoid duplicates" principle.
 *
 * <p><b>Shared Components</b><br>
 *
 * <pre>
 * - VersionService        → data-cloud/application VersionService (identical implementation)
 * - Repository impls      → All InMemory*Repository implementations (identical)
 * - ArchitectureAnalysis  → ArchitectureAnalysisService (identical)
 * - Controllers           → All controller bindings (identical)
 * </pre>
 *
 * <p><b>Environment-Specific Overrides</b><br>
 * ProductionModule and DevelopmentModule only need to provide:
 *
 * <ul>
 *   <li>AuditService (production vs in-memory implementations)
 *   <li>AuthorizationService (production vs permissive implementations)
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Shared DI configuration eliminating duplication
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection, DRY
 */
public class SharedBaseModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(SharedBaseModule.class);

  @Override
  protected void configure() {
    logger.info("Configuring YAPPC API shared components");

    // All controllers use the same binding strategy except those needing special config
    bind(AuditController.class);
    bind(VersionController.class);
    bind(AuthorizationController.class);
    // RequirementsController provided explicitly in ProductionModule (needs ConfigService)
    // AISuggestionsController provided explicitly in ProductionModule (needs ConfigService)
    // ConfigController provided explicitly in ProductionModule (needs ConfigService)
    // WorkspaceController provided explicitly in ProductionModule (needs WorkspaceService)
    bind(ArchitectureController.class);
    bind(ApprovalController.class);
    bind(RailController.class);
    bind(BuildController.class);
    bind(com.ghatana.yappc.api.controller.WebSocketController.class);
    bind(com.ghatana.yappc.api.websocket.ConnectionManager.class);
  }

  // ========== Shared Service Providers ==========

  /** Provides BuildExecutorService. */
  @Provides
  BuildExecutorService buildExecutorService() {
    logger.info("Creating BuildExecutorService");
    return new BuildExecutorService();
  }

  /** Provides RailService for Unified Left Rail. */
  @Provides
  RailService railService(WorkspaceRepository workspaceRepository, LLMGateway llmGateway) {
    logger.info("Creating RailService");
    return new RailService(workspaceRepository, llmGateway);
  }

  /** Provides VersionService with InMemoryVersionRecord (identical in both environments). */
  @Provides
  VersionService versionService() {
    logger.info("Creating VersionService with InMemoryVersionRecord");
    return new VersionService(new InMemoryVersionRecord(), new VersionComparator());
  }

  /** Provides ArchitectureAnalysisService (identical in both environments). */
  @Provides
  ArchitectureAnalysisService architectureAnalysisService() {
    logger.info("Creating ArchitectureAnalysisService");
    return new ArchitectureAnalysisService();
  }

  // ========== Shared Repository Providers ==========

  /** Provides AISuggestionRepository (identical in both environments). */
  @Provides
  AISuggestionRepository aiSuggestionRepository() {
    logger.info("Creating InMemoryAISuggestionRepository");
    return new InMemoryAISuggestionRepository();
  }

  /** Provides RequirementRepository (identical in both environments). */
  @Provides
  RequirementRepository requirementRepository() {
    logger.info("Creating InMemoryRequirementRepository");
    return new InMemoryRequirementRepository();
  }

  /** Provides WorkspaceRepository (identical in both environments). */
  @Provides
  WorkspaceRepository workspaceRepository() {
    logger.info("Creating InMemoryWorkspaceRepository");
    return new InMemoryWorkspaceRepository();
  }

  // ========== Memory & Learning ==========

  /**
   * Provides the default MemoryStore for agent memory operations.
   *
   * <p>Uses the event-log backed in-memory store by default. ProductionModule
   * can override with a persistent MemoryPlane-backed adapter.
   *
   * @return MemoryStore for episodic, semantic, procedural and preference memory
   */
  @Provides
  MemoryStore memoryStore() {
    logger.info("Creating EventLogMemoryStore for agent memory");
    return new com.ghatana.agent.framework.memory.EventLogMemoryStore();
  }
}
