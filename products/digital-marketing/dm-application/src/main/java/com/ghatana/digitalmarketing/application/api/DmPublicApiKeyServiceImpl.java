package com.ghatana.digitalmarketing.application.api;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.api.DmPublicApiKey;
import com.ghatana.digitalmarketing.domain.api.DmPublicApiKeyStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmPublicApiKeyService}.
 *
 * @doc.type class
 * @doc.purpose Manages public API key lifecycle (DMOS-F5-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DmPublicApiKeyServiceImpl implements DmPublicApiKeyService {

    private final DmPublicApiKeyRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmPublicApiKeyServiceImpl(
            DmPublicApiKeyRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmPublicApiKey> issue(DmOperationContext ctx, IssuePublicApiKeyCommand cmd) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(cmd, "cmd must not be null");
        return kernelAdapter.isAuthorized(ctx, "public-api-key", "issue")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to issue API key"));
                DmPublicApiKey key = DmPublicApiKey.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .displayName(cmd.displayName())
                    .keyHash(cmd.keyHash())
                    .scopes(cmd.scopes())
                    .status(DmPublicApiKeyStatus.ACTIVE)
                    .expiresAt(cmd.expiresAt())
                    .createdAt(Instant.now())
                    .build();
                return repository.save(key);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "api-key.issue",
                Map.of("displayName", (Object) saved.getDisplayName()))
                .map(__ -> saved));
    }

    @Override
    public Promise<DmPublicApiKey> revoke(DmOperationContext ctx, String keyId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        return kernelAdapter.isAuthorized(ctx, "public-api-key", "revoke")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to revoke API key"));
                return repository.findById(keyId);
            })
            .then(opt -> {
                DmPublicApiKey existing = opt.orElseThrow(() -> new NoSuchElementException("API key not found: " + keyId));
                if (!existing.getTenantId().equals(ctx.getTenantId().getValue()))
                    return Promise.ofException(new SecurityException("API key does not belong to tenant"));
                return repository.update(existing.revoke());
            })
            .then(updated -> kernelAdapter.recordAudit(ctx, updated.getId(), "api-key.revoke",
                Map.of("status", (Object) updated.getStatus().name()))
                .map(__ -> updated));
    }

    @Override
    public Promise<DmPublicApiKey> recordUsage(DmOperationContext ctx, String keyId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        return repository.findById(keyId)
            .then(opt -> {
                DmPublicApiKey existing = opt.orElseThrow(() -> new NoSuchElementException("API key not found: " + keyId));
                return repository.update(existing.recordUsage());
            });
    }

    @Override
    public Promise<Optional<DmPublicApiKey>> findById(DmOperationContext ctx, String keyId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        return repository.findById(keyId);
    }

    @Override
    public Promise<List<DmPublicApiKey>> listActiveByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.listByTenantAndStatus(ctx.getTenantId().getValue(), DmPublicApiKeyStatus.ACTIVE);
    }
}
