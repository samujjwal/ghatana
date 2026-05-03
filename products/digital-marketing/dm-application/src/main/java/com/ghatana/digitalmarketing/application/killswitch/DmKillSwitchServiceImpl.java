package com.ghatana.digitalmarketing.application.killswitch;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.killswitch.DmKillSwitch;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmKillSwitchService}.
 *
 * @doc.type class
 * @doc.purpose Manages emergency pause activation for campaigns (DMOS-F2-015)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmKillSwitchServiceImpl implements DmKillSwitchService {

    private final DmKillSwitchRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmKillSwitchServiceImpl(
            DmKillSwitchRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmKillSwitch> activate(DmOperationContext ctx, ActivateKillSwitchCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "kill-switches", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to activate kill switches"));
                }
                DmKillSwitch ks = DmKillSwitch.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .scope(command.scope())
                    .scopeId(command.scopeId())
                    .active(true)
                    .reason(command.reason())
                    .activatedBy(ctx.getActor().getPrincipalId())
                    .activatedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
                return repository.save(ks)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "kill-switch-activated",
                        Map.of("scope", command.scope(), "scopeId", command.scopeId(), "reason", command.reason())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<DmKillSwitch> deactivate(DmOperationContext ctx, String killSwitchId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(killSwitchId, "killSwitchId must not be null");

        return kernelAdapter.isAuthorized(ctx, "kill-switches", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to deactivate kill switches"));
                }
                return loadAndValidateTenant(ctx, killSwitchId)
                    .then(existing -> {
                        DmKillSwitch updated = existing.deactivate();
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "kill-switch-deactivated", Map.of()
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Optional<DmKillSwitch>> findById(DmOperationContext ctx, String killSwitchId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(killSwitchId, "killSwitchId must not be null");

        return repository.findById(killSwitchId)
            .map(opt -> opt.filter(ks -> ks.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmKillSwitch>> listActive(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "kill-switches", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list kill switches"));
                }
                return repository.listActive(ctx.getTenantId().getValue());
            });
    }

    @Override
    public Promise<Optional<DmKillSwitch>> findActiveByScope(DmOperationContext ctx, String scope, String scopeId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(scopeId, "scopeId must not be null");

        return repository.findActiveByScope(ctx.getTenantId().getValue(), scope, scopeId);
    }

    private Promise<DmKillSwitch> loadAndValidateTenant(DmOperationContext ctx, String killSwitchId) {
        return repository.findById(killSwitchId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Kill switch not found: " + killSwitchId));
                }
                DmKillSwitch ks = opt.get();
                if (!ks.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("Kill switch does not belong to tenant"));
                }
                return Promise.of(ks);
            });
    }
}
