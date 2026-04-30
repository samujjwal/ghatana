package com.ghatana.yappc.design.figma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Figma integration components.
 *
 * Tests validate:
 * - Figma API client operations
 * - Token conversion to W3C format
 * - Webhook event processing
 * - GitHub PR automation
 * - Error handling and metrics
 *
 * @see FigmaClient
 * @see W3CTokenConverter
 * @see FigmaWebhookHandler
 * @see GitHubPRAutomation
 */
@DisplayName("Figma Integration Tests")
/**
 * @doc.type class
 * @doc.purpose Handles figma integration test operations
 * @doc.layer product
 * @doc.pattern Test
 */
class FigmaIntegrationTest extends EventloopTestBase {
    
    private ObjectMapper objectMapper;
    private MetricsCollector metrics;
    
    @BeforeEach
    void setUp() { // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        metrics = NoopMetricsCollector.getInstance(); // GH-90000
    }
    
    // ========================================================================
    // W3CTokenConverter Tests
    // ========================================================================
    
    /**
     * Verifies color token conversion from Figma to W3C format.
     *
     * GIVEN: Figma variables JSON with color token
     * WHEN: convert() is called // GH-90000
     * THEN: W3C format JSON is returned with correct color value
     */
    @Test
    @DisplayName("Should convert Figma color token to W3C format")
    void shouldConvertColorTokenToW3CFormat() { // GH-90000
        // GIVEN: Figma color variable
        String figmaJson = """
            {
                "variables": {
                    "var-1": {
                        "id": "var-1",
                        "name": "color/primary",
                        "resolvedType": "COLOR",
                        "valuesByMode": {
                            "mode-1": {
                                "r": 0.0,
                                "g": 0.4,
                                "b": 0.8,
                                "a": 1.0
                            }
                        },
                        "description": "Primary brand color"
                    }
                }
            }
            """;
        
        W3CTokenConverter converter = new W3CTokenConverter(objectMapper); // GH-90000
        
        // WHEN: Convert to W3C
        String w3cJson = converter.convert(figmaJson); // GH-90000
        
        // THEN: W3C format with hex color
        JsonNode w3cRoot = converter.parse(w3cJson); // GH-90000
        assertThat(w3cRoot.path("color").path("primary").path("$type").asText())
                .as("Token type should be 'color'")
                .isEqualTo("color");
        assertThat(w3cRoot.path("color").path("primary").path("$value").asText())
                .as("Color value should be hex")
                .matches("#[0-9a-f]{6}");
        assertThat(w3cRoot.path("color").path("primary").path("$description").asText())
                .as("Description should be preserved")
                .isEqualTo("Primary brand color");
    }
    
    /**
     * Verifies RGBA color conversion (with alpha < 1.0). // GH-90000
     *
     * GIVEN: Figma color with transparency
     * WHEN: convert() is called // GH-90000
     * THEN: W3C format uses rgba() notation // GH-90000
     */
    @Test
    @DisplayName("Should convert transparent color to rgba format")
    void shouldConvertTransparentColorToRgbaFormat() { // GH-90000
        // GIVEN: Figma color with alpha
        String figmaJson = """
            {
                "variables": {
                    "var-1": {
                        "name": "color/overlay",
                        "resolvedType": "COLOR",
                        "valuesByMode": {
                            "mode-1": {
                                "r": 0.0,
                                "g": 0.0,
                                "b": 0.0,
                                "a": 0.5
                            }
                        }
                    }
                }
            }
            """;
        
        W3CTokenConverter converter = new W3CTokenConverter(objectMapper); // GH-90000
        
        // WHEN: Convert
        String w3cJson = converter.convert(figmaJson); // GH-90000
        
        // THEN: RGBA format
        JsonNode w3cRoot = converter.parse(w3cJson); // GH-90000
        assertThat(w3cRoot.path("color").path("overlay").path("$value").asText())
                .as("Should use rgba() for transparency")
                .startsWith("rgba(");
    }
    
    /**
     * Verifies number token conversion.
     *
     * GIVEN: Figma FLOAT variable
     * WHEN: convert() is called // GH-90000
     * THEN: W3C format with number type
     */
    @Test
    @DisplayName("Should convert number token to W3C format")
    void shouldConvertNumberTokenToW3CFormat() { // GH-90000
        // GIVEN: Figma number variable
        String figmaJson = """
            {
                "variables": {
                    "var-1": {
                        "name": "spacing/base",
                        "resolvedType": "FLOAT",
                        "valuesByMode": {
                            "mode-1": 8.0
                        }
                    }
                }
            }
            """;
        
        W3CTokenConverter converter = new W3CTokenConverter(objectMapper); // GH-90000
        
        // WHEN: Convert
        String w3cJson = converter.convert(figmaJson); // GH-90000
        
        // THEN: Number type
        JsonNode w3cRoot = converter.parse(w3cJson); // GH-90000
        assertThat(w3cRoot.path("spacing").path("base").path("$type").asText())
                .as("Type should be 'number'")
                .isEqualTo("number");
        assertThat(w3cRoot.path("spacing").path("base").path("$value").asText())
                .as("Value should be numeric string")
                .isEqualTo("8.0");
    }
    
    /**
     * Verifies hierarchical token structure conversion.
     *
     * GIVEN: Figma variables with nested names (e.g., "color/primary/500") // GH-90000
     * WHEN: convert() is called // GH-90000
     * THEN: W3C format preserves hierarchy
     */
    @Test
    @DisplayName("Should preserve token hierarchy in W3C format")
    void shouldPreserveTokenHierarchy() { // GH-90000
        // GIVEN: Nested Figma variables
        String figmaJson = """
            {
                "variables": {
                    "var-1": {
                        "name": "color/primary/500",
                        "resolvedType": "COLOR",
                        "valuesByMode": {
                            "mode-1": {"r": 0.0, "g": 0.4, "b": 0.8, "a": 1.0}
                        }
                    },
                    "var-2": {
                        "name": "color/primary/700",
                        "resolvedType": "COLOR",
                        "valuesByMode": {
                            "mode-1": {"r": 0.0, "g": 0.3, "b": 0.6, "a": 1.0}
                        }
                    }
                }
            }
            """;
        
        W3CTokenConverter converter = new W3CTokenConverter(objectMapper); // GH-90000
        
        // WHEN: Convert
        String w3cJson = converter.convert(figmaJson); // GH-90000
        
        // THEN: Nested structure
        JsonNode w3cRoot = converter.parse(w3cJson); // GH-90000
        assertThat(w3cRoot.path("color").path("primary").path("500").isMissingNode())
                .as("Should have nested color.primary.500")
                .isFalse(); // GH-90000
        assertThat(w3cRoot.path("color").path("primary").path("700").isMissingNode())
                .as("Should have nested color.primary.700")
                .isFalse(); // GH-90000
    }
    
    /**
     * Verifies error handling when Figma JSON is invalid.
     *
     * GIVEN: Invalid Figma JSON (missing 'variables' field) // GH-90000
     * WHEN: convert() is called // GH-90000
     * THEN: TokenConversionException is thrown
     */
    @Test
    @DisplayName("Should throw exception when Figma JSON is invalid")
    void shouldThrowExceptionWhenFigmaJsonInvalid() { // GH-90000
        // GIVEN: Invalid JSON
        String invalidJson = "{ \"meta\": {} }";
        
        W3CTokenConverter converter = new W3CTokenConverter(objectMapper); // GH-90000
        
        // WHEN/THEN: Exception thrown
        assertThatThrownBy(() -> converter.convert(invalidJson)) // GH-90000
                .as("Should throw TokenConversionException")
                .isInstanceOf(W3CTokenConverter.TokenConversionException.class) // GH-90000
                .hasMessageContaining("No 'variables' field");
    }
    
    // ========================================================================
    // FigmaWebhookHandler Tests
    // ========================================================================
    
    /**
     * Verifies webhook signature validation.
     *
     * GIVEN: Valid webhook payload and signature
     * WHEN: handleWebhook() is called // GH-90000
     * THEN: Signature is validated successfully
     */
    @Test
    @DisplayName("Should validate webhook signature")
    void shouldValidateWebhookSignature() { // GH-90000
        // GIVEN: Mock dependencies
        FigmaClient figmaClient = mock(FigmaClient.class); // GH-90000
        W3CTokenConverter converter = mock(W3CTokenConverter.class); // GH-90000
        GitHubPRAutomation github = mock(GitHubPRAutomation.class); // GH-90000
        
        String webhookSecret = "test-secret";
        FigmaWebhookHandler handler = new FigmaWebhookHandler( // GH-90000
                figmaClient, converter, github, objectMapper, metrics, webhookSecret
        );
        
        String payload = "{\"event_type\":\"FILE_UPDATE\",\"file_key\":\"abc123\"}";
        
        // Compute valid signature
        String signature = computeHmacSha256(payload, webhookSecret); // GH-90000
        
        when(figmaClient.getVariables(anyString())) // GH-90000
                .thenReturn(Promise.of("{\"variables\":{}}")); // GH-90000
        when(converter.convert(anyString())) // GH-90000
                .thenReturn("{}");
        when(github.createPullRequest(anyString(), anyString(), anyString(),  // GH-90000
                anyString(), anyString(), anyString())) // GH-90000
                .thenReturn(Promise.of("https://github.com/pr/1"));
        
        // WHEN: Handle webhook
        Promise<Void> result = handler.handleWebhook(payload, signature); // GH-90000
        
        // THEN: No exception (signature valid) // GH-90000
        assertThatCode(() -> result.toCompletableFuture().join()) // GH-90000
                .as("Should process webhook successfully")
                .doesNotThrowAnyException(); // GH-90000
    }
    
    /**
     * Verifies webhook rejection with invalid signature.
     *
     * GIVEN: Valid payload but invalid signature
     * WHEN: handleWebhook() is called // GH-90000
     * THEN: WebhookException is thrown
     */
    @Test
    @DisplayName("Should reject webhook with invalid signature")
    void shouldRejectWebhookWithInvalidSignature() { // GH-90000
        // GIVEN: Handler with secret
        FigmaClient figmaClient = mock(FigmaClient.class); // GH-90000
        W3CTokenConverter converter = mock(W3CTokenConverter.class); // GH-90000
        GitHubPRAutomation github = mock(GitHubPRAutomation.class); // GH-90000
        
        FigmaWebhookHandler handler = new FigmaWebhookHandler( // GH-90000
                figmaClient, converter, github, objectMapper, metrics, "test-secret"
        );
        
        String payload = "{\"event_type\":\"FILE_UPDATE\"}";
        String invalidSignature = "invalid-sig";
        
        // WHEN: Handle with invalid signature
        Promise<Void> result = handler.handleWebhook(payload, invalidSignature); // GH-90000
        
        // THEN: Exception thrown
        assertThatThrownBy(() -> result.toCompletableFuture().join()) // GH-90000
                .as("Should throw WebhookException")
                .hasCauseInstanceOf(FigmaWebhookHandler.WebhookException.class) // GH-90000
                .hasMessageContaining("Invalid webhook signature");
    }
    
    /**
     * Verifies FILE_UPDATE event processing.
     *
     * GIVEN: FILE_UPDATE webhook event
     * WHEN: handleWebhook() is called // GH-90000
     * THEN: Tokens are fetched, converted, and PR created
     */
    @Test
    @DisplayName("Should process FILE_UPDATE event")
    void shouldProcessFileUpdateEvent() { // GH-90000
        // GIVEN: Mocked components
        FigmaClient figmaClient = mock(FigmaClient.class); // GH-90000
        W3CTokenConverter converter = mock(W3CTokenConverter.class); // GH-90000
        GitHubPRAutomation github = mock(GitHubPRAutomation.class); // GH-90000
        
        String webhookSecret = "test-secret";
        FigmaWebhookHandler handler = new FigmaWebhookHandler( // GH-90000
                figmaClient, converter, github, objectMapper, metrics, webhookSecret
        );
        
        String payload = """
            {
                "event_type": "FILE_UPDATE",
                "file_key": "abc123xyz",
                "file_name": "Design System"
            }
            """;
        String signature = computeHmacSha256(payload, webhookSecret); // GH-90000
        
        when(figmaClient.getVariables("abc123xyz"))
                .thenReturn(Promise.of("{\"variables\":{}}")); // GH-90000
        when(converter.convert(anyString())) // GH-90000
                .thenReturn("{\"color\":{}}"); // GH-90000
        when(github.createPullRequest(anyString(), anyString(), anyString(), // GH-90000
                anyString(), anyString(), anyString())) // GH-90000
                .thenReturn(Promise.of("https://github.com/pr/1"));
        
        // WHEN: Process event
        handler.handleWebhook(payload, signature).toCompletableFuture().join(); // GH-90000
        
        // THEN: Workflow executed
        verify(figmaClient, times(1)).getVariables("abc123xyz");
        verify(converter, times(1)).convert(anyString()); // GH-90000
        verify(github, times(1)).createPullRequest( // GH-90000
                eq("design-tokens.json"),
                anyString(), // GH-90000
                anyString(), // GH-90000
                anyString(), // GH-90000
                anyString(), // GH-90000
                anyString() // GH-90000
        );
    }
    
    /**
     * Verifies FILE_DELETE event is ignored.
     *
     * GIVEN: FILE_DELETE webhook event
     * WHEN: handleWebhook() is called // GH-90000
     * THEN: Event is ignored, no processing occurs
     */
    @Test
    @DisplayName("Should ignore FILE_DELETE event")
    void shouldIgnoreFileDeleteEvent() { // GH-90000
        // GIVEN: Handler
        FigmaClient figmaClient = mock(FigmaClient.class); // GH-90000
        W3CTokenConverter converter = mock(W3CTokenConverter.class); // GH-90000
        GitHubPRAutomation github = mock(GitHubPRAutomation.class); // GH-90000
        
        String webhookSecret = "test-secret";
        FigmaWebhookHandler handler = new FigmaWebhookHandler( // GH-90000
                figmaClient, converter, github, objectMapper, metrics, webhookSecret
        );
        
        String payload = "{\"event_type\":\"FILE_DELETE\",\"file_key\":\"abc123\"}";
        String signature = computeHmacSha256(payload, webhookSecret); // GH-90000
        
        // WHEN: Process delete event
        handler.handleWebhook(payload, signature).toCompletableFuture().join(); // GH-90000
        
        // THEN: No API calls made
        verifyNoInteractions(figmaClient, converter, github); // GH-90000
    }
    
    // ========================================================================
    // Helper Methods
    // ========================================================================
    
    /**
     * Compute HMAC-SHA256 signature for testing
     *
     * @param data Data to sign
     * @param secret Secret key
     * @return Hex signature
     */
    private String computeHmacSha256(String data, String secret) { // GH-90000
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = 
                    new javax.crypto.spec.SecretKeySpec( // GH-90000
                            secret.getBytes("UTF-8"), 
                            "HmacSHA256"
                    );
            mac.init(keySpec); // GH-90000
            byte[] hash = mac.doFinal(data.getBytes("UTF-8"));
            
            StringBuilder result = new StringBuilder(); // GH-90000
            for (byte b : hash) { // GH-90000
                result.append(String.format("%02x", b)); // GH-90000
            }
            return result.toString(); // GH-90000
            
        } catch (Exception e) { // GH-90000
            throw new RuntimeException("Failed to compute signature", e); // GH-90000
        }
    }
}
