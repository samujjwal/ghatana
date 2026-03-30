/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

/**
 * Manages the model approval workflow lifecycle — initiation, recording decisions,
 * and rejection. All approval state is persisted through {@link ModelApprovalRepository}.
 *
 * <p>SOX compliance requires that every model used in production financial decisions
 * passes through an explicit human-in-the-loop approval before becoming active.
 * This service enforces that contract: models without a persisted approval record
 * are rejected at the gate by {@link FinanceModelGovernanceImpl#validateModelUsage}.
 *
 * @doc.type class
 * @doc.purpose Finance model approval workflow — initiation, human approval recording, rejection
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class ApprovalWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalWorkflowService.class);

    private final ModelApprovalRepository approvalRepository;
    private final AlertService alertService;

    public ApprovalWorkflowService(ModelApprovalRepository approvalRepository,
                                   AlertService alertService) {
        this.approvalRepository = Objects.requireNonNull(approvalRepository, "approvalRepository must not be null");
        this.alertService       = Objects.requireNonNull(alertService,       "alertService must not be null");
    }

    /**
     * Initiates the approval workflow for a model that has been registered but not yet
     * approved. Sends a notification to approvers and records a PENDING approval record.
     *
     * @param modelId the model identifier requiring approval
     */
    public void initiateApproval(String modelId) {
        Objects.requireNonNull(modelId, "modelId must not be null");

        ModelApprovalRecord existing = approvalRepository.findByModelId(modelId);
        if (existing != null && existing.isApproved()) {
            logger.info("Model '{}' already approved — skipping workflow initiation", modelId);
            return;
        }

        ModelApprovalRecord pending = new ModelApprovalRecord();
        pending.setModelId(modelId);
        pending.setApproved(false);
        pending.setApprover(null);
        pending.setApprovalDate(null);
        pending.setVersion("pending");
        approvalRepository.save(pending);

        alertService.sendAlert(
            "Model Approval Required",
            "Model '" + modelId + "' requires compliance review and approval before production use."
        );
        logger.info("Approval workflow initiated for model='{}'", modelId);
    }

    /**
     * Records a human approver's approval decision for a model version.
     *
     * @param modelId  the model identifier being approved
     * @param approver the approver identifier (e.g. employee ID)
     * @param version  the specific model version being approved
     */
    public void recordApproval(String modelId, String approver, String version) {
        Objects.requireNonNull(modelId,  "modelId must not be null");
        Objects.requireNonNull(approver, "approver must not be null");
        Objects.requireNonNull(version,  "version must not be null");

        ModelApprovalRecord record = new ModelApprovalRecord();
        record.setModelId(modelId);
        record.setApproved(true);
        record.setApprover(approver);
        record.setApprovalDate(Instant.now());
        record.setVersion(version);
        approvalRepository.save(record);

        logger.info("Model '{}' version='{}' approved by '{}'", modelId, version, approver);
    }

    /**
     * Records an approval rejection and removes any pending approval record.
     *
     * @param modelId the model identifier being rejected
     * @param reason  a human-readable rejection reason (for audit)
     */
    public void recordRejection(String modelId, String reason) {
        Objects.requireNonNull(modelId, "modelId must not be null");
        Objects.requireNonNull(reason,  "reason must not be null");

        approvalRepository.delete(modelId);
        alertService.sendAlert(
            "Model Rejected",
            "Model '" + modelId + "' was rejected: " + reason
        );
        logger.warn("Model '{}' approval rejected: {}", modelId, reason);
    }
}
