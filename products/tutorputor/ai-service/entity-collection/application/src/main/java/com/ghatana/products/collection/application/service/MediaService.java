package com.ghatana.products.collection.application.service;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.products.collection.domain.media.MediaAttachment;
import com.ghatana.products.collection.domain.media.MediaStore;
import com.ghatana.products.collection.domain.media.MediaUrl;
import io.activej.promise.Promise;

import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for media file operations.
 *
 * <p><b>Purpose</b><br>
 * Orchestrates media upload, deletion, and URL generation with validation,
 * metrics collection, and error handling. Acts as facade to MediaStore port.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MediaService service = new MediaService(mediaStore, metricsCollector);
 * 
 * // Upload file
 * Promise<MediaAttachment> upload = service.upload(
 *     "tenant-123", entityId, "image.jpg", "image/jpeg", 
 *     524288, inputStream, "user-123");
 * 
 * // Generate access URL
 * Promise<MediaUrl> url = service.getUrl(
 *     "tenant-123", storageKey, Duration.ofHours(1));
 * 
 * // Delete file
 * Promise<Void> delete = service.delete("tenant-123", storageKey);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Application layer: coordinates domain operations with validation
 * - Uses MediaStore port for storage abstraction
 * - Enforces tenant isolation and content policies
 * - Emits metrics for observability
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe if MediaStore implementation is thread-safe.
 *
 * @see MediaStore
 * @see MediaAttachment
 * @see MediaUrl
 * @doc.type class
 * @doc.purpose Application service for media operations
 * @doc.layer application
 * @doc.pattern Service
 */
public class MediaService {

    /** Allowed content types */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/pdf"
    );

    /** Maximum file size: 10MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /** Default URL expiry: 1 hour */
    private static final Duration DEFAULT_URL_EXPIRY = Duration.ofHours(1);

    private final MediaStore mediaStore;
    private final MetricsCollector metricsCollector;

    /**
     * Creates media service with dependencies.
     *
     * @param mediaStore storage port implementation
     * @param metricsCollector metrics collector
     * @throws NullPointerException if any parameter is null
     */
    public MediaService(
            MediaStore mediaStore,
            MetricsCollector metricsCollector
    ) {
        this.mediaStore = Objects.requireNonNull(mediaStore, "mediaStore cannot be null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector cannot be null");
    }

    /**
     * Uploads a file with validation and metrics.
     *
     * <p>Validates:
     * - Tenant ID and entity ID are non-null
     * - File size within limits (max 10MB)
     * - Content type is allowed (images, PDFs only)
     * - Filename is non-blank
     *
     * <p>On success:
     * - Emits "media.upload.success" counter
     * - Records upload duration timer
     *
     * <p>On failure:
     * - Emits "media.upload.error" counter with error type
     *
     * @param tenantId tenant identifier
     * @param entityId entity this file belongs to
     * @param filename original filename
     * @param contentType MIME type
     * @param size file size in bytes
     * @param inputStream file content
     * @param uploadedBy user performing upload (for audit trail)
     * @return Promise of MediaAttachment with storage metadata
     * @throws IllegalArgumentException if validation fails
     */
    public Promise<MediaAttachment> upload(
            String tenantId,
            UUID entityId,
            String filename,
            String contentType,
            long size,
            InputStream inputStream,
            String uploadedBy
    ) {
        long startTime = System.nanoTime();

        // GIVEN: Input validation
        try {
            validateTenantId(tenantId);
            Objects.requireNonNull(entityId, "entityId cannot be null");
            validateFilename(filename);
            validateContentType(contentType);
            validateFileSize(size);
            Objects.requireNonNull(inputStream, "inputStream cannot be null");

        } catch (IllegalArgumentException e) {
            metricsCollector.incrementCounter("media.upload.error",
                    "tenant", tenantId != null ? tenantId : "unknown",
                    "error", "validation");
            return Promise.ofException(e);
        }

        // WHEN: Upload file
        return mediaStore.upload(tenantId, entityId, filename, contentType, size, inputStream)
                .map(attachment -> {
                    // Set uploaded by
                    if (uploadedBy != null) {
                        attachment.setUploadedBy(uploadedBy);
                    }

                    // THEN: Emit success metrics
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    metricsCollector.incrementCounter("media.upload.success",
                            "tenant", tenantId,
                            "contentType", contentType);
                    metricsCollector.recordTimer("media.upload.duration", durationMs,
                            "tenant", tenantId);

                    return attachment;
                })
                .whenException(ex -> {
                    // Emit error metrics
                    metricsCollector.incrementCounter("media.upload.error",
                            "tenant", tenantId,
                            "error", "storage");
                });
    }

    /**
     * Deletes a file with tenant isolation check.
     *
     * <p>Validates tenant ID matches storage key prefix to prevent
     * cross-tenant deletion attacks.
     *
     * @param tenantId tenant identifier
     * @param storageKey storage key from MediaAttachment
     * @return Promise of void (completes on success)
     * @throws IllegalArgumentException if validation fails or tenant mismatch
     */
    public Promise<Void> delete(String tenantId, String storageKey) {
        // GIVEN: Input validation
        try {
            validateTenantId(tenantId);
            validateStorageKey(storageKey);
            validateTenantOwnership(tenantId, storageKey);

        } catch (IllegalArgumentException e) {
            metricsCollector.incrementCounter("media.delete.error",
                    "tenant", tenantId != null ? tenantId : "unknown",
                    "error", "validation");
            return Promise.ofException(e);
        }

        // WHEN: Delete file
        return mediaStore.delete(storageKey)
                .whenResult(v -> {
                    // THEN: Emit success metrics
                    metricsCollector.incrementCounter("media.delete.success",
                            "tenant", tenantId);
                })
                .whenException(ex -> {
                    // Emit error metrics
                    metricsCollector.incrementCounter("media.delete.error",
                            "tenant", tenantId,
                            "error", "storage");
                });
    }

    /**
     * Generates access URL for a file.
     *
     * <p>For public storage: returns permanent URL<br>
     * For private storage: returns signed URL with expiration
     *
     * @param tenantId tenant identifier
     * @param storageKey storage key from MediaAttachment
     * @param expiry URL expiration duration (null = default 1 hour)
     * @return Promise of MediaUrl with access URL
     * @throws IllegalArgumentException if validation fails or tenant mismatch
     */
    public Promise<MediaUrl> getUrl(String tenantId, String storageKey, Duration expiry) {
        // GIVEN: Input validation
        try {
            validateTenantId(tenantId);
            validateStorageKey(storageKey);
            validateTenantOwnership(tenantId, storageKey);

        } catch (IllegalArgumentException e) {
            metricsCollector.incrementCounter("media.getUrl.error",
                    "tenant", tenantId != null ? tenantId : "unknown",
                    "error", "validation");
            return Promise.ofException(e);
        }

        Duration effectiveExpiry = expiry != null ? expiry : DEFAULT_URL_EXPIRY;

        // WHEN: Generate URL
        return mediaStore.generateUrl(storageKey, effectiveExpiry)
                .whenResult(url -> {
                    // THEN: Emit success metrics
                    metricsCollector.incrementCounter("media.getUrl.success",
                            "tenant", tenantId,
                            "urlType", url.isPublic() ? "public" : "signed");
                })
                .whenException(ex -> {
                    // Emit error metrics
                    metricsCollector.incrementCounter("media.getUrl.error",
                            "tenant", tenantId,
                            "error", "storage");
                });
    }

    /**
     * Checks if a file exists.
     *
     * @param tenantId tenant identifier
     * @param storageKey storage key to check
     * @return Promise of boolean (true if exists, false otherwise)
     */
    public Promise<Boolean> exists(String tenantId, String storageKey) {
        try {
            validateTenantId(tenantId);
            validateStorageKey(storageKey);
            validateTenantOwnership(tenantId, storageKey);
        } catch (IllegalArgumentException e) {
            return Promise.ofException(e);
        }

        return mediaStore.exists(storageKey);
    }

    /**
     * Gets file size in bytes.
     *
     * @param tenantId tenant identifier
     * @param storageKey storage key to check
     * @return Promise of Long (file size in bytes, or null if not found)
     */
    public Promise<Long> getSize(String tenantId, String storageKey) {
        try {
            validateTenantId(tenantId);
            validateStorageKey(storageKey);
            validateTenantOwnership(tenantId, storageKey);
        } catch (IllegalArgumentException e) {
            return Promise.ofException(e);
        }

        return mediaStore.getSize(storageKey);
    }

    // Validation helper methods

    private void validateTenantId(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
    }

    private void validateFilename(String filename) {
        Objects.requireNonNull(filename, "filename cannot be null");
        if (filename.isBlank()) {
            throw new IllegalArgumentException("filename cannot be blank");
        }
    }

    private void validateContentType(String contentType) {
        Objects.requireNonNull(contentType, "contentType cannot be null");
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Content type not allowed: " + contentType + ". Allowed: " + ALLOWED_CONTENT_TYPES);
        }
    }

    private void validateFileSize(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }
        if (size > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size " + size + " exceeds maximum " + MAX_FILE_SIZE + " bytes");
        }
    }

    private void validateStorageKey(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey cannot be null");
        if (storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey cannot be blank");
        }
    }

    private void validateTenantOwnership(String tenantId, String storageKey) {
        // Storage key format: {tenantId}/{entityId}/{uuid}.{ext}
        // Validate tenant owns this file
        if (!storageKey.startsWith(tenantId + "/")) {
            throw new IllegalArgumentException(
                    "Storage key does not belong to tenant: " + tenantId);
        }
    }
}
