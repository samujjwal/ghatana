package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.governance.QuotaCheckResult;
import com.ghatana.datacloud.governance.TenantQuotaService;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.datacloud.spi.WriteIdempotencyStore;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles event append and query HTTP endpoints.
 *
 * <p>Extracted from {@code DataCloudHttpServer} to reduce the god-class size.
 *
 * @doc.type class
 * @doc.purpose Event append and query HTTP handlers
 * @doc.layer product
 * @doc.pattern Handler
 */
public class EventHandler {

    private static final Logger log = LoggerFactory.getLogger(EventHandler.class);

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private TraceSpanSupport traceSupport = TraceSpanSupport.disabled();
    private TenantQuotaService tenantQuotaService;
    /** DC-BE-002: Generic idempotency store for event append operations. */
    private WriteIdempotencyStore idempotencyStore;

    /** DC-P1-06: Deployment profile for production validation. */
    private String deploymentProfile = "local";

    /** DC-P1-06: Whether to enforce strict event envelope validation. */
    private boolean strictEventValidation = false;

    public EventHandler(DataCloudClient client, HttpHandlerSupport http) {
        this.client = client;
        this.http = http;
    }

    public EventHandler withTraceSupport(TraceSpanSupport traceSupport) {
        this.traceSupport = traceSupport != null ? traceSupport : TraceSpanSupport.disabled();
        return this;
    }

    public EventHandler withTenantQuotaService(TenantQuotaService tenantQuotaService) {
        this.tenantQuotaService = tenantQuotaService;
        return this;
    }

    /**
     * DC-BE-002: Attaches a generic idempotency store for event append operations.
     *
     * @param idempotencyStore the idempotency store
     * @return {@code this} for method chaining
     */
    public EventHandler withIdempotencyStore(WriteIdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
        return this;
    }

    /**
     * DC-P1-06: Sets the deployment profile for production validation.
     *
     * @param profile the deployment profile (e.g., "local", "production", "staging", "sovereign")
     * @return {@code this} for method chaining
     */
    public EventHandler withDeploymentProfile(String profile) {
        this.deploymentProfile = profile != null ? profile : "local";
        // DC-P1-06: Enable strict validation in production-like profiles
        this.strictEventValidation = isProductionLikeProfile(this.deploymentProfile);
        return this;
    }

    /**
     * DC-P1-06: Validates production requirements for event durability.
     * Throws IllegalStateException if production invariants are violated.
     */
    public void validateProductionRequirements() {
        if (!isProductionLikeProfile(deploymentProfile)) {
            return;
        }

        log.info("[EventHandler] Validating production requirements for profile '{}'", deploymentProfile);

        // DC-P1-06: Durable idempotency store is required in production
        if (idempotencyStore == null) {
            throw new IllegalStateException(
                "DC-P1-06: WriteIdempotencyStore is required in production/staging/sovereign profiles. " +
                "Event append idempotency must be durable across restarts.");
        }

        log.info("[EventHandler] Production requirements validated successfully for profile '{}'", deploymentProfile);
    }

    /**
     * DC-P1-06: Determines if the deployment profile requires production-like strictness.
     */
    private static boolean isProductionLikeProfile(String profile) {
        if (profile == null) return false;
        String lower = profile.trim().toLowerCase();
        return lower.equals("production") || lower.equals("staging") || lower.equals("sovereign");
    }

    /**
     * DC-P1-06: Canonical event envelope for production event append.
     * Rich envelope fields required for provenance, audit, replay, and governance.
     */
    public record EventEnvelope(
        String eventId,        // Unique event identifier (UUID)
        String type,           // Event type (required)
        String tenantId,       // Tenant identifier (enriched server-side in production)
        String workspaceId,    // Workspace scope
        String subject,        // Subject entity identifier
        String actor,          // Actor/principal who triggered the event
        String classification, // Event classification (public, sensitive, critical)
        Map<String, Object> policyContext, // Policy evaluation context
        String provenance,     // Event provenance/source
        String traceContext,   // Distributed trace context
        String correlationId,  // Correlation ID for distributed tracing
        String causationId,    // Causation ID for event sourcing
        Map<String, Object> payload, // Event payload (required)
        Instant timestamp      // Event timestamp (server-enriched in production)
    ) {
        /**
         * Validates the envelope according to DC-P1-06 requirements.
         * @return validation error message, or null if valid
         */
        public String validate(boolean strict) {
            if (eventId == null || eventId.isBlank()) {
                if (strict) return "eventId is required in strict mode";
            }
            if (type == null || type.isBlank()) {
                return "type is required";
            }
            if (payload == null || payload.isEmpty()) {
                return "payload is required";
            }
            if (strict) {
                if (actor == null || actor.isBlank()) {
                    return "actor is required in strict mode";
                }
                if (timestamp == null) {
                    return "timestamp is required in strict mode";
                }
            }
            return null; // valid
        }
    }

    /**
     * P0.5: Check tenant quota before event append operations.
     * Returns an error promise if quota is exceeded, otherwise null.
     */
    private Promise<HttpResponse> checkQuotaOrNull(String tenantId, String operationType, int resourceAmount) {
        if (tenantQuotaService == null) return null;
        QuotaCheckResult result = tenantQuotaService.checkQuota(tenantId, operationType, resourceAmount);
        if (!result.isAllowed()) {
            return Promise.of(http.errorResponse(429,
                "Quota exceeded: " + result.message() + " (quota=" + result.quotaValue()
                    + ", used=" + result.usedAmount() + ")"));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Promise<HttpResponse> handleAppendEvent(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "Tenant is required"));
        }

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        // DC-P1-06: In production, durable idempotency is mandatory
        String idempotencyKey = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));
        String operationScope = "events:append";
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            if (idempotencyStore == null && isProductionLikeProfile(deploymentProfile)) {
                log.error("[EventHandler] DC-P1-06: Idempotency requested but no durable store in production");
                return Promise.of(http.errorResponse(503,
                    "Idempotency service unavailable: durable store required in production"));
            }
            if (idempotencyStore != null) {
                var cached = idempotencyStore.get(tenantId, operationScope, idempotencyKey);
                if (cached.isPresent()) {
                    log.info("[DC-BE-002] Returning cached event append response for key={}", idempotencyKey);
                    return Promise.of(http.jsonResponse(cached.get()));
                }
            }
        }

        Promise<HttpResponse> quotaErr = checkQuotaOrNull(tenantId, "EVENT", 1);
        if (quotaErr != null) return quotaErr;

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
            request,
            tenantId,
            "datacloud.http.event.append",
            traceSupport.requestSpanId(request),
            Map.of());

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                Map<String, Object> eventData = http.objectMapper().readValue(body, Map.class);

                // DC-P1-06: Canonical event envelope validation
                String eventType = (String) eventData.get("type");
                if (eventType == null || eventType.isBlank()) {
                    return Promise.of(http.errorResponse(400, "type is required"));
                }

                Object payloadCandidate = eventData.containsKey("payload")
                    ? eventData.get("payload")
                    : eventData.get("data");
                if (!(payloadCandidate instanceof Map<?, ?> payloadMap)) {
                    return Promise.of(http.errorResponse(400, "payload must be a JSON object"));
                }

                Map<String, Object> payload = (Map<String, Object>) payloadMap;
                if (payload.isEmpty()) {
                    return Promise.of(http.errorResponse(400, "payload must not be empty"));
                }

                // DC-P0-01/DC-P0-02: Enrich server-owned fields BEFORE validation in production
                String eventId = (String) eventData.get("eventId");
                String actor = (String) eventData.get("actor");
                String correlationId = http.resolveCorrelationId(request);
                String traceContext = http.resolveTraceContext(request); // DC-P0-01: Extract trace context
                Instant timestamp = eventData.containsKey("timestamp")
                    ? Instant.parse((String) eventData.get("timestamp"))
                    : Instant.now();

                if (isProductionLikeProfile(deploymentProfile)) {
                    // In production, generate eventId if not provided
                    if (eventId == null || eventId.isBlank()) {
                        eventId = java.util.UUID.randomUUID().toString();
                    }
                    // In production, actor should be enriched from authenticated principal
                    // DC-P0-02: Resolve actor from authenticated principal, not "system" fallback
                    if (actor == null || actor.isBlank()) {
                        // Extract from authenticated principal attached by security filter
                        com.ghatana.platform.governance.security.Principal principal =
                            request.getAttachment(com.ghatana.platform.governance.security.Principal.class);
                        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
                            actor = principal.getName();
                        } else {
                            // DC-P1-03: Fail-closed — never silently assign "system" as actor in production.
                            // Event accountability requires an authenticated principal; reject the request.
                            log.error("[EventHandler] DC-P1-03: No authenticated principal for event append in production profile '{}'. Rejecting to preserve event accountability.", deploymentProfile);
                            return Promise.of(http.errorResponse(401, "Authenticated principal required for event append in production mode"));
                        }
                    }
                }
                
                // Make effectively final for lambda use
                final String finalEventId = eventId;
                final String finalActor = actor;

                // DC-P0-01/DC-P0-02: In production, enforce canonical event envelope with all required fields AFTER enrichment
                if (strictEventValidation) {
                    // Build canonical envelope for validation with correct field order
                    EventEnvelope envelope = new EventEnvelope(
                        finalEventId,
                        eventType,
                        tenantId,
                        (String) eventData.get("workspaceId"),
                        (String) eventData.get("subject"),
                        finalActor,
                        (String) eventData.get("classification"),
                        (Map<String, Object>) eventData.getOrDefault("policyContext", Map.of()),
                        (String) eventData.getOrDefault("provenance", "datacloud.launcher.event-handler"),
                        traceContext,           // DC-P0-01: traceContext (was missing)
                        correlationId,          // DC-P0-01: correlationId (was in wrong position)
                        (String) eventData.get("causationId"),
                        payload,
                        timestamp
                    );

                    String validationError = envelope.validate(true);
                    if (validationError != null) {
                        log.warn("[EventHandler] DC-P0-02: Event envelope validation failed in production profile '{}': {}",
                            deploymentProfile, validationError);
                        return Promise.of(http.errorResponse(400,
                            "Invalid event envelope in production mode: " + validationError));
                    }
                }

                // DC-P0-03: Build canonical event with all enriched envelope fields for persistence
                DataCloudClient.Event event = DataCloudClient.Event.builder()
                    .type(eventType)
                    .payload(payload)
                    .source("datacloud.launcher.event-handler")
                    .subjectType((String) eventData.get("subjectType")) // Optional from request
                    .subjectId((String) eventData.get("subject")) // Optional from request
                    .correlationId(correlationId) // DC-P0-03: Persist correlation ID
                    .causationId((String) eventData.get("causationId")) // DC-P0-03: Persist causation ID
                    .actor(finalActor) // DC-P0-03: Persist actor from authenticated principal
                    .classification((String) eventData.get("classification")) // DC-P0-03: Persist classification
                    .policyContext(serializePolicyContext(eventData.get("policyContext"))) // DC-P1-04: Safe serialization — policyContext may arrive as Map or String
                    .provenance((String) eventData.getOrDefault("provenance", "datacloud.launcher.event-handler")) // DC-P0-03: Persist provenance
                    .traceContext(traceContext) // DC-P0-03: Persist trace context
                    .headers(Map.of(
                        "eventId", finalEventId != null ? finalEventId : "",
                        "workspaceId", (String) eventData.getOrDefault("workspaceId", ""),
                        "tenantId", tenantId
                    ))
                    .timestamp(timestamp)
                    .build();

                return traceSupport.trace(
                    request,
                    tenantId,
                    "datacloud.event.store.append",
                    handlerSpan.spanId(),
                    Map.of("event.type", eventType, "event.id", finalEventId != null ? finalEventId : "auto"),
                    () -> client.appendEvent(tenantId, event))
                    .map(offset -> {
                        Map<String, Object> responseBody = new LinkedHashMap<>();
                        responseBody.put("offset", offset.value());
                        responseBody.put("type", eventType);
                        responseBody.put("eventType", eventType);
                        if (finalEventId != null) {
                            responseBody.put("eventId", finalEventId);
                        }
                        responseBody.put("timestamp", timestamp.toString());
                        if (correlationId != null) {
                            responseBody.put("correlationId", correlationId);
                        }

                        // DC-BE-002: Store idempotency response
                        if (idempotencyStore != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
                            idempotencyStore.put(tenantId, operationScope, idempotencyKey, responseBody);
                        }
                        return http.jsonResponse(responseBody);
                    })
                    .then(
                        result -> Promise.of(result),
                        err -> {
                            log.error("[EventHandler] DC-BE-003: Event append to client failed for tenant={}: {}",
                                tenantId, err.getMessage(), err);
                            return Promise.of(http.errorResponse(500, "Event append failed: " + err.getMessage()));
                        });
            } catch (Exception e) {
                log.error("Error appending event", e);
                return Promise.of(http.errorResponse(400, "Invalid event data: " + e.getMessage()));
            }
        }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    /**
     * DC-P1-04: Safely serializes a policyContext value to a JSON string.
     * The value may arrive as a JSON-parsed {@code Map<String,Object>} or already as a {@code String}.
     * {@code null} values are propagated as {@code null}.
     */
    private String serializePolicyContext(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            return s;
        }
        try {
            return http.objectMapper().writeValueAsString(raw);
        } catch (Exception e) {
            log.warn("[EventHandler] DC-P1-04: Failed to JSON-serialize policyContext ({}), using toString()", raw.getClass().getSimpleName(), e);
            return raw.toString();
        }
    }

    public Promise<HttpResponse> handleQueryEvents(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        // P1-3: Support both offset-based and timestamp-based pagination
        String fromParam = request.getQueryParameter("from");
        String fromTimestampParam = request.getQueryParameter("fromTimestamp");
        int fromOffset = 0;
        Instant fromTimestamp = null;

        if (fromTimestampParam != null && !fromTimestampParam.isBlank()) {
            // P1-3: Use timestamp-based pagination if provided
            try {
                fromTimestamp = Instant.parse(fromTimestampParam.trim());
                log.debug("[P1-3] Using timestamp-based pagination fromTimestamp={}", fromTimestamp);
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Invalid 'fromTimestamp' parameter: must be ISO-8601 format (e.g., 2024-01-01T00:00:00Z)"));
            }
        } else if (fromParam != null && !fromParam.isBlank()) {
            // Fallback to offset-based pagination
            try {
                fromOffset = Integer.parseInt(fromParam.trim());
            } catch (NumberFormatException e) {
                return Promise.of(http.errorResponse(400, "Invalid 'from' parameter: must be an integer"));
            }
        }

        ApiInputValidator.LimitResult limitResult = ApiInputValidator.validateLimit(request.getQueryParameter("limit"), 100);
        if (!limitResult.isValid()) return Promise.of(http.errorResponse(400, limitResult.getError().orElseThrow()));

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.event.query",
                traceSupport.requestSpanId(request),
                Map.of());

        String eventType = request.getQueryParameter("type");
        final int finalFromOffset = fromOffset;
        final Instant finalFromTimestamp = fromTimestamp;
        DataCloudClient.Offset queryOffset = DataCloudClient.Offset.of(Math.max(0, finalFromOffset));
        DataCloudClient.EventQuery query = new DataCloudClient.EventQuery(
            eventType != null ? List.of(eventType) : List.of(),
            null,
            null,
            queryOffset,
            limitResult.getValue());

        return traceSupport.trace(
            request,
            tenantId,
            "datacloud.event.store.query",
            handlerSpan.spanId(),
            eventType == null 
                ? (fromTimestamp != null ? Map.of("fromTimestamp", finalFromTimestamp.toString()) : Map.of("fromOffset", finalFromOffset))
                : (fromTimestamp != null ? Map.of("fromTimestamp", finalFromTimestamp.toString(), "event.type", eventType) : Map.of("fromOffset", finalFromOffset, "event.type", eventType)),
            () -> client.queryEvents(tenantId, query))
            .map(events -> {
                var filtered = events.stream()
                    .filter(e -> finalFromTimestamp == null || e.timestamp().isAfter(finalFromTimestamp))
                    .limit(limitResult.getValue())
                    .toList();

                var eventResponses = new java.util.ArrayList<Map<String, Object>>();
                for (int i = 0; i < filtered.size(); i++) {
                    var e = filtered.get(i);
                    long eventOffset = finalFromOffset + i;
                    String eventOffsetHeader = e.headers().get("_x_dc_offset");
                    if (eventOffsetHeader != null) {
                        try {
                            eventOffset = Long.parseLong(eventOffsetHeader);
                        } catch (NumberFormatException ignored) {
                            // Keep compatibility fallback offset if header is not numeric.
                        }
                    }
                    eventResponses.add(Map.of(
                        "offset", eventOffset,
                        "type", e.type(),
                        "payload", e.payload(),
                        "timestamp", e.timestamp().toString()
                    ));
                }

                // P1-3: Include pagination metadata for both offset and timestamp modes
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("events", eventResponses);
                response.put("count", filtered.size());
                response.put("tenantId", tenantId);
                response.put("timestamp", Instant.now().toString());
                
                if (finalFromTimestamp != null) {
                    // Timestamp-based pagination metadata
                    response.put("fromTimestamp", finalFromTimestamp.toString());
                    if (!filtered.isEmpty()) {
                        response.put("nextTimestamp", filtered.get(filtered.size() - 1).timestamp().toString());
                    }
                } else {
                    // Offset-based pagination metadata
                    response.put("fromOffset", finalFromOffset);
                    response.put("nextOffset", (long)(finalFromOffset + filtered.size()));
                }

                return http.jsonResponse(response);
            }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }

    public Promise<HttpResponse> handleGetEventByOffset(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        String offsetParam = request.getPathParameter("offset");
        if (offsetParam == null || offsetParam.isBlank()) {
            return Promise.of(http.errorResponse(400, "offset path parameter is required"));
        }

        int offset;
        try {
            offset = Integer.parseInt(offsetParam.trim());
        } catch (NumberFormatException e) {
            return Promise.of(http.errorResponse(400, "Invalid offset parameter: must be an integer"));
        }

        TraceSpanSupport.TraceSpanScope handlerSpan = traceSupport.startSpan(
                request,
                tenantId,
                "datacloud.http.event.get_by_offset",
                traceSupport.requestSpanId(request),
                Map.of("offset", offset));

        return traceSupport.trace(
            request,
            tenantId,
            "datacloud.event.store.query",
            handlerSpan.spanId(),
            Map.of("offset", offset),
            () -> client.queryEvents(tenantId, DataCloudClient.EventQuery.all()))
            .map(events -> {
                if (offset < 0 || offset >= events.size()) {
                    return http.errorResponse(404, "Event not found at offset: " + offset);
                }
                var e = events.get(offset);
                return http.jsonResponse(Map.of(
                    "offset", (long)offset,
                    "type", e.type(),
                    "payload", e.payload(),
                    "timestamp", e.timestamp().toString()
                ));
            }).whenComplete((response, error) -> traceSupport.finish(handlerSpan, response, error));
    }
}
