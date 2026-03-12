/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service Module
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.api.GenerationApiController;
import com.ghatana.yappc.api.IntentApiController;
import com.ghatana.yappc.api.ShapeApiController;
import com.ghatana.yappc.api.ValidationApiController;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.evolve.EvolutionServiceImpl;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.generate.GenerationServiceImpl;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.intent.IntentServiceImpl;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.learn.LearningServiceImpl;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.observe.ObserveServiceImpl;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.run.RunServiceImpl;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.shape.ShapeServiceImpl;
import com.ghatana.yappc.services.validate.ValidationService;
import com.ghatana.yappc.services.validate.ValidationServiceImpl;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import com.ghatana.core.event.cloud.InMemoryEventCloud;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.yappc.agent.AepEventPublisher;
import com.ghatana.yappc.agent.DurableEventCloudPublisher;
import com.ghatana.yappc.services.lifecycle.operators.AgentDispatchOperator;
import com.ghatana.yappc.services.lifecycle.operators.GateOrchestratorOperator;
import com.ghatana.yappc.services.lifecycle.operators.LifecycleStatePublisherOperator;
import com.ghatana.yappc.services.lifecycle.operators.PhaseTransitionValidatorOperator;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ DI module for YAPPC Lifecycle services.
 *
 * <p>Provides bindings for all 8 SDLC phase services:
 * <ol>
 *   <li>{@link IntentService} — AI-assisted intent capture and analysis</li>
 *   <li>{@link ShapeService} — Architecture and domain modeling</li>
 *   <li>{@link GenerationService} — Code and artifact generation</li>
 *   <li>{@link RunService} — Build execution and task orchestration</li>
 *   <li>{@link ObserveService} — Runtime monitoring and observability</li>
 *   <li>{@link EvolutionService} — Progressive evolution planning</li>
 *   <li>{@link LearningService} — Pattern extraction and learning</li>
 *   <li>{@link ValidationService} — Security and policy compliance</li>
 * </ol>
 *
 * <p>Each service requires:
 * <ul>
 *   <li>{@link CompletionService} — LLM integration (from platform:ai-integration)</li>
 *   <li>{@link AuditLogger} — Audit logging (from platform:observability)</li>
 *   <li>{@link MetricsCollector} — Metrics (from platform:observability)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DI module for lifecycle phase services
 * @doc.layer product
 * @doc.pattern Module
 */
public class LifecycleServiceModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleServiceModule.class);

    @Override
    protected void configure() {
        logger.info("Configuring YAPPC Lifecycle Service DI bindings (8 phase services)");
    }

    // ========== Phase 1: Intent ==========

    /** Provides IntentService for AI-assisted intent capture and analysis. */
    @Provides
    IntentService intentService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating IntentService");
        return new IntentServiceImpl(aiService, auditLogger, metrics);
    }

    // ========== Phase 2: Shape ==========

    /** Provides ShapeService for architecture and domain modeling. */
    @Provides
    ShapeService shapeService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating ShapeService");
        return new ShapeServiceImpl(aiService, auditLogger, metrics);
    }

    // ========== Phase 3: Generate ==========

    /** Provides GenerationService for code and artifact generation. */
    @Provides
    GenerationService generationService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating GenerationService");
        return new GenerationServiceImpl(aiService, auditLogger, metrics);
    }

    // ========== Phase 4: Run ==========

    /** Provides RunService for build execution and orchestration. */
    @Provides
    RunService runService(
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating RunService");
        return new RunServiceImpl(auditLogger, metrics);
    }

    // ========== Phase 5: Observe ==========

    /** Provides ObserveService for runtime monitoring. */
    @Provides
    ObserveService observeService(
            MetricsCollector metrics,
            AuditLogger auditLogger) {
        logger.info("Creating ObserveService");
        return new ObserveServiceImpl(metrics, auditLogger);
    }

    // ========== Phase 6: Evolve ==========

    /** Provides EvolutionService for progressive evolution planning. */
    @Provides
    EvolutionService evolutionService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating EvolutionService");
        return new EvolutionServiceImpl(aiService, auditLogger, metrics);
    }

    // ========== Phase 7: Learn ==========

    /** Provides LearningService for pattern extraction and learning. */
    @Provides
    LearningService learningService(
            CompletionService aiService,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating LearningService");
        return new LearningServiceImpl(aiService, auditLogger, metrics);
    }

    // ========== Phase 8: Validate ==========

    /**
     * Provides ValidationService for security and policy compliance.
     *
     * <p>PolicyEngine is injected from platform governance modules.</p>
     */
    @Provides
    ValidationService validationService(
            com.ghatana.governance.PolicyEngine policyEngine,
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        logger.info("Creating ValidationService");
        return new ValidationServiceImpl(policyEngine, auditLogger, metrics);
    }

    // ========== HTTP API Controllers ==========

    /**
     * Provides the shared artifact repository used by all API controllers.
     * Using the no-arg constructor creates an in-memory store; callers should
     * replace this binding with DataCloudArtifactStore for production.
     */
    @Provides
    YappcArtifactRepository artifactRepository() {
        logger.info("Creating YappcArtifactRepository");
        return new YappcArtifactRepository();
    }

    /** Provides IntentApiController for Phase 1 (Intent) HTTP routes. */
    @Provides
    IntentApiController intentApiController(IntentService intentService, YappcArtifactRepository repo) {
        logger.info("Creating IntentApiController");
        return new IntentApiController(intentService, repo);
    }

    /** Provides ShapeApiController for Phase 2 (Shape) HTTP routes. */
    @Provides
    ShapeApiController shapeApiController(ShapeService shapeService, YappcArtifactRepository repo) {
        logger.info("Creating ShapeApiController");
        return new ShapeApiController(shapeService, repo);
    }

    /** Provides GenerationApiController for Phase 3 (Generate) HTTP routes. */
    @Provides
    GenerationApiController generationApiController(GenerationService generationService, YappcArtifactRepository repo) {
        logger.info("Creating GenerationApiController");
        return new GenerationApiController(generationService, repo);
    }

    /** Provides ValidationApiController for Phase 8 (Validate) HTTP routes. */
    @Provides
    ValidationApiController validationApiController(ValidationService validationService) {
        logger.info("Creating ValidationApiController");
        return new ValidationApiController(validationService);
    }

    // ========== Lifecycle Transitions ==========

    /**
     * Provides TransitionConfigLoader — lazy YAML loader for {@code lifecycle/transitions.yaml}.
     * Resolves external path ({@code yappc.config.dir}) first, then classpath.
     */
    @Provides
    TransitionConfigLoader transitionConfigLoader() {
        logger.info("Creating TransitionConfigLoader");
        return new TransitionConfigLoader();
    }

    /**
     * Provides AdvancePhaseUseCase — core use case for lifecycle phase transitions.
     * Validates transition rules, checks required artifacts, and evaluates policy gates.
     */
    @Provides
    AdvancePhaseUseCase advancePhaseUseCase(
            TransitionConfigLoader transitionConfigLoader,
            com.ghatana.governance.PolicyEngine policyEngine,
            YappcArtifactRepository artifactRepository) {
        logger.info("Creating AdvancePhaseUseCase");
        return new AdvancePhaseUseCase(transitionConfigLoader, policyEngine, artifactRepository);
    }

    // ========== Stage Config (3.3) ==========

    /**
     * Provides StageConfigLoader — lazy YAML loader for {@code lifecycle/stages.yaml}.
     * Resolves external path ({@code yappc.config.dir}) first, then classpath fallback.
     */
    @Provides
    StageConfigLoader stageConfigLoader() {
        logger.info("Creating StageConfigLoader");
        return new StageConfigLoader();
    }

    /**
     * Provides GateEvaluator — evaluates entry/exit criteria and artifact presence
     * for lifecycle stage gates.
     */
    @Provides
    GateEvaluator gateEvaluator() {
        logger.info("Creating GateEvaluator");
        return new GateEvaluator();
    }

    // ========== EventCloud + AEP Publisher (Ph1c) ==========

    /**
     * Provides EventCloud — in-memory implementation (Ph1b will wire the real EventCloud).
     *
     * @doc.gaa.lifecycle act
     */
    @Provides
    EventCloud eventCloud() {
        logger.info("Creating InMemoryEventCloud (TODO Ph1b: wire real EventCloud)");
        return new InMemoryEventCloud();
    }

    /**
     * Provides AepEventPublisher — EventCloud-backed durable publisher.
     */
    @Provides
    AepEventPublisher aepEventPublisher(EventCloud eventCloud) {
        logger.info("Creating DurableEventCloudPublisher");
        return DurableEventCloudPublisher.fromEnvironment(eventCloud);
    }

    // ========== Human Approval Gate (3.5) ==========

    /**
     * Provides HumanApprovalService — manages phase-advance human-approval gates.
     * Uses in-memory storage; a future JdbcHumanApprovalService will extend this
     * class to persist to the {@code yappc.approval_requests} table (V18 migration).
     */
    @Provides
    HumanApprovalService humanApprovalService(AepEventPublisher publisher) {
        logger.info("Creating HumanApprovalService");
        return new HumanApprovalService(publisher);
    }

    // ========== Lifecycle Pipeline Operators (7.1) ==========

    /**
     * Provides PhaseTransitionValidatorOperator — validates that a requested lifecycle
     * phase transition is permitted per {@code lifecycle/transitions.yaml} and the
     * entry-criteria gate for the target stage.
     */
    @Provides
    PhaseTransitionValidatorOperator phaseTransitionValidatorOperator(
            TransitionConfigLoader transitionConfigLoader,
            StageConfigLoader stageConfigLoader,
            GateEvaluator gateEvaluator) {
        logger.info("Creating PhaseTransitionValidatorOperator");
        return new PhaseTransitionValidatorOperator(transitionConfigLoader, stageConfigLoader, gateEvaluator);
    }

    /**
     * Provides GateOrchestratorOperator — runs policy checks and routes to
     * human-approval when entry criteria are not automatically satisfied.
     */
    @Provides
    GateOrchestratorOperator gateOrchestratorOperator(
            com.ghatana.governance.PolicyEngine policyEngine,
            HumanApprovalService humanApprovalService) {
        logger.info("Creating GateOrchestratorOperator");
        return new GateOrchestratorOperator(policyEngine, humanApprovalService);
    }

    /**
     * Provides AgentDispatchOperator — reads agent assignments from the target
     * {@link StageSpec} and emits {@code lifecycle.agent.dispatched} events.
     */
    @Provides
    AgentDispatchOperator agentDispatchOperator(StageConfigLoader stageConfigLoader) {
        logger.info("Creating AgentDispatchOperator");
        return new AgentDispatchOperator(stageConfigLoader);
    }

    /**
     * Provides LifecycleStatePublisherOperator — emits {@code lifecycle.phase.advanced}
     * events via {@link AepEventPublisher} to signal successful phase transitions.
     */
    @Provides
    LifecycleStatePublisherOperator lifecycleStatePublisherOperator(AepEventPublisher publisher) {
        logger.info("Creating LifecycleStatePublisherOperator");
        return new LifecycleStatePublisherOperator(publisher);
    }

    /**
     * Provides the {@link YappcAepPipelineBootstrapper} that wires the 4 lifecycle operators
     * into a sequential AEP pipeline and starts it at service boot.
     */
    @Provides
    YappcAepPipelineBootstrapper yappcAepPipelineBootstrapper(
            PhaseTransitionValidatorOperator validatorOperator,
            GateOrchestratorOperator gateOperator,
            AgentDispatchOperator dispatchOperator,
            LifecycleStatePublisherOperator publisherOperator,
            DlqPublisher dlqPublisher) {
        logger.info("Creating YappcAepPipelineBootstrapper");
        return new YappcAepPipelineBootstrapper(validatorOperator, gateOperator, dispatchOperator, publisherOperator, dlqPublisher);
    }

    /**
     * Provides a no-op {@link DlqPublisher} for standalone lifecycle service deployments.
     * Overridden by {@code ProductionModule} in {@code backend/api} with the JDBC implementation.
     *
     * @doc.type method
     * @doc.purpose Default (no-op) DLQ publisher for lifecycle-only deployments
     * @doc.layer product
     * @doc.pattern Factory
     */
    @Provides
    DlqPublisher dlqPublisher() {
        return DlqPublisher.noop();
    }
}
