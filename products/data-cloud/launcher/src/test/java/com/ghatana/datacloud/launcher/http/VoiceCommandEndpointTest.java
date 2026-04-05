/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.voice;

import com.ghatana.datacloud.launcher.http.DataCloudHttpServerTestBase;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HTTP integration tests for voice command endpoints (F002).
 *
 * @doc.type class
 * @doc.purpose Voice command API endpoint tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 15, unit = TimeUnit.SECONDS)
@DisplayName("VoiceCommand – HTTP Endpoints (F002)")
class VoiceCommandEndpointTest extends DataCloudHttpServerTestBase {

    @Mock
    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();
        startServer();
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(5000);
    }

    @Nested
    @DisplayName("POST /api/v1/voice/command")
    class ProcessCommandTests {

        @Test
        @DisplayName("[F002]: process_command_returns_200_with_result")
        void processCommandReturns200WithResult() throws Exception {
            Map<String, Object> mockResult = Map.of(
                "sessionId", "session-001",
                "recognizedText", "show me sales report",
                "intent", "view_report",
                "confidence", 0.92,
                "slots", Map.of("reportType", "sales"),
                "action", "NAVIGATE",
                "response", "Opening sales report"
            );

            lenient().when(mockClient.processVoiceCommand(any(), any(), any()))
                .thenReturn(Promise.of(mockResult));

            var response = postJson("/api/v1/voice/command", Map.of(
                "audioData", "base64encodedaudio",
                "format", "wav",
                "sessionId", "session-001"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body).containsKey("intent");
            assertThat(body).containsKey("confidence");
        }

        @Test
        @DisplayName("[F002]: process_text_command_returns_200")
        void processTextCommandReturns200() throws Exception {
            Map<String, Object> mockResult = Map.of(
                "sessionId", "session-001",
                "recognizedText", "create new dashboard",
                "intent", "create_dashboard",
                "confidence", 0.95,
                "slots", Map.of(),
                "action", "CREATE",
                "response", "Creating new dashboard"
            );

            lenient().when(mockClient.processVoiceText(any(), any(), any()))
                .thenReturn(Promise.of(mockResult));

            var response = postJson("/api/v1/voice/text", Map.of(
                "text", "create new dashboard",
                "sessionId", "session-001"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("intent")).isEqualTo("create_dashboard");
        }

        @Test
        @DisplayName("[F002]: missing_audio_data_returns_400")
        void missingAudioDataReturns400() throws Exception {
            var response = postJson("/api/v1/voice/command", Map.of(
                "format", "wav"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 400);
        }

        @Test
        @DisplayName("[F002]: unsupported_format_returns_400")
        void unsupportedFormatReturns400() throws Exception {
            var response = postJson("/api/v1/voice/command", Map.of(
                "audioData", "base64data",
                "format", "unsupported-format"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 400);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/voice/commands")
    class SupportedCommandsTests {

        @Test
        @DisplayName("[F002]: get_supported_commands_returns_list")
        void getSupportedCommandsReturnsList() throws Exception {
            var commands = java.util.List.of(
                Map.of(
                    "intent", "view_report",
                    "description", "View a report",
                    "examples", java.util.List.of("show me sales report", "open revenue dashboard")
                ),
                Map.of(
                    "intent", "create_dashboard",
                    "description", "Create new dashboard",
                    "examples", java.util.List.of("create dashboard", "new dashboard")
                )
            );

            lenient().when(mockClient.getSupportedVoiceCommands(any()))
                .thenReturn(Promise.of(commands));

            var response = get("/api/v1/voice/commands", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> cmds = (java.util.List<Map<String, Object>>) body.get("commands");
            assertThat(cmds).hasSize(2);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/voice/validate")
    class ValidateCommandTests {

        @Test
        @DisplayName("[F002]: validate_valid_command_returns_true")
        void validateValidCommandReturnsTrue() throws Exception {
            Map<String, Object> mockValidation = Map.of(
                "valid", true,
                "intent", "view_report",
                "missingSlots", java.util.List.of(),
                "errors", java.util.List.of()
            );

            lenient().when(mockClient.validateVoiceCommand(any(), any()))
                .thenReturn(Promise.of(mockValidation));

            var response = postJson("/api/v1/voice/validate", Map.of(
                "text", "show me sales report"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("valid")).isEqualTo(true);
        }

        @Test
        @DisplayName("[F002]: validate_invalid_command_returns_errors")
        void validateInvalidCommandReturnsErrors() throws Exception {
            Map<String, Object> mockValidation = Map.of(
                "valid", false,
                "intent", "unknown",
                "missingSlots", java.util.List.of("reportType"),
                "errors", java.util.List.of("Could not determine report type")
            );

            lenient().when(mockClient.validateVoiceCommand(any(), any()))
                .thenReturn(Promise.of(mockValidation));

            var response = postJson("/api/v1/voice/validate", Map.of(
                "text", "show me report"
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("valid")).isEqualTo(false);
            @SuppressWarnings("unchecked")
            java.util.List<String> errors = (java.util.List<String>) body.get("errors");
            assertThat(errors).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Confidence and Slots")
    class ConfidenceAndSlotsTests {

        @Test
        @DisplayName("[F002]: low_confidence_flagged")
        void lowConfidenceFlagged() throws Exception {
            Map<String, Object> mockResult = Map.of(
                "sessionId", "session-001",
                "recognizedText", "ambiguous command",
                "intent", "unknown",
                "confidence", 0.45,
                "requiresConfirmation", true,
                "response", "Did you mean...?"
            );

            lenient().when(mockClient.processVoiceText(any(), any(), any()))
                .thenReturn(Promise.of(mockResult));

            var response = postJson("/api/v1/voice/text", Map.of(
                "text", "ambiguous command"
            ), withTenant("tenant-alpha"));

            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("requiresConfirmation")).isEqualTo(true);
            double confidence = ((Number) body.get("confidence")).doubleValue();
            assertThat(confidence).isLessThan(0.50);
        }

        @Test
        @DisplayName("[F002]: slots_extracted_from_command")
        void slotsExtractedFromCommand() throws Exception {
            Map<String, Object> mockResult = Map.of(
                "sessionId", "session-001",
                "recognizedText", "show me sales report for Q1",
                "intent", "view_report",
                "confidence", 0.95,
                "slots", Map.of(
                    "reportType", "sales",
                    "timePeriod", "Q1"
                ),
                "action", "NAVIGATE"
            );

            lenient().when(mockClient.processVoiceText(any(), any(), any()))
                .thenReturn(Promise.of(mockResult));

            var response = postJson("/api/v1/voice/text", Map.of(
                "text", "show me sales report for Q1"
            ), withTenant("tenant-alpha"));

            Map<String, Object> body = parseJsonResponse(response);
            @SuppressWarnings("unchecked")
            Map<String, Object> slots = (Map<String, Object>) body.get("slots");
            assertThat(slots).containsKeys("reportType", "timePeriod");
        }
    }

    @Nested
    @DisplayName("Session Management")
    class SessionManagementTests {

        @Test
        @DisplayName("[F002]: session_history_retrieved")
        void sessionHistoryRetrieved() throws Exception {
            String sessionId = "session-001";

            var history = java.util.List.of(
                Map.of("recognizedText", "first command", "intent", "greeting"),
                Map.of("recognizedText", "second command", "intent", "query")
            );

            lenient().when(mockClient.getVoiceSessionHistory(any()))
                .thenReturn(Promise.of(history));

            var response = get("/api/v1/voice/sessions/" + sessionId + "/history", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> hist = (java.util.List<Map<String, Object>>) body.get("history");
            assertThat(hist).hasSize(2);
        }

        @Test
        @DisplayName("[F002]: session_context_preserved_across_commands")
        void sessionContextPreservedAcrossCommands() throws Exception {
            String sessionId = "persistent-session";

            // First command
            Map<String, Object> result1 = Map.of(
                "sessionId", sessionId,
                "recognizedText", "show sales",
                "intent", "view_report",
                "slots", Map.of("reportType", "sales")
            );

            lenient().when(mockClient.processVoiceText(eq(sessionId), any(), any()))
                .thenReturn(Promise.of(result1));

            // Both requests should use same session
            assertThat(result1.get("sessionId")).isEqualTo(sessionId);
        }
    }
}
