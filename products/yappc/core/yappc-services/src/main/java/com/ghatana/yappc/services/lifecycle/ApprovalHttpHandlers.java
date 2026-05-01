package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;

/**
 * @doc.type class
 * @doc.purpose Encapsulates lifecycle approval HTTP request handling for listing and deciding approval requests.
 * @doc.layer product
 * @doc.pattern Handler
 */
final class ApprovalHttpHandlers {

    private static final HttpHeader TENANT_HEADER      = HttpHeaders.of("X-Tenant-Id");
    private static final HttpHeader IDEMPOTENCY_HEADER = HttpHeaders.of("Idempotency-Key");

    /** Replay window: successful approve/reject responses are cached for 24 hours. */
    private static final Duration REPLAY_WINDOW = Duration.ofHours(24);

    private final HumanApprovalService humanApprovalService;
    private final ObjectMapper objectMapper;
    private final IdempotencyStore idempotencyStore;

    ApprovalHttpHandlers(HumanApprovalService humanApprovalService, ObjectMapper objectMapper) {
        this(humanApprovalService, objectMapper, new InMemoryIdempotencyStore(REPLAY_WINDOW));
    }

    ApprovalHttpHandlers(HumanApprovalService humanApprovalService, ObjectMapper objectMapper, DataSource dataSource) {
        this(humanApprovalService, objectMapper, new JdbcIdempotencyStore(dataSource, REPLAY_WINDOW));
    }

    ApprovalHttpHandlers(
            HumanApprovalService humanApprovalService,
            ObjectMapper objectMapper,
            IdempotencyStore idempotencyStore) {
        this.humanApprovalService = Objects.requireNonNull(humanApprovalService, "humanApprovalService");
        this.objectMapper         = Objects.requireNonNull(objectMapper, "objectMapper");
        this.idempotencyStore     = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
    }

    Promise<HttpResponse> listPending(HttpRequest request) {
        String tenantId = extractTenantId(request);
        if (tenantId == null) {
            return Promise.of(badRequest("Missing required X-Tenant-Id header"));
        }

        try {
            String json = objectMapper.writeValueAsString(humanApprovalService.allPending(tenantId));
            return HttpResponse.ok200().withJson(json).toPromise();
        } catch (Exception exception) {
            return Promise.of(HttpResponse.ofCode(500).withPlainText("Serialization error").build());
        }
    }

    Promise<HttpResponse> create(HttpRequest request) {
        String tenantId = extractTenantId(request);
        if (tenantId == null) {
            return Promise.of(badRequest("Missing required X-Tenant-Id header"));
        }

        return request.loadBody().then(body -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8), Map.class);

                String projectId = (String) payload.get("projectId");
                String requestingAgentId = (String) payload.get("requestingAgentId");
                String approvalTypeStr = (String) payload.get("approvalType");

                if (projectId == null || projectId.isBlank()) {
                    return Promise.of(badRequest("Missing required field: projectId"));
                }
                if (approvalTypeStr == null || approvalTypeStr.isBlank()) {
                    return Promise.of(badRequest("Missing required field: approvalType"));
                }

                ApprovalRequest.ApprovalType approvalType;
                try {
                    approvalType = ApprovalRequest.ApprovalType.valueOf(approvalTypeStr);
                } catch (IllegalArgumentException e) {
                    return Promise.of(badRequest("Invalid approvalType: " + approvalTypeStr));
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> contextMap = payload.containsKey("context")
                        ? (Map<String, Object>) payload.get("context")
                        : Map.of();
                String fromPhase = (String) contextMap.getOrDefault("fromPhase", "");
                String toPhase = (String) contextMap.getOrDefault("toPhase", "");
                String blockReason = (String) contextMap.getOrDefault("blockReason", "");
                @SuppressWarnings("unchecked")
                List<String> unmetCriteria = contextMap.containsKey("unmetCriteria")
                        ? (List<String>) contextMap.get("unmetCriteria")
                        : List.of();
                @SuppressWarnings("unchecked")
                List<String> missingArtifacts = contextMap.containsKey("missingArtifacts")
                        ? (List<String>) contextMap.get("missingArtifacts")
                        : List.of();

                ApprovalRequest.ApprovalContext context = new ApprovalRequest.ApprovalContext(
                        fromPhase, toPhase, blockReason, unmetCriteria, missingArtifacts);

                return humanApprovalService.requestApproval(
                        tenantId, projectId, requestingAgentId, approvalType, context)
                        .map(created -> {
                            try {
                                return HttpResponse.ofCode(201)
                                        .withJson(objectMapper.writeValueAsString(created))
                                        .build();
                            } catch (Exception ex) {
                                return HttpResponse.ofCode(500).withPlainText("Serialization error").build();
                            }
                        });
            } catch (Exception exception) {
                return Promise.of(badRequest(exception.getMessage()));
            }
        });
    }

    Promise<HttpResponse> getById(HttpRequest request, String requestId) {
        String tenantId = extractTenantId(request);
        if (tenantId == null) {
            return Promise.of(badRequest("Missing required X-Tenant-Id header"));
        }

        Optional<ApprovalRequest> found = humanApprovalService.findById(tenantId, requestId);
        if (found.isEmpty()) {
            return Promise.of(HttpResponse.ofCode(404)
                    .withJson(errorJson("Approval request not found: " + requestId))
                    .build());
        }

        try {
            return Promise.of(HttpResponse.ok200()
                    .withJson(objectMapper.writeValueAsString(found.get()))
                    .build());
        } catch (Exception exception) {
            return Promise.of(HttpResponse.ofCode(500).withPlainText("Serialization error").build());
        }
    }

    Promise<HttpResponse> approve(HttpRequest request, String requestId) {
        return decide(request, requestId, true);
    }

    Promise<HttpResponse> reject(HttpRequest request, String requestId) {
        return decide(request, requestId, false);
    }

    private Promise<HttpResponse> decide(HttpRequest request, String requestId, boolean approved) {
        String tenantId = extractTenantId(request);
        if (tenantId == null) {
            return Promise.of(badRequest("Missing required X-Tenant-Id header"));
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Promise.of(HttpResponse.ofCode(422)
                    .withJson(errorJson("Missing required Idempotency-Key header"))
                    .build());
        }

        // Scope the idempotency key to tenant + request + action to prevent cross-tenant replay.
        String storeKey = tenantId + ":" + requestId + ":" + (approved ? "approve" : "reject")
                + ":" + idempotencyKey;

        Optional<String> replay = idempotencyStore.get(storeKey);
        if (replay.isPresent()) {
            return Promise.of(HttpResponse.ok200()
                    .withHeader(HttpHeaders.of("X-Idempotent-Replayed"), "true")
                    .withJson(replay.get())
                    .build());
        }

        return request.loadBody().then(body -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(
                        body.getString(StandardCharsets.UTF_8), Map.class);
                String decidedBy = (String) payload.getOrDefault("decidedBy", "unknown");

                Promise<ApprovalRequest> decision = approved
                        ? humanApprovalService.approve(tenantId, requestId, decidedBy)
                        : humanApprovalService.reject(tenantId, requestId, decidedBy);

                return decision.map(result -> {
                    try {
                        String responseBody = objectMapper.writeValueAsString(result);
                        idempotencyStore.put(storeKey, responseBody);
                        return HttpResponse.ok200()
                                .withJson(responseBody)
                                .build();
                    } catch (Exception exception) {
                        return HttpResponse.ofCode(500).withPlainText("Serialization error").build();
                    }
                }).then(
                        Promise::of,
                        error -> Promise.of(HttpResponse.ofCode(409)
                                .withJson(errorJson(error.getMessage()))
                                .build())
                );
            } catch (IllegalArgumentException | IllegalStateException domainError) {
                // thrown synchronously by service (e.g. request not found, already decided)
                return Promise.of(HttpResponse.ofCode(409)
                        .withJson(errorJson(domainError.getMessage()))
                        .build());
            } catch (Exception exception) {
                return Promise.of(badRequest(exception.getMessage()));
            }
        });
    }

    private String extractTenantId(HttpRequest request) {
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantId;
    }

    private HttpResponse badRequest(String message) {
        return HttpResponse.ofCode(400)
                .withJson(errorJson(message))
                .build();
    }

    private String errorJson(String message) {
        return "{\"error\":\"" + message + "\"}";
    }

    // ─── Idempotency support ─────────────────────────────────────────────────

    /**
     * Pluggable idempotency store contract.
     *
     * <p>Implementations must be thread-safe. They must return the cached response body
     * within the replay window and expire entries automatically after that window closes.
     */
    interface IdempotencyStore {
        /** Returns the cached response body for {@code key}, if present and not expired. */
        Optional<String> get(String key);

        /** Stores {@code responseBody} under {@code key} for the configured replay window. */
        void put(String key, String responseBody);
    }

    /**
     * In-memory idempotency store with TTL-based expiry.
     *
     * <p>Suitable for single-node deployments. For multi-node deployments, replace with a
     * Redis-backed implementation that delegates to
     * {@code platform:java:cache} (tracked in F-Y048 follow-up).
     */
    static final class InMemoryIdempotencyStore implements IdempotencyStore {

        private record Entry(String responseBody, Instant expiresAt) {}

        private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
        private final Duration replayWindow;

        InMemoryIdempotencyStore(Duration replayWindow) {
            this.replayWindow = Objects.requireNonNull(replayWindow, "replayWindow");
        }

        @Override
        public Optional<String> get(String key) {
            Entry entry = store.get(key);
            if (entry == null) {
                return Optional.empty();
            }
            if (Instant.now().isAfter(entry.expiresAt())) {
                store.remove(key);
                return Optional.empty();
            }
            return Optional.of(entry.responseBody());
        }

        @Override
        public void put(String key, String responseBody) {
            store.put(key, new Entry(responseBody, Instant.now().plus(replayWindow)));
        }
    }
}
