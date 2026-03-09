/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events.canvas;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raised when an edge is added between two canvas nodes.
 *
 * @doc.type class
 * @doc.purpose Canvas edge-connected domain event
 * @doc.layer domain
 
 * @doc.pattern Event
*/
public final class EdgeConnectedEvent extends DomainEvent {

    private final String edgeId;
    private final String sourceNodeId;
    private final String targetNodeId;
    private final String edgeType;

    public EdgeConnectedEvent(
            String canvasId,
            String tenantId,
            String userId,
            String edgeId,
            String sourceNodeId,
            String targetNodeId,
            String edgeType) {
        super("canvas.edge.connected", canvasId, "Canvas", tenantId, userId);
        this.edgeId       = edgeId;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.edgeType     = edgeType;
    }

    public String getEdgeId()       { return edgeId; }
    public String getSourceNodeId() { return sourceNodeId; }
    public String getTargetNodeId() { return targetNodeId; }
    public String getEdgeType()     { return edgeType; }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("edgeId",       edgeId);
        p.put("sourceNodeId", sourceNodeId);
        p.put("targetNodeId", targetNodeId);
        p.put("edgeType",     edgeType);
        return p;
    }
}
