package com.ghatana.services.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for AuthGatewayLauncher JSON field extraction
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AuthGatewayLauncher — JSON field extraction with Jackson")
class AuthGatewayLauncherTest {

    @Test
    @DisplayName("extractJsonField returns null for null input")
    void extractJsonFieldReturnsNullForNullInput() { // GH-90000
        String result = invokeExtractJsonField(null, "username"); // GH-90000
        assertThat(result).isNull(); // GH-90000
    }

    @Test
    @DisplayName("extractJsonField returns null for missing field")
    void extractJsonFieldReturnsNullForMissingField() { // GH-90000
        String json = "{\"username\":\"test\"}";
        String result = invokeExtractJsonField(json, "password"); // GH-90000
        assertThat(result).isNull(); // GH-90000
    }

    @Test
    @DisplayName("extractJsonField extracts simple string field")
    void extractJsonFieldExtractsSimpleStringField() { // GH-90000
        String json = "{\"username\":\"testuser\"}";
        String result = invokeExtractJsonField(json, "username"); // GH-90000
        assertThat(result).isEqualTo("testuser");
    }

    @Test
    @DisplayName("extractJsonField handles escaped quotes")
    void extractJsonFieldHandlesEscapedQuotes() { // GH-90000
        String json = "{\"username\":\"test\\\"user\"}";
        String result = invokeExtractJsonField(json, "username"); // GH-90000
        assertThat(result).isEqualTo("test\"user"); // GH-90000
    }

    @Test
    @DisplayName("extractJsonField handles nested objects")
    void extractJsonFieldHandlesNestedObjects() { // GH-90000
        String json = "{\"user\":{\"username\":\"testuser\"}}";
        String result = invokeExtractJsonField(json, "user"); // GH-90000
        assertThat(result).isEqualTo("{\"username\":\"testuser\"}"); // GH-90000
    }

    @Test
    @DisplayName("extractJsonField handles numeric field")
    void extractJsonFieldHandlesNumericField() { // GH-90000
        String json = "{\"age\":30}";
        String result = invokeExtractJsonField(json, "age"); // GH-90000
        assertThat(result).isEqualTo("30");
    }

    @Test
    @DisplayName("extractJsonField handles boolean field")
    void extractJsonFieldHandlesBooleanField() { // GH-90000
        String json = "{\"active\":true}";
        String result = invokeExtractJsonField(json, "active"); // GH-90000
        assertThat(result).isEqualTo("true");
    }

    @Test
    @DisplayName("extractJsonField handles complex nested JSON")
    void extractJsonFieldHandlesComplexNestedJson() { // GH-90000
        String json = "{\"user\":{\"profile\":{\"username\":\"testuser\",\"email\":\"test@example.com\"}},\"active\":true}";
        String result = invokeExtractJsonField(json, "user"); // GH-90000
        assertThat(result).isEqualTo("{\"profile\":{\"username\":\"testuser\",\"email\":\"test@example.com\"}}"); // GH-90000
    }

    @Test
    @DisplayName("extractJsonField handles field with null value")
    void extractJsonFieldHandlesNullFieldValue() { // GH-90000
        String json = "{\"username\":null}";
        String result = invokeExtractJsonField(json, "username"); // GH-90000
        assertThat(result).isEqualTo("null");
    }

    @Test
    @DisplayName("extractJsonField handles malformed JSON gracefully")
    void extractJsonFieldHandlesMalformedJson() { // GH-90000
        String json = "{invalid json}";
        String result = invokeExtractJsonField(json, "username"); // GH-90000
        assertThat(result).isNull(); // GH-90000
    }

    @Test
    @DisplayName("extractJsonField handles empty JSON object")
    void extractJsonFieldHandlesEmptyJsonObject() { // GH-90000
        String json = "{}";
        String result = invokeExtractJsonField(json, "username"); // GH-90000
        assertThat(result).isNull(); // GH-90000
    }

    @Test
    @DisplayName("extractJsonField handles array field")
    void extractJsonFieldHandlesArrayField() { // GH-90000
        String json = "{\"roles\":[\"admin\",\"user\"]}";
        String result = invokeExtractJsonField(json, "roles"); // GH-90000
        assertThat(result).isEqualTo("[\"admin\",\"user\"]"); // GH-90000
    }

    @Test
    @DisplayName("extractJsonField handles special characters in value")
    void extractJsonFieldHandlesSpecialCharacters() { // GH-90000
        String json = "{\"password\":\"p@$$w0rd!@#\"}";
        String result = invokeExtractJsonField(json, "password"); // GH-90000
        assertThat(result).isEqualTo("p@$$w0rd!@#");
    }

    @Test
    @DisplayName("extractJsonField handles unicode characters")
    void extractJsonFieldHandlesUnicodeCharacters() { // GH-90000
        String json = "{\"name\":\"José García\"}";
        String result = invokeExtractJsonField(json, "name"); // GH-90000
        assertThat(result).isEqualTo("José García");
    }

    @Test
    @DisplayName("extractJsonField handles field name with underscores")
    void extractJsonFieldHandlesUnderscoreFieldName() { // GH-90000
        String json = "{\"user_name\":\"testuser\"}";
        String result = invokeExtractJsonField(json, "user_name"); // GH-90000
        assertThat(result).isEqualTo("testuser");
    }

    /**
     * Helper method to invoke the private extractJsonField method using reflection.
     * This is a workaround for testing private methods without changing visibility.
     */
    private String invokeExtractJsonField(String json, String field) { // GH-90000
        try {
            java.lang.reflect.Method method = com.ghatana.services.auth.AuthGatewayLauncher.class
                .getDeclaredMethod("extractJsonField", String.class, String.class); // GH-90000
            method.setAccessible(true); // GH-90000
            return (String) method.invoke(null, json, field); // GH-90000
        } catch (Exception e) { // GH-90000
            throw new RuntimeException("Failed to invoke extractJsonField", e); // GH-90000
        }
    }
}
