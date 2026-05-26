package com.ghatana.yappc.services.shape;

import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.shape.SystemModel;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * @doc.type interface
 * @doc.purpose Durable repository contract for versioned YAPPC shape artifacts
 * @doc.layer service
 * @doc.pattern Repository
 */
public interface ShapeRepository {

    /**
     * Persists a derived shape version.
     *
     * @param shape derived shape
     * @param context tenant/workspace/project context
     * @return persisted shape version
     */
    Promise<ShapeVersionRecord> saveShape(
            @NotNull ShapeSpec shape,
            @NotNull ShapePersistenceContext context);

    /**
     * Persists a generated system model for a shape.
     *
     * @param model generated system model
     * @param context tenant/workspace/project context
     * @return persisted shape version with system model
     */
    Promise<ShapeVersionRecord> saveSystemModel(
            @NotNull SystemModel model,
            @NotNull ShapePersistenceContext context);

    /**
     * Finds the latest persisted shape version.
     *
     * @param tenantId tenant identifier
     * @param workspaceId workspace identifier
     * @param projectId project identifier
     * @param shapeId shape identifier
     * @return latest shape version if one exists
     */
    Promise<Optional<ShapeVersionRecord>> findLatest(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String shapeId);
}
