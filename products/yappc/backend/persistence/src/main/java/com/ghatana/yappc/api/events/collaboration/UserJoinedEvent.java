/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events.collaboration;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raised when a collaborator joins a canvas session.
 *
 * @doc.type class
 * @doc.purpose Collaboration user-joined domain event
 * @doc.layer domain
 
 * @doc.pattern Event
*/
public final class UserJoinedEvent extends DomainEvent {

    private final String userName;
    private final String userColor;

    public UserJoinedEvent(
            String canvasId,
            String tenantId,
            String userId,
            String userName,
            String userColor) {
        super("collaboration.user.joined", canvasId, "Canvas", tenantId, userId);
        this.userName  = userName;
        this.userColor = userColor;
    }

    public String getUserName()  { return userName; }
    public String getUserColor() { return userColor; }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userName",  userName);
        p.put("userColor", userColor);
        return p;
    }
}
