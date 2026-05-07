package com.ghatana.digitalmarketing.application.optimization;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionRecommendation;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link NextBestActionRecommendationService}.
 *
 * @doc.type class
 * @doc.purpose Publishes and manages lifecycle of next-best-action recommendations (P3-004)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class NextBestActionRecommendationServiceImpl implements NextBestActionRecommendationService {

    private final NextBestActionRecommendationRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public NextBestActionRecommendationServiceImpl(
            NextBestActionRecommendationRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<NextBestActionRecommendation> publish(DmOperationContext ctx, PublishRecommendationCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "next-best-action-recommendations", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to publish recommendations"));
                }
                NextBestActionRecommendation rec = NextBestActionRecommendation.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .campaignId(command.campaignId())
                    .actionType(command.actionType())
                    .title(command.title())
                    .description(command.description())
                    .parameters(command.parameters())
                    .confidenceScore(command.confidenceScore())
                    .rationale(command.rationale())
                    .status(NextBestActionStatus.PENDING)
                    .createdAt(Instant.now())
                    .expiresAt(command.expiresAt())
                    .build();
                return repository.save(rec)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "next-best-action-published",
                        Map.of("campaignId", command.campaignId(), "actionType", command.actionType().name())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<NextBestActionRecommendation> approve(DmOperationContext ctx, String recommendationId, String executedBy) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(recommendationId, "recommendationId must not be null");
        Objects.requireNonNull(executedBy, "executedBy must not be null");

        return kernelAdapter.isAuthorized(ctx, "next-best-action-recommendations", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to approve recommendations"));
                }
                return loadAndValidateTenant(ctx, recommendationId)
                    .then(existing -> {
                        NextBestActionRecommendation updated = existing.approve(executedBy);
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "next-best-action-approved", Map.of("executedBy", executedBy)
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<NextBestActionRecommendation> reject(DmOperationContext ctx, String recommendationId, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(recommendationId, "recommendationId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        return kernelAdapter.isAuthorized(ctx, "next-best-action-recommendations", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to reject recommendations"));
                }
                return loadAndValidateTenant(ctx, recommendationId)
                    .then(existing -> {
                        NextBestActionRecommendation updated = existing.reject(reason);
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "next-best-action-rejected", Map.of("reason", reason)
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<NextBestActionRecommendation> expire(DmOperationContext ctx, String recommendationId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(recommendationId, "recommendationId must not be null");

        return loadAndValidateTenant(ctx, recommendationId)
            .then(existing -> {
                NextBestActionRecommendation updated = existing.expire();
                return repository.update(updated);
            });
    }

    @Override
    public Promise<Optional<NextBestActionRecommendation>> findById(DmOperationContext ctx, String recommendationId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.findById(recommendationId)
            .map(opt -> opt.filter(r -> r.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<NextBestActionRecommendation>> listByWorkspace(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "next-best-action-recommendations", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list recommendations"));
                }
                return repository.listByWorkspace(ctx.getTenantId().getValue(), ctx.getWorkspaceId().getValue());
            });
    }

    @Override
    public Promise<List<NextBestActionRecommendation>> listByCampaign(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        return kernelAdapter.isAuthorized(ctx, "next-best-action-recommendations", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list recommendations"));
                }
                return repository.listByCampaign(ctx.getTenantId().getValue(), campaignId);
            });
    }

    @Override
    public Promise<List<NextBestActionRecommendation>> listByStatus(DmOperationContext ctx, NextBestActionStatus status) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "next-best-action-recommendations", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list recommendations"));
                }
                return repository.listByStatus(ctx.getTenantId().getValue(), status);
            });
    }

    private Promise<NextBestActionRecommendation> loadAndValidateTenant(DmOperationContext ctx, String id) {
        return repository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Recommendation not found: " + id));
                }
                NextBestActionRecommendation r = opt.get();
                if (!r.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("Recommendation does not belong to tenant"));
                }
                return Promise.of(r);
            });
    }
}
