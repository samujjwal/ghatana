package com.ghatana.yappc.agents.config;

import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.yappc.agent.spi.AgentRegistryPort;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * YAPPC Agent Loader integrated with the {@link AgentRegistryPort} adapter seam.
 *
 * <p>This loader loads YAML agent configurations from the classpath and registers
 * them via the {@link AgentRegistryPort} port interface. The concrete registry
 * implementation (e.g., AEP) is injected at wiring time by the infrastructure layer,
 * keeping this class free of direct AEP dependencies.
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Loads YAML agent definitions from classpath</li>
 *   <li>Converts YAML to AEP AgentManifestProto format</li>
 *   <li>Registers agents via {@link AgentRegistryPort}</li>
 *   <li>Supports multi-tenant agent registration</li>
 *   <li>Provides batch registration capabilities</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * AepIntegratedAgentLoader loader = new AepIntegratedAgentLoader(
 *     agentRegistryPort, yamlLoader, converter
 * );
 *
 * Promise<List<AgentManifestProto>> registered =
 *     loader.loadAndRegisterAgents(tenantId);
 * }</pre>
 *
 * @doc.type class
 * @doc.pattern Loader, Adapter
 * @doc.purpose Load YAML agents into the agent registry via port interface
 * @doc.layer config
 */
public class AepIntegratedAgentLoader {
    private static final Logger log = LoggerFactory.getLogger(AepIntegratedAgentLoader.class);

    private final AgentRegistryPort agentRegistry;
    private final YamlAgentLoader yamlLoader;
    private final YamlToManifestConverter converter;

    @Inject
    public AepIntegratedAgentLoader(
        AgentRegistryPort agentRegistry,
        YamlAgentLoader yamlLoader,
        YamlToManifestConverter converter
    ) {
        this.agentRegistry = agentRegistry;
        this.yamlLoader = yamlLoader;
        this.converter = converter;
    }

    /**
     * Load all YAML agents from classpath and register with the agent registry.
     *
     * @param tenantId Tenant that will own these agents
     * @return Promise of registered agent manifests
     */
    public Promise<List<AgentManifestProto>> loadAndRegisterAgents(TenantId tenantId) {
        log.info("Loading YAML agents for tenant: {}", tenantId);
        
        // Load YAML configurations from classpath
        List<YamlAgentConfig> yamlConfigs = yamlLoader.loadFromClasspath("agents/");
        log.info("Loaded {} YAML agent configurations", yamlConfigs.size());
        
        // Convert to AEP manifest format
        List<AgentManifestProto> manifests = converter.convertAll(yamlConfigs);
        
        // Register all agents with AEP registry
        List<Promise<AgentManifestProto>> registrations = manifests.stream()
            .map(manifest -> registerAgent(tenantId, manifest))
            .collect(Collectors.toList());
        
        return Promises.toList(registrations);
    }
    
    /**
     * Load agents from a specific directory and register with AEP.
     * 
     * @param tenantId Tenant that will own these agents
     * @param resourcePath Resource path to load from (e.g., "agents/custom/")
     * @return Promise of registered agent manifests
     */
    public Promise<List<AgentManifestProto>> loadAndRegisterAgents(
        TenantId tenantId, 
        String resourcePath
    ) {
        log.info("Loading YAML agents from {} for tenant: {}", resourcePath, tenantId);
        
        List<YamlAgentConfig> yamlConfigs = yamlLoader.loadFromClasspath(resourcePath);
        log.info("Loaded {} YAML agent configurations from {}", yamlConfigs.size(), resourcePath);
        
        List<AgentManifestProto> manifests = converter.convertAll(yamlConfigs);
        
        List<Promise<AgentManifestProto>> registrations = manifests.stream()
            .map(manifest -> registerAgent(tenantId, manifest))
            .collect(Collectors.toList());
        
        return Promises.toList(registrations);
    }
    
    /**
     * Register a single agent with AEP registry.
     */
    private Promise<AgentManifestProto> registerAgent(TenantId tenantId, AgentManifestProto manifest) {
        String agentId = manifest.getMetadata().getName();
        
        return agentRegistry.register(tenantId, manifest)
            .whenResult(registered -> 
                log.info("Successfully registered agent {} with AEP for tenant {}", 
                    agentId, tenantId))
            .whenException(error -> 
                log.error("Failed to register agent {} with AEP for tenant {}: {}", 
                    agentId, tenantId, error.getMessage(), error));
    }
    
    /**
     * Get an agent by ID from AEP registry.
     * 
     * @param tenantId Tenant scope
     * @param agentId Agent identifier
     * @return Promise of agent manifest
     */
    public Promise<AgentManifestProto> getAgent(TenantId tenantId, String agentId) {
        return agentRegistry.getById(tenantId, agentId);
    }
    
    /**
     * List all agents for a tenant from AEP registry.
     * 
     * @param tenantId Tenant scope
     * @return Promise of all agent manifests
     */
    public Promise<List<AgentManifestProto>> listAgents(TenantId tenantId) {
        return agentRegistry.listAll(tenantId);
    }
    
    /**
     * Find agents by capability using AEP registry.
     * 
     * @param tenantId Tenant scope
     * @param capability Capability to search for
     * @return Promise of matching agent IDs
     */
    public Promise<List<String>> findAgentsByCapability(TenantId tenantId, String capability) {
        return agentRegistry.findByCapabilities(tenantId, Set.of(capability))
            .map(manifests -> manifests.stream()
                .map(m -> m.getMetadata().getId())
                .collect(Collectors.toList()));
    }
    
    /**
     * Find agents by event type using AEP registry.
     * 
     * @param tenantId Tenant scope
     * @param eventType Event type to search for
     * @return Promise of matching agent IDs
     */
    public Promise<List<String>> findAgentsByEventType(TenantId tenantId, String eventType) {
        return agentRegistry.findByEventType(tenantId, eventType)
            .map(manifests -> manifests.stream()
                .map(m -> m.getMetadata().getId())
                .collect(Collectors.toList()));
    }
    
    /**
     * Update an existing agent in AEP registry.
     * 
     * @param tenantId Tenant scope
     * @param agentId Agent identifier
     * @param yamlConfig Updated YAML configuration
     * @return Promise of updated manifest
     */
    public Promise<AgentManifestProto> updateAgent(
        TenantId tenantId, 
        String agentId, 
        YamlAgentConfig yamlConfig
    ) {
        AgentManifestProto manifest = converter.convert(yamlConfig);
        return agentRegistry.update(tenantId, agentId, manifest)
            .whenResult(updated -> 
                log.info("Successfully updated agent {} in AEP for tenant {}", agentId, tenantId))
            .whenException(error -> 
                log.error("Failed to update agent {} in AEP for tenant {}: {}", 
                    agentId, tenantId, error.getMessage(), error));
    }
    
    /**
     * Delete an agent from AEP registry.
     * 
     * @param tenantId Tenant scope
     * @param agentId Agent identifier
     * @return Promise of deletion confirmation
     */
    public Promise<Void> deleteAgent(TenantId tenantId, String agentId) {
        return deleteAgent(tenantId, agentId, false);
    }
    
    /**
     * Delete an agent from AEP registry with hard delete option.
     * 
     * @param tenantId Tenant scope
     * @param agentId Agent identifier
     * @param hardDelete If true, permanently delete; otherwise soft-delete
     * @return Promise of deletion confirmation
     */
    public Promise<Void> deleteAgent(TenantId tenantId, String agentId, boolean hardDelete) {
        return agentRegistry.delete(tenantId, agentId, hardDelete)
            .map(deleted -> (Void) null)
            .whenResult(v -> 
                log.info("Successfully deleted agent {} from AEP for tenant {} (hard={})", 
                    agentId, tenantId, hardDelete))
            .whenException(error -> 
                log.error("Failed to delete agent {} from AEP for tenant {}: {}", 
                    agentId, tenantId, error.getMessage(), error));
    }
}
