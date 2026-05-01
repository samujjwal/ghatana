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
    void extractJsonFieldReturnsNullForNullInput() { 
        String result = invokeExtractJsonField(null, "username"); 
        assertThat(result).isNull(); 
    }

    @Test
    @DisplayName("extractJsonField returns null for missing field")
    void extractJsonFieldReturnsNullForMissingField() { 
        String json = "{\"username\":\"test\"}";
        String result = invokeExtractJsonField(json, "password"); 
        assertThat(result).isNull(); 
    }

    @Test
    @DisplayName("extractJsonField extracts simple string field")
    void extractJsonFieldExtractsSimpleStringField() { 
        String json = "{\"username\":\"testuser\"}";
        String result = invokeExtractJsonField(json, "username"); 
        assertThat(result).isEqualTo("testuser");
    }

    @Test
    @DisplayName("extractJsonField handles escaped quotes")
    void extractJsonFieldHandlesEscapedQuotes() { 
        String json = "{\"username\":\"test\\\"user\"}";
        String result = invokeExtractJsonField(json, "username"); 
        assertThat(result).isEqualTo("test\"user"); 
    }

    @Test
    @DisplayName("extractJsonField handles nested objects")
    void extractJsonFieldHandlesNestedObjects() { 
        String json = "{\"user\":{\"username\":\"testuser\"}}";
        String result = invokeExtractJsonField(json, "user"); 
        assertThat(result).isEqualTo("{\"username\":\"testuser\"}"); 
    }

    @Test
    @DisplayName("extractJsonField handles numeric field")
    void extractJsonFieldHandlesNumericField() { 
        String json = "{\"age\":30}";
        String result = invokeExtractJsonField(json, "age"); 
        assertThat(result).isEqualTo("30");
    }

    @Test
    @DisplayName("extractJsonField handles boolean field")
    void extractJsonFieldHandlesBooleanField() { 
        String json = "{\"active\":true}";
        String result = invokeExtractJsonField(json, "active"); 
        assertThat(result).isEqualTo("true");
    }

    @Test
    @DisplayName("extractJsonField handles complex nested JSON")
    void extractJsonFieldHandlesComplexNestedJson() { 
        String json = "{\"user\":{\"profile\":{\"username\":\"testuser\",\"email\":\"test@example.com\"}},\"active\":true}";
        String result = invokeExtractJsonField(json, "user"); 
        assertThat(result).isEqualTo("{\"profile\":{\"username\":\"testuser\",\"email\":\"test@example.com\"}}"); 
    }

    @Test
    @DisplayName("extractJsonField handles field with null value")
    void extractJsonFieldHandlesNullFieldValue() { 
        String json = "{\"username\":null}";
        String result = invokeExtractJsonField(json, "username"); 
        assertThat(result).isEqualTo("null");
    }

    @Test
    @DisplayName("extractJsonField handles malformed JSON gracefully")
    void extractJsonFieldHandlesMalformedJson() { 
        String json = "{invalid json}";
        String result = invokeExtractJsonField(json, "username"); 
        assertThat(result).isNull(); 
    }

    @Test
    @DisplayName("extractJsonField handles empty JSON object")
    void extractJsonFieldHandlesEmptyJsonObject() { 
        String json = "{}";
        String result = invokeExtractJsonField(json, "username"); 
        assertThat(result).isNull(); 
    }

    @Test
    @DisplayName("extractJsonField handles array field")
    void extractJsonFieldHandlesArrayField() { 
        String json = "{\"roles\":[\"admin\",\"user\"]}";
        String result = invokeExtractJsonField(json, "roles"); 
        assertThat(result).isEqualTo("[\"admin\",\"user\"]"); 
    }

    @Test
    @DisplayName("extractJsonField handles special characters in value")
    void extractJsonFieldHandlesSpecialCharacters() { 
        String json = "{\"password\":\"p@$$w0rd!@#\"}";
        String result = invokeExtractJsonField(json, "password"); 
        assertThat(result).isEqualTo("p@$$w0rd!@#");
    }

    @Test
    @DisplayName("extractJsonField handles unicode characters")
    void extractJsonFieldHandlesUnicodeCharacters() { 
        String json = "{\"name\":\"José García\"}";
        String result = invokeExtractJsonField(json, "name"); 
        assertThat(result).isEqualTo("José García");
    }

    @Test
    @DisplayName("extractJsonField handles field name with underscores")
    void extractJsonFieldHandlesUnderscoreFieldName() { 
        String json = "{\"user_name\":\"testuser\"}";
        String result = invokeExtractJsonField(json, "user_name"); 
        assertThat(result).isEqualTo("testuser");
    }

    /**
     * Helper method to invoke the private extractJsonField method using reflection.
     * This is a workaround for testing private methods without changing visibility.
     */
    private String invokeExtractJsonField(String json, String field) { 
        try {
            java.lang.reflect.Method method = com.ghatana.services.auth.AuthGatewayLauncher.class
                .getDeclaredMethod("extractJsonField", String.class, String.class); 
            method.setAccessible(true); 
            return (String) method.invoke(null, json, field); 
        } catch (Exception e) { 
            throw new RuntimeException("Failed to invoke extractJsonField", e); 
        }
    }
}
