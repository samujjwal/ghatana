package com.ghatana.digitalmarketing.application.content;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.content.ClaimReference;
import com.ghatana.digitalmarketing.domain.content.ContentBlock;
import com.ghatana.digitalmarketing.domain.content.ContentItem;
import com.ghatana.digitalmarketing.domain.content.ContentItemType;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import com.ghatana.digitalmarketing.domain.content.DisclosureReference;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;

/**
 * Application service for the DMOS enriched content version lifecycle (F1-017).
 *
 * <p>Manages creation of content items and their immutable, approvable versions.
 * Campaign launches must only reference versions in {@code APPROVED} state.</p>
 *
 * @doc.type interface
 * @doc.purpose DMOS content item and version lifecycle application service
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface ContentItemService {

    /**
     * Command to create a new content item.
     *
     * @param title       display title for the item; must not be blank
     * @param itemType    the type of content; must not be null
     * @param description optional description; may be null or empty
     */
    record CreateContentItemCommand(
            String title,
            ContentItemType itemType,
            String description) {

        public CreateContentItemCommand {
            Objects.requireNonNull(title,    "title must not be null");
            Objects.requireNonNull(itemType, "itemType must not be null");
            if (title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        }
    }

    /**
     * Command to create a new draft version of a content item.
     *
     * @param itemId               the parent content item ID; must not be blank
     * @param contentBlocks        ordered content blocks; must not be null or empty
     * @param claimReferences      claim references for compliance; must not be null
     * @param disclosureReferences disclosure references for compliance; must not be null
     * @param modelVersion         AI model version used; must not be blank
     * @param promptVersion        prompt version used; must not be blank
     * @param sourceStrategy       source strategy description; must not be blank
     */
    record CreateContentVersionCommand(
            String itemId,
            List<ContentBlock> contentBlocks,
            List<ClaimReference> claimReferences,
            List<DisclosureReference> disclosureReferences,
            String modelVersion,
            String promptVersion,
            String sourceStrategy) {

        public CreateContentVersionCommand {
            Objects.requireNonNull(itemId,               "itemId must not be null");
            Objects.requireNonNull(contentBlocks,        "contentBlocks must not be null");
            Objects.requireNonNull(claimReferences,      "claimReferences must not be null");
            Objects.requireNonNull(disclosureReferences, "disclosureReferences must not be null");
            Objects.requireNonNull(modelVersion,         "modelVersion must not be null");
            Objects.requireNonNull(promptVersion,        "promptVersion must not be null");
            Objects.requireNonNull(sourceStrategy,       "sourceStrategy must not be null");
            if (itemId.isBlank())        throw new IllegalArgumentException("itemId must not be blank");
            if (modelVersion.isBlank())  throw new IllegalArgumentException("modelVersion must not be blank");
            if (promptVersion.isBlank()) throw new IllegalArgumentException("promptVersion must not be blank");
            if (sourceStrategy.isBlank()) throw new IllegalArgumentException("sourceStrategy must not be blank");
            if (contentBlocks.isEmpty()) throw new IllegalArgumentException("contentBlocks must not be empty");
        }
    }

    /**
     * Creates a new content item in the given workspace.
     *
     * @param ctx     the operation context; must not be null
     * @param command the creation command; must not be null
     * @return the persisted {@link ContentItem}
     */
    Promise<ContentItem> createItem(DmOperationContext ctx, CreateContentItemCommand command);

    /**
     * Creates a new draft {@link ContentVersion} for an existing content item.
     *
     * <p>Each call produces a new version number (monotonically increasing). The version
     * starts in {@code DRAFT} status.</p>
     *
     * @param ctx     the operation context; must not be null
     * @param command the version-creation command; must not be null
     * @return the persisted draft {@link ContentVersion}
     */
    Promise<ContentVersion> createVersion(DmOperationContext ctx, CreateContentVersionCommand command);

    /**
     * Returns the latest {@code APPROVED} version for a content item.
     *
     * @param ctx    the operation context; must not be null
     * @param itemId the content item ID; must not be null or blank
     * @return the latest approved {@link ContentVersion}
     * @throws java.util.NoSuchElementException if no approved version exists
     */
    Promise<ContentVersion> getLatestApproved(DmOperationContext ctx, String itemId);

    /**
     * Approves a content version identified by {@code versionId}, transitioning it through
     * {@code DRAFT → PENDING_REVIEW → APPROVED} if it is in DRAFT state, or directly
     * {@code PENDING_REVIEW → APPROVED} if already submitted.
     *
     * @param ctx       the operation context; must not be null
     * @param versionId the version to approve; must not be null or blank
     * @return the updated {@link ContentVersion} in {@code APPROVED} state
     */
    Promise<ContentVersion> approveVersion(DmOperationContext ctx, String versionId);

    /**
     * Returns all versions for a content item, ordered by version number ascending.
     *
     * @param ctx    the operation context; must not be null
     * @param itemId the content item ID; must not be null or blank
     * @return all {@link ContentVersion} records for the item
     */
    Promise<List<ContentVersion>> getVersionHistory(DmOperationContext ctx, String itemId);
}
