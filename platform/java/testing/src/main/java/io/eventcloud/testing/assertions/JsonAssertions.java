package com.ghatana.platform.testing.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import java.util.List;
import java.util.Map;

/**
 * Utility class for JSON assertions in tests.
 *
 * @doc.type class
 * @doc.purpose JSON assertion utilities for equality, containment, and JsonPath validation
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class JsonAssertions {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Configuration jsonPathConfig = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();

    private JsonAssertions() {
        // Utility class
    }

    /**
     * Asserts that the JSON strings are equal, ignoring order and formatting.
     *
     * @param expectedJson the expected JSON string
     * @param actualJson  the actual JSON string to compare against
     * @throws AssertionError if the JSON strings are not equal
     */
    public static void assertJsonEquals(String expectedJson, String actualJson) {
        try {
            JsonNode expectedNode = objectMapper.readTree(expectedJson);
            JsonNode actualNode = objectMapper.readTree(actualJson);
            assertThat(actualNode).isEqualTo(expectedNode);
        } catch (JsonProcessingException e) {
            fail("Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Asserts that the actual JSON contains all fields from the expected JSON.
     *
     * @param expectedJson the expected JSON string with fields to check
     * @param actualJson  the actual JSON string to check against
     * @throws AssertionError if the actual JSON doesn't contain all expected fields
     */
    public static void assertJsonContains(String expectedJson, String actualJson) {
        try {
            JsonNode expectedNode = objectMapper.readTree(expectedJson);
            JsonNode actualNode = objectMapper.readTree(actualJson);
            
            // Convert both to maps for comparison with proper type information
            @SuppressWarnings("unchecked")
            Map<String, Object> expectedMap = objectMapper.convertValue(expectedNode, 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                
            @SuppressWarnings("unchecked")
            Map<String, Object> actualMap = objectMapper.convertValue(actualNode, 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

            // Check that all expected entries exist in the actual map
            assertThat(actualMap).containsAllEntriesOf(expectedMap);
        } catch (JsonProcessingException e) {
            fail("Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Asserts that the JSON string has the expected value at the given JSON path.
     *
     * @param json        the JSON string to evaluate
     * @param jsonPath    the JSON path expression
     * @param expected    the expected value
     * @param <T>         the type of the expected value
     * @throws AssertionError if the JSON path doesn't exist or the value doesn't match
     */
    public static <T> void assertJsonPath(String json, String jsonPath, T expected) {
        DocumentContext context = JsonPath.parse(json, jsonPathConfig);
        Object value = context.read(jsonPath);
        
        // Special handling for null values
        if (expected == null) {
            assertThat(value).isNull();
            return;
        }
        
        // Convert value to string for comparison if it's not null
        String valueStr = value != null ? value.toString() : null;
        
        // Handle different expected types
        if (expected instanceof Integer) {
            if (value instanceof Number) {
                assertThat(((Number) value).intValue()).isEqualTo(expected);
            } else if (valueStr != null) {
                try {
                    assertThat(Integer.parseInt(valueStr)).isEqualTo(expected);
                } catch (NumberFormatException e) {
                    fail(String.format("Expected integer at path '%s' but found: %s", jsonPath, value));
                }
            } else {
                fail(String.format("Expected integer at path '%s' but found: %s", jsonPath, value));
            }
        } else if (expected instanceof Long) {
            if (value instanceof Number) {
                assertThat(((Number) value).longValue()).isEqualTo(expected);
            } else if (valueStr != null) {
                try {
                    assertThat(Long.parseLong(valueStr)).isEqualTo(expected);
                } catch (NumberFormatException e) {
                    fail(String.format("Expected long at path '%s' but found: %s", jsonPath, value));
                }
            } else {
                fail(String.format("Expected long at path '%s' but found: %s", jsonPath, value));
            }
        } else if (expected instanceof String) {
            // Handle the case where the value is a string that might be quoted
            String expectedStr = (String) expected;
            if (valueStr != null && valueStr.startsWith("\"") && valueStr.endsWith("\"") && valueStr.length() > 1) {
                // Remove surrounding quotes for comparison
                valueStr = valueStr.substring(1, valueStr.length() - 1);
            }
            assertThat(valueStr).isEqualTo(expectedStr);
        } else {
            // For other types, try direct comparison first, then fall back to string comparison
            try {
                assertThat(value).isEqualTo(expected);
            } catch (AssertionError e) {
                if (valueStr != null) {
                    assertThat(valueStr).isEqualTo(String.valueOf(expected));
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Asserts that the JSON string has an array at the given path with the expected size.
     * @param json      the JSON string to evaluate
     * @param jsonPath  the JSON path to the array
     * @param expectedSize      the expected size of the array
     * @throws AssertionError if the array doesn't have the expected size
     */
    public static void assertJsonArraySize(String json, String jsonPath, int expectedSize) {
        DocumentContext context = JsonPath.parse(json, jsonPathConfig);
        Object result;
        
        try {
            result = context.read(jsonPath);
        } catch (Exception e) {
            fail(String.format("Failed to evaluate JSON path '%s': %s", jsonPath, e.getMessage()));
            return;
        }

        // Special handling for root array
        if ("$".equals(jsonPath)) {
            try {
                List<?> rootArray = objectMapper.readValue(json, List.class);
                if (rootArray.size() != expectedSize) {
                    fail(String.format("Expected array of size %d at path '%s' but found size %d", expectedSize, jsonPath, rootArray.size()));
                }
                return;
            } catch (Exception e) {
                // Not a root array, fall through to normal handling
            }
        }

        if (result == null) {
            fail(String.format("Expected an array at path '%s' but found null", jsonPath));
        } else if (result instanceof java.util.List) {
            List<?> array = (List<?>) result;
            if (array.size() != expectedSize) {
                fail(String.format("Expected array of size %d at path '%s' but found size %d", expectedSize, jsonPath, array.size()));
            }
        } else if (result instanceof JsonNode) {
            // Handle Jackson JsonNode results (e.g., ArrayNode) returned by the JacksonJsonNodeJsonProvider
            JsonNode node = (JsonNode) result;
            if (node.isArray()) {
                if (node.size() != expectedSize) {
                    fail(String.format("Expected array of size %d at path '%s' but found size %d", expectedSize, jsonPath, node.size()));
                }
            } else {
                fail(String.format("Expected an array at path '%s' but found %s", jsonPath, node.getNodeType()));
            }
        } else {
            fail(String.format("Expected an array at path '%s' but found %s", jsonPath, result.getClass().getSimpleName()));
        }
    }

    /**
     * Asserts that the JSON string has a field at the given path.
     *
     * @param json      the JSON string to evaluate
     * @param jsonPath  the JSON path to check
     * @throws AssertionError if the field doesn't exist
     */
    public static void assertHasJsonPath(String json, String jsonPath) {
        DocumentContext context = JsonPath.parse(json, jsonPathConfig);
        Object value = context.read(jsonPath);
        assertThat(value).isNotNull();
    }

    /**
     * Asserts that the JSON string doesn't have a field at the given path.
     *
     * @param json      the JSON string to evaluate
     * @param jsonPath  the JSON path to check
     * @throws AssertionError if the field exists
     */
    public static void assertDoesNotHaveJsonPath(String json, String jsonPath) {
        DocumentContext context = JsonPath.parse(json, jsonPathConfig);
        Object value = context.read(jsonPath);
        assertThat(value).isNull();
    }
}
