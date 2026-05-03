package com.ghatana.digitalmarketing.application.evaluation;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.evaluation.DmAgentEvaluation;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmAgentEvaluationService}.
 *
 * @doc.type class
 * @doc.purpose Submits and queries agent evaluations (DMOS-F3-005)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmAgentEvaluationServiceImpl implements DmAgentEvaluationService {

    private final DmAgentEvaluationRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmAgentEvaluationServiceImpl(
            DmAgentEvaluationRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmAgentEvaluation> submit(DmOperationContext ctx, SubmitEvaluationCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "agent-evaluations", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to submit agent evaluations"));
                }
                DmAgentEvaluation evaluation = DmAgentEvaluation.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .agentId(command.agentId())
                    .agentType(command.agentType())
                    .metrics(command.metrics())
                    .overallScore(command.overallScore())
                    .verdict(command.verdict())
                    .evaluatedBy(ctx.getActor().getPrincipalId())
                    .evaluatedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
                return repository.save(evaluation)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "agent-evaluation-submitted",
                        Map.of("agentId", (Object) command.agentId(), "verdict", (Object) command.verdict())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<Optional<DmAgentEvaluation>> findById(DmOperationContext ctx, String evaluationId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.findById(evaluationId)
            .map(opt -> opt.filter(e -> e.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmAgentEvaluation>> listByAgent(DmOperationContext ctx, String agentId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "agent-evaluations", "read")
            .then(authorized -> {
                if (!authorized) return Promise.ofException(new SecurityException("Actor not authorised to list agent evaluations"));
                return repository.listByAgent(ctx.getTenantId().getValue(), agentId);
            });
    }
}
