/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.plugins.iceberg.TierMigrationScheduler;
import com.ghatana.datacloud.plugins.s3archive.ArchiveMigrationScheduler;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handles manual storage-tier migration requests (B10).
 *
 * <p>Exposes a single endpoint:
 * <pre>
 *   POST /api/v1/collections/:id/migrate?targetTier=WARM|COLD
 * </pre>
 *
 * <p>Tier semantics:
 * <ul>
 *   <li><b>WARM</b> — L1 → L2 migration via {@link TierMigrationScheduler}
 *       (PostgreSQL → Iceberg)</li>
 *   <li><b>COLD</b> — L2 → L3 migration via {@link ArchiveMigrationScheduler}
 *       (Iceberg → S3 cold archive)</li>
 * </ul>
 *
 * <p>When the relevant scheduler is not wired (no plugin credentials supplied),
 * the endpoint returns {@code 503 Service Unavailable} with a descriptive message.
 *
 * @doc.type class
 * @doc.purpose Manual storage-tier migration endpoint for Data-Cloud collections
 * @doc.layer product
 * @doc.pattern Handler
 */
public class TierMigrationHandler {

    private static final Logger log = LoggerFactory.getLogger(TierMigrationHandler.class);

    private static final Set<String> VALID_TIERS = Set.of("WARM", "COLD");

    private final HttpHandlerSupport http;

    /**
     * Optional warm-tier scheduler (L1 → L2 via Iceberg).
     * {@code null} when Iceberg credentials are not configured.
     */
    private final TierMigrationScheduler tierMigrationScheduler;

    /**
     * Optional cold-tier scheduler (L2 → L3 via S3 archive).
     * {@code null} when S3 archive credentials are not configured.
     */
    private final ArchiveMigrationScheduler archiveMigrationScheduler;

    /**
     * Creates a handler for manual tier migration.
     *
     * @param http                     shared HTTP helper
     * @param tierMigrationScheduler   L1→L2 scheduler; may be {@code null}
     * @param archiveMigrationScheduler L2→L3 scheduler; may be {@code null}
     *
     * @doc.type constructor
     * @doc.purpose Initialise the tier migration handler
     * @doc.layer product
     * @doc.pattern Handler
     */
    public TierMigrationHandler(HttpHandlerSupport http,
                                TierMigrationScheduler tierMigrationScheduler,
                                ArchiveMigrationScheduler archiveMigrationScheduler) {
        this.http = http;
        this.tierMigrationScheduler = tierMigrationScheduler;
        this.archiveMigrationScheduler = archiveMigrationScheduler;
    }

    /**
     * POST /api/v1/collections/:id/migrate?targetTier=WARM|COLD
     *
     * <p>Triggers an on-demand migration cycle for the given collection. The
     * {@code id} path parameter is used as the stream name and the caller's
     * tenant header is used for tenant scoping.
     *
     * <p>Response (200):
     * <pre>{@code
     * {
     *   "collection": "orders",
     *   "targetTier": "WARM",
     *   "status": "SCHEDULED" | "COMPLETED",
     *   "eventsMigrated": 12345
     * }
     * }</pre>
     *
     * @param request incoming HTTP request
     * @return 200 on success, 400 on bad input, 503 when scheduler unavailable
     *
     * @doc.type method
     * @doc.purpose Trigger manual storage-tier migration for a collection
     * @doc.layer product
     * @doc.pattern Handler
     */
    public Promise<HttpResponse> handleMigrateCollection(HttpRequest request) {
        String collectionId = request.getPathParameter("id");
        String tenantId = http.resolveTenantId(request);
        String targetTier = request.getQueryParameter("targetTier");

        if (collectionId == null || collectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "Collection id is required"));
        }
        if (targetTier == null || targetTier.isBlank()) {
            return Promise.of(http.errorResponse(400, "'targetTier' query parameter is required (WARM | COLD)"));
        }
        String normalizedTier = targetTier.trim().toUpperCase();
        if (!VALID_TIERS.contains(normalizedTier)) {
            return Promise.of(http.errorResponse(400, "Invalid targetTier '" + targetTier + "'. Valid values: WARM, COLD"));
        }

        log.info("[B10] Manual tier migration requested: tenant={} collection={} targetTier={}",
                tenantId, collectionId, normalizedTier);

        if ("WARM".equals(normalizedTier)) {
            return handleWarmMigration(tenantId, collectionId);
        } else {
            return handleColdMigration(tenantId, collectionId);
        }
    }

    // ==================== Private Helpers ====================

    private Promise<HttpResponse> handleWarmMigration(String tenantId, String collectionId) {
        if (tierMigrationScheduler == null) {
            return Promise.of(http.errorResponse(503,
                    "WARM tier migration is not configured. Supply Iceberg catalog credentials via ICEBERG_CATALOG_URI."));
        }
        return tierMigrationScheduler.triggerMigration(tenantId, collectionId)
                .map(eventsMigrated -> {
                    log.info("[B10] WARM migration completed: tenant={} collection={} events={}",
                            tenantId, collectionId, eventsMigrated);
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("collection", collectionId);
                    body.put("targetTier", "WARM");
                    body.put("status", "COMPLETED");
                    body.put("eventsMigrated", eventsMigrated);
                    return http.jsonResponse(body);
                })
                .mapException(e -> {
                    log.error("[B10] WARM migration failed: tenant={} collection={}: {}",
                            tenantId, collectionId, e.getMessage(), e);
                    return new io.activej.http.HttpException("Tier migration failed: " + e.getMessage(), e);
                });
    }

    private Promise<HttpResponse> handleColdMigration(String tenantId, String collectionId) {
        if (archiveMigrationScheduler == null) {
            return Promise.of(http.errorResponse(503,
                    "COLD tier migration is not configured. Supply S3 archive credentials via S3_ARCHIVE_BUCKET."));
        }
        try {
            archiveMigrationScheduler.runMigrationCycle();
            log.info("[B10] COLD migration cycle triggered: tenant={} collection={}", tenantId, collectionId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("collection", collectionId);
            body.put("targetTier", "COLD");
            body.put("status", "SCHEDULED");
            body.put("eventsMigrated", 0);
            return Promise.of(http.jsonResponse(body));
        } catch (Exception e) {
            log.error("[B10] COLD migration trigger failed: tenant={} collection={}: {}",
                    tenantId, collectionId, e.getMessage(), e);
            return Promise.of(http.errorResponse(500, "COLD migration trigger failed: " + e.getMessage()));
        }
    }
}
