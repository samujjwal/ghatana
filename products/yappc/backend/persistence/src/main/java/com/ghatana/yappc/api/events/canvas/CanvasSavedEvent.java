/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events.canvas;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raised when a canvas state is persisted (save checkpoint).
 *
 * @doc.type class
 * @doc.purpose Canvas-saved domain event
 * @doc.layer domain
 
 * @doc.pattern Event
*/
public final class CanvasSavedEvent extends DomainEvent {

    private final int nodeCount;
    private final int edgeCount;

    public CanvasSavedEvent(
            String canvasId,
            String tenantId,
            String userId,
            int nodeCount,
            int edgeCount) {
        super("canvas.saved", canvasId, "Canvas", tenantId, userId);
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
    }

    public int getNodeCount() { return nodeCount; }
    public int getEdgeCount() { return edgeCount; }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nodeCount", nodeCount);
        p.put("edgeCount", edgeCount);
        return p;
    }
}
