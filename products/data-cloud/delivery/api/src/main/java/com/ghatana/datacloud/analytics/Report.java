/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

import java.util.UUID;

/**
 * Report domain model.
 * Represents a generated or scheduled Analytics report.
 *
 * @doc.type class
 * @doc.purpose Domain model for Analytics reports
 * @doc.layer product
 * @doc.pattern Entity
 */
public class Report {
    private String id;
    private String name;
    private String format;  // CSV, PDF, XLSX
    private String status;  // PENDING, COMPLETED, FAILED
    private long createdAt;
    private String tenantId;
    private String createdBy;

    public Report() {
        this.id = "report-" + UUID.randomUUID();
        this.createdAt = System.currentTimeMillis();
        this.status = "PENDING";
    }

    public String getId() {
        return id;
    }

    public Report withId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public Report withName(String name) {
        this.name = name;
        return this;
    }

    public String getFormat() {
        return format;
    }

    public Report withFormat(String format) {
        this.format = format;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public Report withStatus(String status) {
        this.status = status;
        return this;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Report withCreatedAt(long createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Report withTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Report withCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }
}
