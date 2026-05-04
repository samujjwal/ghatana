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

package com.ghatana.yappc.domain.pageartifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.domain.pageartifact.http.PageArtifactController;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.concurrent.Executor;

/**
 * ActiveJ DI module for Page Artifact subsystem.
 * <p>
 * Provides bindings for durable page artifact persistence with tenant/workspace/project scoping,
 * optimistic concurrency control, and historical version tracking.
 *
 * @doc.type class
 * @doc.purpose DI module for page artifact repository and controller
 * @doc.layer product
 * @doc.pattern Module
 */
public class PageArtifactModule extends AbstractModule {

    private static final Logger LOG = LoggerFactory.getLogger(PageArtifactModule.class);

    @Override
    protected void configure() {
        LOG.info("Configuring Page Artifact Module DI bindings");
    }

    /**
     * Provides the database-backed PageArtifactRepository.
     * <p>
     * Uses JDBC for durable persistence with tenant/workspace/project scoping,
     * optimistic concurrency control via documentId, and historical version tracking.
     *
     * @param dataSource JDBC data source
     * @param objectMapper JSON object mapper for JSONB serialization
     * @return DbPageArtifactRepository implementation
     */
    @Provides
    PageArtifactRepository pageArtifactRepository(
            DataSource dataSource,
            ObjectMapper objectMapper,
            Executor executor
    ) {
        LOG.info("Creating DbPageArtifactRepository");
        return new DbPageArtifactRepository(dataSource, objectMapper, executor);
    }

    /**
     * Provides the PageArtifactController.
     * <p>
     * HTTP controller for page artifact operations with tenant/workspace/project scoping,
     * optimistic concurrency via If-Match header, authorization checks, and observability.
     *
     * @param repository Page artifact repository
     * @param objectMapper JSON object mapper
     * @param authorizationService Authorization service for permission checks
     * @param metrics Metrics collector for observability
     * @return PageArtifactController instance
     */
    @Provides
    PageArtifactController pageArtifactController(
            PageArtifactRepository repository,
            ObjectMapper objectMapper,
            com.ghatana.platform.security.rbac.SyncAuthorizationService authorizationService,
            com.ghatana.platform.observability.MetricsCollector metrics
    ) {
        LOG.info("Creating PageArtifactController with authorization and observability");
        return new PageArtifactController(repository, objectMapper, authorizationService, metrics);
    }
}
