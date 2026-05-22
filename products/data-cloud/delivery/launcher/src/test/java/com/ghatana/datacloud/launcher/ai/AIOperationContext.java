/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.ai;

/**
 * Context for AI operation execution with governance metadata.
 *
 * @doc.type class
 * @doc.purpose Context for AI operation execution with governance metadata
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class AIOperationContext {

    private final String model;
    private final String prompt;
    private final RiskLevel riskLevel;
    private final String tenantId;
    private String humanApprovalId;
    private String approvedBy;

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public AIOperationContext(String model, String prompt, RiskLevel riskLevel) {
        this.model = model;
        this.prompt = prompt;
        this.riskLevel = riskLevel;
        this.tenantId = null;
    }

    public AIOperationContext(String model, String prompt, RiskLevel riskLevel, String tenantId) {
        this.model = model;
        this.prompt = prompt;
        this.riskLevel = riskLevel;
        this.tenantId = tenantId;
    }

    public String getModel() {
        return model;
    }

    public String getPrompt() {
        return prompt;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getHumanApprovalId() {
        return humanApprovalId;
    }

    public void setHumanApprovalId(String humanApprovalId) {
        this.humanApprovalId = humanApprovalId;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }
}
