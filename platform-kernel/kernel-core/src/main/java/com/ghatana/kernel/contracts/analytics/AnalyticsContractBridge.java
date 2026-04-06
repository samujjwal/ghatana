/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts.analytics;

import com.ghatana.kernel.contracts.AnalyticsContract;

import java.util.List;

/**
 * Bridge interface for subsystems that emit metrics and telemetry to connect
 * to the canonical {@link AnalyticsContract} model.
 *
 * <p>Implementors include:</p>
 * <ul>
 *   <li><b>AI Governance</b> — model lifecycle, bias detection, prediction audit metrics</li>
 *   <li><b>Audit Trail</b> — compliance event counters, retention metrics</li>
 *   <li><b>Agent Framework</b> — memory tier utilization, reasoning latency, reflection cost</li>
 * </ul>
 *
 * <p>Each subsystem exports its metric declarations and dashboard definitions
 * through this interface, enabling the canonical analytics contract to represent
 * the full observability surface.</p>
 *
 * @doc.type interface
 * @doc.purpose Bridge between telemetry subsystems and canonical analytics contract
 * @doc.layer core
 * @doc.pattern Adapter, Bridge
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public interface AnalyticsContractBridge {

    /**
     * Exports the metric declarations from this subsystem.
     */
    List<AnalyticsContract.MetricDeclaration> exportMetrics();

    /**
     * Exports the dashboard definitions from this subsystem.
     */
    List<AnalyticsContract.DashboardDeclaration> exportDashboards();

    /**
     * Returns the subsystem identifier for metric namespacing.
     */
    String getSubsystemId();
}
