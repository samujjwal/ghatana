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

package com.ghatana.yappc.api.grpc;

import com.ghatana.yappc.core.plugin.*;
import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServiceDescriptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * gRPC service for plugin management.
 *
 * @doc.type class
 * @doc.purpose gRPC service for plugin operations
 * @doc.layer presentation
 * @doc.pattern Service
 */
public class PluginManagementService implements BindableService {

    @Override
    public ServerServiceDefinition bindService() {
        return ServerServiceDefinition.builder(
                ServiceDescriptor.newBuilder("ghatana.yappc.PluginManagementService").build())
                .build();
    }

    private final PluginManager pluginManager;

    public PluginManagementService(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    /**
     * List all plugins.
     */
    public void listPlugins(ListPluginsRequest request, StreamObserver<ListPluginsResponse> responseObserver) {
        try {
            List<YappcPlugin> plugins;

            if (request.hasCapability()) {
                PluginCapability capability = PluginCapability.valueOf(request.getCapability().toUpperCase());
                plugins = pluginManager.getRegistry().getPluginsByCapability(capability);
            } else if (request.hasLanguage()) {
                plugins = pluginManager.getRegistry().getPluginsByLanguage(request.getLanguage());
            } else if (request.hasBuildSystem()) {
                plugins = pluginManager.getRegistry().getPluginsByBuildSystem(request.getBuildSystem());
            } else {
                plugins = pluginManager.getRegistry().getAllPlugins();
            }

            List<PluginInfo> pluginInfos = plugins.stream()
                    .map(this::toPluginInfo)
                    .collect(Collectors.toList());

            ListPluginsResponse response = ListPluginsResponse.newBuilder()
                    .addAllPlugins(pluginInfos)
                    .setCount(pluginInfos.size())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Get plugin details.
     */
    public void getPlugin(GetPluginRequest request, StreamObserver<PluginDetail> responseObserver) {
        try {
            YappcPlugin plugin = pluginManager.getRegistry()
                    .getPlugin(request.getPluginId())
                    .orElse(null);

            if (plugin == null) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Plugin not found: " + request.getPluginId())
                        .asRuntimeException());
                return;
            }

            PluginDetail detail = toPluginDetail(plugin);
            responseObserver.onNext(detail);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Load a plugin.
     */
    public void loadPlugin(LoadPluginRequest request, StreamObserver<LoadPluginResponse> responseObserver) {
        try {
            Path jar = Paths.get(request.getJarPath());
            Path workspace = Paths.get(System.getProperty("user.dir"));
            Path packs = workspace.resolve("packs");

            PluginContext context = new PluginContext(
                    workspace,
                    packs,
                    request.getConfigMap(),
                    pluginManager.getEventBus(),
                    PluginSandbox.permissive(workspace));

            YappcPlugin plugin = pluginManager.loadAndInitialize(jar, context);

            LoadPluginResponse response = LoadPluginResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Plugin loaded successfully")
                    .setPlugin(toPluginInfo(plugin))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (PluginException e) {
            LoadPluginResponse response = LoadPluginResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to load plugin: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Unload a plugin.
     */
    public void unloadPlugin(UnloadPluginRequest request, StreamObserver<UnloadPluginResponse> responseObserver) {
        try {
            String pluginId = request.getPluginId();

            if (!pluginManager.getRegistry().isRegistered(pluginId)) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Plugin not found: " + pluginId)
                        .asRuntimeException());
                return;
            }

            pluginManager.shutdown(pluginId);

            UnloadPluginResponse response = UnloadPluginResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Plugin unloaded successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (PluginException e) {
            UnloadPluginResponse response = UnloadPluginResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to unload plugin: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Stream health checks for all plugins.
     */
    public void streamHealthChecks(StreamHealthRequest request, StreamObserver<HealthCheckResult> responseObserver) {
        try {
            Map<String, PluginHealthResult> results = pluginManager.healthCheckAll();

            for (Map.Entry<String, PluginHealthResult> entry : results.entrySet()) {
                HealthCheckResult result = HealthCheckResult.newBuilder()
                        .setPluginId(entry.getKey())
                        .setHealthy(entry.getValue().healthy())
                        .setMessage(entry.getValue().message())
                        .addAllDetails(entry.getValue().details())
                        .build();

                responseObserver.onNext(result);

                if (request.getIntervalSeconds() > 0) {
                    Thread.sleep(request.getIntervalSeconds() * 1000L);
                }
            }

            responseObserver.onCompleted();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.CANCELLED
                    .withDescription("Stream cancelled")
                    .asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    private PluginInfo toPluginInfo(YappcPlugin plugin) {
        PluginMetadata metadata = plugin.getMetadata();
        PluginState state = pluginManager.getPluginState(metadata.id());

        return PluginInfo.newBuilder()
                .setId(metadata.id())
                .setName(metadata.name())
                .setVersion(metadata.version())
                .setState(state.toString())
                .setStability(metadata.stability().toString())
                .addAllCapabilities(metadata.capabilities().stream()
                        .map(Enum::toString)
                        .collect(Collectors.toList()))
                .build();
    }

    private PluginDetail toPluginDetail(YappcPlugin plugin) {
        PluginMetadata metadata = plugin.getMetadata();
        PluginState state = pluginManager.getPluginState(metadata.id());

        return PluginDetail.newBuilder()
                .setId(metadata.id())
                .setName(metadata.name())
                .setVersion(metadata.version())
                .setDescription(metadata.description() != null ? metadata.description() : "")
                .setAuthor(metadata.author() != null ? metadata.author() : "")
                .setState(state.toString())
                .setStability(metadata.stability().toString())
                .addAllCapabilities(metadata.capabilities().stream()
                        .map(Enum::toString)
                        .collect(Collectors.toList()))
                .addAllSupportedLanguages(metadata.supportedLanguages())
                .addAllSupportedBuildSystems(metadata.supportedBuildSystems())
                .putAllRequiredConfig(metadata.requiredConfig())
                .putAllOptionalConfig(metadata.optionalConfig())
                .addAllDependencies(metadata.dependencies())
                .build();
    }

    // Proto message classes (these would normally be generated from .proto files)
    // Included here as placeholder classes for demonstration

    static class ListPluginsRequest {
        private String capability;
        private String language;
        private String buildSystem;

        public boolean hasCapability() {
            return capability != null;
        }

        public String getCapability() {
            return capability;
        }

        public boolean hasLanguage() {
            return language != null;
        }

        public String getLanguage() {
            return language;
        }

        public boolean hasBuildSystem() {
            return buildSystem != null;
        }

        public String getBuildSystem() {
            return buildSystem;
        }
    }

    static class ListPluginsResponse {
        public static Builder newBuilder() {
            return new Builder();
        }

        static class Builder {
            public Builder addAllPlugins(List<PluginInfo> plugins) {
                return this;
            }

            public Builder setCount(int count) {
                return this;
            }

            public ListPluginsResponse build() {
                return new ListPluginsResponse();
            }
        }
    }

    static class GetPluginRequest {
        public String getPluginId() {
            return "";
        }
    }

    static class LoadPluginRequest {
        private String jarPath;
        private Map<String, String> configMap = Map.of();

        public void setJarPath(String jarPath) {
            this.jarPath = jarPath;
        }

        public String getJarPath() {
            return jarPath;
        }

        public Map<String, String> getConfigMap() {
            return configMap;
        }
    }

    static class LoadPluginResponse {
        public static Builder newBuilder() {
            return new Builder();
        }

        static class Builder {
            public Builder setSuccess(boolean success) {
                return this;
            }

            public Builder setMessage(String message) {
                return this;
            }

            public Builder setPlugin(PluginInfo info) {
                return this;
            }

            public LoadPluginResponse build() {
                return new LoadPluginResponse();
            }
        }
    }

    static class UnloadPluginRequest {
        private String pluginId;

        public void setPluginId(String pluginId) {
            this.pluginId = pluginId;
        }

        public String getPluginId() {
            return pluginId;
        }
    }

    static class UnloadPluginResponse {
        public static Builder newBuilder() {
            return new Builder();
        }

        static class Builder {
            public Builder setSuccess(boolean success) {
                return this;
            }

            public Builder setMessage(String message) {
                return this;
            }

            public UnloadPluginResponse build() {
                return new UnloadPluginResponse();
            }
        }
    }

    static class StreamHealthRequest {
        public int getIntervalSeconds() {
            return 0;
        }
    }

    static class HealthCheckResult {
        public static Builder newBuilder() {
            return new Builder();
        }

        static class Builder {
            public Builder setPluginId(String id) {
                return this;
            }

            public Builder setHealthy(boolean healthy) {
                return this;
            }

            public Builder setMessage(String message) {
                return this;
            }

            public Builder addAllDetails(List<String> details) {
                return this;
            }

            public HealthCheckResult build() {
                return new HealthCheckResult();
            }
        }
    }

    static class PluginInfo {
        public static Builder newBuilder() {
            return new Builder();
        }

        static class Builder {
            public Builder setId(String id) {
                return this;
            }

            public Builder setName(String name) {
                return this;
            }

            public Builder setVersion(String version) {
                return this;
            }

            public Builder setState(String state) {
                return this;
            }

            public Builder setStability(String stability) {
                return this;
            }

            public Builder addAllCapabilities(List<String> capabilities) {
                return this;
            }

            public PluginInfo build() {
                return new PluginInfo();
            }
        }
    }

    static class PluginDetail {
        public static Builder newBuilder() {
            return new Builder();
        }

        static class Builder {
            public Builder setId(String id) {
                return this;
            }

            public Builder setName(String name) {
                return this;
            }

            public Builder setVersion(String version) {
                return this;
            }

            public Builder setDescription(String description) {
                return this;
            }

            public Builder setAuthor(String author) {
                return this;
            }

            public Builder setState(String state) {
                return this;
            }

            public Builder setStability(String stability) {
                return this;
            }

            public Builder addAllCapabilities(List<String> capabilities) {
                return this;
            }

            public Builder addAllSupportedLanguages(List<String> languages) {
                return this;
            }

            public Builder addAllSupportedBuildSystems(List<String> buildSystems) {
                return this;
            }

            public Builder putAllRequiredConfig(Map<String, String> config) {
                return this;
            }

            public Builder putAllOptionalConfig(Map<String, String> config) {
                return this;
            }

            public Builder addAllDependencies(List<String> dependencies) {
                return this;
            }

            public PluginDetail build() {
                return new PluginDetail();
            }
        }
    }
}
