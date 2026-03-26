/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import java.time.Instant;
import java.util.Map;

/**
 * Component for entity
 *
 * @doc.type record
 * @doc.purpose Component for entity
 * @doc.layer product
 * @doc.pattern Service
 */
public class ModelApprovalRecord {
    
    private String modelId;
    private boolean approved;
    private String approver;
    private Instant approvalDate;
    private String version;
    private Map<String, Object> conditions;
    
    public String getModelId() {
        return modelId;
    }
    
    public void setModelId(String modelId) {
        this.modelId = modelId;
    }
    
    public boolean isApproved() {
        return approved;
    }
    
    public void setApproved(boolean approved) {
        this.approved = approved;
    }
    
    public String getApprover() {
        return approver;
    }
    
    public void setApprover(String approver) {
        this.approver = approver;
    }
    
    public Instant getApprovalDate() {
        return approvalDate;
    }
    
    public void setApprovalDate(Instant approvalDate) {
        this.approvalDate = approvalDate;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public Map<String, Object> getConditions() {
        return conditions;
    }
    
    public void setConditions(Map<String, Object> conditions) {
        this.conditions = conditions;
    }
}
