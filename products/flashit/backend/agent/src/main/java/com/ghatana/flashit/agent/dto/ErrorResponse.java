package com.ghatana.flashit.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Standard API error response.
 *
 * @doc.type record
 * @doc.purpose Consistent error response format for HTTP error replies
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("message") String message,
        @JsonProperty("details") Map<String, Object> details
) {

    public ErrorResponse(String error, String message) {
        this(error, message, null);
    }
}
