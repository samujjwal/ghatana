/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Data access layer for ModelPerformance
 *
 * @doc.type class
 * @doc.purpose Data access layer for ModelPerformance
 * @doc.layer product
 * @doc.pattern Repository
 */
public class ModelPerformanceRepository {
    
    private final Map<String, List<ModelPerformanceRecord>> performanceRecords = new ConcurrentHashMap<>();
    
    public void save(ModelPerformanceRecord record) {
        performanceRecords.computeIfAbsent(record.getModelId(), k -> new CopyOnWriteArrayList<>())
            .add(record);
    }
    
    public List<ModelPerformanceRecord> findByModelId(String modelId) {
        return performanceRecords.getOrDefault(modelId, List.of());
    }
}
