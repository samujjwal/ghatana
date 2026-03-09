/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events.collaboration;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.Map;

/**
 * Raised when a collaborator leaves a canvas session.
 *
 * @doc.type class
 * @doc.purpose Collaboration user-left domain event
 * @doc.layer domain
 
 * @doc.pattern Event
*/
public final class UserLeftEvent extends DomainEvent {

    public UserLeftEvent(String canvasId, String tenantId, String userId) {
        super("collaboration.user.left", canvasId, "Canvas", tenantId, userId);
    }

    @Override
    public Map<String, Object> toPayload() {
        return Map.of();
    }
}
