package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Contract tests for DMOS release readiness cockpit API
 * @doc.layer product
 * @doc.pattern Controller Test
 */
@DisplayName("DmosReleaseReadinessServlet")
class DmosReleaseReadinessServletTest extends EventloopTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path workspaceRoot;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() throws Exception {
        writeEvidence(
            ".kernel/evidence/digital-marketing/dmos-release-readiness.json",
            """
                {
                  "schemaVersion": "1.0.0",
                  "productId": "digital-marketing",
                  "productName": "Digital Marketing Operations System",
                  "checkedAt": "2026-05-25T20:00:00Z",
                  "validationStatus": "passed",
                  "expiresAt": "2026-05-26T20:00:00Z",
                  "releaseReadiness": {
                    "status": "ready",
                    "overallScore": 9,
                    "blockingIssues": [],
                    "warnings": []
                  },
                  "evidenceCategories": {
                    "connector": {
                      "status": "passed",
                      "lastChecked": "2026-05-25T20:00:00Z",
                      "evidenceRefs": ["connector-proof.json"]
                    },
                    "rollback": {
                      "status": "passed",
                      "lastChecked": "2026-05-25T20:00:00Z",
                      "evidenceRefs": ["rollback-proof.json"]
                    }
                  },
                  "gates": {},
                  "summary": {
                    "totalChecks": 1,
                    "passed": 1,
                    "partial": 0,
                    "failed": 0,
                    "blocked": 0,
                    "overallStatus": "passed"
                  },
                  "nextRequiredWork": []
                }
                """
        );
        writeEvidence(
            ".kernel/evidence/data-cloud/platform-provider-readiness.json",
            """
                {
                  "status": "pass",
                  "generatedAt": "2026-05-25T20:00:00Z",
                  "targetCommitSha": "bdcee47c1e304454e7af848be60d981b24da1151",
                  "targetEnvironment": "staging"
                }
                """
        );
        writeEvidence(
            ".kernel/evidence/data-cloud-release-runtime-profile.json",
            """
                {
                  "validationStatus": "validated",
                  "generatedAt": "2026-05-25T20:00:00Z",
                  "targetCommitSha": "bdcee47c1e304454e7af848be60d981b24da1151",
                  "targetEnvironment": "staging"
                }
                """
        );

        servlet = new DmosReleaseReadinessServlet(
            Eventloop.create(),
            DmosHttpContextFactory.testModeWithAnonymousFallback(),
            workspaceRoot
        ).getServlet();
    }

    @Test
    @DisplayName("GET /release-readiness returns enriched backend evidence")
    void shouldReturnEnrichedReadinessEvidence() throws Exception {
        HttpResponse response = dispatch();

        assertThat(response.getCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(bodyString(response));
        assertThat(body.path("productId").asText()).isEqualTo("digital-marketing");
        assertThat(body.path("requestedEnvironment").asText()).isEqualTo("production");
        assertThat(body.path("evidenceFreshness").path("current").asBoolean()).isTrue();
        assertThat(body.path("dataCloudProviderReadiness").path("status").asText()).isEqualTo("passed");
        assertThat(body.path("dataCloudRuntimeProfile").path("status").asText()).isEqualTo("passed");
        assertThat(body.path("connectorReadiness").path("googleAds").path("status").asText()).isEqualTo("passed");
        assertThat(body.path("rollbackStatus").path("staging").path("hasEvidence").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("GET /release-readiness requires tenant context")
    void shouldRejectMissingTenantContext() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/release-readiness")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer test-token")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-1")
            .withHeader(HttpHeaders.of("X-Session-ID"), "session-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    private HttpResponse dispatch() {
        HttpRequest request = HttpRequest.get("http://localhost/v1/workspaces/ws-1/release-readiness?environment=production")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-1")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer test-token")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user-1")
            .withHeader(HttpHeaders.of("X-Session-ID"), "session-1")
            .withHeader(HttpHeaders.of("X-Permissions"), "dmos.release_readiness")
            .build();
        return runPromise(() -> servlet.serve(request));
    }

    private void writeEvidence(String relativePath, String json) throws Exception {
        Path path = workspaceRoot.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, json);
    }

    private String bodyString(HttpResponse response) throws Exception {
        byte[] bytes = runPromise(response::loadBody).asArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
