package com.ghatana.phr.api;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Simple API response envelope for PHR FHIR controller operations
 * @doc.layer product
 * @doc.pattern DTO
 */
public record FhirApiResponse(int statusCode, String body, Map<String, String> headers) {

    public static FhirApiResponse json(int statusCode, String body) {
        return new FhirApiResponse(statusCode, body, Map.of("Content-Type", "application/fhir+json"));
    }
}
