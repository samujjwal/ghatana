package com.ghatana.products.collection.domain.media;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a media attachment linked to a collection entity.
 *
 * <p><b>Purpose</b><br>
 * Tracks file metadata for images, documents, and other media attached to entities.
 * Stores reference to actual file (via storageKey), not the file content itself.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MediaAttachment attachment = MediaAttachment.builder()
 *     .id(UUID.randomUUID())
 *     .entityId(entityId)
 *     .tenantId(tenantId)
 *     .filename("product-image.jpg")
 *     .contentType("image/jpeg")
 *     .size(524288L)
 *     .storageKey("tenant-123/entity-456/image-uuid.jpg")
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * This class is mutable but not thread-safe. Synchronize external access if shared.
 *
 * @see MediaStore
 * @see MediaUrl
 * @doc.type class
 * @doc.purpose Domain entity for media attachment metadata
 * @doc.layer product
 * @doc.pattern Entity
 */
public class MediaAttachment {

    /** Unique identifier for this attachment */
    private UUID id;

    /** Entity this attachment belongs to */
    private UUID entityId;

    /** Tenant identifier for multi-tenancy */
    private String tenantId;

    /** Original filename (e.g., "product-image.jpg") */
    private String filename;

    /** MIME type (e.g., "image/jpeg", "application/pdf") */
    private String contentType;

    /** File size in bytes */
    private Long size;

    /** Storage key/path for retrieving file (e.g., "tenant-123/entity-456/file-uuid.ext") */
    private String storageKey;

    /** Public or signed URL for accessing file (generated on-demand) */
    private String url;

    /** Upload timestamp */
    private Instant uploadedAt;

    /** User who uploaded the file (for audit trail) */
    private String uploadedBy;

    /** Whether this attachment is currently active (soft delete support) */
    private boolean isActive;

    /**
     * No-arg constructor for JPA/ORM frameworks.
     */
    public MediaAttachment() {
        this.isActive = true;
    }

    /**
     * Validates media attachment state.
     *
     * <p>Ensures all required fields are present and valid.
     *
     * @throws IllegalStateException if validation fails
     */
    public void validate() {
        if (id == null) {
            throw new IllegalStateException("MediaAttachment id cannot be null");
        }
        if (entityId == null) {
            throw new IllegalStateException("MediaAttachment entityId cannot be null");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("MediaAttachment tenantId cannot be null or blank");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalStateException("MediaAttachment filename cannot be null or blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalStateException("MediaAttachment contentType cannot be null or blank");
        }
        if (size == null || size < 0) {
            throw new IllegalStateException("MediaAttachment size must be non-negative");
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalStateException("MediaAttachment storageKey cannot be null or blank");
        }
    }

    /**
     * Checks if content type is an image.
     *
     * @return true if content type starts with "image/"
     */
    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }

    /**
     * Checks if content type is a PDF document.
     *
     * @return true if content type is "application/pdf"
     */
    public boolean isPdf() {
        return "application/pdf".equals(contentType);
    }

    /**
     * Gets human-readable file size string.
     *
     * @return formatted size (e.g., "512 KB", "2.5 MB")
     */
    public String getFormattedSize() {
        if (size == null) {
            return "0 B";
        }
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaAttachment that = (MediaAttachment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MediaAttachment{" +
                "id=" + id +
                ", entityId=" + entityId +
                ", tenantId='" + tenantId + '\'' +
                ", filename='" + filename + '\'' +
                ", contentType='" + contentType + '\'' +
                ", size=" + size +
                ", storageKey='" + storageKey + '\'' +
                ", isActive=" + isActive +
                '}';
    }

    /**
     * Builder for constructing MediaAttachment instances.
     *
     * <p><b>Usage</b><br>
     * <pre>{@code
     * MediaAttachment attachment = MediaAttachment.builder()
     *     .id(UUID.randomUUID())
     *     .entityId(entityId)
     *     .tenantId(tenantId)
     *     .filename("document.pdf")
     *     .contentType("application/pdf")
     *     .size(1024000L)
     *     .storageKey("path/to/file")
     *     .uploadedBy("user-123")
     *     .build();
     * }</pre>
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final MediaAttachment attachment = new MediaAttachment();

        public Builder id(UUID id) {
            attachment.id = id;
            return this;
        }

        public Builder entityId(UUID entityId) {
            attachment.entityId = entityId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            attachment.tenantId = tenantId;
            return this;
        }

        public Builder filename(String filename) {
            attachment.filename = filename;
            return this;
        }

        public Builder contentType(String contentType) {
            attachment.contentType = contentType;
            return this;
        }

        public Builder size(Long size) {
            attachment.size = size;
            return this;
        }

        public Builder storageKey(String storageKey) {
            attachment.storageKey = storageKey;
            return this;
        }

        public Builder url(String url) {
            attachment.url = url;
            return this;
        }

        public Builder uploadedAt(Instant uploadedAt) {
            attachment.uploadedAt = uploadedAt;
            return this;
        }

        public Builder uploadedBy(String uploadedBy) {
            attachment.uploadedBy = uploadedBy;
            return this;
        }

        public Builder isActive(boolean isActive) {
            attachment.isActive = isActive;
            return this;
        }

        public MediaAttachment build() {
            attachment.validate();
            return attachment;
        }
    }
}
