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

import com.ghatana.yappc.api.service.DependencyService;
import com.ghatana.yappc.api.service.PackService;
import com.ghatana.yappc.api.service.ProjectService;
import com.ghatana.yappc.api.service.TemplateService;
import com.ghatana.yappc.api.service.impl.DefaultDependencyService;
import com.ghatana.yappc.api.service.impl.DefaultPackService;
import com.ghatana.yappc.api.service.impl.DefaultProjectService;
import com.ghatana.yappc.api.service.impl.DefaultTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of the YAPPC API.
 * Provides access to all scaffold operations through a unified interface.
 *
 * @doc.type class
 * @doc.purpose Default YappcApi implementation
 * @doc.layer platform
 * @doc.pattern Facade
 */
public final class DefaultYappcApi implements YappcApi {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultYappcApi.class);
    private static final String VERSION = "1.0.0";

    private final YappcConfig config;
    private final PackService packService;
    private final ProjectService projectService;
    private final TemplateService templateService;
    private final DependencyService dependencyService;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * Creates a new DefaultYappcApi with the given configuration.
     *
     * @param config The configuration to use
     */
    public DefaultYappcApi(YappcConfig config) {
        this.config = config;
        
        // Initialize services
        this.templateService = new DefaultTemplateService(config);
        this.packService = new DefaultPackService(config);
        this.projectService = new DefaultProjectService(config, packService, templateService);
        this.dependencyService = new DefaultDependencyService(config, packService);
        
        initialize();
    }

    private void initialize() {
        if (initialized.compareAndSet(false, true)) {
            LOG.info("Initializing YAPPC API v{}", VERSION);
            LOG.debug("Configuration: {}", config);
            
            // Pre-load packs if caching is enabled
            if (config.isCacheEnabled()) {
                try {
                    packService.refresh();
                    LOG.info("Pack cache initialized with {} packs", packService.list().size());
                } catch (Exception e) {
                    LOG.warn("Failed to initialize pack cache: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public PackService packs() {
        ensureReady();
        return packService;
    }

    @Override
    public ProjectService projects() {
        ensureReady();
        return projectService;
    }

    @Override
    public TemplateService templates() {
        ensureReady();
        return templateService;
    }

    @Override
    public DependencyService dependencies() {
        ensureReady();
        return dependencyService;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public boolean isReady() {
        return initialized.get() && !shutdown.get();
    }

    @Override
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            LOG.info("Shutting down YAPPC API");
            // Cleanup resources if needed
        }
    }

    private void ensureReady() {
        if (!initialized.get()) {
            throw new IllegalStateException("YAPPC API not initialized");
        }
        if (shutdown.get()) {
            throw new IllegalStateException("YAPPC API has been shut down");
        }
    }

    /**
     * Get the current configuration.
     *
     * @return The configuration
     */
    public YappcConfig getConfig() {
        return config;
    }
}
