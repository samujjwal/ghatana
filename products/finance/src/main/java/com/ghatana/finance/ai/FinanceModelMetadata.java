/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.ModelGovernanceService;
import java.util.Map;
import java.util.HashMap;

/**
 * Component for FinanceModelMetadata
 *
 * @doc.type record
 * @doc.purpose Component for FinanceModelMetadata
 * @doc.layer product
 * @doc.pattern Service
 */
public class FinanceModelMetadata implements ModelGovernanceService.ModelMetadata {
    
    private final ModelRecord record;
    
    public FinanceModelMetadata(ModelRecord record) {
        this.record = record;
    }
    
    @Override
    public String getModelId() {
        return record.getModelId();
    }
    
    @Override
    public String getName() {
        return record.getName();
    }
    
    @Override
    public String getVersion() {
        return record.getVersion();
    }
    
    @Override
    public String getType() {
        return record.getType();
    }
    
    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        if (record.getMetadata() != null) {
            attributes.putAll(record.getMetadata());
        }
        return attributes;
    }

    @Override
    public long getCreatedDate() {
        return 0L;
    }

    @Override
    public long getLastUpdated() {
        return System.currentTimeMillis();
    }
}
