/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.ai.llm.DefaultLLMGateway;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.ToolAwareAnthropicCompletionService;
import com.ghatana.ai.llm.ToolAwareOpenAICompletionService;
import com.ghatana.platform.audit.AuditQueryService;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.audit.JdbcAuditService;
import com.ghatana.yappc.api.aep.AepClient;
import com.ghatana.yappc.api.aep.AepClientFactory;
import com.ghatana.yappc.api.aep.AepConfig;
import com.ghatana.yappc.api.aep.AepException;
import com.ghatana.yappc.api.aep.AepService;
import com.ghatana.yappc.api.service.LifecycleEventEmitter;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.datacloud.application.version.VersionService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.api.repository.AISuggestionRepository;
import com.ghatana.yappc.api.repository.BootstrappingSessionRepository;
import com.ghatana.yappc.api.repository.ProjectRepository;
import com.ghatana.yappc.api.repository.RequirementRepository;
import com.ghatana.yappc.api.repository.SprintRepository;
import com.ghatana.yappc.api.repository.StoryRepository;
import com.ghatana.yappc.api.repository.WorkspaceRepository;
// Collaboration repositories
import com.ghatana.yappc.api.repository.TeamRepository;
import com.ghatana.yappc.api.repository.CodeReviewRepository;
import com.ghatana.yappc.api.repository.NotificationRepository;
import com.ghatana.yappc.api.repository.ChannelRepository;
// Operations repositories
import com.ghatana.yappc.api.repository.MetricRepository;
import com.ghatana.yappc.api.repository.AlertRepository;
import com.ghatana.yappc.api.repository.IncidentRepository;
import com.ghatana.yappc.api.repository.LogEntryRepository;
import com.ghatana.yappc.api.repository.TraceRepository;
// Security repositories
import com.ghatana.yappc.api.repository.VulnerabilityRepository;
import com.ghatana.yappc.api.repository.SecurityScanRepository;
import com.ghatana.yappc.api.repository.ComplianceRepository;
// JDBC repositories
import com.ghatana.yappc.api.repository.jdbc.JdbcBootstrappingSessionRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcProjectRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcRequirementRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcSprintRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcStoryRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcCodeReviewRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcMetricRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcAlertRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcIncidentRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcLogEntryRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcTraceRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcVulnerabilityRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcSecurityScanRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcComplianceRepository;
// Core services
import com.ghatana.yappc.api.service.AISuggestionService;
import com.ghatana.yappc.api.service.ArchitectureAnalysisService;
import com.ghatana.yappc.api.service.BootstrappingService;
import com.ghatana.yappc.api.service.ConfigLoader;
import com.ghatana.yappc.api.service.ConfigService;
import com.ghatana.yappc.api.service.DashboardService;
import com.ghatana.yappc.api.service.ProjectService;
import com.ghatana.yappc.api.service.RequirementService;
import com.ghatana.yappc.api.service.SprintService;
import com.ghatana.yappc.api.service.StoryService;
import com.ghatana.yappc.api.service.WorkspaceService;
// Collaboration services
import com.ghatana.yappc.api.service.TeamService;
import com.ghatana.yappc.api.service.CodeReviewService;
import com.ghatana.yappc.api.service.NotificationService;
// Operations services
import com.ghatana.yappc.api.service.MetricService;
import com.ghatana.yappc.api.service.AlertService;
import com.ghatana.yappc.api.service.IncidentService;
import com.ghatana.yappc.api.service.LogService;
import com.ghatana.yappc.api.service.TraceService;
// Security services
import com.ghatana.yappc.api.service.VulnerabilityService;
import com.ghatana.yappc.api.service.SecurityScanService;
import com.ghatana.yappc.api.service.ComplianceService;
// Event store
import com.ghatana.yappc.api.events.EventRepository;
import com.ghatana.yappc.api.events.EventPublisher;
import com.ghatana.yappc.api.repository.jdbc.JdbcEventRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcRolePermissionRegistry;
import com.ghatana.yappc.api.repository.jdbc.JdbcVersionRecord;
import com.ghatana.yappc.api.repository.jdbc.JdbcWorkflowStateStore;
import com.ghatana.datacloud.entity.version.VersionRecord;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
// Agent registry
import com.ghatana.yappc.api.repository.AgentRegistryRepository;
import com.ghatana.yappc.api.repository.jdbc.JdbcAgentRegistryRepository;
import com.ghatana.yappc.api.service.AgentRegistryService;
// Agent catalog
import com.ghatana.agent.catalog.CatalogRegistry;
import com.ghatana.agent.catalog.loader.CatalogLoader;
// Controllers
import com.ghatana.yappc.api.bootstrapping.BootstrappingController;
import com.ghatana.yappc.api.development.SprintController;
import com.ghatana.yappc.api.development.StoryController;
import com.ghatana.yappc.api.initialization.ProjectController;
// Collaboration controllers
import com.ghatana.yappc.api.collaboration.TeamController;
import com.ghatana.yappc.api.collaboration.CodeReviewController;
import com.ghatana.yappc.api.collaboration.NotificationController;
// Operations controllers
import com.ghatana.yappc.api.operations.MetricController;
import com.ghatana.yappc.api.operations.AlertController;
import com.ghatana.yappc.api.operations.IncidentController;
import com.ghatana.yappc.api.operations.LogController;
import com.ghatana.yappc.api.operations.TraceController;
// Security controllers
import com.ghatana.yappc.api.security.VulnerabilityController;
import com.ghatana.yappc.api.security.SecurityScanController;
import com.ghatana.yappc.api.security.ComplianceController;
import io.activej.dns.DnsClient;
import io.activej.dns.IDnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.inject.annotation.Provides;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production Dependency Injection Module for YAPPC API.
 *
 * <p><b>Purpose</b><br>
 * Configures production-specific service implementations. Extends SharedBaseModule to eliminate
 * duplication and only overrides environment-specific components.
 *
 * <p><b>Production Overrides</b><br>
 *
 * <pre>
 * - AuditService      → Production AuditService (libs/java/audit)
 * - AuthorizationService → Production AuthorizationService with role mappings
 * - MetricsCollector  → Injected production metrics collector
 * - Service wiring    → Uses production AuditService in dependent services
 * </pre>
 *
 * <p><b>Inherited from SharedBaseModule</b><br>
 *
 * <pre>
 * - VersionService    → data-cloud/application VersionService
 * - All repositories  → InMemory*Repository implementations
 * - Controllers       → All controller bindings
 * - ArchitectureAnalysisService → Standard implementation
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Production DI configuration with shared component reuse
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
    install(new DataSourceModule());
    bind(com.ghatana.yappc.api.controller.GraphQLController.class);
    // Inherit shared components from SharedBaseModule
    super.configure();
  }

  // ========== Production-Specific Service Providers ==========

  /**
   * Provides the durable JDBC-backed audit service (singleton).
   *
   * <p>Implements both {@link AuditService} (writes) and {@link AuditQueryService} (reads)
   * so consumers that need either or both get the same instance.
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
   * Provides the unified health aggregation controller (Observability 6.6).
   * Agent count defaults to 228 (number of YAPPC SDLC specialist agents).
   */
  @Provides
  com.ghatana.yappc.api.observability.HealthAggregationController healthAggregationController(DataSource dataSource) {
    int agentCount = Integer.parseInt(System.getProperty("yappc.agents.count", "228"));
    logger.info("Creating HealthAggregationController (agentCount={})", agentCount);
    return new com.ghatana.yappc.api.observability.HealthAggregationController(dataSource, agentCount);
  }

  /**
   * Provides production SyncAuthorizationService backed by DB-persisted role-permission mappings.
   *
   * <p>Replaces the previous hardcoded {@code DevelopmentModule.createDefaultRegistry()} call.
   * Roles are seeded by the V15 Flyway migration and can be mutated at runtime.
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

  /** Provides HTTP client for production LLM providers. */
  @Provides
  HttpClient llmHttpClient(Eventloop eventloop) {
    IDnsClient dnsClient = DnsClient.builder(eventloop, InetAddress.getLoopbackAddress()).build();
    return HttpClient.create(eventloop, dnsClient);
  }

  // ========== AEP Integration ==========

  /** Provides AEP configuration for production runtime. */
  @Provides
  AepConfig aepConfig() {
    return AepConfig.fromEnvironment("production");
  }

  /** Provides AEP client using configured mode (library/service). */
  @Provides
  AepClient aepClient(AepConfig config) throws AepException {
    return AepClientFactory.create(config);
  }

  /** Provides AEP service for runtime controller integration. */
  @Provides
  AepService aepService(AepClient client) {
    return new AepService(client);
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

  // ========== Infrastructure Services ==========

  @Provides
  ObjectMapper objectMapper() {
    return JsonUtils.getDefaultMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Provides PolicyEngine for governance and compliance enforcement.
   *
   * <p>When {@code OPA_ENDPOINT} env var is set, delegates to the OPA REST API.
   * Otherwise falls back to {@link com.ghatana.yappc.api.infrastructure.policy.PermissivePolicyEngine}
   * which allows all requests with a warning log.
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

  @Provides
  RequirementRepository requirementRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based RequirementRepository");
    return new JdbcRequirementRepository(dataSource, objectMapper);
  }

  @Provides
  AISuggestionRepository aiSuggestionRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based AISuggestionRepository");
    return new com.ghatana.yappc.api.repository.jdbc.JdbcAISuggestionRepository(dataSource, objectMapper);
  }

  @Provides
  WorkspaceRepository workspaceRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based WorkspaceRepository");
    return new com.ghatana.yappc.api.repository.jdbc.JdbcWorkspaceRepository(dataSource, objectMapper);
  }

  @Provides
  BootstrappingSessionRepository bootstrappingSessionRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based BootstrappingSessionRepository");
    return new JdbcBootstrappingSessionRepository(dataSource, objectMapper);
  }

  @Provides
  ProjectRepository projectRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based ProjectRepository");
    return new JdbcProjectRepository(dataSource);
  }

  @Provides
  SprintRepository sprintRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based SprintRepository");
    return new JdbcSprintRepository(dataSource, objectMapper);
  }

  @Provides
  StoryRepository storyRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC-based StoryRepository");
    return new JdbcStoryRepository(dataSource, objectMapper);
  }

  // ========== Collaboration Repositories ==========

  @Provides
  TeamRepository teamRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC TeamRepository");
    return new com.ghatana.yappc.api.repository.jdbc.JdbcTeamRepository(dataSource, objectMapper);
  }

  @Provides
  CodeReviewRepository codeReviewRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC CodeReviewRepository");
    return new JdbcCodeReviewRepository(dataSource, objectMapper);
  }

  @Provides
  NotificationRepository notificationRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC NotificationRepository");
    return new com.ghatana.yappc.api.repository.jdbc.JdbcNotificationRepository(dataSource, objectMapper);
  }

  @Provides
  ChannelRepository channelRepository(DataSource dataSource) {
    logger.info("Creating JDBC ChannelRepository");
    return new com.ghatana.yappc.api.repository.jdbc.JdbcChannelRepository(dataSource);
  }

  // ========== Operations Repositories (JDBC) ==========

  @Provides
  MetricRepository metricRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC MetricRepository");
    return new JdbcMetricRepository(dataSource, objectMapper);
  }

  @Provides
  AlertRepository alertRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC AlertRepository");
    return new JdbcAlertRepository(dataSource);
  }

  @Provides
  IncidentRepository incidentRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC IncidentRepository");
    return new JdbcIncidentRepository(dataSource);
  }

  @Provides
  LogEntryRepository logEntryRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC LogEntryRepository");
    return new JdbcLogEntryRepository(dataSource, objectMapper);
  }

  @Provides
  TraceRepository traceRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC TraceRepository");
    return new JdbcTraceRepository(dataSource, objectMapper);
  }

  // ========== Security Repositories (JDBC) ==========

  @Provides
  VulnerabilityRepository vulnerabilityRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC VulnerabilityRepository");
    return new JdbcVulnerabilityRepository(dataSource, objectMapper);
  }

  @Provides
  SecurityScanRepository securityScanRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC SecurityScanRepository");
    return new JdbcSecurityScanRepository(dataSource);
  }

  @Provides
  ComplianceRepository complianceRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JDBC ComplianceRepository");
    return new JdbcComplianceRepository(dataSource);
  }

  // ========== Configuration Services ==========

  /** Provides ConfigLoader for reading YAML configuration files. */
  @Provides
  ConfigLoader configLoader() {
    logger.info("Creating ConfigLoader for YAPPC configuration");
    // Path to config directory relative to current working directory
    Path configPath = Paths.get("config", "yappc").toAbsolutePath();
    logger.info("Config path: {}", configPath);
    return new ConfigLoader(configPath);
  }

  /** Provides ConfigService for serving configuration data. */
  @Provides
  ConfigService configService(ConfigLoader configLoader) {
    logger.info("Creating ConfigService");
    return new ConfigService(configLoader);
  }

  // ========== Service Wiring (using production AuditService) ==========

  /** Provides AISuggestionService with production AuditService. */
  @Provides
  AISuggestionService aiSuggestionService(
      AISuggestionRepository repository, AuditService auditService,
      com.ghatana.ai.llm.LLMGateway llmGateway) {
    logger.info("Creating AISuggestionService with production audit and LLM gateway");
    return new AISuggestionService(repository, auditService, llmGateway);
  }

  /** Provides RequirementService with production AuditService. */
  @Provides
  RequirementService requirementService(
      RequirementRepository repository, AuditService auditService, VersionService versionService) {
    return new RequirementService(repository, auditService, versionService);
  }

  /** Provides WorkspaceService with production AuditService. */
  @Provides
  WorkspaceService workspaceService(WorkspaceRepository repository, AuditService auditService) {
    return new WorkspaceService(repository, auditService);
  }

  // ========== New Lifecycle Services ==========

  /** Provides BootstrappingService for project bootstrapping phase. */
  @Provides
  BootstrappingService bootstrappingService(
      BootstrappingSessionRepository repository, AuditService auditService,
      com.ghatana.ai.llm.LLMGateway llmGateway) {
    logger.info("Creating BootstrappingService with LLM gateway");
    return new BootstrappingService(repository, auditService, llmGateway);
  }

  /** Provides ProjectService for project lifecycle management. */
  @Provides
  ProjectService projectService(
      ProjectRepository repository, AuditService auditService) {
    logger.info("Creating ProjectService");
    return new ProjectService(repository, auditService);
  }

  /** Provides SprintService for sprint management. */
  @Provides
  SprintService sprintService(
      SprintRepository sprintRepository, 
      StoryRepository storyRepository, 
      AuditService auditService) {
    logger.info("Creating SprintService");
    return new SprintService(sprintRepository, storyRepository, auditService);
  }

  /** Provides StoryService for story management. */
  @Provides
  StoryService storyService(
      StoryRepository repository, AuditService auditService,
      com.ghatana.ai.llm.LLMGateway llmGateway) {
    logger.info("Creating StoryService with LLM gateway");
    return new StoryService(repository, auditService, llmGateway);
  }

  // ========== Collaboration Services ==========

  /** Provides NotificationService for notifications. */
  @Provides
  NotificationService notificationService(NotificationRepository repository) {
    logger.info("Creating NotificationService");
    return new NotificationService(repository);
  }

  /** Provides TeamService for team management. */
  @Provides
  TeamService teamService(TeamRepository repository, AuditService auditService) {
    logger.info("Creating TeamService");
    return new TeamService(repository, auditService);
  }

  /** Provides CodeReviewService for code reviews. */
  @Provides
  CodeReviewService codeReviewService(
      CodeReviewRepository repository, 
      AuditService auditService) {
    logger.info("Creating CodeReviewService");
    return new CodeReviewService(repository, auditService);
  }

  // ========== Operations Services ==========

  /** Provides MetricService for metrics management. */
  @Provides
  MetricService metricService(MetricRepository repository, AuditService auditService) {
    logger.info("Creating MetricService");
    return new MetricService(repository, auditService);
  }

  /** Provides AlertService for alert management. */
  @Provides
  AlertService alertService(
      AlertRepository repository, 
      AuditService auditService,
      NotificationService notificationService) {
    logger.info("Creating AlertService");
    return new AlertService(repository);
  }

  /** Provides IncidentService for incident management. */
  @Provides
  IncidentService incidentService(
      IncidentRepository repository, 
      AuditService auditService,
      NotificationService notificationService) {
    logger.info("Creating IncidentService");
    return new IncidentService(repository);
  }

  /** Provides LogService for log management. */
  @Provides
  LogService logService(LogEntryRepository repository, AuditService auditService) {
    logger.info("Creating LogService");
    return new LogService(repository, auditService);
  }

  /** Provides TraceService for trace management. */
  @Provides
  TraceService traceService(TraceRepository repository, AuditService auditService) {
    logger.info("Creating TraceService");
    return new TraceService(repository, auditService);
  }

  // ========== Security Services ==========

  /** Provides VulnerabilityService for vulnerability management. */
  @Provides
  VulnerabilityService vulnerabilityService(
      VulnerabilityRepository repository, 
      AuditService auditService,
      NotificationService notificationService) {
    logger.info("Creating VulnerabilityService");
    return new VulnerabilityService(repository, auditService, notificationService);
  }

  /** Provides SecurityScanService for security scan management. */
  @Provides
  SecurityScanService securityScanService(
      SecurityScanRepository repository, AuditService auditService) {
    logger.info("Creating SecurityScanService");
    return new SecurityScanService(repository);
  }

  /** Provides ComplianceService for compliance management. */
  @Provides
  ComplianceService complianceService(
      ComplianceRepository repository, AuditService auditService) {
    logger.info("Creating ComplianceService");
    return new ComplianceService(repository);
  }

  /** Provides RequirementsController with ConfigService integration. */
  @Provides
  com.ghatana.yappc.api.requirements.RequirementsController requirementsController(
      RequirementService requirementService, ConfigService configService) {
    logger.info("Creating RequirementsController with ConfigService integration");
    return new com.ghatana.yappc.api.requirements.RequirementsController(
        requirementService, configService);
  }

  /** Provides AISuggestionsController with ConfigService integration. */
  @Provides
  com.ghatana.yappc.api.ai.AISuggestionsController aiSuggestionsController(
      AISuggestionService aiSuggestionService, ConfigService configService, AepService aepService) {
    logger.info("Creating AISuggestionsController with ConfigService integration");
    return new com.ghatana.yappc.api.ai.AISuggestionsController(
        aiSuggestionService, configService, aepService);
  }

  /** Provides ConfigController with ConfigService integration. */
  @Provides
  com.ghatana.yappc.api.controller.ConfigController configController(ConfigService configService) {
    logger.info("Creating ConfigController with ConfigService integration");
    return new com.ghatana.yappc.api.controller.ConfigController(configService);
  }

  /** Provides WorkspaceController with WorkspaceService. */
  @Provides
  com.ghatana.yappc.api.workspace.WorkspaceController workspaceController(
      WorkspaceService workspaceService) {
    logger.info("Creating WorkspaceController with WorkspaceService");
    return new com.ghatana.yappc.api.workspace.WorkspaceController(workspaceService);
  }

  /** Provides AuditController for audit trail endpoints. */
  @Provides
  com.ghatana.yappc.api.audit.AuditController auditController(AuditService auditService) {
    logger.info("Creating AuditController with AuditService");
    return new com.ghatana.yappc.api.audit.AuditController(auditService);
  }

  /** Provides VersionController for version endpoints. */
  @Provides
  com.ghatana.yappc.api.version.VersionController versionController(VersionService versionService) {
    logger.info("Creating VersionController with VersionService");
    return new com.ghatana.yappc.api.version.VersionController(versionService);
  }

  /** Provides AuthorizationController for auth endpoints. */
  @Provides
  com.ghatana.yappc.api.auth.AuthorizationController authorizationController(
      SyncAuthorizationService authorizationService) {
    logger.info("Creating AuthorizationController with SyncAuthorizationService");
    return new com.ghatana.yappc.api.auth.AuthorizationController(authorizationService);
  }

  /** Provides UserRepository for authentication persistence. */
  @Provides
  com.ghatana.yappc.api.auth.repository.UserRepository userRepository() {
    logger.info("Creating DataCloudUserRepository");
    return new com.ghatana.yappc.api.auth.repository.DataCloudUserRepository();
  }

  /** Provides JwtTokenProvider for token creation and validation. */
  @Provides
  com.ghatana.platform.security.jwt.JwtTokenProvider jwtTokenProvider() {
    String secretKey = System.getenv("JWT_SECRET_KEY");
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalStateException(
              "JWT_SECRET_KEY environment variable is required but not set. "
              + "Must be at least 32 characters.");
    }
    long validityMs = 3_600_000L; // 1 hour
    logger.info("Creating JwtTokenProvider with {}ms validity", validityMs);
    return new com.ghatana.platform.security.jwt.JwtTokenProvider(secretKey, validityMs);
  }

  /**
   * Provides local YAPPC JwtTokenProvider for SecurityMiddleware.
   *
   * <p>Uses the same {@code JWT_SECRET_KEY} as the platform provider so tokens
   * issued by {@link com.ghatana.yappc.api.auth.AuthenticationService} are
   * verifiable by {@link com.ghatana.yappc.api.security.SecurityMiddleware}.
   *
   * @doc.type class
   * @doc.purpose YAPPC-scoped JWT token provider for middleware auth
   * @doc.layer api
   * @doc.pattern Provider
   */
  @Provides
  com.ghatana.yappc.api.security.JwtTokenProvider yappcJwtTokenProvider() {
    String secretKey = System.getenv("JWT_SECRET_KEY");
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalStateException(
          "JWT_SECRET_KEY environment variable is required but not set. "
          + "Must be at least 32 characters.");
    }
    long tokenValidityMinutes = 60L;   // 1 hour
    long refreshValidityDays  = 30L;
    logger.info("Creating YAPPC JwtTokenProvider (validity={}min)", tokenValidityMinutes);
    return new com.ghatana.yappc.api.security.JwtTokenProvider(
        secretKey, tokenValidityMinutes, refreshValidityDays);
  }

  /** Provides AuthenticationService for login/register/token management. */
  @Provides
  com.ghatana.yappc.api.auth.AuthenticationService authenticationService(
      com.ghatana.yappc.api.auth.repository.UserRepository userRepository,
      com.ghatana.platform.security.jwt.JwtTokenProvider jwtTokenProvider) {
    logger.info("Creating AuthenticationService");
    return new com.ghatana.yappc.api.auth.AuthenticationService(userRepository, jwtTokenProvider);
  }

  /** Provides AuthenticationController for authentication endpoints. */
  @Provides
  com.ghatana.yappc.api.auth.AuthenticationController authenticationController(
      com.ghatana.yappc.api.auth.AuthenticationService authenticationService) {
    logger.info("Creating AuthenticationController");
    return new com.ghatana.yappc.api.auth.AuthenticationController(authenticationService);
  }

  /** Provides ArchitectureController for architecture endpoints. */
  @Provides
  com.ghatana.yappc.api.architecture.ArchitectureController architectureController(
      ArchitectureAnalysisService architectureAnalysisService) {
    logger.info("Creating ArchitectureController with ArchitectureAnalysisService");
    return new com.ghatana.yappc.api.architecture.ArchitectureController(
        architectureAnalysisService);
  }

  /** Provides JdbcApprovalService — approval workflow state persisted to PostgreSQL. */
  @Provides
  com.ghatana.yappc.api.approval.JdbcApprovalService jdbcApprovalService(
      javax.sql.DataSource dataSource, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    logger.info("Creating JdbcApprovalService — approval workflows persisted to PostgreSQL");
    return new com.ghatana.yappc.api.approval.JdbcApprovalService(dataSource, objectMapper);
  }

  /** Provides ApprovalService backed by JdbcApprovalService. */
  @Provides
  com.ghatana.yappc.api.approval.ApprovalService approvalService(
      com.ghatana.yappc.api.approval.JdbcApprovalService impl) {
    return impl;
  }

  /**
   * Provides JDBC-backed MemoryStore so agent memory (episodes, facts, policies) survives restarts.
   *
   * @doc.type class
   * @doc.purpose Durable agent memory persisted to PostgreSQL (write-through EventLogMemoryStore)
   * @doc.layer product
   * @doc.pattern Decorator, Repository
   * @doc.gaa.memory episodic, semantic, procedural
   */
  @Provides
  com.ghatana.yappc.api.repository.jdbc.JdbcMemoryStore jdbcMemoryStore(
      DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcMemoryStore — agent memory persisted to PostgreSQL");
    return new com.ghatana.yappc.api.repository.jdbc.JdbcMemoryStore(dataSource, objectMapper);
  }

  /** Exposes JdbcMemoryStore as the canonical MemoryStore binding. */
  @Provides
  com.ghatana.agent.framework.memory.MemoryStore memoryStore(
      com.ghatana.yappc.api.repository.jdbc.JdbcMemoryStore impl) {
    return impl;
  }

  /**
   * Provides FeedbackLearningService with production MetricsCollector for observability.
   *
   * @doc.type class
   * @doc.purpose Wires MetricsCollector into the feedback learning loop
   * @doc.layer product
   * @doc.pattern Service
   * @doc.gaa.lifecycle reflect
   */
  @Provides
  com.ghatana.yappc.ai.requirements.ai.feedback.FeedbackLearningService feedbackLearningService(
      MetricsCollector metricsCollector) {
    logger.info("Creating FeedbackLearningService with MetricsCollector: {}",
        metricsCollector.getClass().getSimpleName());
    return new com.ghatana.yappc.ai.requirements.ai.feedback.FeedbackLearningService(
        metricsCollector);
  }

  /** Provides ApprovalController for approval endpoints. */
  @Provides
  com.ghatana.yappc.api.approval.ApprovalController approvalController(
      com.ghatana.yappc.api.approval.ApprovalService approvalService) {
    logger.info("Creating ApprovalController with ApprovalService");
    return new com.ghatana.yappc.api.approval.ApprovalController(approvalService);
  }

  /** Provides DashboardService for dashboard data. */
  @Provides
  DashboardService dashboardService() {
    logger.info("Creating DashboardService");
    return new DashboardService();
  }

  /** Provides DashboardController for dashboard endpoints. */
  @Provides
  com.ghatana.yappc.api.controller.DashboardController dashboardController(
      DashboardService dashboardService) {
    logger.info("Creating DashboardController with DashboardService");
    return new com.ghatana.yappc.api.controller.DashboardController(dashboardService);
  }

  // ========== New Lifecycle Controllers ==========

  /** Provides BootstrappingController for bootstrapping endpoints. */
  @Provides
  BootstrappingController bootstrappingController(
      BootstrappingService bootstrappingService, ObjectMapper objectMapper) {
    logger.info("Creating BootstrappingController");
    return new BootstrappingController(bootstrappingService, objectMapper);
  }

  /** Provides ProjectController for project endpoints. */
  @Provides
  ProjectController projectController(
      ProjectService projectService, ObjectMapper objectMapper) {
    logger.info("Creating ProjectController");
    return new ProjectController(projectService, objectMapper);
  }

  /** Provides SprintController for sprint endpoints. */
  @Provides
  SprintController sprintController(
      SprintService sprintService, ObjectMapper objectMapper) {
    logger.info("Creating SprintController");
    return new SprintController(sprintService, objectMapper);
  }

  /** Provides StoryController for story endpoints. */
  @Provides
  StoryController storyController(
      StoryService storyService, ObjectMapper objectMapper) {
    logger.info("Creating StoryController");
    return new StoryController(storyService, objectMapper);
  }

  // ========== Collaboration Controllers ==========

  /** Provides TeamController for team endpoints. */
  @Provides
  TeamController teamController(TeamService teamService, ObjectMapper objectMapper) {
    logger.info("Creating TeamController");
    return new TeamController(teamService, objectMapper);
  }

  /** Provides CodeReviewController for code review endpoints. */
  @Provides
  CodeReviewController codeReviewController(
      CodeReviewService codeReviewService, ObjectMapper objectMapper) {
    logger.info("Creating CodeReviewController");
    return new CodeReviewController(codeReviewService, objectMapper);
  }

  /** Provides NotificationController for notification endpoints. */
  @Provides
  NotificationController notificationController(
      NotificationService notificationService, ObjectMapper objectMapper) {
    logger.info("Creating NotificationController");
    return new NotificationController(notificationService, objectMapper);
  }

  // ========== Operations Controllers ==========

  /** Provides MetricController for metric endpoints. */
  @Provides
  MetricController metricController(MetricService metricService, ObjectMapper objectMapper) {
    logger.info("Creating MetricController");
    return new MetricController(metricService, objectMapper);
  }

  /** Provides AlertController for alert endpoints. */
  @Provides
  AlertController alertController(AlertService alertService, ObjectMapper objectMapper) {
    logger.info("Creating AlertController");
    return new AlertController(alertService, objectMapper);
  }

  /** Provides IncidentController for incident endpoints. */
  @Provides
  IncidentController incidentController(
      IncidentService incidentService, ObjectMapper objectMapper) {
    logger.info("Creating IncidentController");
    return new IncidentController(incidentService, objectMapper);
  }

  /** Provides LogController for log endpoints. */
  @Provides
  LogController logController(LogService logService, ObjectMapper objectMapper) {
    logger.info("Creating LogController");
    return new LogController(logService, objectMapper);
  }

  /** Provides TraceController for trace endpoints. */
  @Provides
  TraceController traceController(TraceService traceService, ObjectMapper objectMapper) {
    logger.info("Creating TraceController");
    return new TraceController(traceService, objectMapper);
  }

  // ========== Security Controllers ==========

  /** Provides VulnerabilityController for vulnerability endpoints. */
  @Provides
  VulnerabilityController vulnerabilityController(
      VulnerabilityService vulnerabilityService, ObjectMapper objectMapper) {
    logger.info("Creating VulnerabilityController");
    return new VulnerabilityController(vulnerabilityService, objectMapper);
  }

  /** Provides SecurityScanController for security scan endpoints. */
  @Provides
  SecurityScanController securityScanController(
      SecurityScanService securityScanService, ObjectMapper objectMapper) {
    logger.info("Creating SecurityScanController");
    return new SecurityScanController(securityScanService, objectMapper);
  }

  /** Provides ComplianceController for compliance endpoints. */
  @Provides
  ComplianceController complianceController(
      ComplianceService complianceService, ObjectMapper objectMapper) {
    logger.info("Creating ComplianceController");
    return new ComplianceController(complianceService, objectMapper);
  }

  // ========== Event Store ==========

  /** Provides JDBC-backed event repository (transactional outbox pattern). */
  @Provides
  EventRepository eventRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcEventRepository");
    return new JdbcEventRepository(dataSource, objectMapper);
  }

  /** Provides EventPublisher backed by the persistent event store. */
  @Provides
  EventPublisher eventPublisher(EventRepository eventRepository) {
    logger.info("Creating EventPublisher");
    return new EventPublisher(eventRepository);
  }

  // ========== Persistent Agent Registry ==========

  /** Provides JDBC-backed agent registry repository. */
  @Provides
  AgentRegistryRepository agentRegistryRepository(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcAgentRegistryRepository");
    return new JdbcAgentRegistryRepository(dataSource, objectMapper);
  }

  /** Provides AgentRegistryService backed by persistent storage. */
  @Provides
  AgentRegistryService agentRegistryService(AgentRegistryRepository agentRegistryRepository) {
    logger.info("Creating AgentRegistryService (persistent PostgreSQL-backed)");
    return new AgentRegistryService(agentRegistryRepository);
  }

  // ========== Agent Catalog (boot-time YAML loading) ==========

  /**
   * Provides the YAPPC agent {@link CatalogRegistry} by loading all
   * {@code agent-catalog.yaml} files at startup.
   *
   * <p>The root directory is resolved from the {@code YAPPC_AGENT_CATALOG_DIR}
   * environment variable (defaults to {@code config/agents} relative to the
   * working directory). Loading failures for individual catalog files are
   * logged as warnings rather than crashing the server.
   *
   * @doc.layer product
   * @doc.pattern Registry
   * @doc.gaa.lifecycle perceive
   */
  @Provides
  CatalogRegistry catalogRegistry(ObjectMapper objectMapper) {
    CatalogRegistry registry = CatalogRegistry.empty();

    String catalogDirEnv = java.util.Optional
        .ofNullable(System.getenv("YAPPC_AGENT_CATALOG_DIR"))
        .filter(s -> !s.isBlank())
        .orElse("config/agents");

    Path catalogDir = Path.of(catalogDirEnv);
    if (!java.nio.file.Files.isDirectory(catalogDir)) {
      logger.warn("Agent catalog directory not found at '{}' — " +
          "set YAPPC_AGENT_CATALOG_DIR or ensure config/agents is present. " +
          "CatalogRegistry will be empty.", catalogDir.toAbsolutePath());
      return registry;
    }

    com.fasterxml.jackson.databind.ObjectMapper yamlMapper =
        new com.fasterxml.jackson.databind.ObjectMapper(
            new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
    CatalogLoader loader = new CatalogLoader(yamlMapper);

    try {
      java.util.List<com.ghatana.agent.catalog.AgentCatalog> catalogs =
          loader.discoverAndLoad(catalogDir);
      catalogs.forEach(registry::register);
      logger.info("Loaded {} agent catalog(s) with {} agent definitions from {}",
          catalogs.size(), registry.size(), catalogDir.toAbsolutePath());
    } catch (Exception e) {
      logger.error("Failed to load agent catalogs from '{}': {}",
          catalogDir.toAbsolutePath(), e.getMessage(), e);
    }

    return registry;
  }

  /**
   * Provides the boot-time pipeline definition loader.
   *
   * <p>Scans {@code YAPPC_PIPELINE_DIR} (or defaults) for {@code *.yaml} pipeline manifests.
   * Pipelines are indexed by name and available for introspection at runtime.
   * Parse failures are logged as warnings rather than crashing the server.
   *
   * @doc.type class
   * @doc.purpose Boot-time YAML pipeline manifest loader and registry
   * @doc.layer product
   * @doc.pattern Registry, Loader
   */
  @Provides
  com.ghatana.yappc.api.pipeline.PipelineDefinitionLoader pipelineDefinitionLoader(
      ObjectMapper objectMapper) {
    com.ghatana.yappc.api.pipeline.PipelineDefinitionLoader loader =
        new com.ghatana.yappc.api.pipeline.PipelineDefinitionLoader(objectMapper);
    logger.info("PipelineDefinitionLoader ready — {} pipeline(s) registered", loader.size());
    return loader;
  }

  // ========== Durable Workflow Engine ==========

  /**
   * Provides JDBC-backed entity version record for durable version history.
   *
   * @doc.layer product
   * @doc.pattern Repository
   */
  @Provides
  JdbcVersionRecord jdbcVersionRecord(DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcVersionRecord — entity version history persisted to PostgreSQL");
    return new JdbcVersionRecord(dataSource, objectMapper);
  }

  /**
   * Exposes JdbcVersionRecord as the canonical VersionRecord binding.
   *
   * @doc.layer product
   * @doc.pattern Repository
   */
  @Provides
  VersionRecord versionRecord(JdbcVersionRecord store) {
    return store;
  }

  /**
   * Provides JDBC-backed workflow state store for durable workflow persistence.
   *
   * @doc.layer product
   * @doc.pattern Repository
   */
  @Provides
  JdbcWorkflowStateStore jdbcWorkflowStateStore(
      DataSource dataSource, ObjectMapper objectMapper) {
    logger.info("Creating JdbcWorkflowStateStore — workflow runs persisted to PostgreSQL");
    return new JdbcWorkflowStateStore(dataSource, objectMapper);
  }

  /**
   * Exposes JdbcWorkflowStateStore as the canonical WorkflowStateStore binding.
   *
   * @doc.layer product
   * @doc.pattern Repository
   */
  @Provides
  DurableWorkflowEngine.WorkflowStateStore workflowStateStore(
      JdbcWorkflowStateStore store) {
    return store;
  }

  /**
   * Provides DurableWorkflowEngine with JDBC-backed state for cross-restart durability.
   *
   * @doc.layer product
   * @doc.pattern Service
   */
  @Provides
  DurableWorkflowEngine durableWorkflowEngine(JdbcWorkflowStateStore stateStore) {
    logger.info("Creating DurableWorkflowEngine with JdbcWorkflowStateStore");
    return DurableWorkflowEngine.builder()
        .stateStore(stateStore)
        .defaultTimeout(java.time.Duration.ofMinutes(30))
        .defaultMaxRetries(3)
        .build();
  }

  // ========== Workflow Agent Services ==========

  /** Provides WorkflowAgentRegistry for agent registration and lookup. */
  @Provides
  com.ghatana.agent.workflow.WorkflowAgentRegistry workflowAgentRegistry() {
    logger.info("Creating InMemoryWorkflowAgentRegistry");
    return new com.ghatana.agent.workflow.InMemoryWorkflowAgentRegistry();
  }

  /** Provides LLMGateway for agent AI operations with per-tenant cost enforcement. */
  @Provides
  com.ghatana.ai.llm.LLMGateway llmGateway(MetricsCollector metricsCollector, HttpClient llmHttpClient) {
    DefaultLLMGateway.Builder builder = DefaultLLMGateway.builder().metrics(metricsCollector);
    List<String> providers = new ArrayList<>();

    // CostEnforcingCompletionService enforces 10M tokens/tenant by default;
    // call setTenantBudget(tenantId, n) via admin API to override per-tenant.
    logger.info("LLMGateway: cost enforcement enabled (default 10M tokens/tenant)");

    String openAiApiKey = normalizedEnv("OPENAI_API_KEY");
    if (openAiApiKey != null) {
      LLMConfiguration openAiConfig =
          LLMConfiguration.builder()
              .apiKey(openAiApiKey)
              .baseUrl(normalizedEnv("OPENAI_BASE_URL"))
              .modelName(envOrDefault("OPENAI_MODEL", "gpt-4o-mini"))
              .temperature(parseDoubleEnv("LLM_TEMPERATURE", 0.7))
              .maxTokens(parseIntEnv("LLM_MAX_TOKENS", 2000))
              .timeoutSeconds(parseIntEnv("LLM_TIMEOUT_SECONDS", 30))
              .maxRetries(parseIntEnv("LLM_MAX_RETRIES", 3))
              .build();

      builder.addProvider("openai",
          new ToolAwareOpenAICompletionService(openAiConfig, llmHttpClient, metricsCollector));
      providers.add("openai");
      logger.info("LLMGateway: OpenAI provider enabled with cost enforcement");
    }

    String anthropicApiKey = normalizedEnv("ANTHROPIC_API_KEY");
    if (anthropicApiKey != null) {
      LLMConfiguration anthropicConfig =
          LLMConfiguration.builder()
              .apiKey(anthropicApiKey)
              .baseUrl(normalizedEnv("ANTHROPIC_BASE_URL"))
              .modelName(envOrDefault("ANTHROPIC_MODEL", "claude-3-5-sonnet-20241022"))
              .temperature(parseDoubleEnv("LLM_TEMPERATURE", 0.7))
              .maxTokens(parseIntEnv("LLM_MAX_TOKENS", 2000))
              .timeoutSeconds(parseIntEnv("LLM_TIMEOUT_SECONDS", 30))
              .maxRetries(parseIntEnv("LLM_MAX_RETRIES", 3))
              .build();

      builder.addProvider("anthropic",
          new ToolAwareAnthropicCompletionService(anthropicConfig, llmHttpClient, metricsCollector));
      providers.add("anthropic");
      logger.info("LLMGateway: Anthropic provider enabled with cost enforcement");
    }

    if (providers.isEmpty()) {
      throw new IllegalStateException(
          "No production LLM provider configured. Set OPENAI_API_KEY and/or ANTHROPIC_API_KEY.");
    }

    String primaryProvider = envOrDefault("LLM_PRIMARY_PROVIDER", providers.get(0));
    if (!providers.contains(primaryProvider)) {
      logger.warn("Configured LLM_PRIMARY_PROVIDER '{}' is unavailable, using '{}'", primaryProvider, providers.get(0));
      primaryProvider = providers.get(0);
    }

    builder.defaultProvider(primaryProvider).fallbackOrder(providers);
    logger.info("Creating production LLMGateway with providers={} primary={}", providers, primaryProvider);
    return builder.build();
  }

  private static String normalizedEnv(String key) {
    String value = System.getenv(key);
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String envOrDefault(String key, String defaultValue) {
    String value = normalizedEnv(key);
    return value != null ? value : defaultValue;
  }

  private static int parseIntEnv(String key, int defaultValue) {
    String value = normalizedEnv(key);
    if (value == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static double parseDoubleEnv(String key, double defaultValue) {
    String value = normalizedEnv(key);
    if (value == null) {
      return defaultValue;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Provides CompletionService by adapting the multi-provider LLMGateway.
   *
   * <p>This ensures a single LLM integration path — routing, fallback, and
   * circuit-breaker behaviour from the gateway are reused by every consumer.</p>
   */
  @Provides
  com.ghatana.ai.llm.CompletionService completionService(
      com.ghatana.ai.llm.LLMGateway llmGateway,
      MetricsCollector metricsCollector) {
    logger.info("Creating GatewayCompletionServiceAdapter backed by LLMGateway");
    return new com.ghatana.yappc.api.ai.GatewayCompletionServiceAdapter(llmGateway, metricsCollector);
  }

  /** Provides LLMSuggestionGenerator for AI-powered suggestion generation. */
  @Provides
  com.ghatana.yappc.api.service.LLMSuggestionGenerator llmSuggestionGenerator(
      com.ghatana.ai.llm.CompletionService completionService) {
    logger.info("Creating LLMSuggestionGenerator");
    return new com.ghatana.yappc.api.service.LLMSuggestionGenerator(completionService);
  }

  /** Provides WorkflowAgentService for agent execution. */
  @Provides
  com.ghatana.agent.workflow.WorkflowAgentService workflowAgentService(
      com.ghatana.agent.workflow.WorkflowAgentRegistry registry,
      com.ghatana.ai.llm.LLMGateway llmGateway,
      MetricsCollector metricsCollector) {
    logger.info("Creating DefaultWorkflowAgentService");
    return new com.ghatana.agent.workflow.DefaultWorkflowAgentService(registry, llmGateway, metricsCollector);
  }

  /** Provides WorkflowAgentController for workflow agent endpoints. */
  @Provides
  com.ghatana.yappc.api.controller.WorkflowAgentController workflowAgentController(
      com.ghatana.agent.workflow.WorkflowAgentService agentService,
      com.ghatana.agent.workflow.WorkflowAgentRegistry agentRegistry) {
    logger.info("Creating WorkflowAgentController");
    return new com.ghatana.yappc.api.controller.WorkflowAgentController(agentService, agentRegistry);
  }

  /** Provides WorkflowAgentInitializer for agent registration on startup. */
  @Provides
  com.ghatana.yappc.api.service.WorkflowAgentInitializer workflowAgentInitializer(
      com.ghatana.agent.workflow.WorkflowAgentRegistry agentRegistry,
      com.ghatana.ai.llm.LLMGateway llmGateway) {
    logger.info("Creating WorkflowAgentInitializer");
    com.ghatana.yappc.api.service.WorkflowAgentInitializer initializer =
        new com.ghatana.yappc.api.service.WorkflowAgentInitializer(agentRegistry, llmGateway);
    
    // Initialize agents on startup
    initializer.initialize().whenComplete((count, error) -> {
      if (error != null) {
        logger.error("Failed to initialize workflow agents", error);
      } else {
        logger.info("Successfully registered {} workflow agents", count);
      }
    });
    
    return initializer;
  }

  // -------------------------------------------------------------------------
  // Phase 4.2 — AI Integration: CodeGen & TestGen
  // -------------------------------------------------------------------------

  /**
   * Provides AIIntegrationService backed by the production LLMGateway.
   *
   * <p>The adapter wraps the multi-provider gateway (already cost-enforced) and exposes
   * the simple {@code generateCode(prompt)} / {@code complete(prompt)} contract that
   * {@code CodeGenerationService} and {@code TestGenerationService} depend on.
   */
  @Provides
  com.ghatana.ai.AIIntegrationService aiIntegrationService(
      com.ghatana.ai.llm.LLMGateway llmGateway) {
    logger.info("Creating LLMGatewayAIIntegrationService");
    return new com.ghatana.yappc.api.ai.LLMGatewayAIIntegrationService(llmGateway);
  }

  /** Provides AI-powered code generation service. */
  @Provides
  com.ghatana.yappc.api.codegen.CodeGenerationService codeGenerationService(
      com.ghatana.ai.AIIntegrationService aiIntegrationService) {
    logger.info("Creating CodeGenerationService");
    return new com.ghatana.yappc.api.codegen.CodeGenerationService(aiIntegrationService);
  }

  /** Provides the HTTP controller for code generation endpoints. */
  @Provides
  com.ghatana.yappc.api.codegen.CodeGenerationController codeGenerationController(
      com.ghatana.yappc.api.codegen.CodeGenerationService codeGenerationService) {
    return new com.ghatana.yappc.api.codegen.CodeGenerationController(codeGenerationService);
  }

  /** Provides AI-powered test generation service. */
  @Provides
  com.ghatana.yappc.api.testing.TestGenerationService testGenerationService(
      com.ghatana.ai.AIIntegrationService aiIntegrationService) {
    logger.info("Creating TestGenerationService");
    return new com.ghatana.yappc.api.testing.TestGenerationService(aiIntegrationService);
  }

  /** Provides the HTTP controller for test generation endpoints. */
  @Provides
  com.ghatana.yappc.api.testing.TestGenerationController testGenerationController(
      com.ghatana.yappc.api.testing.TestGenerationService testGenerationService) {
    return new com.ghatana.yappc.api.testing.TestGenerationController(testGenerationService);
  }

  // -------------------------------------------------------------------------
  // Orchestration 7.3 — Workflow Template Materialization
  // -------------------------------------------------------------------------

  /**
   * Provides {@link com.ghatana.yappc.api.workflow.WorkflowMaterializer} — reads
   * {@code lifecycle-workflow-templates.yaml} and registers all templates with the
   * durable workflow engine at startup.
   *
   * @doc.layer product
   * @doc.pattern Service, Bootstrapper
   */
  @Provides
  com.ghatana.yappc.api.workflow.WorkflowMaterializer workflowMaterializer(
      DurableWorkflowEngine engine) {
    logger.info("Creating WorkflowMaterializer — materializing canonical workflow templates");
    com.ghatana.yappc.api.workflow.WorkflowMaterializer materializer =
        new com.ghatana.yappc.api.workflow.WorkflowMaterializer(engine);
    int loaded = materializer.materializeAll();
    logger.info("WorkflowMaterializer ready — {} template(s) loaded", loaded);
    return materializer;
  }

  /**
   * Provides {@link com.ghatana.yappc.api.workflow.WorkflowExecutionController} —
   * REST API for starting and monitoring durable workflow runs.
   *
   * @doc.layer api
   * @doc.pattern Controller
   */
  @Provides
  com.ghatana.yappc.api.workflow.WorkflowExecutionController workflowExecutionController(
      com.ghatana.yappc.api.workflow.WorkflowMaterializer workflowMaterializer) {
    logger.info("Creating WorkflowExecutionController");
    return new com.ghatana.yappc.api.workflow.WorkflowExecutionController(workflowMaterializer);
  }

  // -------------------------------------------------------------------------
  // Orchestration 7.4 — Dead-Letter Queue
  // -------------------------------------------------------------------------

  /**
   * Provides JDBC-backed {@link com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher}.
   *
   * @doc.layer product
   * @doc.pattern Repository
   */
  @Provides
  com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher dlqPublisher(
      DataSource dataSource, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    logger.info("Creating JdbcDlqPublisher — DLQ persistence via PostgreSQL");
    return new com.ghatana.yappc.api.dlq.JdbcDlqPublisher(dataSource, objectMapper);
  }

  /**
   * Provides {@link com.ghatana.yappc.api.dlq.JdbcDlqRepository} for DLQ CRUD.
   *
   * @doc.layer product
   * @doc.pattern Repository
   */
  @Provides
  com.ghatana.yappc.api.dlq.JdbcDlqRepository jdbcDlqRepository(
      DataSource dataSource, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    logger.info("Creating JdbcDlqRepository");
    return new com.ghatana.yappc.api.dlq.JdbcDlqRepository(dataSource, objectMapper);
  }

  /**
   * Provides {@link com.ghatana.yappc.api.dlq.DlqController} — REST API for DLQ management.
   *
   * @doc.layer api
   * @doc.pattern Controller
   */
  @Provides
  com.ghatana.yappc.api.dlq.DlqController dlqController(
      com.ghatana.yappc.api.dlq.JdbcDlqRepository dlqRepository,
      com.ghatana.yappc.api.workflow.WorkflowMaterializer workflowMaterializer) {
    logger.info("Creating DlqController");
    return new com.ghatana.yappc.api.dlq.DlqController(dlqRepository, workflowMaterializer);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 9.1 — Persistent Memory Plane
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Provides {@link com.ghatana.agent.memory.persistence.JdbcMemoryItemRepository}
   * backed by the shared YAPPC DataSource. Used by the PersistentMemoryPlane.
   *
   * <p>Schema: {@code memory_items} table created by the agent-memory platform migration
   * (V001__create_memory_items.sql). The YAPPC product adds its own migration alias
   * {@code V20__agent_memory_items.sql}.
   *
   * @doc.layer product
   * @doc.pattern Repository
   */
  @Provides
  com.ghatana.agent.memory.persistence.JdbcMemoryItemRepository jdbcMemoryItemRepository(
      javax.sql.DataSource dataSource) {
    logger.info("Creating JdbcMemoryItemRepository");
    return new com.ghatana.agent.memory.persistence.JdbcMemoryItemRepository(dataSource);
  }

  /**
   * Provides {@link com.ghatana.agent.memory.store.taskstate.TaskStateStore} backed by
   * {@link com.ghatana.agent.memory.persistence.JdbcTaskStateRepository}.
   *
   * @doc.layer product
   * @doc.pattern Repository
   */
  @Provides
  com.ghatana.agent.memory.store.taskstate.TaskStateStore taskStateStore() {
    logger.info("Creating JdbcTaskStateStore");
    com.ghatana.agent.memory.persistence.JdbcTaskStateRepository repo =
        new com.ghatana.agent.memory.persistence.JdbcTaskStateRepository();
    return new com.ghatana.agent.memory.store.taskstate.JdbcTaskStateStore(repo);
  }

  /**
   * Provides the production {@link com.ghatana.agent.memory.persistence.PersistentMemoryPlane}
   * that replaces the in-memory {@code EventLogMemoryStore}.
   *
   * <p>All agent episodic, semantic, procedural, and preference memory is persisted to
   * PostgreSQL via {@link com.ghatana.agent.memory.persistence.JdbcMemoryItemRepository}.
   *
   * @param memoryItemRepository the JDBC-backed memory item repository
   * @param taskStateStore       the task state store for durable task tracking
   * @doc.layer product
   * @doc.pattern Service
   * @doc.gaa.memory episodic|semantic|procedural|preference
   */
  @Provides
  com.ghatana.agent.memory.persistence.PersistentMemoryPlane persistentMemoryPlane(
      com.ghatana.agent.memory.persistence.JdbcMemoryItemRepository memoryItemRepository,
      com.ghatana.agent.memory.store.taskstate.TaskStateStore taskStateStore) {
    logger.info("Creating PersistentMemoryPlane (JDBC-backed, no EventCloud)");
    com.ghatana.agent.memory.model.working.WorkingMemoryConfig workingMemoryConfig =
        com.ghatana.agent.memory.model.working.WorkingMemoryConfig.builder()
            .maxEntries(5000)
            .build();
    return new com.ghatana.agent.memory.persistence.PersistentMemoryPlane(
        memoryItemRepository, taskStateStore, workingMemoryConfig);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 9.2 — Memory Governance
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Provides the {@link com.ghatana.agent.memory.security.MemoryRedactionFilter} loaded
   * from {@code config/memory/redaction-rules.yaml} (external config dir) with a fallback
   * to the default built-in patterns if the file is not found.
   *
   * @doc.layer product
   * @doc.pattern Security
   * @doc.gaa.memory episodic|semantic|procedural|preference
   */
  @Provides
  com.ghatana.agent.memory.security.MemoryRedactionFilter memoryRedactionFilter() {
    String configDir = System.getProperty("yappc.config.dir");
    if (configDir != null && !configDir.isBlank()) {
      java.nio.file.Path rulesPath = java.nio.file.Paths.get(configDir, "memory/redaction-rules.yaml");
      if (java.nio.file.Files.exists(rulesPath)) {
        try {
          com.ghatana.agent.memory.security.YamlRedactionPatternProvider provider =
              com.ghatana.agent.memory.security.YamlRedactionPatternProvider.fromPath(rulesPath);
          logger.info("MemoryRedactionFilter: loaded YAML rules from '{}'", rulesPath);
          return new com.ghatana.agent.memory.security.MemoryRedactionFilter(true, true, provider);
        } catch (java.io.IOException e) {
          logger.warn("MemoryRedactionFilter: failed to load '{}', using defaults: {}", rulesPath, e.getMessage());
        }
      }
    }
    logger.info("MemoryRedactionFilter: using built-in default patterns");
    return com.ghatana.agent.memory.security.MemoryRedactionFilter.defaultFilter();
  }

  /**
   * Provides the {@link com.ghatana.agent.memory.security.MemorySecurityManager} that
   * enforces tenant isolation on all memory reads and writes.
   *
   * @doc.layer product
   * @doc.pattern Security
   */
  @Provides
  com.ghatana.agent.memory.security.MemorySecurityManager memorySecurityManager() {
    logger.info("Creating TenantIsolatedMemorySecurityManager");
    return new com.ghatana.yappc.api.memory.TenantIsolatedMemorySecurityManager();
  }
}

