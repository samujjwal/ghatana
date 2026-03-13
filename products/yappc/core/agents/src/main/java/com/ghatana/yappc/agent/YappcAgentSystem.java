package com.ghatana.yappc.agent;

import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.planner.AgentRegistry;
import com.ghatana.agent.framework.planner.PlannerAgentFactory;
import com.ghatana.agent.framework.runtime.BaseAgent;
import com.ghatana.agent.framework.runtime.generators.LLMGenerator;
import com.ghatana.yappc.agent.tools.YappcToolRegistry;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import com.ghatana.yappc.agent.YAPPCAgentRegistry;
import com.ghatana.yappc.agent.generators.LLMGeneratorFactory;
import com.ghatana.yappc.agent.leads.*;
import com.ghatana.yappc.agent.specialists.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified agent system for YAPPC.
 *
 * <p>Consolidates both agent subsystems into a single class:
 * <ul>
 *   <li><b>SDLC Specialists</b>: 27 specialist agents + 4 phase leads
 *       registered programmatically with LLM-powered generators</li>
 *   <li><b>Planner Agents</b>: YAML-defined agents loaded via
 *       {@code PlannerAgentFactory}</li>
 * </ul>
 *
 * <p>This is the <b>sole entry point</b> for all YAPPC agent initialization.
 *
 * <p><b>Usage</b>:
 * <pre>{@code
 * YappcAgentSystem system = YappcAgentSystem.builder()
 *     .eventloop(eventloop)
 *     .memoryStore(memoryStore)
 *     .llmGateway(gateway)
 *     .build();
 *
 * system.initialize().whenResult(() -> {
 *     BaseAgent planner = system.getPlannerAgent("products-officer");
 *     YAPPCAgentRegistry sdlc = system.getSdlcRegistry();
 * });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Unified YAPPC agent system with SDLC specialists and planner agents
 * @doc.layer product
 * @doc.pattern Facade
 * @doc.gaa.lifecycle initialize
 */
public class YappcAgentSystem {

    private static final Logger log = LoggerFactory.getLogger(YappcAgentSystem.class);

    // --- Planner subsystem fields ---
    private final Eventloop eventloop;
    private final PlannerAgentFactory agentFactory;
    private final AgentRegistry plannerRegistry;
    private final Map<String, BaseAgent<?, ?>> plannerAgentInstances;
    private final String configBasePath;

    // --- SDLC subsystem fields ---
    private final YAPPCAgentRegistry sdlcRegistry;
    private final MemoryStore memoryStore;
    private final LLMGenerator.LLMGateway llmGateway;
    private final LLMGenerator.LLMConfig llmConfig;

    // --- Observability ---
    private final AgentHeartbeatService heartbeatService;

    // --- Definition and catalog storage ---
    /** Raw YAML content keyed by agent id, loaded from {@code definitions/*.yaml}. */
    private final Map<String, Map<String, Object>> agentDefinitions;
    /** Catalog entries keyed by agent id, loaded from all domain catalogs via {@code _index.yaml}. */
    private final Map<String, Map<String, Object>> catalogEntries;

    // --- AEP integration (Phase 9.2) ---
    @Nullable
    private final AepEventPublisher aepEventPublisher;

    private boolean initialized = false;

    private YappcAgentSystem(
            @NotNull Eventloop eventloop,
            @NotNull String configBasePath,
            @NotNull YAPPCAgentRegistry sdlcRegistry,
            @NotNull MemoryStore memoryStore,
            @Nullable LLMGenerator.LLMGateway llmGateway,
            @NotNull LLMGenerator.LLMConfig llmConfig,
            @Nullable AepEventPublisher aepEventPublisher) {
        this.eventloop = eventloop;
        this.configBasePath = configBasePath;
        this.agentFactory = new PlannerAgentFactory();
        this.plannerRegistry = new AgentRegistry(agentFactory);
        this.plannerAgentInstances = new HashMap<>();
        this.agentDefinitions = new HashMap<>();
        this.catalogEntries = new HashMap<>();
        this.sdlcRegistry = sdlcRegistry;
        this.memoryStore = memoryStore;
        this.llmGateway = llmGateway;
        this.llmConfig = llmConfig;
        this.heartbeatService = new AgentHeartbeatService(sdlcRegistry, eventloop);
        this.aepEventPublisher = aepEventPublisher;
    }

    // ==================== INITIALIZATION ====================

    /**
     * Initializes all agent subsystems.
     *
     * <p>Sequence:
     * <ol>
     *   <li>Register SDLC specialist agents (27 specialists + 4 phase leads)</li>
     *   <li>Register YAPPC tools and set global context</li>
     *   <li>Load planner agents from YAML definitions</li>
     * </ol>
     *
     * @return Promise that completes when all agents are initialized
     */
    public Promise<Void> initialize() {
        if (initialized) {
            log.warn("YappcAgentSystem already initialized. Skipping.");
            return Promise.complete();
        }

        log.info("Initializing unified YAPPC agent system...");

        return Promise.ofBlocking(eventloop, () -> {
            // Step 0: Wire AEP publisher directly (same-package field access).
            // configureAepEventPublisher() was removed in 2.4.0; YappcAgentSystem and
            // YAPPCAgentBase share the com.ghatana.yappc.agent package.
            if (aepEventPublisher != null) {
                YAPPCAgentBase.globalAepEventPublisher = aepEventPublisher;
                log.info("AEP event publisher wired to SDLC agents: {}",
                        aepEventPublisher.getClass().getSimpleName());
            } else {
                log.warn("No AepEventPublisher configured — SDLC step events will use no-op publisher");
            }

            // Step 1: SDLC specialists
            bootstrapSdlcAgents();

            // Step 2: Planner tools + context
            registerPlannerTools();
            setGlobalContext();

            // Step 3: Load YAML agent definitions (YAPPC-format definitions/*.yaml)
            loadAgentDefinitions();
            // Step 4: Load full agent catalog from _index.yaml (590+ domain agents)
            loadAgentCatalog();
            validatePlannerInitialization();

            initialized = true;
            log.info("Unified YAPPC agent system initialized. SDLC agents: {}, Agent definitions: {}, Catalog agents: {}",
                    sdlcRegistry.getAgentCount(), agentDefinitions.size(), catalogEntries.size());
            return null;
        }).then(() -> heartbeatService.start());
    }

    // ==================== SDLC BOOTSTRAP (inlined) ====================

    private void bootstrapSdlcAgents() {
        log.info("Starting SDLC agent bootstrap... (LLM-powered: {})",
                llmGateway != null ? "YES" : "NO (using stubs)");

        registerArchitectureSpecialists();
        registerImplementationSpecialists();
        registerTestingSpecialists();
        registerOpsSpecialists();
        registerPhaseLeads();
        registerOrchestrators();

        log.info("SDLC agent bootstrap complete. Total agents: {}, Phases: {}",
                sdlcRegistry.getAgentCount(), sdlcRegistry.getAllPhases());
    }

    private void registerArchitectureSpecialists() {
        log.info("Registering Architecture phase specialists...");

        sdlcRegistry.register(new IntakeSpecialistAgent(memoryStore,
                llmGateway != null
                        ? LLMGeneratorFactory.createIntakeGenerator(llmGateway, llmConfig)
                        : new IntakeSpecialistAgent.IntakeGenerator()));

        sdlcRegistry.register(new DesignSpecialistAgent(memoryStore,
                llmGateway != null
                        ? LLMGeneratorFactory.createDesignGenerator(llmGateway, llmConfig)
                        : new DesignSpecialistAgent.DesignGenerator()));

        sdlcRegistry.register(new DeriveContractsSpecialistAgent(memoryStore,
                new DeriveContractsSpecialistAgent.DeriveContractsGenerator()));

        sdlcRegistry.register(new DeriveDataModelsSpecialistAgent(memoryStore,
                new DeriveDataModelsSpecialistAgent.DeriveDataModelsGenerator()));

        sdlcRegistry.register(new ValidateArchitectureSpecialistAgent(memoryStore,
                new ValidateArchitectureSpecialistAgent.ValidateArchitectureGenerator()));

        sdlcRegistry.register(new HITLReviewSpecialistAgent(memoryStore,
                Map.of("architecture.hitlReview",
                        new HITLReviewSpecialistAgent.HITLReviewGenerator())));

        sdlcRegistry.register(new PublishArchitectureSpecialistAgent(memoryStore,
                new PublishArchitectureSpecialistAgent.PublishArchitectureGenerator()));

        log.info("Registered 7 Architecture specialists (2 LLM-powered)");
    }

    private void registerImplementationSpecialists() {
        log.info("Registering Implementation phase specialists...");

        sdlcRegistry.register(new ScaffoldSpecialistAgent(memoryStore,
                llmGateway != null
                        ? LLMGeneratorFactory.createScaffoldGenerator(llmGateway, llmConfig)
                        : new ScaffoldSpecialistAgent.ScaffoldGenerator()));

        sdlcRegistry.register(new PlanUnitsSpecialistAgent(memoryStore,
                llmGateway != null
                        ? LLMGeneratorFactory.createPlanUnitsGenerator(llmGateway, llmConfig)
                        : new PlanUnitsSpecialistAgent.PlanUnitsGenerator()));

        sdlcRegistry.register(new ImplementSpecialistAgent(memoryStore,
                llmGateway != null
                        ? LLMGeneratorFactory.createImplementGenerator(llmGateway, llmConfig)
                        : new ImplementSpecialistAgent.ImplementGenerator()));

        sdlcRegistry.register(new ReviewSpecialistAgent(memoryStore,
                llmGateway != null
                        ? LLMGeneratorFactory.createReviewGenerator(llmGateway, llmConfig)
                        : new ReviewSpecialistAgent.ReviewGenerator()));

        sdlcRegistry.register(new BuildSpecialistAgent(memoryStore,
                llmGateway != null
                        ? LLMGeneratorFactory.createBuildGenerator(llmGateway, llmConfig)
                        : new BuildSpecialistAgent.BuildGenerator()));

        sdlcRegistry.register(new QualityGateSpecialistAgent(memoryStore,
                Map.of("implementation.qualityGate",
                        new QualityGateSpecialistAgent.QualityGateGenerator())));

        sdlcRegistry.register(new ArtifactPublishSpecialistAgent(memoryStore,
                Map.of("implementation.artifactPublish",
                        new ArtifactPublishSpecialistAgent.ArtifactPublishGenerator())));

        log.info("Registered 7 Implementation specialists (5 LLM-powered)");
    }

    private void registerTestingSpecialists() {
        log.info("Registering Testing phase specialists...");

        sdlcRegistry.register(new DeriveTestPlanSpecialistAgent(memoryStore,
                Map.of("testing.deriveTestPlan",
                        new DeriveTestPlanSpecialistAgent.DeriveTestPlanGenerator())));

        sdlcRegistry.register(new GenerateTestsSpecialistAgent(memoryStore,
                Map.of("testing.generateTests",
                        llmGateway != null
                                ? LLMGeneratorFactory.createGenerateTestsGenerator(llmGateway, llmConfig)
                                : new GenerateTestsSpecialistAgent.GenerateTestsGenerator())));

        sdlcRegistry.register(new ExecuteTestsSpecialistAgent(memoryStore,
                new ExecuteTestsSpecialistAgent.ExecuteTestsGenerator()));

        sdlcRegistry.register(new AnalyzeTestResultsSpecialistAgent(memoryStore,
                new AnalyzeTestResultsSpecialistAgent.AnalyzeTestResultsGenerator()));

        sdlcRegistry.register(new SecurityTestsSpecialistAgent(memoryStore,
                new SecurityTestsSpecialistAgent.SecurityTestsGenerator()));

        sdlcRegistry.register(new PerformanceTestsSpecialistAgent(memoryStore,
                new PerformanceTestsSpecialistAgent.PerformanceTestsGenerator()));

        log.info("Registered 6 Testing specialists (1 LLM-powered)");
    }

    private void registerOpsSpecialists() {
        log.info("Registering Ops phase specialists...");

        sdlcRegistry.register(new DeployStagingSpecialistAgent(memoryStore,
                llmGateway != null
                        ? LLMGeneratorFactory.createDeployStagingGenerator(llmGateway, llmConfig)
                        : new DeployStagingSpecialistAgent.DeployStagingGenerator()));

        sdlcRegistry.register(new ValidateReleaseSpecialistAgent(memoryStore,
                new ValidateReleaseSpecialistAgent.ValidateReleaseGenerator()));

        sdlcRegistry.register(new CanarySpecialistAgent(memoryStore,
                llmGateway != null
                        ? LLMGeneratorFactory.createCanaryGenerator(llmGateway, llmConfig)
                        : new CanarySpecialistAgent.CanaryGenerator()));

        sdlcRegistry.register(new MonitorSpecialistAgent(memoryStore,
                llmGateway != null
                        ? LLMGeneratorFactory.createMonitorGenerator(llmGateway, llmConfig)
                        : new MonitorSpecialistAgent.MonitorGenerator()));

        sdlcRegistry.register(new PromoteOrRollbackSpecialistAgent(memoryStore,
                new PromoteOrRollbackSpecialistAgent.PromoteOrRollbackGenerator()));

        sdlcRegistry.register(new IncidentResponseSpecialistAgent(memoryStore,
                llmGateway != null
                        ? LLMGeneratorFactory.createIncidentResponseGenerator(llmGateway, llmConfig)
                        : new IncidentResponseSpecialistAgent.IncidentResponseGenerator()));

        sdlcRegistry.register(new PublishSpecialistAgent(memoryStore,
                Map.of("ops.publish", new PublishSpecialistAgent.PublishGenerator())));

        log.info("Registered 7 Ops specialists (4 LLM-powered)");
    }

    private void registerPhaseLeads() {
        log.info("Registering Phase Lead agents...");

        sdlcRegistry.register(new ArchitecturePhaseLeadAgent(
                sdlcRegistry, memoryStore, new ArchitecturePhaseGenerator(sdlcRegistry)));

        sdlcRegistry.register(new ImplementationPhaseLeadAgent(
                sdlcRegistry, memoryStore, new ImplementationPhaseGenerator(sdlcRegistry)));

        sdlcRegistry.register(new TestingPhaseLeadAgent(
                sdlcRegistry, memoryStore, new TestingPhaseGenerator(sdlcRegistry)));

        sdlcRegistry.register(new OpsPhaseLeadAgent(
                sdlcRegistry, memoryStore, new OpsPhaseGenerator()));

        log.info("Registered 4 Phase Lead agents");
    }

    private void registerOrchestrators() {
        log.info("Registering L1/L2 Orchestrator agents...");

        sdlcRegistry.register(new GovernanceOrchestratorAgent(memoryStore,
                new GovernanceOrchestratorAgent.GovernanceOrchestratorGenerator()));

        sdlcRegistry.register(new AgentDispatcherAgent(memoryStore,
                new AgentDispatcherAgent.AgentDispatcherGenerator(),
                sdlcRegistry));

        sdlcRegistry.register(new ReleaseOrchestratorAgent(memoryStore,
                new ReleaseOrchestratorAgent.ReleaseOrchestratorGenerator()));

        sdlcRegistry.register(new OperationsOrchestratorAgent(memoryStore,
                new OperationsOrchestratorAgent.OperationsOrchestratorGenerator()));

        sdlcRegistry.register(new MultiCloudOrchestratorAgent(memoryStore,
                new MultiCloudOrchestratorAgent.MultiCloudOrchestratorGenerator()));

        log.info("Registered 5 Orchestrator agents (3 L1Orchestrators, 1 L2Dispatcher, 1 L1Cloud)");
    }

    // ==================== PLANNER BOOTSTRAP (inlined) ====================

    private void registerPlannerTools() {
        log.info("Registering YAPPC planner tools");
        YappcToolRegistry.registerAll(agentFactory);
        log.info("Planner tools registered successfully");
    }

    private void setGlobalContext() {
        log.info("Setting global context variables");
        agentFactory.registerGlobalContext("workspace_root", System.getProperty("user.dir"));
        agentFactory.registerGlobalContext("java_version", System.getProperty("java.version"));
        agentFactory.registerGlobalContext("os_name", System.getProperty("os.name"));
        agentFactory.registerGlobalContext("product_name", "yappc");
        agentFactory.registerGlobalContext("product_version", "2.0.0");
    }

    /**
     * Loads YAPPC-format agent definitions from {@code definitions/*.yaml}.
     *
     * <p>The YAPPC definition format (id, name, version, generator, memory, tools) differs
     * from the platform {@link com.ghatana.agent.framework.config.AgentDefinition} format;
     * definitions are stored raw in {@link #agentDefinitions} and instantiated lazily.
     */
    private void loadAgentDefinitions() {
        log.info("Loading agent definitions from: {}", configBasePath);

        File definitionsDir = new File(configBasePath + "/definitions");
        if (!definitionsDir.exists() || !definitionsDir.isDirectory()) {
            log.warn("Agent definitions directory not found at {} — skipping definition loading",
                    definitionsDir.getAbsolutePath());
            return;
        }

        File[] yamlFiles = definitionsDir.listFiles(
                (dir, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));

        if (yamlFiles == null || yamlFiles.length == 0) {
            log.warn("No agent definition files found in: {}", definitionsDir.getAbsolutePath());
            return;
        }

        log.info("Found {} agent definition files", yamlFiles.length);
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        int successCount = 0;

        for (File yamlFile : yamlFiles) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> definition = yamlMapper.readValue(yamlFile, Map.class);
                String agentId = (String) definition.get("id");
                if (agentId == null || agentId.isBlank()) {
                    agentId = yamlFile.getName().replaceFirst("\\.ya?ml$", "");
                }
                agentDefinitions.put(agentId, definition);
                successCount++;
                log.debug("Loaded agent definition: {}", agentId);
            } catch (Exception e) {
                log.error("Failed to load agent definition: {}", yamlFile.getName(), e);
            }
        }

        log.info("Agent definition loading complete: {}/{} succeeded",
                successCount, yamlFiles.length);
    }

    /**
     * Loads the full YAPPC agent catalog from {@code _index.yaml}.
     *
     * <p>Reads {@code spec.catalogs} entries in priority order, then for each
     * referenced catalog YAML parses the {@code spec.agents} list into
     * {@link #catalogEntries} keyed by agent id.
     */
    @SuppressWarnings("unchecked")
    private void loadAgentCatalog() {
        File indexFile = new File(configBasePath + "/_index.yaml");
        if (!indexFile.exists()) {
            log.warn("Agent catalog index not found at {} — skipping catalog loading",
                    indexFile.getAbsolutePath());
            return;
        }

        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try {
            Map<String, Object> index = yamlMapper.readValue(indexFile, Map.class);
            Map<String, Object> spec = (Map<String, Object>) index.get("spec");
            if (spec == null) {
                log.warn("_index.yaml has no 'spec' key — skipping catalog loading");
                return;
            }

            List<Map<String, Object>> catalogs = (List<Map<String, Object>>) spec.get("catalogs");
            if (catalogs == null || catalogs.isEmpty()) {
                log.warn("_index.yaml 'spec.catalogs' is empty — no domain catalogs to load");
                return;
            }

            int totalAgents = 0;
            int catalogsLoaded = 0;
            for (Map<String, Object> catalogMeta : catalogs) {
                String fileName = (String) catalogMeta.get("file");
                if (fileName == null) continue;

                File catalogFile = new File(configBasePath + "/" + fileName);
                if (!catalogFile.exists()) {
                    log.warn("Catalog file not found: {}", catalogFile.getAbsolutePath());
                    continue;
                }

                int loaded = loadSingleCatalogFile(yamlMapper, catalogFile);
                totalAgents += loaded;
                catalogsLoaded++;
                log.debug("Loaded {} agents from catalog: {}", loaded, fileName);
            }

            log.info("Agent catalog loaded: {} domain catalogs, {} agents total",
                    catalogsLoaded, totalAgents);

        } catch (Exception e) {
            log.error("Failed to load agent catalog from _index.yaml", e);
        }
    }

    @SuppressWarnings("unchecked")
    private int loadSingleCatalogFile(ObjectMapper yamlMapper, File catalogFile) {
        try {
            Map<String, Object> catalog = yamlMapper.readValue(catalogFile, Map.class);
            Map<String, Object> spec = (Map<String, Object>) catalog.get("spec");
            if (spec == null) return 0;

            List<Map<String, Object>> agents = (List<Map<String, Object>>) spec.get("agents");
            if (agents == null) return 0;

            int count = 0;
            for (Map<String, Object> agent : agents) {
                String agentId = (String) agent.get("id");
                if (agentId != null && !agentId.isBlank()) {
                    catalogEntries.put(agentId, agent);
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.warn("Failed to load catalog file {}: {}", catalogFile.getName(), e.getMessage());
            return 0;
        }
    }

    private void validatePlannerInitialization() {
        if (agentDefinitions.isEmpty()) {
            log.warn("No agent definitions loaded. System may not be fully functional.");
        }

        List<String> criticalAgents = List.of("products-officer", "systems-architect", "java-expert");
        for (String critical : criticalAgents) {
            if (!agentDefinitions.containsKey(critical)) {
                log.warn("Critical agent definition not loaded: {}", critical);
            }
        }
    }

    // ==================== PUBLIC API ====================

    /**
     * Gets a planner agent by ID.
     *
     * @param agentId the agent ID (e.g., "products-officer")
     * @return the BaseAgent instance
     * @throws IllegalStateException    if not initialized
     * @throws IllegalArgumentException if agent not found
     * @deprecated Agent instantiation from YAML definitions is not yet implemented;
     *             use {@link #getAgentDefinition(String)} to access definition metadata.
     */
    @Deprecated(since = "2.4.0")
    public BaseAgent<?, ?> getPlannerAgent(String agentId) {
        requireInitialized();
        if (!plannerAgentInstances.containsKey(agentId)) {
            throw new IllegalArgumentException(
                    "Planner agent not instantiated: " + agentId
                    + ". Use getAgentDefinition() to inspect the definition.");
        }
        return plannerAgentInstances.get(agentId);
    }

    /**
     * Returns the raw YAML definition map for the given agent id (loaded from
     * {@code definitions/*.yaml}).
     *
     * @param agentId agent identifier as declared in the YAML {@code id} field
     * @return raw definition map, or {@code null} if not found
     * @throws IllegalStateException if not initialized
     */
    @Nullable
    public Map<String, Object> getAgentDefinition(String agentId) {
        requireInitialized();
        return agentDefinitions.get(agentId);
    }

    /**
     * Returns all loaded definition ids (from {@code definitions/*.yaml}).
     *
     * @throws IllegalStateException if not initialized
     */
    public List<String> getLoadedDefinitionIds() {
        requireInitialized();
        return new ArrayList<>(agentDefinitions.keySet());
    }

    /**
     * Returns a catalog entry by agent id (loaded from domain catalogs via {@code _index.yaml}).
     *
     * @param agentId catalog agent identifier
     * @return catalog entry map, or {@code null} if not found
     * @throws IllegalStateException if not initialized
     */
    @Nullable
    public Map<String, Object> getCatalogEntry(String agentId) {
        requireInitialized();
        return catalogEntries.get(agentId);
    }

    /**
     * Returns the total number of agents in the loaded catalog across all domain catalogs.
     *
     * @throws IllegalStateException if not initialized
     */
    public int getCatalogSize() {
        requireInitialized();
        return catalogEntries.size();
    }

    /**
     * Gets all loaded planner agent IDs.
     *
     * @return list of planner agent IDs
     */
    /**
     * @deprecated Use {@link #getLoadedDefinitionIds()} instead.
     */
    @Deprecated(since = "2.4.0")
    public List<String> getLoadedPlannerAgentIds() {
        return new ArrayList<>(agentDefinitions.keySet());
    }

    /**
     * Gets the SDLC specialist agent registry.
     *
     * @return the SDLC agent registry
     * @throws IllegalStateException if not initialized
     */
    public YAPPCAgentRegistry getSdlcRegistry() {
        requireInitialized();
        return sdlcRegistry;
    }

    /**     * Gets the agent heartbeat service.
     *
     * @return the heartbeat service (started after {@link #initialize()})
     */
    public AgentHeartbeatService getHeartbeatService() {
        return heartbeatService;
    }

    /**     * Gets the planner agent registry.
     *
     * @return the planner agent registry
     * @throws IllegalStateException if not initialized
     */
    public AgentRegistry getPlannerRegistry() {
        requireInitialized();
        return plannerRegistry;
    }

    /**
     * Gets all loaded agent IDs across both subsystems.
     *
     * @return combined list of agent IDs
     */
    public List<String> getAllAgentIds() {
        List<String> ids = new ArrayList<>(agentDefinitions.keySet());
        sdlcRegistry.getAllPhases().stream()
                .flatMap(phase -> sdlcRegistry.getAgentsByPhase(phase).stream())
                .forEach(agent -> ids.add(agent.stepName()));
        return ids;
    }

    /**
     * Gets a formatted summary of all registered agents.
     *
     * @return formatted summary string
     */
    @NotNull
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== YAPPC Agent System Summary ===\n");
        sb.append(String.format("SDLC Agents: %d\n", sdlcRegistry.getAgentCount()));
        sb.append(String.format("Agent Definitions: %d\n", agentDefinitions.size()));
        sb.append(String.format("Catalog Agents: %d\n", catalogEntries.size()));
        sb.append(String.format("SDLC Phases: %d\n\n", sdlcRegistry.getAllPhases().size()));

        for (String phase : sdlcRegistry.getAllPhases().stream().sorted().toList()) {
            var agents = sdlcRegistry.getAgentsByPhase(phase);
            sb.append(String.format("%s (%d agents):\n", phase.toUpperCase(), agents.size()));
            for (var agent : agents) {
                sb.append(String.format("  - %s\n", agent.stepName()));
            }
            sb.append("\n");
        }

        if (!agentDefinitions.isEmpty()) {
            sb.append("AGENT DEFINITIONS:\n");
            for (String id : agentDefinitions.keySet().stream().sorted().toList()) {
                sb.append(String.format("  - %s\n", id));
            }
        }

        return sb.toString();
    }

    /**
     * Returns whether the system has been initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    private void requireInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                    "YappcAgentSystem not initialized. Call initialize() first.");
        }
    }

    /**
     * Creates a builder for constructing a YappcAgentSystem.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for YappcAgentSystem.
     *
     * @doc.type class
     * @doc.purpose Fluent builder for unified agent system
     * @doc.layer product
     * @doc.pattern Builder
     */
    public static class Builder {

        private Eventloop eventloop;
        private MemoryStore memoryStore;
        private LLMGenerator.LLMGateway llmGateway;
        private LLMGenerator.LLMConfig llmConfig;
        private String configBasePath = "products/yappc/config/agents";
        private AepEventPublisher aepEventPublisher;

        /**
         * Sets the eventloop for async operations.
         *
         * @param eventloop ActiveJ eventloop
         * @return this builder
         */
        public Builder eventloop(@NotNull Eventloop eventloop) {
            this.eventloop = eventloop;
            return this;
        }

        /**
         * Sets the memory store for agent state.
         *
         * @param memoryStore agent memory store
         * @return this builder
         */
        public Builder memoryStore(@NotNull MemoryStore memoryStore) {
            this.memoryStore = memoryStore;
            return this;
        }

        /**
         * Sets the LLM gateway for AI-powered agents.
         *
         * @param llmGateway LLM gateway (nullable for stub mode)
         * @return this builder
         */
        public Builder llmGateway(@Nullable LLMGenerator.LLMGateway llmGateway) {
            this.llmGateway = llmGateway;
            return this;
        }

        /**
         * Sets the LLM configuration.
         *
         * @param llmConfig LLM configuration (nullable for defaults)
         * @return this builder
         */
        public Builder llmConfig(@Nullable LLMGenerator.LLMConfig llmConfig) {
            this.llmConfig = llmConfig;
            return this;
        }

        /**
         * Sets the base path for agent YAML configurations.
         *
         * @param configBasePath config directory path
         * @return this builder
         */
        public Builder configBasePath(@NotNull String configBasePath) {
            this.configBasePath = configBasePath;
            return this;
        }

        /**
         * Sets the AEP event publisher for SDLC step event emission.
         *
         * @param aepEventPublisher publisher for AEP integration (nullable for no-op mode)
         * @return this builder
         */
        public Builder aepEventPublisher(@Nullable AepEventPublisher aepEventPublisher) {
            this.aepEventPublisher = aepEventPublisher;
            return this;
        }

        /**
         * Builds the unified agent system.
         *
         * @return configured YappcAgentSystem
         * @throws IllegalArgumentException if required fields are missing
         */
        public YappcAgentSystem build() {
            if (eventloop == null) {
                throw new IllegalArgumentException("Eventloop is required");
            }
            if (memoryStore == null) {
                throw new IllegalArgumentException("MemoryStore is required");
            }

            LLMGenerator.LLMConfig effectiveConfig = llmConfig != null
                    ? llmConfig
                    : LLMGenerator.LLMConfig.builder()
                            .model(System.getenv().getOrDefault("YAPPC_DEFAULT_MODEL", "llama3"))
                            .temperature(0.7)
                            .maxTokens(4000)
                            .build();

            return new YappcAgentSystem(
                    eventloop, configBasePath,
                    new YAPPCAgentRegistry(), memoryStore,
                    llmGateway, effectiveConfig, aepEventPublisher);
        }
    }
}
