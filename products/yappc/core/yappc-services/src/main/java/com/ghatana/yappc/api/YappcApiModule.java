package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.security.rbac.InMemoryRolePermissionRegistry;
import com.ghatana.platform.security.rbac.RolePermissionRegistry;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.yappc.services.capability.CapabilityEvaluationService;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.evolve.EvolutionServiceImpl;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.generate.GenerationServiceImpl;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.intent.IntentServiceImpl;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.learn.LearningServiceImpl;
import com.ghatana.yappc.services.lifecycle.JdbcAuditLogger;
import com.ghatana.yappc.services.lifecycle.TransitionConfigLoader;
import com.ghatana.yappc.services.lifecycle.gate.PhaseGateValidator;
import com.ghatana.yappc.services.platform.PlatformIntegrationClient;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import com.ghatana.yappc.services.metrics.BusinessMetrics;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.observe.ObserveServiceImpl;
import com.ghatana.yappc.services.phase.PhasePacketService;
import com.ghatana.yappc.services.phase.PhasePacketServiceImpl;
import com.ghatana.yappc.services.phase.DegradedAuditService;
import com.ghatana.yappc.services.phase.DegradedPreviewRuntimeService;
import com.ghatana.yappc.services.run.CiCdPort;
import com.ghatana.yappc.services.run.GitHubActionsCiCdAdapter;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.run.RunServiceImpl;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.shape.ShapeServiceImpl;
import com.ghatana.yappc.services.validate.ValidationService;
import com.ghatana.yappc.services.validate.ValidationServiceImpl;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Dependency injection module for YAPPC API
 * @doc.layer api
 * @doc.pattern Module
 */
public class YappcApiModule extends AbstractModule {

    @Provides
    IntentService intentService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        return new IntentServiceImpl(aiService, auditLogger, metrics);
    }

    @Provides
    ShapeService shapeService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        return new ShapeServiceImpl(aiService, auditLogger, metrics);
    }

    @Provides
    ValidationService validationService(
            PolicyEngine policyEngine,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        return new ValidationServiceImpl(policyEngine, auditLogger, metrics);
    }

    @Provides
    GenerationService generationService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            GenerationRunRepository generationRunRepository,
            ObjectMapper objectMapper) {
        return new GenerationServiceImpl(aiService, auditLogger, metrics, generationRunRepository, objectMapper);
    }

    @Provides
    CiCdPort ciCdPort() {
        return GitHubActionsCiCdAdapter.fromEnvironment();
    }

    @Provides
    RunService runService(
            AuditLogger auditLogger,
            MetricsCollector metrics,
            CiCdPort ciCdPort) {
        return new RunServiceImpl(auditLogger, metrics, ciCdPort);
    }

    @Provides
    ObserveService observeService(
            MetricsCollector metrics,
            AuditLogger auditLogger) {
        return new ObserveServiceImpl(metrics, auditLogger);
    }

    @Provides
    LearningService learningService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        return new LearningServiceImpl(aiService, auditLogger, metrics);
    }

    @Provides
    EvolutionService evolutionService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        return new EvolutionServiceImpl(aiService, auditLogger, metrics);
    }

    @Provides
    RunApiController runApiController(RunService runService, AuditLogger auditLogger) {
        return new RunApiController(runService, auditLogger);
    }

    @Provides
    ObserveApiController observeApiController(ObserveService observeService) {
        return new ObserveApiController(observeService);
    }

    @Provides
    LearnApiController learnApiController(LearningService learningService) {
        return new LearnApiController(learningService);
    }

    @Provides
    EvolveApiController evolveApiController(EvolutionService evolutionService) {
        return new EvolveApiController(evolutionService);
    }

    @Provides
    LifecycleApiController lifecycleApiController(
            IntentService intentService,
            ShapeService shapeService,
            ValidationService validationService,
            GenerationService generationService,
            RunService runService,
            ObserveService observeService,
            LearningService learningService,
            EvolutionService evolutionService,
            Eventloop eventloop,
            HttpClient httpClient,
            LifecycleExecutionRepository executionRepository) {
        return new LifecycleApiController(
            intentService,
            shapeService,
            validationService,
            generationService,
            runService,
            observeService,
            learningService,
            evolutionService,
            eventloop,
            httpClient,
            executionRepository
        );
    }

    @Provides
    RolePermissionRegistry rolePermissionRegistry() {
        InMemoryRolePermissionRegistry registry = new InMemoryRolePermissionRegistry();
        
        // YAPPC-specific role permissions
        // OWNER: Full access to all resources within tenant
        registry.registerRole("OWNER", Set.of(
            "workspace:*",
            "project:*",
            "artifact:*",
            "lifecycle:*",
            "preview:*",
            "generation:*",
            "admin:system"
        ));
        
        // ADMIN: Administrative access within tenant
        registry.registerRole("ADMIN", Set.of(
            "workspace:read",
            "workspace:write",
            "project:read",
            "project:write",
            "artifact:read",
            "artifact:write",
            "lifecycle:execute",
            "preview:create",
            "generation:execute",
            "generation:review"
        ));
        
        // DEVELOPER: Standard development access
        registry.registerRole("DEVELOPER", Set.of(
            "workspace:read",
            "project:read",
            "project:write",
            "artifact:read",
            "artifact:write",
            "lifecycle:execute",
            "preview:create",
            "generation:execute"
        ));
        
        // VIEWER: Read-only access
        registry.registerRole("VIEWER", Set.of(
            "workspace:read",
            "project:read",
            "artifact:read",
            "lifecycle:read",
            "preview:read"
        ));
        
        return registry;
    }

    @Provides
    SyncAuthorizationService syncAuthorizationService(RolePermissionRegistry rolePermissionRegistry) {
        return new SyncAuthorizationService(rolePermissionRegistry);
    }

    @Provides
    YappcAuthorizationService yappcAuthorizationService(SyncAuthorizationService syncAuthorizationService) {
        return new YappcAuthorizationService(syncAuthorizationService);
    }

    @Provides
    PreviewSessionApiController previewSessionApiController(
            ObjectMapper objectMapper,
            AuditLogger auditLogger,
            YappcAuthorizationService yappcAuthorizationService,
            PreviewSecurityPolicy previewSecurityPolicy) {
        String signingSecret = System.getenv("YAPPC_PREVIEW_SESSION_SECRET");
        boolean isProduction = Boolean.parseBoolean(System.getenv().getOrDefault("YAPPC_PRODUCTION", "false"));
        return PreviewSessionApiController.createProductionSafe(
            objectMapper,
            signingSecret,
            auditLogger,
            yappcAuthorizationService,
            previewSecurityPolicy,
            isProduction
        );
    }

    @Provides
    RouteAuthorizationRegistry routeAuthorizationRegistry(YappcAuthorizationService yappcAuthorizationService) {
        return new RouteAuthorizationRegistry(yappcAuthorizationService);
    }

    @Provides
    PreviewSecurityPolicy previewSecurityPolicy() {
        boolean isProduction = Boolean.parseBoolean(System.getenv().getOrDefault("YAPPC_PRODUCTION", "false"));
        if (isProduction) {
            return PreviewSecurityPolicy.productionDefaults();
        } else {
            return PreviewSecurityPolicy.developmentDefaults();
        }
    }

    @Provides
    LifecycleExecutionRepository lifecycleExecutionRepository(DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcLifecycleExecutionRepository(dataSource, objectMapper);
    }

    @Provides
    AuditLogger auditLogger(DataSource dataSource, ObjectMapper objectMapper) {
        // P1-10: Provide real JdbcAuditLogger instead of no-op
        // Production startup guard in YappcHttpServer ensures DATABASE_URL is configured
        return new JdbcAuditLogger(dataSource, objectMapper);
    }

    @Provides
    AuditService phasePacketAuditService() {
        return new DegradedAuditService("PLATFORM_AUDIT_SERVICE_UNAVAILABLE");
    }

    @Provides
    com.ghatana.core.runtime.PreviewRuntimeService phasePacketPreviewRuntimeService() {
        return new DegradedPreviewRuntimeService("PREVIEW_RUNTIME_SERVICE_UNAVAILABLE");
    }

    @Provides
    PhasePacketService phasePacketService(
            DataCloudClient dataCloudClient,
            YappcArtifactRepository artifactRepository,
            PhaseGateValidator phaseGateValidator,
            PolicyEngine policyEngine,
            CapabilityEvaluationService capabilityEvaluationService,
            TransitionConfigLoader transitionConfigLoader,
            PlatformIntegrationClient platformIntegrationClient,
            @Nullable BusinessMetrics metrics,
            AuditService phasePacketAuditService,
            com.ghatana.core.runtime.PreviewRuntimeService phasePacketPreviewRuntimeService,
            @Nullable com.ghatana.audit.AuditLogger auditLogger) {
        return new PhasePacketServiceImpl(
            dataCloudClient,
            artifactRepository,
            phaseGateValidator,
            policyEngine,
            capabilityEvaluationService,
            transitionConfigLoader,
            platformIntegrationClient,
            metrics,
            phasePacketAuditService,
            phasePacketPreviewRuntimeService,
            auditLogger
        );
    }

    @Provides
    PhasePacketController phasePacketController(ObjectMapper objectMapper, PhasePacketService phasePacketService) {
        return new PhasePacketController(objectMapper, phasePacketService);
    }
}
