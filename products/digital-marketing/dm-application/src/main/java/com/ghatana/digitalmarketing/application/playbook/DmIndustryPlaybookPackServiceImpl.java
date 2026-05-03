package com.ghatana.digitalmarketing.application.playbook;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.playbook.DmIndustryPlaybookPack;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmIndustryPlaybookPackService}.
 *
 * @doc.type class
 * @doc.purpose Manages creation and publication of industry playbook packs (DMOS-F4-004)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DmIndustryPlaybookPackServiceImpl implements DmIndustryPlaybookPackService {

    private final DmIndustryPlaybookPackRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmIndustryPlaybookPackServiceImpl(
            DmIndustryPlaybookPackRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmIndustryPlaybookPack> create(DmOperationContext ctx, CreateIndustryPlaybookPackCommand cmd) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(cmd, "cmd must not be null");
        return kernelAdapter.isAuthorized(ctx, "industry-playbook-pack", "create")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to create industry playbook pack"));
                DmIndustryPlaybookPack pack = DmIndustryPlaybookPack.builder()
                    .id(UUID.randomUUID().toString())
                    .name(cmd.name())
                    .industry(cmd.industry())
                    .description(cmd.description())
                    .version(cmd.version())
                    .playbookIds(cmd.playbookIds())
                    .published(false)
                    .createdAt(Instant.now())
                    .build();
                return repository.save(pack);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "playbook-pack.create",
                Map.of("industry", (Object) saved.getIndustry()))
                .map(__ -> saved));
    }

    @Override
    public Promise<DmIndustryPlaybookPack> publish(DmOperationContext ctx, String packId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(packId, "packId must not be null");
        return kernelAdapter.isAuthorized(ctx, "industry-playbook-pack", "publish")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to publish industry playbook pack"));
                return repository.findById(packId);
            })
            .then(opt -> {
                DmIndustryPlaybookPack existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Playbook pack not found: " + packId));
                DmIndustryPlaybookPack published = DmIndustryPlaybookPack.builder()
                    .id(existing.getId())
                    .name(existing.getName())
                    .industry(existing.getIndustry())
                    .description(existing.getDescription())
                    .version(existing.getVersion())
                    .playbookIds(existing.getPlaybookIds())
                    .published(true)
                    .publishedAt(Instant.now())
                    .createdAt(existing.getCreatedAt())
                    .build();
                return repository.update(published);
            })
            .then(updated -> kernelAdapter.recordAudit(ctx, updated.getId(), "playbook-pack.publish",
                Map.of("industry", (Object) updated.getIndustry()))
                .map(__ -> updated));
    }

    @Override
    public Promise<Optional<DmIndustryPlaybookPack>> findById(DmOperationContext ctx, String packId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(packId, "packId must not be null");
        return repository.findById(packId);
    }

    @Override
    public Promise<List<DmIndustryPlaybookPack>> listPublished(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.listPublished();
    }

    @Override
    public Promise<List<DmIndustryPlaybookPack>> listByIndustry(DmOperationContext ctx, String industry) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(industry, "industry must not be null");
        return repository.listByIndustry(industry);
    }
}
