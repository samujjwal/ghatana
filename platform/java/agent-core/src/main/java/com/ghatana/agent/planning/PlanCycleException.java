/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.planning;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown when a {@link PlanGraph} is constructed with a cyclic dependency
 * between {@link PlannedAction} nodes.
 *
 * @doc.type class
 * @doc.purpose Signals a dependency cycle detected during PlanGraph validation
 * @doc.layer platform
 * @doc.pattern Exception
 */
public final class PlanCycleException extends IllegalArgumentException {

    /**
     * Constructs a {@code PlanCycleException} with the given detail message.
     *
     * @param message human-readable cycle description; must not be null
     */
    public PlanCycleException(@NotNull String message) {
        super(message);
    }
}
