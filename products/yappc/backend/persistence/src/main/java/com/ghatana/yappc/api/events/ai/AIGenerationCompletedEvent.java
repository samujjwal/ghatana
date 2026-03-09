/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events.ai;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raised when an AI generation request completes (successfully or not).
 *
 * @doc.type class
 * @doc.purpose AI generation-completed domain event
 * @doc.layer domain
 
 * @doc.pattern Event
*/
public final class AIGenerationCompletedEvent extends DomainEvent {

    private final String requestId;
    private final boolean success;
    private final long durationMs;
    private final double estimatedCostUsd;

    public AIGenerationCompletedEvent(
            String canvasId,
            String tenantId,
            String userId,
            String requestId,
            boolean success,
            long durationMs,
            double estimatedCostUsd) {
        super("ai.generation.completed", canvasId, "Canvas", tenantId, userId);
        this.requestId         = requestId;
        this.success           = success;
        this.durationMs        = durationMs;
        this.estimatedCostUsd  = estimatedCostUsd;
    }

    public String getRequestId()        { return requestId; }
    public boolean isSuccess()          { return success; }
    public long getDurationMs()         { return durationMs; }
    public double getEstimatedCostUsd() { return estimatedCostUsd; }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("requestId",        requestId);
        p.put("success",          success);
        p.put("durationMs",       durationMs);
        p.put("estimatedCostUsd", estimatedCostUsd);
        return p;
    }
}
