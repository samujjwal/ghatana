/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Domain — Project Domain Events
 */
package com.ghatana.products.yappc.domain.events;

import com.ghatana.yappc.api.events.DomainEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Domain event raised when a YAPPC project advances to a new lifecycle stage.
 *
 * <p>Lifecycle stages follow the YAPPC methodology:
 * {@code intent → context → plan → execute → verify → observe → learn → institutionalize}.
 *
 * @doc.type class
 * @doc.purpose Domain event for project stage transitions
 * @doc.layer domain
 * @doc.pattern EventSourced
 */
public final class ProjectStageAdvancedEvent extends DomainEvent {

    private final String previousStage;
    private final String newStage;

    /**
     * Creates a ProjectStageAdvancedEvent.
     *
     * @param projectId     the UUID of the project
     * @param tenantId      the tenant this project belongs to
     * @param previousStage the stage the project transitioned FROM
     * @param newStage      the stage the project transitioned TO
     */
    public ProjectStageAdvancedEvent(UUID projectId, String tenantId, String previousStage, String newStage) {
        super("ProjectStageAdvanced", projectId.toString(), "Project", tenantId, null);
        this.previousStage = previousStage;
        this.newStage      = newStage;
    }

    public String getPreviousStage() { return previousStage; }
    public String getNewStage()      { return newStage; }

    @Override
    public Map<String, Object> toPayload() {
        return Map.of(
                "projectId",     getAggregateId(),
                "previousStage", previousStage,
                "newStage",      newStage,
                "tenantId",      getTenantId()
        );
    }
}
