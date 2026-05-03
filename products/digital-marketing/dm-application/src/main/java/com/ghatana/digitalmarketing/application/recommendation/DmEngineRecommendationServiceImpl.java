package com.ghatana.digitalmarketing.application.recommendation;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.recommendation.DmEngineRecommendation;
import com.ghatana.digitalmarketing.domain.recommendation.DmEngineRecommendationStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmEngineRecommendationService}.
 *
 * @doc.type class
 * @doc.purpose Publishes and manages lifecycle of engine recommendations (DMOS-F3-001)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmEngineRecommendationServiceImpl implements DmEngineRecommendationService {

    private final DmEngineRecommendationRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmEngineRecommendationServiceImpl(
            DmEngineRecommendationRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmEngineRecommendation> publish(DmOperationContext ctx, PublishRecommendationCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "engine-recommendations", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to publish recommendations"));
                }
                DmEngineRecommendation rec = DmEngineRecommendation.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .recommendationType(command.recommendationType())
                    .rationale(command.rationale())
                    .confidenceScore(command.confidenceScore())
                    .supportingMetricKeys(command.supportingMetricKeys() != null ? command.supportingMetricKeys() : List.of())
                    .suggestedActions(command.suggestedActions() != null ? command.suggestedActions() : List.of())
                    .status(DmEngineRecommendationStatus.ACTIVE)
                    .createdAt(Instant.now())
                    .expiresAt(command.expiresAt())
                    .build();
                return repository.save(rec)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "recommendation-published",
                        Map.of("type", (Object) command.recommendationType())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<DmEngineRecommendation> accept(DmOperationContext ctx, String recommendationId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(recommendationId, "recommendationId must not be null");

        return kernelAdapter.isAuthorized(ctx, "engine-recommendations", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to accept recommendations"));
                }
                return loadAndValidateTenant(ctx, recommendationId)
                    .then(existing -> {
                        DmEngineRecommendation updated = DmEngineRecommendation.builder()
                            .id(existing.getId()).tenantId(existing.getTenantId()).workspaceId(existing.getWorkspaceId())
                            .recommendationType(existing.getRecommendationType()).rationale(existing.getRationale())
                            .confidenceScore(existing.getConfidenceScore()).supportingMetricKeys(existing.getSupportingMetricKeys())
                            .suggestedActions(existing.getSuggestedActions()).createdAt(existing.getCreatedAt()).expiresAt(existing.getExpiresAt())
                            .status(DmEngineRecommendationStatus.ACCEPTED)
                            .build();
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "recommendation-accepted", Map.of()
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<DmEngineRecommendation> reject(DmOperationContext ctx, String recommendationId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(recommendationId, "recommendationId must not be null");

        return kernelAdapter.isAuthorized(ctx, "engine-recommendations", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to reject recommendations"));
                }
                return loadAndValidateTenant(ctx, recommendationId)
                    .then(existing -> {
                        DmEngineRecommendation updated = DmEngineRecommendation.builder()
                            .id(existing.getId()).tenantId(existing.getTenantId()).workspaceId(existing.getWorkspaceId())
                            .recommendationType(existing.getRecommendationType()).rationale(existing.getRationale())
                            .confidenceScore(existing.getConfidenceScore()).supportingMetricKeys(existing.getSupportingMetricKeys())
                            .suggestedActions(existing.getSuggestedActions()).createdAt(existing.getCreatedAt()).expiresAt(existing.getExpiresAt())
                            .status(DmEngineRecommendationStatus.REJECTED)
                            .build();
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "recommendation-rejected", Map.of()
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Optional<DmEngineRecommendation>> findById(DmOperationContext ctx, String recommendationId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.findById(recommendationId)
            .map(opt -> opt.filter(r -> r.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmEngineRecommendation>> listByTenant(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "engine-recommendations", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list recommendations"));
                }
                return repository.listByTenant(ctx.getTenantId().getValue());
            });
    }

    @Override
    public Promise<List<DmEngineRecommendation>> listByStatus(DmOperationContext ctx, DmEngineRecommendationStatus status) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "engine-recommendations", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list recommendations"));
                }
                return repository.listByStatus(ctx.getTenantId().getValue(), status);
            });
    }

    private Promise<DmEngineRecommendation> loadAndValidateTenant(DmOperationContext ctx, String id) {
        return repository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Recommendation not found: " + id));
                }
                DmEngineRecommendation r = opt.get();
                if (!r.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("Recommendation does not belong to tenant"));
                }
                return Promise.of(r);
            });
    }
}
