package com.ghatana.digitalmarketing.application.preflight;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightCheckResult;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightChecklist;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmPreflightChecklistService}.
 *
 * @doc.type class
 * @doc.purpose Evaluates campaign preflight safety and persists the result (DMOS-F2-013)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmPreflightChecklistServiceImpl implements DmPreflightChecklistService {

    private final DmPreflightChecklistRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmPreflightChecklistServiceImpl(
            DmPreflightChecklistRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmPreflightChecklist> evaluate(DmOperationContext ctx, EvaluatePreflightCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "preflight-checklists", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to evaluate preflight checklists"));
                }
                DmPreflightStatus status = deriveStatus(command.items());
                DmPreflightChecklist checklist = DmPreflightChecklist.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .campaignId(command.campaignId())
                    .items(command.items())
                    .status(status)
                    .evaluatedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
                return repository.save(checklist)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "preflight-evaluated",
                        Map.of("campaignId", command.campaignId(), "status", status.name())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<Optional<DmPreflightChecklist>> findById(DmOperationContext ctx, String checklistId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(checklistId, "checklistId must not be null");

        return repository.findById(checklistId)
            .map(opt -> opt.filter(c -> c.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmPreflightChecklist>> listByCampaign(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        return kernelAdapter.isAuthorized(ctx, "preflight-checklists", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list preflight checklists"));
                }
                return repository.listByCampaign(ctx.getTenantId().getValue(), campaignId);
            });
    }

    @Override
    public Promise<Optional<DmPreflightChecklist>> findLatestByCampaign(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        return repository.findLatestByCampaign(ctx.getTenantId().getValue(), campaignId)
            .map(opt -> opt.filter(c -> c.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    private DmPreflightStatus deriveStatus(List<DmPreflightChecklist.DmPreflightCheckItem> items) {
        boolean anyRequiredFailed = items.stream()
            .filter(DmPreflightChecklist.DmPreflightCheckItem::required)
            .anyMatch(i -> i.result() == DmPreflightCheckResult.FAILED);
        if (anyRequiredFailed) {
            return DmPreflightStatus.BLOCKED;
        }
        boolean anyWarning = items.stream().anyMatch(i -> i.result() == DmPreflightCheckResult.WARNING);
        return anyWarning ? DmPreflightStatus.WARNING : DmPreflightStatus.PASSED;
    }
}
