package com.ghatana.products.collection.application.service;

import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.products.collection.domain.media.MediaAttachment;
import com.ghatana.products.collection.domain.media.MediaStore;
import com.ghatana.products.collection.domain.media.MediaUrl;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MediaService.
 *
 * Tests validate:
 * - File upload with validation (size, content type, tenant)
 * - File deletion with tenant isolation
 * - URL generation (public/signed) with expiry
 * - Error handling (invalid input, storage failures)
 * - Tenant ownership validation
 * - Metrics emission
 *
 * @see MediaService
 */
@DisplayName("MediaService Tests")
class MediaServiceTest extends EventloopTestBase {

    private MediaService mediaService;
    private MediaStore mediaStore;

    @BeforeEach
    void setUp() {
        // GIVEN: MediaService with mocked dependencies
        mediaStore = mock(MediaStore.class);
        mediaService = new MediaService(mediaStore, NoopMetricsCollector.getInstance());
    }

    // ========================================================================
    // Upload Tests
    // ========================================================================

    /**
     * Verifies successful file upload with valid inputs.
     *
     * GIVEN: Valid upload parameters
     * WHEN: upload() is called
     * THEN: File is uploaded and MediaAttachment returned
     */
    @Test
    @DisplayName("Should upload file successfully when all inputs are valid")
    void shouldUploadFileSuccessfullyWhenAllInputsAreValid() {
        // GIVEN: Valid upload parameters
        String tenantId = "tenant-123";
        UUID entityId = UUID.randomUUID();
        String filename = "test-image.jpg";
        String contentType = "image/jpeg";
        long size = 1024;
        InputStream inputStream = new ByteArrayInputStream(new byte[1024]);
        String uploadedBy = "user-456";

        MediaAttachment expectedAttachment = MediaAttachment.builder()
                .id(UUID.randomUUID())
                .entityId(entityId)
                .tenantId(tenantId)
                .filename(filename)
                .contentType(contentType)
                .size(size)
                .storageKey("tenant-123/entity-id/file-uuid.jpg")
                .uploadedAt(Instant.now())
                .isActive(true)
                .build();

        when(mediaStore.upload(eq(tenantId), eq(entityId), eq(filename), eq(contentType), eq(size), any(InputStream.class)))
                .thenReturn(Promise.of(expectedAttachment));

        // WHEN: Upload file
        MediaAttachment result = runPromise(() -> mediaService.upload(
                tenantId, entityId, filename, contentType, size, inputStream, uploadedBy));

        // THEN: File uploaded successfully
        assertThat(result)
                .as("Upload should return MediaAttachment")
                .isNotNull();
        assertThat(result.getTenantId())
                .as("Tenant ID should match input")
                .isEqualTo(tenantId);
        assertThat(result.getFilename())
                .as("Filename should match input")
                .isEqualTo(filename);

        verify(mediaStore).upload(eq(tenantId), eq(entityId), eq(filename), eq(contentType), eq(size), any(InputStream.class));
    }

    /**
     * Verifies upload fails when tenant ID is null.
     *
     * GIVEN: Null tenant ID
     * WHEN: upload() is called
     * THEN: IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should reject upload when tenant ID is null")
    void shouldRejectUploadWhenTenantIdIsNull() {
        // GIVEN: Null tenant ID
        UUID entityId = UUID.randomUUID();
        InputStream inputStream = new ByteArrayInputStream(new byte[1024]);

        // WHEN/THEN: Upload should fail
        assertThatThrownBy(() -> runPromise(() -> mediaService.upload(
                        null, entityId, "file.jpg", "image/jpeg", 1024, inputStream, "user-1")))
                .as("Upload with null tenant ID should fail")
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId cannot be null");
    }

    /**
     * Verifies upload fails when filename is blank.
     *
     * GIVEN: Blank filename
     * WHEN: upload() is called
     * THEN: IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should reject upload when filename is blank")
    void shouldRejectUploadWhenFilenameIsBlank() {
        // GIVEN: Blank filename
        String tenantId = "tenant-123";
        UUID entityId = UUID.randomUUID();
        InputStream inputStream = new ByteArrayInputStream(new byte[1024]);

        // WHEN/THEN: Upload should fail
        assertThatThrownBy(() -> runPromise(() -> mediaService.upload(
                        tenantId, entityId, "  ", "image/jpeg", 1024, inputStream, "user-1")))
                .as("Upload with blank filename should fail")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filename cannot be blank");
    }

    /**
     * Verifies upload fails when file size exceeds maximum.
     *
     * GIVEN: File size > 10MB
     * WHEN: upload() is called
     * THEN: IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should reject upload when file size exceeds maximum")
    void shouldRejectUploadWhenFileSizeExceedsMaximum() {
        // GIVEN: File size > 10MB
        String tenantId = "tenant-123";
        UUID entityId = UUID.randomUUID();
        long oversizedFileSize = 11 * 1024 * 1024; // 11MB
        InputStream inputStream = new ByteArrayInputStream(new byte[1024]);

        // WHEN/THEN: Upload should fail
        assertThatThrownBy(() -> runPromise(() -> mediaService.upload(
                        tenantId, entityId, "large.jpg", "image/jpeg", oversizedFileSize, inputStream, "user-1")))
                .as("Upload with oversized file should fail")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    /**
     * Verifies upload fails when content type is not allowed.
     *
     * GIVEN: Unsupported content type (e.g., "video/mp4")
     * WHEN: upload() is called
     * THEN: IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should reject upload when content type is not allowed")
    void shouldRejectUploadWhenContentTypeIsNotAllowed() {
        // GIVEN: Unsupported content type
        String tenantId = "tenant-123";
        UUID entityId = UUID.randomUUID();
        String invalidContentType = "video/mp4";
        InputStream inputStream = new ByteArrayInputStream(new byte[1024]);

        // WHEN/THEN: Upload should fail
        assertThatThrownBy(() -> runPromise(() -> mediaService.upload(
                        tenantId, entityId, "video.mp4", invalidContentType, 1024, inputStream, "user-1")))
                .as("Upload with invalid content type should fail")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Content type not allowed");
    }

    /**
     * Verifies upload accepts all allowed content types.
     *
     * GIVEN: Each allowed content type (JPEG, PNG, GIF, WebP, PDF)
     * WHEN: upload() is called
     * THEN: Upload succeeds for each type
     */
    @Test
    @DisplayName("Should accept upload for all allowed content types")
    void shouldAcceptUploadForAllAllowedContentTypes() {
        // GIVEN: Allowed content types
        String[] allowedTypes = {"image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf"};
        String tenantId = "tenant-123";
        UUID entityId = UUID.randomUUID();

        for (String contentType : allowedTypes) {
            // Mock store to return success
            MediaAttachment mockAttachment = MediaAttachment.builder()
                    .id(UUID.randomUUID())
                    .entityId(entityId)
                    .tenantId(tenantId)
                    .filename("test." + contentType.split("/")[1])
                    .contentType(contentType)
                    .size(1024L)
                    .storageKey("key")
                    .uploadedAt(Instant.now())
                    .isActive(true)
                    .build();

            when(mediaStore.upload(eq(tenantId), eq(entityId), anyString(), eq(contentType), anyLong(), any()))
                    .thenReturn(Promise.of(mockAttachment));

            // WHEN: Upload file
            InputStream inputStream = new ByteArrayInputStream(new byte[1024]);
            MediaAttachment result = runPromise(() -> mediaService.upload(
                    tenantId, entityId, "file", contentType, 1024, inputStream, "user-1"));

            // THEN: Upload succeeds
            assertThat(result)
                    .as("Upload with content type " + contentType + " should succeed")
                    .isNotNull();
            assertThat(result.getContentType())
                    .as("Content type should be preserved")
                    .isEqualTo(contentType);
        }
    }

    // ========================================================================
    // Delete Tests
    // ========================================================================

    /**
     * Verifies successful file deletion.
     *
     * GIVEN: Valid tenant ID and storage key
     * WHEN: delete() is called
     * THEN: File is deleted successfully
     */
    @Test
    @DisplayName("Should delete file successfully when inputs are valid")
    void shouldDeleteFileSuccessfullyWhenInputsAreValid() {
        // GIVEN: Valid delete parameters
        String tenantId = "tenant-123";
        String storageKey = "tenant-123/entity-456/file-uuid.jpg";

        when(mediaStore.delete(storageKey))
                .thenReturn(Promise.complete());

        // WHEN: Delete file
        runPromise(() -> mediaService.delete(tenantId, storageKey));

        // THEN: File deleted successfully
        verify(mediaStore).delete(storageKey);
    }

    /**
     * Verifies delete fails when tenant ID is null.
     *
     * GIVEN: Null tenant ID
     * WHEN: delete() is called
     * THEN: NullPointerException is thrown
     */
    @Test
    @DisplayName("Should reject delete when tenant ID is null")
    void shouldRejectDeleteWhenTenantIdIsNull() {
        // GIVEN: Null tenant ID
        String storageKey = "tenant-123/entity-456/file-uuid.jpg";

        // WHEN/THEN: Delete should fail
        assertThatThrownBy(() -> runPromise(() -> mediaService.delete(null, storageKey)))
                .as("Delete with null tenant ID should fail")
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId cannot be null");
    }

    /**
     * Verifies delete fails when storage key is blank.
     *
     * GIVEN: Blank storage key
     * WHEN: delete() is called
     * THEN: IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should reject delete when storage key is blank")
    void shouldRejectDeleteWhenStorageKeyIsBlank() {
        // GIVEN: Blank storage key
        String tenantId = "tenant-123";

        // WHEN/THEN: Delete should fail
        assertThatThrownBy(() -> runPromise(() -> mediaService.delete(tenantId, "  ")))
                .as("Delete with blank storage key should fail")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storageKey cannot be blank");
    }

    /**
     * Verifies delete fails when tenant doesn't own the file.
     *
     * GIVEN: Storage key belonging to different tenant
     * WHEN: delete() is called
     * THEN: IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should reject delete when tenant doesn't own file")
    void shouldRejectDeleteWhenTenantDoesntOwnFile() {
        // GIVEN: Mismatched tenant and storage key
        String tenantId = "tenant-123";
        String storageKey = "tenant-999/entity-456/file-uuid.jpg"; // Different tenant

        // WHEN/THEN: Delete should fail
        assertThatThrownBy(() -> runPromise(() -> mediaService.delete(tenantId, storageKey)))
                .as("Delete with non-owned storage key should fail")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to tenant");
    }

    // ========================================================================
    // URL Generation Tests
    // ========================================================================

    /**
     * Verifies successful URL generation with custom expiry.
     *
     * GIVEN: Valid tenant ID, storage key, and expiry duration
     * WHEN: getUrl() is called
     * THEN: Signed URL is generated with expiration
     */
    @Test
    @DisplayName("Should generate signed URL successfully with custom expiry")
    void shouldGenerateSignedUrlSuccessfullyWithCustomExpiry() {
        // GIVEN: Valid URL generation parameters
        String tenantId = "tenant-123";
        String storageKey = "tenant-123/entity-456/file-uuid.jpg";
        Duration expiry = Duration.ofHours(2);
        Instant expiresAt = Instant.now().plus(expiry);

        MediaUrl expectedUrl = MediaUrl.signedUrl("https://storage.example.com/" + storageKey, expiresAt);
        when(mediaStore.generateUrl(storageKey, expiry))
                .thenReturn(Promise.of(expectedUrl));

        // WHEN: Generate URL
        MediaUrl result = runPromise(() -> mediaService.getUrl(tenantId, storageKey, expiry));

        // THEN: Signed URL returned
        assertThat(result)
                .as("URL generation should return MediaUrl")
                .isNotNull();
        assertThat(result.isPublic())
                .as("URL should be signed (not public)")
                .isFalse();
        assertThat(result.getUrl())
                .as("URL should contain storage key")
                .contains(storageKey);

        verify(mediaStore).generateUrl(storageKey, expiry);
    }

    /**
     * Verifies URL generation uses default expiry when not specified.
     *
     * GIVEN: Null expiry parameter
     * WHEN: getUrl() is called
     * THEN: Default 1-hour expiry is used
     */
    @Test
    @DisplayName("Should use default expiry when expiry is null")
    void shouldUseDefaultExpiryWhenExpiryIsNull() {
        // GIVEN: Null expiry
        String tenantId = "tenant-123";
        String storageKey = "tenant-123/entity-456/file-uuid.jpg";
        Duration defaultExpiry = Duration.ofHours(1);

        MediaUrl expectedUrl = MediaUrl.signedUrl("https://storage.example.com/" + storageKey,
                Instant.now().plus(defaultExpiry));
        when(mediaStore.generateUrl(eq(storageKey), any(Duration.class)))
                .thenReturn(Promise.of(expectedUrl));

        // WHEN: Generate URL with null expiry
        MediaUrl result = runPromise(() -> mediaService.getUrl(tenantId, storageKey, null));

        // THEN: URL generated with default expiry
        assertThat(result)
                .as("URL generation should return MediaUrl")
                .isNotNull();

        verify(mediaStore).generateUrl(eq(storageKey), eq(defaultExpiry));
    }

    /**
     * Verifies URL generation fails when tenant doesn't own the file.
     *
     * GIVEN: Storage key belonging to different tenant
     * WHEN: getUrl() is called
     * THEN: IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should reject URL generation when tenant doesn't own file")
    void shouldRejectUrlGenerationWhenTenantDoesntOwnFile() {
        // GIVEN: Mismatched tenant and storage key
        String tenantId = "tenant-123";
        String storageKey = "tenant-999/entity-456/file-uuid.jpg"; // Different tenant

        // WHEN/THEN: URL generation should fail
        assertThatThrownBy(() -> runPromise(() -> mediaService.getUrl(tenantId, storageKey, Duration.ofHours(1))))
                .as("URL generation with non-owned storage key should fail")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to tenant");
    }

    // ========================================================================
    // Existence Check Tests
    // ========================================================================

    /**
     * Verifies file existence check returns true for existing file.
     *
     * GIVEN: Existing file
     * WHEN: exists() is called
     * THEN: Returns true
     */
    @Test
    @DisplayName("Should return true when file exists")
    void shouldReturnTrueWhenFileExists() {
        // GIVEN: Existing file
        String tenantId = "tenant-123";
        String storageKey = "tenant-123/entity-456/file-uuid.jpg";

        when(mediaStore.exists(storageKey))
                .thenReturn(Promise.of(true));

        // WHEN: Check existence
        Boolean result = runPromise(() -> mediaService.exists(tenantId, storageKey));

        // THEN: Returns true
        assertThat(result)
                .as("exists() should return true for existing file")
                .isTrue();

        verify(mediaStore).exists(storageKey);
    }

    /**
     * Verifies file existence check returns false for non-existing file.
     *
     * GIVEN: Non-existing file
     * WHEN: exists() is called
     * THEN: Returns false
     */
    @Test
    @DisplayName("Should return false when file does not exist")
    void shouldReturnFalseWhenFileDoesNotExist() {
        // GIVEN: Non-existing file
        String tenantId = "tenant-123";
        String storageKey = "tenant-123/entity-456/nonexistent.jpg";

        when(mediaStore.exists(storageKey))
                .thenReturn(Promise.of(false));

        // WHEN: Check existence
        Boolean result = runPromise(() -> mediaService.exists(tenantId, storageKey));

        // THEN: Returns false
        assertThat(result)
                .as("exists() should return false for non-existing file")
                .isFalse();

        verify(mediaStore).exists(storageKey);
    }

    // ========================================================================
    // File Size Tests
    // ========================================================================

    /**
     * Verifies file size retrieval for existing file.
     *
     * GIVEN: Existing file with known size
     * WHEN: getSize() is called
     * THEN: Returns file size
     */
    @Test
    @DisplayName("Should return file size when file exists")
    void shouldReturnFileSizeWhenFileExists() {
        // GIVEN: Existing file
        String tenantId = "tenant-123";
        String storageKey = "tenant-123/entity-456/file-uuid.jpg";
        long expectedSize = 524288L; // 512 KB

        when(mediaStore.getSize(storageKey))
                .thenReturn(Promise.of(expectedSize));

        // WHEN: Get file size
        Long result = runPromise(() -> mediaService.getSize(tenantId, storageKey));

        // THEN: Returns size
        assertThat(result)
                .as("getSize() should return file size")
                .isEqualTo(expectedSize);

        verify(mediaStore).getSize(storageKey);
    }

    /**
     * Verifies file size retrieval returns null for non-existing file.
     *
     * GIVEN: Non-existing file
     * WHEN: getSize() is called
     * THEN: Returns null
     */
    @Test
    @DisplayName("Should return null when file does not exist")
    void shouldReturnNullWhenFileDoesNotExist() {
        // GIVEN: Non-existing file
        String tenantId = "tenant-123";
        String storageKey = "tenant-123/entity-456/nonexistent.jpg";

        when(mediaStore.getSize(storageKey))
                .thenReturn(Promise.of(null));

        // WHEN: Get file size
        Long result = runPromise(() -> mediaService.getSize(tenantId, storageKey));

        // THEN: Returns null
        assertThat(result)
                .as("getSize() should return null for non-existing file")
                .isNull();

        verify(mediaStore).getSize(storageKey);
    }

    // ========================================================================
    // Tenant Isolation Tests
    // ========================================================================

    /**
     * Verifies tenant isolation in all operations.
     *
     * GIVEN: Operations with mismatched tenant and storage key
     * WHEN: Any operation is called
     * THEN: IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should enforce tenant isolation across all operations")
    void shouldEnforceTenantIsolationAcrossAllOperations() {
        // GIVEN: Tenant A trying to access Tenant B's file
        String tenantA = "tenant-A";
        String tenantBStorageKey = "tenant-B/entity-456/file-uuid.jpg";

        // WHEN/THEN: Delete should fail
        assertThatThrownBy(() -> runPromise(() -> mediaService.delete(tenantA, tenantBStorageKey)))
                .as("Delete across tenants should fail")
                .isInstanceOf(IllegalArgumentException.class);

        // WHEN/THEN: Get URL should fail
        assertThatThrownBy(() -> runPromise(() -> mediaService.getUrl(tenantA, tenantBStorageKey, Duration.ofHours(1))))
                .as("Get URL across tenants should fail")
                .isInstanceOf(IllegalArgumentException.class);

        // WHEN/THEN: Exists should fail
        assertThatThrownBy(() -> runPromise(() -> mediaService.exists(tenantA, tenantBStorageKey)))
                .as("Exists across tenants should fail")
                .isInstanceOf(IllegalArgumentException.class);

        // WHEN/THEN: Get size should fail
        assertThatThrownBy(() -> runPromise(() -> mediaService.getSize(tenantA, tenantBStorageKey)))
                .as("Get size across tenants should fail")
                .isInstanceOf(IllegalArgumentException.class);
    }
}
