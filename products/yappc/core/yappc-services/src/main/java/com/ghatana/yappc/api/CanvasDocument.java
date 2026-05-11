/**
 * Canonical Canvas Document
 * 
 * Single source of truth for canvas document structure.
 * Defines the canonical schema for canvas documents that are persisted and shared across
 * the YAPPC platform. All canvas-related operations must use this schema as the authoritative
 * contract.
 * 
 * @doc.type class
 * @doc.purpose Canonical canvas document schema
 * @doc.layer product
 * @doc.pattern DTO
 */

package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical canvas document representing the complete state of a project canvas.
 * This is the authoritative schema for all canvas persistence and operations.
 */
public final class CanvasDocument {

    private final String id;
    private final String projectId;
    private final String workspaceId;
    private final String tenantId;
    private final String version;
    private final CanvasMetadata metadata;
    private final List<CanvasNode> nodes;
    private final List<CanvasEdge> edges;
    private final CanvasViewport viewport;
    private final CanvasDocumentState state;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;
    private final String updatedBy;
    private final Long revision;

    public CanvasDocument(
            @NotNull String id,
            @NotNull String projectId,
            @NotNull String workspaceId,
            @NotNull String tenantId,
            @NotNull String version,
            @NotNull CanvasMetadata metadata,
            @NotNull List<CanvasNode> nodes,
            @NotNull List<CanvasEdge> edges,
            @NotNull CanvasViewport viewport,
            @NotNull CanvasDocumentState state,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            @NotNull String createdBy,
            @NotNull String updatedBy,
            @NotNull Long revision
    ) {
        this.id = id;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.tenantId = tenantId;
        this.version = version;
        this.metadata = metadata;
        this.nodes = nodes;
        this.edges = edges;
        this.viewport = viewport;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.revision = revision;
    }

    public String id() {
        return id;
    }

    public String projectId() {
        return projectId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String version() {
        return version;
    }

    public CanvasMetadata metadata() {
        return metadata;
    }

    public List<CanvasNode> nodes() {
        return nodes;
    }

    public List<CanvasEdge> edges() {
        return edges;
    }

    public CanvasViewport viewport() {
        return viewport;
    }

    public CanvasDocumentState state() {
        return state;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public String updatedBy() {
        return updatedBy;
    }

    public Long revision() {
        return revision;
    }

    /**
     * Canvas metadata containing descriptive and governance information.
     */
    public record CanvasMetadata(
            String name,
            String description,
            String lifecyclePhase,
            Set<String> tags,
            Map<String, String> customProperties
    ) {}

    /**
     * Canvas node representing a single element in the canvas graph.
     */
    public record CanvasNode(
            String id,
            String nodeType,
            String artifactId,
            CanvasPoint position,
            CanvasBounds bounds,
            CanvasTransform transform,
            CanvasNodeData data,
            CanvasNodeStyle style,
            CanvasNodeState state,
            Set<String> tags,
            Map<String, Object> customProperties,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * Canvas edge representing a connection between two nodes.
     */
    public record CanvasEdge(
            String id,
            String sourceNodeId,
            String targetNodeId,
            String sourceHandleId,
            String targetHandleId,
            List<CanvasPoint> path,
            CanvasEdgeStyle style,
            CanvasEdgeData data,
            CanvasEdgeState state,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * Canvas viewport representing the current view state.
     */
    public record CanvasViewport(
            CanvasPoint position,
            double scale,
            double rotation
    ) {}

    /**
     * Canvas document state representing the overall document status.
     */
    public record CanvasDocumentState(
            DocumentStatus status,
            boolean isDirty,
            boolean isValid,
            List<String> validationErrors,
            String lockedBy,
            Instant lockedAt
    ) {
        public enum DocumentStatus {
            DRAFT,
            ACTIVE,
            ARCHIVED,
            LOCKED
        }
    }

    /**
     * Canvas point representing a 2D coordinate.
     */
    public record CanvasPoint(
            double x,
            double y
    ) {}

    /**
     * Canvas bounds representing a rectangular area.
     */
    public record CanvasBounds(
            double x,
            double y,
            double width,
            double height
    ) {}

    /**
     * Canvas transform representing position, scale, and rotation.
     */
    public record CanvasTransform(
            CanvasPoint position,
            double scale,
            double rotation
    ) {}

    /**
     * Canvas node data containing node-specific information.
     */
    public record CanvasNodeData(
            String label,
            String description,
            Map<String, Object> properties
    ) {}

    /**
     * Canvas node style containing visual properties.
     */
    public record CanvasNodeStyle(
            String backgroundColor,
            String borderColor,
            double borderWidth,
            String textColor,
            String fontFamily,
            double fontSize,
            double borderRadius,
            double opacity
    ) {}

    /**
     * Canvas node state representing the node's current status.
     */
    public record CanvasNodeState(
            NodeStatus status,
            boolean isVisible,
            boolean isLocked,
            boolean isSelected
    ) {
        public enum NodeStatus {
            NORMAL,
            ERROR,
            WARNING,
            SUCCESS
        }
    }

    /**
     * Canvas edge style containing visual properties.
     */
    public record CanvasEdgeStyle(
            String strokeColor,
            double strokeWidth,
            String strokeStyle,
            String markerEnd,
            String markerStart,
            double opacity
    ) {}

    /**
     * Canvas edge data containing edge-specific information.
     */
    public record CanvasEdgeData(
            String label,
            String description,
            Map<String, Object> properties
    ) {}

    /**
     * Canvas edge state representing the edge's current status.
     */
    public record CanvasEdgeState(
            EdgeStatus status,
            boolean isVisible,
            boolean isSelected
    ) {
        public enum EdgeStatus {
            NORMAL,
            ERROR,
            WARNING
        }
    }
}
