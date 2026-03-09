/**
 * ML Observatory, Monitoring, and Automation type definitions.
 *
 * <p><b>Purpose</b><br>
 * Central type definitions for ML model management, real-time monitoring,
 * and automation workflow features. These types are used across multiple
 * feature modules.
 *
 * @doc.type types
 * @doc.purpose ML and Monitoring type definitions
 * @doc.layer product
 * @doc.pattern Type Definition
 */

// ===== ML OBSERVATORY TYPES =====

/**
 * Machine learning model definition.
 */
export interface Model {
    id: string;
    name: string;
    version: string;
    accuracy?: number;
    deploytime?: string;
}

/**
 * ML training job definition.
 */
export interface TrainingJob {
    id: string;
    name?: string;
    progress: number;
    status: 'pending' | 'running' | 'completed' | 'failed';
    startTime: string | Date;
    estimatedTime?: string | number;
}

/**
 * A/B test experiment definition.
 */
export interface ABTest {
    id: string;
    name: string;
    status: 'running' | 'completed' | 'paused';
    modelA: string;
    modelB: string;
    winnerModelId?: string;
    confidenceScore?: number;
}

// ===== MONITORING TYPES =====

/**
 * Metric data point for time-series visualization.
 */
export interface MetricDataPoint {
    timestamp: string;
    value: number;
    threshold?: number;
}

/**
 * Detected anomaly in monitoring data.
 */
export interface Anomaly {
    id: string;
    metric: string;
    severity: 'low' | 'medium' | 'high' | 'critical';
    detectedAt: string;
    value: number;
    baselineValue: number;
}

// ===== AUTOMATION TYPES =====

/**
 * Workflow trigger configuration.
 */
export interface WorkflowTrigger {
    id: string;
    type: 'schedule' | 'event' | 'webhook';
    name?: string;
    enabled: boolean;
    config?: Record<string, unknown>;
}

/**
 * Workflow execution record.
 */
export interface WorkflowExecution {
    id: string;
    workflowId: string;
    status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';
    startTime: string | Date;
    endTime?: string | Date;
    error?: string;
    progress?: number;
    duration?: number;
    inputs?: Record<string, unknown>;
    outputs?: Record<string, unknown>;
    tasks?: Array<{
        id: string;
        name: string;
        status: string;
        duration?: number;
    }>;
}

/**
 * Workflow statistics summary.
 */
export interface WorkflowStats {
    totalExecutions: number;
    successCount: number;
    failureCount: number;
    averageDuration: number;
    lastExecutionTime: string;
}
