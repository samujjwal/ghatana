package com.ghatana.kernel.descriptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Contains deployment configuration for kernel components.
 *
 * <p>Deployment configuration includes environment settings, resource allocation,
 * scaling policies, and deployment strategy.</p>
 *
 * @doc.type class
 * @doc.purpose Deployment configuration for kernel component orchestration
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class DeploymentConfiguration {
    
    private final String environment;
    private final String region;
    private final int replicas;
    private final boolean autoScaling;
    private final int minReplicas;
    private final int maxReplicas;
    private final String deploymentStrategy;
    private final Map<String, String> environmentVariables;
    private final Map<String, String> secrets;

    private DeploymentConfiguration(Builder builder) {
        this.environment = builder.environment != null ? builder.environment : "development";
        this.region = builder.region != null ? builder.region : "default";
        this.replicas = builder.replicas;
        this.autoScaling = builder.autoScaling;
        this.minReplicas = builder.minReplicas;
        this.maxReplicas = builder.maxReplicas;
        this.deploymentStrategy = builder.deploymentStrategy != null ? builder.deploymentStrategy : "rolling";
        this.environmentVariables = Collections.unmodifiableMap(new HashMap<>(builder.environmentVariables));
        this.secrets = Collections.unmodifiableMap(new HashMap<>(builder.secrets));
    }

    public static DeploymentConfiguration defaultConfig() {
        return new Builder().build();
    }

    // Getters
    public String getEnvironment() { return environment; }
    public String getRegion() { return region; }
    public int getReplicas() { return replicas; }
    public boolean isAutoScaling() { return autoScaling; }
    public int getMinReplicas() { return minReplicas; }
    public int getMaxReplicas() { return maxReplicas; }
    public String getDeploymentStrategy() { return deploymentStrategy; }
    public Map<String, String> getEnvironmentVariables() { return environmentVariables; }
    public Map<String, String> getSecrets() { return secrets; }

    // Builder
    public static class Builder {
        private String environment = "development";
        private String region = "default";
        private int replicas = 1;
        private boolean autoScaling = false;
        private int minReplicas = 1;
        private int maxReplicas = 1;
        private String deploymentStrategy = "rolling";
        private Map<String, String> environmentVariables = new HashMap<>();
        private Map<String, String> secrets = new HashMap<>();

        public Builder withEnvironment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder withRegion(String region) {
            this.region = region;
            return this;
        }

        public Builder withReplicas(int replicas) {
            this.replicas = replicas;
            return this;
        }

        public Builder withAutoScaling(boolean autoScaling) {
            this.autoScaling = autoScaling;
            return this;
        }

        public Builder withMinReplicas(int minReplicas) {
            this.minReplicas = minReplicas;
            return this;
        }

        public Builder withMaxReplicas(int maxReplicas) {
            this.maxReplicas = maxReplicas;
            return this;
        }

        public Builder withDeploymentStrategy(String strategy) {
            this.deploymentStrategy = strategy;
            return this;
        }

        public Builder withEnvironmentVariable(String key, String value) {
            this.environmentVariables.put(key, value);
            return this;
        }

        public Builder withSecret(String key, String value) {
            this.secrets.put(key, value);
            return this;
        }

        public DeploymentConfiguration build() {
            return new DeploymentConfiguration(this);
        }
    }
}
