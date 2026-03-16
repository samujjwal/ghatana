/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link StepOperatorRegistry} for testing and simple deployments.
 *
 * @doc.type class
 * @doc.purpose In-memory operator lookup for tests
 * @doc.layer platform
 * @doc.pattern Registry
 */
public final class DefaultStepOperatorRegistry implements StepOperatorRegistry {

    private final ConcurrentHashMap<String, StepOperator> operators = new ConcurrentHashMap<>();

    /**
     * Registers an operator.
     *
     * @param operatorId unique ID
     * @param operator   the executable operator
     */
    public void register(@NotNull String operatorId, @NotNull StepOperator operator) {
        operators.put(operatorId, operator);
    }

    @Override
    public StepOperator find(@NotNull String operatorId) {
        return operators.get(operatorId);
    }

    /** Returns the number of registered operators. */
    public int size() {
        return operators.size();
    }

    /** Clears all registrations (for test teardown). */
    public void clear() {
        operators.clear();
    }
}
