package com.ghatana.softwareorg.launcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Launcher configuration.
 *
 * <p><b>Purpose</b><br>
 * Holds all configuration parameters for the Software-Org launcher including
 * config paths, ports, environment settings, and feature flags.
 *
 * <p><b>Configuration Sources</b><br>
 * 1. Command line arguments (highest priority)
 * 2. Environment variables
 * 3. Default values (lowest priority)
 *
 * @doc.type class
 * @doc.purpose Launcher configuration with command-line and environment variable support
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class LauncherConfig {

    private static final String DEFAULT_CONFIG_PATH = "products/software-org/config";
    private static final int DEFAULT_API_PORT = 8080;
    private static final String DEFAULT_ENVIRONMENT = "development";

    private final Path configPath;
    private final int apiPort;
    private final String environment;
    private final boolean enableMetrics;
    private final boolean enableHealthCheck;
    private final boolean enableHotReload;
    private final Map<String, String> additionalProperties;

    private LauncherConfig(Builder builder) {
        this.configPath = builder.configPath;
        this.apiPort = builder.apiPort;
        this.environment = builder.environment;
        this.enableMetrics = builder.enableMetrics;
        this.enableHealthCheck = builder.enableHealthCheck;
        this.enableHotReload = builder.enableHotReload;
        this.additionalProperties = new HashMap<>(builder.additionalProperties);
    }

    /**
     * Creates configuration from command line arguments.
     *
     * @param args Command line arguments
     * @return Launcher configuration
     */
    public static LauncherConfig fromArgs(String[] args) {
        Builder builder = builder();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.startsWith("--config-path=")) {
                builder.configPath(Paths.get(arg.substring("--config-path=".length())));
            } else if (arg.equals("--config-path") && i + 1 < args.length) {
                builder.configPath(Paths.get(args[++i]));
            } else if (arg.startsWith("--port=")) {
                builder.apiPort(Integer.parseInt(arg.substring("--port=".length())));
            } else if (arg.equals("--port") && i + 1 < args.length) {
                builder.apiPort(Integer.parseInt(args[++i]));
            } else if (arg.startsWith("--env=")) {
                builder.environment(arg.substring("--env=".length()));
            } else if (arg.equals("--env") && i + 1 < args.length) {
                builder.environment(args[++i]);
            } else if (arg.equals("--no-metrics")) {
                builder.enableMetrics(false);
            } else if (arg.equals("--no-health-check")) {
                builder.enableHealthCheck(false);
            } else if (arg.equals("--enable-hot-reload")) {
                builder.enableHotReload(true);
            } else if (arg.startsWith("--")) {
                // Additional properties
                String[] parts = arg.substring(2).split("=", 2);
                if (parts.length == 2) {
                    builder.additionalProperty(parts[0], parts[1]);
                }
            }
        }

        // Override with environment variables
        String envConfigPath = System.getenv("SOFTWARE_ORG_CONFIG_PATH");
        if (envConfigPath != null) {
            builder.configPath(Paths.get(envConfigPath));
        }

        String envApiPort = System.getenv("SOFTWARE_ORG_API_PORT");
        if (envApiPort != null) {
            builder.apiPort(Integer.parseInt(envApiPort));
        }

        String envEnvironment = System.getenv("SOFTWARE_ORG_ENVIRONMENT");
        if (envEnvironment != null) {
            builder.environment(envEnvironment);
        }

        return builder.build();
    }

    /**
     * Creates a new builder.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public Path getConfigPath() {
        return configPath;
    }

    public int getApiPort() {
        return apiPort;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isEnableMetrics() {
        return enableMetrics;
    }

    public boolean isEnableHealthCheck() {
        return enableHealthCheck;
    }

    public boolean isEnableHotReload() {
        return enableHotReload;
    }

    public Map<String, String> getAdditionalProperties() {
        return new HashMap<>(additionalProperties);
    }

    public String getProperty(String key) {
        return additionalProperties.get(key);
    }

    public String getProperty(String key, String defaultValue) {
        return additionalProperties.getOrDefault(key, defaultValue);
    }

    /**
     * Builder for LauncherConfig.
     */
    public static class Builder {
        private Path configPath = Paths.get(DEFAULT_CONFIG_PATH);
        private int apiPort = DEFAULT_API_PORT;
        private String environment = DEFAULT_ENVIRONMENT;
        private boolean enableMetrics = true;
        private boolean enableHealthCheck = true;
        private boolean enableHotReload = false;
        private Map<String, String> additionalProperties = new HashMap<>();

        public Builder configPath(Path configPath) {
            this.configPath = configPath;
            return this;
        }

        public Builder configPath(String configPath) {
            this.configPath = Paths.get(configPath);
            return this;
        }

        public Builder apiPort(int apiPort) {
            this.apiPort = apiPort;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder enableMetrics(boolean enableMetrics) {
            this.enableMetrics = enableMetrics;
            return this;
        }

        public Builder enableHealthCheck(boolean enableHealthCheck) {
            this.enableHealthCheck = enableHealthCheck;
            return this;
        }

        public Builder enableHotReload(boolean enableHotReload) {
            this.enableHotReload = enableHotReload;
            return this;
        }

        public Builder additionalProperty(String key, String value) {
            this.additionalProperties.put(key, value);
            return this;
        }

        public Builder additionalProperties(Map<String, String> properties) {
            this.additionalProperties.putAll(properties);
            return this;
        }

        public LauncherConfig build() {
            return new LauncherConfig(this);
        }
    }

    @Override
    public String toString() {
        return "LauncherConfig{" +
                "configPath=" + configPath +
                ", apiPort=" + apiPort +
                ", environment='" + environment + '\'' +
                ", enableMetrics=" + enableMetrics +
                ", enableHealthCheck=" + enableHealthCheck +
                ", enableHotReload=" + enableHotReload +
                ", additionalProperties=" + additionalProperties +
                '}';
    }
}
