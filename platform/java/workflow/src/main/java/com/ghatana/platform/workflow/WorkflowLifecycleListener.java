/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

/**
 * SPI for observing workflow lifecycle events.
 *
 * <p>Implementations are registered on the workflow engine and receive events
 * at every significant phase of execution. Built-in listeners include:
 * <ul>
 *   <li>Metrics listener (Micrometer counters and histograms)</li>
 *   <li>Audit listener (structured audit records via {@code AuditService})</li>
 * </ul>
 *
 * <p>Listener exceptions are logged but never propagated — a faulty listener
 * must not disrupt workflow execution.
 *
 * @doc.type interface
 * @doc.purpose SPI for workflow lifecycle event observation
 * @doc.layer core
 * @doc.pattern Observer
 */
@FunctionalInterface
public interface WorkflowLifecycleListener {

    /**
     * Called when a lifecycle event occurs during workflow execution.
     *
     * @param event the lifecycle event (never null)
     */
    void onEvent(WorkflowLifecycleEvent event);
}
