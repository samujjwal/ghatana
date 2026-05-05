package com.ghatana.digitalmarketing.application.approval;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.domain.approval.ApprovalSnapshot;
import com.ghatana.digitalmarketing.domain.approval.ApprovalTargetType;
import com.ghatana.plugin.approval.ApprovalDecision;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.ApprovalRequest;
import com.ghatana.plugin.approval.HumanApprovalPlugin;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * DMOS approval workflow service — delegates persistence-side approval lifecycle
 * to {@link HumanApprovalPlugin} and stores immutable {@link ApprovalSnapshot}s.
 *
 * <p>Routing logic uses risk level and target type to determine the required approver
 * role. Risk levels 1-2 route to {@code brand-manager}, 3 to {@code marketing-director},
 * and 4-5 to {@code exec-sponsor}. OVERRIDE type always routes to {@code exec-sponsor}
 * regardless of risk level.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS F1-022 approval workflow service implementation
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ApprovalWorkflowServiceImpl implements ApprovalWorkflowService {

    static final String ROLE_BRAND_MANAGER       = "brand-manager";
    static final String ROLE_MARKETING_DIRECTOR  = "marketing-director";
    static final String ROLE_EXEC_SPONSOR        = "exec-sponsor";

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final HumanApprovalPlugin           approvalPlugin;
    private final ApprovalSnapshotRepository    snapshotRepository;
    private final DmosMetricsCollector          metrics;

    public ApprovalWorkflowServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            HumanApprovalPlugin approvalPlugin,
            ApprovalSnapshotRepository snapshotRepository,
            DmosMetricsCollector metrics) {
        this.kernelAdapter      = Objects.requireNonNull(kernelAdapter,      "kernelAdapter must not be null");
        this.approvalPlugin     = Objects.requireNonNull(approvalPlugin,     "approvalPlugin must not be null");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository must not be null");
        this.metrics            = Objects.requireNonNull(metrics,            "metrics must not be null");
    }

    @Override
    public Promise<ApprovalRecord> submitForApproval(
            DmOperationContext ctx, SubmitForApprovalCommand command) {
        Objects.requireNonNull(ctx,     "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "approval/" + command.targetId(), "submit")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor is not authorised to submit for approval"));
                }

                String requestId   = UUID.randomUUID().toString();
                String requiredRole = resolveApproverRole(command.targetType(), command.riskLevel());

                ApprovalRequest pluginRequest = new ApprovalRequest(
                    requestId,
                    command.targetId(),
                    ctx.getActor().getPrincipalId(),
                    "dmos-approval/" + command.targetType().name().toLowerCase(),
                    command.description(),
                    Map.of(
                        "targetType",  command.targetType().name(),
                        "riskLevel",   command.riskLevel(),
                        "workspaceId", ctx.getWorkspaceId()
                    ),
                    Instant.now(),
                    null
                );

                return approvalPlugin.requestApproval(pluginRequest)
                    .then(record -> {
                        ApprovalSnapshot snapshot = new ApprovalSnapshot(
                            requestId,
                            command.targetType(),
                            command.targetId(),
                            ctx.getWorkspaceId().getValue(),
                            command.description(),
                            command.validationResultId(),
                            command.riskLevel(),
                            requiredRole,
                            Instant.now(),
                            0L
                        );
                        return snapshotRepository.save(ctx.getWorkspaceId().getValue(), snapshot)
                            .then(saved -> {
                                Map<String, Object> details = Map.of(
                                    "requestId",   requestId,
                                    "targetId",    command.targetId(),
                                    "targetType",  command.targetType().name(),
                                    "riskLevel",   command.riskLevel(),
                                    "approverRole", requiredRole
                                );
                                return kernelAdapter.recordAudit(
                                        ctx,
                                        command.targetId(),
                                        "approval-submitted",
                                        details)
                                    .map(ignored -> {
                                        metrics.increment(DmosMetricsCollector.APPROVAL_REQUESTED, Map.of(
                                            "tenantId",     ctx.getTenantId().getValue(),
                                            "operationType", command.targetType().name()
                                        ));
                                        return record;
                                    });
                            });
                    });
            });
    }

    @Override
    public Promise<ApprovalRecord> recordDecision(
            DmOperationContext ctx, RecordApprovalDecisionCommand command) {
        Objects.requireNonNull(ctx,     "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "approval/" + command.requestId(), "decide")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor is not authorised to decide on this approval"));
                }

                if (command.decision() == ApprovalDecision.REJECTED
                        && (command.notes() == null || command.notes().isBlank())) {
                    return Promise.ofException(
                        new IllegalArgumentException("Notes are required when rejecting an approval"));
                }

                return approvalPlugin.completeApproval(
                        command.requestId(),
                        command.decision(),
                        ctx.getActor().getPrincipalId(),
                        command.notes())
                    .then(record -> {
                        Map<String, Object> details = Map.of(
                            "requestId", command.requestId(),
                            "decision",  command.decision().name()
                        );
                        return kernelAdapter.recordAudit(
                                ctx,
                                command.requestId(),
                                "approval-decision",
                                details)
                            .map(ignored -> {
                                if (command.decision() == ApprovalDecision.REJECTED) {
                                    metrics.increment(DmosMetricsCollector.COMPLIANCE_VIOLATION, Map.of(
                                        "tenantId", ctx.getTenantId().getValue(),
                                        "workspaceId", ctx.getWorkspaceId().getValue(),
                                        "ruleSet",  "dmos-approval-rejection"
                                    ));
                                }
                                return record;
                            });
                    });
            });
    }

    @Override
    public Promise<Optional<ApprovalRecord>> getApprovalStatus(
            DmOperationContext ctx, String requestId) {
        Objects.requireNonNull(ctx,       "ctx must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        if (requestId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("requestId must not be blank"));
        }
        return kernelAdapter.isAuthorized(ctx, "approval/" + requestId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor is not authorised to read this approval"));
                }
                return approvalPlugin.getApprovalStatus(requestId);
            });
    }

    @Override
    public Promise<List<ApprovalRecord>> listPendingApprovals(
            DmOperationContext ctx, String subjectId) {
        Objects.requireNonNull(ctx,       "ctx must not be null");
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        if (subjectId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("subjectId must not be blank"));
        }
        return kernelAdapter.isAuthorized(ctx, "approvals/pending", "list")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor is not authorised to list pending approvals"));
                }
                return approvalPlugin.listPendingForSubject(subjectId)
                    .map(records -> {
                        metrics.increment(DmosMetricsCollector.APPROVAL_PENDING_GAUGE, Map.of(
                            "tenantId",    ctx.getTenantId().getValue(),
                            "workspaceId", ctx.getWorkspaceId().getValue(),
                            "count",       String.valueOf(records.size())
                        ));
                        return records;
                    });
            });
    }

    @Override
    public Promise<List<ApprovalRecord>> listPendingApprovalsForWorkspace(
            DmOperationContext ctx, String workspaceId) {
        Objects.requireNonNull(ctx,          "ctx must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        if (workspaceId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("workspaceId must not be blank"));
        }
        return kernelAdapter.isAuthorized(ctx, "approvals/pending", "list")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor is not authorised to list pending approvals"));
                }
                return approvalPlugin.listPendingForWorkspace(workspaceId)
                    .map(records -> {
                        metrics.increment(DmosMetricsCollector.APPROVAL_PENDING_GAUGE, Map.of(
                            "tenantId",    ctx.getTenantId().getValue(),
                            "workspaceId", workspaceId,
                            "count",       String.valueOf(records.size())
                        ));
                        return records;
                    });
            });
    }

    @Override
    public Promise<Optional<ApprovalSnapshot>> getSnapshot(
            DmOperationContext ctx, String requestId) {
        Objects.requireNonNull(ctx,       "ctx must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        if (requestId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("requestId must not be blank"));
        }
        return kernelAdapter.isAuthorized(ctx, "approval/" + requestId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(
                        new SecurityException("Actor is not authorised to read this approval snapshot"));
                }
                return snapshotRepository.findByRequestId(ctx.getWorkspaceId().getValue(), requestId);
            });
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the required approver role from target type and risk level.
     *
     * <p>OVERRIDE type always requires {@code exec-sponsor}. Otherwise:
     * risk 1-2 → brand-manager, risk 3 → marketing-director, risk 4-5 → exec-sponsor.</p>
     */
    static String resolveApproverRole(ApprovalTargetType targetType, int riskLevel) {
        if (targetType == ApprovalTargetType.OVERRIDE) {
            return ROLE_EXEC_SPONSOR;
        }
        if (riskLevel >= 4) {
            return ROLE_EXEC_SPONSOR;
        }
        if (riskLevel == 3) {
            return ROLE_MARKETING_DIRECTOR;
        }
        return ROLE_BRAND_MANAGER;
    }
}
