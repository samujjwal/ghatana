/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.analytics.report;

import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.QueryResult;
import com.ghatana.datacloud.analytics.export.EntityExportService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Production report-generation service for the Data-Cloud platform.
 *
 * <p>Bridges the analytics and export tiers to produce structured or formatted
 * reports on-demand. All blocking work (SQL execution, entity scanning) is
 * dispatched to a virtual-thread executor so the ActiveJ event loop is never
 * blocked.
 *
 * <h3>Report types</h3>
 * <ul>
 *   <li>{@link ReportType#QUERY} — runs an arbitrary SQL expression through the
 *       {@link AnalyticsQueryEngine} and returns tabular row data.</li>
 *   <li>{@link ReportType#ENTITY_EXPORT} — streams all entities from a named
 *       collection via {@link EntityExportService} and returns CSV or NDJSON
 *       text (or JSON rows for {@link ReportFormat#JSON}).</li>
 * </ul>
 *
 * <h3>Output formats</h3>
 * <ul>
 *   <li>{@link ReportFormat#JSON}   — structured JSON array (always available).</li>
 *   <li>{@link ReportFormat#CSV}    — RFC 4180-compliant CSV rendered by
 *       {@link EntityExportService} for ENTITY_EXPORT reports.</li>
 *   <li>{@link ReportFormat#NDJSON} — newline-delimited JSON rendered by
 *       {@link EntityExportService} for ENTITY_EXPORT reports.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * Thread-safe. The result cache is a bounded LRU {@link LinkedHashMap} guarded
 * by a {@link ConcurrentHashMap} wrapper. The virtual-thread executor is
 * terminated gracefully on {@link #close()}.
 *
 * <h3>Caching</h3>
 * Completed results are cached under their {@code reportId} for fast retrieval.
 * The cache is bounded to {@value #CACHE_MAX_SIZE} entries (LRU eviction).
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * ReportService service = new ReportService(analyticsEngine, exportService);
 *
 * ReportDefinition def = ReportDefinition.builder()
 *     .name("weekly-events")
 *     .type(ReportType.QUERY)
 *     .format(ReportFormat.CSV)
 *     .query("SELECT event_type, COUNT(*) AS cnt FROM events GROUP BY event_type")
 *     .build();
 *
 * ReportResult result = runPromise(() -> service.generate("tenant-123", def));
 * System.out.println(result.getFormattedBody()); // CSV text
 * }</pre>
 *
 * @see ReportDefinition
 * @see ReportResult
 * @doc.type class
 * @doc.purpose On-demand report generation service (query + entity-export)
 * @doc.layer product
 * @doc.pattern Service, Facade
 */
public final class ReportService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final int CACHE_MAX_SIZE = 500;

    private final AnalyticsQueryEngine analyticsEngine;
    private final EntityExportService exportService;

    /**
     * LRU result cache — bounded to {@value #CACHE_MAX_SIZE} entries.
     * Evicts the oldest entry when the limit is exceeded.
     */
    private final Map<String, ReportResult> resultCache =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ReportResult> eldest) {
                    return size() > CACHE_MAX_SIZE;
                }
            });

    // ── Constructors ───────────────────────────────────────────────────────────

    /**
     * Creates a {@link ReportService} backed by an analytics engine and an
     * export service.
     *
     * @param analyticsEngine engine for SQL-based QUERY reports (required)
     * @param exportService   service for entity ENTITY_EXPORT reports (required)
     */
    public ReportService(AnalyticsQueryEngine analyticsEngine, EntityExportService exportService) {
        this.analyticsEngine = Objects.requireNonNull(analyticsEngine, "analyticsEngine");
        this.exportService   = Objects.requireNonNull(exportService,   "exportService");
    }

    /**
     * Creates a {@link ReportService} — kept for test convenience; executor param ignored.
     *
     * @deprecated Use {@link #ReportService(AnalyticsQueryEngine, EntityExportService)} directly.
     */
    @Deprecated
    public ReportService(AnalyticsQueryEngine analyticsEngine,
                         EntityExportService exportService,
                         ExecutorService executor) {
        this(analyticsEngine, exportService);
    }

    // ── Core API ───────────────────────────────────────────────────────────────

    /**
     * Generates a report according to {@code definition} for the given tenant.
     *
     * <p>The returned {@link Promise} resolves to a {@link ReportResult} on
     * success, or propagates an exception on failure. The result is automatically
     * stored in the in-process cache for later retrieval via {@link #getResult}.
     *
     * @param tenantId   tenant identifier (non-blank)
     * @param definition report specification (non-null, validated)
     * @return promise of a {@link ReportResult}
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if {@code tenantId} is blank
     */
    public Promise<ReportResult> generate(String tenantId, ReportDefinition definition) {
        Objects.requireNonNull(tenantId,   "tenantId must not be null");
        Objects.requireNonNull(definition, "definition must not be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }

        String reportId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        log.info("[REPORT] Starting report id={} name='{}' type={} format={} tenant={}",
                reportId, definition.getName(), definition.getType(),
                definition.getFormat(), tenantId);

        return switch (definition.getType()) {
            case QUERY         -> executeQueryReport(reportId, tenantId, definition, startTime);
            case ENTITY_EXPORT -> executeExportReport(reportId, tenantId, definition, startTime);
        };
    }

    /**
     * Retrieves a cached {@link ReportResult} by its {@code reportId}.
     *
     * @param reportId the identifier returned at generation time
     * @return the cached result, or {@code null} if not found / evicted
     */
    public ReportResult getResult(String reportId) {
        return resultCache.get(reportId);
    }

    /**
     * Returns snapshot of all cached report IDs and their names.
     * Useful for listing recently-generated reports without re-running them.
     *
     * @return unmodifiable map of {@code reportId → reportName}
     */
    public Map<String, String> listCachedReports() {
        synchronized (resultCache) {
            Map<String, String> snapshot = new LinkedHashMap<>(resultCache.size());
            resultCache.forEach((id, r) -> snapshot.put(id, r.getReportName()));
            return Collections.unmodifiableMap(snapshot);
        }
    }

    // ── Private Execution Strategies ──────────────────────────────────────────

    /**
     * Executes a {@link ReportType#QUERY} report via {@link AnalyticsQueryEngine}.
     */
    private Promise<ReportResult> executeQueryReport(String reportId,
                                                     String tenantId,
                                                     ReportDefinition definition,
                                                     Instant startTime) {
        return analyticsEngine
                .submitQuery(tenantId, definition.getQuery(), definition.getParameters())
                .map(queryResult -> {
                    List<Map<String, Object>> rows = applyLimit(queryResult.getRows(), definition.getLimit());
                    String formatted = definition.getFormat() != ReportFormat.JSON
                            ? renderTextFormat(rows, definition.getFormat())
                            : null;

                    ReportResult result = ReportResult.builder()
                            .reportId(reportId)
                            .reportName(definition.getName())
                            .format(definition.getFormat())
                            .rows(definition.getFormat() == ReportFormat.JSON ? rows : List.of())
                            .rowCount(rows.size())
                            .formattedBody(formatted)
                            .contentType(contentTypeFor(definition.getFormat()))
                            .generatedAt(startTime)
                            .executionTime(Duration.between(startTime, Instant.now()))
                            .build();

                    resultCache.put(reportId, result);
                    log.info("[REPORT] Completed QUERY report id={} rows={} durationMs={}",
                            reportId, rows.size(), result.getExecutionTime().toMillis());
                    return result;
                })
                .then(Promise::of, ex -> {
                    log.error("[REPORT] QUERY report id={} failed: {}", reportId, ex.getMessage(), ex);
                    return Promise.ofException(ex);
                });
    }

    /**
     * Executes an {@link ReportType#ENTITY_EXPORT} report via {@link EntityExportService}.
     *
     * <p>{@link EntityExportService} returns Promises backed by its own virtual-thread
     * executor — these are safe to call and chain on the ActiveJ event loop without
     * wrapping in {@code Promise.ofBlocking}.
     *
     * <p>For {@link ReportFormat#JSON} the exported NDJSON text is parsed back to
     * structured row maps. For {@link ReportFormat#CSV} and {@link ReportFormat#NDJSON}
     * the raw text is stored directly in {@link ReportResult#getFormattedBody()}.
     */
    private Promise<ReportResult> executeExportReport(String reportId,
                                                      String tenantId,
                                                      ReportDefinition definition,
                                                      Instant startTime) {
        // For JSON output we export as NDJSON then convert to row maps in-process.
        // CSV and NDJSON are passed through as-is.
        Promise<String> exportPromise = definition.getFormat() == ReportFormat.CSV
                ? exportService.exportCsv(tenantId, definition.getCollection(), Map.of(), definition.getLimit())
                : exportService.exportNdjson(tenantId, definition.getCollection(), Map.of(), definition.getLimit());

        return exportPromise
                .map(body -> {
                    List<Map<String, Object>> rows;
                    String formattedBody;
                    int rowCount;

                    if (definition.getFormat() == ReportFormat.JSON) {
                        // NDJSON body → structured rows
                        rows = applyLimit(parseNdjson(body), definition.getLimit());
                        formattedBody = null;
                        rowCount = rows.size();
                    } else {
                        rows = List.of();           // raw text — avoid memory duplication
                        formattedBody = body;
                        rowCount = countLines(body, definition.getFormat());
                    }

                    ReportResult result = ReportResult.builder()
                            .reportId(reportId)
                            .reportName(definition.getName())
                            .format(definition.getFormat())
                            .rows(rows)
                            .rowCount(rowCount)
                            .formattedBody(formattedBody)
                            .contentType(contentTypeFor(definition.getFormat()))
                            .generatedAt(startTime)
                            .executionTime(Duration.between(startTime, Instant.now()))
                            .build();

                    resultCache.put(reportId, result);
                    log.info("[REPORT] Completed ENTITY_EXPORT report id={} collection='{}' rows={} durationMs={}",
                            reportId, definition.getCollection(), rowCount,
                            result.getExecutionTime().toMillis());
                    return result;
                })
                .then(Promise::of, ex -> {
                    log.error("[REPORT] ENTITY_EXPORT report id={} collection='{}' failed: {}",
                            reportId, definition.getCollection(), ex.getMessage(), ex);
                    return Promise.ofException(ex);
                });
    }

    // ── Formatting Helpers ─────────────────────────────────────────────────────

    /**
     * Renders a list of row maps as CSV or NDJSON text.
     *
     * <p>CSV rendering follows RFC 4180: values containing commas, double-quotes
     * or newlines are enclosed in double-quotes with internal double-quotes doubled.
     *
     * @param rows   source data
     * @param format target format ({@link ReportFormat#CSV} or {@link ReportFormat#NDJSON})
     * @return formatted text; never {@code null}
     */
    static String renderTextFormat(List<Map<String, Object>> rows, ReportFormat format) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        return switch (format) {
            case CSV    -> renderCsv(rows);
            case NDJSON -> renderNdjson(rows);
            case JSON   -> throw new IllegalArgumentException("JSON format must not reach renderTextFormat");
        };
    }

    private static String renderCsv(List<Map<String, Object>> rows) {
        // Collect union of all column names in insertion order
        LinkedHashSet<String> cols = new LinkedHashSet<>();
        rows.forEach(r -> cols.addAll(r.keySet()));
        String[] columns = cols.toArray(new String[0]);

        StringBuilder sb = new StringBuilder();
        // Header row
        appendCsvRow(sb, Arrays.stream(columns).map(ReportService::csvQuote).toArray(String[]::new));
        // Data rows
        for (Map<String, Object> row : rows) {
            String[] cells = Arrays.stream(columns)
                    .map(c -> csvQuote(row.getOrDefault(c, "") != null
                                      ? String.valueOf(row.getOrDefault(c, ""))
                                      : ""))
                    .toArray(String[]::new);
            appendCsvRow(sb, cells);
        }
        return sb.toString();
    }

    private static void appendCsvRow(StringBuilder sb, String[] cells) {
        sb.append(String.join(",", cells)).append("\r\n");
    }

    private static String csvQuote(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String renderNdjson(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : rows) {
            sb.append(mapToJsonLine(row)).append('\n');
        }
        return sb.toString();
    }

    /**
     * Minimal JSON-object serializer for a single row map.
     * Falls back gracefully — complex values are rendered via {@code toString()}.
     */
    private static String mapToJsonLine(Map<String, Object> row) {
        StringJoiner sj = new StringJoiner(",", "{", "}");
        row.forEach((k, v) -> {
            String key = "\"" + jsonEscape(k) + "\"";
            String val = v == null     ? "null"
                       : v instanceof Number n ? n.toString()
                       : v instanceof Boolean b ? b.toString()
                       : "\"" + jsonEscape(v.toString()) + "\"";
            sj.add(key + ":" + val);
        });
        return sj.toString();
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Parses a NDJSON string into a list of string-keyed maps.
     * Lines that cannot be parsed are silently skipped.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseNdjson(String ndjson) {
        if (ndjson == null || ndjson.isBlank()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (String line : ndjson.split("\n")) {
            line = line.strip();
            if (line.isEmpty()) continue;
            try {
                // Minimal NDJSON parse: use Jackson if available, otherwise skip complex values
                // This is a best-effort parse for the JSON output mode of ENTITY_EXPORT
                Map<String, Object> row = new LinkedHashMap<>();
                // Strip outer braces
                if (line.startsWith("{") && line.endsWith("}")) {
                    String inner = line.substring(1, line.length() - 1).trim();
                    // Split on commas NOT inside strings (simplified — sufficient for flat JSON)
                    for (String pair : splitTopLevelCommas(inner)) {
                        int colon = pair.indexOf(':');
                        if (colon < 0) continue;
                        String key = pair.substring(0, colon).trim().replaceAll("^\"|\"$", "");
                        String val = pair.substring(colon + 1).trim();
                        row.put(key, parseJsonValue(val));
                    }
                }
                result.add(row);
            } catch (Exception ignored) {
                log.debug("[REPORT] Skipped unparseable NDJSON line: {}", line);
            }
        }
        return result;
    }

    private static List<String> splitTopLevelCommas(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inStr = !inStr;
            if (!inStr && (c == '{' || c == '[')) depth++;
            if (!inStr && (c == '}' || c == ']')) depth--;
            if (!inStr && depth == 0 && c == ',') {
                parts.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < s.length()) parts.add(s.substring(start).trim());
        return parts;
    }

    private static Object parseJsonValue(String v) {
        if ("null".equals(v))  return null;
        if ("true".equals(v))  return Boolean.TRUE;
        if ("false".equals(v)) return Boolean.FALSE;
        if (v.startsWith("\"") && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1).replace("\\\"", "\"");
        }
        try { return Long.parseLong(v); }   catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(v); } catch (NumberFormatException ignored) {}
        return v;
    }

    private static List<Map<String, Object>> applyLimit(List<Map<String, Object>> rows, int limit) {
        return rows.size() <= limit ? rows : rows.subList(0, limit);
    }

    private static int countLines(String body, ReportFormat format) {
        if (body == null || body.isBlank()) return 0;
        long lines = body.lines().filter(l -> !l.isBlank()).count();
        // CSV: subtract 1 for header row
        return format == ReportFormat.CSV ? (int) Math.max(0, lines - 1) : (int) lines;
    }

    private static String contentTypeFor(ReportFormat format) {
        return switch (format) {
            case JSON  -> "application/json";
            case CSV   -> "text/csv; charset=UTF-8";
            case NDJSON -> "application/x-ndjson";
        };
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * No-op — included for {@link AutoCloseable} contract symmetry.
     * Delegates shut-down to the underlying engines which manage their own executors.
     */
    @Override
    public void close() {
        log.info("[REPORT] ReportService closed");
    }
}
