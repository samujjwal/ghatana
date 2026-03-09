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

package com.ghatana.yappc.api;

import com.ghatana.yappc.api.model.*;
import com.ghatana.yappc.api.service.DependencyService;
import com.ghatana.yappc.api.service.PackService;
import com.ghatana.yappc.api.service.ProjectService;
import com.ghatana.yappc.api.service.TemplateService;

import java.nio.file.Path;
import java.util.Map;

/**
 * Main entry point for YAPPC programmatic access.
 * Provides a unified API for pack management, project scaffolding,
 * feature additions, and dependency analysis.
 *
 * <p>Usage example:
 * <pre>{@code
 * YappcApi yappc = YappcApi.create();
 * 
 * // Create a new project
 * CreateResult result = yappc.projects().create(
 *     CreateRequest.builder()
 *         .projectName("my-service")
 *         .packName("java-service-spring-gradle")
 *         .variable("packageName", "com.example")
 *         .build()
 * );
 * 
 * // Add a feature
 * yappc.projects().addFeature(
 *     AddFeatureRequest.builder()
 *         .projectPath(result.projectPath())
 *         .feature("database")
 *         .type("postgresql")
 *         .build()
 * );
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Main entry point for YAPPC programmatic API
 * @doc.layer platform
 * @doc.pattern Facade
 */
public interface YappcApi {

    /**
     * Creates a new YappcApi instance with default configuration.
     *
     * @return A new YappcApi instance
     */
    static YappcApi create() {
        return create(YappcConfig.defaults());
    }

    /**
     * Creates a new YappcApi instance with custom configuration.
     *
     * @param config The configuration to use
     * @return A new YappcApi instance
     */
    static YappcApi create(YappcConfig config) {
        return new DefaultYappcApi(config);
    }

    /**
     * Creates a builder for fluent API configuration.
     *
     * @return A new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    // === Service Access ===

    /**
     * Get the pack service for pack-related operations.
     *
     * @return The pack service
     */
    PackService packs();

    /**
     * Get the project service for project-related operations.
     *
     * @return The project service
     */
    ProjectService projects();

    /**
     * Get the template service for template rendering.
     *
     * @return The template service
     */
    TemplateService templates();

    /**
     * Get the dependency service for dependency analysis.
     *
     * @return The dependency service
     */
    DependencyService dependencies();

    // === Convenience Methods ===

    /**
     * Quick method to create a project with minimal configuration.
     *
     * @param projectName The project name
     * @param packName The pack to use
     * @return The creation result
     */
    default CreateResult createProject(String projectName, String packName) {
        return projects().create(
                CreateRequest.builder()
                        .projectName(projectName)
                        .packName(packName)
                        .build()
        );
    }

    /**
     * Quick method to create a project with variables.
     *
     * @param projectName The project name
     * @param packName The pack to use
     * @param variables Template variables
     * @return The creation result
     */
    default CreateResult createProject(String projectName, String packName, Map<String, Object> variables) {
        return projects().create(
                CreateRequest.builder()
                        .projectName(projectName)
                        .packName(packName)
                        .variables(variables)
                        .build()
        );
    }

    /**
     * Quick method to add a feature to a project.
     *
     * @param projectPath Path to the project
     * @param feature Feature name (database, auth, observability)
     * @param type Feature type/variant
     * @return The add result
     */
    default AddResult addFeature(Path projectPath, String feature, String type) {
        return projects().addFeature(
                AddFeatureRequest.builder()
                        .projectPath(projectPath)
                        .feature(feature)
                        .type(type)
                        .build()
        );
    }

    /**
     * Get the current API version.
     *
     * @return The API version string
     */
    String getVersion();

    /**
     * Check if the API is properly initialized.
     *
     * @return true if ready to use
     */
    boolean isReady();

    /**
     * Shutdown and cleanup resources.
     */
    void shutdown();

    /**
     * Builder for creating YappcApi instances with custom configuration.
     */
    class Builder {
        private Path packsPath;
        private Path workspacePath;
        private boolean enableCache = true;
        private boolean enableTelemetry = false;

        public Builder packsPath(Path path) {
            this.packsPath = path;
            return this;
        }

        public Builder workspacePath(Path path) {
            this.workspacePath = path;
            return this;
        }

        public Builder enableCache(boolean enable) {
            this.enableCache = enable;
            return this;
        }

        public Builder enableTelemetry(boolean enable) {
            this.enableTelemetry = enable;
            return this;
        }

        public YappcApi build() {
            YappcConfig config = YappcConfig.builder()
                    .packsPath(packsPath)
                    .workspacePath(workspacePath)
                    .enableCache(enableCache)
                    .enableTelemetry(enableTelemetry)
                    .build();
            return YappcApi.create(config);
        }
    }
}
