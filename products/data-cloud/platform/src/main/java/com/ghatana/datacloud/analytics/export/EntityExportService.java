/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved.
 */
package com.ghatana.datacloud.analytics.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Export service for entity collections in CSV and NDJSON (newline-delimited JSON) formats.
 *
 * <p>Designed for bulk data egress (reporting, ETL, data migration). Supports two formats:
 * <ul>
 *   <li><b>CSV</b> — RFC 4180-compliant, with a header row derived from the superset of
 *       all field keys across the exported entities. Values are properly quoted and escaped.
 *   </li>
 *   <li><b>NDJSON</b> — one JSON object per line (also known as JSON Lines). Includes the
 *       full entity metadata (id, tenantId, collectionName, createdAt) plus the {@code data}
 *       payload flattened at the top level for maximum compatibility with streaming consumers.
 *   </li>
 * </ul>
 *
 * <p><b>Pagination</b><br>
 * Large collections are fetched in {@value #PAGE_SIZE}-entity pages and appended to the
 * output buffer. The combined output is then produced from a single virtual-thread call.
 *
 * <p><b>Thread safety</b><br>
 * All blocking work (I/O, serialization) is dispatched to a virtual-thread executor
 * so the ActiveJ event loop is never blocked.
 *
 * <p><b>Limits</b><br>
 * A maximum of {@value #HARD_LIMIT} entities can be exported per request to prevent
 * runaway memory allocation. Callers may pass a smaller {@code limit}.
 *
 * @doc.type service
 * @doc.purpose CSV and NDJSON bulk export for entity collections
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class EntityExportService {

    private static final Logger LOG = LoggerFactory.getLogger(EntityExportService.class);

    /** Maximum entities exported in one request (safety ceiling). */
    public static final int HARD_LIMIT = 100_000;

    /** Number of entities fetched per repository page. */
    static final int PAGE_SIZE = 500;

    /** RFC 4180 CSV delimiter. */
    private static final char CSV_DELIMITER = ',';
    private static final String CSV_LINE_SEPARATOR = "\r\n";

    // ── Dependencies ───────────────────────────────────────────────────────────

    private final EntityRepository entityRepository;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    /**
     * Creates an export service with default configuration.
     *
     * @param entityRepository repository used for data access
     * @param objectMapper     Jackson mapper for JSON serialization
     */
    public EntityExportService(EntityRepository entityRepository, ObjectMapper objectMapper) {
        this(entityRepository, objectMapper, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Full constructor for testing or custom configuration.
     *
     * @param entityRepository repository for data access
     * @param objectMapper     Jackson mapper for JSON serialization
     * @param executor         executor for off-loop blocking work
     */
    EntityExportService(EntityRepository entityRepository,
                        ObjectMapper objectMapper,
                        Executor executor) {
        this.entityRepository = Objects.requireNonNull(entityRepository, "entityRepository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Exports entities from the given collection as RFC 4180 CSV.
     *
     * <p>The header row is derived from the superset of all field names across the returned
     * entities, in sorted order. Missing fields for individual entities are left blank.
     * Standard entity metadata columns ({@code id}, {@code tenantId}, {@code collectionName},
     * {@code createdAt}) are prepended before the dynamic data fields.
     *
     * @param tenantId       tenant identifier (required)
     * @param collectionName name of the collection to export (required)
     * @param filter         optional equality filters; pass an empty map for no filtering
     * @param limit          maximum number of entities to export; capped at {@value #HARD_LIMIT}
     * @return promise resolving to a UTF-8 CSV string
     */
    public Promise<String> exportCsv(String tenantId, String collectionName,
                                     Map<String, Object> filter, int limit) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collectionName, "collectionName");
        int effectiveLimit = clampLimit(limit);

        return fetchAll(tenantId, collectionName, filter, effectiveLimit)
                .then(entities -> Promise.ofBlocking(executor, () -> {
                    LOG.debug("Exporting {} entities as CSV for tenant={} collection={}",
                            entities.size(), tenantId, collectionName);
                    return buildCsv(entities);
                }));
    }

    /**
     * Exports entities from the given collection as NDJSON (Newline-Delimited JSON).
     *
     * <p>Each line is a self-contained JSON object containing standard entity metadata
     * merged with the entity's {@code data} payload. This format is directly compatible
     * with streaming consumers such as AWS S3 Select, BigQuery, and Elasticsearch bulk API.
     *
     * @param tenantId       tenant identifier (required)
     * @param collectionName name of the collection to export (required)
     * @param filter         optional equality filters; pass an empty map for no filtering
     * @param limit          maximum number of entities to export; capped at {@value #HARD_LIMIT}
     * @return promise resolving to a UTF-8 NDJSON string (one JSON object per line)
     */
    public Promise<String> exportNdjson(String tenantId, String collectionName,
                                        Map<String, Object> filter, int limit) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(collectionName, "collectionName");
        int effectiveLimit = clampLimit(limit);

        return fetchAll(tenantId, collectionName, filter, effectiveLimit)
                .then(entities -> Promise.ofBlocking(executor, () -> {
                    LOG.debug("Exporting {} entities as NDJSON for tenant={} collection={}",
                            entities.size(), tenantId, collectionName);
                    return buildNdjson(entities);
                }));
    }

    // ── Fetching ───────────────────────────────────────────────────────────────

    /**
     * Fetches all entities up to {@code limit} by issuing as many paged repository
     * calls as necessary on the event loop. Pages are assembled with
     * {@link Promises#sequence} so each page starts only after the previous one resolves.
     */
    private Promise<List<Entity>> fetchAll(String tenantId, String collectionName,
                                           Map<String, Object> filter, int limit) {
        List<Entity> collected = new ArrayList<>(Math.min(limit, PAGE_SIZE));

        // Use iterative page-fetching: issue pages one at a time until we have enough
        return fetchPage(tenantId, collectionName, filter, 0, limit, collected);
    }

    private Promise<List<Entity>> fetchPage(String tenantId, String collectionName,
                                             Map<String, Object> filter,
                                             int offset, int remaining,
                                             List<Entity> collected) {
        if (remaining <= 0) {
            return Promise.of(collected);
        }
        int batchSize = Math.min(PAGE_SIZE, remaining);
        return entityRepository.findAll(tenantId, collectionName, filter, "createdAt:ASC", offset, batchSize)
                .then(page -> {
                    collected.addAll(page);
                    if (page.size() < batchSize) {
                        // No more entities available
                        return Promise.of(collected);
                    }
                    return fetchPage(tenantId, collectionName, filter,
                            offset + batchSize, remaining - batchSize, collected);
                });
    }

    // ── CSV serialization ──────────────────────────────────────────────────────

    private static String buildCsv(List<Entity> entities) {
        if (entities.isEmpty()) {
            return "";
        }

        // Collect all unique data field names in sorted order for a stable header
        Set<String> dataFields = new LinkedHashSet<>();
        for (Entity entity : entities) {
            if (entity.getData() != null) {
                dataFields.addAll(entity.getData().keySet());
            }
        }
        List<String> dataFieldList = new ArrayList<>(dataFields);
        Collections.sort(dataFieldList);

        // Build CSV header
        List<String> headers = new ArrayList<>();
        headers.add("id");
        headers.add("tenantId");
        headers.add("collectionName");
        headers.add("createdAt");
        headers.addAll(dataFieldList);

        StringBuilder sb = new StringBuilder(entities.size() * 64);
        appendCsvRow(sb, headers);

        // Build data rows
        for (Entity entity : entities) {
            List<String> row = new ArrayList<>(headers.size());
            row.add(nullToEmpty(entity.getId()));
            row.add(nullToEmpty(entity.getTenantId()));
            row.add(nullToEmpty(entity.getCollectionName()));
            row.add(nullToEmpty(entity.getCreatedAt()));

            Map<String, Object> data = entity.getData() != null ? entity.getData() : Collections.emptyMap();
            for (String field : dataFieldList) {
                row.add(nullToEmpty(data.get(field)));
            }
            appendCsvRow(sb, row);
        }

        return sb.toString();
    }

    private static void appendCsvRow(StringBuilder sb, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(CSV_DELIMITER);
            sb.append(csvEscape(values.get(i)));
        }
        sb.append(CSV_LINE_SEPARATOR);
    }

    /**
     * RFC 4180 CSV field escaping.
     * Fields containing commas, double-quotes, or newlines are wrapped in double-quotes.
     * Existing double-quotes are doubled.
     */
    static String csvEscape(String value) {
        if (value == null || value.isEmpty()) return "";
        boolean needsQuoting = value.indexOf(CSV_DELIMITER) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuoting) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    // ── NDJSON serialization ───────────────────────────────────────────────────

    private String buildNdjson(List<Entity> entities) throws JsonProcessingException {
        if (entities.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(entities.size() * 128);
        for (Entity entity : entities) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", entity.getId() != null ? entity.getId().toString() : null);
            record.put("tenantId", entity.getTenantId());
            record.put("collectionName", entity.getCollectionName());
            record.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
            // Merge data fields at top level for easy consumption
            if (entity.getData() != null) {
                record.putAll(entity.getData());
            }
            sb.append(objectMapper.writeValueAsString(record));
            sb.append('\n');
        }
        return sb.toString();
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static int clampLimit(int requested) {
        if (requested <= 0) return HARD_LIMIT;
        return Math.min(requested, HARD_LIMIT);
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }
}
