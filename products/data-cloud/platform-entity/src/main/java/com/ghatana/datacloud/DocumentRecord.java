package com.ghatana.datacloud;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Schema-free document record - flexible structure with content-centric features.
 *
 * <p>
 * <b>Purpose</b><br>
 * Stores loosely structured or unstructured content where the shape of data is
 * unknown at design time. Follows the document-oriented model used by MongoDB,
 * Elasticsearch, and similar stores. Each record is self-describing — the
 * {@code data} map is the document body and {@code contentType} declares its
 * media type.
 *
 * <p>
 * <b>Features</b><br>
 * <ul>
 * <li><b>Schema-Free</b> — No fixed column structure; payload carried in
 *     JSONB {@code data}.</li>
 * <li><b>Content Type</b> — MIME-style declaration allows mixed content
 *     (JSON, HTML, plain text).</li>
 * <li><b>Tagging</b> — Comma-separated tags for lightweight classification.</li>
 * <li><b>Slug / Title</b> — Human-friendly identifiers for CMS-style use
 *     cases.</li>
 * <li><b>Versioning &amp; Soft Delete</b> — Full CRUD with optimistic
 *     locking, identical to EntityRecord.</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Create a JSON document
 * DocumentRecord doc = DocumentRecord.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("knowledge-base")
 *     .title("Getting Started Guide")
 *     .slug("getting-started")
 *     .contentType("application/json")
 *     .tags("onboarding,guide,quickstart")
 *     .data(Map.of(
 *         "sections", List.of(
 *             Map.of("heading", "Installation", "body", "Run pip install …"),
 *             Map.of("heading", "Configuration", "body", "Set env vars …")
 *         ),
 *         "author", "docs-team"
 *     ))
 *     .build();
 *
 * // Full-text search (delegated to EmbeddedDataCloudClient)
 * var results = client.fullTextSearch(tenantId, "knowledge-base", "installation");
 *
 * // Update document body — version auto-increments
 * doc.getData().put("status", "published");
 * repository.save(doc);
 * }</pre>
 *
 * <p>
 * <b>Database Table</b><br>
 * <pre>
 * CREATE TABLE documents (
 *     id UUID PRIMARY KEY,
 *     tenant_id VARCHAR(255) NOT NULL,
 *     collection_name VARCHAR(255) NOT NULL,
 *     record_type VARCHAR(50) NOT NULL,
 *     data JSONB,
 *     metadata JSONB,
 *     title VARCHAR(512),
 *     slug VARCHAR(512),
 *     content_type VARCHAR(128) DEFAULT 'application/json',
 *     tags VARCHAR(2048),
 *     language VARCHAR(10),
 *     version INTEGER DEFAULT 1,
 *     active BOOLEAN DEFAULT TRUE,
 *     created_at TIMESTAMP,
 *     created_by VARCHAR(255),
 *     updated_at TIMESTAMP,
 *     updated_by VARCHAR(255)
 * );
 * </pre>
 *
 * @see RecordType#DOCUMENT
 * @see DataRecord
 * @doc.type class
 * @doc.purpose Schema-free document record with content management features
 * @doc.layer core
 * @doc.pattern Domain Model, Document Store
 */
@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_doc_tenant", columnList = "tenant_id"),
    @Index(name = "idx_doc_collection", columnList = "tenant_id, collection_name"),
    @Index(name = "idx_doc_slug", columnList = "tenant_id, collection_name, slug"),
    @Index(name = "idx_doc_content_type", columnList = "tenant_id, collection_name, content_type"),
    @Index(name = "idx_doc_active", columnList = "tenant_id, collection_name, active"),
    @Index(name = "idx_doc_created_at", columnList = "tenant_id, collection_name, created_at DESC")
})
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRecord extends DataRecord {

    // ═══════════════════════════════════════════════════════════════
    // Document-specific Fields
    // ═══════════════════════════════════════════════════════════════

    /**
     * Human-readable title of the document.
     */
    @Column(name = "title", length = 512)
    private String title;

    /**
     * URL-friendly slug for human-readable addressing.
     * <p>
     * Unique within (tenantId, collectionName).
     */
    @Column(name = "slug", length = 512)
    private String slug;

    /**
     * MIME content type declaring the document's format.
     * <p>
     * Common values: {@code application/json}, {@code text/plain},
     * {@code text/html}, {@code text/markdown}.
     */
    @Column(name = "content_type", length = 128)
    @Builder.Default
    private String contentType = "application/json";

    /**
     * Comma-separated tags for lightweight classification.
     * <p>
     * Use {@link #getTagList()} for typed access.
     */
    @Column(name = "tags", length = 2048)
    private String tags;

    /**
     * ISO 639-1 language code (e.g. "en", "fr", "ja").
     */
    @Column(name = "language", length = 10)
    private String language;

    // ═══════════════════════════════════════════════════════════════
    // Entity Lifecycle (mutable, versioned — parallels EntityRecord)
    // ═══════════════════════════════════════════════════════════════

    @Version
    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    // ═══════════════════════════════════════════════════════════════
    // RecordType
    // ═══════════════════════════════════════════════════════════════

    @Override
    public RecordType getRecordType() {
        return RecordType.DOCUMENT;
    }

    @Override
    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (recordType == null) recordType = RecordType.DOCUMENT;
        if (contentType == null) contentType = "application/json";
        if (version == null) version = 1;
        if (active == null) active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ═══════════════════════════════════════════════════════════════
    // Document helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns tags as a list, splitting on comma.
     *
     * @return unmodifiable list of tags; empty list if no tags
     */
    public java.util.List<String> getTagList() {
        if (tags == null || tags.isBlank()) {
            return java.util.List.of();
        }
        return java.util.List.of(tags.split(","))
                .stream()
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();
    }

    /**
     * Sets tags from a collection.
     *
     * @param tagList tags to set
     */
    public void setTagList(java.util.Collection<String> tagList) {
        this.tags = tagList == null || tagList.isEmpty()
                ? null
                : String.join(",", tagList);
    }

    /**
     * Whether this document has the given tag.
     *
     * @param tag tag to check (case-insensitive)
     * @return true if present
     */
    public boolean hasTag(String tag) {
        return tag != null && getTagList().stream()
                .anyMatch(t -> t.equalsIgnoreCase(tag));
    }

    /**
     * Whether this document has JSON content type.
     *
     * @return true if content type starts with {@code application/json}
     */
    public boolean isJson() {
        return contentType != null && contentType.startsWith("application/json");
    }

    /**
     * Whether this document has text content type.
     *
     * @return true if content type starts with {@code text/}
     */
    public boolean isText() {
        return contentType != null && contentType.startsWith("text/");
    }

    /**
     * Soft-delete this document.
     *
     * @param deletedBy user performing the delete
     */
    public void softDelete(String deletedBy) {
        this.active = false;
        this.updatedBy = deletedBy;
        this.updatedAt = Instant.now();
    }

    /**
     * Restore a soft-deleted document.
     *
     * @param restoredBy user performing the restore
     */
    public void restore(String restoredBy) {
        this.active = true;
        this.updatedBy = restoredBy;
        this.updatedAt = Instant.now();
    }

    /**
     * Check if this document is soft-deleted.
     *
     * @return true if inactive
     */
    public boolean isDeleted() {
        return !Boolean.TRUE.equals(active);
    }

    @Override
    public String toString() {
        return "DocumentRecord{"
                + "id=" + id
                + ", tenantId='" + tenantId + '\''
                + ", collectionName='" + collectionName + '\''
                + ", title='" + title + '\''
                + ", slug='" + slug + '\''
                + ", contentType='" + contentType + '\''
                + ", version=" + version
                + ", active=" + active
                + '}';
    }
}
