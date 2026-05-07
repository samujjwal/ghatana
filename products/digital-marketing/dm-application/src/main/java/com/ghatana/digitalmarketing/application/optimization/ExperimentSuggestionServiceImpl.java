package com.ghatana.digitalmarketing.application.optimization;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.optimization.ExperimentSuggestion;
import com.ghatana.digitalmarketing.domain.optimization.ExperimentSuggestionStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link ExperimentSuggestionService}.
 *
 * @doc.type class
 * @doc.purpose Publishes and manages lifecycle of experiment suggestions (P3-004)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class ExperimentSuggestionServiceImpl implements ExperimentSuggestionService {

    private final ExperimentSuggestionRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public ExperimentSuggestionServiceImpl(
            ExperimentSuggestionRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<ExperimentSuggestion> publish(DmOperationContext ctx, PublishSuggestionCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "experiment-suggestions", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to publish suggestions"));
                }
                ExperimentSuggestion suggestion = ExperimentSuggestion.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .campaignId(command.campaignId())
                    .experimentType(command.experimentType())
                    .title(command.title())
                    .description(command.description())
                    .controlVariant(command.controlVariant())
                    .treatmentVariant(command.treatmentVariant())
                    .hypothesis(command.hypothesis())
                    .successMetric(command.successMetric())
                    .minimumDetectableEffect(command.minimumDetectableEffect())
                    .rationale(command.rationale())
                    .status(ExperimentSuggestionStatus.PENDING)
                    .createdAt(Instant.now())
                    .expiresAt(command.expiresAt())
                    .build();
                return repository.save(suggestion)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "experiment-suggestion-published",
                        Map.of("campaignId", command.campaignId(), "experimentType", command.experimentType().name())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<ExperimentSuggestion> approve(DmOperationContext ctx, String suggestionId, String experimentId, String approvedBy) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(suggestionId, "suggestionId must not be null");
        Objects.requireNonNull(experimentId, "experimentId must not be null");
        Objects.requireNonNull(approvedBy, "approvedBy must not be null");

        return kernelAdapter.isAuthorized(ctx, "experiment-suggestions", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to approve suggestions"));
                }
                return loadAndValidateTenant(ctx, suggestionId)
                    .then(existing -> {
                        ExperimentSuggestion updated = existing.approve(experimentId, approvedBy);
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "experiment-suggestion-approved",
                                Map.of("experimentId", experimentId, "approvedBy", approvedBy)
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<ExperimentSuggestion> reject(DmOperationContext ctx, String suggestionId, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(suggestionId, "suggestionId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        return kernelAdapter.isAuthorized(ctx, "experiment-suggestions", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to reject suggestions"));
                }
                return loadAndValidateTenant(ctx, suggestionId)
                    .then(existing -> {
                        ExperimentSuggestion updated = existing.reject(reason);
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "experiment-suggestion-rejected", Map.of("reason", reason)
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<ExperimentSuggestion> expire(DmOperationContext ctx, String suggestionId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(suggestionId, "suggestionId must not be null");

        return loadAndValidateTenant(ctx, suggestionId)
            .then(existing -> {
                ExperimentSuggestion updated = existing.expire();
                return repository.update(updated);
            });
    }

    @Override
    public Promise<Optional<ExperimentSuggestion>> findById(DmOperationContext ctx, String suggestionId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.findById(suggestionId)
            .map(opt -> opt.filter(r -> r.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<ExperimentSuggestion>> listByWorkspace(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "experiment-suggestions", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list suggestions"));
                }
                return repository.listByWorkspace(ctx.getTenantId().getValue(), ctx.getWorkspaceId().getValue());
            });
    }

    @Override
    public Promise<List<ExperimentSuggestion>> listByCampaign(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        return kernelAdapter.isAuthorized(ctx, "experiment-suggestions", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list suggestions"));
                }
                return repository.listByCampaign(ctx.getTenantId().getValue(), campaignId);
            });
    }

    @Override
    public Promise<List<ExperimentSuggestion>> listByStatus(DmOperationContext ctx, ExperimentSuggestionStatus status) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "experiment-suggestions", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list suggestions"));
                }
                return repository.listByStatus(ctx.getTenantId().getValue(), status);
            });
    }

    private Promise<ExperimentSuggestion> loadAndValidateTenant(DmOperationContext ctx, String id) {
        return repository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Suggestion not found: " + id));
                }
                ExperimentSuggestion r = opt.get();
                if (!r.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("Suggestion does not belong to tenant"));
                }
                return Promise.of(r);
            });
    }
}
