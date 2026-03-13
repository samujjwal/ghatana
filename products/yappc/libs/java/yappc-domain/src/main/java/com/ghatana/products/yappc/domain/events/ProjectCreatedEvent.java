/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Domain — Project Domain Events
 */
package com.ghatana.products.yappc.domain.events;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Domain event raised when a new YAPPC project is created.
 *
 * <p>Published to Event Cloud via AEP after the project entity is persisted,
 * allowing downstream services to react to the project creation.
 *
 * @doc.type class
 * @doc.purpose Domain event for project creation lifecycle signal
 * @doc.layer domain
 * @doc.pattern EventSourced
 */
public final class ProjectCreatedEvent extends DomainEvent {

    private final String projectName;
    private final String createdBy;

    /**
     * Creates a ProjectCreatedEvent.
     *
     * @param projectId   the UUID of the newly created project
     * @param tenantId    the tenant this project belongs to
     * @param projectName the human-readable project name
     * @param createdBy   user or service that initiated the creation
     */
    public ProjectCreatedEvent(UUID projectId, String tenantId, String projectName, String createdBy) {
        super("ProjectCreated", projectId.toString(), "Project", tenantId, createdBy);
        this.projectName = projectName;
        this.createdBy   = createdBy;
    }

    public String getProjectName() { return projectName; }
    public String getCreatedBy()   { return createdBy; }

    @Override
    public Map<String, Object> toPayload() {
        return Map.of(
                "projectId",   getAggregateId(),
                "projectName", projectName,
                "createdBy",   createdBy,
                "tenantId",    getTenantId()
        );
    }
}
