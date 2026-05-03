package com.ghatana.digitalmarketing.application.security;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.security.DmEnterpriseSecurityConfig;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmEnterpriseSecurityConfigService}.
 *
 * @doc.type class
 * @doc.purpose Manages enterprise security configuration for tenants (DMOS-F4-005)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DmEnterpriseSecurityConfigServiceImpl implements DmEnterpriseSecurityConfigService {

    private final DmEnterpriseSecurityConfigRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmEnterpriseSecurityConfigServiceImpl(
            DmEnterpriseSecurityConfigRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmEnterpriseSecurityConfig> provision(DmOperationContext ctx, ProvisionSecurityConfigCommand cmd) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(cmd, "cmd must not be null");
        return kernelAdapter.isAuthorized(ctx, "enterprise-security-config", "create")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to provision enterprise security config"));
                Instant now = Instant.now();
                DmEnterpriseSecurityConfig config = DmEnterpriseSecurityConfig.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .mfaRequired(cmd.mfaRequired())
                    .ipAllowlistEnabled(cmd.ipAllowlistEnabled())
                    .allowedIpCidrs(cmd.allowedIpCidrs())
                    .auditLogEnabled(cmd.auditLogEnabled())
                    .sessionTimeoutMinutes(cmd.sessionTimeoutMinutes())
                    .ssoProvider(cmd.ssoProvider())
                    .ssoMetadataUrl(cmd.ssoMetadataUrl())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
                return repository.save(config);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "security-config.provision",
                Map.of("tenantId", (Object) saved.getTenantId()))
                .map(__ -> saved));
    }

    @Override
    public Promise<DmEnterpriseSecurityConfig> update(DmOperationContext ctx, String configId, UpdateSecurityConfigCommand cmd) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(configId, "configId must not be null");
        Objects.requireNonNull(cmd, "cmd must not be null");
        return kernelAdapter.isAuthorized(ctx, "enterprise-security-config", "update")
            .then(allowed -> {
                if (!allowed) return Promise.ofException(new SecurityException("Not authorized to update enterprise security config"));
                return repository.findById(configId);
            })
            .then(opt -> {
                DmEnterpriseSecurityConfig existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Security config not found: " + configId));
                if (!existing.getTenantId().equals(ctx.getTenantId().getValue()))
                    return Promise.ofException(new SecurityException("Security config does not belong to tenant"));
                DmEnterpriseSecurityConfig updated = DmEnterpriseSecurityConfig.builder()
                    .id(existing.getId())
                    .tenantId(existing.getTenantId())
                    .mfaRequired(cmd.mfaRequired())
                    .ipAllowlistEnabled(cmd.ipAllowlistEnabled())
                    .allowedIpCidrs(cmd.allowedIpCidrs())
                    .auditLogEnabled(cmd.auditLogEnabled())
                    .sessionTimeoutMinutes(cmd.sessionTimeoutMinutes())
                    .ssoProvider(cmd.ssoProvider())
                    .ssoMetadataUrl(cmd.ssoMetadataUrl())
                    .createdAt(existing.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();
                return repository.update(updated);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "security-config.update",
                Map.of("tenantId", (Object) saved.getTenantId()))
                .map(__ -> saved));
    }

    @Override
    public Promise<Optional<DmEnterpriseSecurityConfig>> findByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.findByTenantId(ctx.getTenantId().getValue());
    }
}
