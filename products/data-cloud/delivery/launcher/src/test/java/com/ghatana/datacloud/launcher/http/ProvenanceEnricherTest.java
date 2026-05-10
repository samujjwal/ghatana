/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProvenanceEnricher} provenance enrichment utility.
 *
 * <p>Validates:
 * <ul>
 *   <li>Provenance metadata is added to entity data</li>
 *   <li>Actor ID is resolved from request headers</li>
 *   <li>Timestamp is current</li>
 *   <li>Correlation ID is preserved</li>
 *   <li>Data sensitivity classification works</li>
 *   <li>System-initiated operations use correct provenance</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unit tests for ProvenanceEnricher
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ProvenanceEnricher Tests")
class ProvenanceEnricherTest {

    @Test
    @DisplayName("Enriches entity data with provenance metadata")
    void enrichesDataWithProvenance() {
        // Given entity data
        Map<String, Object> data = Map.of("name", "test-entity", "value", 123);

        // And HTTP request with headers
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-API-Key"), "test-key-12345")
            .withHeader(HttpHeaders.of("X-Request-Id"), "req-abc-789")
            .build();

        // When enriching with provenance
        Map<String, Object> enriched = ProvenanceEnricher.enrichWithProvenance(data, request, "correlation-xyz");

        // Then provenance metadata is added
        assertTrue(enriched.containsKey("_provenance"));

        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) enriched.get("_provenance");
        assertNotNull(provenance);

        // And actor information is present
        @SuppressWarnings("unchecked")
        Map<String, Object> actor = (Map<String, Object>) provenance.get("actor");
        assertNotNull(actor);
        assertEquals("api", actor.get("type"));
        assertTrue(((String) actor.get("id")).startsWith("test-key"));

        // And timestamp is current
        String timestamp = (String) provenance.get("timestamp");
        assertNotNull(timestamp);
        // Verify timestamp is recent (within last minute)
        Instant provenanceTime = Instant.parse(timestamp);
        assertTrue(Instant.now().minusSeconds(60).isBefore(provenanceTime));

        // And correlation ID is preserved
        assertEquals("correlation-xyz", provenance.get("correlationId"));

        // And source is rest-api
        assertEquals("rest-api", provenance.get("source"));

        // And data classification is present
        assertNotNull(provenance.get("dataClassification"));

        // And original data is preserved
        assertEquals("test-entity", enriched.get("name"));
        assertEquals(123, enriched.get("value"));
    }

    @Test
    @DisplayName("Resolves actor ID from X-API-Key header")
    void resolvesActorIdFromApiKey() {
        // Given request with API key
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-API-Key"), "my-secret-api-key-123456789")
            .build();

        // When enriching
        Map<String, Object> enriched = ProvenanceEnricher.enrichWithProvenance(Map.of(), request, null);

        // Then actor ID is truncated API key
        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) enriched.get("_provenance");
        @SuppressWarnings("unchecked")
        Map<String, Object> actor = (Map<String, Object>) provenance.get("actor");
        assertEquals("my-secre...", actor.get("id"));
    }

    @Test
    @DisplayName("Resolves actor ID from X-User-Id header")
    void resolvesActorIdFromUserId() {
        // Given request with user ID
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test")
            .withHeader(HttpHeaders.of("X-User-Id"), "user-12345")
            .build();

        // When enriching
        Map<String, Object> enriched = ProvenanceEnricher.enrichWithProvenance(Map.of(), request, null);

        // Then actor ID is user ID
        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) enriched.get("_provenance");
        @SuppressWarnings("unchecked")
        Map<String, Object> actor = (Map<String, Object>) provenance.get("actor");
        assertEquals("user-12345", actor.get("id"));
    }

    @Test
    @DisplayName("Returns unknown actor when no authentication headers present")
    void returnsUnknownActorWithoutAuthHeaders() {
        // Given request without auth headers
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test").build();

        // When enriching
        Map<String, Object> enriched = ProvenanceEnricher.enrichWithProvenance(Map.of(), request, null);

        // Then actor ID is unknown
        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) enriched.get("_provenance");
        @SuppressWarnings("unchecked")
        Map<String, Object> actor = (Map<String, Object>) provenance.get("actor");
        assertEquals("unknown", actor.get("id"));
    }

    @Test
    @DisplayName("Classifies data as PII when sensitive fields present")
    void classifiesDataAsPiiWithSensitiveFields() {
        // Given data with PII fields
        Map<String, Object> data = Map.of("name", "John Doe", "email", "john@example.com");

        // When enriching
        Map<String, Object> enriched = ProvenanceEnricher.enrichWithProvenance(
            data, HttpRequest.get("http://localhost/api/v1/entities/test").build(), null);

        // Then classification is pii
        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) enriched.get("_provenance");
        assertEquals("pii", provenance.get("dataClassification"));
    }

    @Test
    @DisplayName("Classifies data as standard when no sensitive fields present")
    void classifiesDataAsStandardWithoutSensitiveFields() {
        // Given data without PII fields
        Map<String, Object> data = Map.of("title", "Test", "count", 42);

        // When enriching
        Map<String, Object> enriched = ProvenanceEnricher.enrichWithProvenance(
            data, HttpRequest.get("http://localhost/api/v1/entities/test").build(), null);

        // Then classification is standard
        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) enriched.get("_provenance");
        assertEquals("standard", provenance.get("dataClassification"));
    }

    @Test
    @DisplayName("Enriches system-initiated operations with system provenance")
    void enrichesSystemOperationsWithSystemProvenance() {
        // Given entity data
        Map<String, Object> data = Map.of("status", "processed");

        // When enriching for system operation
        Map<String, Object> enriched = ProvenanceEnricher.enrichWithProvenance(
            data, "system.auto-remediate", "system");

        // Then provenance reflects system actor
        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) enriched.get("_provenance");
        assertNotNull(provenance);

        @SuppressWarnings("unchecked")
        Map<String, Object> actor = (Map<String, Object>) provenance.get("actor");
        assertEquals("system", actor.get("type"));
        assertEquals("system.auto-remediate", actor.get("id"));

        assertEquals("system", provenance.get("source"));
        assertEquals("", provenance.get("correlationId"));
    }

    @Test
    @DisplayName("Handles null correlation ID gracefully")
    void handlesNullCorrelationId() {
        // Given request without correlation ID
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities/test").build();

        // When enriching with null correlation ID
        Map<String, Object> enriched = ProvenanceEnricher.enrichWithProvenance(Map.of(), request, null);

        // Then correlation ID is empty string
        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) enriched.get("_provenance");
        assertEquals("", provenance.get("correlationId"));
    }

    @Test
    @DisplayName("Handles null request gracefully")
    void handlesNullRequest() {
        // When enriching with null request
        Map<String, Object> enriched = ProvenanceEnricher.enrichWithProvenance(
            Map.of(), (HttpRequest) null, null);

        // Then actor ID is unknown
        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) enriched.get("_provenance");
        @SuppressWarnings("unchecked")
        Map<String, Object> actor = (Map<String, Object>) provenance.get("actor");
        assertEquals("unknown", actor.get("id"));
    }

    @Test
    @DisplayName("Preserves existing data fields")
    void preservesExistingDataFields() {
        // Given data with multiple fields
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("id", "entity-123");
        data.put("name", "Test Entity");
        data.put("metadata", Map.of("key", "value"));

        // When enriching
        Map<String, Object> enriched = ProvenanceEnricher.enrichWithProvenance(
            data, HttpRequest.get("http://localhost/api/v1/entities/test").build(), null);

        // Then all original fields are preserved
        assertEquals("entity-123", enriched.get("id"));
        assertEquals("Test Entity", enriched.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) enriched.get("metadata");
        assertEquals("value", metadata.get("key"));
    }
}
