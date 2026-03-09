package com.ghatana.products.collection.infrastructure.media;

import com.ghatana.products.collection.domain.media.MediaAttachment;
import com.ghatana.products.collection.domain.media.MediaStore;
import com.ghatana.products.collection.domain.media.MediaUrl;
import io.activej.promise.Promise;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of MediaStore for testing.
 *
 * <p><b>Purpose</b><br>
 * Provides fast, deterministic storage for unit tests without external dependencies.
 * Stores files in memory as byte arrays with metadata.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MediaStore store = new InMemoryMediaStore();
 * 
 * // Upload file
 * MediaAttachment attachment = store.upload(
 *     "tenant-123", entityId, "test.jpg", "image/jpeg", 
 *     1024, new ByteArrayInputStream(bytes))
 *     .getResult();
 * 
 * // Retrieve URL
 * MediaUrl url = store.generateUrl(
 *     attachment.getStorageKey(), Duration.ofHours(1))
 *     .getResult();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe using ConcurrentHashMap for storage.
 *
 * <p><b>Limitations</b><br>
 * - Files stored in memory (not persistent across restarts)
 * - No actual file I/O validation
 * - URLs are synthetic (not real HTTP endpoints)
 *
 * @see MediaStore
 * @see S3MediaStore
 * @doc.type class
 * @doc.purpose In-memory media storage for testing
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class InMemoryMediaStore implements MediaStore {

    /** Storage map: storageKey -> file content */
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    /** Metadata map: storageKey -> MediaAttachment */
    private final Map<String, MediaAttachment> metadata = new ConcurrentHashMap<>();

    /** Base URL for generated URLs */
    private final String baseUrl;

    /** Maximum file size in bytes (default 10MB) */
    private final long maxFileSize;

    /**
     * Creates in-memory store with default settings.
     *
     * <p>Defaults:
     * - Base URL: "https://inmemory.storage.local"
     * - Max file size: 10MB
     */
    public InMemoryMediaStore() {
        this("https://inmemory.storage.local", 10 * 1024 * 1024);
    }

    /**
     * Creates in-memory store with custom settings.
     *
     * @param baseUrl base URL for generated URLs
     * @param maxFileSize maximum file size in bytes
     */
    public InMemoryMediaStore(String baseUrl, long maxFileSize) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        this.maxFileSize = maxFileSize;
    }

    @Override
    public Promise<MediaAttachment> upload(
            String tenantId,
            UUID entityId,
            String filename,
            String contentType,
            long size,
            InputStream inputStream
    ) {
        // GIVEN: Input validation
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(filename, "filename cannot be null");
        Objects.requireNonNull(contentType, "contentType cannot be null");
        Objects.requireNonNull(inputStream, "inputStream cannot be null");

        if (tenantId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("tenantId cannot be blank"));
        }
        if (filename.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("filename cannot be blank"));
        }
        if (size < 0) {
            return Promise.ofException(new IllegalArgumentException("size cannot be negative"));
        }
        if (size > maxFileSize) {
            return Promise.ofException(new IllegalArgumentException(
                    "File size " + size + " exceeds maximum " + maxFileSize));
        }

        try {
            // WHEN: Read file content
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            byte[] content = buffer.toByteArray();

            // Generate storage key: tenant/entity/uuid.ext
            String extension = getFileExtension(filename);
            String storageKey = String.format("%s/%s/%s%s",
                    tenantId, entityId, UUID.randomUUID(), extension);

            // Store file content
            files.put(storageKey, content);

            // Create metadata
            MediaAttachment attachment = MediaAttachment.builder()
                    .id(UUID.randomUUID())
                    .entityId(entityId)
                    .tenantId(tenantId)
                    .filename(filename)
                    .contentType(contentType)
                    .size((long) content.length)
                    .storageKey(storageKey)
                    .uploadedAt(Instant.now())
                    .isActive(true)
                    .build();

            metadata.put(storageKey, attachment);

            // THEN: Return attachment
            return Promise.of(attachment);

        } catch (Exception e) {
            return Promise.ofException(new RuntimeException("Failed to upload file: " + e.getMessage(), e));
        }
    }

    @Override
    public Promise<Void> delete(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey cannot be null");
        if (storageKey.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("storageKey cannot be blank"));
        }

        // Remove file and metadata (idempotent - no error if not exists)
        files.remove(storageKey);
        metadata.remove(storageKey);

        return Promise.complete();
    }

    @Override
    public Promise<MediaUrl> generateUrl(String storageKey, Duration expiry) {
        Objects.requireNonNull(storageKey, "storageKey cannot be null");
        Objects.requireNonNull(expiry, "expiry cannot be null");

        if (storageKey.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("storageKey cannot be blank"));
        }

        // Check if file exists
        if (!files.containsKey(storageKey)) {
            return Promise.ofException(new IllegalArgumentException(
                    "File not found: " + storageKey));
        }

        // Generate synthetic URL
        String url = baseUrl + "/" + storageKey;

        // For in-memory store, always use signed URLs with expiration
        Instant expiresAt = Instant.now().plus(expiry);
        MediaUrl mediaUrl = MediaUrl.signedUrl(url, expiresAt);

        return Promise.of(mediaUrl);
    }

    @Override
    public Promise<Boolean> exists(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey cannot be null");
        return Promise.of(files.containsKey(storageKey));
    }

    @Override
    public Promise<Long> getSize(String storageKey) {
        Objects.requireNonNull(storageKey, "storageKey cannot be null");

        byte[] content = files.get(storageKey);
        if (content == null) {
            return Promise.of(null);
        }

        return Promise.of((long) content.length);
    }

    /**
     * Gets file content for testing/verification.
     *
     * <p>NOT part of MediaStore interface - test utility only.
     *
     * @param storageKey storage key
     * @return file content or null if not found
     */
    public byte[] getFileContent(String storageKey) {
        return files.get(storageKey);
    }

    /**
     * Gets metadata for testing/verification.
     *
     * <p>NOT part of MediaStore interface - test utility only.
     *
     * @param storageKey storage key
     * @return MediaAttachment or null if not found
     */
    public MediaAttachment getMetadata(String storageKey) {
        return metadata.get(storageKey);
    }

    /**
     * Clears all stored files and metadata.
     *
     * <p>Test utility for cleaning up between tests.
     */
    public void clear() {
        files.clear();
        metadata.clear();
    }

    /**
     * Gets count of stored files.
     *
     * @return number of files in storage
     */
    public int getFileCount() {
        return files.size();
    }

    /**
     * Extracts file extension from filename.
     *
     * @param filename filename with extension
     * @return extension with dot (e.g., ".jpg") or empty string
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot);
        }
        return "";
    }
}
