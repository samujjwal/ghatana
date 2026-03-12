package com.ghatana.products.collection.domain.media;

import io.activej.promise.Promise;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

/**
 * Port interface for media storage operations.
 *
 * <p><b>Purpose</b><br>
 * Abstracts file storage implementations (S3, local filesystem, etc.) to enable
 * pluggable storage backends without changing domain/application logic.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MediaStore store = new S3MediaStore(s3Client, bucketName, metricsCollector);
 * 
 * // Upload file
 * Promise<MediaAttachment> uploadResult = store.upload(
 *     tenantId, entityId, filename, contentType, size, inputStream);
 * 
 * // Generate signed URL
 * Promise<MediaUrl> urlResult = store.generateUrl(
 *     storageKey, Duration.ofHours(1));
 * 
 * // Delete file
 * Promise<Void> deleteResult = store.delete(storageKey);
 * }</pre>
 *
 * <p><b>Implementation Notes</b><br>
 * Implementations must:
 * - Be thread-safe (multiple concurrent operations allowed)
 * - Use ActiveJ Promise for async operations
 * - Implement tenant isolation (files stored with tenant prefix)
 * - Handle cleanup on upload failures (no orphaned files)
 * - Emit metrics for storage operations
 *
 * <p><b>Storage Key Format</b><br>
 * Storage keys should follow convention: {@code {tenantId}/{entityId}/{uuid}.{ext}}
 * Example: {@code "tenant-123/entity-456/abc-def-uuid.jpg"}
 *
 * @see MediaAttachment
 * @see MediaUrl
 * @see S3MediaStore
 * @see InMemoryMediaStore
 * @doc.type interface
 * @doc.purpose Port for media storage abstraction
 * @doc.layer product
 * @doc.pattern Port
 */
public interface MediaStore {

    /**
     * Uploads a file to storage and returns attachment metadata.
     *
     * <p>Implementation should:
     * - Generate unique storage key with tenant/entity prefix
     * - Validate file size and content type
     * - Store file content to backend
     * - Return MediaAttachment with storageKey populated
     * - Clean up on failure (delete partial uploads)
     *
     * @param tenantId tenant identifier for isolation
     * @param entityId entity this file belongs to
     * @param filename original filename (e.g., "product.jpg")
     * @param contentType MIME type (e.g., "image/jpeg")
     * @param size file size in bytes
     * @param inputStream file content stream
     * @return Promise of MediaAttachment with storage metadata
     * @throws IllegalArgumentException if validation fails (size too large, invalid type)
     */
    Promise<MediaAttachment> upload(
            String tenantId,
            UUID entityId,
            String filename,
            String contentType,
            long size,
            InputStream inputStream
    );

    /**
     * Deletes a file from storage.
     *
     * <p>Implementation should:
     * - Remove file from backend
     * - Handle missing files gracefully (idempotent)
     * - Emit deletion metrics
     *
     * @param storageKey storage key from MediaAttachment
     * @return Promise of void (completes on success)
     * @throws IllegalArgumentException if storageKey is null/blank
     */
    Promise<Void> delete(String storageKey);

    /**
     * Generates a URL for accessing a file.
     *
     * <p>For public storage: returns permanent public URL<br>
     * For private storage: returns signed URL with expiration
     *
     * @param storageKey storage key from MediaAttachment
     * @param expiry URL expiration duration (ignored for public storage)
     * @return Promise of MediaUrl with access URL
     * @throws IllegalArgumentException if storageKey is null/blank
     */
    Promise<MediaUrl> generateUrl(String storageKey, Duration expiry);

    /**
     * Checks if a file exists in storage.
     *
     * <p>Used for validation and cleanup operations.
     *
     * @param storageKey storage key to check
     * @return Promise of boolean (true if exists, false otherwise)
     */
    Promise<Boolean> exists(String storageKey);

    /**
     * Gets file size in bytes.
     *
     * <p>Used for validation and quota management.
     *
     * @param storageKey storage key to check
     * @return Promise of Long (file size in bytes, or null if not found)
     */
    Promise<Long> getSize(String storageKey);
}
