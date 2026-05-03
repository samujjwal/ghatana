package com.ghatana.digitalmarketing.application.email;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.email.DmEmailFollowUp;
import com.ghatana.digitalmarketing.domain.email.DmEmailFollowUpStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmEmailFollowUpService}.
 *
 * @doc.type class
 * @doc.purpose Executes tenant-safe email follow-up scheduling and lifecycle (DMOS-F2-012)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmEmailFollowUpServiceImpl implements DmEmailFollowUpService {

    private final DmEmailFollowUpRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmEmailFollowUpServiceImpl(
            DmEmailFollowUpRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmEmailFollowUp> schedule(DmOperationContext ctx, ScheduleEmailFollowUpCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "email-followups", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to schedule email follow-ups"));
                }
                DmEmailFollowUp followUp = DmEmailFollowUp.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .connectorId(command.connectorId())
                    .recipientEmails(command.recipientEmails())
                    .subject(command.subject())
                    .bodyHtml(command.bodyHtml())
                    .status(DmEmailFollowUpStatus.PENDING)
                    .sentCount(0)
                    .failedCount(0)
                    .scheduledAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
                return repository.save(followUp)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "email-followup-scheduled",
                        Map.of("connectorId", saved.getConnectorId(), "recipients", (Object) String.valueOf(saved.getRecipientEmails().size()))
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<DmEmailFollowUp> markSent(DmOperationContext ctx, String followUpId, int sentCount, int failedCount) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(followUpId, "followUpId must not be null");

        return kernelAdapter.isAuthorized(ctx, "email-followups", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to update email follow-ups"));
                }
                return loadAndValidateTenant(ctx, followUpId)
                    .then(existing -> {
                        DmEmailFollowUp updated = existing.markSent(sentCount, failedCount);
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "email-followup-sent",
                                Map.of("sent", (Object) String.valueOf(sentCount), "failed", (Object) String.valueOf(failedCount))
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<DmEmailFollowUp> markFailed(DmOperationContext ctx, String followUpId, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(followUpId, "followUpId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        return kernelAdapter.isAuthorized(ctx, "email-followups", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to update email follow-ups"));
                }
                return loadAndValidateTenant(ctx, followUpId)
                    .then(existing -> {
                        DmEmailFollowUp updated = existing.markFailed(reason);
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "email-followup-failed",
                                Map.of("reason", (Object) reason)
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<DmEmailFollowUp> cancel(DmOperationContext ctx, String followUpId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(followUpId, "followUpId must not be null");

        return kernelAdapter.isAuthorized(ctx, "email-followups", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to cancel email follow-ups"));
                }
                return loadAndValidateTenant(ctx, followUpId)
                    .then(existing -> {
                        DmEmailFollowUp updated = existing.cancel();
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "email-followup-cancelled", Map.<String, Object>of()
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Optional<DmEmailFollowUp>> findById(DmOperationContext ctx, String followUpId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(followUpId, "followUpId must not be null");

        return repository.findById(followUpId)
            .map(opt -> opt.filter(e -> e.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmEmailFollowUp>> listByStatus(DmOperationContext ctx, DmEmailFollowUpStatus status, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(status, "status must not be null");

        return kernelAdapter.isAuthorized(ctx, "email-followups", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list email follow-ups"));
                }
                return repository.listByStatus(ctx.getTenantId().getValue(), status, limit);
            });
    }

    private Promise<DmEmailFollowUp> loadAndValidateTenant(DmOperationContext ctx, String followUpId) {
        return repository.findById(followUpId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Email follow-up not found: " + followUpId));
                }
                DmEmailFollowUp e = opt.get();
                if (!e.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("Email follow-up does not belong to tenant"));
                }
                return Promise.of(e);
            });
    }
}
