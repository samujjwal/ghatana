/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events.canvas;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raised when a canvas node is removed.
 *
 * @doc.type class
 * @doc.purpose Canvas node-deleted domain event
 * @doc.layer domain
 
 * @doc.pattern Event
*/
public final class NodeDeletedEvent extends DomainEvent {

    private final String nodeId;

    public NodeDeletedEvent(
            String canvasId,
            String tenantId,
            String userId,
            String nodeId) {
        super("canvas.node.deleted", canvasId, "Canvas", tenantId, userId);
        this.nodeId = nodeId;
    }

    public String getNodeId() { return nodeId; }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nodeId", nodeId);
        return p;
    }
}
