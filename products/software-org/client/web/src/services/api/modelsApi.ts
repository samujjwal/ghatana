import { apiClient } from './index';

/**
 * ML Model Management API (Day 9).
 *
 * <p><b>Purpose</b><br>
 * API methods for model registry, performance tracking, A/B testing, and deployment.
 *
 * <p><b>Endpoints</b><br>
 * - GET /models: Model registry
 * - GET /models/:id: Model details
 * - GET /models/:id/versions: Model version history
 * - POST /models/:id/deploy: Deploy model version
 * - POST /models/:id/test: Run test suite
 *
 * @doc.type service
 * @doc.purpose ML Model Management API client
 * @doc.layer product
 * @doc.pattern Service Layer
 */

export type ModelStatus = 'active' | 'testing' | 'deprecated' | 'archived';
export type TestStatus = 'pass' | 'fail' | 'pending' | 'running';

export interface Model {
    id: string;
    name: string;
    type: string; // 'classification', 'regression', 'ranking', etc.
    description?: string;
    currentVersion: string;
    latestVersion?: string;
    status: ModelStatus;
    accuracy: number;
    precision: number;
    recall: number;
    f1Score: number;
    latency: number; // ms
    throughput: number; // req/sec
    deployedAt?: string;
    lastUpdated: string;
}

export interface ModelVersion {
    version: string;
    createdAt: string;
    accuracy: number;
    precision: number;
    recall: number;
    f1Score: number;
    latency: number;
    throughput: number;
    trainingData: {
        samples: number;
        features: number;
        trainingTimeMs: number;
    };
    deployedAt?: string;
    status: ModelStatus;
}

export interface ModelComparison {
    id: string;
    version: string;
    accuracy: number;
    precision: number;
    recall: number;
    f1Score: number;
    latency: number;
    throughput: number;
}

export interface TestCase {
    id: string;
    name: string;
    type: 'unit' | 'integration' | 'e2e';
    status: TestStatus;
    duration: number;
    coverage: number;
    assertions: number;
}

export interface ABTestResult {
    modelAId: string;
    modelBId: string;
    startedAt: string;
    duration: string;
    totalRequests: number;
    modelAWins: number;
    modelBWins: number;
    statistically: boolean;
}

export const modelsApi = {
    /**
     * Get all models in registry
     */
    async getModels(params?: { status?: ModelStatus; type?: string }) {
        const response = await apiClient.get<Model[]>('/models', { params });
        return response.data;
    },

    /**
     * Get model details
     */
    async getModel(modelId: string) {
        const response = await apiClient.get<Model>(`/models/${modelId}`);
        return response.data;
    },

    /**
     * Get model version history
     */
    async getModelVersions(modelId: string) {
        const response = await apiClient.get<ModelVersion[]>(`/models/${modelId}/versions`);
        return response.data;
    },

    /**
     * Get specific model version
     */
    async getModelVersion(modelId: string, version: string) {
        const response = await apiClient.get<ModelVersion>(`/models/${modelId}/versions/${version}`);
        return response.data;
    },

    /**
     * Compare two model versions
     */
    async compareModels(modelId1: string, modelId2: string) {
        const response = await apiClient.get<{
            model1: ModelComparison;
            model2: ModelComparison;
            winner: string;
            metrics: Array<{ name: string; model1: number; model2: number; delta: number }>;
        }>('/models/compare', {
            params: { modelId1, modelId2 },
        });
        return response.data;
    },

    /**
     * Deploy a model version
     */
    async deployModel(modelId: string, version: string) {
        const response = await apiClient.post(`/models/${modelId}/deploy`, { version });
        return response.data;
    },

    /**
     * Rollback to previous version
     */
    async rollbackModel(modelId: string, targetVersion: string) {
        const response = await apiClient.post(`/models/${modelId}/rollback`, { targetVersion });
        return response.data;
    },

    /**
     * Get test suite for model
     */
    async getTestSuite(modelId: string) {
        const response = await apiClient.get<TestCase[]>(`/models/${modelId}/tests`);
        return response.data;
    },

    /**
     * Run model test suite
     */
    async runTestSuite(modelId: string, testIds?: string[]) {
        const response = await apiClient.post<{
            runId: string;
            status: TestStatus;
            results: TestCase[];
        }>(`/models/${modelId}/tests/run`, { testIds });
        return response.data;
    },

    /**
     * Get A/B test results
     */
    async getABTestResults(modelId: string) {
        const response = await apiClient.get<ABTestResult[]>(`/models/${modelId}/ab-tests`);
        return response.data;
    },

    /**
     * Start A/B test between two models
     */
    async startABTest(modelId1: string, modelId2: string, durationHours: number) {
        const response = await apiClient.post<{ testId: string; status: string }>(
            '/models/ab-tests/start',
            { modelId1, modelId2, durationHours }
        );
        return response.data;
    },
};
