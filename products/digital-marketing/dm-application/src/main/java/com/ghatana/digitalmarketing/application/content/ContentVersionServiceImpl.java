package com.ghatana.digitalmarketing.application.content;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.content.ContentAssetVersion;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link ContentVersionService}.
 *
 * @doc.type class
 * @doc.purpose DMOS immutable content versioning implementation
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class ContentVersionServiceImpl implements ContentVersionService {

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final ContentVersionRepository repository;

    public ContentVersionServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            ContentVersionRepository repository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<ContentAssetVersion> createInitialVersion(
            DmOperationContext ctx,
            String assetId,
            String contentBody,
            String changeSummary) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return createVersion(ctx, assetId, contentBody, changeSummary, 1, true);
    }

    @Override
    public Promise<ContentAssetVersion> createNextVersion(
            DmOperationContext ctx,
            String assetId,
            String contentBody,
            String changeSummary) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(assetId, "assetId must not be null");
        Objects.requireNonNull(contentBody, "contentBody must not be null");

        return repository.findLatestVersion(ctx.getWorkspaceId(), assetId)
            .then(latest -> {
                if (latest.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Cannot create next version before initial version"));
                }
                int next = latest.get().getVersionNumber() + 1;
                return createVersion(ctx, assetId, contentBody, changeSummary, next, false);
            });
    }

    @Override
    public Promise<List<ContentAssetVersion>> listVersions(DmOperationContext ctx, String assetId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(assetId, "assetId must not be null");

        return kernelAdapter.isAuthorized(ctx, "content/" + assetId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read content versions"));
                }
                return repository.listVersions(ctx.getWorkspaceId(), assetId);
            });
    }

    private Promise<ContentAssetVersion> createVersion(
            DmOperationContext ctx,
            String assetId,
            String contentBody,
            String changeSummary,
            int versionNumber,
            boolean initial) {
        if (assetId == null || assetId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("assetId must not be blank"));
        }
        if (contentBody == null || contentBody.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("contentBody must not be blank"));
        }

        return kernelAdapter.isAuthorized(ctx, "content/" + assetId, "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to version content"));
                }

                ContentAssetVersion version = ContentAssetVersion.builder()
                    .versionId(UUID.randomUUID().toString())
                    .assetId(assetId)
                    .workspaceId(ctx.getWorkspaceId())
                    .versionNumber(versionNumber)
                    .contentBody(contentBody)
                    .changeSummary(changeSummary)
                    .createdAt(Instant.now())
                    .createdBy(ctx.getActor().getPrincipalId())
                    .build();

                String action = initial ? "create-initial-version" : "create-next-version";
                return repository.save(version)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx,
                        "content/" + assetId,
                        action,
                        Map.of("version", Integer.toString(saved.getVersionNumber()))
                    ).map(__ -> saved));
            });
    }
}
