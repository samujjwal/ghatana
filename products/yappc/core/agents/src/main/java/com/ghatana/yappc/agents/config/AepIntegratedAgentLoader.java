package com.ghatana.yappc.agents.config;

import com.ghatana.agent.registry.service.AgentRegistryService;
import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * YAPPC Agent Loader integrated with AEP AgentRegistryService.
 * 
 * This loader replaces the custom YappcAgentRegistry by leveraging AEP's
 * AgentRegistryService for all agent management operations. It loads YAML
 * agent configurations and registers them with AEP's registry.
 * 
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Loads YAML agent definitions from classpath</li>
 *   <li>Converts YAML to AEP AgentManifestProto format</li>
 *   <li>Registers agents with AEP AgentRegistryService</li>
 *   <li>Supports multi-tenant agent registration</li>
 *   <li>Provides batch registration capabilities</li>
 * </ul>
 * 
 * <p><b>Usage:</b>
 * <pre>{@code
 * AepIntegratedAgentLoader loader = new AepIntegratedAgentLoader(
 *     aepRegistry, yamlLoader, converter
 * );
 * 
 * Promise<List<AgentManifestProto>> registered = 
 *     loader.loadAndRegisterAgents(tenantId);
 * }</pre>
 * 
 * @doc.pattern Loader, Adapter
 * @doc.purpose Load YAML agents into AEP registry
 * @doc.layer config
 */
public class AepIntegratedAgentLoader {
    private static final Logger log = LoggerFactory.getLogger(AepIntegratedAgentLoader.class);
    
    private final AgentRegistryService aepRegistry;
    private final YamlAgentLoader yamlLoader;
    private final YamlToManifestConverter converter;
    
    @Inject
    public AepIntegratedAgentLoader(
        AgentRegistryService aepRegistry,
        YamlAgentLoader yamlLoader,
        YamlToManifestConverter converter
    ) {
        this.aepRegistry = aepRegistry;
        this.yamlLoader = yamlLoader;
        this.converter = converter;
    }
    
    /**
     * Load all YAML agents from classpath and register with AEP.
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
        
        return Promises.all(registrations);
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
        
        return Promises.all(registrations);
    }
    
    /**
     * Register a single agent with AEP registry.
     */
    private Promise<AgentManifestProto> registerAgent(TenantId tenantId, AgentManifestProto manifest) {
        String agentId = manifest.getMetadata().getName();
        
        return aepRegistry.register(tenantId, manifest)
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
        return aepRegistry.getById(tenantId, agentId);
    }
    
    /**
     * List all agents for a tenant from AEP registry.
     * 
     * @param tenantId Tenant scope
     * @return Promise of all agent manifests
     */
    public Promise<List<AgentManifestProto>> listAgents(TenantId tenantId) {
        return aepRegistry.listAll(tenantId);
    }
    
    /**
     * Find agents by capability using AEP registry.
     * 
     * @param tenantId Tenant scope
     * @param capability Capability to search for
     * @return Promise of matching agent IDs
     */
    public Promise<List<String>> findAgentsByCapability(TenantId tenantId, String capability) {
        return aepRegistry.findByCapability(tenantId, capability);
    }
    
    /**
     * Find agents by event type using AEP registry.
     * 
     * @param tenantId Tenant scope
     * @param eventType Event type to search for
     * @return Promise of matching agent IDs
     */
    public Promise<List<String>> findAgentsByEventType(TenantId tenantId, String eventType) {
        return aepRegistry.findByEventType(tenantId, eventType);
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
        return aepRegistry.update(tenantId, agentId, manifest)
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
        return aepRegistry.delete(tenantId, agentId)
            .whenResult(() -> 
                log.info("Successfully deleted agent {} from AEP for tenant {}", agentId, tenantId))
            .whenException(error -> 
                log.error("Failed to delete agent {} from AEP for tenant {}: {}", 
                    agentId, tenantId, error.getMessage(), error));
    }
}
