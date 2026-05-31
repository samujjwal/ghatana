package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.memory.media.DataCloudMediaArtifactRepository;
import com.ghatana.platform.governance.security.Principal;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * J5: Tenant security tests for MediaArtifactController.
 *
 * <p>Verifies that:
 * - Production rejects raw tenant header
 * - Cross-tenant access is blocked
 * - Tenant isolation is enforced
 *
 * @doc.type class
 * @doc.purpose Tenant security tests for media artifact controller
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MediaArtifactController Tenant Security")
class MediaArtifactControllerTenantSecurityTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MediaArtifactController controller;

    @BeforeEach
    void setUp() {
        controller = new MediaArtifactController(new DataCloudMediaArtifactRepository(), MAPPER);
    }

    @Test
    @DisplayName("production rejects raw tenant header")
    void productionRejectsRawTenantHeader() {
        // J5: In production mode, the controller should reject requests that use
        // raw X-Tenant-Id header instead of authenticated Principal
        HttpRequest request = mock(HttpRequest.class);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getPath()).thenReturn("/api/v1/media/artifacts");
        when(request.getHeader(HttpHeaders.of("X-Tenant-ID"))).thenReturn("tenant-a");
        // No Principal attached - this should be rejected
        when(request.getAttachment(Principal.class)).thenReturn(null);
        when(request.getQueryParameter("mediaType")).thenReturn("audio/wav");
        when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(new byte[0])));

        HttpResponse response = runPromise(() -> controller.handle(request));

        // Should return 401 unauthorized since no authenticated principal
        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("cross-tenant access is blocked")
    void crossTenantAccessIsBlocked() throws Exception {
        // Create artifact for tenant-a
        HttpResponse createResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts",
            "tenant-a",
            null,
            Map.of(
                "agentId", "agent-1",
                "mediaType", "audio/wav",
                "storageUri", "s3://bucket/artifacts/a.wav",
                "consentStatus", "granted"
            ))));

        assertThat(createResponse.getCode()).isEqualTo(201);
        String artifactId = String.valueOf(parseObject(createResponse).get("artifactId"));

        // Try to access artifact from tenant-b
        HttpResponse getResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.GET,
            "/api/v1/media/artifacts/" + artifactId,
            "tenant-b",
            null,
            null)));

        // J5: Cross-tenant access should be blocked - artifact not found for tenant-b
        assertThat(getResponse.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("tenant isolation is enforced for delete")
    void tenantIsolationEnforcedForDelete() throws Exception {
        // Create artifact for tenant-a
        HttpResponse createResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts",
            "tenant-a",
            null,
            Map.of(
                "agentId", "agent-1",
                "mediaType", "audio/wav",
                "storageUri", "s3://bucket/artifacts/a.wav",
                "consentStatus", "granted"
            ))));

        assertThat(createResponse.getCode()).isEqualTo(201);
        String artifactId = String.valueOf(parseObject(createResponse).get("artifactId"));

        // Try to delete artifact from tenant-b
        HttpResponse deleteResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.DELETE,
            "/api/v1/media/artifacts/" + artifactId,
            "tenant-b",
            null,
            null)));

        // J5: Cross-tenant delete should be blocked
        assertThat(deleteResponse.getCode()).isEqualTo(404);

        // Verify artifact still exists for tenant-a
        HttpResponse getResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.GET,
            "/api/v1/media/artifacts/" + artifactId,
            "tenant-a",
            null,
            null)));

        assertThat(getResponse.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("tenant isolation is enforced for list")
    void tenantIsolationEnforcedForList() throws Exception {
        // Create artifact for tenant-a
        runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts",
            "tenant-a",
            null,
            Map.of(
                "agentId", "agent-1",
                "mediaType", "audio/wav",
                "storageUri", "s3://bucket/artifacts/a.wav",
                "consentStatus", "granted"
            ))));

        // Create artifact for tenant-b
        runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts",
            "tenant-b",
            null,
            Map.of(
                "agentId", "agent-2",
                "mediaType", "audio/wav",
                "storageUri", "s3://bucket/artifacts/b.wav",
                "consentStatus", "granted"
            ))));

        // List artifacts for tenant-a
        HttpResponse listResponseA = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.GET,
            "/api/v1/media/artifacts",
            "tenant-a",
            Map.of("mediaType", "audio/wav"),
            null)));

        assertThat(listResponseA.getCode()).isEqualTo(200);
        List<Map<String, Object>> itemsA = parseItems(listResponseA);
        // J5: Should only see tenant-a's artifacts
        assertThat(itemsA).hasSize(1);
        assertThat(itemsA.get(0)).containsEntry("agentId", "agent-1");

        // List artifacts for tenant-b
        HttpResponse listResponseB = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.GET,
            "/api/v1/media/artifacts",
            "tenant-b",
            Map.of("mediaType", "audio/wav"),
            null)));

        assertThat(listResponseB.getCode()).isEqualTo(200);
        List<Map<String, Object>> itemsB = parseItems(listResponseB);
        // J5: Should only see tenant-b's artifacts
        assertThat(itemsB).hasSize(1);
        assertThat(itemsB.get(0)).containsEntry("agentId", "agent-2");
    }

    private HttpRequest mockRequest(
        HttpMethod method,
        String path,
        String tenantId,
        Map<String, String> queryParams,
        Map<String, Object> body
    ) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getPath()).thenReturn(path);
        when(request.getHeader(HttpHeaders.of("X-Tenant-ID"))).thenReturn(tenantId);
        when(request.getAttachment(Principal.class)).thenReturn(
            tenantId == null ? null : new Principal("media-test-user", List.of("editor"), tenantId));

        when(request.getQueryParameter("mediaType")).thenReturn(queryParams == null ? null : queryParams.get("mediaType"));
        when(request.getQueryParameter("agentId")).thenReturn(queryParams == null ? null : queryParams.get("agentId"));
        when(request.getQueryParameter("limit")).thenReturn(queryParams == null ? null : queryParams.get("limit"));

        if (body != null) {
            try {
                byte[] payload = MAPPER.writeValueAsBytes(body);
                when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(payload)));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize test body", e);
            }
        } else {
            when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(new byte[0])));
        }

        return request;
    }

    private static Map<String, Object> parseObject(HttpResponse response) throws Exception {
        String payload = response.getBody().getString(StandardCharsets.UTF_8);
        return MAPPER.readValue(payload, new TypeReference<>() {});
    }

    private static List<Map<String, Object>> parseItems(HttpResponse response) throws Exception {
        Map<String, Object> payload = parseObject(response);
        return MAPPER.convertValue(payload.get("items"), new TypeReference<>() {});
    }
}
