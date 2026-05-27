package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.PromptLifecycleService;
import com.ghatana.yappc.ai.PromptTemplateRegistry;
import com.ghatana.yappc.ai.abtesting.ABTestingEvaluationService;
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
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies admin A/B testing APIs persist experiments, audit, evaluation, rollback, and learning evidence
 * @doc.layer api
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAbTestingController")
class AdminAbTestingControllerTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-1";
    private static final String EXPERIMENT_ID = "exp-1";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private DataCloudClient dataCloudClient;

    @Test
    @DisplayName("create experiment persists canonical experiment, audit, and learning evidence records")
    void createExperimentPersistsAuditAndLearningEvidence() throws Exception {
        when(dataCloudClient.save(eq(TENANT_ID), anyString(), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of(
                        String.valueOf(((Map<?, ?>) invocation.getArgument(2)).get("id")),
                        invocation.getArgument(1),
                        invocation.getArgument(2))));

        HttpResponse response = runPromise(() -> controller().createExperiment(post(
                "/api/admin/ab-experiments",
                Map.of(
                        "experimentName", "Intent Prompt Test",
                        "description", "Compare capture prompt variants",
                        "promptName", "intent.capture",
                        "variantA", "short prompt",
                        "variantB", "grounded prompt"))));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getBody().asString(StandardCharsets.UTF_8))
                .contains("\"name\":\"Intent Prompt Test\"")
                .contains("\"status\":\"running\"");

        ArgumentCaptor<Map<String, Object>> captor = typedMapCaptor();
        verify(dataCloudClient).save(eq(TENANT_ID), eq(AdminAbTestingController.EXPERIMENT_COLLECTION), captor.capture());
        verify(dataCloudClient).save(eq(TENANT_ID), eq(AdminAbTestingController.AUDIT_COLLECTION), captor.capture());
        verify(dataCloudClient).save(eq(TENANT_ID), eq(AdminAbTestingController.LEARNING_EVIDENCE_COLLECTION), captor.capture());
        assertThat(captor.getAllValues().get(0))
                .containsEntry("promptName", "intent.capture")
                .containsEntry("status", "running");
        assertThat(captor.getAllValues().get(1)).containsEntry("eventType", "CREATED");
        assertThat(captor.getAllValues().get(2))
                .containsEntry("projectId", "admin-ab-testing")
                .extracting(record -> ((Map<?, ?>) record.get("metadata")).get("eventType"))
                .isEqualTo("AB_EXPERIMENT_CREATED");
    }

    @Test
    @DisplayName("promote winner evaluates metrics, persists rollback metadata, audit, and learning evidence")
    void promoteWinnerPersistsEvaluationRollbackAndLearningEvidence() throws Exception {
        when(dataCloudClient.findById(TENANT_ID, AdminAbTestingController.EXPERIMENT_COLLECTION, EXPERIMENT_ID))
                .thenReturn(Promise.of(Optional.of(DataCloudClient.Entity.of(
                        EXPERIMENT_ID,
                        AdminAbTestingController.EXPERIMENT_COLLECTION,
                        completedExperiment()))));
        when(dataCloudClient.save(eq(TENANT_ID), anyString(), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of(
                        String.valueOf(((Map<?, ?>) invocation.getArgument(2)).get("id")),
                        invocation.getArgument(1),
                        invocation.getArgument(2))));

        HttpResponse response = runPromise(() -> controller().promoteWinner(postWithPath(
                "/api/admin/ab-experiments/" + EXPERIMENT_ID + "/promote",
                "experimentId",
                EXPERIMENT_ID,
                Map.of("variantId", "var-b", "reason", "Higher quality and conversion"))));

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"winnerId\":\"var-b\"")
                .contains("\"rollbackTargetWinnerId\":\"var-a\"")
                .contains("\"reversible\":true")
                .contains("\"modelEvaluation\"");

        ArgumentCaptor<Map<String, Object>> captor = typedMapCaptor();
        verify(dataCloudClient).save(eq(TENANT_ID), eq(AdminAbTestingController.EXPERIMENT_COLLECTION), captor.capture());
        verify(dataCloudClient).save(eq(TENANT_ID), eq(AdminAbTestingController.AUDIT_COLLECTION), captor.capture());
        verify(dataCloudClient).save(eq(TENANT_ID), eq(AdminAbTestingController.LEARNING_EVIDENCE_COLLECTION), captor.capture());
        assertThat(captor.getAllValues().get(0))
                .containsEntry("winnerId", "var-b")
                .containsEntry("rollbackTargetWinnerId", "var-a")
                .containsEntry("reversible", true);
        assertThat(captor.getAllValues().get(1))
                .containsEntry("eventType", "WINNER_PROMOTED")
                .containsEntry("reason", "Higher quality and conversion");
        assertThat(captor.getAllValues().get(2))
                .extracting(record -> ((Map<?, ?>) record.get("metadata")).get("eventType"))
                .isEqualTo("AB_EXPERIMENT_WINNER_PROMOTED");
    }

    private AdminAbTestingController controller() {
        return new AdminAbTestingController(
                dataCloudClient,
                objectMapper,
                new ABTestingEvaluationService(),
                new PromptLifecycleService(new PromptTemplateRegistry(), event -> Promise.complete()));
    }

    private Map<String, Object> completedExperiment() {
        return Map.of(
                "id", EXPERIMENT_ID,
                "tenantId", TENANT_ID,
                "name", "Intent Prompt Test",
                "description", "Compare capture prompt variants",
                "status", "completed",
                "promptName", "intent.capture",
                "promptVersion", "active",
                "winnerId", "var-a",
                "createdAt", "2026-05-26T00:00:00Z",
                "variants", List.of(
                        variant("var-a", "Variant A", 1_000, 100, 0.10, 3.1),
                        variant("var-b", "Variant B", 1_000, 180, 0.18, 4.8)));
    }

    private Map<String, Object> variant(
            String id,
            String name,
            int impressions,
            int conversions,
            double conversionRate,
            double quality) {
        return Map.of(
                "variantId", id,
                "variantName", name,
                "impressions", impressions,
                "conversions", conversions,
                "conversionRate", conversionRate,
                "avgResponseTimeMs", 250.0,
                "avgCostUsd", 0.01,
                "avgQualityScore", quality,
                "statisticalSignificance", true);
    }

    private HttpRequest post(String url, Object body) throws Exception {
        HttpRequest request = HttpRequest.post("http://localhost" + url)
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-ab-1")
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
