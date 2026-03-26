package com.ghatana.yappc.agents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.activej.inject.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads agent configurations from YAML files.
 * 
 * This loader discovers and parses YAML agent definitions from the classpath
 * or file system, converting them into {@link YamlAgentConfig} objects.
 * 
 * <p><b>Usage:</b>
 * <pre>{@code
 * YamlAgentLoader loader = new YamlAgentLoader();
 * List<YamlAgentConfig> configs = loader.loadFromClasspath("agents/");
 * }</pre>
 * 
 * @see YamlAgentConfig
 * @doc.type class
 * @doc.pattern Loader
 * @doc.purpose Load YAML agent configurations from classpath
 * @doc.layer product
 */
public class YamlAgentLoader {
    private static final Logger log = LoggerFactory.getLogger(YamlAgentLoader.class);
    
    private final ObjectMapper yamlMapper;
    
    @Inject
    public YamlAgentLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }
    
    /**
     * Load all agent configurations from the classpath.
     * 
     * @param resourcePath The resource path to search (e.g., "agents/")
     * @return List of loaded configurations
     */
    public List<YamlAgentConfig> loadFromClasspath(String resourcePath) {
        List<YamlAgentConfig> configs = new ArrayList<>();
        
        try {
            var resources = getClass().getClassLoader().getResources(resourcePath);
            while (resources.hasMoreElements()) {
                var url = resources.nextElement();
                if (url.getProtocol().equals("file")) {
                    Path path = Paths.get(url.toURI());
                    configs.addAll(loadFromDirectory(path));
                }
            }
        } catch (Exception e) {
            log.error("Failed to load agents from classpath: {}", resourcePath, e);
        }
        
        return configs;
    }
    
    /**
     * Load all agent configurations from a directory.
     * 
     * @param directory The directory containing YAML files
     * @return List of loaded configurations
     */
    public List<YamlAgentConfig> loadFromDirectory(Path directory) {
        List<YamlAgentConfig> configs = new ArrayList<>();
        
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            log.warn("Agent directory does not exist: {}", directory);
            return configs;
        }
        
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(path -> path.toString().endsWith(".yaml") || path.toString().endsWith(".yml"))
                .forEach(path -> {
                    try {
                        YamlAgentConfig config = loadFromFile(path);
                        configs.add(config);
                        log.info("Loaded agent config: {} from {}", config.getId(), path.getFileName());
                    } catch (Exception e) {
                        log.error("Failed to load agent config from: {}", path, e);
                    }
                });
        } catch (IOException e) {
            log.error("Failed to list agent configs in: {}", directory, e);
        }
        
        return configs;
    }
    
    /**
     * Load a single agent configuration from a file.
     * 
     * @param file The YAML file to load
     * @return The loaded configuration
     * @throws IOException if loading fails
     */
    public YamlAgentConfig loadFromFile(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            return loadFromStream(is);
        }
    }
    
    /**
     * Load a single agent configuration from an input stream.
     * 
     * @param stream The input stream containing YAML
     * @return The loaded configuration
     * @throws IOException if loading fails
     */
    public YamlAgentConfig loadFromStream(InputStream stream) throws IOException {
        AgentYamlDto dto = yamlMapper.readValue(stream, AgentYamlDto.class);
        return convertToConfig(dto);
    }
    
    /**
     * Load a single agent configuration from a string.
     * 
     * @param yamlContent The YAML content as string
     * @return The loaded configuration
     * @throws IOException if parsing fails
     */
    public YamlAgentConfig loadFromString(String yamlContent) throws IOException {
        AgentYamlDto dto = yamlMapper.readValue(yamlContent, AgentYamlDto.class);
        return convertToConfig(dto);
    }
    
    /**
     * Convert DTO to configuration object.
     */
    private YamlAgentConfig convertToConfig(AgentYamlDto dto) {
        YamlAgentConfig.Builder builder = YamlAgentConfig.builder()
            .id(dto.agent.id)
            .name(dto.agent.name)
            .description(dto.agent.description)
            .version(dto.agent.version)
            .tags(Set.copyOf(dto.agent.tags))
            .capabilities(Set.copyOf(dto.agent.capabilities))
            .metadata(dto.agent.metadata != null ? dto.agent.metadata : Map.of());
        
        // Generator config
        if (dto.agent.generator != null) {
            YamlAgentConfig.GeneratorConfig genConfig = new YamlAgentConfig.GeneratorConfig(
                dto.agent.generator.type,
                dto.agent.generator.prompt_template,
                dto.agent.generator.model,
                dto.agent.generator.temperature,
                dto.agent.generator.max_tokens,
                dto.agent.generator.max_retries,
                dto.agent.generator.properties
            );
            builder.generator(genConfig);
        }
        
        // Validation config
        if (dto.agent.validation != null) {
            YamlAgentConfig.ValidationConfig valConfig = new YamlAgentConfig.ValidationConfig(
                dto.agent.validation.input_schema,
                dto.agent.validation.output_schema,
                dto.agent.validation.strict != null ? dto.agent.validation.strict : true
            );
            builder.validation(valConfig);
        }
        
        // Cache config
        if (dto.agent.cache != null) {
            YamlAgentConfig.CacheConfig cacheConfig = new YamlAgentConfig.CacheConfig(
                dto.agent.cache.enabled != null ? dto.agent.cache.enabled : false,
                dto.agent.cache.ttl != null ? dto.agent.cache.ttl : 3600,
                dto.agent.cache.key_fields != null ? Set.copyOf(dto.agent.cache.key_fields) : Set.of()
            );
            builder.cache(cacheConfig);
        }
        
        return builder.build();
    }
    
    /**
     * DTO for YAML parsing.
     */
    private static class AgentYamlDto {
        public AgentDto agent;
    }
    
    private static class AgentDto {
        public String id;
        public String name;
        public String description;
        public String version;
        public List<String> tags = List.of();
        public List<String> capabilities = List.of();
        public GeneratorDto generator;
        public ValidationDto validation;
        public CacheDto cache;
        public Map<String, Object> metadata = Map.of();
    }
    
    private static class GeneratorDto {
        public String type;
        public String prompt_template;
        public String model;
        public double temperature = 0.7;
        public int max_tokens = 2000;
        public int max_retries = 3;
        public Map<String, Object> properties = Map.of();
    }
    
    private static class ValidationDto {
        public String input_schema;
        public String output_schema;
        public Boolean strict;
    }
    
    private static class CacheDto {
        public Boolean enabled;
        public Long ttl;
        public List<String> key_fields;
    }
}
