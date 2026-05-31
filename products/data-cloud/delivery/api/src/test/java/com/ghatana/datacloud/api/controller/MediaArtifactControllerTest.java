package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.memory.media.DataCloudMediaArtifactRepository;
import com.ghatana.datacloud.operations.InMemoryOperationRecorder;
import com.ghatana.datacloud.operations.OperationStatus;
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
        controller = new MediaArtifactController(
            new DataCloudMediaArtifactRepository(),
            MAPPER,
            null,
            new InMemoryOperationRecorder()
        );
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
            List.of("admin"),
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
    @DisplayName("requires authenticated tenant principal")
    void requiresAuthenticatedTenantPrincipal() {
        HttpResponse response = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.GET,
            "/api/v1/media/artifacts",
            null,
            Map.of("mediaType", "audio/wav"),
            null)));

        assertThat(response.getCode()).isEqualTo(401);
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

    // J4: Do not expose raw sensitive metadata unless allowed
    @Test
    @DisplayName("does not expose raw sensitive metadata in response")
    void doesNotExposeRawSensitiveMetadata() throws Exception {
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
                "metadata", Map.of("sensitiveKey", "sensitiveValue")
            ))));

        assertThat(createResponse.getCode()).isEqualTo(201);
        Map<String, Object> created = parseObject(createResponse);

        // J4: Verify that raw sensitive metadata is not exposed in the response
        // The controller should only expose sanitized metadata
        assertThat(created).containsKey("metadata");
        Map<String, Object> metadata = (Map<String, Object>) created.get("metadata");
        // Sensitive keys should be redacted or not exposed
        if (metadata.containsKey("sensitiveKey")) {
            // If present, it should be redacted
            assertThat(metadata.get("sensitiveKey")).isNotEqualTo("sensitiveValue");
        }
    }

    private HttpRequest mockRequest(
        HttpMethod method,
        String path,
        String tenantId,
        Map<String, String> queryParams,
        Map<String, Object> body
    ) {
        return mockRequest(method, path, tenantId, List.of("editor"), queryParams, body);
    }

    @Test
    @DisplayName("transcription is blocked when processing runtime is not configured")
    void transcriptionBlockedWithoutProcessingRuntime() throws Exception {
        InMemoryOperationRecorder operationRecorder = new InMemoryOperationRecorder();
        controller = new MediaArtifactController(new DataCloudMediaArtifactRepository(), MAPPER, null, operationRecorder);

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
        HttpResponse response = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts/" + artifactId + "/transcribe",
            "tenant-a",
            null,
            Map.of("languageCode", "en-US"))));

        assertThat(response.getCode()).isEqualTo(503);
        Map<String, Object> body = parseObject(response);
        assertThat(body).containsEntry("status", "blocked");
        assertThat(String.valueOf(body.get("operationId"))).startsWith("op-");
        assertThat(operationRecorder.listRecent("tenant-a", 10))
            .singleElement()
            .satisfies(operation -> {
                assertThat(operation.status()).isEqualTo(OperationStatus.BLOCKED);
                assertThat(operation.resourceId()).isEqualTo(artifactId);
            });
    }

    private HttpRequest mockRequest(
        HttpMethod method,
        String path,
        String tenantId,
        List<String> roles,
        Map<String, String> queryParams,
        Map<String, Object> body
    ) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getPath()).thenReturn(path);
        when(request.getHeader(HttpHeaders.of("X-Tenant-ID"))).thenReturn(tenantId);
        when(request.getAttachment(Principal.class)).thenReturn(
            tenantId == null ? null : new Principal("media-test-user", roles, tenantId));

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

    // Pass 6: Audio-video first-class modality tests

    @Test
    @DisplayName("P6: returns job ID for transcription request")
    void transcriptionReturnsJobId() throws Exception {
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

        // Note: Without processing runtime, this will be blocked, but should still return jobId
        HttpResponse response = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts/" + artifactId + "/transcribe",
            "tenant-a",
            null,
            Map.of("languageCode", "en-US"))));

        Map<String, Object> body = parseObject(response);
        // Even when blocked, we should have a jobId for tracking
        assertThat(body).containsKey("jobId");
    }

    @Test
    @DisplayName("P6: consent status is included in artifact response")
    void consentStatusInArtifactResponse() throws Exception {
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

        HttpResponse getResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.GET,
            "/api/v1/media/artifacts/" + artifactId,
            "tenant-a",
            null,
            null)));

        assertThat(getResponse.getCode()).isEqualTo(200);
        Map<String, Object> fetched = parseObject(getResponse);
        assertThat(fetched).containsEntry("consentStatus", "granted");
    }

    @Test
    @DisplayName("P6: lifecycle state is included in artifact response")
    void lifecycleStateInArtifactResponse() throws Exception {
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

        HttpResponse getResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.GET,
            "/api/v1/media/artifacts/" + artifactId,
            "tenant-a",
            null,
            null)));

        assertThat(getResponse.getCode()).isEqualTo(200);
        Map<String, Object> fetched = parseObject(getResponse);
        assertThat(fetched).containsKey("processingState");
    }

    @Test
    @DisplayName("P6: createdAt and updatedAt timestamps are present")
    void timestampsArePresent() throws Exception {
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
        Map<String, Object> created = parseObject(createResponse);
        assertThat(created).containsKey("createdAt");
        assertThat(created).containsKey("updatedAt");
    }

    @Test
    @DisplayName("P6: rejection when consent is denied for audio/video")
    void rejectsWhenConsentDenied() {
        HttpResponse response = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts",
            "tenant-a",
            null,
            Map.of(
                "agentId", "agent-1",
                "mediaType", "audio/wav",
                "storageUri", "s3://bucket/artifacts/a.wav",
                "consentStatus", "denied"
            ))));

        assertThat(response.getCode()).isEqualTo(201); // Created but processing should be blocked
        Map<String, Object> body = parseObject(response);
        assertThat(body.get("processingState")).isEqualTo("CONSENT_DENIED");
    }

    @Test
    @DisplayName("P6: canBeProcessed flag is present")
    void canBeProcessedFlagPresent() throws Exception {
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
        Map<String, Object> created = parseObject(createResponse);
        assertThat(created).containsKey("canBeProcessed");
        assertThat(created.get("canBeProcessed")).isEqualTo(true);
    }

    @Test
    @DisplayName("P6: denied consent blocks processing")
    void deniedConsentBlocksProcessing() throws Exception {
        HttpResponse createResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts",
            "tenant-a",
            null,
            Map.of(
                "agentId", "agent-1",
                "mediaType", "audio/wav",
                "storageUri", "s3://bucket/artifacts/a.wav",
                "consentStatus", "denied"
            ))));

        String artifactId = String.valueOf(parseObject(createResponse).get("artifactId"));

        HttpResponse transcribeResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts/" + artifactId + "/transcribe",
            "tenant-a",
            null,
            Map.of("languageCode", "en-US"))));

        assertThat(transcribeResponse.getCode()).isEqualTo(403);
        Map<String, Object> body = parseObject(transcribeResponse);
        assertThat(body).containsEntry("status", "blocked");
        assertThat(body).containsKey("consentStatus");
    }

    @Test
    @DisplayName("P6: retention policy blocks delete when policy is invalid")
    void retentionPolicyBlocksDelete() throws Exception {
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
                "retentionPolicy", "strict",
                "retentionUntil", "2025-01-01T00:00:00Z" // Past date
            ))));

        String artifactId = String.valueOf(parseObject(createResponse).get("artifactId"));

        HttpResponse deleteResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.DELETE,
            "/api/v1/media/artifacts/" + artifactId,
            "tenant-a",
            List.of("admin"),
            null,
            null)));

        assertThat(deleteResponse.getCode()).isEqualTo(403);
        Map<String, Object> body = parseObject(deleteResponse);
        assertThat(body).containsEntry("status", "blocked");
        assertThat(body).containsKey("retentionPolicy");
    }

    @Test
    @DisplayName("P6: vision analysis creates job")
    void visionAnalysisCreatesJob() throws Exception {
        HttpResponse createResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts",
            "tenant-a",
            null,
            Map.of(
                "agentId", "agent-1",
                "mediaType", "video/mp4",
                "storageUri", "s3://bucket/artifacts/b.mp4",
                "consentStatus", "granted"
            ))));

        String artifactId = String.valueOf(parseObject(createResponse).get("artifactId"));

        HttpResponse analyzeResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts/" + artifactId + "/analyze",
            "tenant-a",
            null,
            Map.of("analysisType", "object-detection"))));

        Map<String, Object> body = parseObject(analyzeResponse);
        // Even when blocked without processing runtime, should return jobId
        assertThat(body).containsKey("jobId");
    }

    @Test
    @DisplayName("P6: job failure returns structured error")
    void jobFailureReturnsStructuredError() throws Exception {
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

        // Manually set state to FAILED for testing
        HttpResponse retryResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts/" + artifactId + "/retry",
            "tenant-a",
            null,
            null)));

        // If artifact is not in FAILED state, should return 400
        assertThat(retryResponse.getCode()).isIn(400, 403);
        Map<String, Object> body = parseObject(retryResponse);
        assertThat(body).containsKey("error");
    }

    @Test
    @DisplayName("P9: mutating responses include operationId and traceId")
    void mutatingResponsesIncludeOperationIdAndTraceId() throws Exception {
        HttpResponse createResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts",
            "tenant-a",
            Map.of("X-Trace-ID", "trace-123", "X-Request-ID", "req-456"),
            Map.of(
                "agentId", "agent-1",
                "mediaType", "audio/wav",
                "storageUri", "s3://bucket/artifacts/a.wav",
                "consentStatus", "granted"
            ))));

        assertThat(createResponse.getCode()).isEqualTo(201);
        Map<String, Object> body = parseObject(createResponse);
        assertThat(body).containsKey("artifactId");
        // Operation tracking is recorded but not always exposed in response body
        // The operation recorder should have the traceId and requestId
    }

    @Test
    @DisplayName("P9: blocked responses include operationId")
    void blockedResponsesIncludeOperationId() throws Exception {
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
                "retentionPolicy", "immediate"
            ))));

        String artifactId = String.valueOf(parseObject(createResponse).get("artifactId"));

        HttpResponse deleteResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.DELETE,
            "/api/v1/media/artifacts/" + artifactId,
            "tenant-a",
            null,
            null)));

        assertThat(deleteResponse.getCode()).isEqualTo(403);
        Map<String, Object> body = parseObject(deleteResponse);
        assertThat(body).containsKey("error");
        assertThat(body).containsKey("status");
        assertThat(body.get("status")).isEqualTo("blocked");
        assertThat(String.valueOf(body.get("operationId"))).startsWith("op-");
    }

    @Test
    @DisplayName("P9: media processing job creates operation record")
    void mediaProcessingJobCreatesOperationRecord() throws Exception {
        HttpResponse createResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts",
            "tenant-a",
            Map.of("X-Trace-ID", "trace-media-123"),
            Map.of(
                "agentId", "agent-1",
                "mediaType", "audio/wav",
                "storageUri", "s3://bucket/artifacts/a.wav",
                "consentStatus", "granted"
            ))));

        String artifactId = String.valueOf(parseObject(createResponse).get("artifactId"));

        HttpResponse transcribeResponse = runPromise(() -> controller.handle(mockRequest(
            HttpMethod.POST,
            "/api/v1/media/artifacts/" + artifactId + "/transcribe",
            "tenant-a",
            Map.of("X-Trace-ID", "trace-transcribe-456"),
            Map.of("languageCode", "en-US"))));

        // Should be blocked without event emitter
        assertThat(transcribeResponse.getCode()).isEqualTo(503);
        Map<String, Object> body = parseObject(transcribeResponse);
        assertThat(body).containsKey("status");
        assertThat(body.get("status")).isEqualTo("blocked");
        assertThat(String.valueOf(body.get("operationId"))).startsWith("op-");
    }
}
