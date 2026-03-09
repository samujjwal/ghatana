package com.ghatana.catalog.adapters.jdbc.entity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Row model for table `event_types`.
 * This is a simple data carrier used by the JDBC repository.
 */
public final class EventTypeRow {
    // Core identifiers
    private final String tenantId;
    private final String namespace;
    private final String name;
    private final int versionMajor;
    private final int versionMinor;
    private final int versionPatch;
    private final String versionString; // e.g. 1.0.0[-pre]
    
    // Schema definitions
    private final String headerSpecJson;    // serialized headers schema
    private final String payloadSchemaJson; // serialized payload schema
    private final String policyMode;        // compatibility policy string
    
    // Event context
    private final String contextType;
    private final boolean intervalBased;
    private final Long granularity;
    private final boolean supportsConfidence;
    
    // Metadata
    private final String description;
    private final List<String> tags;
    private final List<String> examples;
    private final String category;
    private final String compatibilityPolicy;
    private final String lifecycleState;
    private final String statusMessage;
    private final String governance;
    private final String storageHints;
    private final String owner;
    
    // Timestamps
    private final Instant createdAt;
    private final Instant updatedAt;

    public EventTypeRow(
            // Core identifiers
            String tenantId,
            String namespace,
            String name,
            int versionMajor,
            int versionMinor,
            int versionPatch,
            String versionString,
            
            // Schema definitions
            String headerSpecJson,
            String payloadSchemaJson,
            String policyMode,
            
            // Event context
            String contextType,
            Boolean intervalBased,
            Long granularity,
            Boolean supportsConfidence,
            
            // Metadata
            String description,
            List<String> tags,
            List<String> examples,
            String category,
            String compatibilityPolicy,
            String lifecycleState,
            String statusMessage,
            String governance,
            String storageHints,
            String owner,
            
            // Timestamps
            Instant createdAt,
            Instant updatedAt) {
        
        // Core identifiers
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.versionMajor = versionMajor;
        this.versionMinor = versionMinor;
        this.versionPatch = versionPatch;
        this.versionString = Objects.requireNonNull(versionString, "versionString cannot be null");
        
        // Schema definitions
        this.headerSpecJson = headerSpecJson;
        this.payloadSchemaJson = payloadSchemaJson;
        this.policyMode = policyMode;
        
        // Event context
        this.contextType = contextType != null ? contextType : "EVENT_CONTEXT_TYPE_UNSPECIFIED";
        this.intervalBased = intervalBased != null ? intervalBased : false;
        this.granularity = granularity;
        this.supportsConfidence = supportsConfidence != null ? supportsConfidence : true;
        
        // Metadata
        this.description = description;
        this.tags = tags != null ? List.copyOf(tags) : List.of();
        this.examples = examples != null ? List.copyOf(examples) : List.of();
        this.category = category;
        this.compatibilityPolicy = compatibilityPolicy;
        this.lifecycleState = lifecycleState != null ? lifecycleState : "ACTIVE";
        this.statusMessage = statusMessage;
        this.governance = governance;
        this.storageHints = storageHints;
        this.owner = owner;
        
        // Timestamps
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public String getTenantId() {
        return tenantId;
    }
    public String getNamespace() {
        return namespace;
    }
    public String getName() {
        return name;
    }
    public int getVersionMajor() {
        return versionMajor;
    }
    public int getVersionMinor() {
        return versionMinor;
    }
    public int getVersionPatch() {
        return versionPatch;
    }
    public String getVersionString() {
        return versionString;
    }
    public String getHeaderSpecJson() {
        return headerSpecJson;
    }
    public String getPayloadSchemaJson() {
        return payloadSchemaJson;
    }
    public String getPolicyMode() {
        return policyMode;
    }
    public String getLifecycleState() {
        return lifecycleState;
    }
    public String getOwner() {
        return owner;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}
