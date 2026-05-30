package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.memory.media.DataCloudMediaArtifactRepository;
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
 * @doc.type class
 * @doc.purpose Regression tests for media artifact controller baseline modality API
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MediaArtifactController")
class MediaArtifactControllerTest extends EventloopTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MediaArtifactController controller;

    @BeforeEach
    void setUp() {
        controller = new MediaArtifactController(new DataCloudMediaArtifactRepository(), MAPPER);
    }

    @Test
    @DisplayName("creates artifact and can retrieve it by id")
    void createsArtifactAndRetrievesById() throws Exception {
        HttpResponse createResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts",
            "tenant-a",
            null,
            Map.of(
                "agentId", "agent-1",
                "mediaType", "audio/wav",
                "storageUri", "s3://bucket/artifacts/a.wav",
                "consentStatus", "granted",
                "sizeBytes", 1024,
                "durationMs", 3000
            ))));

        assertThat(createResponse.getCode()).isEqualTo(201);
        Map<String, Object> created = parseObject(createResponse);
        String artifactId = String.valueOf(created.get("artifactId"));

        HttpResponse getResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.GET,
            "/api/v1/media/artifacts/" + artifactId,
            "tenant-a",
            null,
            null)));

        assertThat(getResponse.getCode()).isEqualTo(200);
        Map<String, Object> fetched = parseObject(getResponse);
        assertThat(fetched).containsEntry("artifactId", artifactId);
        assertThat(fetched).containsEntry("agentId", "agent-1");
    }

    @Test
    @DisplayName("lists artifacts by mediaType filter")
    void listsArtifactsByMediaType() throws Exception {
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

        runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts",
            "tenant-a",
            null,
            Map.of(
                "agentId", "agent-2",
                "mediaType", "video/mp4",
                "storageUri", "s3://bucket/artifacts/b.mp4",
                "consentStatus", "granted"
            ))));

        HttpResponse listResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.GET,
            "/api/v1/media/artifacts",
            "tenant-a",
            Map.of("mediaType", "audio/wav"),
            null)));

        assertThat(listResponse.getCode()).isEqualTo(200);
        List<Map<String, Object>> items = parseItems(listResponse);
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("mediaType", "audio/wav");
    }

    @Test
    @DisplayName("rejects list request without filter")
    void rejectsListWithoutFilter() {
        HttpResponse response = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.GET,
            "/api/v1/media/artifacts",
            "tenant-a",
            null,
            null)));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("deletes artifact and returns not found on subsequent read")
    void deletesArtifactAndNotFoundAfterDelete() throws Exception {
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

        String artifactId = String.valueOf(parseObject(createResponse).get("artifactId"));

        HttpResponse deleteResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.DELETE,
            "/api/v1/media/artifacts/" + artifactId,
            "tenant-a",
            null,
            null)));

        assertThat(deleteResponse.getCode()).isEqualTo(204);

        HttpResponse getResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.GET,
            "/api/v1/media/artifacts/" + artifactId,
            "tenant-a",
            null,
            null)));

        assertThat(getResponse.getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("requires tenant header")
    void requiresTenantHeader() {
        HttpResponse response = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.GET,
            "/api/v1/media/artifacts",
            null,
            Map.of("mediaType", "audio/wav"),
            null)));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("rejects audio/video create request without consentStatus")
    void rejectsCreateWithoutConsentStatusForAudioVideo() {
        HttpResponse response = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts",
            "tenant-a",
            null,
            Map.of(
                "agentId", "agent-1",
                "mediaType", "audio/wav",
                "storageUri", "s3://bucket/artifacts/a.wav"
            ))));

        assertThat(response.getCode()).isEqualTo(400);
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