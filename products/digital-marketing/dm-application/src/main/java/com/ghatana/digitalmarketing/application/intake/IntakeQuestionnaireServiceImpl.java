package com.ghatana.digitalmarketing.application.intake;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.intake.BusinessIntakeProfile;
import com.ghatana.digitalmarketing.domain.intake.IntakeStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link IntakeQuestionnaireService}.
 *
 * @doc.type class
 * @doc.purpose DMOS intake questionnaire service implementation for draft and submit flows
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class IntakeQuestionnaireServiceImpl implements IntakeQuestionnaireService {

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final IntakeQuestionnaireRepository repository;

    public IntakeQuestionnaireServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            IntakeQuestionnaireRepository repository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<BusinessIntakeProfile> saveDraft(DmOperationContext ctx, SaveDraftCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "intake/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to save intake draft"));
                }
                return repository.findByWorkspaceId(ctx.getWorkspaceId())
                    .then(existing -> {
                        Instant now = Instant.now();
                        BusinessIntakeProfile profile = existing
                            .map(value -> value.toBuilder()
                                .businessName(command.businessName())
                                .websiteUrl(command.websiteUrl())
                                .offerSummary(command.offerSummary())
                                .targetAudience(command.targetAudience())
                                .primaryGeography(command.primaryGeography())
                                .monthlyBudgetAmount(command.monthlyBudgetAmount())
                                .competitorDomains(command.competitorDomains())
                                .constraints(command.constraints())
                                .growthGoal(command.growthGoal())
                                .riskTolerance(command.riskTolerance())
                                .status(IntakeStatus.DRAFT)
                                .updatedAt(now)
                                .submittedAt(null)
                                .build())
                            .orElseGet(() -> BusinessIntakeProfile.builder()
                                .intakeId(UUID.randomUUID().toString())
                                .workspaceId(ctx.getWorkspaceId())
                                .businessName(command.businessName())
                                .websiteUrl(command.websiteUrl())
                                .offerSummary(command.offerSummary())
                                .targetAudience(command.targetAudience())
                                .primaryGeography(command.primaryGeography())
                                .monthlyBudgetAmount(command.monthlyBudgetAmount())
                                .competitorDomains(command.competitorDomains())
                                .constraints(command.constraints())
                                .growthGoal(command.growthGoal())
                                .riskTolerance(command.riskTolerance())
                                .status(IntakeStatus.DRAFT)
                                .createdAt(now)
                                .updatedAt(now)
                                .createdBy(ctx.getActor().getPrincipalId())
                                .build());

                        return repository.save(profile);
                    });
            });
    }

    @Override
    public Promise<BusinessIntakeProfile> getDraft(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "intake/*", "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read intake draft"));
                }
                return repository.findByWorkspaceId(ctx.getWorkspaceId())
                    .then(existing -> existing
                        .map(Promise::of)
                        .orElseGet(() -> Promise.ofException(new NoSuchElementException("Intake draft not found"))));
            });
    }

    @Override
    public Promise<BusinessIntakeProfile> submitIntake(DmOperationContext ctx, SubmitIntakeCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "intake/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to submit intake"));
                }
                return repository.findByWorkspaceId(ctx.getWorkspaceId())
                    .then(existing -> {
                        if (existing.isEmpty()) {
                            return Promise.ofException(new NoSuchElementException("Intake draft not found"));
                        }
                        BusinessIntakeProfile submitted = existing.get().submit(
                            command.aiSummary(),
                            command.aiConfidenceScore(),
                            command.aiUnknowns(),
                            Instant.now()
                        );

                        return repository.save(submitted)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx,
                                "intake/" + saved.getIntakeId(),
                                "intake-submitted",
                                Map.of(
                                    "eventType", "dm.intake.submitted.v1",
                                    "confidence", Double.toString(saved.getAiConfidenceScore()),
                                    "unknownCount", Integer.toString(saved.getAiUnknowns().size())
                                )
                            ).map(__ -> saved));
                    });
            });
    }
}
