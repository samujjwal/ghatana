/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics;

/**
 * ReportTemplate domain model.
 * Template for generating a new report from query and format specifications.
 *
 * @doc.type class
 * @doc.purpose Template for report generation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public class ReportTemplate {
    private String name;
    private String format;  // CSV, PDF, XLSX
    private String query;

    public ReportTemplate() {}

    public String getName() {
        return name;
    }

    public ReportTemplate withName(String name) {
        this.name = name;
        return this;
    }

    public String getFormat() {
        return format;
    }

    public ReportTemplate withFormat(String format) {
        this.format = format;
        return this;
    }

    public String getQuery() {
        return query;
    }

    public ReportTemplate withQuery(String query) {
        this.query = query;
        return this;
    }
}
