package com.ghatana.yappc.domain.workflow.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.products.yappc.domain.workflow.AiWorkflowInstance;
import com.ghatana.products.yappc.domain.workflow.AiWorkflowService;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WorkflowProblemHandler} — RFC-7807 error mapping.
 *
 * @doc.type class
 * @doc.purpose RFC-7807 problem handler unit tests (F-Y045 / K-Y12)
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("WorkflowProblemHandler — RFC-7807 error mapping")
class WorkflowProblemHandlerTest {

    private WorkflowProblemHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new WorkflowProblemHandler(objectMapper);
    }

    // ── WorkflowNotFoundException → 404 ───────────────────────────────────────

    @Test
    @DisplayName("WorkflowNotFoundException maps to 404 with problem+json body")
    void workflowNotFoundException_maps404() throws Exception {
        Exception ex = new AiWorkflowService.WorkflowNotFoundException("wf-missing not found");

        HttpResponse response = handler.handle(ex, "/api/v1/workflows/wf-missing", "corr-001");

        assertThat(response.getCode()).isEqualTo(404);
        assertProblemJson(response, 404, "workflow-not-found", "corr-001");
    }

    // ── InvalidWorkflowStateException → 409 ──────────────────────────────────

    @Test
    @DisplayName("InvalidWorkflowStateException maps to 409 with problem+json body")
    void invalidStateException_maps409() throws Exception {
        Exception ex = new AiWorkflowService.InvalidWorkflowStateException("wf-1", AiWorkflowInstance.WorkflowStatus.IN_PROGRESS, "Cannot start: already running");

        HttpResponse response = handler.handle(ex, "/api/v1/workflows/wf-1/start", "corr-002");

        assertThat(response.getCode()).isEqualTo(409);
        assertProblemJson(response, 409, "invalid-workflow-state", "corr-002");
    }

    // ── WorkflowExecutionException → 500 ─────────────────────────────────────

    @Test
    @DisplayName("WorkflowExecutionException maps to 500 with problem+json body")
    void executionException_maps500() throws Exception {
        Exception ex = new AiWorkflowService.WorkflowExecutionException("wf-1", "Agent timeout");

        HttpResponse response = handler.handle(ex, "/api/v1/workflows/wf-1/execute", "corr-003");

        assertThat(response.getCode()).isEqualTo(500);
        assertProblemJson(response, 500, "workflow-execution-error", "corr-003");
    }

    // ── Unexpected exception → 500 ────────────────────────────────────────────

    @Test
    @DisplayName("Unexpected exception maps to 500 with problem+json body")
    void unexpectedException_maps500() throws Exception {
        Exception ex = new RuntimeException("Something went wrong");

        HttpResponse response = handler.handle(ex, "/api/v1/workflows/wf-1/start", "corr-004");

        assertThat(response.getCode()).isEqualTo(500);
        assertProblemJson(response, 500, "unexpected-error", "corr-004");
    }

    // ── Null instance and correlationId ──────────────────────────────────────

    @Test
    @DisplayName("Null instance and correlationId are tolerated")
    void nullInstanceAndCorrelation_tolerated() throws Exception {
        Exception ex = new AiWorkflowService.WorkflowNotFoundException("wf-x not found");

        HttpResponse response = handler.handle(ex, null, null);

        assertThat(response.getCode()).isEqualTo(404);
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.has("status")).isTrue();
        assertThat(json.get("status").asInt()).isEqualTo(404);
    }

    // ── asPromiseHandler ──────────────────────────────────────────────────────

    @Test
    @DisplayName("asPromiseHandler wraps response in a completed promise")
    void asPromiseHandler_wrapsInPromise() throws Exception {
        var fn = handler.asPromiseHandler("/api/v1/workflows/wf-1", "corr-005");

        Promise<HttpResponse> promise = fn.apply(
            new AiWorkflowService.WorkflowNotFoundException("wf-1 not found")
        );

        // Promise should be completed synchronously
        assertThat(promise.isComplete()).isTrue();
        HttpResponse response = promise.getResult();
        assertThat(response.getCode()).isEqualTo(404);
    }

    // ── Problem type URI prefix ───────────────────────────────────────────────

    @Test
    @DisplayName("Problem type uses the YAPPC problem base URI")
    void problemType_usesBaseUri() throws Exception {
        Exception ex = new AiWorkflowService.WorkflowNotFoundException("wf-z not found");
        HttpResponse response = handler.handle(ex, null, null);

        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("type").asText()).startsWith(WorkflowProblemDetail.PROBLEM_BASE_URI);
    }

    // ── Content-Type ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Response Content-Type is application/problem+json")
    void contentType_isProblemJson() throws Exception {
        HttpResponse response = handler.handle(
            new AiWorkflowService.WorkflowNotFoundException("wf-1 not found"),
            null, null
        );

        String contentType = response.getHeader(HttpHeaders.of("Content-Type"));
        assertThat(contentType).contains("application/problem+json");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertProblemJson(HttpResponse response, int expectedStatus, String typeFragment, String expectedCorrelationId) throws Exception {
        String body = new String(response.getBody().asArray(), StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(body);

        assertThat(json.get("status").asInt()).isEqualTo(expectedStatus);
        assertThat(json.get("type").asText()).contains(typeFragment);
        assertThat(json.get("title").asText()).isNotBlank();
        assertThat(json.get("timestamp").asText()).isNotBlank();
        assertThat(json.get("correlationId").asText()).isEqualTo(expectedCorrelationId);
    }
}
