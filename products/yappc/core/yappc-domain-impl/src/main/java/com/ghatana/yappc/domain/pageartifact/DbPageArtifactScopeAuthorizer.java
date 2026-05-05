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

import com.ghatana.core.database.jdbc.JdbcTemplate;
import com.ghatana.platform.observability.util.BlockingExecutors;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.concurrent.Executor;

/**
 * Database-backed resource scope authorizer for page artifacts.
 *
 * <p>Verifies that the requested workspace belongs to the given tenant and that the
 * requested project belongs to the given workspace and tenant. This prevents cross-tenant
 * and cross-workspace artifact access by a principal that holds a generic permission
 * but has no ownership relationship to the target resource.
 *
 * @doc.type class
 * @doc.purpose Resource-scoped authorization for page artifact workspace/project ownership
 * @doc.layer product
 * @doc.pattern Port Implementation
 */
public final class DbPageArtifactScopeAuthorizer implements PageArtifactResourceScopeAuthorizer {

    private static final Logger LOG = LoggerFactory.getLogger(DbPageArtifactScopeAuthorizer.class);

    private static final String WORKSPACE_EXISTS_IN_TENANT = """
            SELECT 1 FROM workspaces
            WHERE id = ? AND tenant_id = ? AND status != 'DELETED'
            LIMIT 1
            """;

    private static final String PROJECT_EXISTS_IN_WORKSPACE = """
            SELECT 1 FROM projects
            WHERE id = ? AND workspace_id = ? AND tenant_id = ? AND status != 'DELETED'
            LIMIT 1
            """;

    private final JdbcTemplate jdbc;
    private final Executor blockingExecutor;

    /**
     * Creates a new DbPageArtifactScopeAuthorizer.
     *
     * @param dataSource JDBC data source
     */
    public DbPageArtifactScopeAuthorizer(@NotNull DataSource dataSource) {
        this(dataSource, BlockingExecutors.blockingExecutor());
    }

    /**
     * Creates a new DbPageArtifactScopeAuthorizer with a custom executor.
     *
     * @param dataSource       JDBC data source
     * @param blockingExecutor executor for blocking DB calls
     */
    public DbPageArtifactScopeAuthorizer(
            @NotNull DataSource dataSource,
            @NotNull Executor blockingExecutor
    ) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.blockingExecutor = blockingExecutor;
    }

    @Override
    public Promise<Void> authorize(
            @Nullable String userId,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String artifactId,
            @NotNull String requiredPermission
    ) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            if (!workspaceBelongsToTenant(workspaceId, tenantId)) {
                LOG.warn(
                        "Resource scope violation: workspace {} does not belong to tenant {}. user={}, artifact={}",
                        workspaceId, tenantId, userId, artifactId
                );
                throw new AccessDeniedException(
                        "Workspace " + workspaceId + " does not belong to the authenticated tenant"
                );
            }

            if (!projectBelongsToWorkspace(projectId, workspaceId, tenantId)) {
                LOG.warn(
                        "Resource scope violation: project {} does not belong to workspace {} in tenant {}. user={}, artifact={}",
                        projectId, workspaceId, tenantId, userId, artifactId
                );
                throw new AccessDeniedException(
                        "Project " + projectId + " does not belong to workspace " + workspaceId
                );
            }

            return null;
        });
    }

    private boolean workspaceBelongsToTenant(String workspaceId, String tenantId) {
        return jdbc.queryForObject(
                WORKSPACE_EXISTS_IN_TENANT,
                rs -> true,
                workspaceId,
                tenantId
        ).isPresent();
    }

    private boolean projectBelongsToWorkspace(String projectId, String workspaceId, String tenantId) {
        return jdbc.queryForObject(
                PROJECT_EXISTS_IN_WORKSPACE,
                rs -> true,
                projectId,
                workspaceId,
                tenantId
        ).isPresent();
    }
}
