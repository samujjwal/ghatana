/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.atomic;

import java.util.ArrayList;
import java.util.List;

/**
 * Context for atomic workflow execution.
 *
 * @doc.type class
 * @doc.purpose Context for atomic workflow execution
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class AtomicWorkflowContext {

    private final String workflowId;
    private final String tenantId;
    private final String operationType;
    private String idempotencyKey;
    private boolean requireAudit;
    private boolean requireOutbox;
    private int maxRetries;
    private final List<String> compensationSteps;

    public AtomicWorkflowContext(String workflowId, String tenantId, String operationType) {
        this.workflowId = workflowId;
        this.tenantId = tenantId;
        this.operationType = operationType;
        this.compensationSteps = new ArrayList<>();
        this.maxRetries = 3;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public boolean isRequireAudit() {
        return requireAudit;
    }

    public void setRequireAudit(boolean requireAudit) {
        this.requireAudit = requireAudit;
    }

    public boolean isRequireOutbox() {
        return requireOutbox;
    }

    public void setRequireOutbox(boolean requireOutbox) {
        this.requireOutbox = requireOutbox;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public List<String> getCompensationSteps() {
        return compensationSteps;
    }

    public void addCompensationStep(String step) {
        this.compensationSteps.add(step);
    }
}
