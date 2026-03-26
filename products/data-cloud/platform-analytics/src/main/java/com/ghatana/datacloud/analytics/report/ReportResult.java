/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics.report;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result produced by a successful {@link ReportService#generate} invocation.
 *
 * <p>Callers receive either a structured {@link #rows()} view (always populated) or the
 * formatted text body ({@link #formattedBody()}) when the requested
 * {@link ReportFormat} is {@link ReportFormat#CSV} or {@link ReportFormat#NDJSON}.
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@link #reportId}     — stable identifier for caching / retrieval.</li>
 *   <li>{@link #reportName}   — human-readable label from the definition.</li>
 *   <li>{@link #format}       — the output format that was generated.</li>
 *   <li>{@link #rows}         — structured row data (non-null; empty list for NDJSON/CSV
 *                               raw modes).</li>
 *   <li>{@link #rowCount}     — total rows in the result.</li>
 *   <li>{@link #formattedBody} — pre-rendered CSV or NDJSON string; {@code null} for JSON.</li>
 *   <li>{@link #contentType}  — MIME type matching the format.</li>
 *   <li>{@link #generatedAt}  — UTC timestamp of generation.</li>
 *   <li>{@link #executionTime} — wall-clock duration of the report run.</li>
 * </ul>
 *
 * @doc.type record
 * @doc.purpose Immutable report execution result
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class ReportResult {

    private final String reportId;
    private final String reportName;
    private final ReportFormat format;
    private final List<Map<String, Object>> rows;
    private final int rowCount;
    private final String formattedBody;
    private final String contentType;
    private final Instant generatedAt;
    private final Duration executionTime;

    private ReportResult(Builder b) {
        this.reportId       = Objects.requireNonNull(b.reportId,     "reportId");
        this.reportName     = Objects.requireNonNull(b.reportName,   "reportName");
        this.format         = Objects.requireNonNull(b.format,       "format");
        this.rows           = b.rows != null ? List.copyOf(b.rows) : List.of();
        this.rowCount       = b.rowCount;
        this.formattedBody  = b.formattedBody;
        this.contentType    = Objects.requireNonNull(b.contentType,  "contentType");
        this.generatedAt    = b.generatedAt != null ? b.generatedAt : Instant.now();
        this.executionTime  = Objects.requireNonNull(b.executionTime, "executionTime");
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public String getReportId()                       { return reportId;      }
    public String getReportName()                     { return reportName;    }
    public ReportFormat getFormat()                   { return format;        }
    public List<Map<String, Object>> getRows()        { return rows;          }
    public int getRowCount()                          { return rowCount;      }
    public String getFormattedBody()                  { return formattedBody; }
    public String getContentType()                    { return contentType;   }
    public Instant getGeneratedAt()                   { return generatedAt;   }
    public Duration getExecutionTime()                { return executionTime; }

    // ── Builder ────────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link ReportResult}.
     */
    public static final class Builder {
        private String reportId;
        private String reportName;
        private ReportFormat format;
        private List<Map<String, Object>> rows;
        private int rowCount;
        private String formattedBody;
        private String contentType;
        private Instant generatedAt;
        private Duration executionTime;

        private Builder() {}

        public Builder reportId(String v)                           { reportId = v;       return this; }
        public Builder reportName(String v)                         { reportName = v;     return this; }
        public Builder format(ReportFormat v)                       { format = v;         return this; }
        public Builder rows(List<Map<String, Object>> v)            { rows = v;           return this; }
        public Builder rowCount(int v)                              { rowCount = v;       return this; }
        public Builder formattedBody(String v)                      { formattedBody = v;  return this; }
        public Builder contentType(String v)                        { contentType = v;    return this; }
        public Builder generatedAt(Instant v)                       { generatedAt = v;    return this; }
        public Builder executionTime(Duration v)                    { executionTime = v;  return this; }

        public ReportResult build() { return new ReportResult(this); }
    }
}
