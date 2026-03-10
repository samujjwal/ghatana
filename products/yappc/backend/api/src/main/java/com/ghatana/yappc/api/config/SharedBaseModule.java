/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config;

import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.datacloud.application.version.VersionComparator;
import com.ghatana.datacloud.application.version.VersionService;
import com.ghatana.datacloud.entity.version.VersionRecord;
import com.ghatana.yappc.api.approval.ApprovalController;
import com.ghatana.yappc.api.architecture.ArchitectureController;
import com.ghatana.yappc.api.audit.AuditController;
import com.ghatana.yappc.api.auth.AuthorizationController;
import com.ghatana.yappc.api.build.BuildController;
import com.ghatana.yappc.api.build.BuildExecutorService;
import com.ghatana.yappc.api.controller.RailController;
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

  /**
   * Provides VersionService, delegating persistence to the environment-specific VersionRecord.
   *
   * <p>ProductionModule supplies a {@link com.ghatana.yappc.api.repository.jdbc.JdbcVersionRecord}
   * while DevelopmentModule supplies an in-memory variant.
   *
   * @param versionRecord environment-specific version persistence implementation
   * @return configured VersionService
   */
  @Provides
  VersionService versionService(VersionRecord versionRecord) {
    logger.info("Creating VersionService with {}", versionRecord.getClass().getSimpleName());
    return new VersionService(versionRecord, new VersionComparator());
  }

  /** Provides ArchitectureAnalysisService (identical in both environments). */
  @Provides
  ArchitectureAnalysisService architectureAnalysisService() {
    logger.info("Creating ArchitectureAnalysisService");
    return new ArchitectureAnalysisService();
  }

}
// Note: MemoryStore binding is declared per-environment:
//   DevelopmentModule → EventLogMemoryStore (in-memory)
//   ProductionModule  → JdbcMemoryStore    (PostgreSQL write-through)
