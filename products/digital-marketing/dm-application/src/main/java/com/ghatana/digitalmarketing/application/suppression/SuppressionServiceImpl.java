package com.ghatana.digitalmarketing.application.suppression;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.suppression.SuppressionEntry;
import com.ghatana.platform.security.port.HashingPort;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Production suppression and DNC service.
 *
 * @doc.type class
 * @doc.purpose Enforce suppression checks and auditable DNC updates in DMOS
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class SuppressionServiceImpl implements SuppressionService {

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final SuppressionRepository repository;
    private final HashingPort hashingPort;

    public SuppressionServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            SuppressionRepository repository,
            HashingPort hashingPort) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.hashingPort = Objects.requireNonNull(hashingPort, "hashingPort must not be null");
    }

    @Override
    public Promise<SuppressionEntry> addSuppression(DmOperationContext ctx, AddSuppressionCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "suppression/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to add suppression"));
                }
                // Hash the email for PII protection
                return hashingPort.hashContactPoint(command.email())
                    .then(contactPointHash -> repository.findActiveByContactPointHash(ctx.getWorkspaceId(), contactPointHash)
                        .then(existing -> {
                            if (existing.isPresent()) {
                                return Promise.of(existing.get());
                            }
                            Instant now = Instant.now();
                            SuppressionEntry entry = SuppressionEntry.builder()
                                .id(UUID.randomUUID().toString())
                                .workspaceId(ctx.getWorkspaceId())
                                .contactPointHash(contactPointHash)
                                .reason(command.reason())
                                .active(true)
                                .createdAt(now)
                                .updatedAt(now)
                                .createdBy(ctx.getActor().getPrincipalId())
                                .build();

                            return repository.save(entry);
                        })
                        .then(saved -> kernelAdapter.recordAudit(
                                ctx,
                                "suppression/" + saved.getContactPointHash(),
                                "suppression-add",
                                Map.of("reason", saved.getReason())
                            ).map(__ -> saved)
                        ));
            });
    }

    @Override
    public Promise<SuppressionEntry> removeSuppression(DmOperationContext ctx, String email) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(email, "email must not be null");

        return kernelAdapter.isAuthorized(ctx, "suppression/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to remove suppression"));
                }
                // Hash the email for PII protection
                return hashingPort.hashContactPoint(email)
                    .then(contactPointHash -> repository.findActiveByContactPointHash(ctx.getWorkspaceId(), contactPointHash)
                        .then(existing -> {
                            if (existing.isEmpty()) {
                                return Promise.ofException(new NoSuchElementException("Suppression not found for email: " + email));
                            }
                            SuppressionEntry deactivated = existing.get().deactivate();
                            return repository.save(deactivated);
                        })
                        .then(saved -> kernelAdapter.recordAudit(
                                ctx,
                                "suppression/" + saved.getContactPointHash(),
                                "suppression-remove",
                                Map.of("active", Boolean.toString(saved.isActive()))
                            ).map(__ -> saved)
                        ));
            });
    }

    @Override
    public Promise<Boolean> isSuppressed(DmOperationContext ctx, String email) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(email, "email must not be null");

        // Hash the email for PII protection
        return hashingPort.hashContactPoint(email)
            .then(contactPointHash -> repository.findActiveByContactPointHash(ctx.getWorkspaceId(), contactPointHash)
                .map(java.util.Optional::isPresent));
    }
}
