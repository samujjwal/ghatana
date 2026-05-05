package com.ghatana.yappc.domain.pageartifact;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Transactional mutation capability for page artifact repositories.
 *
 * <p>Implementations persist the page artifact mutation and the audit event
 * in a single database transaction to avoid partial-write states.
 *
 * @doc.type interface
 * @doc.purpose Atomic page artifact mutation and audit capability
 * @doc.layer product
 * @doc.pattern Port
 */
public interface PageArtifactAtomicMutationRepository {

    Promise<PageArtifactDocument> saveWithAudit(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull PageArtifactDocument document,
            @NotNull String action,
            @NotNull String actor,
            @NotNull String summary
    );
}
