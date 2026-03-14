/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics.report;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable specification for a data report.
 *
 * <p>A {@link ReportDefinition} describes <em>what</em> to report and <em>how</em>
 * to format the output. It is the input contract for {@link ReportService#generate}.
 *
 * <p>Two report types are supported:
 * <ul>
 *   <li>{@link ReportType#QUERY} — executes an arbitrary SQL expression via the
 *       {@link com.ghatana.datacloud.analytics.AnalyticsQueryEngine}.</li>
 *   <li>{@link ReportType#ENTITY_EXPORT} — streams all entities from a named
 *       collection via the
 *       {@link com.ghatana.datacloud.analytics.export.EntityExportService}.</li>
 * </ul>
 *
 * <p>The output {@link ReportFormat} controls what the caller receives:
 * <ul>
 *   <li>{@link ReportFormat#JSON} — structured JSON array of row maps.</li>
 *   <li>{@link ReportFormat#CSV}  — RFC 4180-compliant CSV with a header row.</li>
 *   <li>{@link ReportFormat#NDJSON} — newline-delimited JSON (JSON Lines).</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * ReportDefinition def = ReportDefinition.builder()
 *     .name("monthly-signups")
 *     .type(ReportType.QUERY)
 *     .format(ReportFormat.CSV)
 *     .query("SELECT country, COUNT(*) AS cnt FROM signups GROUP BY country")
 *     .limit(5000)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Immutable report specification (name, type, format, query/collection)
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class ReportDefinition {

    private final String name;
    private final ReportType type;
    private final ReportFormat format;

    /** SQL text — required when {@code type == QUERY}. */
    private final String query;

    /** Named query parameters bound into {@code query} — may be {@code null}. */
    private final Map<String, Object> parameters;

    /** Collection to export — required when {@code type == ENTITY_EXPORT}. */
    private final String collection;

    /** Maximum rows / entities returned (default {@value #DEFAULT_LIMIT}). */
    private final int limit;

    private static final int DEFAULT_LIMIT = 10_000;

    private ReportDefinition(Builder b) {
        this.name       = Objects.requireNonNull(b.name,   "name must not be null");
        this.type       = Objects.requireNonNull(b.type,   "type must not be null");
        this.format     = b.format != null ? b.format : ReportFormat.JSON;
        this.query      = b.query;
        this.parameters = b.parameters != null ? Map.copyOf(b.parameters) : Map.of();
        this.collection = b.collection;
        this.limit      = b.limit > 0 ? b.limit : DEFAULT_LIMIT;

        if (type == ReportType.QUERY && (query == null || query.isBlank())) {
            throw new IllegalArgumentException("QUERY reports require a non-blank 'query'");
        }
        if (type == ReportType.ENTITY_EXPORT && (collection == null || collection.isBlank())) {
            throw new IllegalArgumentException("ENTITY_EXPORT reports require a non-blank 'collection'");
        }
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public String getName()                   { return name;       }
    public ReportType getType()               { return type;       }
    public ReportFormat getFormat()           { return format;     }
    public String getQuery()                  { return query;      }
    public Map<String, Object> getParameters(){ return parameters; }
    public String getCollection()             { return collection; }
    public int getLimit()                     { return limit;      }

    // ── Builder ────────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for {@link ReportDefinition}.
     */
    public static final class Builder {
        private String name;
        private ReportType type;
        private ReportFormat format;
        private String query;
        private Map<String, Object> parameters;
        private String collection;
        private int limit;

        private Builder() {}

        public Builder name(String name)                      { this.name = name;             return this; }
        public Builder type(ReportType type)                  { this.type = type;             return this; }
        public Builder format(ReportFormat format)            { this.format = format;         return this; }
        public Builder query(String query)                    { this.query = query;           return this; }
        public Builder parameters(Map<String, Object> params) { this.parameters = params;     return this; }
        public Builder collection(String collection)          { this.collection = collection; return this; }
        public Builder limit(int limit)                       { this.limit = limit;           return this; }

        public ReportDefinition build() { return new ReportDefinition(this); }
    }

    // ── Factory helpers ────────────────────────────────────────────────────────

    /**
     * Builds a {@link ReportDefinition} from a JSON-parsed request map.
     *
     * <p>Expected keys (matching the HTTP API contract):
     * <ul>
     *   <li>{@code name}       (String, required)</li>
     *   <li>{@code type}       (String, required — "QUERY" | "ENTITY_EXPORT")</li>
     *   <li>{@code format}     (String, optional — "JSON" | "CSV" | "NDJSON", default "JSON")</li>
     *   <li>{@code query}      (String, required for QUERY type)</li>
     *   <li>{@code parameters} (Map&lt;String,Object&gt;, optional)</li>
     *   <li>{@code collection} (String, required for ENTITY_EXPORT type)</li>
     *   <li>{@code limit}      (int, optional, default 10 000)</li>
     * </ul>
     *
     * @param payload parsed request body map
     * @return a validated {@link ReportDefinition}
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    @SuppressWarnings("unchecked")
    public static ReportDefinition fromMap(Map<String, Object> payload) {
        Objects.requireNonNull(payload, "payload must not be null");

        String rawType   = (String) payload.get("type");
        String rawFormat = (String) payload.get("format");

        ReportType   type   = rawType   != null ? ReportType.valueOf(rawType.toUpperCase())   : null;
        ReportFormat format = rawFormat != null ? ReportFormat.valueOf(rawFormat.toUpperCase()) : ReportFormat.JSON;

        Object rawLimit = payload.get("limit");
        int limit = rawLimit instanceof Number num ? num.intValue() : DEFAULT_LIMIT;

        return builder()
            .name((String) payload.get("name"))
            .type(type)
            .format(format)
            .query((String) payload.get("query"))
            .parameters((Map<String, Object>) payload.get("parameters"))
            .collection((String) payload.get("collection"))
            .limit(limit)
            .build();
    }
}
