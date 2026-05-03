package com.ghatana.digitalmarketing.application.narrative;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.narrative.DmNarrativeReview;
import com.ghatana.digitalmarketing.domain.narrative.DmNarrativeReviewStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmNarrativeReviewService}.
 *
 * @doc.type class
 * @doc.purpose Generates and tracks AI narrative review lifecycle (DMOS-F3-006)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmNarrativeReviewServiceImpl implements DmNarrativeReviewService {

    private final DmNarrativeReviewRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmNarrativeReviewServiceImpl(
            DmNarrativeReviewRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmNarrativeReview> generate(DmOperationContext ctx, GenerateNarrativeReviewCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "narrative-reviews", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to generate narrative reviews"));
                }
                DmNarrativeReview review = DmNarrativeReview.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .periodType(command.periodType())
                    .periodStart(command.periodStart())
                    .periodEnd(command.periodEnd())
                    .status(DmNarrativeReviewStatus.PENDING)
                    .generatedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
                return repository.save(review)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "narrative-review-generated",
                        Map.of("periodType", (Object) command.periodType().name())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<DmNarrativeReview> markReady(DmOperationContext ctx, String reviewId, String narrativeText,
                                                  List<String> keyInsights, List<String> recommendations) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "narrative-reviews", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to update narrative reviews"));
                }
                return loadAndValidateTenant(ctx, reviewId)
                    .then(existing -> {
                        DmNarrativeReview ready = DmNarrativeReview.builder()
                            .id(existing.getId()).tenantId(existing.getTenantId()).workspaceId(existing.getWorkspaceId())
                            .periodType(existing.getPeriodType()).periodStart(existing.getPeriodStart()).periodEnd(existing.getPeriodEnd())
                            .generatedAt(existing.getGeneratedAt()).createdAt(existing.getCreatedAt())
                            .narrativeText(narrativeText)
                            .keyInsights(keyInsights != null ? String.join("\n", keyInsights) : null)
                            .recommendations(recommendations != null ? String.join("\n", recommendations) : null)
                            .status(DmNarrativeReviewStatus.READY)
                            .build();
                        return repository.update(ready)
                            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "narrative-review-ready", Map.of()).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<DmNarrativeReview> markFailed(DmOperationContext ctx, String reviewId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "narrative-reviews", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to update narrative reviews"));
                }
                return loadAndValidateTenant(ctx, reviewId)
                    .then(existing -> {
                        DmNarrativeReview failed = DmNarrativeReview.builder()
                            .id(existing.getId()).tenantId(existing.getTenantId()).workspaceId(existing.getWorkspaceId())
                            .periodType(existing.getPeriodType()).periodStart(existing.getPeriodStart()).periodEnd(existing.getPeriodEnd())
                            .narrativeText(existing.getNarrativeText()).keyInsights(existing.getKeyInsights())
                            .recommendations(existing.getRecommendations()).generatedAt(existing.getGeneratedAt()).createdAt(existing.getCreatedAt())
                            .status(DmNarrativeReviewStatus.FAILED)
                            .build();
                        return repository.update(failed)
                            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "narrative-review-failed", Map.of()).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Optional<DmNarrativeReview>> findById(DmOperationContext ctx, String reviewId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.findById(reviewId)
            .map(opt -> opt.filter(r -> r.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmNarrativeReview>> listByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "narrative-reviews", "read")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(new SecurityException("Actor not authorised to list narrative reviews"));
                return repository.listByTenant(ctx.getTenantId().getValue());
            });
    }

    private Promise<DmNarrativeReview> loadAndValidateTenant(DmOperationContext ctx, String id) {
        return repository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) return Promise.ofException(new NoSuchElementException("NarrativeReview not found: " + id));
                DmNarrativeReview r = opt.get();
                if (!r.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("NarrativeReview does not belong to tenant"));
                }
                return Promise.of(r);
            });
    }
}
