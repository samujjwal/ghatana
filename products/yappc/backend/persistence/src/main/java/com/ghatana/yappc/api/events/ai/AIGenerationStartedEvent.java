/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events.ai;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raised when an AI generation request is submitted.
 *
 * @doc.type class
 * @doc.purpose AI generation-started domain event
 * @doc.layer domain
 
 * @doc.pattern Event
*/
public final class AIGenerationStartedEvent extends DomainEvent {

    private final String requestId;
    private final String feature;
    private final String prompt;

    public AIGenerationStartedEvent(
            String canvasId,
            String tenantId,
            String userId,
            String requestId,
            String feature,
            String prompt) {
        super("ai.generation.started", canvasId, "Canvas", tenantId, userId);
        this.requestId = requestId;
        this.feature   = feature;
        this.prompt    = prompt;
    }

    public String getRequestId() { return requestId; }
    public String getFeature()   { return feature; }
    public String getPrompt()    { return prompt; }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("requestId", requestId);
        p.put("feature",   feature);
        p.put("prompt",    prompt);
        return p;
    }
}
