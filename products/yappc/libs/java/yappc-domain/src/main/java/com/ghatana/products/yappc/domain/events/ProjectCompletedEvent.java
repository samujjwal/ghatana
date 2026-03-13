/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Domain — Project Domain Events
 */
package com.ghatana.products.yappc.domain.events;

import com.ghatana.yappc.api.events.DomainEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event raised when a YAPPC project reaches the {@code institutionalize} terminal stage,
 * marking it as completed.
 *
 * @doc.type class
 * @doc.purpose Domain event signalling project completion
 * @doc.layer domain
 * @doc.pattern EventSourced
 */
public final class ProjectCompletedEvent extends DomainEvent {

    private final Instant completedAt;

    /**
     * Creates a ProjectCompletedEvent.
     *
     * @param projectId   the UUID of the completed project
     * @param tenantId    the tenant this project belongs to
     * @param completedAt the instant at which the project completed
     */
    public ProjectCompletedEvent(UUID projectId, String tenantId, Instant completedAt) {
        super("ProjectCompleted", projectId.toString(), "Project", tenantId, null);
        this.completedAt = completedAt;
    }

    public Instant getCompletedAt() { return completedAt; }

    @Override
    public Map<String, Object> toPayload() {
        return Map.of(
                "projectId",   getAggregateId(),
                "completedAt", completedAt.toString(),
                "tenantId",    getTenantId()
        );
    }
}
