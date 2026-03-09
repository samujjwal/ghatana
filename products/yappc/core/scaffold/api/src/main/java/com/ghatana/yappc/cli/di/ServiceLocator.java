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

package com.ghatana.yappc.cli.di;

import com.ghatana.yappc.core.build.BuildGenerator;
import com.ghatana.yappc.core.cache.LocalCacheManager;
import com.ghatana.yappc.core.di.CoreModule;
import com.ghatana.yappc.core.telemetry.UnifiedTelemetryProvider;
import com.ghatana.yappc.core.template.TemplateEngine;
import io.activej.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service locator for CLI components that provides access to dependency-injected services without
 * requiring constructor modifications for existing CLI commands.
 *
 * <p>Provides a bridge between the new DI system and existing manual wiring, allowing gradual
 * migration to full dependency injection while eliminating manual service creation.
 *
 * <p>Week 10 Day 50: Phase 4 architectural improvements - Dependency Injection Strategy
 *
 * @doc.type class
 * @doc.purpose Service locator for CLI components that provides access to dependency-injected services without
 * @doc.layer platform
 * @doc.pattern Component
 */
public class ServiceLocator {

    private static final Logger log = LoggerFactory.getLogger(ServiceLocator.class);

    private static volatile ServiceLocator instance;
    private final Injector injector;

    private ServiceLocator() {
        try {
            this.injector = Injector.of(new CoreModule());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize service locator", e);
        }
    }

    public static ServiceLocator getInstance() {
        if (instance == null) {
            synchronized (ServiceLocator.class) {
                if (instance == null) {
                    instance = new ServiceLocator();
                }
            }
        }
        return instance;
    }

    // Core service accessors
    public com.ghatana.yappc.core.config.SimpleUnifiedConfigurationManager
            getConfigurationManager() {
        return injector.getInstance(
                com.ghatana.yappc.core.config.SimpleUnifiedConfigurationManager.class);
    }

    public UnifiedTelemetryProvider getTelemetryProvider() {
        return injector.getInstance(UnifiedTelemetryProvider.class);
    }

    public LocalCacheManager getCacheManager() {
        return injector.getInstance(LocalCacheManager.class);
    }

    public TemplateEngine getTemplateEngine() {
        return injector.getInstance(TemplateEngine.class);
    }

    public BuildGenerator getBuildGenerator() {
        return injector.getInstance(BuildGenerator.class);
    }

    public com.ghatana.yappc.core.security.SecurityReviewFramework getSecurityReviewFramework() {
        return injector.getInstance(com.ghatana.yappc.core.security.SecurityReviewFramework.class);
    }

    // Factory method for SecurityReviewFramework with specific project path
    public com.ghatana.yappc.core.security.SecurityReviewFramework createSecurityReviewFramework(
            java.nio.file.Path projectPath) {
        return new com.ghatana.yappc.core.security.SecurityReviewFramework(projectPath);
    }

    public com.ghatana.yappc.core.services.ProjectAnalysisService getProjectAnalysisService() {
        return injector.getInstance(com.ghatana.yappc.core.services.ProjectAnalysisService.class);
    }

    public com.ghatana.yappc.core.snapshots.CISnapshotManager getCISnapshotManager() {
        return injector.getInstance(com.ghatana.yappc.core.snapshots.CISnapshotManager.class);
    }

    // Correlation ID management for error tracking
    public String getCurrentCorrelationId() {
        try {
            return getConfigurationManager().getSecurityConfig().enableCorrelationIds
                    ? java.util.UUID.randomUUID().toString()
                    : "disabled";
        } catch (Exception e) {
            return "error-" + System.currentTimeMillis();
        }
    }

    // Graceful shutdown
    public void shutdown() {
        try {
            if (injector != null) {
                getConfigurationManager().close();
            }
        } catch (Exception e) {
            log.error("Warning: Error during service locator shutdown: {}", e.getMessage());
        }
    }

    // For testing - allows injector replacement
    static void setInstance(ServiceLocator testInstance) {
        instance = testInstance;
    }

    // For testing - reset to force re-initialization
    static void resetForTesting() {
        instance = null;
    }
}
