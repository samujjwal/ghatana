package com.ghatana.phr.api;

import java.util.Map;

/**
 * Unified API response envelope for PHR controller operations.
 * Replaces FhirApiResponse and NepalHieApiResponse.
 *
 * @doc.type record
 * @doc.purpose Unified API response envelope for PHR
 * @doc.layer product
 * @doc.pattern DTO
 */
public record PhrApiResponse(int statusCode, String body, Map<String, String> headers) {

    public static PhrApiResponse json(int statusCode, String body) {
        return new PhrApiResponse(statusCode, body, Map.of("Content-Type", "application/json"));
    }

    public static PhrApiResponse fhirJson(int statusCode, String body) {
        return new PhrApiResponse(statusCode, body, Map.of("Content-Type", "application/fhir+json"));
    }
}
