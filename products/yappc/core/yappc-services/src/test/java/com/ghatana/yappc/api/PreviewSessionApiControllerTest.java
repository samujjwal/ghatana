package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verify preview session API audit parity for issue and validation operations
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PreviewSessionApiController")
class PreviewSessionApiControllerTest extends EventloopTestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("create session emits scoped audit event")
    void createSessionEmitsScopedAuditEvent() throws Exception {
        RecordingAuditLogger auditLogger = new RecordingAuditLogger();
        PreviewSessionApiController controller = new PreviewSessionApiController(objectMapper, "test-secret", auditLogger);

        HttpRequest request = jsonPost(
                "http://localhost/api/v1/preview/session/create",
                Map.of("projectId", "proj-1", "artifactId", "artifact-1", "duration", 60)
        );
        request.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

        HttpResponse response = runPromise(() -> controller.createSession(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(auditLogger.events()).hasSize(1);
        assertThat(auditLogger.events().get(0)).containsEntry("type", "preview.session.create")
                .containsEntry("outcome", "succeeded")
                .containsEntry("actor", "user-1")
                .containsEntry("tenantId", "tenant-1")
                .containsEntry("projectId", "proj-1")
                .containsEntry("artifactId", "artifact-1");
        Map<?, ?> metadata = (Map<?, ?>) auditLogger.events().get(0).get("metadata");
        assertThat(metadata.get("durationSeconds")).isEqualTo(60);
    }

    @Test
    @DisplayName("validate session emits outcome audit event")
    void validateSessionEmitsOutcomeAuditEvent() throws Exception {
        RecordingAuditLogger auditLogger = new RecordingAuditLogger();
        PreviewSessionApiController controller = new PreviewSessionApiController(objectMapper, "test-secret", auditLogger);

        HttpRequest createRequest = jsonPost(
                "http://localhost/api/v1/preview/session/create",
                Map.of("projectId", "proj-1", "artifactId", "artifact-1")
        );
        createRequest.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));
        HttpResponse createResponse = runPromise(() -> controller.createSession(createRequest));
        String sessionToken = stringValue(readJson(createResponse).get("sessionToken"));

        HttpRequest validateRequest = jsonPost(
                "http://localhost/api/v1/preview/session/validate",
                Map.of("sessionToken", sessionToken)
        );
        validateRequest.attach(Principal.class, new Principal("user-1", List.of("builder"), "tenant-1"));

        HttpResponse validateResponse = runPromise(() -> controller.validateSession(validateRequest));

        assertThat(validateResponse.getCode()).isEqualTo(200);
        assertThat(auditLogger.events()).hasSize(2);
        assertThat(auditLogger.events().get(1)).containsEntry("type", "preview.session.validate")
                .containsEntry("outcome", "succeeded")
                .containsEntry("actor", "user-1")
                .containsEntry("tenantId", "tenant-1");
        Map<?, ?> metadata = (Map<?, ?>) auditLogger.events().get(1).get("metadata");
        assertThat(metadata.get("valid")).isEqualTo(true);
        assertThat(metadata.get("reason")).isEqualTo("valid");
    }

    private HttpRequest jsonPost(String url, Map<String, Object> body) throws Exception {
        return HttpRequest.post(url)
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(body)))
                .build();
    }

    private Map<String, Object> readJson(HttpResponse response) throws Exception {
        return objectMapper.readValue(
                response.getBody().asString(StandardCharsets.UTF_8),
                new TypeReference<>() {
                }
        );
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : "";
    }

    private static final class RecordingAuditLogger implements AuditLogger {
        private final List<Map<String, Object>> events = new ArrayList<>();

        @Override
        public Promise<Void> log(Map<String, Object> event) {
            events.add(event);
            return Promise.complete();
        }

        List<Map<String, Object>> events() {
            return events;
        }
    }
}
