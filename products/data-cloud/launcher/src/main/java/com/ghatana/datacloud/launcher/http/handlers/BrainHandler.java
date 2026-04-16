package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.brain.BrainConfig;
import com.ghatana.datacloud.brain.BrainContext;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.EntityRecord;
import com.ghatana.datacloud.attention.AttentionManager;
import com.ghatana.datacloud.workspace.GlobalWorkspace;
import com.ghatana.datacloud.workspace.SpotlightItem;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles brain HTTP endpoints (DC-6).
 *
 * <p>Covers health, config, stats, workspace, attention, patterns, and salience.
 * The SSE workspace stream ({@code /api/v1/brain/workspace/stream}) remains in
 * {@code DataCloudHttpServer} due to its tight coupling with SSE infrastructure.
 *
 * @doc.type class
 * @doc.purpose Brain HTTP handlers (DC-6)
 * @doc.layer product
 * @doc.pattern Handler
 */
public class BrainHandler {

    private static final Logger log = LoggerFactory.getLogger(BrainHandler.class);

    private final DataCloudBrain brain;
    private final HttpHandlerSupport http;

    public BrainHandler(DataCloudBrain brain, HttpHandlerSupport http) {
        this.brain = brain;
        this.http = http;
    }

    public Promise<HttpResponse> handleBrainHealth(HttpRequest request) {
        if (brain == null) {
            return Promise.of(http.errorResponse(503, "Brain not available in this deployment"));
        }
        return brain.health()
            .map(h -> {
                Map<String, Object> componentsMap = new LinkedHashMap<>();
                h.getComponents().forEach((k, v) -> componentsMap.put(k, v.name()));
                return http.jsonResponse(Map.of(
                    "status",     h.getStatus().name(),
                    "components", componentsMap,
                    "messages",   h.getMessages(),
                    "timestamp",  Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[DC-6] brain health check failed: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(503, "Brain health check failed: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleBrainConfig(HttpRequest request) {
        if (brain == null) {
            return Promise.of(http.errorResponse(503, "Brain not available in this deployment"));
        }
        try {
            BrainConfig cfg = brain.getConfig();
            return Promise.of(http.jsonResponse(Map.of(
                "brainId",          cfg.getBrainId(),
                "name",             cfg.getName(),
                "learningEnabled",  cfg.isLearningEnabled(),
                "reflexesEnabled",  cfg.isReflexesEnabled(),
                "salienceThreshold", cfg.getSalienceThreshold(),
                "timestamp",        Instant.now().toString()
            )));
        } catch (Exception e) {
            log.error("[DC-6] brain config retrieval failed: {}", e.getMessage(), e);
            return Promise.of(http.errorResponse(500, "Failed to retrieve brain config: " + e.getMessage()));
        }
    }

    public Promise<HttpResponse> handleBrainStats(HttpRequest request) {
        if (brain == null) {
            return Promise.of(http.errorResponse(503, "Brain not available in this deployment"));
        }
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        BrainContext ctx = BrainContext.forTenant(tenantId);
        return brain.getStats(ctx)
            .map(s -> http.jsonResponse(Map.of(
                "totalRecordsProcessed", s.getTotalRecordsProcessed(),
                "activePatterns",        s.getActivePatterns(),
                "activeRules",           s.getActiveRules(),
                "hotTierRecords",        s.getHotTierRecords(),
                "warmTierRecords",       s.getWarmTierRecords(),
                "avgProcessingTimeMs",   s.getAvgProcessingTimeMs(),
                "uptimeSeconds",         s.getUptimeSeconds(),
                "tenantId",              tenantId,
                "timestamp",             Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[DC-6] brain stats retrieval failed: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(503, "Failed to retrieve brain stats: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleBrainWorkspace(HttpRequest request) {
        if (brain == null) {
            return Promise.of(http.errorResponse(503, "Brain not available in this deployment"));
        }
        BrainConfig cfg = brain.getConfig();
        return Promise.of(http.jsonResponse(Map.of(
            "status",    "active",
            "brainId",   cfg.getBrainId(),
            "note",      "Detailed spotlight items available via GET /api/v1/brain/stats",
            "timestamp", Instant.now().toString()
        )));
    }

    public Promise<HttpResponse> handleBrainAttentionElevate(HttpRequest request) {
        if (brain == null) {
            return Promise.of(http.errorResponse(503, "Brain not available in this deployment"));
        }
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        Optional<AttentionManager> amOpt = brain.getAttentionManager();
        if (amOpt.isEmpty()) {
            return Promise.of(http.errorResponse(503,
                "Attention manager not available for this brain implementation"));
        }
        AttentionManager am = amOpt.get();

        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = http.objectMapper().readValue(body.asArray(), Map.class);
                    String id       = (String) req.getOrDefault("id",
                        java.util.UUID.randomUUID().toString());
                    String content  = (String) req.getOrDefault("content", "");
                    String reason   = (String) req.getOrDefault("reason", "manual-api-elevation");
                    boolean emergency = Boolean.TRUE.equals(req.get("emergency"));

                    EntityRecord record = EntityRecord.builder()
                        .id(java.util.UUID.fromString(id))
                        .tenantId(tenantId)
                        .collectionName("api-elevation")
                        .data(Map.of("content", content))
                        .build();

                    return am.elevate(record, reason, emergency)
                        .map(result -> http.jsonResponse(Map.of(
                            "elevated",  result.wasElevated(),
                            "emergency", result.wasEmergency(),
                            "action",    result.getAction().name(),
                            "recordId",  id,
                            "reason",    reason,
                            "tenantId",  tenantId,
                            "timestamp", Instant.now().toString()
                        )));
                } catch (IllegalArgumentException e) {
                    return Promise.of(http.errorResponse(400,
                        "Invalid 'id' — must be a valid UUID: " + e.getMessage()));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400,
                        "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[DC-6] attention elevate failed: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(500,
                    "Attention elevation failed: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleBrainAttentionThresholds(HttpRequest request) {
        if (brain == null) {
            return Promise.of(http.errorResponse(503, "Brain not available in this deployment"));
        }
        Optional<AttentionManager> amOpt = brain.getAttentionManager();
        BrainConfig cfg = brain.getConfig();
        if (amOpt.isEmpty()) {
            return Promise.of(http.jsonResponse(Map.of(
                "elevationThreshold", AttentionManager.DEFAULT_ELEVATION_THRESHOLD,
                "emergencyThreshold", AttentionManager.DEFAULT_EMERGENCY_THRESHOLD,
                "salienceThreshold",  (double) cfg.getSalienceThreshold(),
                "source",             "defaults",
                "timestamp",          Instant.now().toString()
            )));
        }
        AttentionManager.AttentionStats stats = amOpt.get().getStats();
        return Promise.of(http.jsonResponse(Map.of(
            "elevationThreshold", stats.elevationThreshold(),
            "emergencyThreshold", stats.emergencyThreshold(),
            "salienceThreshold",  (double) cfg.getSalienceThreshold(),
            "totalProcessed",     stats.totalProcessed(),
            "elevatedCount",      stats.elevatedCount(),
            "emergencyCount",     stats.emergencyCount(),
            "elevationRate",      stats.elevationRate(),
            "timestamp",          Instant.now().toString()
        )));
    }

    public Promise<HttpResponse> handleBrainAttentionThresholdsUpdate(HttpRequest request) {
        if (brain == null) {
            return Promise.of(http.errorResponse(503, "Brain not available in this deployment"));
        }
        return request.loadBody()
            .map(body -> http.jsonResponse(Map.of(
                "acknowledged", true,
                "note",         "Threshold changes require restarting the brain with updated BrainConfig. "
                              + "Set DATACLOUD_BRAIN_ELEVATION_THRESHOLD and DATACLOUD_BRAIN_EMERGENCY_THRESHOLD env vars.",
                "timestamp",    Instant.now().toString()
            )));
    }

    public Promise<HttpResponse> handleBrainPatterns(HttpRequest request) {
        if (brain == null) {
            return Promise.of(http.errorResponse(503, "Brain not available in this deployment"));
        }
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        int limit = Math.min(HttpHandlerSupport.parseLimitParam(request.getQueryParameter("limit"), 100), 1000);
        BrainContext ctx = BrainContext.forTenant(tenantId);

        return brain.listPatterns(limit, ctx)
            .map(patterns -> {
                List<Map<String, Object>> patternList = patterns.stream()
                    .map(p -> {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("id",          p.getId());
                        entry.put("name",        p.getName() != null ? p.getName() : "");
                        entry.put("type",        p.getType() != null ? p.getType().name() : "UNKNOWN");
                        entry.put("description", p.getDescription() != null ? p.getDescription() : "");
                        entry.put("confidence",  p.getConfidence());
                        entry.put("observations", p.getObservationCount());
                        entry.put("discoveredAt", p.getDiscoveredAt().toString());
                        entry.put("updatedAt",   p.getUpdatedAt().toString());
                        return Map.copyOf(entry);
                    })
                    .toList();
                return http.jsonResponse(Map.of(
                    "patterns",  patternList,
                    "count",     patternList.size(),
                    "limit",     limit,
                    "tenantId",  tenantId,
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[DC-6] list patterns failed: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(500,
                    "Failed to list patterns: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleBrainPatternsMatch(HttpRequest request) {
        if (brain == null) {
            return Promise.of(http.errorResponse(503, "Brain not available in this deployment"));
        }
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        BrainContext ctx = BrainContext.forTenant(tenantId);

        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = http.objectMapper().readValue(body.asArray(), Map.class);
                    String rawId   = (String) req.get("id");
                    java.util.UUID id = rawId != null
                        ? java.util.UUID.fromString(rawId)
                        : java.util.UUID.randomUUID();
                    String content = (String) req.getOrDefault("content", "");
                    String type    = (String) req.getOrDefault("type", "QUERY");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> attributes =
                        (Map<String, Object>) req.getOrDefault("attributes", Map.of());

                    EntityRecord record = EntityRecord.builder()
                        .id(id)
                        .tenantId(tenantId)
                        .collectionName("api-match")
                        .data(Map.of("content", content, "type", type))
                        .metadata(attributes)
                        .build();

                    return brain.matchPatterns(record, ctx)
                        .map(matches -> {
                            List<Map<String, Object>> matchList = matches.stream()
                                .map(m -> {
                                    Map<String, Object> entry = new LinkedHashMap<>();
                                    if (m.getPattern() != null) {
                                        entry.put("patternId",   m.getPattern().getId());
                                        entry.put("patternName", m.getPattern().getName());
                                    }
                                    entry.put("score",       m.getScore());
                                    entry.put("confidence",  m.getConfidence());
                                    entry.put("explanation", m.getExplanation() != null
                                        ? m.getExplanation() : "");
                                    return Map.copyOf(entry);
                                })
                                .toList();
                            return http.jsonResponse(Map.of(
                                "recordId",  id.toString(),
                                "matches",   matchList,
                                "count",     matchList.size(),
                                "tenantId",  tenantId,
                                "timestamp", Instant.now().toString()
                            ));
                        });
                } catch (IllegalArgumentException e) {
                    return Promise.of(http.errorResponse(400,
                        "Invalid 'id' — must be a valid UUID: " + e.getMessage()));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400,
                        "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[DC-6] pattern match failed: {}", e.getMessage(), e);
                return Promise.of(http.errorResponse(500,
                    "Pattern match failed: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleBrainSalience(HttpRequest request) {
        if (brain == null) {
            return Promise.of(http.errorResponse(503, "Brain not available in this deployment"));
        }
        String itemId = request.getPathParameter("itemId");
        if (itemId == null || itemId.isBlank()) {
            return Promise.of(http.errorResponse(400, "itemId path parameter is required"));
        }
        Optional<GlobalWorkspace> wsOpt = brain.getWorkspace();
        if (wsOpt.isEmpty()) {
            return Promise.of(http.errorResponse(503,
                "Workspace not available for this brain implementation"));
        }
        Optional<SpotlightItem> item = wsOpt.get().get(itemId);
        if (item.isEmpty()) {
            return Promise.of(http.errorResponse(404,
                "Item not found in workspace spotlight: " + itemId));
        }
        SpotlightItem si = item.get();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("itemId",        itemId);
        resp.put("salienceScore", si.getSalienceScore().getScore());
        resp.put("isHigh",        si.getSalienceScore().isHigh());
        resp.put("isEmergency",   si.isEmergency());
        resp.put("priority",      si.getPriority());
        resp.put("summary",       si.getSummary() != null ? si.getSummary() : "");
        resp.put("tenantId",      si.getTenantId());
        resp.put("spotlightedAt", si.getSpotlightedAt().toString());
        resp.put("timestamp",     Instant.now().toString());
        return Promise.of(http.jsonResponse(Map.copyOf(resp)));
    }
}
