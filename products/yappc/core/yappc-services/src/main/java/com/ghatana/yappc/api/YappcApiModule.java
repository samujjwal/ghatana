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
import com.ghatana.yappc.ai.PromptLifecycleService;
import com.ghatana.yappc.ai.PromptTemplateRegistry;
import com.ghatana.yappc.ai.abtesting.ABTestingEvaluationService;
import com.ghatana.yappc.services.capability.CapabilityEvaluationService;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.evolve.EvolutionServiceImpl;
import com.ghatana.yappc.services.evolve.ArtifactGraphEvolutionImpactAnalysisService;
import com.ghatana.yappc.services.evolve.DataCloudEvolutionPlanRepository;
import com.ghatana.yappc.services.evolve.DataCloudEvolutionExecutionHandoffService;
import com.ghatana.yappc.services.evolve.EvolutionExecutionHandoffDispatcher;
import com.ghatana.yappc.services.evolve.EvolutionExecutionHandoffSchedulerService;
import com.ghatana.yappc.services.evolve.EvolutionImpactAnalysisService;
import com.ghatana.yappc.services.evolve.EvolutionKernelUpdateService;
import com.ghatana.yappc.services.evolve.KernelProductUnitEvolutionUpdateService;
import com.ghatana.yappc.services.evolve.EvolutionLifecycleExecutionDispatcher;
import com.ghatana.yappc.services.evolve.LifecycleApiExecutionDispatcher;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.generate.GenerationAssuranceService;
import com.ghatana.yappc.services.generate.GenerationServiceImpl;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.intent.IntentServiceImpl;
import com.ghatana.yappc.services.intent.DataCloudIntentRepository;
import com.ghatana.yappc.services.intent.IntentRepository;
import com.ghatana.yappc.services.intent.IntentEvidenceService;
import com.ghatana.yappc.services.intent.PlatformIntentEvidenceService;
import com.ghatana.yappc.services.kernel.KernelProductUnitHandoffService;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.learn.LearningServiceImpl;
import com.ghatana.yappc.services.learn.DataCloudLearningEvidenceRepository;
import com.ghatana.yappc.services.learn.LearningEvidenceService;
import com.ghatana.yappc.services.learn.LearningEvidenceServiceImpl;
import com.ghatana.yappc.services.lifecycle.JdbcAuditLogger;
import com.ghatana.yappc.services.lifecycle.StageConfigLoader;
import com.ghatana.yappc.services.lifecycle.TransitionConfigLoader;
import com.ghatana.yappc.services.lifecycle.gate.PhaseGateValidator;
import com.ghatana.yappc.services.platform.PlatformIntegrationClient;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import com.ghatana.yappc.storage.ArtifactGraphRepository;
import com.ghatana.yappc.services.metrics.BusinessMetrics;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.observe.ObserveServiceImpl;
import com.ghatana.yappc.services.phase.PhasePacketService;
import com.ghatana.yappc.services.phase.PhasePacketServiceImpl;
import com.ghatana.yappc.services.phase.DegradedAuditService;
import com.ghatana.yappc.services.phase.DegradedPreviewRuntimeService;
import com.ghatana.yappc.services.phase.DataCloudPlatformRunStatusService;
import com.ghatana.yappc.services.phase.DegradedPhasePacketFactory;
import com.ghatana.yappc.services.phase.PhaseActionAuthorizationService;
import com.ghatana.yappc.services.phase.PlatformRunStatusService;
import com.ghatana.yappc.services.phase.PhaseRequiredArtifactProvider;
import com.ghatana.yappc.services.run.CiCdPort;
import com.ghatana.yappc.services.run.GitHubActionsCiCdAdapter;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.run.RunServiceImpl;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.shape.ShapeServiceImpl;
import com.ghatana.yappc.services.shape.DataCloudShapeRepository;
import com.ghatana.yappc.services.shape.ShapeRepository;
import com.ghatana.yappc.services.validate.ValidationService;
import com.ghatana.yappc.services.validate.ValidationServiceImpl;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.jetbrains.annotations.Nullable;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * @doc.type class
 * @doc.purpose Dependency injection module for YAPPC API
 * @doc.layer api
 * @doc.pattern Module
 */
public class YappcApiModule extends AbstractModule {

    @Provides
    IntentRepository intentRepository(DataCloudClient dataCloudClient) {
        return new DataCloudIntentRepository(dataCloudClient);
    }

    @Provides
    IntentEvidenceService intentEvidenceService(PlatformIntegrationClient platformIntegrationClient) {
        return new PlatformIntentEvidenceService(platformIntegrationClient);
    }

    @Provides
    IntentService intentService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            IntentRepository intentRepository,
            IntentEvidenceService intentEvidenceService) {
        return new IntentServiceImpl(aiService, auditLogger, metrics, intentRepository, intentEvidenceService);
    }

    @Provides
    ShapeRepository shapeRepository(DataCloudClient dataCloudClient) {
        return new DataCloudShapeRepository(dataCloudClient);
    }

    @Provides
    ShapeService shapeService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            ShapeRepository shapeRepository) {
        return new ShapeServiceImpl(aiService, auditLogger, metrics, shapeRepository);
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
            ObjectMapper objectMapper,
            GenerationAssuranceService generationAssuranceService) {
        return new GenerationServiceImpl(
                aiService,
                auditLogger,
                metrics,
                generationRunRepository,
                objectMapper,
                com.ghatana.yappc.services.generate.AiHealthProvider.alwaysHealthy(),
                generationAssuranceService);
    }

    @Provides
    GenerationAssuranceService generationAssuranceService() {
        return new GenerationAssuranceService();
    }

    @Provides
    CiCdPort ciCdPort() {
        return GitHubActionsCiCdAdapter.fromEnvironment();
    }

    @Provides
    RunService runService(
            AuditLogger auditLogger,
            MetricsCollector metrics,
            CiCdPort ciCdPort,
            LearningEvidenceService learningEvidenceService) {
        return new RunServiceImpl(auditLogger, metrics, ciCdPort, learningEvidenceService);
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
            MetricsCollector metrics,
            DataCloudClient dataCloudClient) {
        return new LearningServiceImpl(
                aiService,
                auditLogger,
                metrics,
                new DataCloudLearningEvidenceRepository(dataCloudClient));
    }

    @Provides
    LearningEvidenceService learningEvidenceService(DataCloudClient dataCloudClient) {
        return new LearningEvidenceServiceImpl(new DataCloudLearningEvidenceRepository(dataCloudClient));
    }

    @Provides
    EvolutionService evolutionService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics,
            DataCloudClient dataCloudClient,
            EvolutionImpactAnalysisService impactAnalysisService,
            EvolutionKernelUpdateService kernelUpdateService) {
        return new EvolutionServiceImpl(
                aiService,
                auditLogger,
                metrics,
            new DataCloudEvolutionPlanRepository(dataCloudClient),
            new DataCloudEvolutionExecutionHandoffService(dataCloudClient),
            impactAnalysisService,
            kernelUpdateService);
    }

    @Provides
    EvolutionImpactAnalysisService evolutionImpactAnalysisService(ArtifactGraphRepository artifactGraphRepository) {
        return new ArtifactGraphEvolutionImpactAnalysisService(artifactGraphRepository);
    }

    @Provides
    EvolutionKernelUpdateService evolutionKernelUpdateService(KernelProductUnitHandoffService handoffService) {
        return new KernelProductUnitEvolutionUpdateService(handoffService);
    }

    @Provides
    KernelProductUnitHandoffService kernelProductUnitHandoffService() {
        return new KernelProductUnitHandoffService();
    }

            @Provides
            EvolutionLifecycleExecutionDispatcher evolutionLifecycleExecutionDispatcher(
                LifecycleApiController lifecycleApiController,
                LifecycleExecutionRepository executionRepository,
                ObjectMapper objectMapper
            ) {
            return new LifecycleApiExecutionDispatcher(lifecycleApiController, executionRepository, objectMapper);
            }

            @Provides
            EvolutionExecutionHandoffDispatcher evolutionExecutionHandoffDispatcher(
                DataCloudClient dataCloudClient,
                EvolutionLifecycleExecutionDispatcher lifecycleDispatcher
            ) {
            return new EvolutionExecutionHandoffDispatcher(dataCloudClient, lifecycleDispatcher);
            }

            @Provides
            EvolutionExecutionHandoffSchedulerService evolutionExecutionHandoffSchedulerService(
                Eventloop eventloop,
                EvolutionExecutionHandoffDispatcher dispatcher
            ) {
            Map<String, String> config = Map.of(
                "yappc.scheduler.evolve-handoff.enabled", System.getenv().getOrDefault("YAPPC_EVOLVE_HANDOFF_SCHEDULER_ENABLED", "false"),
                "yappc.scheduler.evolve-handoff.interval", System.getenv().getOrDefault("YAPPC_EVOLVE_HANDOFF_SCHEDULER_INTERVAL_SECONDS", "30"),
                "yappc.scheduler.evolve-handoff.limit", System.getenv().getOrDefault("YAPPC_EVOLVE_HANDOFF_SCHEDULER_LIMIT", "25"),
                "yappc.scheduler.evolve-handoff.tenants", System.getenv().getOrDefault("YAPPC_EVOLVE_HANDOFF_SCHEDULER_TENANTS", "")
            );

            EvolutionExecutionHandoffSchedulerService scheduler =
                new EvolutionExecutionHandoffSchedulerService(eventloop, dispatcher, config);
            scheduler.start();
            return scheduler;
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
            "generation:review",
            "admin:system"
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
    AdminObservabilityController adminObservabilityController(ObjectMapper objectMapper) {
        return new AdminObservabilityController(
            objectMapper,
            java.nio.file.Path.of("."),
            Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    @Provides
    AdminFeatureFlagController adminFeatureFlagController(DataCloudClient dataCloudClient, ObjectMapper objectMapper) {
        return new AdminFeatureFlagController(dataCloudClient, objectMapper);
    }

    @Provides
    AdminAbTestingController adminAbTestingController(
            DataCloudClient dataCloudClient,
            ObjectMapper objectMapper,
            ABTestingEvaluationService abTestingEvaluationService,
            PromptLifecycleService promptLifecycleService) {
        return new AdminAbTestingController(
                dataCloudClient,
                objectMapper,
                abTestingEvaluationService,
                promptLifecycleService);
    }

    @Provides
    ABTestingEvaluationService abTestingEvaluationService() {
        return new ABTestingEvaluationService();
    }

    @Provides
    PromptTemplateRegistry promptTemplateRegistry() {
        return new PromptTemplateRegistry();
    }

    @Provides
    PromptLifecycleService promptLifecycleService(PromptTemplateRegistry registry, AuditLogger auditLogger) {
        return new PromptLifecycleService(registry, auditLogger);
    }

    @Provides
    AdminPromptVersionController adminPromptVersionController(
            DataCloudClient dataCloudClient,
            ObjectMapper objectMapper,
            PromptLifecycleService promptLifecycleService) {
        return new AdminPromptVersionController(dataCloudClient, objectMapper, promptLifecycleService);
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
    PlatformRunStatusService platformRunStatusService(DataCloudClient dataCloudClient) {
        return new DataCloudPlatformRunStatusService(dataCloudClient);
    }

    @Provides
    PhaseActionAuthorizationService phaseActionAuthorizationService() {
        return new PhaseActionAuthorizationService();
    }

    @Provides
    PhaseRequiredArtifactProvider phaseRequiredArtifactProvider(StageConfigLoader stageConfigLoader) {
        return new PhaseRequiredArtifactProvider(stageConfigLoader);
    }

    @Provides
    DegradedPhasePacketFactory degradedPhasePacketFactory() {
        return new DegradedPhasePacketFactory();
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
            PlatformRunStatusService platformRunStatusService,
            PhaseActionAuthorizationService phaseActionAuthorizationService,
            PhaseRequiredArtifactProvider phaseRequiredArtifactProvider,
            DegradedPhasePacketFactory degradedPhasePacketFactory,
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
            platformRunStatusService,
            phaseActionAuthorizationService,
            phaseRequiredArtifactProvider,
            degradedPhasePacketFactory,
            auditLogger
        );
    }

    @Provides
    PhasePacketController phasePacketController(ObjectMapper objectMapper, PhasePacketService phasePacketService) {
        return new PhasePacketController(objectMapper, phasePacketService);
    }
}
