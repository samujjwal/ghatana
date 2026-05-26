package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
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
 * @doc.purpose Verifies admin feature flag APIs persist canonical Data Cloud records and audit entries
 * @doc.layer api
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminFeatureFlagController")
class AdminFeatureFlagControllerTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-1";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private DataCloudClient dataCloudClient;

    @Test
    @DisplayName("lists tenant feature flags from Data Cloud")
    void listFlagsLoadsCanonicalRecords() {
        when(dataCloudClient.query(eq(TENANT_ID), eq(AdminFeatureFlagController.FLAG_COLLECTION), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(List.of(DataCloudClient.Entity.of(
                        "phase.advance",
                        AdminFeatureFlagController.FLAG_COLLECTION,
                        Map.of(
                                "key", "phase.advance",
                                "enabled", true,
                                "description", "Allow phase advance",
                                "rolloutPercentage", 100,
                                "createdAt", "2026-05-26T00:00:00Z",
                                "updatedAt", "2026-05-26T00:00:00Z",
                                "updatedBy", "admin")))));

        HttpResponse response = runPromise(() -> controller().listFlags(get("/api/admin/feature-flags")));

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"key\":\"phase.advance\"")
                .contains("\"provider\":\"DATA_CLOUD_CANONICAL\"");
    }

    @Test
    @DisplayName("set flag persists updated flag and audit event")
    void setFlagPersistsFlagAndAudit() throws Exception {
        when(dataCloudClient.findById(TENANT_ID, AdminFeatureFlagController.FLAG_COLLECTION, "phase.advance"))
                .thenReturn(Promise.of(Optional.of(DataCloudClient.Entity.of(
                        "phase.advance",
                        AdminFeatureFlagController.FLAG_COLLECTION,
                        Map.of(
                                "id", "phase.advance",
                                "key", "phase.advance",
                                "enabled", false,
                                "description", "Allow phase advance",
                                "createdAt", "2026-05-01T00:00:00Z")))));
        when(dataCloudClient.save(eq(TENANT_ID), anyString(), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of(
                        String.valueOf(((Map<?, ?>) invocation.getArgument(2)).get("id")),
                        invocation.getArgument(1),
                        invocation.getArgument(2))));

        HttpResponse response = runPromise(() -> controller().setFlag(put(
                "/api/admin/feature-flags/phase.advance",
                "phase.advance",
                Map.of("enabled", true, "reason", "Enable phase advancement from admin"))));

        assertThat(response.getCode()).isEqualTo(200);
        ArgumentCaptor<Map<String, Object>> flagCaptor = typedMapCaptor();
        ArgumentCaptor<Map<String, Object>> auditCaptor = typedMapCaptor();
        verify(dataCloudClient).save(eq(TENANT_ID), eq(AdminFeatureFlagController.FLAG_COLLECTION), flagCaptor.capture());
        verify(dataCloudClient).save(eq(TENANT_ID), eq(AdminFeatureFlagController.AUDIT_COLLECTION), auditCaptor.capture());
        assertThat(flagCaptor.getValue())
                .containsEntry("key", "phase.advance")
                .containsEntry("enabled", true)
                .containsEntry("provider", "DATA_CLOUD_CANONICAL");
        assertThat(auditCaptor.getValue())
                .containsEntry("flagKey", "phase.advance")
                .containsEntry("previousValue", false)
                .containsEntry("newValue", true)
                .containsEntry("changedBy", "admin-user")
                .containsEntry("reason", "Enable phase advancement from admin")
                .containsEntry("correlationId", "corr-flags-1");
    }

    @Test
    @DisplayName("lists feature flag audit records")
    void listAuditReturnsCanonicalAuditRecords() {
        when(dataCloudClient.query(eq(TENANT_ID), eq(AdminFeatureFlagController.AUDIT_COLLECTION), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(List.of(DataCloudClient.Entity.of(
                        "audit-1",
                        AdminFeatureFlagController.AUDIT_COLLECTION,
                        Map.of(
                                "id", "audit-1",
                                "flagKey", "phase.advance",
                                "previousValue", false,
                                "newValue", true,
                                "changedBy", "admin-user",
                                "reason", "Enable",
                                "timestamp", "2026-05-26T00:00:00Z")))));

        HttpResponse response = runPromise(() -> controller().listAudit(get("/api/admin/feature-flags/phase.advance/audit", "phase.advance")));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getBody().asString(StandardCharsets.UTF_8))
                .contains("\"flagKey\":\"phase.advance\"")
                .contains("\"reason\":\"Enable\"");
    }

    private AdminFeatureFlagController controller() {
        return new AdminFeatureFlagController(dataCloudClient, objectMapper);
    }

    private HttpRequest get(String url) {
        HttpRequest request = HttpRequest.get("http://localhost" + url).build();
        request.attach(Principal.class, new Principal("admin-user", List.of("admin"), TENANT_ID));
        return request;
    }

    private HttpRequest get(String url, String flagKey) {
        HttpRequest request = get(url);
        putPathParameter(request, "flagKey", flagKey);
        return request;
    }

    private HttpRequest put(String url, String flagKey, Object body) throws Exception {
        HttpRequest request = HttpRequest.put("http://localhost" + url)
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-flags-1")
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(body)))
                .build();
        request.attach(Principal.class, new Principal("admin-user", List.of("admin"), TENANT_ID));
        putPathParameter(request, "flagKey", flagKey);
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
