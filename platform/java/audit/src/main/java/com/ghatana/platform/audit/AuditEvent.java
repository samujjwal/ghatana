/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.audit;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain object representing an audit event.
 *
 * @doc.type class
 * @doc.purpose Immutable audit event capturing who-what-when-where
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class AuditEvent {
    private final String id;
    private final String tenantId;
    private final String eventType;
    private final String principal;
    private final String resourceType;
    private final String resourceId;
    private final Boolean success;
    private final Instant timestamp;
    private final Map<String, Object> details;

    private AuditEvent(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId is required");
        this.eventType = Objects.requireNonNull(builder.eventType, "eventType is required");
        this.principal = builder.principal;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.success = builder.success;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.details = builder.details != null ? new HashMap<>(builder.details) : new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public Boolean getSuccess() {
        return success;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getDetails() {
        return Collections.unmodifiableMap(details);
    }

    public Object getDetail(String key) {
        return details.get(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String tenantId;
        private String eventType;
        private String principal;
        private String resourceType;
        private String resourceId;
        private Boolean success;
        private Instant timestamp;
        private Map<String, Object> details = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder principal(String principal) {
            this.principal = principal;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder success(Boolean success) {
            this.success = success;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder detail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details != null ? new HashMap<>(details) : new HashMap<>();
            return this;
        }

        public AuditEvent build() {
            return new AuditEvent(this);
        }
    }
}
