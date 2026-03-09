package com.ghatana.virtualorg.framework;

import com.ghatana.virtualorg.framework.agent.AgentRegistry;
import com.ghatana.virtualorg.framework.cnp.TaskMarket;
import com.ghatana.virtualorg.framework.config.TemplateRegistry;
import com.ghatana.virtualorg.framework.memory.OrganizationalMemory;
import com.ghatana.virtualorg.framework.norm.NormRegistry;
import com.ghatana.virtualorg.framework.norm.NormativeMonitor;
import com.ghatana.virtualorg.framework.ontology.Ontology;
import com.ghatana.virtualorg.framework.spi.ExtensionLoader;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main context/container for the Virtual-Org framework.
 *
 * <p><b>Purpose</b><br>
 * VirtualOrgContext is the central orchestration point that ties together
 * all framework components: agents, norms, ontology, task market, memory,
 * and extensions. It provides a single point of access for all framework
 * services and manages the lifecycle.
 *
 * <p><b>Key Components</b><br>
 * - {@link AgentRegistry}: Agent factory management and agent creation
 * - {@link NormRegistry}: Organizational norms (obligations, prohibitions, permissions)
 * - {@link NormativeMonitor}: Real-time norm enforcement
 * - {@link Ontology}: Semantic vocabulary for interoperability
 * - {@link TaskMarket}: Contract Net Protocol for task allocation
 * - {@link TemplateRegistry}: Configuration template management
 * - {@link OrganizationalMemory}: Shared knowledge base
 * - {@link ExtensionLoader}: Plugin management
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * VirtualOrgContext context = VirtualOrgContext.builder(eventloop)
 *     .withAutoDiscovery(true)
 *     .build();
 *
 * context.initialize().getResult();
 *
 * // Access components
 * AgentRegistry agents = context.getAgentRegistry();
 * NormRegistry norms = context.getNormRegistry();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Central framework context and orchestrator
 * @doc.layer platform
 * @doc.pattern Facade, Context
 */
public class VirtualOrgContext {

    private static final Logger LOG = LoggerFactory.getLogger(VirtualOrgContext.class);

    private final Eventloop eventloop;
    private final AgentRegistry agentRegistry;
    private final NormRegistry normRegistry;
    private final NormativeMonitor normativeMonitor;
    private final Ontology ontology;
    private final TaskMarket taskMarket;
    private final TemplateRegistry templateRegistry;
    private final OrganizationalMemory memory;
    private final ExtensionLoader extensionLoader;
    private final java.time.Duration monitoringInterval;

    private boolean initialized = false;

    private VirtualOrgContext(Builder builder) {
        this.eventloop = builder.eventloop;
        this.agentRegistry = builder.agentRegistry;
        this.normRegistry = builder.normRegistry;
        this.normativeMonitor = new NormativeMonitor(normRegistry);
        this.ontology = builder.ontology;
        this.taskMarket = builder.taskMarket;
        this.templateRegistry = builder.templateRegistry;
        this.memory = builder.memory;
        this.extensionLoader = builder.extensionLoader;
        this.monitoringInterval = builder.monitoringInterval;
    }

    /**
     * Creates a builder for VirtualOrgContext.
     *
     * @param eventloop the event loop to use
     * @return new builder
     */
    public static Builder builder(Eventloop eventloop) {
        return new Builder(eventloop);
    }

    /**
     * Initializes the framework.
     *
     * <p>This method:
     * <ul>
     *   <li>Discovers and loads extensions</li>
     *   <li>Initializes the ontology with core concepts</li>
     *   <li>Sets up the normative monitor</li>
     * </ul>
     *
     * @return promise completing when initialization is done
     */
    public Promise<Void> initialize() {
        if (initialized) {
            LOG.warn("VirtualOrgContext already initialized");
            return Promise.complete();
        }

        LOG.info("Initializing VirtualOrgContext...");

        // Start normative monitoring if configured
        if (monitoringInterval != null) {
            normativeMonitor.startMonitoring(monitoringInterval);
        }

        // Initialize extensions
        return extensionLoader.initializeAll(this)
                .whenComplete(() -> {
                    initialized = true;
                    LOG.info("VirtualOrgContext initialized successfully");
                })
                .whenException(e -> LOG.error("Failed to initialize VirtualOrgContext", e));
    }

    /**
     * Shuts down the framework.
     *
     * @return promise completing when shutdown is done
     */
    public Promise<Void> shutdown() {
        if (!initialized) {
            return Promise.complete();
        }

        LOG.info("Shutting down VirtualOrgContext...");

        // Stop normative monitoring
        normativeMonitor.stopMonitoring();

        return extensionLoader.shutdownAll()
                .whenComplete(() -> {
                    initialized = false;
                    LOG.info("VirtualOrgContext shut down");
                });
    }

    // Getters for all components

    public Eventloop getEventloop() {
        return eventloop;
    }

    public AgentRegistry getAgentRegistry() {
        return agentRegistry;
    }

    public NormRegistry getNormRegistry() {
        return normRegistry;
    }

    public NormativeMonitor getNormativeMonitor() {
        return normativeMonitor;
    }

    public Ontology getOntology() {
        return ontology;
    }

    public TaskMarket getTaskMarket() {
        return taskMarket;
    }

    public TemplateRegistry getTemplateRegistry() {
        return templateRegistry;
    }

    public OrganizationalMemory getMemory() {
        return memory;
    }

    public ExtensionLoader getExtensionLoader() {
        return extensionLoader;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Builder for VirtualOrgContext.
     */
    public static class Builder {
        private final Eventloop eventloop;
        private AgentRegistry agentRegistry;
        private NormRegistry normRegistry;
        private Ontology ontology;
        private TaskMarket taskMarket;
        private TemplateRegistry templateRegistry;
        private OrganizationalMemory memory;
        private ExtensionLoader extensionLoader;
        private boolean autoDiscovery = false;
        private java.time.Duration monitoringInterval;

        private Builder(Eventloop eventloop) {
            this.eventloop = eventloop;
        }

        /**
         * Sets the agent registry.
         */
        public Builder agentRegistry(AgentRegistry registry) {
            this.agentRegistry = registry;
            return this;
        }

        /**
         * Sets the norm registry.
         */
        public Builder normRegistry(NormRegistry registry) {
            this.normRegistry = registry;
            return this;
        }

        /**
         * Sets the ontology.
         */
        public Builder ontology(Ontology ontology) {
            this.ontology = ontology;
            return this;
        }

        /**
         * Sets the task market.
         */
        public Builder taskMarket(TaskMarket market) {
            this.taskMarket = market;
            return this;
        }

        /**
         * Sets the template registry.
         */
        public Builder templateRegistry(TemplateRegistry registry) {
            this.templateRegistry = registry;
            return this;
        }

        /**
         * Sets the organizational memory.
         */
        public Builder memory(OrganizationalMemory memory) {
            this.memory = memory;
            return this;
        }

        /**
         * Sets the extension loader.
         */
        public Builder extensionLoader(ExtensionLoader loader) {
            this.extensionLoader = loader;
            return this;
        }

        /**
         * Enables auto-discovery of extensions and agent factories.
         */
        public Builder withAutoDiscovery(boolean enabled) {
            this.autoDiscovery = enabled;
            return this;
        }

        /**
         * Enables normative monitoring with the specified interval.
         */
        public Builder withNormativeMonitoring(java.time.Duration interval) {
            this.monitoringInterval = interval;
            return this;
        }

        /**
         * Builds the VirtualOrgContext.
         */
        public VirtualOrgContext build() {
            // Create defaults if not provided
            if (agentRegistry == null) {
                agentRegistry = new AgentRegistry();
            }
            if (normRegistry == null) {
                normRegistry = new com.ghatana.virtualorg.framework.norm.InMemoryNormRegistry();
            }
            if (ontology == null) {
                ontology = Ontology.withCoreConceptsAsync();
            }
            if (taskMarket == null) {
                taskMarket = new TaskMarket();
            }
            if (templateRegistry == null) {
                templateRegistry = new TemplateRegistry();
            }
            if (memory == null) {
                memory = new com.ghatana.virtualorg.framework.memory.InMemoryOrganizationalMemory();
            }
            if (extensionLoader == null) {
                extensionLoader = new ExtensionLoader();
            }

            // Auto-discover if enabled
            if (autoDiscovery) {
                agentRegistry.discoverFactories();
                extensionLoader.discover();
            }

            return new VirtualOrgContext(this);
        }
    }
}
