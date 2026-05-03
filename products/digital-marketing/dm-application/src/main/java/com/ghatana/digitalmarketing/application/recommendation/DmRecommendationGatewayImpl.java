package com.ghatana.digitalmarketing.application.recommendation;

import com.ghatana.digitalmarketing.application.command.DmCommandRepository;
import com.ghatana.digitalmarketing.application.command.DmCommandService;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import com.ghatana.digitalmarketing.domain.recommendation.DmAgentRecommendation;
import com.ghatana.digitalmarketing.domain.recommendation.DmRecommendationStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmRecommendationGateway}.
 *
 * <p>Accepts agent recommendations, evaluates them for authorization, and converts
 * accepted ones into DMOS commands via the command repository.</p>
 *
 * @doc.type class
 * @doc.purpose Converts authorized agent recommendations into executable commands (DMOS-F2-005)
 * @doc.layer product
 * @doc.pattern ApplicationService, Gateway
 */
public final class DmRecommendationGatewayImpl implements DmRecommendationGateway {

    private final DmRecommendationRepository recommendationRepository;
    private final DmCommandRepository commandRepository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmRecommendationGatewayImpl(
            DmRecommendationRepository recommendationRepository,
            DmCommandRepository commandRepository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.recommendationRepository = Objects.requireNonNull(
            recommendationRepository, "recommendationRepository must not be null");
        this.commandRepository = Objects.requireNonNull(
            commandRepository, "commandRepository must not be null");
        this.kernelAdapter = Objects.requireNonNull(
            kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmAgentRecommendation> submit(DmOperationContext ctx, SubmitRecommendationRequest request) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(request, "request must not be null");

        return kernelAdapter.isAuthorized(ctx, "recommendations/*", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to submit recommendations"));

                DmAgentRecommendation recommendation = DmAgentRecommendation.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .agentId(request.agentId())
                    .targetCommandType(request.targetCommandType())
                    .payload(request.payload())
                    .rationale(request.rationale())
                    .status(DmRecommendationStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();

                return recommendationRepository.save(recommendation);
            });
    }

    @Override
    public Promise<DmAgentRecommendation> accept(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "recommendations/*", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to accept recommendations"));
                return loadOwned(ctx, id);
            })
            .then(rec -> {
                Instant now = Instant.now();
                DmCommand command = DmCommand.builder()
                    .id(UUID.randomUUID().toString())
                    .commandType(rec.getTargetCommandType())
                    .tenantId(rec.getTenantId())
                    .workspaceId(rec.getWorkspaceId())
                    .correlationId(ctx.getCorrelationId().getValue())
                    .issuedBy(ctx.getActor().getPrincipalId())
                    .serializedPayload(rec.getPayload().toString())
                    .status(DmCommandStatus.PENDING)
                    .attemptCount(0)
                    .createdAt(now)
                    .scheduledAt(now)
                    .build();

                return commandRepository.save(command)
                    .then(savedCommand -> {
                        DmAgentRecommendation accepted = rec.accept(savedCommand.getId());
                        return recommendationRepository.update(accepted);
                    });
            });
    }

    @Override
    public Promise<DmAgentRecommendation> reject(DmOperationContext ctx, String id, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "recommendations/*", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to reject recommendations"));
                return loadOwned(ctx, id);
            })
            .then(rec -> recommendationRepository.update(rec.reject(reason)));
    }

    @Override
    public Promise<DmAgentRecommendation> expire(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return kernelAdapter.isAuthorized(ctx, "recommendations/*", "write")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(
                    new SecurityException("Not authorized to expire recommendations"));
                return loadOwned(ctx, id);
            })
            .then(rec -> recommendationRepository.update(rec.expire()));
    }

    @Override
    public Promise<Optional<DmAgentRecommendation>> findById(DmOperationContext ctx, String id) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (id == null || id.isBlank())
            return Promise.ofException(new IllegalArgumentException("id must not be blank"));

        return recommendationRepository.findById(id)
            .then(opt -> {
                if (opt.isPresent()
                        && !opt.get().getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.of(Optional.empty());
                }
                return Promise.of(opt);
            });
    }

    @Override
    public Promise<List<DmAgentRecommendation>> listPending(DmOperationContext ctx, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return recommendationRepository.findByStatus(
            ctx.getTenantId().getValue(), DmRecommendationStatus.PENDING, limit);
    }

    @Override
    public Promise<Long> countByStatus(DmOperationContext ctx, DmRecommendationStatus status) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(status, "status must not be null");
        return recommendationRepository.countByStatus(ctx.getTenantId().getValue(), status);
    }

    private Promise<DmAgentRecommendation> loadOwned(DmOperationContext ctx, String id) {
        return recommendationRepository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(
                        new NoSuchElementException("Recommendation not found: " + id));
                }
                DmAgentRecommendation rec = opt.get();
                if (!rec.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(
                        new NoSuchElementException("Recommendation not found: " + id));
                }
                return Promise.of(rec);
            });
    }
}
