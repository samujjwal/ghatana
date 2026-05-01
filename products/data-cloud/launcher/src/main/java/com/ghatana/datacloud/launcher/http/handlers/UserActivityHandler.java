package com.ghatana.datacloud.launcher.http.handlers;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * HTTP handler for user-activity tracking endpoints.
 *
 * <p>Exposes a lightweight, in-memory ring buffer for recent user activity so that
 * the frontend's {@code IntelligentHub} and {@code SqlWorkspacePage} components can
 * display per-session activity without a persistent store.
 *
 * <p>Routes served:
 * <ul>
 *   <li>{@code GET  /api/v1/user-activity/recent} — return recent activities and continue-working items</li>
 *   <li>{@code POST /api/v1/user-activity/log}    — append a new activity entry</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose In-memory user-activity tracking for the IntelligentHub frontend
 * @doc.layer product
 * @doc.pattern Handler
 */
public class UserActivityHandler {

    private static final Logger log = LoggerFactory.getLogger(UserActivityHandler.class);

    private static final int MAX_ACTIVITIES = 50;

    private final HttpHandlerSupport http;

    /** Ring buffer of logged activity entries, newest first. */
    private final ConcurrentLinkedDeque<Map<String, Object>> activities = new ConcurrentLinkedDeque<>();

    /**
     * @param http shared HTTP helper
     */
    public UserActivityHandler(HttpHandlerSupport http) {
        this.http = http;
    }

    /**
     * {@code GET /api/v1/user-activity/recent}
     *
     * <p>Returns up to 20 recent activity entries and derives a "continue working" list
     * from the most recently accessed resource paths.
     */
    public Promise<HttpResponse> handleGetRecentActivity(HttpRequest request) {
        List<Map<String, Object>> recent = new ArrayList<>(activities);
        if (recent.size() > 20) {
            recent = recent.subList(0, 20);
        }

        // Derive continue-working items from unique resource paths in the ring buffer
        List<Map<String, Object>> continueWorking = new ArrayList<>();
        Set<String> seenPaths = new LinkedHashSet<>();
        for (Map<String, Object> entry : recent) {
            String resourceId = (String) entry.get("resourceId");
            if (resourceId != null && !resourceId.isBlank() && seenPaths.add(resourceId)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", entry.get("id"));
                item.put("name", entry.getOrDefault("target", resourceId));
                item.put("type", mapResourceTypeToWorkingType((String) entry.get("resourceType")));
                item.put("lastAccessed", entry.get("timestamp"));
                item.put("path", resourceId);
                continueWorking.add(item);
                if (continueWorking.size() >= 5) break;
            }
        }

        return Promise.of(http.jsonResponse(Map.of(
            "activities",      recent,
            "continueWorking", continueWorking,
            "count",           recent.size(),
            "timestamp",       Instant.now().toString()
        )));
    }

    /**
     * {@code POST /api/v1/user-activity/log}
     *
     * <p>Accepts a JSON body with {@code action}, {@code target}, {@code type},
     * and optional {@code resourceType} / {@code resourceId} fields.
     * Appends the entry to the ring buffer and trims to {@link #MAX_ACTIVITIES}.
     */
    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleLogActivity(HttpRequest request) {
        return request.loadBody().then(body -> {
            try {
                Map<String, Object> payload = http.objectMapper().readValue(
                    body.getString(StandardCharsets.UTF_8), Map.class);

                String action = (String) payload.get("action");
                String target = (String) payload.get("target");
                String type   = (String) payload.get("type");

                if (action == null || action.isBlank()) {
                    return Promise.of(http.errorResponse(400, "'action' field is required"));
                }
                if (target == null || target.isBlank()) {
                    return Promise.of(http.errorResponse(400, "'target' field is required"));
                }
                if (type == null || type.isBlank()) {
                    return Promise.of(http.errorResponse(400, "'type' field is required"));
                }

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id",           UUID.randomUUID().toString());
                entry.put("action",       action);
                entry.put("target",       target);
                entry.put("type",         type);
                entry.put("resourceType", payload.get("resourceType"));
                entry.put("resourceId",   payload.get("resourceId"));
                entry.put("timestamp",    Instant.now().toString());

                activities.addFirst(entry);
                while (activities.size() > MAX_ACTIVITIES) {
                    activities.pollLast();
                }

                log.debug("[user-activity] logged action={} target={} type={}", action, target, type);

                return Promise.of(http.jsonResponse(Map.of(
                    "ok",        true,
                    "id",        entry.get("id"),
                    "timestamp", entry.get("timestamp")
                )));
            } catch (Exception e) {
                log.warn("[user-activity] invalid log request: {}", e.getMessage());
                return Promise.of(http.errorResponse(400, "Invalid JSON body: " + e.getMessage()));
            }
        });
    }

    private static String mapResourceTypeToWorkingType(String resourceType) {
        if (resourceType == null) return "collection";
        return switch (resourceType.toLowerCase()) {
            case "workflow", "pipeline" -> "workflow";
            case "query"               -> "query";
            case "dashboard", "insight" -> "insight";
            default                    -> "collection";
        };
    }
}
