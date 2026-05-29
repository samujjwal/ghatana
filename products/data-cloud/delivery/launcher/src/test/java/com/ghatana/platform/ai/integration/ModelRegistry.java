package com.ghatana.platform.ai.integration;

import io.activej.promise.Promise;

/**
 * Test-scope compatibility shim for legacy launcher AI governance tests.
 */
public interface ModelRegistry {
    Promise<Boolean> isModelAvailable(String model);

    Promise<Double> getCostBudget(String tenantId);

    Promise<Double> getCurrentCost(String tenantId);

    Promise<Double> getModelQuality(String model);
}
