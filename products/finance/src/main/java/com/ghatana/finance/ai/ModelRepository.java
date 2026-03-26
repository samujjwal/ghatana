/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data access layer for Model
 *
 * @doc.type class
 * @doc.purpose Data access layer for Model
 * @doc.layer product
 * @doc.pattern Repository
 */
public class ModelRepository {
    
    private final Map<String, ModelRecord> models = new ConcurrentHashMap<>();
    
    public ModelRecord findByModelId(String modelId) {
        return models.get(modelId);
    }
    
    public void save(ModelRecord record) {
        models.put(record.getModelId(), record);
    }
    
    public void delete(String modelId) {
        models.remove(modelId);
    }
}
