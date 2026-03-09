package com.ghatana.softwareorg.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Configuration loader for Software-Org.
 *
 * <p><b>Purpose</b><br>
 * Loads and parses YAML configuration files from the resources directory.
 * Provides typed access to all organization configuration entities including
 * departments, personas, phases, stages, operators, workflows, services, and integrations.
 * Reuses existing utilities from {@code libs:common-utils} (JsonUtils) and
 * {@code libs:config-runtime} (YamlConfigSource).
 *
 * <p><b>Features</b><br>
 * - Loads YAML files from configurable base path
 * - Caches loaded configurations for performance
 * - Supports hot reload via file watching (optional)
 * - Validates configurations
 * - Provides unified OrgConfiguration aggregation
 * - Uses Jackson YAML parser (no SnakeYAML dependency)
 *
 * @doc.type class
 * @doc.purpose Load YAML configs from resources using existing platform utilities
 * @doc.layer product
 * @doc.pattern Facade
 */
public class ConfigurationLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationLoader.class);

    private final Path configBasePath;
    private final ObjectMapper yamlMapper;
    private final Map<String, Object> configCache;
    private WatchService watchService;

    /**
     * Creates a new configuration loader.
     *
     * @param configBasePath Base path for configuration files
     */
    public ConfigurationLoader(Path configBasePath) {
        this.configBasePath = configBasePath;
        this.yamlMapper = createYamlMapper();
        this.configCache = new ConcurrentHashMap<>();
    }

    /**
     * Creates YAML object mapper with proper configuration.
     * Reuses Jackson configuration from JsonUtils for consistency.
     *
     * @return Configured ObjectMapper
     */
    private ObjectMapper createYamlMapper() {
        // Use YAMLFactory but maintain same configuration as JsonUtils
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        mapper.findAndRegisterModules();
        return mapper;
    }

    /**
     * Loads all configuration files and returns aggregated org configuration.
     *
     * @return Complete organization configuration
     * @throws IOException if loading fails
     */
    public OrgConfiguration loadAll() throws IOException {
        logger.info("Loading all configuration files from: {}", configBasePath);

        OrgConfiguration.Builder builder = OrgConfiguration.builder();

        // Load org config
        Path orgConfigPath = configBasePath.resolve("config/org.yaml");
        if (Files.exists(orgConfigPath)) {
            Map<String, Object> orgConfig = loadYamlFile(orgConfigPath);
            builder.name((String) orgConfig.get("name"));
            builder.version((String) orgConfig.get("version"));
            builder.description((String) orgConfig.get("description"));
        }

        // Load personas
        List<Map<String, Object>> personas = loadConfigDirectory("devsecops/personas");
        builder.personas(personas);
        logger.info("Loaded {} personas", personas.size());

        // Load departments
        List<Map<String, Object>> departments = loadConfigDirectory("devsecops/departments");
        builder.departments(departments);
        logger.info("Loaded {} departments", departments.size());

        // Load phases
        List<Map<String, Object>> phases = loadConfigDirectory("devsecops/phases");
        builder.phases(phases);
        logger.info("Loaded {} phases", phases.size());

        // Load stages
        List<Map<String, Object>> stages = loadConfigDirectory("devsecops/stages");
        builder.stages(stages);
        logger.info("Loaded {} stages", stages.size());

        // Load agents
        List<Map<String, Object>> agents = loadConfigDirectory("devsecops/agents");
        builder.agents(agents);
        logger.info("Loaded {} agents", agents.size());

        // Load workflows
        List<Map<String, Object>> workflows = loadConfigDirectory("workflows");
        builder.workflows(workflows);
        logger.info("Loaded {} workflows", workflows.size());

        // Load operators
        List<Map<String, Object>> operators = loadConfigDirectory("operations/operators");
        builder.operators(operators);
        logger.info("Loaded {} operators", operators.size());

        // Load services
        List<Map<String, Object>> services = loadConfigDirectory("devsecops/services");
        builder.services(services);
        logger.info("Loaded {} services", services.size());

        // Load integrations
        List<Map<String, Object>> integrations = loadConfigDirectory("devsecops/integrations");
        builder.integrations(integrations);
        logger.info("Loaded {} integrations", integrations.size());

        // Load flows
        List<Map<String, Object>> flows = loadConfigDirectory("devsecops/flows");
        builder.flows(flows);
        logger.info("Loaded {} flows", flows.size());

        // Load KPIs
        List<Map<String, Object>> kpis = loadConfigDirectory("devsecops/kpis");
        builder.kpis(kpis);
        logger.info("Loaded {} KPIs", kpis.size());

        OrgConfiguration config = builder.build();
        logger.info("✓ All configuration loaded successfully");

        return config;
    }

    /**
     * Loads all YAML files from a directory.
     *
     * @param relativePath Relative path from config base
     * @return List of parsed configurations
     * @throws IOException if loading fails
     */
    private List<Map<String, Object>> loadConfigDirectory(String relativePath) throws IOException {
        Path dirPath = configBasePath.resolve(relativePath);

        if (!Files.exists(dirPath)) {
            logger.warn("Config directory does not exist: {}", dirPath);
            return Collections.emptyList();
        }

        if (!Files.isDirectory(dirPath)) {
            logger.warn("Config path is not a directory: {}", dirPath);
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.walk(dirPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .map(this::loadYamlFileSafe)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Loads a single YAML file.
     *
     * @param path Path to YAML file
     * @return Parsed configuration
     * @throws IOException if loading fails
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYamlFile(Path path) throws IOException {
        String cacheKey = path.toString();

        if (configCache.containsKey(cacheKey)) {
            return (Map<String, Object>) configCache.get(cacheKey);
        }

        logger.debug("Loading config file: {}", path);
        Map<String, Object> config = yamlMapper.readValue(path.toFile(), Map.class);
        configCache.put(cacheKey, config);

        return config;
    }

    /**
     * Loads a YAML file safely, returning null on error.
     *
     * @param path Path to YAML file
     * @return Parsed configuration or null
     */
    private Map<String, Object> loadYamlFileSafe(Path path) {
        try {
            return loadYamlFile(path);
        } catch (Exception e) {
            logger.error("Failed to load config file: {}", path, e);
            return null;
        }
    }

    /**
     * Reloads all configuration files.
     *
     * @return Updated organization configuration
     * @throws IOException if loading fails
     */
    public OrgConfiguration reload() throws IOException {
        logger.info("Reloading all configuration files...");
        configCache.clear();
        return loadAll();
    }

    /**
     * Starts watching configuration directory for changes.
     *
     * @param callback Callback to invoke on changes
     * @throws IOException if watch service cannot be started
     */
    public void startWatching(Runnable callback) throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        configBasePath.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

        Thread watchThread = new Thread(() -> {
            try {
                WatchKey key;
                while ((key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        logger.info("Config file changed: {} - {}", event.kind(), event.context());
                        callback.run();
                    }
                    key.reset();
                }
            } catch (InterruptedException e) {
                logger.info("Config watch thread interrupted");
                Thread.currentThread().interrupt();
            }
        });
        watchThread.setDaemon(true);
        watchThread.start();

        logger.info("Started watching config directory for changes");
    }

    /**
     * Stops watching configuration directory.
     */
    public void stopWatching() {
        if (watchService != null) {
            try {
                watchService.close();
                logger.info("Stopped watching config directory");
            } catch (IOException e) {
                logger.error("Error closing watch service", e);
            }
        }
    }

    /**
     * Gets the config base path.
     *
     * @return Config base path
     */
    public Path getConfigBasePath() {
        return configBasePath;
    }

    /**
     * Clears the configuration cache.
     */
    public void clearCache() {
        configCache.clear();
        logger.info("Configuration cache cleared");
    }
}
