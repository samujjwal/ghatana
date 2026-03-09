/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events.canvas;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raised when a canvas node is modified.
 *
 * @doc.type class
 * @doc.purpose Canvas node-updated domain event
 * @doc.layer domain
 
 * @doc.pattern Event
*/
public final class NodeUpdatedEvent extends DomainEvent {

    private final String nodeId;
    private final Map<String, Object> changes;

    public NodeUpdatedEvent(
            String canvasId,
            String tenantId,
            String userId,
            String nodeId,
            Map<String, Object> changes) {
        super("canvas.node.updated", canvasId, "Canvas", tenantId, userId);
        this.nodeId   = nodeId;
        this.changes  = changes != null ? changes : Map.of();
    }

    public String getNodeId()                { return nodeId; }
    public Map<String, Object> getChanges()  { return changes; }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nodeId",   nodeId);
        p.put("changes",  changes);
        return p;
    }
}
