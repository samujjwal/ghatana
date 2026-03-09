/**
 * ML Model API Client
 *
 * <p><b>Purpose</b><br>
 * Provides API methods for machine learning model operations, including performance metrics,
 * feature importance, model comparisons, and training management.
 *
 * <p><b>Features</b><br>
 * - Model performance metrics and predictions
 * - Feature importance and drift detection
 * - A/B testing and model comparisons
 * - Training job management
 * - Model deployment and versioning
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * const models = await mlApi.getModels(tenantId);
 * const metrics = await mlApi.getModelMetrics(modelId, timeRange);
 * const comparison = await mlApi.compareModels(modelIds);
 * ```
 *
 * @doc.type service
 * @doc.purpose ML model operations API client
 * @doc.layer product
 * @doc.pattern API Client
 */

export interface ModelMetrics {
    accuracy: number;
    precision: number;
    recall: number;
    f1Score: number;
    auc: number;
    latency: number;
    throughput: number;
}

export interface FeatureImportance {
    name: string;
    importance: number;
    trend: 'increasing' | 'stable' | 'decreasing';
}

export interface ModelPerformance {
    modelId: string;
    timestamp: Date;
    predictions: number;
    accuracy: number;
    latency: number;
    errors: number;
}

export interface TrainingJob {
    id: string;
    modelId: string;
    status: 'pending' | 'running' | 'completed' | 'failed';
    progress: number;
    startTime: Date;
    estimatedTime: number;
    trainingLoss: number;
    validationLoss: number;
}

export interface ModelComparison {
    modelIds: string[];
    metrics: Record<string, ModelMetrics>;
    performanceDelta: Record<string, number>;
    recommendation: string;
}

/**
 * Get all models for a tenant with metadata and status.
 * @param tenantId - Tenant identifier
 * @returns Array of model objects with performance summaries
 */
export async function getModels(tenantId: string) {
    const response = await fetch(`/api/v1/tenants/${tenantId}/models`, {
        headers: { 'X-Tenant-ID': tenantId },
    });
    if (!response.ok) throw new Error('Failed to fetch models');
    return response.json();
}

/**
 * Get detailed metrics for a specific model over a time range.
 * @param modelId - Model identifier
 * @param timeRange - Object with start and end dates
 * @returns ModelMetrics object with performance statistics
 */
export async function getModelMetrics(
    modelId: string,
    timeRange: { start: Date; end: Date }
): Promise<ModelMetrics> {
    const params = new URLSearchParams({
        startTime: timeRange.start.toISOString(),
        endTime: timeRange.end.toISOString(),
    });
    const response = await fetch(`/api/v1/models/${modelId}/metrics?${params}`);
    if (!response.ok) throw new Error('Failed to fetch model metrics');
    return response.json();
}

/**
 * Get feature importance rankings for a model.
 * @param modelId - Model identifier
 * @returns Array of features sorted by importance
 */
export async function getFeatureImportance(modelId: string): Promise<FeatureImportance[]> {
    const response = await fetch(`/api/v1/models/${modelId}/feature-importance`);
    if (!response.ok) throw new Error('Failed to fetch feature importance');
    return response.json();
}

/**
 * Get historical performance data for trend analysis.
 * @param modelId - Model identifier
 * @param timeRange - Object with start and end dates
 * @returns Array of performance data points
 */
export async function getModelPerformanceHistory(
    modelId: string,
    timeRange: { start: Date; end: Date }
): Promise<ModelPerformance[]> {
    const params = new URLSearchParams({
        startTime: timeRange.start.toISOString(),
        endTime: timeRange.end.toISOString(),
    });
    const response = await fetch(`/api/v1/models/${modelId}/performance-history?${params}`);
    if (!response.ok) throw new Error('Failed to fetch performance history');
    return response.json();
}

/**
 * Trigger drift detection on a model.
 * @param modelId - Model identifier
 * @returns Object with drift metrics and affected features
 */
export async function detectDrift(modelId: string) {
    const response = await fetch(`/api/v1/models/${modelId}/detect-drift`, { method: 'POST' });
    if (!response.ok) throw new Error('Failed to detect drift');
    return response.json();
}

/**
 * Compare multiple models by their performance metrics.
 * @param modelIds - Array of model identifiers to compare
 * @returns ModelComparison object with metrics and recommendations
 */
export async function compareModels(modelIds: string[]): Promise<ModelComparison> {
    const response = await fetch('/api/v1/models/compare', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ modelIds }),
    });
    if (!response.ok) throw new Error('Failed to compare models');
    return response.json();
}

/**
 * Get active training jobs for a tenant.
 * @param tenantId - Tenant identifier
 * @returns Array of training job objects
 */
export async function getTrainingJobs(tenantId: string): Promise<TrainingJob[]> {
    const response = await fetch(`/api/v1/tenants/${tenantId}/training-jobs`, {
        headers: { 'X-Tenant-ID': tenantId },
    });
    if (!response.ok) throw new Error('Failed to fetch training jobs');
    return response.json();
}

/**
 * Get A/B tests for a tenant (alias expected by hooks).
 */
export async function getABTests(tenantId: string) {
    const response = await fetch(`/api/v1/tenants/${tenantId}/ab-tests`, {
        headers: { 'X-Tenant-ID': tenantId },
    });
    if (!response.ok) throw new Error('Failed to fetch A/B tests');
    return response.json();
}

/**
 * Cancel a training job (alias expected by hooks).
 */
export async function cancelTrainingJob(jobId: string) {
    const response = await fetch(`/api/v1/training-jobs/${jobId}/cancel`, {
        method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to cancel training job');
    return response.json();
}

/**
 * Stop an active A/B test (alias expected by hooks).
 */
export async function stopABTest(testId: string) {
    const response = await fetch(`/api/v1/ab-tests/${testId}/stop`, {
        method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to stop A/B test');
    return response.json();
}

/**
 * Start a new model training job.
 * @param modelId - Model identifier
 * @param config - Training configuration
 * @returns Created TrainingJob object
 */
export async function startTraining(
    modelId: string,
    config: { epochs: number; batchSize: number; learningRate: number }
) {
    const response = await fetch(`/api/v1/models/${modelId}/train`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
    });
    if (!response.ok) throw new Error('Failed to start training');
    return response.json();
}

/**
 * Deploy a model to production.
 * @param modelId - Model identifier
 * @param deploymentConfig - Deployment configuration
 * @returns Deployment status and details
 */
export async function deployModel(
    modelId: string,
    deploymentConfig: { replicas: number; region: string }
) {
    const response = await fetch(`/api/v1/models/${modelId}/deploy`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(deploymentConfig),
    });
    if (!response.ok) throw new Error('Failed to deploy model');
    return response.json();
}

/**
 * Get A/B test results for models.
 * @param testId - A/B test identifier
 * @returns Test results with statistical significance
 */
export async function getAbTestResults(testId: string) {
    const response = await fetch(`/api/v1/ab-tests/${testId}/results`);
    if (!response.ok) throw new Error('Failed to fetch A/B test results');
    return response.json();
}

/**
 * Create a new A/B test between two models.
 * @param config - Test configuration with model IDs and split percentage
 * @returns Created test object with ID
 */
export async function createAbTest(config: {
    modelIdA: string;
    modelIdB: string;
    splitPercentage: number;
    duration: number;
}) {
    const response = await fetch('/api/v1/ab-tests', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(config),
    });
    if (!response.ok) throw new Error('Failed to create A/B test');
    return response.json();
}

export default {
    getModels,
    getModelMetrics,
    getFeatureImportance,
    getModelPerformanceHistory,
    detectDrift,
    compareModels,
    getTrainingJobs,
    startTraining,
    deployModel,
    getAbTestResults,
    createAbTest,
    getABTests,
    cancelTrainingJob,
    stopABTest,
};
