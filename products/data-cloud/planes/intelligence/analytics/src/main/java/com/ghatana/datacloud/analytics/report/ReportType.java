/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics.report;

/**
 * Report type — determines the execution strategy used by {@link ReportService}.
 *
 * @doc.type enum
 * @doc.purpose Enumeration of supported report execution strategies
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum ReportType {

    /**
     * SQL-based analytical report executed via
     * {@link com.ghatana.datacloud.analytics.AnalyticsQueryEngine}.
     * The {@link ReportDefinition#getQuery()} field must be non-blank.
     */
    QUERY,

    /**
     * Bulk entity export from a named collection, executed via
     * {@link com.ghatana.datacloud.analytics.export.EntityExportService}.
     * The {@link ReportDefinition#getCollection()} field must be non-blank.
     */
    ENTITY_EXPORT
}
