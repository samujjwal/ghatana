package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.PromptLifecycleService;
import com.ghatana.yappc.ai.PromptTemplateRegistry;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies admin prompt version APIs list, rollback, rebalance weights, and audit canonical records
 * @doc.layer api
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPromptVersionController")
class AdminPromptVersionControllerTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-1";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private DataCloudClient dataCloudClient;

    @Test
    @DisplayName("lists prompt versions with active version, metrics, score, and weight")
    void listVersionsReturnsActiveMetricsAndWeights() {
        when(dataCloudClient.query(eq(TENANT_ID), eq(AdminPromptVersionController.VERSION_COLLECTION), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(List.of(DataCloudClient.Entity.of(
                        "intent-v2",
                        AdminPromptVersionController.VERSION_COLLECTION,
                        promptVersion("intent-v2", true, 0.75)))));

        HttpResponse response = runPromise(() -> controller().listVersions(get("/api/admin/prompt-versions")));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getBody().asString(StandardCharsets.UTF_8))
                .contains("\"id\":\"intent-v2\"")
                .contains("\"active\":true")
                .contains("\"weight\":0.75")
                .contains("\"successRate\":0.98");
    }

    @Test
    @DisplayName("rollback flips active prompt version and writes audit")
    void rollbackVersionUpdatesActiveStateAndAudit() throws Exception {
        when(dataCloudClient.findById(TENANT_ID, AdminPromptVersionController.VERSION_COLLECTION, "intent-v1"))
                .thenReturn(Promise.of(Optional.of(DataCloudClient.Entity.of(
                        "intent-v1",
                        AdminPromptVersionController.VERSION_COLLECTION,
                        promptVersion("intent-v1", false, 0.25)))));
        when(dataCloudClient.query(eq(TENANT_ID), eq(AdminPromptVersionController.VERSION_COLLECTION), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(List.of(
                        DataCloudClient.Entity.of("intent-v1", AdminPromptVersionController.VERSION_COLLECTION,
                                promptVersion("intent-v1", false, 0.25)),
                        DataCloudClient.Entity.of("intent-v2", AdminPromptVersionController.VERSION_COLLECTION,
                                promptVersion("intent-v2", true, 0.75)))));
        when(dataCloudClient.save(eq(TENANT_ID), anyString(), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of(
                        String.valueOf(((Map<?, ?>) invocation.getArgument(2)).get("id")),
                        invocation.getArgument(1),
                        invocation.getArgument(2))));

        HttpResponse response = runPromise(() -> controller().rollbackVersion(postWithPath(
                "/api/admin/prompt-versions/intent-v1/rollback",
                "versionId",
                "intent-v1",
                Map.of("reason", "Restore safer prompt"))));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getBody().asString(StandardCharsets.UTF_8))
                .contains("\"active\":true")
                .contains("\"previousActiveVersionId\":\"intent-v2\"");

        ArgumentCaptor<Map<String, Object>> captor = typedMapCaptor();
        verify(dataCloudClient, times(2)).save(eq(TENANT_ID), eq(AdminPromptVersionController.VERSION_COLLECTION), captor.capture());
        verify(dataCloudClient).save(eq(TENANT_ID), eq(AdminPromptVersionController.AUDIT_COLLECTION), captor.capture());
        assertThat(captor.getAllValues().get(0)).containsEntry("active", true);
        assertThat(captor.getAllValues().get(1)).containsEntry("active", false);
        assertThat(captor.getAllValues().get(2))
                .containsEntry("eventType", "ROLLED_BACK")
                .containsEntry("reason", "Restore safer prompt");
    }

    @Test
    @DisplayName("weight update persists score-backed rebalance and audit")
    void updateWeightsPersistsWeightsAndAudit() throws Exception {
        when(dataCloudClient.findById(TENANT_ID, AdminPromptVersionController.VERSION_COLLECTION, "intent-v2"))
                .thenReturn(Promise.of(Optional.of(DataCloudClient.Entity.of(
                        "intent-v2",
                        AdminPromptVersionController.VERSION_COLLECTION,
                        promptVersion("intent-v2", true, 0.75)))));
        when(dataCloudClient.save(eq(TENANT_ID), anyString(), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of(
                        String.valueOf(((Map<?, ?>) invocation.getArgument(2)).get("id")),
                        invocation.getArgument(1),
                        invocation.getArgument(2))));

        HttpResponse response = runPromise(() -> controller().updateWeights(post(
                "/api/admin/prompt-versions/weights",
                Map.of("weights", Map.of("intent-v2", 0.65)))));

        assertThat(response.getCode()).isEqualTo(200);
        ArgumentCaptor<Map<String, Object>> captor = typedMapCaptor();
        verify(dataCloudClient).save(eq(TENANT_ID), eq(AdminPromptVersionController.VERSION_COLLECTION), captor.capture());
        verify(dataCloudClient).save(eq(TENANT_ID), eq(AdminPromptVersionController.AUDIT_COLLECTION), captor.capture());
        assertThat(captor.getAllValues().get(0)).containsEntry("weight", 0.65);
        assertThat(captor.getAllValues().get(1)).containsEntry("eventType", "WEIGHTS_REBALANCED");
    }

    private AdminPromptVersionController controller() {
        return new AdminPromptVersionController(
                dataCloudClient,
                objectMapper,
                new PromptLifecycleService(new PromptTemplateRegistry(), event -> Promise.complete()));
    }

    private Map<String, Object> promptVersion(String id, boolean active, double weight) {
        Map<String, Object> version = new java.util.LinkedHashMap<>();
        version.put("id", id);
        version.put("promptName", "intent.capture");
        version.put("promptVersion", id);
        version.put("content", "Capture intent safely");
        version.put("contentHash", "sha256-" + id);
        version.put("description", "Prompt " + id);
        version.put("author", "admin-user");
        version.put("active", active);
        version.put("weight", weight);
        version.put("createdAt", "2026-05-26T00:00:00Z");
        version.put("metrics", Map.of(
                "avgCost", 0.01,
                "avgLatencyMs", 120,
                "successRate", 0.98,
                "sampleCount", 42));
        return version;
    }

    private HttpRequest get(String url) {
        HttpRequest request = HttpRequest.get("http://localhost" + url).build();
        request.attach(Principal.class, new Principal("admin-user", List.of("admin"), TENANT_ID));
        return request;
    }

    private HttpRequest post(String url, Object body) throws Exception {
        HttpRequest request = HttpRequest.post("http://localhost" + url)
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-prompt-1")
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(body)))
                .build();
        request.attach(Principal.class, new Principal("admin-user", List.of("admin"), TENANT_ID));
        return request;
    }

    private HttpRequest postWithPath(String url, String key, String value, Object body) throws Exception {
        HttpRequest request = post(url, body);
        putPathParameter(request, key, value);
        return request;
    }

    private static void putPathParameter(HttpRequest request, String key, String value) {
        try {
            Method method = HttpRequest.class.getDeclaredMethod("putPathParameter", String.class, String.class);
            method.setAccessible(true);
            method.invoke(request, key, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> typedMapCaptor() {
        return ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
    }
}
