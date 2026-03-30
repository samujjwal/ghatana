package com.ghatana.yappc.agents.config;

import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.contracts.agent.v1.AgentSpecProto;
import com.ghatana.contracts.agent.v1.RuntimeProto;
import com.ghatana.contracts.agent.v1.MetadataProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts YAPPC YAML agent configurations to AEP AgentManifestProto format.
 * 
 * This converter bridges YAPPC's simplified YAML-based agent definitions with
 * AEP's standard AgentManifestProto format, enabling YAPPC agents to be registered
 * and executed through AEP's AgentRegistryService.
 * 
 * <p><b>Conversion Strategy:</b>
 * <ul>
 *   <li>YAML id → AgentManifest metadata.name</li>
 *   <li>YAML name → AgentManifest metadata.displayName</li>
 *   <li>YAML capabilities → AgentSpec capabilities</li>
 *   <li>YAML tags → AgentSpec inputEventTypes (prefixed with "tag:")</li>
 *   <li>YAML generator → AgentSpec runtime configuration</li>
 *   <li>YAML validation → AgentSpec validation rules</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.pattern Converter, Adapter
 * @doc.purpose Convert YAML configs to AEP manifest format
 * @doc.layer product
 */
public class YamlToManifestConverter {
    private static final Logger log = LoggerFactory.getLogger(YamlToManifestConverter.class);
    
    /**
     * Convert YAML agent config to AEP AgentManifestProto.
     * 
     * @param yamlConfig The YAML-based agent configuration
     * @return AgentManifestProto ready for AEP registration
     */
    public AgentManifestProto convert(YamlAgentConfig yamlConfig) {
        log.debug("Converting YAML config to AgentManifest: {}", yamlConfig.getId());
        
        return AgentManifestProto.newBuilder()
            .setMetadata(buildMetadata(yamlConfig))
            .setSpec(buildSpec(yamlConfig))
            .build();
    }
    
    /**
     * Build metadata section of the manifest.
     */
    private MetadataProto buildMetadata(YamlAgentConfig config) {
        MetadataProto.Builder metadata = MetadataProto.newBuilder()
            .setId(config.getId())
            .setName(config.getName())
            .setDescription(config.getDescription())
            .setVersion(config.getVersion());
        
        // Add labels from tags
        Map<String, String> labels = new HashMap<>();
        for (String tag : config.getTags()) {
            labels.put("tag." + tag, "true");
        }
        
        // Add metadata fields
        for (Map.Entry<String, Object> entry : config.getMetadata().entrySet()) {
            labels.put("metadata." + entry.getKey(), String.valueOf(entry.getValue()));
        }

        // Add event processing metadata as labels for observability / routing config
        if (config.getEventProcessing() != null) {
            YamlAgentConfig.EventProcessingConfig ep = config.getEventProcessing();
            labels.put("event.ordering", ep.getOrdering());
            labels.put("event.max_in_flight", String.valueOf(ep.getMaxInFlight()));
            if (ep.getDeadLetterQueue() != null) {
                labels.put("event.dlq", ep.getDeadLetterQueue());
            }
        }
        
        metadata.putAllLabels(labels);
        
        return metadata.build();
    }
    
    /**
     * Build spec section of the manifest.
     */
    private AgentSpecProto buildSpec(YamlAgentConfig config) {
        AgentSpecProto.Builder spec = AgentSpecProto.newBuilder();
        
        // Set runtime configuration
        spec.setRuntime(buildRuntime(config));
        
        // Add capabilities
        for (String capability : config.getCapabilities()) {
            spec.addCapabilities(capability);
        }

        // Prefer explicit event_processing config; fall back to derived event types
        if (config.getEventProcessing() != null) {
            YamlAgentConfig.EventProcessingConfig ep = config.getEventProcessing();
            for (String inputType : ep.getInputEventTypes()) {
                spec.addInputEventTypes(inputType);
            }
            for (String outputType : ep.getOutputEventTypes()) {
                spec.addOutputEventTypes(outputType);
            }
        } else {
            // Add tags as input event types for filtering
            for (String tag : config.getTags()) {
                spec.addInputEventTypes("tag:" + tag);
            }
            // Add generator-specific event types
            if (config.getGenerator() != null) {
                addGeneratorEventTypes(spec, config);
            }
        }
        
        // Add validation configuration
        if (config.getValidation() != null) {
            addValidationConfig(spec, config);
        }
        
        return spec.build();
    }
    
    /**
     * Build runtime configuration.
     */
    private RuntimeProto buildRuntime(YamlAgentConfig config) {
        RuntimeProto.Builder runtime = RuntimeProto.newBuilder()
            .setType("java")
            .setVersion(config.getVersion())
            .setEntrypoint(config.getId());
        
        // Add generator configuration
        if (config.getGenerator() != null) {
            YamlAgentConfig.GeneratorConfig gen = config.getGenerator();
            
            // Set runtime type based on generator
            switch (gen.getType()) {
                case "llm":
                    runtime.setType("LLM_BASED");
                    runtime.putConfig("model", gen.getModel());
                    runtime.putConfig("temperature", String.valueOf(gen.getTemperature()));
                    runtime.putConfig("max_tokens", String.valueOf(gen.getMaxTokens()));
                    runtime.putConfig("prompt_template", gen.getPromptTemplate());
                    break;
                case "rule_based":
                    runtime.setType("RULE_BASED");
                    break;
                case "template":
                    runtime.setType("TEMPLATE_BASED");
                    break;
                default:
                    runtime.setType("CUSTOM");
            }
            
            // Add custom properties
            for (Map.Entry<String, Object> entry : gen.getProperties().entrySet()) {
                runtime.putConfig(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        
        // Add cache configuration
        if (config.getCache() != null && config.getCache().isEnabled()) {
            runtime.putConfig("cache.enabled", "true");
            runtime.putConfig("cache.ttl", String.valueOf(config.getCache().getTtlSeconds()));
            runtime.putConfig("cache.key_fields", String.join(",", config.getCache().getKeyFields()));
        }
        
        return runtime.build();
    }
    
    /**
     * Add generator-specific event types.
     */
    private void addGeneratorEventTypes(AgentSpecProto.Builder spec, YamlAgentConfig config) {
        String agentType = config.getId().split("\\.")[0]; // e.g., "expert" from "expert.java"
        
        // Add standard event types based on agent type
        spec.addInputEventTypes(agentType + ".request");
        spec.addOutputEventTypes(agentType + ".response");
        
        // Add capability-based event types
        for (String capability : config.getCapabilities()) {
            spec.addInputEventTypes(capability + ".requested");
            spec.addOutputEventTypes(capability + ".completed");
        }
    }
    
    /**
     * Add validation configuration.
     */
    private void addValidationConfig(AgentSpecProto.Builder spec, YamlAgentConfig config) {
        YamlAgentConfig.ValidationConfig validation = config.getValidation();
        
        // Add validation rules
        if (validation.getInputSchema() != null) {
            spec.addValidation("input_schema=" + validation.getInputSchema());
        }
        if (validation.getOutputSchema() != null) {
            spec.addValidation("output_schema=" + validation.getOutputSchema());
        }
        if (validation.isStrict()) {
            spec.addValidation("strict_mode=true");
        }
    }
    
    /**
     * Convert multiple YAML configs to manifests.
     */
    public java.util.List<AgentManifestProto> convertAll(java.util.List<YamlAgentConfig> configs) {
        return configs.stream()
            .map(this::convert)
            .toList();
    }
}
