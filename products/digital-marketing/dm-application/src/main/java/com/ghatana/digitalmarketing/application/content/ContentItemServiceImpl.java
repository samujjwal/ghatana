package com.ghatana.digitalmarketing.application.content;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.content.ContentItem;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import com.ghatana.digitalmarketing.domain.content.ContentVersionStatus;
import com.ghatana.digitalmarketing.domain.content.GeneratorMetadata;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link ContentItemService}.
 *
 * @doc.type class
 * @doc.purpose DMOS enriched content item and version lifecycle service implementation
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class ContentItemServiceImpl implements ContentItemService {

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final ContentItemRepository itemRepository;
    private final ContentItemVersionRepository versionRepository;

    public ContentItemServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            ContentItemRepository itemRepository,
            ContentItemVersionRepository versionRepository) {
        this.kernelAdapter   = Objects.requireNonNull(kernelAdapter,      "kernelAdapter must not be null");
        this.itemRepository  = Objects.requireNonNull(itemRepository,     "itemRepository must not be null");
        this.versionRepository = Objects.requireNonNull(versionRepository,"versionRepository must not be null");
    }

    @Override
    public Promise<ContentItem> createItem(DmOperationContext ctx, CreateContentItemCommand command) {
        Objects.requireNonNull(ctx,     "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "content", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to create content items"));
                }
                ContentItem item = ContentItem.builder()
                    .itemId(UUID.randomUUID().toString())
                    .workspaceId(ctx.getWorkspaceId())
                    .title(command.title())
                    .itemType(command.itemType())
                    .description(command.description() != null ? command.description() : "")
                    .createdAt(Instant.now())
                    .createdBy(ctx.getActor().getPrincipalId())
                    .build();
                return itemRepository.save(item)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx,
                        "content-item/" + saved.getItemId(),
                        "content-item-created",
                        Map.of("type", saved.getItemType().name())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<ContentVersion> createVersion(DmOperationContext ctx, CreateContentVersionCommand command) {
        Objects.requireNonNull(ctx,     "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "content/" + command.itemId(), "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to create content versions"));
                }
                return versionRepository.findLatestByItemId(ctx.getWorkspaceId(), command.itemId())
                    .then(latest -> {
                        int nextVersion = latest.map(v -> v.getVersionNumber() + 1).orElse(1);
                        GeneratorMetadata metadata = new GeneratorMetadata(
                            command.modelVersion(),
                            command.promptVersion(),
                            command.sourceStrategy(),
                            Instant.now()
                        );
                        ContentVersion version = ContentVersion.builder()
                            .versionId(UUID.randomUUID().toString())
                            .itemId(command.itemId())
                            .workspaceId(ctx.getWorkspaceId())
                            .versionNumber(nextVersion)
                            .contentBlocks(command.contentBlocks())
                            .claimReferences(command.claimReferences())
                            .disclosureReferences(command.disclosureReferences())
                            .generatorMetadata(metadata)
                            .status(ContentVersionStatus.DRAFT)
                            .createdAt(Instant.now())
                            .createdBy(ctx.getActor().getPrincipalId())
                            .build();
                        return versionRepository.save(version)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx,
                                "content-version/" + saved.getVersionId(),
                                "content-version-created",
                                Map.of(
                                    "itemId", saved.getItemId(),
                                    "versionNumber", Integer.toString(saved.getVersionNumber())
                                )
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<ContentVersion> getLatestApproved(DmOperationContext ctx, String itemId) {
        Objects.requireNonNull(ctx,    "ctx must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        if (itemId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("itemId must not be blank"));
        }

        return kernelAdapter.isAuthorized(ctx, "content/" + itemId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read content versions"));
                }
                return versionRepository.findLatestApproved(ctx.getWorkspaceId(), itemId)
                    .then(opt -> opt.isPresent()
                        ? Promise.of(opt.get())
                        : Promise.ofException(new NoSuchElementException("No approved version found for item: " + itemId)));
            });
    }

    @Override
    public Promise<ContentVersion> approveVersion(DmOperationContext ctx, String versionId) {
        Objects.requireNonNull(ctx,       "ctx must not be null");
        Objects.requireNonNull(versionId, "versionId must not be null");
        if (versionId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("versionId must not be blank"));
        }

        return kernelAdapter.isAuthorized(ctx, "content-version/" + versionId, "approve")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to approve content versions"));
                }
                return versionRepository.findById(ctx.getWorkspaceId(), versionId)
                    .then(opt -> {
                        ContentVersion existing = opt.orElseThrow(
                            () -> new NoSuchElementException("Content version not found: " + versionId));
                        ContentVersion toApprove = existing.getStatus() == ContentVersionStatus.DRAFT
                            ? existing.submitForReview()
                            : existing;
                        ContentVersion approved = toApprove.approve(
                            ctx.getActor().getPrincipalId(), Instant.now());
                        return versionRepository.save(approved)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx,
                                "content-version/" + saved.getVersionId(),
                                "content-version-approved",
                                Map.of("itemId", saved.getItemId())
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<List<ContentVersion>> getVersionHistory(DmOperationContext ctx, String itemId) {
        Objects.requireNonNull(ctx,    "ctx must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        if (itemId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("itemId must not be blank"));
        }

        return kernelAdapter.isAuthorized(ctx, "content/" + itemId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read version history"));
                }
                return versionRepository.findByItemId(ctx.getWorkspaceId(), itemId);
            });
    }
}
