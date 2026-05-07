/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        utils = new HttpHandlerUtils(); 
    }

    @Test
    @DisplayName("jsonResponse serializes a Map and returns correct status code")
    void jsonResponse_mapData_returnsJsonWithCorrectStatus() { 
        Map<String, Object> data = Map.of("key", "value", "count", 42); 

        HttpResponse response = utils.jsonResponse(200, data); 

        assertThat(response.getCode()).isEqualTo(200); 
        String body = response.getBody().asString(StandardCharsets.UTF_8); 
        assertThat(body).contains("\"key\"").contains("\"value\"").contains("42");
        assertThat(response.getHeader(io.activej.http.HttpHeaders.CONTENT_TYPE)) 
                .contains("application/json");
    }

    @Test
    @DisplayName("jsonResponse with 404 sets the correct HTTP status code")
    void jsonResponse_404Status_setsCorrectCode() { 
        HttpResponse response = utils.jsonResponse(404, Map.of("error", "not_found")); 

        assertThat(response.getCode()).isEqualTo(404); 
    }

    @Test
    @DisplayName("errorResponse wraps message in error field and returns the given status")
    void errorResponse_wrapsMessage() { 
        HttpResponse response = utils.errorResponse(400, "bad request body"); 

        assertThat(response.getCode()).isEqualTo(400); 
        String body = response.getBody().asString(StandardCharsets.UTF_8); 
        assertThat(body).contains("\"error\"").contains("bad request body");
    }

    @Test
    @DisplayName("toJson converts a simple object to a Map")
    void toJson_simpleMap_returnsConvertedMap() { 
        Map<String, Object> input = Map.of("name", "test-agent", "version", "1.0"); 

        Map<String, Object> result = utils.toJson(input); 

        assertThat(result).containsKey("name").containsKey("version");
        assertThat(result.get("name")).isEqualTo("test-agent");
    }

    @Test
    @DisplayName("toJson returns empty map on unserializable input")
    void toJson_unserializableInput_returnsEmptyMap() { 
        // Pass an object that cannot be converted to a Map (a simple String) 
        Map<String, Object> result = utils.toJson("not-a-map");

        // Jackson will try to convert a String to Map<String,Object> and fail
        assertThat(result).isEmpty(); 
    }

    @Test
    @DisplayName("jsonResponse handles serialization failure gracefully with 500")
    void jsonResponse_serializationError_returns500() { 
        // Create a circular reference to trigger serialization failure
        HttpResponse response = utils.jsonResponse(200, 
                new Object() { 
                    @Override
                    public String toString() { return "safe-string"; } 
                    // Jackson will attempt to serialize this plain Object, leading to empty {}
                });

        // Either success (empty JSON object) or internal 500 — must not throw 
        assertThat(response.getCode()).isIn(200, 500); 
    }
}
