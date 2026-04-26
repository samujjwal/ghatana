/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * T-22: Canonical AI suggestion envelope shared across the AEP builder,
 * operate, and learn surfaces.
 *
 * <p>All AI suggestion responses — whether from the pipeline stage suggester,
 * the anomaly detector, or the learning/reflect endpoint — must be wrapped in
 * this envelope before being serialised and sent to clients. This ensures:
 * <ul>
 *   <li>A consistent wire shape consumers can depend on.</li>
 *   <li>Mandatory confidence and rationale fields that make AI output auditable.</li>
 *   <li>An optional {@code evidence} list linking suggestions back to observable signals.</li>
 *   <li>An {@code auditHook} field that identifies who asked and when, for compliance logging.</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * AiSuggestionEnvelope envelope = AiSuggestionEnvelope.builder()
 *     .suggestionId(UUID.randomUUID().toString())
 *     .type("stage")
 *     .severity("medium")
 *     .message("Add a fraud-detection stage after customer enrichment")
 *     .confidence(0.87)
 *     .rationale("3 anomaly signals in the last hour for transactions > $500")
 *     .evidence(List.of(Map.of("signalType", "anomaly", "entityId", "pipeline-42")))
 *     .tenantId("tenant-abc")
 *     .surface("builder")
 *     .generatedAt(Instant.now())
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Canonical shared AI suggestion response envelope for builder/operate/learn
 * @doc.layer product
 * @doc.pattern DTO
 */
public final class AiSuggestionEnvelope {

    private final String suggestionId;
    private final String type;
    private final String severity;
    private final String message;
    private final double confidence;
    private final String rationale;
    private final List<Map<String, Object>> evidence;
    private final String tenantId;
    private final String surface;
    private final Instant generatedAt;
    private final AuditHook auditHook;

    private AiSuggestionEnvelope(Builder builder) {
        this.suggestionId = Objects.requireNonNull(builder.suggestionId, "suggestionId");
        this.type         = Objects.requireNonNull(builder.type,         "type");
        this.severity     = builder.severity != null ? builder.severity : "medium";
        this.message      = Objects.requireNonNull(builder.message,      "message");
        this.confidence   = builder.confidence;
        this.rationale    = builder.rationale != null ? builder.rationale : "";
        this.evidence     = builder.evidence  != null ? List.copyOf(builder.evidence) : List.of();
        this.tenantId     = Objects.requireNonNull(builder.tenantId,     "tenantId");
        this.surface      = builder.surface != null ? builder.surface : "unknown";
        this.generatedAt  = builder.generatedAt != null ? builder.generatedAt : Instant.now();
        this.auditHook    = builder.auditHook;
    }

    public String suggestionId()             { return suggestionId; }
    public String type()                     { return type; }
    public String severity()                 { return severity; }
    public String message()                  { return message; }
    public double confidence()               { return confidence; }
    public String rationale()                { return rationale; }
    public List<Map<String, Object>> evidence() { return evidence; }
    public String tenantId()                 { return tenantId; }
    public String surface()                  { return surface; }
    public Instant generatedAt()             { return generatedAt; }
    public AuditHook auditHook()             { return auditHook; }

    /**
     * Converts this envelope to a plain map for JSON serialisation.
     *
     * @return mutable map representation
     */
    public Map<String, Object> toMap() {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("suggestionId", suggestionId);
        map.put("type", type);
        map.put("severity", severity);
        map.put("message", message);
        map.put("confidence", confidence);
        map.put("rationale", rationale);
        map.put("evidence", evidence);
        map.put("tenantId", tenantId);
        map.put("surface", surface);
        map.put("generatedAt", generatedAt.toString());
        if (auditHook != null) {
            map.put("auditHook", auditHook.toMap());
        }
        return map;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Audit hook that identifies the requesting principal and request context.
     *
     * <p>Populated by the HTTP handler when a JWT principal is present. Used
     * by downstream compliance consumers (audit log, SIEM) to associate AI
     * suggestions with the operator who triggered them.
     *
     * @param principalId the authenticated user or service identity
     * @param requestId   correlation ID for the originating HTTP request
     * @param requestedAt when the suggestion was requested
     */
    public record AuditHook(
            String principalId,
            String requestId,
            Instant requestedAt
    ) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "principalId", principalId != null ? principalId : "",
                    "requestId",   requestId   != null ? requestId   : "",
                    "requestedAt", requestedAt != null ? requestedAt.toString() : ""
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link AiSuggestionEnvelope}.
     */
    public static final class Builder {
        private String suggestionId;
        private String type;
        private String severity;
        private String message;
        private double confidence;
        private String rationale;
        private List<Map<String, Object>> evidence;
        private String tenantId;
        private String surface;
        private Instant generatedAt;
        private AuditHook auditHook;

        private Builder() {}

        public Builder suggestionId(String suggestionId) { this.suggestionId = suggestionId; return this; }
        public Builder type(String type)                 { this.type = type;                 return this; }
        public Builder severity(String severity)         { this.severity = severity;         return this; }
        public Builder message(String message)           { this.message = message;           return this; }
        public Builder confidence(double confidence)     { this.confidence = confidence;     return this; }
        public Builder rationale(String rationale)       { this.rationale = rationale;       return this; }
        public Builder evidence(List<Map<String, Object>> evidence) { this.evidence = evidence; return this; }
        public Builder tenantId(String tenantId)         { this.tenantId = tenantId;         return this; }
        public Builder surface(String surface)           { this.surface = surface;           return this; }
        public Builder generatedAt(Instant generatedAt) { this.generatedAt = generatedAt;   return this; }
        public Builder auditHook(AuditHook auditHook)   { this.auditHook = auditHook;       return this; }

        public AiSuggestionEnvelope build() {
            return new AiSuggestionEnvelope(this);
        }
    }
}
