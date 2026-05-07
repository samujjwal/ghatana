package com.ghatana.digitalmarketing.application.funnel;

import com.ghatana.digitalmarketing.application.funnel.TrialOnboardingService.CreateTrialOnboardingCommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.funnel.TrialOnboarding;
import com.ghatana.digitalmarketing.domain.funnel.TrialOnboardingStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of TrialOnboardingService.
 *
 * @doc.type class
 * @doc.purpose Trial onboarding service implementation (P3-001)
 * @doc.layer product
 * @doc.pattern Service
 */
public class TrialOnboardingServiceImpl implements TrialOnboardingService {

    private final TrialOnboardingRepository repository;

    public TrialOnboardingServiceImpl(TrialOnboardingRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<TrialOnboarding> create(DmOperationContext ctx, CreateTrialOnboardingCommand command) {
        String onboardingId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        TrialOnboarding onboarding = TrialOnboarding.builder()
            .id(onboardingId)
            .tenantId(ctx.tenantId().getValue())
            .workspaceId(ctx.workspaceId().getValue())
            .leadId(command.leadId())
            .demoWorkspaceId(command.demoWorkspaceId())
            .status(TrialOnboardingStatus.PENDING)
            .currentStep(0)
            .totalSteps(command.totalSteps())
            .stepProgress(Map.of())
            .createdAt(now)
            .build();

        return repository.save(onboarding);
    }

    @Override
    public Promise<TrialOnboarding> start(DmOperationContext ctx, String onboardingId) {
        return repository.findById(onboardingId)
            .then(onboardingOpt -> {
                if (onboardingOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Trial onboarding not found: " + onboardingId));
                }
                TrialOnboarding onboarding = onboardingOpt.get();
                TrialOnboarding started = onboarding.start();
                return repository.save(started);
            });
    }

    @Override
    public Promise<TrialOnboarding> advanceStep(DmOperationContext ctx, String onboardingId, int stepNumber, Map<String, Object> progress) {
        return repository.findById(onboardingId)
            .then(onboardingOpt -> {
                if (onboardingOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Trial onboarding not found: " + onboardingId));
                }
                TrialOnboarding onboarding = onboardingOpt.get();
                TrialOnboarding advanced = onboarding.advanceStep(stepNumber, progress);
                return repository.save(advanced);
            });
    }

    @Override
    public Promise<TrialOnboarding> complete(DmOperationContext ctx, String onboardingId) {
        return repository.findById(onboardingId)
            .then(onboardingOpt -> {
                if (onboardingOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Trial onboarding not found: " + onboardingId));
                }
                TrialOnboarding onboarding = onboardingOpt.get();
                TrialOnboarding completed = onboarding.complete();
                return repository.save(completed);
            });
    }

    @Override
    public Promise<TrialOnboarding> cancel(DmOperationContext ctx, String onboardingId, String reason) {
        return repository.findById(onboardingId)
            .then(onboardingOpt -> {
                if (onboardingOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Trial onboarding not found: " + onboardingId));
                }
                TrialOnboarding onboarding = onboardingOpt.get();
                TrialOnboarding cancelled = onboarding.cancel(reason);
                return repository.save(cancelled);
            });
    }

    @Override
    public Promise<Optional<TrialOnboarding>> findById(DmOperationContext ctx, String onboardingId) {
        return repository.findById(onboardingId);
    }

    @Override
    public Promise<Optional<TrialOnboarding>> findByLeadId(DmOperationContext ctx, String leadId) {
        return repository.findByLeadId(leadId);
    }

    @Override
    public Promise<java.util.List<TrialOnboarding>> list(DmOperationContext ctx) {
        return repository.listByTenant(ctx.tenantId().getValue());
    }
}
