/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.plugin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Configuration for the plugin system.
 *
 * @doc.type record
 * @doc.purpose Plugin system configuration
 * @doc.layer platform
 * @doc.pattern Configuration
 */
public record PluginConfig(
        @JsonProperty("enabled") List<String> enabled,
        @JsonProperty("disabled") List<String> disabled,
        @JsonProperty("paths") List<Path> paths,
        @JsonProperty("registry") String registry,
        @JsonProperty("sandbox") SandboxConfig sandbox,
        @JsonProperty("autoLoad") boolean autoLoad,
        @JsonProperty("healthCheckInterval") Duration healthCheckInterval) {

    @JsonCreator
    public PluginConfig {
    }

    /**
     * Sandbox configuration.
     */
    public record SandboxConfig(
            @JsonProperty("allowedWritePaths") List<Path> allowedWritePaths,
            @JsonProperty("deniedWritePaths") List<Path> deniedWritePaths,
            @JsonProperty("timeout") Duration timeout,
            @JsonProperty("dryRunOnly") boolean dryRunOnly,
            @JsonProperty("allowNetworkAccess") boolean allowNetworkAccess) {

        @JsonCreator
        public SandboxConfig {
        }

        public static SandboxConfig defaults(Path workspacePath) {
            return new SandboxConfig(
                    List.of(workspacePath),
                    List.of(),
                    Duration.ofMinutes(5),
                    false,
                    true);
        }

        public static SandboxConfig restrictive(List<Path> allowedPaths) {
            return new SandboxConfig(
                    allowedPaths,
                    List.of(Path.of("/etc"), Path.of("/sys"), Path.of("/proc")),
                    Duration.ofMinutes(1),
                    false,
                    false);
        }

        public static SandboxConfig dryRun() {
            return new SandboxConfig(
                    List.of(),
                    List.of(),
                    Duration.ofSeconds(30),
                    true,
                    false);
        }

        public PluginSandbox toPluginSandbox() {
            return new PluginSandbox(
                    allowedWritePaths,
                    deniedWritePaths,
                    timeout,
                    dryRunOnly,
                    allowNetworkAccess);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> enabled = List.of();
        private List<String> disabled = List.of();
        private List<Path> paths = List.of();
        private String registry = null;
        private SandboxConfig sandbox = null;
        private boolean autoLoad = true;
        private Duration healthCheckInterval = Duration.ofMinutes(5);

        public Builder enabled(List<String> enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder disabled(List<String> disabled) {
            this.disabled = disabled;
            return this;
        }

        public Builder paths(List<Path> paths) {
            this.paths = paths;
            return this;
        }

        public Builder registry(String registry) {
            this.registry = registry;
            return this;
        }

        public Builder sandbox(SandboxConfig sandbox) {
            this.sandbox = sandbox;
            return this;
        }

        public Builder autoLoad(boolean autoLoad) {
            this.autoLoad = autoLoad;
            return this;
        }

        public Builder healthCheckInterval(Duration healthCheckInterval) {
            this.healthCheckInterval = healthCheckInterval;
            return this;
        }

        public PluginConfig build() {
            return new PluginConfig(
                    enabled,
                    disabled,
                    paths,
                    registry,
                    sandbox,
                    autoLoad,
                    healthCheckInterval);
        }
    }

    /**
     * Creates default plugin configuration.
     *
     * @param workspacePath workspace path
     * @return default configuration
     */
    public static PluginConfig defaults(Path workspacePath) {
        return builder()
                .sandbox(SandboxConfig.defaults(workspacePath))
                .build();
    }
}
