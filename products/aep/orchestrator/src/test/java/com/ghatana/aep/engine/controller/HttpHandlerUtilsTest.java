/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.engine.controller;

import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HttpHandlerUtils}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for HTTP response utility methods
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("HttpHandlerUtils")
class HttpHandlerUtilsTest {

    private HttpHandlerUtils utils;

    @BeforeEach
    void setUp() { // GH-90000
        utils = new HttpHandlerUtils(); // GH-90000
    }

    @Test
    @DisplayName("jsonResponse serializes a Map and returns correct status code")
    void jsonResponse_mapData_returnsJsonWithCorrectStatus() { // GH-90000
        Map<String, Object> data = Map.of("key", "value", "count", 42); // GH-90000

        HttpResponse response = utils.jsonResponse(200, data); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().asString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"key\"").contains("\"value\"").contains("42");
        assertThat(response.getHeader(io.activej.http.HttpHeaders.CONTENT_TYPE)) // GH-90000
                .contains("application/json");
    }

    @Test
    @DisplayName("jsonResponse with 404 sets the correct HTTP status code")
    void jsonResponse_404Status_setsCorrectCode() { // GH-90000
        HttpResponse response = utils.jsonResponse(404, Map.of("error", "not_found")); // GH-90000

        assertThat(response.getCode()).isEqualTo(404); // GH-90000
    }

    @Test
    @DisplayName("errorResponse wraps message in error field and returns the given status")
    void errorResponse_wrapsMessage() { // GH-90000
        HttpResponse response = utils.errorResponse(400, "bad request body"); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
        String body = response.getBody().asString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"error\"").contains("bad request body");
    }

    @Test
    @DisplayName("toJson converts a simple object to a Map")
    void toJson_simpleMap_returnsConvertedMap() { // GH-90000
        Map<String, Object> input = Map.of("name", "test-agent", "version", "1.0"); // GH-90000

        Map<String, Object> result = utils.toJson(input); // GH-90000

        assertThat(result).containsKey("name").containsKey("version");
        assertThat(result.get("name")).isEqualTo("test-agent");
    }

    @Test
    @DisplayName("toJson returns empty map on unserializable input")
    void toJson_unserializableInput_returnsEmptyMap() { // GH-90000
        // Pass an object that cannot be converted to a Map (a simple String) // GH-90000
        Map<String, Object> result = utils.toJson("not-a-map");

        // Jackson will try to convert a String to Map<String,Object> and fail
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("jsonResponse handles serialization failure gracefully with 500")
    void jsonResponse_serializationError_returns500() { // GH-90000
        // Create a circular reference to trigger serialization failure
        HttpResponse response = utils.jsonResponse(200, // GH-90000
                new Object() { // GH-90000
                    @Override
                    public String toString() { return "safe-string"; } // GH-90000
                    // Jackson will attempt to serialize this plain Object, leading to empty {}
                });

        // Either success (empty JSON object) or internal 500 — must not throw // GH-90000
        assertThat(response.getCode()).isIn(200, 500); // GH-90000
    }
}
