/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.export;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Filter;
import com.ghatana.datacloud.DataCloudClient.Query;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Data export service for AEP entity collections.
 *
 * <p>Exports data from any DataCloud collection to CSV, JSON, or Newline-Delimited
 * JSON (NDJSON) format. Exports are bounded by {@link ExportRequest#maxRows()} and
 * fetched in pages of {@value #PAGE_SIZE} to avoid OOM on large collections.
 *
 * <h3>Supported formats</h3>
 * <table border="1">
 *   <tr><th>Format</th><th>MIME type</th><th>Description</th></tr>
 *   <tr><td>CSV</td>   <td>text/csv</td>  <td>RFC 4180 header + rows; values are double-quoted when needed</td></tr>
 *   <tr><td>JSON</td>  <td>application/json</td><td>JSON array of objects including {@code _id}</td></tr>
 *   <tr><td>NDJSON</td><td>application/x-ndjson</td><td>One JSON object per line; safe for streaming</td></tr>
 * </table>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AepDataExportService exporter = new AepDataExportService(client, meterRegistry);
 *
 * ExportResult result = exporter.export(ExportRequest.builder()
 *         .tenantId("tenant-acme")
 *         .collection("aep_patterns")
 *         .filter(Filter.eq("status", "ACTIVE"))
 *         .format(ExportFormat.CSV)
 *         .maxRows(5000)
 *         .build())
 *     .getResult();
 *
 * String csvContent = result.content();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose CSV/JSON/NDJSON data export service for AEP DataCloud collections
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AepDataExportService {

    private static final Logger log = LoggerFactory.getLogger(AepDataExportService.class);

    /** Number of entities fetched per DataCloud query page. */
    static final int PAGE_SIZE = 500;
    /** Hard cap on rows that can be exported in a single request. */
    static final int MAX_ROWS  = 100_000;

    private final DataCloudClient client;
    private final Counter         exportCounter;
    private final Counter         errorCounter;
    private final Timer           exportTimer;

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates an export service backed by the given DataCloud client.
     *
     * @param client        the DataCloud client; must not be {@code null}
     * @param meterRegistry Micrometer registry for operational metrics
     */
    public AepDataExportService(DataCloudClient client, MeterRegistry meterRegistry) {
        this.client       = Objects.requireNonNull(client, "DataCloudClient must not be null");
        Objects.requireNonNull(meterRegistry, "MeterRegistry must not be null");
        this.exportCounter = meterRegistry.counter("aep.export.total");
        this.errorCounter  = meterRegistry.counter("aep.export.errors");
        this.exportTimer   = meterRegistry.timer("aep.export.duration");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Export API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Exports entities from a DataCloud collection according to the given request.
     *
     * <p>The operation fetches data in pages and assembles the output in-memory.
     * Set a reasonable {@link ExportRequest#maxRows()} to bound memory usage.
     *
     * @param request export parameters
     * @return promise of the completed export result
     */
    public Promise<ExportResult> export(ExportRequest request) {
        Objects.requireNonNull(request, "ExportRequest must not be null");

        int maxRows  = Math.min(request.maxRows(), MAX_ROWS);
        Instant start = Instant.now();

        return fetchAllPages(request.tenantId(), request.collection(),
                             request.filters(), maxRows)
                .map(entities -> {
                    exportCounter.increment();
                    exportTimer.record(Duration.between(start, Instant.now()));

                    String content = render(entities, request.format());
                    log.info("Export complete tenant={} collection={} format={} rows={}",
                            request.tenantId(), request.collection(), request.format(), entities.size());

                    return new ExportResult(
                            request.tenantId(),
                            request.collection(),
                            request.format(),
                            content,
                            entities.size(),
                            Instant.now()
                    );
                })
                .whenException(e -> {
                    errorCounter.increment();
                    log.error("Export failed tenant={} collection={}: {}",
                            request.tenantId(), request.collection(), e.getMessage(), e);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Internal — Paged Fetch
    // ─────────────────────────────────────────────────────────────────────────

    private Promise<List<Entity>> fetchAllPages(String tenantId, String collection,
                                                 List<Filter> filters, int maxRows) {
        return fetchPage(tenantId, collection, filters, 0, maxRows, new ArrayList<>());
    }

    private Promise<List<Entity>> fetchPage(String tenantId, String collection,
                                             List<Filter> filters, int offset, int remaining,
                                             List<Entity> accumulated) {
        if (remaining <= 0) return Promise.of(Collections.unmodifiableList(accumulated));

        int pageSize = Math.min(PAGE_SIZE, remaining);

        // Tenant-isolation filter — always included
        List<Filter> effectiveFilters = new ArrayList<>(filters);
        boolean hasTenantFilter = filters.stream().anyMatch(f -> "tenantId".equals(f.field()));
        if (!hasTenantFilter) {
            effectiveFilters.add(Filter.eq("tenantId", tenantId));
        }

        Query query = Query.builder()
                .filters(effectiveFilters)
                .offset(offset)
                .limit(pageSize)
                .build();

        return client.query(tenantId, collection, query)
                .then(page -> {
                    accumulated.addAll(page);
                    // If we got fewer entities than the page size there are no more pages
                    if (page.size() < pageSize) {
                        return Promise.of(Collections.unmodifiableList(accumulated));
                    }
                    return fetchPage(tenantId, collection, filters,
                                     offset + pageSize, remaining - page.size(), accumulated);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Internal — Rendering
    // ─────────────────────────────────────────────────────────────────────────

    private static String render(List<Entity> entities, ExportFormat format) {
        return switch (format) {
            case CSV   -> renderCsv(entities);
            case JSON  -> renderJson(entities);
            case NDJSON-> renderNdjson(entities);
        };
    }

    private static String renderCsv(List<Entity> entities) {
        if (entities.isEmpty()) return "";

        // Collect all field names across all entities for stable header
        LinkedHashSet<String> allKeys = new LinkedHashSet<>();
        allKeys.add("_id");  // Entity ID first
        for (Entity e : entities) {
            allKeys.addAll(e.data().keySet());
        }
        List<String> headers = new ArrayList<>(allKeys);

        StringBuilder sb = new StringBuilder();
        // Header row
        sb.append(String.join(",", headers.stream().map(AepDataExportService::csvQuote)
                .collect(Collectors.toList())));
        sb.append("\n");

        // Data rows
        for (Entity entity : entities) {
            Map<String, Object> data = entity.data();
            List<String> values = headers.stream()
                    .map(h -> "_id".equals(h) ? entity.id() : String.valueOf(data.getOrDefault(h, "")))
                    .map(AepDataExportService::csvQuote)
                    .collect(Collectors.toList());
            sb.append(String.join(",", values));
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String renderJson(List<Entity> entities) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < entities.size(); i++) {
            sb.append("  ").append(entityToJsonObject(entities.get(i)));
            if (i < entities.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String renderNdjson(List<Entity> entities) {
        StringBuilder sb = new StringBuilder();
        for (Entity entity : entities) {
            sb.append(entityToJsonObject(entity)).append("\n");
        }
        return sb.toString();
    }

    private static String entityToJsonObject(Entity entity) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"_id\":").append(jsonString(entity.id()));
        for (Map.Entry<String, Object> entry : entity.data().entrySet()) {
            sb.append(",");
            sb.append(jsonString(entry.getKey())).append(":");
            sb.append(jsonValue(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Produces a properly escaped CSV-quoted field value.
     * Fields containing commas, double-quotes, or newlines are double-quoted.
     * Existing double-quotes are escaped by doubling them.
     */
    static String csvQuote(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                            .replace("\t", "\\t") + "\"";
    }

    private static String jsonValue(Object value) {
        if (value == null)              return "null";
        if (value instanceof Boolean)   return value.toString();
        if (value instanceof Number)    return value.toString();
        if (value instanceof String s)  return jsonString(s);
        if (value instanceof List<?> list) {
            return "[" + list.stream().map(AepDataExportService::jsonValue).collect(Collectors.joining(",")) + "]";
        }
        return jsonString(value.toString());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API Types
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Export output format.
     *
     * @doc.type enum
     * @doc.purpose Selects the serialization format for exported data
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public enum ExportFormat {
        /** RFC 4180 CSV with header row. */
        CSV,
        /** JSON array of objects. */
        JSON,
        /** Newline-delimited JSON; one object per line; suitable for streaming. */
        NDJSON
    }

    /**
     * Export request parameters.
     *
     * <p>Build via {@link ExportRequest#builder()}:
     * <pre>{@code
     * ExportRequest.builder()
     *     .tenantId("t1")
     *     .collection("aep_patterns")
     *     .filter(Filter.eq("status", "ACTIVE"))
     *     .format(ExportFormat.CSV)
     *     .maxRows(1000)
     *     .build();
     * }</pre>
     *
     * @param tenantId   owning tenant
     * @param collection DataCloud collection name
     * @param filters    row filters (combined with implicit tenantId isolation filter)
     * @param format     output format
     * @param maxRows    maximum rows to export (hard-capped at {@value MAX_ROWS})
     */
    public record ExportRequest(
            String       tenantId,
            String       collection,
            List<Filter> filters,
            ExportFormat format,
            int          maxRows) {

        private static final int DEFAULT_MAX_ROWS = 10_000;

        public ExportRequest {
            Objects.requireNonNull(tenantId,   "tenantId must not be null");
            Objects.requireNonNull(collection, "collection must not be null");
            Objects.requireNonNull(format,     "format must not be null");
            filters = filters != null ? List.copyOf(filters) : List.of();
            if (maxRows < 1) maxRows = DEFAULT_MAX_ROWS;
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String       tenantId;
            private String       collection;
            private final List<Filter> filters = new ArrayList<>();
            private ExportFormat format  = ExportFormat.JSON;
            private int          maxRows = DEFAULT_MAX_ROWS;

            public Builder tenantId(String id)   { this.tenantId   = id;  return this; }
            public Builder collection(String c)  { this.collection = c;   return this; }
            public Builder filter(Filter f)      { this.filters.add(f);   return this; }
            public Builder format(ExportFormat f){ this.format = f;        return this; }
            public Builder maxRows(int n)        { this.maxRows = n;       return this; }

            public ExportRequest build() {
                return new ExportRequest(tenantId, collection, filters, format, maxRows);
            }
        }
    }

    /**
     * Result of a completed data export.
     *
     * @param tenantId   owning tenant
     * @param collection source collection
     * @param format     output format used
     * @param content    serialized content (empty string when no rows were exported)
     * @param rowCount   number of rows included in the content
     * @param exportedAt when the export completed
     *
     * @doc.type record
     * @doc.purpose Immutable data export result
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record ExportResult(
            String       tenantId,
            String       collection,
            ExportFormat format,
            String       content,
            int          rowCount,
            Instant      exportedAt) {}
}
