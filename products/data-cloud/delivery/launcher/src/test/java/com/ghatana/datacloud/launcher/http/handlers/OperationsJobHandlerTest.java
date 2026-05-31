package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.operations.InMemoryOperationRecorder;
import com.ghatana.datacloud.operations.OperationKind;
import com.ghatana.datacloud.operations.OperationRecord;
import com.ghatana.datacloud.operations.OperationStatus;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for unified operation job API
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("OperationsJobHandler")
class OperationsJobHandlerTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("lists tenant-scoped operation jobs")
    void listsTenantScopedOperationJobs() throws Exception {
        InMemoryOperationRecorder recorder = new InMemoryOperationRecorder();
        OperationRecord tenantOperation = recorder.record(OperationRecord.create(
            "tenant-a",
            OperationKind.CONNECTOR_SYNC,
            OperationStatus.RUNNING,
            "connector",
            "connection-1",
            "Connector sync",
            "Connector sync requested",
            "operator-1",
            "corr-1",
            true,
            Map.of()));
        recorder.record(OperationRecord.create(
            "tenant-b",
            OperationKind.MEDIA_PROCESSING,
            OperationStatus.BLOCKED,
            "media-artifact",
            "artifact-1",
            "Media transcription",
            "Runtime unavailable",
            "operator-2",
            "corr-2",
            false,
            Map.of()));

        OperationsJobHandler handler = new OperationsJobHandler(http(), recorder);
        HttpResponse response = runPromise(() -> handler.handleListJobs(request(
            HttpMethod.GET,
            "/api/v1/operations/jobs",
            "operations:jobs:read")));

        assertThat(response.getCode()).isEqualTo(200);
        Map<String, Object> body = parse(response);
        assertThat(body).containsEntry("tenantId", "tenant-a");
        assertThat(body).containsEntry("count", 1);
        assertThat(body).containsEntry("storageMode", "volatile");
        List<Map<String, Object>> items = MAPPER.convertValue(body.get("items"), new TypeReference<>() {});
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("operationId", tenantOperation.operationId());
            assertThat(item).containsEntry("status", "RUNNING");
            assertThat(item).containsEntry("kind", "CONNECTOR_SYNC");
        });
    }

    @Test
    @DisplayName("cancels cancellable operation")
    void cancelsCancellableOperation() throws Exception {
        InMemoryOperationRecorder recorder = new InMemoryOperationRecorder();
        OperationRecord operation = recorder.record(OperationRecord.create(
            "tenant-a",
            OperationKind.PIPELINE_EXECUTION,
            OperationStatus.RUNNING,
            "pipeline",
            "pipeline-1",
            "Pipeline execution",
            "Pipeline running",
            "operator-1",
            "corr-1",
            true,
            Map.of()));

        OperationsJobHandler handler = new OperationsJobHandler(http(), recorder);
        HttpResponse response = runPromise(() -> handler.handleCancelJob(request(
            HttpMethod.POST,
            "/api/v1/operations/jobs/" + operation.operationId() + "/cancel",
            "operations:jobs:cancel")));

        assertThat(response.getCode()).isEqualTo(200);
        Map<String, Object> body = parse(response);
        assertThat(body).containsEntry("operationId", operation.operationId());
        assertThat(body).containsEntry("status", "CANCELLED");
    }

    private static HttpHandlerSupport http() {
        return new HttpHandlerSupport(
            MAPPER,
            "*",
            "GET,POST,PUT,DELETE,OPTIONS",
            "Content-Type,Authorization,X-Permissions",
            false,
            "local");
    }

    private static HttpRequest request(HttpMethod method, String path, String permissions) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getPath()).thenReturn(path);
        when(request.getHeader(HttpHeaders.of("X-Tenant-Id"))).thenReturn("tenant-a");
        when(request.getHeader(HttpHeaders.of("X-Permissions"))).thenReturn(permissions);
        when(request.getAttachment(Principal.class)).thenReturn(new Principal("operator-1", List.of("OPERATOR"), "tenant-a"));
        when(request.getPathParameter("operationId")).thenAnswer(invocation -> {
            String marker = "/api/v1/operations/jobs/";
            if (!path.startsWith(marker)) {
                return null;
            }
            String remaining = path.substring(marker.length());
            int slash = remaining.indexOf('/');
            return slash >= 0 ? remaining.substring(0, slash) : remaining;
        });
        return request;
    }

    private static Map<String, Object> parse(HttpResponse response) throws Exception {
        return MAPPER.readValue(response.getBody().getString(StandardCharsets.UTF_8), new TypeReference<>() {});
    }
}
