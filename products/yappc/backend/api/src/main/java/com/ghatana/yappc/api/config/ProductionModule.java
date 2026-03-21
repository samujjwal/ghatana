/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.audit.AuditQueryService;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.yappc.api.audit.JdbcAuditService;
import com.ghatana.yappc.api.config.modules.AepIntegrationModule;
import com.ghatana.yappc.api.config.modules.AgentMemoryModule;
import com.ghatana.yappc.api.config.modules.AiCodegenModule;
import com.ghatana.yappc.api.config.modules.AuthModule;
import com.ghatana.yappc.api.config.modules.CollaborationModule;
import com.ghatana.yappc.api.config.modules.LlmModule;
import com.ghatana.yappc.api.config.modules.OperationsModule;
import com.ghatana.yappc.api.config.modules.RepositoryModule;
import com.ghatana.yappc.api.config.modules.SecurityModule;
import com.ghatana.yappc.api.config.modules.ServiceWiringModule;
import com.ghatana.yappc.api.config.modules.WorkflowModule;
import com.ghatana.yappc.api.repository.jdbc.JdbcRolePermissionRegistry;
import com.ghatana.yappc.api.service.ArchitectureAnalysisService;
import io.activej.inject.annotation.Provides;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production Dependency Injection Module for YAPPC API.
 *
 * <p><b>Purpose</b><br>
 * Configures production-specific service implementations. Extends SharedBaseModule to eliminate
 * duplication and delegates domain-specific bindings to focused sub-modules.
 *
 * <p><b>Sub-modules installed</b><br>
 *
 * <pre>
 * - RepositoryModule       → All JDBC repository bindings
 * - AepIntegrationModule   → AEP client, outbox relay, lifecycle emitter
 * - AuthModule             → JWT providers, authentication/authorization controllers
 * - LlmModule              → Multi-provider LLM gateway, completion service
 * - ServiceWiringModule    → Core services, lifecycle services, controllers
 * - CollaborationModule    → Teams, code reviews, notifications
 * - OperationsModule       → Metrics, alerts, incidents, logs, traces
 * - SecurityModule         → Vulnerabilities, scans, compliance
 * - WorkflowModule         → Workflow engine, agent catalog, pipeline loader, DLQ
 * - AgentMemoryModule      → Persistent memory plane, governance, history, policies
 * - AiCodegenModule        → Code generation, test generation
 * </pre>
 *
 * <p><b>Production-specific (kept here)</b><br>
 *
 * <pre>
 * - AuditService           → JdbcAuditService (PostgreSQL-backed)
 * - AuthorizationService   → JdbcRolePermissionRegistry-backed
 * - MetricsCollector       → Injected production metrics collector
 * - ObjectMapper           → Jackson with JavaTimeModule
 * - PolicyEngine           → OPA or permissive fallback
 * - ApprovalService        → JdbcApprovalService
 * - HealthAggregationController → Observability endpoint
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Production DI configuration with modular sub-module delegation
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection, DRY
 */
public class ProductionModule extends SharedBaseModule {

  private static final Logger logger = LoggerFactory.getLogger(ProductionModule.class);

  private final MetricsCollector metricsCollector;

  /**
   * Create production module with dependencies.
   *
   * @param metricsCollector Metrics collector for observability
   */
  public ProductionModule(MetricsCollector metricsCollector) {
    this.metricsCollector = metricsCollector;
  }

  @Override
  protected void configure() {
    logger.info("Configuring YAPPC API with production services");

    // Infrastructure modules
    install(new DataSourceModule());
    install(new DataCloudModule());
    install(new YappcAiPlatformModule());

    // Feature sub-modules (extracted from this god-module)
    install(new RepositoryModule());
    install(new AepIntegrationModule());
    install(new AuthModule());
    install(new LlmModule());
    install(new ServiceWiringModule());
    install(new CollaborationModule());
    install(new OperationsModule());
    install(new SecurityModule());
    install(new WorkflowModule());
    install(new AgentMemoryModule());
    install(new AiCodegenModule());

    bind(com.ghatana.yappc.api.controller.GraphQLController.class);

    // Inherit shared components from SharedBaseModule
    super.configure();
  }

  // ========== Production-Specific Service Providers ==========

  /**
   * Provides the durable JDBC-backed audit service (singleton).
   *
   * <p>Implements both {@link AuditService} (writes) and {@link AuditQueryService} (reads) so
   * consumers that need either or both get the same instance.
   *
   * @doc.layer product
   * @doc.pattern Repository
   */
  @Provides
  JdbcAuditService jdbcAuditService(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcAuditService — audit events persisted to PostgreSQL");
    return new JdbcAuditService(dataSource, objectMapper);
  }

  /** Binds {@link AuditService} to the shared {@link JdbcAuditService} singleton. */
  @Provides
  AuditService auditService(JdbcAuditService jdbcAuditService) {
    return jdbcAuditService;
  }

  /** Binds {@link AuditQueryService} to the shared {@link JdbcAuditService} singleton. */
  @Provides
  AuditQueryService auditQueryService(JdbcAuditService jdbcAuditService) {
    return jdbcAuditService;
  }

  /**
   * Provides the unified health aggregation controller (Observability 6.6). Agent count defaults to
   * 228 (number of YAPPC SDLC specialist agents).
   */
  @Provides
  com.ghatana.yappc.api.observability.HealthAggregationController healthAggregationController(
      DataSource dataSource) {
    int agentCount = Integer.parseInt(System.getProperty("yappc.agents.count", "228"));
    logger.info("Creating HealthAggregationController (agentCount={})", agentCount);
    return new com.ghatana.yappc.api.observability.HealthAggregationController(
        dataSource, agentCount);
  }

  /**
   * Provides production SyncAuthorizationService backed by DB-persisted role-permission mappings.
   *
   * @doc.layer product
   * @doc.pattern Service
   */
  @Provides
  JdbcRolePermissionRegistry jdbcRolePermissionRegistry(
      DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcRolePermissionRegistry — roles loaded from PostgreSQL");
    return new JdbcRolePermissionRegistry(dataSource, objectMapper);
  }

  /** Provides SyncAuthorizationService backed by the JDBC role-permission registry. */
  @Provides
  SyncAuthorizationService authorizationService(JdbcRolePermissionRegistry registry) {
    logger.info("Creating production SyncAuthorizationService with JdbcRolePermissionRegistry");
    return new SyncAuthorizationService(registry);
  }

  /** Provides MetricsCollector for observability. */
  @Provides
  MetricsCollector metricsCollector() {
    return metricsCollector;
  }

  // ========== Infrastructure ==========

  @Provides
  ObjectMapper objectMapper() {
    return JsonUtils.getDefaultMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Provides PolicyEngine for governance and compliance enforcement.
   *
   * <p>When {@code OPA_ENDPOINT} env var is set, delegates to the OPA REST API. Otherwise falls
   * back to {@link com.ghatana.yappc.api.infrastructure.policy.PermissivePolicyEngine} which allows
   * all requests with a warning log.
   *
   * @doc.type class
   * @doc.purpose OPA-backed governance policy enforcement
   * @doc.layer api
   * @doc.pattern Adapter, Strategy
   */
  @Provides
  com.ghatana.governance.PolicyEngine policyEngine(ObjectMapper objectMapper) {
    String opaEndpoint = System.getenv("OPA_ENDPOINT");
    if (opaEndpoint != null && !opaEndpoint.isBlank()) {
      logger.info("PolicyEngine → OpaRestPolicyEngine (endpoint={})", opaEndpoint);
      return new com.ghatana.yappc.api.infrastructure.policy.OpaRestPolicyEngine(
          opaEndpoint, objectMapper);
    }
    logger.warn("OPA_ENDPOINT not set — PolicyEngine → PermissivePolicyEngine (allow-all)");
    return new com.ghatana.yappc.api.infrastructure.policy.PermissivePolicyEngine();
  }

  // ========== Approval ==========

  /** Provides JdbcApprovalService — approval workflow state persisted to PostgreSQL. */
  @Provides
  com.ghatana.yappc.api.approval.JdbcApprovalService jdbcApprovalService(
      DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcApprovalService — approval workflows persisted to PostgreSQL");
    return new com.ghatana.yappc.api.approval.JdbcApprovalService(dataSource, objectMapper);
  }

  /** Provides ApprovalService backed by JdbcApprovalService. */
  @Provides
  com.ghatana.yappc.api.approval.ApprovalService approvalService(
      com.ghatana.yappc.api.approval.JdbcApprovalService impl) {
    return impl;
  }

  /** Provides ApprovalController for approval endpoints. */
  @Provides
  com.ghatana.yappc.api.approval.ApprovalController approvalController(
      com.ghatana.yappc.api.approval.ApprovalService approvalService) {
    logger.info("Creating ApprovalController with ApprovalService");
    return new com.ghatana.yappc.api.approval.ApprovalController(approvalService);
  }

  /** Provides ArchitectureController for architecture endpoints. */
  @Provides
  com.ghatana.yappc.api.architecture.ArchitectureController architectureController(
      ArchitectureAnalysisService architectureAnalysisService) {
    logger.info("Creating ArchitectureController with ArchitectureAnalysisService");
    return new com.ghatana.yappc.api.architecture.ArchitectureController(
        architectureAnalysisService);
  }
}
