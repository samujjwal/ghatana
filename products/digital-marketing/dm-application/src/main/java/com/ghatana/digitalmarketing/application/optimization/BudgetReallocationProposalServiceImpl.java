package com.ghatana.digitalmarketing.application.optimization;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.optimization.BudgetReallocationProposal;
import com.ghatana.digitalmarketing.domain.optimization.BudgetReallocationStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link BudgetReallocationProposalService}.
 *
 * @doc.type class
 * @doc.purpose Publishes and manages lifecycle of budget reallocation proposals (P3-004)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class BudgetReallocationProposalServiceImpl implements BudgetReallocationProposalService {

    private final BudgetReallocationProposalRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public BudgetReallocationProposalServiceImpl(
            BudgetReallocationProposalRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<BudgetReallocationProposal> publish(DmOperationContext ctx, PublishProposalCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-reallocation-proposals", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to publish proposals"));
                }
                BudgetReallocationProposal proposal = BudgetReallocationProposal.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .budgetRecommendationId(command.budgetRecommendationId())
                    .title(command.title())
                    .description(command.description())
                    .adjustments(command.adjustments())
                    .totalReallocatedAmount(command.totalReallocatedAmount())
                    .rationale(command.rationale())
                    .status(BudgetReallocationStatus.PENDING)
                    .createdAt(Instant.now())
                    .expiresAt(command.expiresAt())
                    .build();
                return repository.save(proposal)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "budget-reallocation-published",
                        Map.of("budgetRecommendationId", command.budgetRecommendationId(), "totalAmount", command.totalReallocatedAmount())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<BudgetReallocationProposal> approve(DmOperationContext ctx, String proposalId, String approvedBy) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(proposalId, "proposalId must not be null");
        Objects.requireNonNull(approvedBy, "approvedBy must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-reallocation-proposals", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to approve proposals"));
                }
                return loadAndValidateTenant(ctx, proposalId)
                    .then(existing -> {
                        BudgetReallocationProposal updated = existing.approve(approvedBy);
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "budget-reallocation-approved", Map.of("approvedBy", approvedBy)
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<BudgetReallocationProposal> reject(DmOperationContext ctx, String proposalId, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(proposalId, "proposalId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-reallocation-proposals", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to reject proposals"));
                }
                return loadAndValidateTenant(ctx, proposalId)
                    .then(existing -> {
                        BudgetReallocationProposal updated = existing.reject(reason);
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "budget-reallocation-rejected", Map.of("reason", reason)
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<BudgetReallocationProposal> execute(DmOperationContext ctx, String proposalId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(proposalId, "proposalId must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-reallocation-proposals", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to execute proposals"));
                }
                return loadAndValidateTenant(ctx, proposalId)
                    .then(existing -> {
                        BudgetReallocationProposal updated = existing.execute();
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "budget-reallocation-executed", Map.of()
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<BudgetReallocationProposal> expire(DmOperationContext ctx, String proposalId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(proposalId, "proposalId must not be null");

        return loadAndValidateTenant(ctx, proposalId)
            .then(existing -> {
                BudgetReallocationProposal updated = existing.expire();
                return repository.update(updated);
            });
    }

    @Override
    public Promise<Optional<BudgetReallocationProposal>> findById(DmOperationContext ctx, String proposalId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.findById(proposalId)
            .map(opt -> opt.filter(r -> r.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<BudgetReallocationProposal>> listByWorkspace(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-reallocation-proposals", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list proposals"));
                }
                return repository.listByWorkspace(ctx.getTenantId().getValue(), ctx.getWorkspaceId().getValue());
            });
    }

    @Override
    public Promise<List<BudgetReallocationProposal>> listByBudgetRecommendation(DmOperationContext ctx, String budgetRecommendationId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(budgetRecommendationId, "budgetRecommendationId must not be null");

        return kernelAdapter.isAuthorized(ctx, "budget-reallocation-proposals", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list proposals"));
                }
                return repository.listByBudgetRecommendation(ctx.getTenantId().getValue(), budgetRecommendationId);
            });
    }

    @Override
    public Promise<List<BudgetReallocationProposal>> listByStatus(DmOperationContext ctx, BudgetReallocationStatus status) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "budget-reallocation-proposals", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list proposals"));
                }
                return repository.listByStatus(ctx.getTenantId().getValue(), status);
            });
    }

    private Promise<BudgetReallocationProposal> loadAndValidateTenant(DmOperationContext ctx, String id) {
        return repository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Proposal not found: " + id));
                }
                BudgetReallocationProposal r = opt.get();
                if (!r.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("Proposal does not belong to tenant"));
                }
                return Promise.of(r);
            });
    }
}
