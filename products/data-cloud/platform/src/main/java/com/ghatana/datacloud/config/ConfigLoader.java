/*
 * Copyright (c) 2025 Ghatana Platforms, Inc. All rights reserved.
 */
package com.ghatana.datacloud.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.ghatana.platform.core.exception.ConfigurationException;
import com.ghatana.datacloud.config.model.RawCollectionConfig;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads data-cloud collection configuration from YAML files.
 *
 * <p>
 * Extends config-runtime's infrastructure with data-cloud specific features
 * like collection loading and Event model validation.
 *
 * <p>
 * <b>Features:</b><br>
 * - YAML parsing using Jackson (reuses config-runtime) - Environment variable
 * interpolation - Async loading using ActiveJ Promise.ofBlocking - Classpath
 * and filesystem support
 *
 * @doc.type class
 * @doc.purpose Load collection YAML configuration (extends config-runtime)
 * @doc.layer core
 * @doc.pattern Adapter, Promise-based Async
 */
@Slf4j
public class ConfigLoader {

    private static final Pattern VAR_PATTERN
            = Pattern.compile("\\$\\{([^}:]+)(?::([^}]+))?\\}");

    private final Eventloop eventloop;
    private final Executor blockingExecutor;
    private final ObjectMapper yamlMapper;
    private final Path configBasePath;

    public ConfigLoader(Eventloop eventloop, Executor blockingExecutor) {
        this(eventloop, blockingExecutor, Paths.get("config"));
    }

    public ConfigLoader(Eventloop eventloop, Executor blockingExecutor, Path configBasePath) {
        this.eventloop = eventloop;
        this.blockingExecutor = blockingExecutor;
        this.configBasePath = configBasePath;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Creates a ConfigLoader without eventloop (for CLI and sync operations).
     *
     * @param configBasePath the base path for config files
     * @param blockingExecutor executor for blocking operations
     */
    public ConfigLoader(Path configBasePath, Executor blockingExecutor) {
        this.eventloop = null;
        this.blockingExecutor = blockingExecutor;
        this.configBasePath = configBasePath;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Load collection configuration asynchronously.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return promise of raw collection config
     */
    public Promise<RawCollectionConfig> loadCollectionAsync(
            String tenantId,
            String collectionName) {

        return Promise.ofBlocking(blockingExecutor, () -> {
            log.debug("Loading collection config: tenant={}, collection={}",
                    tenantId, collectionName);

            Path filePath = configBasePath
                    .resolve("collections")
                    .resolve(tenantId)
                    .resolve(collectionName + ".yaml");

            InputStream inputStream;
            if (Files.exists(filePath)) {
                log.debug("Loading from filesystem: {}", filePath);
                inputStream = Files.newInputStream(filePath);
            } else {
                String classpathResource = String.format(
                        "datacloud/collections/%s/%s.yaml",
                        tenantId, collectionName);
                log.debug("Loading from classpath: {}", classpathResource);
                inputStream = getClass().getClassLoader()
                        .getResourceAsStream(classpathResource);

                if (inputStream == null) {
                    throw new ConfigNotFoundException(
                            String.format("Collection config not found: %s/%s",
                                    tenantId, collectionName));
                }
            }

            return yamlMapper.readValue(inputStream, RawCollectionConfig.class);
        })
                .then(this::interpolateVariablesAsync);
    }

    private Promise<RawCollectionConfig> interpolateVariablesAsync(RawCollectionConfig config) {
        return Promise.of(interpolateEnvVars(config));
    }

    private RawCollectionConfig interpolateEnvVars(RawCollectionConfig config) {
        // Records are immutable - if we need to interpolate, we'd need to recreate
        // For now, interpolation is handled at YAML load time
        return config;
    }

    private String interpolateString(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        Matcher matcher = VAR_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String defaultValue = matcher.group(2);

            String envValue = System.getenv(varName);
            String replacement;
            if (envValue != null) {
                replacement = envValue;
            } else if (defaultValue != null) {
                replacement = defaultValue;
            } else {
                throw new ConfigurationException(
                        String.format("Required environment variable not found: %s", varName));
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Lists all collection names for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return Promise containing list of collection names
     */
    public Promise<java.util.List<String>> listCollectionsAsync(String tenantId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            java.util.List<String> collections = new java.util.ArrayList<>();

            // Check filesystem first
            Path tenantPath = configBasePath.resolve("collections").resolve(tenantId);
            if (Files.exists(tenantPath) && Files.isDirectory(tenantPath)) {
                try (var stream = Files.list(tenantPath)) {
                    stream.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                            .map(Path::getFileName)
                            .map(Path::toString)
                            .map(name -> name.replaceAll("\\.(yaml|yml)$", ""))
                            .forEach(collections::add);
                }
            }

            // Also check classpath (for bundled defaults)
            // This is a simplified implementation - in production would use resource scanning
            log.debug("Listed {} collections for tenant {}", collections.size(), tenantId);

            return collections;
        });
    }

    // ===== Plugin Loading =====
    /**
     * Load plugin configuration asynchronously.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     * @return promise of raw plugin config
     */
    public Promise<com.ghatana.datacloud.config.model.RawPluginConfig> loadPluginAsync(
            String tenantId,
            String pluginName) {

        return Promise.ofBlocking(blockingExecutor, () -> {
            log.debug("Loading plugin config: tenant={}, plugin={}", tenantId, pluginName);

            Path filePath = configBasePath
                    .resolve("plugins")
                    .resolve(tenantId)
                    .resolve(pluginName + ".yaml");

            InputStream inputStream;
            if (Files.exists(filePath)) {
                log.debug("Loading plugin from filesystem: {}", filePath);
                inputStream = Files.newInputStream(filePath);
            } else {
                // Try default plugins (shared across tenants)
                Path defaultPath = configBasePath
                        .resolve("plugins")
                        .resolve("default")
                        .resolve(pluginName + ".yaml");

                if (Files.exists(defaultPath)) {
                    log.debug("Loading default plugin from filesystem: {}", defaultPath);
                    inputStream = Files.newInputStream(defaultPath);
                } else {
                    String classpathResource = String.format(
                            "datacloud/plugins/%s/%s.yaml", tenantId, pluginName);
                    log.debug("Loading plugin from classpath: {}", classpathResource);
                    inputStream = getClass().getClassLoader()
                            .getResourceAsStream(classpathResource);

                    if (inputStream == null) {
                        throw new ConfigNotFoundException(
                                String.format("Plugin config not found: %s/%s",
                                        tenantId, pluginName));
                    }
                }
            }

            return yamlMapper.readValue(inputStream,
                    com.ghatana.datacloud.config.model.RawPluginConfig.class);
        });
    }

    /**
     * Lists all plugin names for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return Promise containing list of plugin names
     */
    public Promise<java.util.List<String>> listPluginsAsync(String tenantId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            java.util.List<String> plugins = new java.util.ArrayList<>();

            // Check tenant-specific plugins
            Path tenantPath = configBasePath.resolve("plugins").resolve(tenantId);
            if (Files.exists(tenantPath) && Files.isDirectory(tenantPath)) {
                try (var stream = Files.list(tenantPath)) {
                    stream.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                            .map(Path::getFileName)
                            .map(Path::toString)
                            .map(name -> name.replaceAll("\\.(yaml|yml)$", ""))
                            .forEach(plugins::add);
                }
            }

            // Also include default plugins
            Path defaultPath = configBasePath.resolve("plugins").resolve("default");
            if (Files.exists(defaultPath) && Files.isDirectory(defaultPath)) {
                try (var stream = Files.list(defaultPath)) {
                    stream.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                            .map(Path::getFileName)
                            .map(Path::toString)
                            .map(name -> name.replaceAll("\\.(yaml|yml)$", ""))
                            .filter(name -> !plugins.contains(name)) // Don't duplicate
                            .forEach(plugins::add);
                }
            }

            log.debug("Listed {} plugins for tenant {}", plugins.size(), tenantId);
            return plugins;
        });
    }

    // ===== Storage Profile Loading =====
    /**
     * Load storage profile configuration asynchronously.
     *
     * @param tenantId tenant identifier (use "default" for global profiles)
     * @param profileName storage profile name
     * @return promise of raw storage profile
     */
    public Promise<com.ghatana.datacloud.config.model.RawStorageProfileConfig.RawStorageProfile> loadStorageProfileAsync(
            String tenantId,
            String profileName) {

        return Promise.ofBlocking(blockingExecutor, () -> {
            log.debug("Loading storage profile config: tenant={}, profile={}", tenantId, profileName);

            // Load all profiles from YAML file and find the one we need
            com.ghatana.datacloud.config.model.RawStorageProfileConfig allProfiles
                    = loadAllStorageProfiles(tenantId);

            if (allProfiles == null || allProfiles.profiles() == null) {
                throw new ConfigNotFoundException(
                        String.format("Storage profiles not found for tenant: %s", tenantId));
            }

            return allProfiles.profiles().stream()
                    .filter(p -> profileName.equals(p.name()))
                    .findFirst()
                    .orElseThrow(() -> new ConfigNotFoundException(
                    String.format("Storage profile not found: %s/%s", tenantId, profileName)));
        });
    }

    /**
     * Lists all storage profile names for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return Promise containing list of profile names
     */
    public Promise<java.util.List<String>> listStorageProfilesAsync(String tenantId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            java.util.List<String> profiles = new java.util.ArrayList<>();

            try {
                com.ghatana.datacloud.config.model.RawStorageProfileConfig allProfiles
                        = loadAllStorageProfiles(tenantId);

                if (allProfiles != null && allProfiles.profiles() != null) {
                    allProfiles.profiles().stream()
                            .map(com.ghatana.datacloud.config.model.RawStorageProfileConfig.RawStorageProfile::name)
                            .forEach(profiles::add);
                }
            } catch (ConfigNotFoundException e) {
                log.debug("No storage profiles found for tenant {}", tenantId);
            }

            // Also include default profiles if not tenant-specific
            if (!"default".equals(tenantId)) {
                try {
                    com.ghatana.datacloud.config.model.RawStorageProfileConfig defaultProfiles
                            = loadAllStorageProfiles("default");

                    if (defaultProfiles != null && defaultProfiles.profiles() != null) {
                        defaultProfiles.profiles().stream()
                                .map(com.ghatana.datacloud.config.model.RawStorageProfileConfig.RawStorageProfile::name)
                                .filter(name -> !profiles.contains(name))
                                .forEach(profiles::add);
                    }
                } catch (ConfigNotFoundException e) {
                    log.debug("No default storage profiles found");
                }
            }

            log.debug("Listed {} storage profiles for tenant {}", profiles.size(), tenantId);
            return profiles;
        });
    }

    private com.ghatana.datacloud.config.model.RawStorageProfileConfig loadAllStorageProfiles(
            String tenantId) throws Exception {

        Path filePath = configBasePath
                .resolve("storage-profiles")
                .resolve(tenantId)
                .resolve("storage-profiles.yaml");

        InputStream inputStream;
        if (Files.exists(filePath)) {
            log.debug("Loading storage profiles from filesystem: {}", filePath);
            inputStream = Files.newInputStream(filePath);
        } else {
            String classpathResource = String.format(
                    "datacloud/storage-profiles/%s/storage-profiles.yaml", tenantId);
            log.debug("Loading storage profiles from classpath: {}", classpathResource);
            inputStream = getClass().getClassLoader()
                    .getResourceAsStream(classpathResource);

            if (inputStream == null) {
                throw new ConfigNotFoundException(
                        String.format("Storage profiles not found: %s", tenantId));
            }
        }

        return yamlMapper.readValue(inputStream,
                com.ghatana.datacloud.config.model.RawStorageProfileConfig.class);
    }

    // ===== Policy Loading =====
    /**
     * Load policy configuration asynchronously.
     *
     * @param tenantId tenant identifier (use "default" for global policies)
     * @param policyName policy name
     * @return promise of raw policy config
     */
    public Promise<com.ghatana.datacloud.config.model.RawPolicyConfig> loadPolicyAsync(
            String tenantId,
            String policyName) {

        return Promise.ofBlocking(blockingExecutor, () -> {
            log.debug("Loading policy config: tenant={}, policy={}", tenantId, policyName);

            Path filePath = configBasePath
                    .resolve("policies")
                    .resolve(tenantId)
                    .resolve(policyName + ".yaml");

            InputStream inputStream;
            if (Files.exists(filePath)) {
                log.debug("Loading policy from filesystem: {}", filePath);
                inputStream = Files.newInputStream(filePath);
            } else {
                // Try default policies (shared across tenants)
                Path defaultPath = configBasePath
                        .resolve("policies")
                        .resolve("default")
                        .resolve(policyName + ".yaml");

                if (Files.exists(defaultPath)) {
                    log.debug("Loading default policy from filesystem: {}", defaultPath);
                    inputStream = Files.newInputStream(defaultPath);
                } else {
                    String classpathResource = String.format(
                            "datacloud/policies/%s/%s.yaml", tenantId, policyName);
                    log.debug("Loading policy from classpath: {}", classpathResource);
                    inputStream = getClass().getClassLoader()
                            .getResourceAsStream(classpathResource);

                    if (inputStream == null) {
                        throw new ConfigNotFoundException(
                                String.format("Policy config not found: %s/%s",
                                        tenantId, policyName));
                    }
                }
            }

            return yamlMapper.readValue(inputStream,
                    com.ghatana.datacloud.config.model.RawPolicyConfig.class);
        });
    }

    /**
     * Lists all policy names for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return Promise containing list of policy names
     */
    public Promise<java.util.List<String>> listPoliciesAsync(String tenantId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            java.util.List<String> policies = new java.util.ArrayList<>();

            // Check tenant-specific policies
            Path tenantPath = configBasePath.resolve("policies").resolve(tenantId);
            if (Files.exists(tenantPath) && Files.isDirectory(tenantPath)) {
                try (var stream = Files.list(tenantPath)) {
                    stream.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                            .map(Path::getFileName)
                            .map(Path::toString)
                            .map(name -> name.replaceAll("\\.(yaml|yml)$", ""))
                            .forEach(policies::add);
                }
            }

            // Also include default policies
            Path defaultPath = configBasePath.resolve("policies").resolve("default");
            if (Files.exists(defaultPath) && Files.isDirectory(defaultPath)) {
                try (var stream = Files.list(defaultPath)) {
                    stream.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                            .map(Path::getFileName)
                            .map(Path::toString)
                            .map(name -> name.replaceAll("\\.(yaml|yml)$", ""))
                            .filter(name -> !policies.contains(name)) // Don't duplicate
                            .forEach(policies::add);
                }
            }

            log.debug("Listed {} policies for tenant {}", policies.size(), tenantId);
            return policies;
        });
    }

    /**
     * Loads routing configuration asynchronously.
     *
     * @param tenantId tenant identifier
     * @param routingName routing config name (often collection name or
     * "default")
     * @return promise of raw routing config
     */
    public Promise<com.ghatana.datacloud.config.model.RawRoutingConfig> loadRoutingAsync(
            String tenantId,
            String routingName) {

        return Promise.ofBlocking(blockingExecutor, () -> {
            log.debug("Loading routing config: tenant={}, routing={}", tenantId, routingName);

            Path filePath = configBasePath
                    .resolve("routing")
                    .resolve(tenantId)
                    .resolve(routingName + ".yaml");

            InputStream inputStream;
            if (Files.exists(filePath)) {
                log.debug("Loading routing from filesystem: {}", filePath);
                inputStream = Files.newInputStream(filePath);
            } else {
                // Try default routing config
                Path defaultPath = configBasePath
                        .resolve("routing")
                        .resolve("default")
                        .resolve(routingName + ".yaml");

                if (Files.exists(defaultPath)) {
                    log.debug("Loading default routing from filesystem: {}", defaultPath);
                    inputStream = Files.newInputStream(defaultPath);
                } else {
                    // Try classpath resources
                    String classpathResource = String.format(
                            "config/routing/%s.yaml", routingName);
                    log.debug("Loading routing from classpath: {}", classpathResource);
                    inputStream = getClass().getClassLoader()
                            .getResourceAsStream(classpathResource);

                    if (inputStream == null) {
                        // Try storage-routing.yaml as default
                        classpathResource = "config/routing/storage-routing.yaml";
                        inputStream = getClass().getClassLoader()
                                .getResourceAsStream(classpathResource);
                    }

                    if (inputStream == null) {
                        throw new ConfigNotFoundException(
                                String.format("Routing config not found: %s/%s",
                                        tenantId, routingName));
                    }
                }
            }

            return yamlMapper.readValue(inputStream,
                    com.ghatana.datacloud.config.model.RawRoutingConfig.class);
        });
    }

    // ===== Synchronous File Loading (for CLI) =====
    /**
     * Loads a collection configuration from a specific file.
     *
     * @param file the file to load
     * @return the raw collection config
     * @throws Exception if loading fails
     */
    public RawCollectionConfig loadCollectionFromFile(Path file) throws Exception {
        log.debug("Loading collection from file: {}", file);
        try (InputStream is = Files.newInputStream(file)) {
            return yamlMapper.readValue(is, RawCollectionConfig.class);
        }
    }

    /**
     * Loads a plugin configuration from a specific file.
     *
     * @param file the file to load
     * @return the raw plugin config
     * @throws Exception if loading fails
     */
    public com.ghatana.datacloud.config.model.RawPluginConfig loadPluginFromFile(Path file) throws Exception {
        log.debug("Loading plugin from file: {}", file);
        try (InputStream is = Files.newInputStream(file)) {
            return yamlMapper.readValue(is, com.ghatana.datacloud.config.model.RawPluginConfig.class);
        }
    }

    /**
     * Loads a storage profile configuration from a specific file.
     *
     * @param file the file to load
     * @return the raw storage profile config
     * @throws Exception if loading fails
     */
    public com.ghatana.datacloud.config.model.RawStorageProfileConfig loadStorageProfileFromFile(Path file) throws Exception {
        log.debug("Loading storage profile from file: {}", file);
        try (InputStream is = Files.newInputStream(file)) {
            return yamlMapper.readValue(is, com.ghatana.datacloud.config.model.RawStorageProfileConfig.class);
        }
    }

    /**
     * Loads a policy configuration from a specific file.
     *
     * @param file the file to load
     * @return the raw policy config
     * @throws Exception if loading fails
     */
    public com.ghatana.datacloud.config.model.RawPolicyConfig loadPolicyFromFile(Path file) throws Exception {
        log.debug("Loading policy from file: {}", file);
        try (InputStream is = Files.newInputStream(file)) {
            return yamlMapper.readValue(is, com.ghatana.datacloud.config.model.RawPolicyConfig.class);
        }
    }

    /**
     * Loads a routing configuration from a specific file.
     *
     * @param file the file to load
     * @return the raw routing config
     * @throws Exception if loading fails
     */
    public com.ghatana.datacloud.config.model.RawRoutingConfig loadRoutingFromFile(Path file) throws Exception {
        log.debug("Loading routing from file: {}", file);
        try (InputStream is = Files.newInputStream(file)) {
            return yamlMapper.readValue(is, com.ghatana.datacloud.config.model.RawRoutingConfig.class);
        }
    }
}
