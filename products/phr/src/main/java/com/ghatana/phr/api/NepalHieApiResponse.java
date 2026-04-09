package com.ghatana.phr.api;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose API response envelope for Nepal HIE submission operations
 * @doc.layer product
 * @doc.pattern DTO
 */
public record NepalHieApiResponse(int statusCode, String body, Map<String, String> headers) {

    public static NepalHieApiResponse json(int statusCode, String body) {
        return new NepalHieApiResponse(statusCode, body, Map.of("Content-Type", "application/json"));
    }
}
