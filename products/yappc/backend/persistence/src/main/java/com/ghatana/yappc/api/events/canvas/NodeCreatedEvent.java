/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events.canvas;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raised when a node is added to a canvas.
 *
 * @doc.type class
 * @doc.purpose Canvas node-created domain event
 * @doc.layer domain
 
 * @doc.pattern Event
*/
public final class NodeCreatedEvent extends DomainEvent {

    private final String nodeId;
    private final String nodeType;
    private final double positionX;
    private final double positionY;
    private final Map<String, Object> data;

    public NodeCreatedEvent(
            String canvasId,
            String tenantId,
            String userId,
            String nodeId,
            String nodeType,
            double positionX,
            double positionY,
            Map<String, Object> data) {
        super("canvas.node.created", canvasId, "Canvas", tenantId, userId);
        this.nodeId    = nodeId;
        this.nodeType  = nodeType;
        this.positionX = positionX;
        this.positionY = positionY;
        this.data      = data != null ? data : Map.of();
    }

    public String getNodeId()              { return nodeId; }
    public String getNodeType()            { return nodeType; }
    public double getPositionX()           { return positionX; }
    public double getPositionY()           { return positionY; }
    public Map<String, Object> getData()   { return data; }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nodeId",    nodeId);
        p.put("nodeType",  nodeType);
        p.put("positionX", positionX);
        p.put("positionY", positionY);
        p.put("data",      data);
        return p;
    }
}
