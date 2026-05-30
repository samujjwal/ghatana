package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.activej.http.HttpResponse;

/**
 * Shared ActiveJ response helpers for API controllers.
 *
 * @doc.type utility
 * @doc.purpose Serialize controller payloads into ActiveJ JSON responses
 * @doc.layer product
 * @doc.pattern Controller Helper
 */
final class ApiResponses {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private ApiResponses() {
    }

    static HttpResponse json(int statusCode, Object payload) {
        try {
            return HttpResponse.ofCode(statusCode)
                    .withJson(OBJECT_MAPPER.writeValueAsString(payload))
                    .build();
        } catch (JsonProcessingException e) {
            return HttpResponse.ofCode(500)
                    .withPlainText("JSON serialization failed")
                    .build();
        }
    }
}
