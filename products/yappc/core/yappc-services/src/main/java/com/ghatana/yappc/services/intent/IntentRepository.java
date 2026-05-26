package com.ghatana.yappc.services.intent;

import com.ghatana.yappc.domain.intent.IntentSpec;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Durable repository contract for versioned YAPPC intent state
 * @doc.layer service
 * @doc.pattern Repository
 */
public interface IntentRepository {

    /**
     * Persists a new version for an intent.
     *
     * @param spec captured intent specification
     * @param context tenant/workspace/project persistence context
     * @return persisted version record
     */
    Promise<IntentVersionRecord> saveVersion(
            @NotNull IntentSpec spec,
            @NotNull IntentPersistenceContext context);

    /**
     * Finds the latest version for an intent in a project.
     *
     * @param tenantId tenant identifier
     * @param workspaceId workspace identifier
     * @param projectId project identifier
     * @param intentId canonical intent identifier
     * @return latest version record if one exists
     */
    Promise<Optional<IntentVersionRecord>> findLatest(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String intentId);

    /**
     * Lists version history for an intent in a project.
     *
     * @param tenantId tenant identifier
     * @param workspaceId workspace identifier
     * @param projectId project identifier
     * @param intentId canonical intent identifier
     * @return version history ordered newest first
     */
    Promise<List<IntentVersionRecord>> history(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String intentId);

    /**
     * Counts persisted intent version entities for a tenant.
     *
     * @param tenantId tenant identifier
     * @return entity count
     */
    Promise<Long> count(@NotNull String tenantId);
}
