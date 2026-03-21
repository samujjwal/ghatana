package com.ghatana.orchestrator.models;

import java.time.Instant;

public class OrchestratorPipelineEntity {
    public String id;
    public String name;
    public String description;
    public String config; // JSON string of pipeline config
    public String version;
    public Instant createdAt;
    public Instant updatedAt;
    public String createdBy;
    public String status; // DRAFT, ACTIVE, INACTIVE, DEPRECATED
    public String tenantId = "default";
}
