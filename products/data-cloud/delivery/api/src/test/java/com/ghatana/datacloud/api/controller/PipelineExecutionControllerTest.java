package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.application.WorkflowService;
import com.ghatana.datacloud.entity.WorkflowExecution;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for pipeline execution controller hardening
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PipelineExecutionController")
@ExtendWith(MockitoExtension.class)
class PipelineExecutionControllerTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private WorkflowService workflowService;

    @Mock
    private MetricsCollector metricsCollector;

    private PipelineExecutionController controller;

    @BeforeEach
    void setUp() {
        controller = new PipelineExecutionController(workflowService, metricsCollector, MAPPER);
    }

    @Test
    @DisplayName("accepts canonical action pipeline execute route")
    void acceptsCanonicalActionPipelineExecuteRoute() throws Exception {
        UUID workflowId = UUID.randomUUID();
        WorkflowExecution execution = WorkflowExecution.builder()
            .id(UUID.randomUUID())
            .tenantId("tenant-a")
            .workflowId(workflowId)
            .status(WorkflowExecution.Status.RUNNING)
            .startedBy("user-a")
            .startedAt(Instant.now())
            .build();

        when(workflowService.executeWorkflow(eq("tenant-a"), eq(workflowId), eq("user-a"), anyMap()))
            .thenReturn(Promise.of(execution));

        HttpRequest request = mockExecuteRequest(
            "/api/v1/action/pipelines/" + workflowId + "/execute",
            "tenant-a",
            "user-a",
            "{\"inputVariables\":{\"priority\":\"high\"}}"
        );

        HttpResponse response = runPromise(() -> controller.handle(request));
        Map<String, Object> body = parseBody(response);

        assertThat(response.getCode()).isEqualTo(202);
        assertThat(body).containsEntry("workflowId", workflowId.toString());
        assertThat(body).containsEntry("triggeredBy", "user-a");
        verify(workflowService).executeWorkflow(eq("tenant-a"), eq(workflowId), eq("user-a"), anyMap());
    }

    @Test
    @DisplayName("returns 401 when X-User-ID header is missing")
    void returnsUnauthorizedWhenUserHeaderMissing() {
        UUID workflowId = UUID.randomUUID();
        HttpRequest request = mockExecuteRequest(
            "/api/v1/action/pipelines/" + workflowId + "/execute",
            "tenant-a",
            null,
            null
        );

        HttpResponse response = runPromise(() -> controller.handle(request));

        assertThat(response.getCode()).isEqualTo(401);
        verify(workflowService, never()).executeWorkflow(anyString(), any(UUID.class), anyString(), anyMap());
    }

    @Test
    @DisplayName("returns 400 when inputVariables is not an object")
    void returnsBadRequestForNonObjectInputVariables() {
        UUID workflowId = UUID.randomUUID();
        HttpRequest request = mockExecuteRequest(
            "/api/v1/action/pipelines/" + workflowId + "/execute",
            "tenant-a",
            "user-a",
            "{\"inputVariables\":\"invalid\"}"
        );

        HttpResponse response = runPromise(() -> controller.handle(request));

        assertThat(response.getCode()).isEqualTo(400);
        verify(workflowService, never()).executeWorkflow(anyString(), any(UUID.class), anyString(), anyMap());
    }

    private HttpRequest mockExecuteRequest(String path, String tenantId, String userId, String bodyJson) {
        HttpRequest request = org.mockito.Mockito.mock(HttpRequest.class);
        when(request.getPath()).thenReturn(path);
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getHeader(HttpHeaders.of("X-Tenant-ID"))).thenReturn(tenantId);
        when(request.getHeader(HttpHeaders.of("X-User-ID"))).thenReturn(userId);
        if (bodyJson != null) {
            when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(bodyJson.getBytes(StandardCharsets.UTF_8))));
        }
        return request;
    }

    private static Map<String, Object> parseBody(HttpResponse response) throws Exception {
        String payload = response.getBody().getString(StandardCharsets.UTF_8);
        return MAPPER.readValue(payload, new TypeReference<>() {});
    }
}
