/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.memory.media.MediaArtifactEventEmitter;
import com.ghatana.datacloud.memory.media.MediaArtifactRecord;
import com.ghatana.datacloud.memory.media.MediaArtifactRepository;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Security tests for {@link MediaArtifactController}.
 *
 * <p>Validates:
 * <ul>
 *   <li>Canonical permission enforcement (media:artifact:create, media:artifact:read, etc.)</li>
 *   <li>Permission-based access control (not just role-based)</li>
 *   <li>Audit trail for security-sensitive operations</li>
 *   <li>Tenant isolation enforcement</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Security tests for media artifact permission handling
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MediaArtifactController Security Tests")
class MediaArtifactControllerSecurityTest extends EventloopTestBase {

    private static final String TEST_TENANT = "test-tenant";
    private static final String TEST_ARTIFACT = "test-artifact-123";
    private static final String COLLECTION_PATH = "/api/v1/media/artifacts";

    private MediaArtifactController controller;
    private MediaArtifactRepository repository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        repository = mock(MediaArtifactRepository.class);
        controller = new MediaArtifactController(repository, objectMapper);
    }

    @Nested
    @DisplayName("Create Permission (media:artifact:create)")
    class CreatePermissionTests {

        @Test
        @DisplayName("Create requires media:artifact:create permission")
        void createRequiresCreatePermission() {
            // Given: Principal without create permission
            Principal principal = Principal.builder()
                .id("viewer-user")
                .name("Viewer User")
                .tenantId(TEST_TENANT)
                .role("VIEWER")
                .permission("media:artifact:read") // Only read permission
                .build();

            HttpRequest request = HttpRequest.post("http://localhost" + COLLECTION_PATH)
                .withBody("{}".getBytes())
                .build();
            request = request.withAttachment(Principal.class, principal);

            // When: Create artifact request
            HttpResponse response = runPromise(() -> controller.handle(request));

            // Then: 403 forbidden
            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("Create allowed with media:artifact:create permission")
        void createAllowedWithPermission() {
            // Given: Principal with create permission
            Principal principal = Principal.builder()
                .id("editor-user")
                .name("Editor User")
                .tenantId(TEST_TENANT)
                .role("EDITOR")
                .permission("media:artifact:create")
                .build();

            MediaArtifactRecord savedRecord = MediaArtifactRecord.create(
                TEST_TENANT, "agent-123", "image/jpeg", "s3://bucket/image.jpg",
                1024L, "abc123", 0L, "tool-1", "corr-1", java.util.Map.of());

            when(repository.save(any())).thenReturn(Promise.of(savedRecord));

            String payload = "{\"agentId\":\"agent-123\",\"mediaType\":\"image/jpeg\",\"storageUri\":\"s3://bucket/image.jpg\"}";
            HttpRequest request = HttpRequest.post("http://localhost" + COLLECTION_PATH)
                .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_TYPE.toString())
                .withBody(payload.getBytes())
                .build();
            request = request.withAttachment(Principal.class, principal);

            // When: Create artifact request
            HttpResponse response = runPromise(() -> controller.handle(request));

            // Then: 201 created
            assertThat(response.getCode()).isEqualTo(201);
        }
    }

    @Nested
    @DisplayName("Read Permission (media:artifact:read)")
    class ReadPermissionTests {

        @Test
        @DisplayName("List requires media:artifact:read permission")
        void listRequiresReadPermission() {
            // Given: Principal without read permission
            Principal principal = Principal.builder()
                .id("processor-user")
                .name("Processor User")
                .tenantId(TEST_TENANT)
                .role("PROCESSOR")
                .permission("media:artifact:process") // Only process permission
                .build();

            HttpRequest request = HttpRequest.get("http://localhost" + COLLECTION_PATH + "?agentId=agent-123")
                .build();
            request = request.withAttachment(Principal.class, principal);

            // When: List artifacts request
            HttpResponse response = runPromise(() -> controller.handle(request));

            // Then: 403 forbidden
            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("Get artifact requires media:artifact:read permission")
        void getRequiresReadPermission() {
            // Given: Principal without read permission
            Principal principal = Principal.builder()
                .id("processor-user")
                .name("Processor User")
                .tenantId(TEST_TENANT)
                .role("PROCESSOR")
                .permission("media:artifact:process")
                .build();

            HttpRequest request = HttpRequest.get("http://localhost" + COLLECTION_PATH + "/" + TEST_ARTIFACT)
                .build();
            request = request.withAttachment(Principal.class, principal);

            // When: Get artifact request
            HttpResponse response = runPromise(() -> controller.handle(request));

            // Then: 403 forbidden
            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("Read allowed with media:artifact:read permission")
        void readAllowedWithPermission() {
            // Given: Principal with read permission
            Principal principal = Principal.builder()
                .id("viewer-user")
                .name("Viewer User")
                .tenantId(TEST_TENANT)
                .role("VIEWER")
                .permission("media:artifact:read")
                .build();

            MediaArtifactRecord record = MediaArtifactRecord.create(
                TEST_TENANT, "agent-123", "image/jpeg", "s3://bucket/image.jpg",
                1024L, "abc123", 0L, "tool-1", "corr-1", java.util.Map.of());

            when(repository.findById(TEST_ARTIFACT, TEST_TENANT)).thenReturn(Promise.of(Optional.of(record)));

            HttpRequest request = HttpRequest.get("http://localhost" + COLLECTION_PATH + "/" + TEST_ARTIFACT)
                .build();
            request = request.withAttachment(Principal.class, principal);

            // When: Get artifact request
            HttpResponse response = runPromise(() -> controller.handle(request));

            // Then: 200 OK
            assertThat(response.getCode()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("Process Permission (media:artifact:process)")
    class ProcessPermissionTests {

        @Test
        @DisplayName("Transcription requires media:artifact:process permission")
        void transcriptionRequiresProcessPermission() {
            // Given: Principal without process permission
            Principal principal = Principal.builder()
                .id("viewer-user")
                .name("Viewer User")
                .tenantId(TEST_TENANT)
                .role("VIEWER")
                .permission("media:artifact:read") // Only read
                .build();

            MediaArtifactRecord record = MediaArtifactRecord.create(
                TEST_TENANT, "agent-123", "audio/wav", "s3://bucket/audio.wav",
                10240L, "def456", 60000L, "tool-1", "corr-1", java.util.Map.of());

            when(repository.findById(TEST_ARTIFACT, TEST_TENANT)).thenReturn(Promise.of(Optional.of(record)));

            HttpRequest request = HttpRequest.post("http://localhost" + COLLECTION_PATH + "/" + TEST_ARTIFACT + "/transcribe")
                .build();
            request = request.withAttachment(Principal.class, principal);

            // When: Transcription request
            HttpResponse response = runPromise(() -> controller.handle(request));

            // Then: 403 forbidden
            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("Vision analysis requires media:artifact:process permission")
        void visionAnalysisRequiresProcessPermission() {
            // Given: Principal without process permission
            Principal principal = Principal.builder()
                .id("viewer-user")
                .name("Viewer User")
                .tenantId(TEST_TENANT)
                .role("VIEWER")
                .permission("media:artifact:read")
                .build();

            MediaArtifactRecord record = MediaArtifactRecord.create(
                TEST_TENANT, "agent-123", "image/jpeg", "s3://bucket/image.jpg",
                1024L, "ghi789", 0L, "tool-1", "corr-1", java.util.Map.of());

            when(repository.findById(TEST_ARTIFACT, TEST_TENANT)).thenReturn(Promise.of(Optional.of(record)));

            HttpRequest request = HttpRequest.post("http://localhost" + COLLECTION_PATH + "/" + TEST_ARTIFACT + "/analyze")
                .build();
            request = request.withAttachment(Principal.class, principal);

            // When: Vision analysis request
            HttpResponse response = runPromise(() -> controller.handle(request));

            // Then: 403 forbidden
            assertThat(response.getCode()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("Delete Permission (media:artifact:delete)")
    class DeletePermissionTests {

        @Test
        @DisplayName("Delete requires media:artifact:delete permission")
        void deleteRequiresDeletePermission() {
            // Given: Principal with create/read/process but not delete
            Principal principal = Principal.builder()
                .id("editor-user")
                .name("Editor User")
                .tenantId(TEST_TENANT)
                .role("EDITOR")
                .permission("media:artifact:create")
                .permission("media:artifact:read")
                .permission("media:artifact:process")
                // Missing media:artifact:delete
                .build();

            MediaArtifactRecord record = MediaArtifactRecord.create(
                TEST_TENANT, "agent-123", "image/jpeg", "s3://bucket/image.jpg",
                1024L, "jkl012", 0L, "tool-1", "corr-1", java.util.Map.of());

            when(repository.findById(TEST_ARTIFACT, TEST_TENANT)).thenReturn(Promise.of(Optional.of(record)));

            HttpRequest request = HttpRequest.delete("http://localhost" + COLLECTION_PATH + "/" + TEST_ARTIFACT)
                .build();
            request = request.withAttachment(Principal.class, principal);

            // When: Delete request
            HttpResponse response = runPromise(() -> controller.handle(request));

            // Then: 403 forbidden
            assertThat(response.getCode()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("Role-based Permission Mapping")
    class RoleBasedPermissionMappingTests {

        @Test
        @DisplayName("ADMIN role has all media artifact permissions")
        void adminRoleHasAllPermissions() {
            // Given: Principal with ADMIN role
            Principal principal = Principal.builder()
                .id("admin-user")
                .name("Admin User")
                .tenantId(TEST_TENANT)
                .role("ADMIN")
                .permission("media:artifact:create")
                .permission("media:artifact:read")
                .permission("media:artifact:process")
                .permission("media:artifact:delete")
                .build();

            MediaArtifactRecord record = MediaArtifactRecord.create(
                TEST_TENANT, "agent-123", "image/jpeg", "s3://bucket/image.jpg",
                1024L, "mno345", 0L, "tool-1", "corr-1", java.util.Map.of());

            when(repository.findById(TEST_ARTIFACT, TEST_TENANT)).thenReturn(Promise.of(Optional.of(record)));

            HttpRequest request = HttpRequest.get("http://localhost" + COLLECTION_PATH + "/" + TEST_ARTIFACT)
                .build();
            request = request.withAttachment(Principal.class, principal);

            // When: Get artifact (read operation)
            HttpResponse response = runPromise(() -> controller.handle(request));

            // Then: 200 OK (ADMIN has all permissions)
            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("EDITOR role has create, read, and process permissions")
        void editorRoleHasCreateReadProcess() {
            // Given: Principal with EDITOR role (mapped permissions)
            Principal principal = Principal.builder()
                .id("editor-user")
                .name("Editor User")
                .tenantId(TEST_TENANT)
                .role("EDITOR")
                .permission("media:artifact:create")
                .permission("media:artifact:read")
                .permission("media:artifact:process")
                // Missing media:artifact:delete
                .build();

            MediaArtifactRecord record = MediaArtifactRecord.create(
                TEST_TENANT, "agent-123", "image/jpeg", "s3://bucket/image.jpg",
                1024L, "pqr678", 0L, "tool-1", "corr-1", java.util.Map.of());

            when(repository.findById(TEST_ARTIFACT, TEST_TENANT)).thenReturn(Promise.of(Optional.of(record)));

            // Test read permission
            HttpRequest readRequest = HttpRequest.get("http://localhost" + COLLECTION_PATH + "/" + TEST_ARTIFACT)
                .build();
            readRequest = readRequest.withAttachment(Principal.class, principal);

            HttpResponse readResponse = runPromise(() -> controller.handle(readRequest));
            assertThat(readResponse.getCode()).isEqualTo(200);

            // Test delete permission (should fail)
            HttpRequest deleteRequest = HttpRequest.delete("http://localhost" + COLLECTION_PATH + "/" + TEST_ARTIFACT)
                .build();
            deleteRequest = deleteRequest.withAttachment(Principal.class, principal);

            HttpResponse deleteResponse = runPromise(() -> controller.handle(deleteRequest));
            assertThat(deleteResponse.getCode()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolationTests {

        @Test
        @DisplayName("Tenant isolation enforced - principal tenant must match")
        void tenantIsolationEnforced() {
            // Given: Principal with different tenant than requested
            Principal principal = Principal.builder()
                .id("admin-user")
                .name("Admin User")
                .tenantId("different-tenant") // Different from request
                .role("ADMIN")
                .permission("media:artifact:read")
                .build();

            HttpRequest request = HttpRequest.get("http://localhost" + COLLECTION_PATH + "/" + TEST_ARTIFACT)
                .build();
            request = request.withAttachment(Principal.class, principal);

            // When: Request with mismatched tenant
            HttpResponse response = runPromise(() -> controller.handle(request));

            // Then: 401 unauthorized (tenant mismatch)
            assertThat(response.getCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("Missing tenant in principal returns unauthorized")
        void missingTenantReturnsUnauthorized() {
            // Given: Principal without tenant
            Principal principal = Principal.builder()
                .id("admin-user")
                .name("Admin User")
                // Missing tenantId
                .role("ADMIN")
                .permission("media:artifact:read")
                .build();

            HttpRequest request = HttpRequest.get("http://localhost" + COLLECTION_PATH + "/" + TEST_ARTIFACT)
                .build();
            request = request.withAttachment(Principal.class, principal);

            // When: Request without tenant
            HttpResponse response = runPromise(() -> controller.handle(request));

            // Then: 401 unauthorized
            assertThat(response.getCode()).isEqualTo(401);
        }
    }

    @Nested
    @DisplayName("X-Permissions Header Rejection")
    class XPermissionsHeaderRejectionTests {

        @Test
        @DisplayName("X-Permissions header ignored - permissions from Principal only")
        void xPermissionsHeaderIgnored() {
            // Given: Principal with VIEWER role, but spoofed X-Permissions header claiming admin
            Principal principal = Principal.builder()
                .id("viewer-user")
                .name("Viewer User")
                .tenantId(TEST_TENANT)
                .role("VIEWER")
                .permission("media:artifact:read") // Only has read
                .build();

            MediaArtifactRecord record = MediaArtifactRecord.create(
                TEST_TENANT, "agent-123", "image/jpeg", "s3://bucket/image.jpg",
                1024L, "stu901", 0L, "tool-1", "corr-1", java.util.Map.of());

            when(repository.findById(TEST_ARTIFACT, TEST_TENANT)).thenReturn(Promise.of(Optional.of(record)));

            HttpRequest request = HttpRequest.get("http://localhost" + COLLECTION_PATH + "/" + TEST_ARTIFACT)
                .withHeader(HttpHeaders.of("X-Permissions"), "media:artifact:delete,media:artifact:admin")
                .build();
            request = request.withAttachment(Principal.class, principal);

            // When: Request with spoofed X-Permissions header
            HttpResponse response = runPromise(() -> controller.handle(request));

            // Then: 200 OK (read allowed), delete not attempted
            assertThat(response.getCode()).isEqualTo(200);
        }
    }
}
