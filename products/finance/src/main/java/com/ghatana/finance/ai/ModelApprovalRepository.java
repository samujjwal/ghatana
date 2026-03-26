/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data access layer for ModelApproval
 *
 * @doc.type class
 * @doc.purpose Data access layer for ModelApproval
 * @doc.layer product
 * @doc.pattern Repository
 */
public class ModelApprovalRepository {
    
    private final Map<String, ModelApprovalRecord> approvals = new ConcurrentHashMap<>();
    
    public ModelApprovalRecord findByModelId(String modelId) {
        return approvals.get(modelId);
    }
    
    public void save(ModelApprovalRecord record) {
        approvals.put(record.getModelId(), record);
    }
    
    public void delete(String modelId) {
        approvals.remove(modelId);
    }
}
