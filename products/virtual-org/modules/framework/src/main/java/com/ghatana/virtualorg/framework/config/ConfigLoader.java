package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class for loading configuration from YAML.
 *
 * <p><b>Purpose</b><br>
 * ConfigLoader provides static methods to parse YAML configuration
 * into typed Java objects using Jackson.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // From YAML string
 * AgentConfig config = ConfigLoader.loadConfig(yamlString, AgentConfig.class);
 *
 * // From file path
 * AgentConfig config = ConfigLoader.loadConfigFromFile(path, AgentConfig.class);
 *
 * // From resource
 * AgentConfig config = ConfigLoader.loadConfigFromResource("/agents/ceo.yaml", AgentConfig.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose YAML configuration loading utility
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class ConfigLoader {

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private ConfigLoader() {
        // Utility class
    }

    /**
     * Loads configuration from a YAML string.
     *
     * @param yaml  The YAML string to parse.
     * @param clazz The target class type.
     * @param <T>   The configuration type.
     * @return The parsed configuration object.
     * @throws IOException If parsing fails.
     */
    public static <T> T loadConfig(String yaml, Class<T> clazz) throws IOException {
        return YAML_MAPPER.readValue(yaml, clazz);
    }

    /**
     * Loads configuration from a file path.
     *
     * @param path  The path to the YAML file.
     * @param clazz The target class type.
     * @param <T>   The configuration type.
     * @return The parsed configuration object.
     * @throws IOException If reading or parsing fails.
     */
    public static <T> T loadConfigFromFile(Path path, Class<T> clazz) throws IOException {
        String yaml = Files.readString(path);
        return loadConfig(yaml, clazz);
    }

    /**
     * Loads configuration from a classpath resource.
     *
     * @param resourcePath The resource path (e.g., "/agents/ceo.yaml").
     * @param clazz        The target class type.
     * @param <T>          The configuration type.
     * @return The parsed configuration object.
     * @throws IOException If reading or parsing fails.
     */
    public static <T> T loadConfigFromResource(String resourcePath, Class<T> clazz) throws IOException {
        try (InputStream is = ConfigLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return YAML_MAPPER.readValue(is, clazz);
        }
    }

    /**
     * Gets the shared YAML ObjectMapper instance.
     *
     * @return The YAML ObjectMapper.
     */
    public static ObjectMapper getYamlMapper() {
        return YAML_MAPPER;
    }
}
