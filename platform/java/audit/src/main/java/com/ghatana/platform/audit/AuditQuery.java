/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-05-11
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * Query criteria for searching audit events.
 *
 * @doc.type class
 * @doc.purpose Encapsulates search criteria for audit event queries
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class AuditQuery {

    private final String tenantId;
    private final String projectId;
    private final String phase;
    private final String eventType;
    private final String principal;
    private final String resourceType;
    private final String resourceId;
    private final Instant startDate;
    private final Instant endDate;
    private final Boolean success;
    private final int offset;
    private final int limit;

    private AuditQuery(Builder builder) {
        this.tenantId = builder.tenantId;
        this.projectId = builder.projectId;
        this.phase = builder.phase;
        this.eventType = builder.eventType;
        this.principal = builder.principal;
        this.resourceType = builder.resourceType;
        this.resourceId = builder.resourceId;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.success = builder.success;
        this.offset = builder.offset;
        this.limit = builder.limit;
    }

    public String tenantId() {
        return tenantId;
    }

    public String projectId() {
        return projectId;
    }

    public String phase() {
        return phase;
    }

    public String eventType() {
        return eventType;
    }

    public String principal() {
        return principal;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    public Instant startDate() {
        return startDate;
    }

    public Instant endDate() {
        return endDate;
    }

    public Boolean success() {
        return success;
    }

    public int offset() {
        return offset;
    }

    public int limit() {
        return limit;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tenantId;
        private String projectId;
        private String phase;
        private String eventType;
        private String principal;
        private String resourceType;
        private String resourceId;
        private Instant startDate;
        private Instant endDate;
        private Boolean success;
        private int offset = 0;
        private int limit = 100;

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder phase(String phase) {
            this.phase = phase;
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

        public Builder startDate(Instant startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder endDate(Instant endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder success(Boolean success) {
            this.success = success;
            return this;
        }

        public Builder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public AuditQuery build() {
            return new AuditQuery(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditQuery that = (AuditQuery) o;
        return offset == that.offset &&
                limit == that.limit &&
                Objects.equals(tenantId, that.tenantId) &&
                Objects.equals(projectId, that.projectId) &&
                Objects.equals(phase, that.phase) &&
                Objects.equals(eventType, that.eventType) &&
                Objects.equals(principal, that.principal) &&
                Objects.equals(resourceType, that.resourceType) &&
                Objects.equals(resourceId, that.resourceId) &&
                Objects.equals(startDate, that.startDate) &&
                Objects.equals(endDate, that.endDate) &&
                Objects.equals(success, that.success);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, projectId, phase, eventType, principal, resourceType, resourceId,
                startDate, endDate, success, offset, limit);
    }

    @Override
    public String toString() {
        return "AuditQuery{" +
                "tenantId='" + tenantId + '\'' +
                ", projectId='" + projectId + '\'' +
                ", phase='" + phase + '\'' +
                ", eventType='" + eventType + '\'' +
                ", principal='" + principal + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", resourceId='" + resourceId + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", success=" + success +
                ", offset=" + offset +
                ", limit=" + limit +
                '}';
    }
}
