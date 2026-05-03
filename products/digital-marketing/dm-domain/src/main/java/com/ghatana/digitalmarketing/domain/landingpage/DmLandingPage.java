package com.ghatana.digitalmarketing.domain.landingpage;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing a landing page and its publishing status.
 *
 * @doc.type class
 * @doc.purpose Domain entity for landing page runtime publishing (DMOS-F2-010)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmLandingPage {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String slug;
    private final String title;
    private final String contentHtml;
    private final DmLandingPageStatus status;
    private final String publishedUrl;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmLandingPage(Builder b) {
        this.id            = b.id;
        this.tenantId      = b.tenantId;
        this.workspaceId   = b.workspaceId;
        this.slug          = b.slug;
        this.title         = b.title;
        this.contentHtml   = b.contentHtml;
        this.status        = b.status;
        this.publishedUrl  = b.publishedUrl;
        this.failureReason = b.failureReason;
        this.createdAt     = b.createdAt;
        this.updatedAt     = b.updatedAt;
    }

    public DmLandingPage publish(String publishedUrl) {
        Objects.requireNonNull(publishedUrl, "publishedUrl must not be null");
        if (status != DmLandingPageStatus.DRAFT && status != DmLandingPageStatus.UNPUBLISHED) {
            throw new IllegalStateException("Cannot publish page in status: " + status);
        }
        return toBuilder().status(DmLandingPageStatus.PUBLISHED)
            .publishedUrl(publishedUrl).updatedAt(Instant.now()).build();
    }

    public DmLandingPage unpublish() {
        if (status != DmLandingPageStatus.PUBLISHED) {
            throw new IllegalStateException("Cannot unpublish page in status: " + status);
        }
        return toBuilder().status(DmLandingPageStatus.UNPUBLISHED).updatedAt(Instant.now()).build();
    }

    public DmLandingPage markFailed(String reason) {
        return toBuilder().status(DmLandingPageStatus.FAILED)
            .failureReason(reason).updatedAt(Instant.now()).build();
    }

    public String getId()           { return id; }
    public String getTenantId()     { return tenantId; }
    public String getWorkspaceId()  { return workspaceId; }
    public String getSlug()         { return slug; }
    public String getTitle()        { return title; }
    public String getContentHtml()  { return contentHtml; }
    public DmLandingPageStatus getStatus() { return status; }
    public String getPublishedUrl() { return publishedUrl; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt()   { return createdAt; }
    public Instant getUpdatedAt()   { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmLandingPage)) return false;
        return id.equals(((DmLandingPage) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmLandingPage{id='" + id + "', slug='" + slug + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .slug(slug).title(title).contentHtml(contentHtml).status(status)
            .publishedUrl(publishedUrl).failureReason(failureReason)
            .createdAt(createdAt).updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, slug, title, contentHtml;
        private DmLandingPageStatus status;
        private String publishedUrl, failureReason;
        private Instant createdAt, updatedAt;

        public Builder id(String v)            { this.id = v; return this; }
        public Builder tenantId(String v)      { this.tenantId = v; return this; }
        public Builder workspaceId(String v)   { this.workspaceId = v; return this; }
        public Builder slug(String v)          { this.slug = v; return this; }
        public Builder title(String v)         { this.title = v; return this; }
        public Builder contentHtml(String v)   { this.contentHtml = v; return this; }
        public Builder status(DmLandingPageStatus v) { this.status = v; return this; }
        public Builder publishedUrl(String v)  { this.publishedUrl = v; return this; }
        public Builder failureReason(String v) { this.failureReason = v; return this; }
        public Builder createdAt(Instant v)    { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)    { this.updatedAt = v; return this; }

        public DmLandingPage build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (slug == null || slug.isBlank()) throw new IllegalArgumentException("slug must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmLandingPage(this);
        }
    }
}
