package com.ghatana.yappc.domain.pageartifact;

import com.ghatana.core.database.jdbc.JdbcTemplate;
import com.ghatana.platform.observability.util.BlockingExecutors;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.concurrent.Executor;

/**
 * @doc.type class
 * @doc.purpose JDBC-backed page artifact audit repository implementation
 * @doc.layer product
 * @doc.pattern Repository Implementation
 */
public final class DbPageArtifactAuditRepository implements PageArtifactAuditRepository {

    private static final String INSERT_AUDIT_EVENT = """
            INSERT INTO page_artifact_audit_events (
                action,
                tenant_id,
                workspace_id,
                project_id,
                artifact_id,
                actor,
                summary,
                occurred_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final Executor blockingExecutor;

    public DbPageArtifactAuditRepository(@NotNull DataSource dataSource) {
        this(dataSource, BlockingExecutors.blockingExecutor());
    }

    public DbPageArtifactAuditRepository(
            @NotNull DataSource dataSource,
            @NotNull Executor blockingExecutor
    ) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.blockingExecutor = blockingExecutor;
    }

    @Override
    public Promise<Void> record(
            @NotNull String action,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String artifactId,
            @NotNull String actor,
            @NotNull String summary
    ) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            jdbc.update(
                    INSERT_AUDIT_EVENT,
                    action,
                    tenantId,
                    workspaceId,
                    projectId,
                    artifactId,
                    actor,
                    summary,
                    Instant.now()
            );
            return null;
        });
    }
}
