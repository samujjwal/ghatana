/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events.collaboration;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raised when a comment is added to a canvas node.
 *
 * @doc.type class
 * @doc.purpose Collaboration comment-added domain event
 * @doc.layer domain
 
 * @doc.pattern Event
*/
public final class CommentAddedEvent extends DomainEvent {

    private final String commentId;
    private final String nodeId;
    private final String text;

    public CommentAddedEvent(
            String canvasId,
            String tenantId,
            String userId,
            String commentId,
            String nodeId,
            String text) {
        super("collaboration.comment.added", canvasId, "Canvas", tenantId, userId);
        this.commentId = commentId;
        this.nodeId    = nodeId;
        this.text      = text;
    }

    public String getCommentId() { return commentId; }
    public String getNodeId()    { return nodeId; }
    public String getText()      { return text; }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("commentId", commentId);
        p.put("nodeId",    nodeId);
        p.put("text",      text);
        return p;
    }
}
