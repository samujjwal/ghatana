package com.ghatana.ai.service.integration;

import com.ghatana.ai.service.LLMService;
import com.ghatana.ai.service.OpenAIService;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OpenAIService generateStructured with schema validation.
 *
 * @doc.type class
 * @doc.purpose Integration tests for OpenAIService schema validation
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("OpenAIService Schema Validation Integration Tests")
class OpenAIServiceIntegrationTest {

    /**
     * Test data class for structured output validation.
     */
    static class TestSchema {
        public String name;
        public int value;
        public boolean active;

        public TestSchema() {}

        public TestSchema(String name, int value, boolean active) {
            this.name = name;
            this.value = value;
            this.active = active;
        }
    }

    @Test
    @DisplayName("Integration test: Should parse valid structured output from LLM")
    void shouldParseValidStructuredOutput() throws Exception {
        Eventloop eventloop = Eventloop.create();
        eventloop.run(() -> {
            // This integration test would require a real OpenAI API key
            // It tests the end-to-end flow:
            // 1. Send prompt to OpenAI with JSON mode enabled
            // 2. Receive JSON response
            // 3. Parse and validate against TestSchema
            // 4. Return typed object
            
            // To run this test, set OPENAI_API_KEY environment variable
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                // Skip test if no API key
                return Promise.complete(null);
            }
            
            LLMService service = new OpenAIService(apiKey);
            
            String prompt = "Generate a JSON object with name='test', value=42, active=true";
            
            return service.generateStructured(prompt, TestSchema.class)
                .then(result -> {
                    assertNotNull(result);
                    assertEquals("test", result.name);
                    assertEquals(42, result.value);
                    assertTrue(result.active);
                    return Promise.complete(null);
                })
                .whenException(e -> {
                    fail("Should not throw exception for valid structured output: " + e.getMessage());
                    return Promise.complete(null);
                });
        });
    }

    @Test
    @DisplayName("Integration test: Should throw exception for invalid structured output")
    void shouldThrowExceptionForInvalidStructuredOutput() throws Exception {
        Eventloop eventloop = Eventloop.create();
        eventloop.run(() -> {
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                return Promise.complete(null);
            }
            
            LLMService service = new OpenAIService(apiKey);
            
            // Prompt that might generate invalid JSON or JSON not matching schema
            String prompt = "Generate plain text, not JSON";
            
            return service.generateStructured(prompt, TestSchema.class)
                .then(result -> {
                    fail("Should throw exception for invalid structured output");
                    return Promise.complete(null);
                })
                .whenException(e -> {
                    // Expected - the LLM response cannot be parsed as TestSchema
                    assertTrue(e.getMessage().contains("Failed to parse"));
                    return Promise.complete(null);
                });
        });
    }
}
