package com.ghatana.digitalmarketing.domain.content;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An immutable snapshot of a content item at a particular version number.
 *
 * <p>A {@code ContentVersion} progresses through: {@code DRAFT → PENDING_REVIEW → APPROVED → ARCHIVED}.
 * Once approved, the version body is immutable. Campaign launches must reference only
 * {@link ContentVersionStatus#APPROVED} versions. Edits produce new draft versions.</p>
 *
 * <p>Instances are created via {@link #builder()} and transitioned via
 * {@link #submitForReview()}, {@link #approve(String, Instant)}, and {@link #archive()}.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS content version aggregate for immutable, approvable content snapshots
 * @doc.layer product
 * @doc.pattern Aggregate
 */
public final class ContentVersion {

    private final String versionId;
    private final String itemId;
    private final DmWorkspaceId workspaceId;
    private final int versionNumber;
    private final List<ContentBlock> contentBlocks;
    private final List<ClaimReference> claimReferences;
    private final List<DisclosureReference> disclosureReferences;
    private final GeneratorMetadata generatorMetadata;
    private final ContentVersionStatus status;
    private final String approvedBy;
    private final Instant approvedAt;
    private final Instant createdAt;
    private final String createdBy;

    private ContentVersion(Builder builder) {
        this.versionId           = Objects.requireNonNull(builder.versionId,           "versionId must not be null");
        this.itemId              = Objects.requireNonNull(builder.itemId,              "itemId must not be null");
        this.workspaceId         = Objects.requireNonNull(builder.workspaceId,         "workspaceId must not be null");
        this.contentBlocks       = Objects.requireNonNull(builder.contentBlocks,       "contentBlocks must not be null");
        this.claimReferences     = Objects.requireNonNull(builder.claimReferences,     "claimReferences must not be null");
        this.disclosureReferences= Objects.requireNonNull(builder.disclosureReferences,"disclosureReferences must not be null");
        this.generatorMetadata   = Objects.requireNonNull(builder.generatorMetadata,   "generatorMetadata must not be null");
        this.status              = Objects.requireNonNull(builder.status,              "status must not be null");
        this.createdAt           = Objects.requireNonNull(builder.createdAt,           "createdAt must not be null");
        this.createdBy           = Objects.requireNonNull(builder.createdBy,           "createdBy must not be null");
        this.versionNumber       = builder.versionNumber;
        this.approvedBy          = builder.approvedBy;
        this.approvedAt          = builder.approvedAt;
        if (this.versionId.isBlank())  throw new IllegalArgumentException("versionId must not be blank");
        if (this.itemId.isBlank())     throw new IllegalArgumentException("itemId must not be blank");
        if (this.createdBy.isBlank())  throw new IllegalArgumentException("createdBy must not be blank");
        if (this.versionNumber <= 0)   throw new IllegalArgumentException("versionNumber must be positive");
        if (this.contentBlocks.isEmpty()) throw new IllegalArgumentException("contentBlocks must not be empty");
    }

    /** Returns the version identifier. Never {@code null} or blank. */
    public String getVersionId() { return versionId; }

    /** Returns the parent content item ID. Never blank. */
    public String getItemId() { return itemId; }

    /** Returns the owning workspace. Never {@code null}. */
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }

    /** Returns the sequential version number (≥ 1). */
    public int getVersionNumber() { return versionNumber; }

    /** Returns the ordered content blocks. Never {@code null}; at least one element. */
    public List<ContentBlock> getContentBlocks() { return contentBlocks; }

    /** Returns the claim references for compliance linkage. Never {@code null}. */
    public List<ClaimReference> getClaimReferences() { return claimReferences; }

    /** Returns the disclosure references for compliance linkage. Never {@code null}. */
    public List<DisclosureReference> getDisclosureReferences() { return disclosureReferences; }

    /** Returns the AI generator metadata for this version. Never {@code null}. */
    public GeneratorMetadata getGeneratorMetadata() { return generatorMetadata; }

    /** Returns the lifecycle status. Never {@code null}. */
    public ContentVersionStatus getStatus() { return status; }

    /**
     * Returns the principal who approved this version, or {@code null} if not yet approved.
     */
    public String getApprovedBy() { return approvedBy; }

    /**
     * Returns the approval timestamp, or {@code null} if not yet approved.
     */
    public Instant getApprovedAt() { return approvedAt; }

    /** Returns the creation timestamp. Never {@code null}. */
    public Instant getCreatedAt() { return createdAt; }

    /** Returns the principal who created this version. Never blank. */
    public String getCreatedBy() { return createdBy; }

    /**
     * Returns {@code true} if this version is in {@link ContentVersionStatus#APPROVED} state.
     */
    public boolean isApproved() { return status == ContentVersionStatus.APPROVED; }

    /**
     * Transitions this version from {@link ContentVersionStatus#DRAFT} to
     * {@link ContentVersionStatus#PENDING_REVIEW}.
     *
     * @return a new {@code ContentVersion} in {@code PENDING_REVIEW} state
     * @throws IllegalStateException if the current status is not {@code DRAFT}
     */
    public ContentVersion submitForReview() {
        if (status != ContentVersionStatus.DRAFT) {
            throw new IllegalStateException(
                "Cannot submit for review: current status is " + status);
        }
        return toBuilder().status(ContentVersionStatus.PENDING_REVIEW).build();
    }

    /**
     * Transitions this version from {@link ContentVersionStatus#PENDING_REVIEW} to
     * {@link ContentVersionStatus#APPROVED}.
     *
     * @param approvedBy the principal granting approval; must not be null or blank
     * @param approvedAt the approval timestamp; must not be null
     * @return a new {@code ContentVersion} in {@code APPROVED} state with approval snapshot
     * @throws IllegalStateException if the current status is not {@code PENDING_REVIEW}
     * @throws NullPointerException  if {@code approvedBy} or {@code approvedAt} is null
     * @throws IllegalArgumentException if {@code approvedBy} is blank
     */
    public ContentVersion approve(String approvedBy, Instant approvedAt) {
        Objects.requireNonNull(approvedBy, "approvedBy must not be null");
        Objects.requireNonNull(approvedAt, "approvedAt must not be null");
        if (approvedBy.isBlank()) throw new IllegalArgumentException("approvedBy must not be blank");
        if (status != ContentVersionStatus.PENDING_REVIEW) {
            throw new IllegalStateException(
                "Cannot approve: current status is " + status);
        }
        return toBuilder()
            .status(ContentVersionStatus.APPROVED)
            .approvedBy(approvedBy)
            .approvedAt(approvedAt)
            .build();
    }

    /**
     * Transitions this version from {@link ContentVersionStatus#APPROVED} to
     * {@link ContentVersionStatus#ARCHIVED}.
     *
     * @return a new {@code ContentVersion} in {@code ARCHIVED} state
     * @throws IllegalStateException if the current status is not {@code APPROVED}
     */
    public ContentVersion archive() {
        if (status != ContentVersionStatus.APPROVED) {
            throw new IllegalStateException(
                "Cannot archive: current status is " + status);
        }
        return toBuilder().status(ContentVersionStatus.ARCHIVED).build();
    }

    /** Returns a builder pre-populated from this instance. */
    public Builder toBuilder() {
        return new Builder()
            .versionId(this.versionId)
            .itemId(this.itemId)
            .workspaceId(this.workspaceId)
            .versionNumber(this.versionNumber)
            .contentBlocks(this.contentBlocks)
            .claimReferences(this.claimReferences)
            .disclosureReferences(this.disclosureReferences)
            .generatorMetadata(this.generatorMetadata)
            .status(this.status)
            .approvedBy(this.approvedBy)
            .approvedAt(this.approvedAt)
            .createdAt(this.createdAt)
            .createdBy(this.createdBy);
    }

    /** Returns a fresh builder. */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link ContentVersion}.
     */
    public static final class Builder {
        private String versionId;
        private String itemId;
        private DmWorkspaceId workspaceId;
        private int versionNumber;
        private List<ContentBlock> contentBlocks;
        private List<ClaimReference> claimReferences;
        private List<DisclosureReference> disclosureReferences;
        private GeneratorMetadata generatorMetadata;
        private ContentVersionStatus status;
        private String approvedBy;
        private Instant approvedAt;
        private Instant createdAt;
        private String createdBy;

        private Builder() {}

        public Builder versionId(String v)                     { this.versionId = v;            return this; }
        public Builder itemId(String v)                        { this.itemId = v;               return this; }
        public Builder workspaceId(DmWorkspaceId v)            { this.workspaceId = v;          return this; }
        public Builder versionNumber(int v)                    { this.versionNumber = v;        return this; }
        public Builder contentBlocks(List<ContentBlock> v)     { this.contentBlocks = v;        return this; }
        public Builder claimReferences(List<ClaimReference> v) { this.claimReferences = v;     return this; }
        public Builder disclosureReferences(List<DisclosureReference> v) {
            this.disclosureReferences = v; return this;
        }
        public Builder generatorMetadata(GeneratorMetadata v)  { this.generatorMetadata = v;   return this; }
        public Builder status(ContentVersionStatus v)          { this.status = v;              return this; }
        public Builder approvedBy(String v)                    { this.approvedBy = v;          return this; }
        public Builder approvedAt(Instant v)                   { this.approvedAt = v;          return this; }
        public Builder createdAt(Instant v)                    { this.createdAt = v;           return this; }
        public Builder createdBy(String v)                     { this.createdBy = v;           return this; }

        public ContentVersion build() { return new ContentVersion(this); }
    }
}
