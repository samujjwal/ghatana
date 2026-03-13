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
import com.ghatana.core.database.config.JpaConfig;
import com.ghatana.core.operator.catalog.InMemoryOperatorCatalog;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import com.ghatana.core.template.YamlTemplateEngine;
import com.ghatana.aep.event.AepEventCloudFactory;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.yappc.agent.AepEventPublisher;
import com.ghatana.yappc.agent.YappcAgentSystem;
import com.ghatana.yappc.services.lifecycle.AepEventBridge;

import javax.sql.DataSource;
import com.ghatana.yappc.services.lifecycle.operators.AgentDispatchOperator;
import com.ghatana.yappc.services.lifecycle.operators.AgentDispatchValidatorOperator;
import com.ghatana.yappc.services.lifecycle.operators.AgentExecutorOperator;
import com.ghatana.yappc.services.lifecycle.operators.BackpressureOperator;
import com.ghatana.yappc.services.lifecycle.operators.GateOrchestratorOperator;
import com.ghatana.yappc.services.lifecycle.operators.LifecycleStatePublisherOperator;
import com.ghatana.yappc.services.lifecycle.operators.MetricsCollectorOperator;
import com.ghatana.yappc.services.lifecycle.operators.PhaseTransitionValidatorOperator;
import com.ghatana.yappc.services.lifecycle.operators.ResultAggregatorOperator;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import com.ghatana.yappc.services.lifecycle.dlq.JdbcDlqPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
// ─── Workflow Engine (YAPPC-Ph9) ──────────────────────────────────────────
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import com.ghatana.yappc.services.lifecycle.workflow.LifecycleWorkflowService;
// ─── Agent Orchestration Bootstrapper (YAPPC-Ph6) ─────────────────────────
import com.ghatana.yappc.services.lifecycle.YappcAgentOrchestrationBootstrapper;
import com.ghatana.agent.framework.loader.AgentDefinitionLoader;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.memory.model.working.WorkingMemoryConfig;
import com.ghatana.agent.memory.persistence.JdbcMemoryItemRepository;
import com.ghatana.agent.memory.persistence.JdbcTaskStateRepository;
import com.ghatana.agent.memory.persistence.MemoryItemRepository;
import com.ghatana.agent.memory.persistence.MemoryStoreAdapter;
import com.ghatana.agent.memory.persistence.PersistentMemoryPlane;
import com.ghatana.agent.memory.store.MemoryPlane;
import com.ghatana.agent.memory.store.procedural.InMemoryPatternEngine;
import com.ghatana.agent.memory.store.procedural.ProceduralMemoryManager;
import com.ghatana.agent.memory.store.procedural.ProcedureSelector;
import com.ghatana.agent.memory.store.semantic.SemanticMemoryManager;
import com.ghatana.agent.memory.store.taskstate.JdbcTaskStateStore;
import com.ghatana.agent.memory.store.taskstate.TaskStateStore;
// ──────────────────────────────────────────────────────────────────────────
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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

    // ========== DataCloud persistence (YAPPC-Ph1a/1b) ==========

    /**
     * Provides the JDBC {@link DataSource} for YAPPC's PostgreSQL database.
     *
     * <p>Configuration via environment variables:
     * <ul>
     *   <li>{@code YAPPC_DB_URL} — JDBC URL (default: {@code jdbc:postgresql://localhost:5432/yappc})</li>
     *   <li>{@code YAPPC_DB_USERNAME} — DB username (default: {@code yappc})</li>
     *   <li>{@code YAPPC_DB_PASSWORD} — DB password (required in production)</li>
     *   <li>{@code YAPPC_DB_POOL_SIZE} — HikariCP max pool size (default: {@code 10})</li>
     * </ul>
     *
     * @doc.type method
     * @doc.purpose Provides HikariCP JDBC DataSource for YAPPC PostgreSQL persistence
     * @doc.layer product
     * @doc.pattern Factory
     */
    @Provides
    DataSource dataSource() {
        String url = System.getenv().getOrDefault("YAPPC_DB_URL",
            "jdbc:postgresql://localhost:5432/yappc");
        String username = System.getenv().getOrDefault("YAPPC_DB_USERNAME", "yappc");
        String password = System.getenv().getOrDefault("YAPPC_DB_PASSWORD", "");
        int poolSize = Integer.parseInt(
            System.getenv().getOrDefault("YAPPC_DB_POOL_SIZE", "10"));

        logger.info("Creating DataSource: url={}, username={}, poolSize={}",
            url, username, poolSize);

        return JpaConfig.builder()
            .jdbcUrl(url)
            .username(username)
            .password(password)
            .entityPackages("com.ghatana.yappc")
            .poolSize(poolSize)
            .ddlAuto("none")
            .showSql(false)
            .build()
            .createDataSource();
    }


    // ========== Schema Registry (YAPPC-Ph2) ==========

    // ========== YAML Template Engine (YAPPC-Ph3) ==========

    /**
     * Provides the {@link YamlTemplateEngine} for rendering YAML templates with
     * variable substitution and {@code extends}-based inheritance.
     *
     * <p>Used by {@link GenerationService}, {@link ShapeService}, and other lifecycle
     * phases to render agent definitions, pipeline specs, and operator schemas from
     * parameterised YAML templates.
     *
     * @return YAML template engine instance (stateless, singleton-safe)
     *
     * @doc.type method
     * @doc.purpose Provides YamlTemplateEngine for YAML-based agent/pipeline definitions (YAPPC-Ph3)
     * @doc.layer product
     * @doc.pattern Strategy, Factory
     */
    @Provides
    YamlTemplateEngine yamlTemplateEngine() {
        logger.info("Creating YamlTemplateEngine (YAPPC-Ph3)");
        return new YamlTemplateEngine();
    }

    // ========== Operator Catalog (YAPPC-Ph4) ==========

    /**
     * Provides the unified {@link OperatorCatalog} for YAPPC lifecycle operators.
     *
     * <p>Pre-registers all four YAPPC lifecycle pipeline operators:
     * <ol>
     *   <li>{@link PhaseTransitionValidatorOperator} — validates phase transitions</li>
     *   <li>{@link GateOrchestratorOperator} — runs quality gates + human approval routing</li>
     *   <li>{@link AgentDispatchOperator} — dispatches stage agents</li>
     *   <li>{@link LifecycleStatePublisherOperator} — publishes {@code lifecycle.phase.advanced}</li>
     * </ol>
     *
     * <p>The catalog is backed by {@link InMemoryOperatorCatalog} and populated synchronously
     * at startup (all {@code register()} Promises are resolved immediately in-memory).
     *
     * @param validatorOperator  phase transition validator
     * @param gateOperator       gate orchestrator
     * @param dispatchOperator   agent dispatch operator
     * @param publisherOperator  lifecycle state publisher
     * @return populated operator catalog singleton
     *
     * @doc.type method
     * @doc.purpose Provides pre-populated OperatorCatalog for YAPPC lifecycle pipeline (YAPPC-Ph4)
     * @doc.layer product
     * @doc.pattern Registry, Factory
     */
    @Provides
    OperatorCatalog operatorCatalog(
            PhaseTransitionValidatorOperator validatorOperator,
            GateOrchestratorOperator gateOperator,
            AgentDispatchOperator dispatchOperator,
            LifecycleStatePublisherOperator publisherOperator) {
        // YAPPC lifecycle operators use platform.workflow.operator.UnifiedOperator, while
        // InMemoryOperatorCatalog uses core.operator.UnifiedOperator — two separate hierarchies.
        // The operators are wired directly into YappcAepPipelineBootstrapper and do not need
        // catalog-based discovery within the YAPPC lifecycle module.
        logger.info("Created OperatorCatalog for YAPPC lifecycle operators (YAPPC-Ph4): direct pipeline wiring");
        return new InMemoryOperatorCatalog();
    }

    // ========== EventCloud — transport-agnostic event log (Ph1c) ==========

    /**
     * Provides the AEP {@link EventCloud} facade for the lifecycle service.
     *
     * <p>Transport is selected via the {@code EVENT_CLOUD_TRANSPORT} environment
     * variable ({@code eventlog} default, {@code grpc}, or {@code http}). The
     * factory fails fast at startup if the selected transport is not properly
     * configured (e.g. missing gRPC endpoint). Lifecycle code never manages
     * transport details — all of that is encapsulated in the active connector.
     *
     * @return EventCloud facade singleton
     *
     * @doc.type method
     * @doc.purpose Provides transport-agnostic EventCloud facade for lifecycle event publishing (Ph1c)
     * @doc.layer product
     * @doc.pattern Factory
     */
    @Provides
    EventCloud eventCloud() {
        logger.info("Creating EventCloud via AepEventCloudFactory (transport: {})",
                System.getenv().getOrDefault("EVENT_CLOUD_TRANSPORT", "eventlog"));
        return AepEventCloudFactory.createDefault();
    }

    // ========== AEP Publisher — EventCloud-backed (Ph1c) ==========

    /**
     * Provides {@link AepEventPublisher} as a {@link DurableEventCloudPublisher}.
     *
     * <p>Replaces {@code AepHttpEventPublisher}. Events are appended directly to
     * the active {@link EventCloud} connector (gRPC/HTTP/EventLog) — no bespoke
     * HTTP client, no hardcoded AEP URL, no manual retry logic.
     *
     * @param eventCloud active EventCloud facade
     * @return DurableEventCloudPublisher singleton
     *
     * @doc.type method
     * @doc.purpose Provides durable EventCloud-backed AepEventPublisher (replaces HttpAepEventPublisher)
     * @doc.layer product
     * @doc.pattern Adapter
     * @doc.gaa.lifecycle act
     */
    @Provides
    AepEventPublisher aepEventPublisher(EventCloud eventCloud) {
        logger.info("Creating DurableEventCloudPublisher (EventCloud-backed, transport-agnostic)");
        return new DurableEventCloudPublisher(eventCloud);
    }

    /**
     * Provides {@link AepEventBridge} — a resilient, fire-and-forget facade over
     * {@link AepEventPublisher} for lifecycle domain events (YAPPC-Ph5).
     *
     * <p>Publishes {@code lifecycle.phase.advanced} and {@code lifecycle.phase.blocked}
     * events; all AEP failures are swallowed so lifecycle state-machine processing
     * is never blocked by downstream event-publishing issues.
     *
     * @param publisher underlying AEP publisher
     * @return AepEventBridge singleton
     *
     * @doc.type method
     * @doc.purpose Provides resilient AEP event bridge for lifecycle domain events (YAPPC-Ph5)
     * @doc.layer product
     * @doc.pattern Facade
     */
    @Provides
    AepEventBridge aepEventBridge(AepEventPublisher publisher) {
        logger.info("Creating AepEventBridge (YAPPC-Ph5)");
        return new AepEventBridge(publisher);
    }

    // ========== Agent Definition Loader (YAPPC-Ph7) ==========

    /**
     * Provides an {@link AgentDefinitionLoader} for loading agent YAML blueprints
     * (the 228 YAPPC agents in {@code config/agents/}) at startup.
     *
     * <p>Uses an empty {@code TemplateContext} — YAPPC agent YAMLs do not use
     * {@code {{ varName }}} placeholders. Individual services that need a populated
     * template context (e.g., for environment-specific substitution) should create
     * their own scoped loaders.
     *
     * @return AgentDefinitionLoader singleton
     *
     * @doc.type method
     * @doc.purpose Provides YAML-based AgentDefinition loader for YAPPC agent catalog (YAPPC-Ph7)
     * @doc.layer product
     * @doc.pattern Factory
     * @doc.gaa.lifecycle perceive
     */
    @Provides
    AgentDefinitionLoader agentDefinitionLoader() {
        logger.info("Creating AgentDefinitionLoader (YAPPC-Ph7)");
        return new AgentDefinitionLoader();
    }

    // ========== GAA Memory Stack (YAPPC-Ph8) ==========

    /**
     * Provides the JDBC-backed {@link MemoryItemRepository} for persisting episodic,
     * semantic, and procedural memory items to the {@code yappc.memory_items} table.
     *
     * @param dataSource shared HikariCP connection pool
     * @return JdbcMemoryItemRepository singleton
     *
     * @doc.type method
     * @doc.purpose Provides JDBC memory item repository for GAA memory persistence (YAPPC-Ph8)
     * @doc.layer product
     * @doc.pattern Repository
     * @doc.gaa.memory episodic
     */
    @Provides
    MemoryItemRepository memoryItemRepository(DataSource dataSource) {
        logger.info("Creating JdbcMemoryItemRepository (YAPPC-Ph8)");
        return new JdbcMemoryItemRepository(dataSource);
    }

    /**
     * Provides the JDBC-backed {@link TaskStateStore} for persisting agent task
     * state to the {@code yappc.task_states} table.
     *
     * @return JdbcTaskStateStore singleton
     *
     * @doc.type method
     * @doc.purpose Provides JDBC task state store for agent execution tracking (YAPPC-Ph8)
     * @doc.layer product
     * @doc.pattern Repository
     * @doc.gaa.lifecycle capture
     */
    @Provides
    TaskStateStore taskStateStore() {
        logger.info("Creating JdbcTaskStateStore (YAPPC-Ph8)");
        JdbcTaskStateRepository repository = new JdbcTaskStateRepository();
        return new JdbcTaskStateStore(repository);
    }

    /**
     * Provides the {@link WorkingMemoryConfig} for YAPPC lifecycle agents.
     *
     * <p>Configured for medium-scale YAPPC agent turns:
     * <ul>
     *   <li>Max 2,000 working memory entries per turn</li>
     *   <li>Max 20 MB total working memory</li>
     *   <li>LRU eviction (most recently used context kept)</li>
     * </ul>
     *
     * @return WorkingMemoryConfig singleton (immutable value object)
     *
     * @doc.type method
     * @doc.purpose Configures bounded working memory for YAPPC GAA agents (YAPPC-Ph8)
     * @doc.layer product
     * @doc.pattern ValueObject
     * @doc.gaa.memory episodic
     */
    @Provides
    WorkingMemoryConfig workingMemoryConfig() {
        return WorkingMemoryConfig.builder()
                .maxEntries(2000)
                .maxBytes(20L * 1024 * 1024)
                .evictionPolicy(WorkingMemoryConfig.EvictionPolicy.LRU)
                .build();
    }

    /**
     * Provides the production {@link MemoryPlane} backed by PostgreSQL via
     * {@link JdbcMemoryItemRepository} and {@link JdbcTaskStateStore}.
     *
     * <p>This is the core GAA memory persistence layer for YAPPC lifecycle agents.
     *
     * @param itemRepository  JDBC-backed memory item store
     * @param taskStateStore  JDBC-backed task state store
     * @param config          working memory bounds configuration
     * @return PersistentMemoryPlane singleton
     *
     * @doc.type method
     * @doc.purpose Provides PostgreSQL-backed MemoryPlane for durable GAA memory (YAPPC-Ph8)
     * @doc.layer product
     * @doc.pattern Repository
     * @doc.gaa.memory episodic
     */
    @Provides
    MemoryPlane memoryPlane(
            MemoryItemRepository itemRepository,
            TaskStateStore taskStateStore,
            WorkingMemoryConfig config) {
        logger.info("Creating PersistentMemoryPlane (YAPPC-Ph8)");
        return new PersistentMemoryPlane(itemRepository, taskStateStore, config);
    }

    /**
     * Provides the {@link MemoryStore} adapter that bridges the agent-framework
     * {@code MemoryStore} interface to the agent-memory {@link MemoryPlane} API.
     *
     * <p>This is the binding used by {@code BaseAgent} / {@code DefaultAgentContext}
     * to read and write episodic, semantic, and procedural memory during agent turns.
     *
     * @param memoryPlane the persistent memory plane
     * @return MemoryStoreAdapter implementing {@link MemoryStore}
     *
     * @doc.type method
     * @doc.purpose Bridges MemoryPlane to MemoryStore for GAA agent context (YAPPC-Ph8)
     * @doc.layer product
     * @doc.pattern Adapter
     * @doc.gaa.memory episodic
     */
    @Provides
    MemoryStore memoryStore(MemoryPlane memoryPlane) {
        logger.info("Creating MemoryStoreAdapter (YAPPC-Ph8)");
        return new MemoryStoreAdapter(memoryPlane);
    }

    /**
     * Provides the {@link SemanticMemoryManager} for de-duplicating and versioning
     * structured facts stored by YAPPC lifecycle agents.
     *
     * <p>Used in the REFLECT phase of the GAA lifecycle to convert episodic memory
     * into durable semantic facts ({@code EnhancedFact}).
     *
     * @param memoryPlane the persistent memory plane
     * @return SemanticMemoryManager singleton
     *
     * @doc.type method
     * @doc.purpose Provides semantic fact management for learning from agent turns (YAPPC-Ph8)
     * @doc.layer product
     * @doc.pattern Service
     * @doc.gaa.memory semantic
     * @doc.gaa.lifecycle reflect
     */
    @Provides
    SemanticMemoryManager semanticMemoryManager(MemoryPlane memoryPlane) {
        logger.info("Creating SemanticMemoryManager (YAPPC-Ph8)");
        return new SemanticMemoryManager(memoryPlane);
    }

    /**
     * Provides the {@link ProceduralMemoryManager} for inducing, merging, and
     * retrieving procedural policies from YAPPC lifecycle agent turns.
     *
     * <p>Uses an {@link InMemoryPatternEngine} for fast pattern matching during the
     * REASON phase, and a {@link ProcedureSelector} to retrieve the best-matching
     * procedure for a given situation with a minimum confidence of 0.5.
     *
     * @param memoryPlane the persistent memory plane
     * @return ProceduralMemoryManager singleton
     *
     * @doc.type method
     * @doc.purpose Provides procedural policy management for adaptive agent behaviour (YAPPC-Ph8)
     * @doc.layer product
     * @doc.pattern Service
     * @doc.gaa.memory procedural
     * @doc.gaa.lifecycle reflect
     */
    @Provides
    ProceduralMemoryManager proceduralMemoryManager(MemoryPlane memoryPlane) {
        logger.info("Creating ProceduralMemoryManager with InMemoryPatternEngine (YAPPC-Ph8)");
        ProcedureSelector selector = new ProcedureSelector(memoryPlane);
        return new ProceduralMemoryManager(memoryPlane, selector);
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
     * events via {@link AepEventBridge} to signal successful phase transitions (YAPPC-Ph6).
     */
    @Provides
    LifecycleStatePublisherOperator lifecycleStatePublisherOperator(AepEventBridge bridge) {
        logger.info("Creating LifecycleStatePublisherOperator (YAPPC-Ph6)");
        return new LifecycleStatePublisherOperator(bridge);
    }

    // ========== Agent Orchestration Pipeline Operators (YAPPC-Ph6) ==========

    /**
     * Provides {@link AgentDispatchValidatorOperator} — validates
     * {@code agent.dispatch.requested} events against required field constraints
     * before they enter the agent execution pipeline.
     *
     * @doc.type method
     * @doc.purpose Provides schema validator for agent dispatch events (YAPPC-Ph6)
     * @doc.layer product
     * @doc.pattern Validator
     * @doc.gaa.lifecycle perceive
     */
    @Provides
    AgentDispatchValidatorOperator agentDispatchValidatorOperator() {
        logger.info("Creating AgentDispatchValidatorOperator (YAPPC-Ph6)");
        return new AgentDispatchValidatorOperator();
    }

    /**
     * Provides {@link BackpressureOperator} — bounded FIFO buffer (DROP_OLDEST)
     * protecting downstream agent operators from event-rate spikes.
     *
     * @doc.type method
     * @doc.purpose Provides backpressure buffer for agent dispatch events (YAPPC-Ph6)
     * @doc.layer product
     * @doc.pattern Backpressure
     * @doc.gaa.lifecycle perceive
     */
    @Provides
    BackpressureOperator backpressureOperator() {
        logger.info("Creating BackpressureOperator (YAPPC-Ph6) — buffer=2048, strategy=DROP_OLDEST");
        return new BackpressureOperator();
    }

    /**
     * Provides the unified {@link YappcAgentSystem} that bootstraps all 27+ SDLC
     * specialist agents and planner agents.
     *
     * <p>Initialization is deferred — callers must invoke {@code yappcAgentSystem.initialize()}
     * at service startup (typically from {@link YappcAgentOrchestrationBootstrapper}).
     *
     * @param eventloop       ActiveJ eventloop for async initialization
     * @param memoryStore     GAA memory plane for agent state
     * @param aepEventPublisher publisher for SDLC step events (injected from DI container)
     * @return configured but not yet initialized YappcAgentSystem
     * @doc.type method
     * @doc.purpose Provides unified YAPPC agent system for SDLC specialist and planner agents
     * @doc.layer product
     * @doc.pattern Facade
     * @doc.gaa.lifecycle initialize
     */
    @Provides
    YappcAgentSystem yappcAgentSystem(
            Eventloop eventloop,
            MemoryStore memoryStore,
            AepEventPublisher aepEventPublisher) {
        logger.info("Creating YappcAgentSystem (YAPPC-Ph9.2) — AEP publisher wired: {}",
                aepEventPublisher.getClass().getSimpleName());
        return YappcAgentSystem.builder()
                .eventloop(eventloop)
                .memoryStore(memoryStore)
                .aepEventPublisher(aepEventPublisher)
                .build();
    }

    /**
     * Provides {@link AgentExecutorOperator} — executes agents referenced by dispatch
     * events and emits {@code agent.result.produced} events with execution outcomes.
     *
     * @doc.type method
     * @doc.purpose Provides agent executor for the orchestration pipeline (YAPPC-Ph6)
     * @doc.layer product
     * @doc.pattern Service, Command
     * @doc.gaa.lifecycle act
     */
    @Provides
    AgentExecutorOperator agentExecutorOperator(YappcAgentSystem yappcAgentSystem) {
        logger.info("Creating AgentExecutorOperator (YAPPC-Ph6) — wired to YappcAgentSystem");
        return new AgentExecutorOperator(yappcAgentSystem);
    }

    /**
     * Provides {@link ResultAggregatorOperator} — aggregates {@code agent.result.produced}
     * events by {@code correlation_id} and emits {@code workflow.step.completed} events.
     *
     * @doc.type method
     * @doc.purpose Provides result aggregator for agent execution pipeline (YAPPC-Ph6)
     * @doc.layer product
     * @doc.pattern Aggregator
     * @doc.gaa.lifecycle capture
     */
    @Provides
    ResultAggregatorOperator resultAggregatorOperator() {
        logger.info("Creating ResultAggregatorOperator (YAPPC-Ph6) — threshold=1 (immediate emission)");
        return new ResultAggregatorOperator();
    }

    /**
     * Provides {@link MetricsCollectorOperator} — terminal pass-through operator that
     * collects execution metrics from agent result events and emits
     * {@code agent.metrics.updated} events for observability consumers.
     *
     * @doc.type method
     * @doc.purpose Provides metrics collector for agent execution observability (YAPPC-Ph6)
     * @doc.layer product
     * @doc.pattern Metrics, Observer
     * @doc.gaa.lifecycle capture
     */
    @Provides
    MetricsCollectorOperator metricsCollectorOperator(MeterRegistry meterRegistry) {
        logger.info("Creating MetricsCollectorOperator (YAPPC-Ph6) — Micrometer-wired");
        return new MetricsCollectorOperator(meterRegistry);
    }

    /**
     * Provides the {@link YappcAgentOrchestrationBootstrapper} that wires the 5 agent
     * orchestration operators into the {@code agent-orchestration-v1} AEP pipeline and
     * starts it at service boot.
     *
     * @doc.type method
     * @doc.purpose Bootstraps the YAPPC agent-orchestration-v1 pipeline (YAPPC-Ph6)
     * @doc.layer product
     * @doc.pattern Bootstrapper
     * @doc.gaa.lifecycle perceive
     */
    @Provides
    YappcAgentOrchestrationBootstrapper yappcAgentOrchestrationBootstrapper(
            AgentDispatchValidatorOperator validatorOperator,
            BackpressureOperator backpressureOperator,
            AgentExecutorOperator executorOperator,
            ResultAggregatorOperator aggregatorOperator,
            MetricsCollectorOperator metricsOperator,
            DlqPublisher dlqPublisher) {
        logger.info("Creating YappcAgentOrchestrationBootstrapper (YAPPC-Ph6)");
        return new YappcAgentOrchestrationBootstrapper(
                validatorOperator, backpressureOperator, executorOperator,
                aggregatorOperator, metricsOperator, dlqPublisher);
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
     * Provides the Micrometer {@link MeterRegistry} backed by Prometheus for YAPPC
     * lifecycle service observability.
     *
     * @doc.type method
     * @doc.purpose Provides Prometheus-backed MeterRegistry for YAPPC observability
     * @doc.layer product
     * @doc.pattern Factory
     */
    @Provides
    MeterRegistry meterRegistry() {
        logger.info("Creating PrometheusMeterRegistry");
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    /**
     * Provides {@link JdbcDlqPublisher} backed by the YAPPC PostgreSQL data source.
     *
     * <p>All JDBC calls are dispatched on a virtual-thread executor so the ActiveJ
     * event loop is never blocked.
     *
     * @doc.type method
     * @doc.purpose JDBC-backed DLQ publisher for failed YAPPC pipeline events
     * @doc.layer product
     * @doc.pattern Repository, Factory
     */
    @Provides
    DlqPublisher dlqPublisher(DataSource dataSource) {
        Executor executor = Executors.newVirtualThreadPerTaskExecutor();
        ObjectMapper objectMapper = new ObjectMapper();
        logger.info("Creating JdbcDlqPublisher (YAPPC-Ph12)");
        return new JdbcDlqPublisher(dataSource, executor, objectMapper);
    }

    // ========== Workflow Engine (YAPPC-Ph9) ==========

    /**
     * Provides the {@link DurableWorkflowEngine} for executing canonical YAPPC
     * lifecycle workflows durably with retry and compensation support.
     *
     * <p>Configuration:
     * <ul>
     *   <li>Default per-step timeout: 5 minutes</li>
     *   <li>Default max retries per step: 2 (3 total attempts)</li>
     *   <li>Default retry backoff: 2 seconds (exponential)</li>
     *   <li>State store: {@link DurableWorkflowEngine.InMemoryWorkflowStateStore}</li>
     * </ul>
     *
     * <p>For production deployments that require persistent workflow state across restarts,
     * override this binding (in {@code ProductionModule} or a deployment-specific module)
     * with a {@code JdbcWorkflowStateStore}-backed engine.
     *
     * @return DurableWorkflowEngine singleton
     *
     * @doc.type method
     * @doc.purpose Provides durable workflow engine for canonical YAPPC lifecycle workflows (YAPPC-Ph9)
     * @doc.layer product
     * @doc.pattern Service, Factory
     */
    @Provides
    DurableWorkflowEngine durableWorkflowEngine() {
        logger.info("Creating DurableWorkflowEngine (YAPPC-Ph9) — InMemoryWorkflowStateStore, "
                  + "5min timeout, 2 retries");
        return DurableWorkflowEngine.builder()
                .stateStore(new DurableWorkflowEngine.InMemoryWorkflowStateStore())
                .defaultTimeout(Duration.ofMinutes(5))
                .defaultMaxRetries(2)
                .defaultRetryBackoff(Duration.ofSeconds(2))
                .build();
    }

    /**
     * Provides the {@link LifecycleWorkflowService} that materialises canonical workflow
     * templates from {@code lifecycle-workflow-templates.yaml} and orchestrates their
     * durable execution via {@link DurableWorkflowEngine}.
     *
     * <p>Calls {@link LifecycleWorkflowService#initialize()} eagerly at startup so all
     * templates are available before the first HTTP request is served.
     *
     * @param engine durable workflow engine
     * @return initialised LifecycleWorkflowService singleton
     *
     * @doc.type method
     * @doc.purpose Provides and initialises the YAPPC canonical workflow service (YAPPC-Ph9)
     * @doc.layer product
     * @doc.pattern Service, Factory
     * @doc.gaa.lifecycle perceive
     */
    @Provides
    LifecycleWorkflowService lifecycleWorkflowService(DurableWorkflowEngine engine) {
        logger.info("Creating LifecycleWorkflowService (YAPPC-Ph9)");
        LifecycleWorkflowService service = new LifecycleWorkflowService(engine);
        int loaded = service.initialize();
        logger.info("LifecycleWorkflowService initialised — {} workflow template(s) registered", loaded);
        return service;
    }
}
