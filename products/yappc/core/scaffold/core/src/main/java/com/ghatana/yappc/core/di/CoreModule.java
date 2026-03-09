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

package com.ghatana.yappc.core.di;

import com.ghatana.yappc.core.build.BuildGenerator;
import com.ghatana.yappc.core.cache.LocalCacheManager;
import com.ghatana.yappc.core.telemetry.UnifiedTelemetryProvider;
import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import com.ghatana.yappc.core.template.TemplateEngine;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

/**
 * Core dependency injection module providing unified access to all core services.
 *
 * <p>This module consolidates all core services with proper lifecycle management and
 * configuration-driven instantiation. Replaces manual wiring throughout the application.
 *
 * <p>Week 10 Day 50: Phase 4 architectural improvements - Dependency Injection Strategy
 *
 * @doc.type class
 * @doc.purpose Core dependency injection module providing unified access to all core services.
 * @doc.layer platform
 * @doc.pattern Module
 */
public class CoreModule extends AbstractModule {

    @Provides
    public UnifiedTelemetryProvider telemetryProvider() {
        return (UnifiedTelemetryProvider) UnifiedTelemetryProvider.createSimple("yappc");
    }

    @Provides
    public com.ghatana.yappc.core.security.SecurityReviewFramework securityReviewFramework() {
        // Default constructor - will be initialized with project path when used
        return new com.ghatana.yappc.core.security.SecurityReviewFramework(
                java.nio.file.Paths.get("."));
    }

    @Provides
    public com.ghatana.yappc.core.services.ProjectAnalysisService projectAnalysisService() {
        return new com.ghatana.yappc.core.services.ProjectAnalysisService();
    }

    @Provides
    public com.ghatana.yappc.core.snapshots.CISnapshotManager ciSnapshotManager() {
        return new com.ghatana.yappc.core.snapshots.CISnapshotManager();
    }

    @Provides
    public LocalCacheManager cacheManager() {
        return new LocalCacheManager();
    }

    @Provides
    TemplateEngine templateEngine() {
        return new SimpleTemplateEngine();
    }

    @Provides
    BuildGenerator buildGenerator(
            UnifiedTelemetryProvider telemetryProvider, TemplateEngine templateEngine) {
        return BuildGenerator.defaultProvider()
                .withTelemetryEnabled(true)
                .withTemplateEngine(templateEngine)
                .build();
    }

    @Provides
    public com.ghatana.yappc.core.config.SimpleUnifiedConfigurationManager configurationManager() {
        return new com.ghatana.yappc.core.config.SimpleUnifiedConfigurationManager();
    }

    @Provides
    public com.ghatana.yappc.core.composition.CompositionEngine compositionEngine(
            com.ghatana.yappc.core.pack.PackEngine packEngine,
            TemplateEngine templateEngine) {
        return new com.ghatana.yappc.core.composition.CompositionEngine(packEngine, templateEngine);
    }

    @Provides
    public com.ghatana.yappc.core.pack.PackEngine packEngine(TemplateEngine templateEngine) {
        return new com.ghatana.yappc.core.pack.DefaultPackEngine(templateEngine);
    }

    @Provides
    public com.ghatana.yappc.core.integration.IntegrationTemplateEngine integrationTemplateEngine(
            TemplateEngine templateEngine) {
        java.nio.file.Path integrationTemplatesPath = 
            java.nio.file.Paths.get("templates", "integrations");
        return new com.ghatana.yappc.core.integration.IntegrationTemplateEngine(
            templateEngine, integrationTemplatesPath);
    }

    @Provides
    public com.ghatana.yappc.core.validation.SchemaValidationService schemaValidationService() {
        return new com.ghatana.yappc.core.validation.SchemaValidationService();
    }

    @Provides
    public com.ghatana.yappc.core.template.TemplateInheritanceResolver templateInheritanceResolver() {
        return new com.ghatana.yappc.core.template.TemplateInheritanceResolver();
    }

    @Provides
    public com.ghatana.yappc.core.language.LanguageRegistry languageRegistry() {
        return new com.ghatana.yappc.core.language.LanguageRegistry();
    }
}
