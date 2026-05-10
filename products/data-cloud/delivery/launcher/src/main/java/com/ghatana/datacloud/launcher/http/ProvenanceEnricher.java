/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared provenance enrichment utility for write operations.
 *
 * <p>DC-P1-008: Every write operation must include provenance metadata
 * (who, when, from where, trace ID, etc.) to ensure full auditability and
 * traceability of all data changes.</p>
 *
 * <p>This utility provides a centralized method to add provenance metadata
 * to entity data before it is persisted, ensuring consistent provenance
 * enforcement across all write boundaries.</p>
 *
 * @doc.type class
 * @doc.purpose Centralized provenance enrichment for write operations
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class ProvenanceEnricher {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceEnricher.class);

    /**
     * Fields that indicate personally identifiable information.
     * Used for data sensitivity classification in provenance.
     */
    private static final Set<String> PII_FIELDS = Set.of(
        "name", "email", "phone", "ssn", "socialSecurityNumber",
        "address", "creditcard", "creditCard", "password",
        "secret", "token", "apiKey", "personal", "identity"
    );

    private ProvenanceEnricher() {
        // Utility class - prevent instantiation
    }

    /**
     * Enriches entity data with provenance metadata.
     *
     * <p>Added provenance fields:
     * <ul>
     *   <li>actor: Who performed the operation (API ID or system)</li>
     *   <li>timestamp: When the operation occurred</li>
     *   <li>correlationId: Trace correlation ID for request tracing</li>
     *   <li>source: Source of the write operation (e.g., rest-api, system, migration)</li>
     *   <li>dataClassification: Sensitivity classification (pii or standard)</li>
     * </ul>
     *
     * @param data the entity data to enrich
     * @param request the HTTP request (for extracting actor and correlation ID)
     * @param correlationId the correlation ID for tracing
     * @return a new map with the original data plus provenance metadata
     */
    public static Map<String, Object> enrichWithProvenance(
            Map<String, Object> data,
            HttpRequest request,
            String correlationId) {
        Map<String, Object> enriched = new LinkedHashMap<>(data);
        Map<String, Object> provenance = new LinkedHashMap<>();

        provenance.put("actor", Map.of(
            "type", "api",
            "id", resolveActorId(request)
        ));
        provenance.put("timestamp", Instant.now().toString());
        provenance.put("correlationId", correlationId != null ? correlationId : "");
        provenance.put("source", "rest-api");
        provenance.put("dataClassification", classifyDataSensitivity(data));

        enriched.put("_provenance", provenance);
        return enriched;
    }

    /**
     * Enriches entity data with provenance for system-initiated operations.
     *
     * <p>Used for operations performed by the system (e.g., auto-remediation,
     * scheduled tasks) rather than direct API calls from users.</p>
     *
     * @param data the entity data to enrich
     * @param actorId the system actor ID (e.g., "system.auto-remediate")
     * @param source the source of the operation (e.g., "system", "migration")
     * @return a new map with the original data plus provenance metadata
     */
    public static Map<String, Object> enrichWithProvenance(
            Map<String, Object> data,
            String actorId,
            String source) {
        Map<String, Object> enriched = new LinkedHashMap<>(data);
        Map<String, Object> provenance = new LinkedHashMap<>();

        provenance.put("actor", Map.of(
            "type", "system",
            "id", actorId
        ));
        provenance.put("timestamp", Instant.now().toString());
        provenance.put("correlationId", "");
        provenance.put("source", source);
        provenance.put("dataClassification", classifyDataSensitivity(data));

        enriched.put("_provenance", provenance);
        return enriched;
    }

    /**
     * Resolves the actor ID from the HTTP request.
     *
     * <p>Extracts the API key or user ID from the request for provenance tracking.</p>
     *
     * @param request the HTTP request
     * @return the actor ID, or "unknown" if not available
     */
    private static String resolveActorId(HttpRequest request) {
        if (request == null) {
            return "unknown";
        }

        String apiKey = request.getHeader(HttpHeaders.of("X-API-Key"));
        if (apiKey != null && !apiKey.isBlank()) {
            // Truncate API keys for security in logs
            return apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : apiKey;
        }

        String userId = request.getHeader(HttpHeaders.of("X-User-Id"));
        if (userId != null && !userId.isBlank()) {
            return userId;
        }

        return "unknown";
    }

    /**
     * Lightweight heuristic policy classification for data sensitivity.
     *
     * <p>Classifies data as PII (Personally Identifiable Information) or standard
     * based on field names. This is a lightweight heuristic; production systems
     * should use more sophisticated classification.</p>
     *
     * @param data the entity data to classify
     * @return "pii" if data contains PII fields, "standard" otherwise
     */
    private static String classifyDataSensitivity(Map<String, Object> data) {
        if (data == null) {
            return "standard";
        }

        for (String key : data.keySet()) {
            String lower = key.toLowerCase();
            if (PII_FIELDS.contains(lower)) {
                return "pii";
            }
        }

        return "standard";
    }
}
