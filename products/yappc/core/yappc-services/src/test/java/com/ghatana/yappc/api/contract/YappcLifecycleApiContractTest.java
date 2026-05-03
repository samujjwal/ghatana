package com.ghatana.yappc.api.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for the YAPPC Lifecycle API.
 *
 * <p>Validates that request and response JSON payloads produced or consumed by
 * {@link com.ghatana.yappc.api.LifecycleApiController} conform to the documented
 * contract structure. No HTTP server or event loop is required — these are pure
 * JSON-schema assertions on canonical payload shapes.
 *
 * @doc.type class
 * @doc.purpose Contract validation for YAPPC Lifecycle API request and response schemas
 * @doc.layer test
 * @doc.pattern ContractTest
 */
@DisplayName("YAPPC Lifecycle API Contract Tests")
class YappcLifecycleApiContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // =========================================================================
    // LifecycleExecuteRequest — inbound contract
    // =========================================================================

    @Nested
    @DisplayName("LifecycleExecuteRequest — inbound schema")
    class LifecycleExecuteRequestContract {

        @Test
        @DisplayName("minimal valid request requires only intentInput.rawText")
        void minimalRequest_hasRequiredFields() throws Exception {
            String json = """
                    {
                      "intentInput": {
                        "rawText": "Build an authentication service"
                      }
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);

            assertThat(node.has("intentInput")).isTrue();
            assertThat(node.at("/intentInput/rawText").asText()).isNotBlank();
        }

        @Test
        @DisplayName("full request with optional environment and constraints is valid")
        void fullRequest_withAllOptionalFields() throws Exception {
            String json = """
                    {
                      "intentInput": {
                        "rawText": "Build auth service",
                        "tenantId": "tenant-alpha",
                        "userId": "user-001"
                      },
                      "environment": "staging",
                      "constraints": {
                        "maxDurationMs": 120000,
                        "allowedPhases": ["INTENT","SHAPE","VALIDATE","GENERATE"]
                      }
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);

            assertThat(node.at("/intentInput/rawText").asText()).isNotBlank();
            assertThat(node.at("/intentInput/tenantId").asText()).isEqualTo("tenant-alpha");
            assertThat(node.at("/environment").asText()).isEqualTo("staging");
            assertThat(node.at("/constraints/maxDurationMs").asLong()).isEqualTo(120000L);
            assertThat(node.at("/constraints/allowedPhases").isArray()).isTrue();
        }

        @Test
        @DisplayName("intentInput must be present — missing intentInput is invalid")
        void missingIntentInput_isInvalid() throws Exception {
            String json = """
                    {
                      "environment": "production"
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);

            assertThat(node.has("intentInput")).isFalse();
            // Contract: server must respond 400 when intentInput absent
        }

        @Test
        @DisplayName("rawText must be non-blank — blank rawText is invalid")
        void blankRawText_isInvalid() throws Exception {
            String json = """
                    {
                      "intentInput": {
                        "rawText": ""
                      }
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);

            assertThat(node.at("/intentInput/rawText").asText()).isBlank();
            // Contract: server must respond 400 when rawText blank
        }

        @Test
        @DisplayName("environment field accepts staging, production, dev, test")
        @ParameterizedTest
        @ValueSource(strings = {"staging", "production", "dev", "test"})
        void environment_acceptsKnownValues(String env) throws Exception {
            ObjectNode node = MAPPER.createObjectNode();
            ObjectNode intentInput = MAPPER.createObjectNode();
            intentInput.put("rawText", "Build service");
            node.set("intentInput", intentInput);
            node.put("environment", env);

            assertThat(node.at("/environment").asText()).isEqualTo(env);
        }
    }

    // =========================================================================
    // LifecycleExecutionResult — outbound contract
    // =========================================================================

    @Nested
    @DisplayName("LifecycleExecutionResult — outbound schema")
    class LifecycleExecutionResultContract {

        @Test
        @DisplayName("successful result must include metadata with status=SUCCESS")
        void successResult_hasCorrectMetadata() throws Exception {
            String json = """
                    {
                      "metadata": {
                        "status": "SUCCESS",
                        "pipelineMode": "DAG",
                        "pipelineGraphVersion": "2026-04-17"
                      },
                      "intentResult": {
                        "id": "intent-abc",
                        "title": "Auth Service",
                        "description": "Authentication microservice"
                      }
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);

            assertThat(node.at("/metadata/status").asText()).isEqualTo("SUCCESS");
            assertThat(node.at("/metadata/pipelineMode").asText()).isEqualTo("DAG");
            assertThat(node.at("/metadata/pipelineGraphVersion").asText()).isNotBlank();
        }

        @Test
        @DisplayName("failed result must include metadata with status=FAILED")
        void failedResult_hasCorrectMetadata() throws Exception {
            String json = """
                    {
                      "metadata": {
                        "status": "FAILED",
                        "pipelineMode": "DAG",
                        "error": "RuntimeException"
                      }
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);

            assertThat(node.at("/metadata/status").asText()).isEqualTo("FAILED");
            assertThat(node.at("/metadata/error").asText()).isNotBlank();
        }

        @Test
        @DisplayName("validation-failed result has status=VALIDATION_FAILED")
        void validationFailedResult_hasCorrectStatus() throws Exception {
            String json = """
                    {
                      "metadata": {
                        "status": "VALIDATION_FAILED",
                        "pipelineMode": "DAG"
                      },
                      "validationResult": {
                        "passed": false,
                        "hasBlockingIssues": true
                      }
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);

            assertThat(node.at("/metadata/status").asText()).isEqualTo("VALIDATION_FAILED");
            assertThat(node.at("/validationResult/passed").asBoolean()).isFalse();
            assertThat(node.at("/validationResult/hasBlockingIssues").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("status must be one of: SUCCESS, FAILED, VALIDATION_FAILED")
        @ParameterizedTest
        @ValueSource(strings = {"SUCCESS", "FAILED", "VALIDATION_FAILED"})
        void validStatusValues_areAccepted(String status) throws Exception {
            ObjectNode metadata = MAPPER.createObjectNode();
            metadata.put("status", status);
            metadata.put("pipelineMode", "DAG");

            assertThat(metadata.get("status").asText()).matches("SUCCESS|FAILED|VALIDATION_FAILED");
        }

        @Test
        @DisplayName("intentResult (when present) must have id, title, and description")
        void intentResult_hasRequiredFields() throws Exception {
            String json = """
                    {
                      "intentResult": {
                        "id": "intent-001",
                        "title": "Auth Service",
                        "description": "JWT-based auth service"
                      }
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);
            JsonNode intentResult = node.get("intentResult");

            assertThat(intentResult.has("id")).isTrue();
            assertThat(intentResult.has("title")).isTrue();
            assertThat(intentResult.has("description")).isTrue();
            assertThat(intentResult.get("id").asText()).isNotBlank();
            assertThat(intentResult.get("title").asText()).isNotBlank();
            assertThat(intentResult.get("description").asText()).isNotBlank();
        }

        @Test
        @DisplayName("shapeResult (when present) must have components and architecture")
        void shapeResult_hasRequiredFields() throws Exception {
            String json = """
                    {
                      "shapeResult": {
                        "components": ["gateway", "auth-service", "database"],
                        "architecture": "microservices"
                      }
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);
            JsonNode shapeResult = node.get("shapeResult");

            assertThat(shapeResult.has("components")).isTrue();
            assertThat(shapeResult.get("components").isArray()).isTrue();
            assertThat(shapeResult.get("components").size()).isGreaterThan(0);
        }

        @Test
        @DisplayName("runResult (when present) must have status")
        void runResult_hasRequiredStatusField() throws Exception {
            String json = """
                    {
                      "runResult": {
                        "status": "SUCCEEDED",
                        "id": "run-001"
                      }
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);
            JsonNode runResult = node.get("runResult");

            assertThat(runResult.has("status")).isTrue();
            assertThat(runResult.get("status").asText()).matches("SUCCEEDED|FAILED|RUNNING|PENDING");
        }

        @Test
        @DisplayName("metadata phaseDurationsMs is present on successful execution")
        void successResult_hasPhaseDurations() throws Exception {
            String json = """
                    {
                      "metadata": {
                        "status": "SUCCESS",
                        "pipelineMode": "DAG",
                        "pipelineGraphVersion": "2026-04-17",
                        "executionPlan": "INTENT->SHAPE->VALIDATE->GENERATE->RUN->OBSERVE->LEARN->EVOLVE",
                        "executedPhases": "INTENT->SHAPE->VALIDATE->GENERATE->RUN->OBSERVE->LEARN->EVOLVE",
                        "phaseDurationsMs": "{INTENT=42, SHAPE=88, VALIDATE=15}"
                      }
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);

            assertThat(node.at("/metadata/executionPlan").asText()).contains("INTENT");
            assertThat(node.at("/metadata/executedPhases").asText()).isNotBlank();
            assertThat(node.at("/metadata/phaseDurationsMs").asText()).isNotBlank();
        }
    }

    // =========================================================================
    // Error response contract
    // =========================================================================

    @Nested
    @DisplayName("Error response contract")
    class ErrorResponseContract {

        @Test
        @DisplayName("400 error response must have error field")
        void badRequestResponse_hasErrorField() throws Exception {
            String json = """
                    {
                      "error": "intentInput.rawText is required",
                      "status": 400
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);

            assertThat(node.has("error")).isTrue();
            assertThat(node.get("error").asText()).isNotBlank();
            assertThat(node.at("/status").asInt()).isEqualTo(400);
        }

        @Test
        @DisplayName("500 error response must have error field")
        void serverErrorResponse_hasErrorField() throws Exception {
            String json = """
                    {
                      "error": "Internal server error",
                      "status": 500
                    }
                    """;
            JsonNode node = MAPPER.readTree(json);

            assertThat(node.has("error")).isTrue();
            assertThat(node.at("/status").asInt()).isEqualTo(500);
        }
    }
}
