package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical JSON response envelope for all Data-Cloud HTTP endpoints.
 *
 * <h2>Shape (success)</h2>
 * <pre>{@code
 * {
 *   "data": { ... },
 *   "meta": {
 *     "requestId": "uuid",
 *     "tenantId":  "acme",
 *     "timestamp": "2026-03-23T10:00:00Z",
 *     "apiVersion": "v1"
 *   }
 * }
 * }</pre>
 *
 * <h2>Shape (error)</h2>
 * <pre>{@code
 * {
 *   "error": {
 *     "code": "NOT_FOUND",
 *     "message": "Entity not found",
 *     "details": { ... }
 *   },
 *   "meta": {
 *     "requestId": "uuid",
 *     "tenantId":  "acme",
 *     "timestamp": "2026-03-23T10:00:00Z",
 *     "apiVersion": "v1"
 *   }
 * }
 * }</pre>
 *
 * <h2>AI-Enriched Shape</h2>
 * When an AI/ML operation produces confidence metadata, the {@code ai} block is included:
 * <pre>{@code
 * {
 *   "data": { ... },
 *   "ai": {
 *     "confidence": 0.92,
 *     "model":      "datacloud-suggest-v2",
 *     "reasons":    ["recency", "relevance"],
 *     "fallback":   false
 *   },
 *   "meta": { ... }
 * }
 * }</pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Success
 * String json = ApiResponse.success(data, tenantId, requestId).toJson(mapper);
 *
 * // Error
 * String json = ApiResponse.error("NOT_FOUND", "Entity not found", tenantId, requestId).toJson(mapper);
 *
 * // AI-enriched
 * String json = ApiResponse.success(data, tenantId, requestId)
 *     .withAiMeta(confidence, model, reasons, fallback)
 *     .toJson(mapper);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Canonical JSON response envelope for Data-Cloud HTTP API
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiResponse {

    private static final String API_VERSION = "v1";

    @JsonProperty("data")
    private final Object data;

    @JsonProperty("error")
    private final ErrorBody error;

    @JsonProperty("ai")
    private final AiMeta ai;

    @JsonProperty("meta")
    private final Meta meta;

    private ApiResponse(Object data, ErrorBody error, AiMeta ai, Meta meta) {
        this.data  = data;
        this.error = error;
        this.ai    = ai;
        this.meta  = Objects.requireNonNull(meta, "meta");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Factory methods
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a success envelope wrapping the given data payload.
     *
     * @param data      the response payload (Map, List, or any serialisable object)
     * @param tenantId  the tenant context
     * @param requestId the correlation ID
     * @return a new success ApiResponse
     */
    public static ApiResponse success(Object data, String tenantId, String requestId) {
        return new ApiResponse(data, null, null, Meta.of(tenantId, requestId));
    }

    /**
     * Creates an error envelope with a machine-readable code and human-readable message.
     *
     * @param code      error code (e.g. "NOT_FOUND", "VALIDATION_FAILED")
     * @param message   human-readable description
     * @param tenantId  tenant context
     * @param requestId correlation ID
     * @return a new error ApiResponse
     */
    public static ApiResponse error(String code, String message, String tenantId, String requestId) {
        return new ApiResponse(null, new ErrorBody(code, message, null), null, Meta.of(tenantId, requestId));
    }

    /**
     * Creates an error envelope with extra structured details.
     *
     * @param code      error code
     * @param message   human-readable description
     * @param details   optional map with additional context (field errors, hints, etc.)
     * @param tenantId  tenant context
     * @param requestId correlation ID
     * @return a new error ApiResponse
     */
    public static ApiResponse error(String code, String message, Map<String, Object> details,
                                    String tenantId, String requestId) {
        return new ApiResponse(null, new ErrorBody(code, message, details), null, Meta.of(tenantId, requestId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mutators (return new instance — envelope is immutable after addition)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Attaches AI/ML confidence metadata to this response.
     *
     * @param confidence score in [0.0, 1.0]
     * @param model      model name/version that produced the result
     * @param reasons    human-readable reason codes
     * @param fallback   true if a fallback path was used instead of the primary model
     * @return a new ApiResponse with the {@code ai} block populated
     */
    public ApiResponse withAiMeta(double confidence, String model, java.util.List<String> reasons, boolean fallback) {
        return new ApiResponse(this.data, this.error, new AiMeta(confidence, model, reasons, fallback), this.meta);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessors (needed for serialisation and tests)
    // ─────────────────────────────────────────────────────────────────────────

    public Object getData()      { return data; }
    public ErrorBody getError()  { return error; }
    public AiMeta getAi()        { return ai; }
    public Meta getMeta()        { return meta; }
    public boolean isSuccess()   { return error == null; }

    /**
     * Serialises this envelope to a compact JSON string using the provided ObjectMapper.
     *
     * @param mapper Jackson ObjectMapper (must not be null)
     * @return JSON string
     * @throws RuntimeException if serialisation fails
     */
    public String toJson(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(this);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("ApiResponse serialisation failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nested types
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Response metadata block always present in every response.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Meta {

        @JsonProperty("requestId")
        private final String requestId;

        @JsonProperty("tenantId")
        private final String tenantId;

        @JsonProperty("timestamp")
        private final String timestamp;

        @JsonProperty("apiVersion")
        private final String apiVersion;

        private Meta(String requestId, String tenantId) {
            this.requestId  = requestId;
            this.tenantId   = tenantId;
            this.timestamp  = Instant.now().toString();
            this.apiVersion = API_VERSION;
        }

        public static Meta of(String tenantId, String requestId) {
            return new Meta(requestId, tenantId);
        }

        public String getRequestId()  { return requestId; }
        public String getTenantId()   { return tenantId; }
        public String getTimestamp()  { return timestamp; }
        public String getApiVersion() { return apiVersion; }
    }

    /**
     * Structured error block present only on error responses.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ErrorBody {

        @JsonProperty("code")
        private final String code;

        @JsonProperty("message")
        private final String message;

        @JsonProperty("details")
        private final Map<String, Object> details;

        private ErrorBody(String code, String message, Map<String, Object> details) {
            this.code    = Objects.requireNonNull(code, "error code");
            this.message = Objects.requireNonNull(message, "error message");
            this.details = (details != null && !details.isEmpty()) ? new LinkedHashMap<>(details) : null;
        }

        public String getCode()              { return code; }
        public String getMessage()           { return message; }
        public Map<String, Object> getDetails() { return details; }
    }

    /**
     * AI/ML confidence metadata block, present only on AI-enriched responses.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class AiMeta {

        @JsonProperty("confidence")
        private final double confidence;

        @JsonProperty("model")
        private final String model;

        @JsonProperty("reasons")
        private final java.util.List<String> reasons;

        @JsonProperty("fallback")
        private final boolean fallback;

        private AiMeta(double confidence, String model, java.util.List<String> reasons, boolean fallback) {
            this.confidence = confidence;
            this.model      = model;
            this.reasons    = (reasons != null) ? java.util.List.copyOf(reasons) : java.util.List.of();
            this.fallback   = fallback;
        }

        public double getConfidence()             { return confidence; }
        public String getModel()                  { return model; }
        public java.util.List<String> getReasons() { return reasons; }
        public boolean isFallback()               { return fallback; }
    }
}
