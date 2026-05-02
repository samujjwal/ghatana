package com.ghatana.digitalmarketing.domain.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable version snapshot for a content asset.
 *
 * @doc.type class
 * @doc.purpose DMOS immutable content asset version for version-controlled asset library
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class ContentAssetVersion {

    private final String versionId;
    private final String assetId;
    private final DmWorkspaceId workspaceId;
    private final int versionNumber;
    private final String contentBody;
    private final String changeSummary;
    private final Instant createdAt;
    private final String createdBy;

    private ContentAssetVersion(Builder builder) {
        this.versionId = Objects.requireNonNull(builder.versionId, "versionId must not be null");
        this.assetId = Objects.requireNonNull(builder.assetId, "assetId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.versionNumber = builder.versionNumber;
        this.contentBody = Objects.requireNonNull(builder.contentBody, "contentBody must not be null");
        this.changeSummary = builder.changeSummary != null ? builder.changeSummary : "";
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.createdBy = Objects.requireNonNull(builder.createdBy, "createdBy must not be null");

        if (this.versionId.isBlank()) {
            throw new IllegalArgumentException("versionId must not be blank");
        }
        if (this.assetId.isBlank()) {
            throw new IllegalArgumentException("assetId must not be blank");
        }
        if (this.versionNumber <= 0) {
            throw new IllegalArgumentException("versionNumber must be positive");
        }
    }

    public String getVersionId() {
        return versionId;
    }

    public String getAssetId() {
        return assetId;
    }

    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getContentBody() {
        return contentBody;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String versionId;
        private String assetId;
        private DmWorkspaceId workspaceId;
        private int versionNumber;
        private String contentBody;
        private String changeSummary;
        private Instant createdAt;
        private String createdBy;

        private Builder() {
        }

        public Builder versionId(String versionId) {
            this.versionId = versionId;
            return this;
        }

        public Builder assetId(String assetId) {
            this.assetId = assetId;
            return this;
        }

        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder versionNumber(int versionNumber) {
            this.versionNumber = versionNumber;
            return this;
        }

        public Builder contentBody(String contentBody) {
            this.contentBody = contentBody;
            return this;
        }

        public Builder changeSummary(String changeSummary) {
            this.changeSummary = changeSummary;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public ContentAssetVersion build() {
            return new ContentAssetVersion(this);
        }
    }
}
