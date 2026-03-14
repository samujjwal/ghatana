/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics.report;

/**
 * Output format for a generated report.
 *
 * @doc.type enum
 * @doc.purpose Enumeration of supported report output formats
 * @doc.layer product
 * @doc.pattern Value Object
 */
public enum ReportFormat {

    /**
     * Structured JSON — an array of row maps.
     * Content-Type: {@code application/json}.
     */
    JSON,

    /**
     * RFC 4180-compliant CSV with a header row derived from column names.
     * Content-Type: {@code text/csv; charset=UTF-8}.
     */
    CSV,

    /**
     * Newline-delimited JSON (JSON Lines / NDJSON).
     * One JSON object per line; suitable for streaming consumers.
     * Content-Type: {@code application/x-ndjson}.
     */
    NDJSON
}
