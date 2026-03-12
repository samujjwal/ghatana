/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config;

import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.security.rbac.InMemoryRolePermissionRegistry;
import com.ghatana.platform.security.rbac.Permission;
import com.ghatana.platform.security.rbac.RolePermissionRegistry;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.datacloud.application.version.VersionService;
import com.ghatana.datacloud.entity.version.VersionRecord;
import com.ghatana.datacloud.infrastructure.persistence.version.InMemoryVersionRecord;
import com.ghatana.yappc.api.repository.AISuggestionRepository;
import com.ghatana.yappc.api.repository.InMemoryAISuggestionRepository;
import com.ghatana.yappc.api.repository.InMemoryRequirementRepository;
import com.ghatana.yappc.api.repository.InMemoryWorkspaceRepository;
import com.ghatana.yappc.api.repository.RequirementRepository;
import com.ghatana.yappc.api.repository.WorkspaceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.yappc.api.service.AISuggestionService;
import com.ghatana.yappc.api.service.ApprovalWorkflowService;
import com.ghatana.yappc.api.service.LifecycleEventEmitter;
import com.ghatana.yappc.api.service.RequirementService;
import com.ghatana.yappc.api.service.WorkspaceService;
import io.activej.inject.annotation.Provides;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development Dependency Injection Module for YAPPC API.
 *
 * <p><b>Purpose</b><br>
 * Configures development-specific service implementations. Extends SharedBaseModule to eliminate
 * duplication and only overrides environment-specific components.
 *
 * <p><b>Development Overrides</b><br>
 *
 * <pre>
 * - AuditService      → InMemoryAuditService with in-memory storage
 * - AuthorizationService → Permissive AuthorizationService (all actions allowed)
 * - Service wiring    → Uses development AuditService in dependent services
 * - Sample data       → Seeds development data for testing
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
 * <p><b>Usage</b><br>
 *
 * <pre>{@code
 * // Default in ApiApplication for local development
 * // Automatically used when no production config is provided
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Development DI configuration with shared component reuse
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection, DRY
 */
public class DevelopmentModule extends SharedBaseModule {

  private static final Logger logger = LoggerFactory.getLogger(DevelopmentModule.class);

  // In-memory storage for development
  private final List<AuditEvent> auditEvents = Collections.synchronizedList(new ArrayList<>());
  private final Map<String, List<Map<String, Object>>> versionHistory = new ConcurrentHashMap<>();

  @Override
  protected void configure() {
    logger.info("⚠️  YAPPC API running in DEVELOPMENT mode with mock services");
    logger.info("   Data will not be persisted across restarts");

    // Wire Data-Cloud client (embedded mode by default in dev — set DC_DEPLOYMENT_MODE=embedded)
    install(new DataCloudModule());

    // Inherit shared components from SharedBaseModule
    super.configure();
  }

  // ========== Dev-only Repository Providers ==========
  // These were removed from SharedBaseModule to eliminate DI conflicts with
  // ProductionModule's JDBC overrides. DevelopmentModule owns InMemory repos.

  /**
   * Provides in-memory VersionRecord for development.
   *
   * <p>SharedBaseModule.versionService() consumes this to construct VersionService.
   */
  @Provides
  VersionRecord versionRecord() {
    logger.info("Creating InMemoryVersionRecord (dev — version history lost on restart)");
    return new InMemoryVersionRecord();
  }

  /** Provides in-memory AISuggestionRepository for development. */
  @Provides
  AISuggestionRepository aiSuggestionRepository() {
    logger.info("Creating InMemoryAISuggestionRepository");
    return new InMemoryAISuggestionRepository();
  }

  /** Provides in-memory RequirementRepository for development. */
  @Provides
  RequirementRepository requirementRepository() {
    logger.info("Creating InMemoryRequirementRepository");
    return new InMemoryRequirementRepository();
  }

  /** Provides in-memory WorkspaceRepository for development. */
  @Provides
  WorkspaceRepository workspaceRepository() {
    logger.info("Creating InMemoryWorkspaceRepository");
    return new InMemoryWorkspaceRepository();
  }

  // ========== Development-Specific Service Providers ==========

  /** Provides in-memory AuditService for development. */
  @Provides
  AuditService auditService() {
    logger.info("Creating in-memory AuditService");
    return new InMemoryAuditService();
  }

  /** Provides permissive AuthorizationService for development. Uses default RolePermissionMapping
   * that grants all permissions.
   */
  @Provides
  SyncAuthorizationService authorizationService() {
    logger.info("Creating permissive SyncAuthorizationService (all actions allowed in dev mode)");
    InMemoryRolePermissionRegistry registry = createDefaultRegistry();
    return new SyncAuthorizationService(registry);
  }

  /**
   * Provides local YAPPC JwtTokenProvider for SecurityMiddleware (development).
   *
   * <p>Uses a fixed dev secret so the server starts without any env vars set.
   * Tokens are still validated — dev mode is not auth-bypass.
   *
   * @doc.type class
   * @doc.purpose Dev-mode YAPPC JwtTokenProvider for SecurityMiddleware
   * @doc.layer api
   * @doc.pattern Provider
   */
  @Provides
  com.ghatana.yappc.api.security.JwtTokenProvider yappcJwtTokenProvider() {
    String secretKey = java.util.Optional.ofNullable(System.getenv("JWT_SECRET_KEY"))
        .filter(s -> !s.isBlank())
        .orElse("dev-yappc-jwt-secret-key-32-chars!!");
    logger.info("Creating development YAPPC JwtTokenProvider");
    return new com.ghatana.yappc.api.security.JwtTokenProvider(secretKey, 60L, 7L);
  }

  // ========== Infrastructure Providers ==========

  /**
   * Provides ObjectMapper for development (identical config to ProductionModule).
   *
   * @doc.type class
   * @doc.purpose Jackson ObjectMapper for JSON serialization in dev
   * @doc.layer api
   * @doc.pattern Provider
   */
  @Provides
  ObjectMapper objectMapper() {
    return JsonUtils.getDefaultMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Provides noop LifecycleEventEmitter for development.
   *
   * <p>Events are logged at DEBUG level without actually connecting to AEP,
   * so the dev environment starts without requiring an AEP sidecar.
   *
   * @doc.type class
   * @doc.purpose Noop lifecycle event emitter for development without AEP
   * @doc.layer api
   * @doc.pattern Provider
   */
  @Provides
  LifecycleEventEmitter lifecycleEventEmitter(ObjectMapper objectMapper) {
    logger.info("Creating noop LifecycleEventEmitter (dev — events logged, not published)");
    return LifecycleEventEmitter.noop(objectMapper);
  }

  /**
   * Provides permissive PolicyEngine for development.
   *
   * <p>PermissivePolicyEngine allows all requests and logs what real OPA would evaluate.
   * In production, the OpaRestPolicyEngine is used when {@code OPA_ENDPOINT} is set.
   *
   * @doc.type class
   * @doc.purpose Permissive policy engine for dev without OPA sidecar
   * @doc.layer api
   * @doc.pattern Null Object
   */
  @Provides
  com.ghatana.governance.PolicyEngine policyEngine() {
    logger.info("Creating PermissivePolicyEngine (dev — all policy checks ALLOWED)");
    return new com.ghatana.yappc.api.infrastructure.policy.PermissivePolicyEngine();
  }

  /**
   * Provides in-memory MemoryStore for development — no persistence between restarts.
   *
   * @doc.type class
   * @doc.purpose In-memory agent memory for development mode
   * @doc.layer api
   * @doc.pattern Null Object
   * @doc.gaa.memory episodic, semantic, procedural, preference
   */
  @Provides
  com.ghatana.agent.framework.memory.MemoryStore memoryStore() {
    logger.info("Creating EventLogMemoryStore for agent memory (dev — in-memory only)");
    return new com.ghatana.agent.framework.memory.EventLogMemoryStore();
  }

  /**
   * Provides FeedbackLearningService with noop metrics for development mode.
   *
   * @doc.type class
   * @doc.purpose Wires FeedbackLearningService for dev (noop metrics, no persistence)
   * @doc.layer api
   * @doc.pattern Service
   */
  @Provides
  com.ghatana.yappc.ai.requirements.ai.feedback.FeedbackLearningService feedbackLearningService() {
    logger.info("Creating FeedbackLearningService (dev — noop metrics)");
    return new com.ghatana.yappc.ai.requirements.ai.feedback.FeedbackLearningService();
  }

  /**
   * Provides a boot-time pipeline definition loader for development mode.
   *
   * <p>Scans the same directories as production; gracefully returns an empty
   * loader when no pipeline YAMLs are found (no crash, just a WARN log).
   *
   * @doc.type class
   * @doc.purpose Boot-time YAML pipeline manifest loader (dev)
   * @doc.layer api
   * @doc.pattern Registry, Loader
   */
  @Provides
  com.ghatana.yappc.api.pipeline.PipelineDefinitionLoader pipelineDefinitionLoader(
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    logger.info("Creating PipelineDefinitionLoader (dev)");
    return new com.ghatana.yappc.api.pipeline.PipelineDefinitionLoader(objectMapper);
  }

  // ========== Service Wiring (using development AuditService) ==========

  /** Provides RequirementService with development AuditService. */
  @Provides
  RequirementService requirementService(
      RequirementRepository repository, AuditService auditService, VersionService versionService) {
    logger.info("Creating RequirementService with development audit");
    return new RequirementService(repository, auditService, versionService);
  }

  /**
   * Provides AISuggestionService with development AuditService and LLM gateway.
   */
  @Provides
  AISuggestionService aiSuggestionService(
      AISuggestionRepository repository, AuditService auditService,
      com.ghatana.ai.llm.LLMGateway llmGateway) {
    logger.info("Creating AISuggestionService with LLM gateway: {}", llmGateway.getClass().getSimpleName());
    return new AISuggestionService(repository, auditService, llmGateway);
  }

  /**
   * Provides LLMGateway for development mode.
   *
   * <p>Uses real providers (OpenAI/Anthropic) when API keys are available via environment
   * variables, otherwise falls back to NoOpLLMGateway for offline development.
   */
  @Provides
  com.ghatana.ai.llm.LLMGateway llmGateway(io.activej.eventloop.Eventloop eventloop) {
    java.util.List<String> providers = new java.util.ArrayList<>();
    com.ghatana.ai.llm.DefaultLLMGateway.Builder builder = com.ghatana.ai.llm.DefaultLLMGateway.builder();
    com.ghatana.platform.observability.NoopMetricsCollector metricsCollector =
        new com.ghatana.platform.observability.NoopMetricsCollector();
    io.activej.dns.DnsClient dnsClient = io.activej.dns.DnsClient.builder(eventloop, java.net.InetAddress.getLoopbackAddress()).build();
    io.activej.http.HttpClient httpClient = io.activej.http.HttpClient.create(eventloop, dnsClient);

    String openAiKey = System.getenv("OPENAI_API_KEY");
    if (openAiKey != null && !openAiKey.isBlank()) {
      com.ghatana.ai.llm.LLMConfiguration openAiConfig = com.ghatana.ai.llm.LLMConfiguration.builder()
          .apiKey(openAiKey.trim())
          .modelName(envOrDefault("OPENAI_MODEL", "gpt-4o-mini"))
          .temperature(0.7)
          .maxTokens(2000)
          .timeoutSeconds(30)
          .maxRetries(2)
          .build();
      builder.addProvider("openai",
          new com.ghatana.ai.llm.ToolAwareOpenAICompletionService(openAiConfig, httpClient, metricsCollector));
      providers.add("openai");
    }

    String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
    if (anthropicKey != null && !anthropicKey.isBlank()) {
      com.ghatana.ai.llm.LLMConfiguration anthropicConfig = com.ghatana.ai.llm.LLMConfiguration.builder()
          .apiKey(anthropicKey.trim())
          .modelName(envOrDefault("ANTHROPIC_MODEL", "claude-3-5-sonnet-20241022"))
          .temperature(0.7)
          .maxTokens(2000)
          .timeoutSeconds(30)
          .maxRetries(2)
          .build();
      builder.addProvider("anthropic",
          new com.ghatana.ai.llm.ToolAwareAnthropicCompletionService(anthropicConfig, httpClient, metricsCollector));
      providers.add("anthropic");
    }

    if (providers.isEmpty()) {
      logger.info("No LLM API keys found — using NoOpLLMGateway for offline development");
      return new com.ghatana.yappc.api.service.NoOpLLMGateway(metricsCollector);
    }

    String primary = providers.get(0);
    builder.defaultProvider(primary).fallbackOrder(providers);
    logger.info("Development LLMGateway configured with real providers={} primary={}", providers, primary);
    return builder.build();
  }

  private static String envOrDefault(String key, String defaultValue) {
    String value = System.getenv(key);
    return (value != null && !value.isBlank()) ? value.trim() : defaultValue;
  }

  /** Provides WorkspaceService with development AuditService. */
  @Provides
  WorkspaceService workspaceService(WorkspaceRepository repository, AuditService auditService) {
    logger.info("Creating WorkspaceService with development audit");
    return new WorkspaceService(repository, auditService);
  }

  /** Provides ApprovalWorkflowService with development AuditService. */
  @Provides
  ApprovalWorkflowService approvalWorkflowService(AuditService auditService) {
    logger.info("Creating ApprovalWorkflowService");
    return new ApprovalWorkflowService(auditService);
  }

  // ========== Data Seeding ==========

  /** Seeds sample data for development/demo purposes. */
  private void seedDevelopmentData() {
    logger.info("Seeding development data...");

    // Add sample audit events
    auditEvents.add(
        createMockAuditEvent(
            "REQUIREMENT",
            "CREATE",
            "req-001",
            "user-1",
            "John Doe",
            Map.of("title", "User Authentication", "priority", "HIGH")));
    auditEvents.add(
        createMockAuditEvent(
            "COMPONENT",
            "UPDATE",
            "comp-001",
            "user-2",
            "Jane Smith",
            Map.of("field", "description", "oldValue", "Old", "newValue", "New")));
    auditEvents.add(
        createMockAuditEvent(
            "AI_SUGGESTION",
            "ACCEPT",
            "sug-001",
            "user-1",
            "John Doe",
            Map.of("suggestionType", "REFACTORING", "confidence", 0.87)));
    auditEvents.add(
        createMockAuditEvent(
            "VERSION",
            "CREATE",
            "ver-001",
            "user-3",
            "Bob Wilson",
            Map.of("versionNumber", "1.0.0", "resourceType", "REQUIREMENT")));
    auditEvents.add(
        createMockAuditEvent(
            "SECURITY",
            "VALIDATE",
            "sec-001",
            "automation",
            "Security Scanner",
            Map.of("vulnerabilities", 2, "severity", "MEDIUM")));

    logger.info("Seeded {} sample audit events", auditEvents.size());
  }

  private AuditEvent createMockAuditEvent(
      String category,
      String action,
      String resourceId,
      String userId,
      String userName,
      Map<String, Object> details) {
    return AuditEvent.builder()
        .tenantId("dev-tenant")
        .eventType(category + "." + action)
        .principal(userName)
        .resourceType(category)
        .resourceId(resourceId)
        .success(true)
        .details(details)
        .timestamp(Instant.now())
        .build();
  }

  // ========== In-Memory Service Implementations ==========

  /** In-memory AuditService that stores events in a list. */
  private class InMemoryAuditService implements AuditService {
    @Override
    public Promise<Void> record(AuditEvent event) {
      auditEvents.add(event);
      logger.debug("Recorded audit event: {}", event.getEventType());
      return Promise.complete();
    }

    // Extended methods for querying (not in interface, added for convenience)
    public List<AuditEvent> getEvents() {
      return new ArrayList<>(auditEvents);
    }

    public List<AuditEvent> getEventsByResource(String resourceType, String resourceId) {
      return auditEvents.stream()
          .filter(
              e -> resourceType.equals(e.getResourceType()) && resourceId.equals(e.getResourceId()))
          .toList();
    }
  }

  /**
   * Creates default role-permission registry matching deprecated RolePermissionMapping defaults.
   */
  public static InMemoryRolePermissionRegistry createDefaultRegistry() {
    InMemoryRolePermissionRegistry registry = new InMemoryRolePermissionRegistry();

    // OWNER: Full system access
    registry.registerRole("OWNER", java.util.Set.of(
        Permission.WORKSPACE_CREATE, Permission.WORKSPACE_READ, Permission.WORKSPACE_UPDATE,
        Permission.WORKSPACE_DELETE, Permission.WORKSPACE_MANAGE_MEMBERS,
        Permission.PROJECT_CREATE, Permission.PROJECT_READ, Permission.PROJECT_UPDATE,
        Permission.PROJECT_DELETE, Permission.REQUIREMENT_CREATE, Permission.REQUIREMENT_READ,
        Permission.REQUIREMENT_UPDATE, Permission.REQUIREMENT_DELETE, Permission.REQUIREMENT_APPROVE,
        Permission.AI_SUGGESTION_REQUEST, Permission.AI_SUGGESTION_FEEDBACK,
        Permission.USER_MANAGE, Permission.ROLE_ASSIGN, Permission.ADMIN_SYSTEM));

    // ADMIN: Administrative access
    registry.registerRole("ADMIN", java.util.Set.of(
        Permission.WORKSPACE_CREATE, Permission.WORKSPACE_READ, Permission.WORKSPACE_UPDATE,
        Permission.WORKSPACE_DELETE, Permission.WORKSPACE_MANAGE_MEMBERS,
        Permission.PROJECT_CREATE, Permission.PROJECT_READ, Permission.PROJECT_UPDATE,
        Permission.PROJECT_DELETE, Permission.REQUIREMENT_APPROVE,
        Permission.USER_MANAGE, Permission.ROLE_ASSIGN, Permission.AI_SUGGESTION_REQUEST));

    // MEMBER: Standard user permissions
    registry.registerRole("MEMBER", java.util.Set.of(
        Permission.WORKSPACE_READ, Permission.PROJECT_CREATE, Permission.PROJECT_READ,
        Permission.PROJECT_UPDATE, Permission.REQUIREMENT_CREATE, Permission.REQUIREMENT_READ,
        Permission.REQUIREMENT_UPDATE, Permission.REQUIREMENT_DELETE,
        Permission.AI_SUGGESTION_REQUEST, Permission.AI_SUGGESTION_FEEDBACK));

    // VIEWER: Read-only access
    registry.registerRole("VIEWER", java.util.Set.of(
        Permission.WORKSPACE_READ, Permission.PROJECT_READ, Permission.REQUIREMENT_READ));

    // EDITOR: Same as MEMBER
    registry.registerRole("EDITOR", registry.getPermissions("MEMBER"));

    // USER: Legacy role name (maps to MEMBER)
    registry.registerRole("USER", registry.getPermissions("MEMBER"));

    return registry;
  }

  // -------------------------------------------------------------------------
  // Phase 4.2 — AI Integration: CodeGen & TestGen (dev stubs)
  // -------------------------------------------------------------------------

  /**
   * Provides a no-op AIIntegrationService for local development.
   *
   * <p>Returns stub responses so the server starts without LLM credentials.
   */
  @Provides
  com.ghatana.ai.AIIntegrationService aiIntegrationService() {
    logger.warn("Using no-op AIIntegrationService — LLM calls will return stub text");
    return new com.ghatana.ai.AIIntegrationService() {
      @Override
      public String generateCode(String prompt) {
        return "// [DEV STUB] Code generation is disabled in development mode.\n"
            + "// Prompt received: " + prompt.lines().findFirst().orElse("(empty)");
      }

      @Override
      public io.activej.promise.Promise<String> complete(String prompt) {
        return io.activej.promise.Promise.of(
            "[DEV STUB] Completion disabled in development mode. Prompt: "
                + prompt.lines().findFirst().orElse("(empty)"));
      }
    };
  }

  /** Provides CodeGenerationService (backed by dev AIIntegrationService). */
  @Provides
  com.ghatana.yappc.api.codegen.CodeGenerationService codeGenerationService(
      com.ghatana.ai.AIIntegrationService aiIntegrationService) {
    return new com.ghatana.yappc.api.codegen.CodeGenerationService(aiIntegrationService);
  }

  /** Provides CodeGenerationController (dev). */
  @Provides
  com.ghatana.yappc.api.codegen.CodeGenerationController codeGenerationController(
      com.ghatana.yappc.api.codegen.CodeGenerationService codeGenerationService) {
    return new com.ghatana.yappc.api.codegen.CodeGenerationController(codeGenerationService);
  }

  /** Provides TestGenerationService (backed by dev AIIntegrationService). */
  @Provides
  com.ghatana.yappc.api.testing.TestGenerationService testGenerationService(
      com.ghatana.ai.AIIntegrationService aiIntegrationService) {
    return new com.ghatana.yappc.api.testing.TestGenerationService(aiIntegrationService);
  }

  /** Provides TestGenerationController (dev). */
  @Provides
  com.ghatana.yappc.api.testing.TestGenerationController testGenerationController(
      com.ghatana.yappc.api.testing.TestGenerationService testGenerationService) {
    return new com.ghatana.yappc.api.testing.TestGenerationController(testGenerationService);
  }
}
