package com.ghatana.products.yappc.service;

import com.ghatana.products.yappc.domain.task.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Loads task definitions from YAML files.
 *
 * @doc.type class
 * @doc.purpose YAML-based task definition loading
 * @doc.layer product
 * @doc.pattern Provider, SPI
 */
public class YamlTaskDefinitionProvider implements TaskDefinitionProvider {

    private static final Logger LOG = LoggerFactory.getLogger(YamlTaskDefinitionProvider.class);

    private final Path configDirectory;
    private final Executor executor;
    private final Yaml yaml;

    public YamlTaskDefinitionProvider(@NotNull Path configDirectory, @NotNull Executor executor) {
        this.configDirectory = configDirectory;
        this.executor = executor;
        this.yaml = new Yaml();
    }

    @Override
    @NotNull
    public String getName() {
        return "yaml";
    }

    @Override
    public int getPriority() {
        return 100; // Standard priority for YAML configs
    }

    @Override
    @NotNull
    public Promise<List<TaskDefinition>> loadTasks() {
        return Promise.ofBlocking(executor, () -> {
            LOG.info("Loading task definitions from: {}", configDirectory);

            List<TaskDefinition> tasks = new ArrayList<>();

            // Find all YAML files in config directory
            try (Stream<Path> paths = Files.walk(configDirectory)) {
                List<Path> yamlFiles = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".yaml") || path.toString().endsWith(".yml"))
                        .toList();

                LOG.debug("Found {} YAML files", yamlFiles.size());

                for (Path yamlFile : yamlFiles) {
                    try {
                        List<TaskDefinition> fileTasks = loadTasksFromFile(yamlFile);
                        tasks.addAll(fileTasks);
                        LOG.debug("Loaded {} tasks from {}", fileTasks.size(), yamlFile.getFileName());
                    } catch (Exception e) {
                        LOG.error("Failed to load tasks from {}: {}", yamlFile, e.getMessage(), e);
                    }
                }

                LOG.info("Loaded total {} tasks from {} files", tasks.size(), yamlFiles.size());
                return tasks;
            }
        });
    }

    /**
     * Loads tasks from a single YAML file.
     */
    @NotNull
    private List<TaskDefinition> loadTasksFromFile(@NotNull Path yamlFile) throws IOException {
        try (InputStream inputStream = Files.newInputStream(yamlFile)) {
            Map<String, Object> data = yaml.load(inputStream);

            if (data == null || !data.containsKey("tasks")) {
                LOG.warn("No tasks found in file: {}", yamlFile);
                return List.of();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> taskMaps = (List<Map<String, Object>>) data.get("tasks");

            return taskMaps.stream()
                    .map(this::parseTaskDefinition)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    /**
     * Parses a single task definition from YAML map.
     */
    private TaskDefinition parseTaskDefinition(@NotNull Map<String, Object> taskMap) {
        try {
            String id = (String) taskMap.get("id");
            String name = (String) taskMap.get("name");
            String description = (String) taskMap.get("description");
            String domain = (String) taskMap.get("domain");

            // Parse phase
            String phaseStr = (String) taskMap.get("phase");
            SDLCPhase phase = parsePhase(phaseStr);

            // Parse capabilities
            @SuppressWarnings("unchecked")
            List<String> capabilities = (List<String>) taskMap.getOrDefault("requiredCapabilities", List.of());

            // Parse parameters
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> parametersMap =
                    (Map<String, Map<String, Object>>) taskMap.getOrDefault("parameters", Map.of());
            Map<String, ParameterSpec> parameters = parseParameters(parametersMap);

            // Parse complexity
            String complexityStr = (String) taskMap.getOrDefault("complexity", "MODERATE");
            TaskComplexity complexity = parseComplexity(complexityStr);

            // Parse dependencies
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> depsMap =
                    (List<Map<String, Object>>) taskMap.getOrDefault("dependencies", List.of());
            List<TaskDependency> dependencies = parseDependencies(depsMap);

            // Parse metadata
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) taskMap.getOrDefault("metadata", Map.of());

            return TaskDefinition.builder()
                    .id(id)
                    .name(name)
                    .description(description)
                    .domain(domain)
                    .phase(phase)
                    .requiredCapabilities(capabilities)
                    .parameters(parameters)
                    .complexity(complexity)
                    .dependencies(dependencies)
                    .metadata(metadata)
                    .build();

        } catch (Exception e) {
            LOG.error("Failed to parse task definition: {}", e.getMessage(), e);
            return null;
        }
    }

    @NotNull
    private SDLCPhase parsePhase(String phaseStr) {
        if (phaseStr == null) return SDLCPhase.IMPLEMENTATION;
        try {
            return SDLCPhase.valueOf(phaseStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown phase: {}, defaulting to IMPLEMENTATION", phaseStr);
            return SDLCPhase.IMPLEMENTATION;
        }
    }

    @NotNull
    private TaskComplexity parseComplexity(String complexityStr) {
        if (complexityStr == null || complexityStr.isBlank()) {
            return TaskComplexity.MODERATE;
        }

        String normalized = complexityStr.trim().toUpperCase();
        if ("MEDIUM".equals(normalized)) {
            return TaskComplexity.MODERATE;
        }

        try {
            return TaskComplexity.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown complexity: {}, defaulting to MODERATE", complexityStr);
            return TaskComplexity.MODERATE;
        }
    }

    @NotNull
    private Map<String, ParameterSpec> parseParameters(@NotNull Map<String, Map<String, Object>> parametersMap) {
        Map<String, ParameterSpec> parameters = new HashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : parametersMap.entrySet()) {
            String paramName = entry.getKey();
            Map<String, Object> paramSpec = entry.getValue();

            String type = (String) paramSpec.getOrDefault("type", "string");
            Boolean required = (Boolean) paramSpec.getOrDefault("required", false);
            String description = (String) paramSpec.getOrDefault("description", "");
            Object defaultValue = paramSpec.get("default");

            parameters.put(paramName, new ParameterSpec(type, required, description, defaultValue));
        }

        return parameters;
    }

    @NotNull
    private List<TaskDependency> parseDependencies(@NotNull List<Map<String, Object>> depsMap) {
        return depsMap.stream()
                .map(depMap -> {
                    String taskId = (String) depMap.get("taskId");
                    Boolean required = (Boolean) depMap.getOrDefault("required", true);
                    String description = (String) depMap.getOrDefault("description", "Dependency task: " + taskId);
                    if (description == null || description.isBlank()) {
                        description = "Dependency task: " + taskId;
                    }
                    return new TaskDependency(taskId, required, description);
                })
                .toList();
    }
}
