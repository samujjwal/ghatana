package com.ghatana.digitalmarketing.application.playbook;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.playbook.DmPlaybookVersion;
import com.ghatana.digitalmarketing.domain.playbook.DmPlaybookVersionStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmPlaybookVersionService}.
 *
 * @doc.type class
 * @doc.purpose Creates and manages playbook version lifecycle (DMOS-F3-004)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmPlaybookVersionServiceImpl implements DmPlaybookVersionService {

    private final DmPlaybookVersionRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmPlaybookVersionServiceImpl(
            DmPlaybookVersionRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmPlaybookVersion> create(DmOperationContext ctx, CreatePlaybookVersionCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "playbook-versions", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to create playbook versions"));
                }
                DmPlaybookVersion version = DmPlaybookVersion.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .playbookId(command.playbookId())
                    .versionNumber(command.versionNumber())
                    .contentJson(command.contentJson())
                    .status(DmPlaybookVersionStatus.DRAFT)
                    .createdAt(Instant.now())
                    .build();
                return repository.save(version)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "playbook-version-created",
                        Map.of("playbookId", (Object) command.playbookId(), "version", (Object) command.versionNumber())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<DmPlaybookVersion> promote(DmOperationContext ctx, String versionId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "playbook-versions", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to promote playbook versions"));
                }
                return loadAndValidateTenant(ctx, versionId)
                    .then(existing -> {
                        DmPlaybookVersion promoted = existing.promote(ctx.getActor().getPrincipalId());
                        return repository.update(promoted)
                            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "playbook-version-promoted", Map.of()).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<DmPlaybookVersion> archive(DmOperationContext ctx, String versionId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "playbook-versions", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to archive playbook versions"));
                }
                return loadAndValidateTenant(ctx, versionId)
                    .then(existing -> {
                        DmPlaybookVersion archived = existing.archive();
                        return repository.update(archived)
                            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "playbook-version-archived", Map.of()).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Optional<DmPlaybookVersion>> findById(DmOperationContext ctx, String versionId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.findById(versionId)
            .map(opt -> opt.filter(v -> v.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmPlaybookVersion>> listByPlaybook(DmOperationContext ctx, String playbookId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "playbook-versions", "read")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(new SecurityException("Actor not authorised to list playbook versions"));
                return repository.listByPlaybook(ctx.getTenantId().getValue(), playbookId);
            });
    }

    private Promise<DmPlaybookVersion> loadAndValidateTenant(DmOperationContext ctx, String id) {
        return repository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) return Promise.ofException(new NoSuchElementException("PlaybookVersion not found: " + id));
                DmPlaybookVersion v = opt.get();
                if (!v.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("PlaybookVersion does not belong to tenant"));
                }
                return Promise.of(v);
            });
    }
}
