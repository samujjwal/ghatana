/**
 * Growth Plans API Client
 *
 * <p><b>Purpose</b><br>
 * Typed client for the Personal Growth Plan journey.
 *
 * <p><b>Endpoints</b><br>
 * - GET /growth-plans
 * - POST /growth-plans
 * - GET /growth-plans/:id
 * - PUT /growth-plans/:id
 * - POST /growth-plans/:id/complete
 *
 * @doc.type service
 * @doc.purpose Growth plans API client
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import { apiClient } from './index';

export type JsonValue =
    | null
    | boolean
    | number
    | string
    | { [key: string]: JsonValue }
    | JsonValue[];

export interface GrowthPlanResponse {
    id: string;
    userId: string;
    title: string;
    description: string | null;
    period: string;
    status: string;
    goals: JsonValue;
    skills: JsonValue;
    resources: JsonValue;
    progress: number;
    createdAt: string;
    updatedAt: string;
    completedAt: string | null;
}

export interface ListGrowthPlansResponse {
    data: GrowthPlanResponse[];
    total: number;
    limit: number;
    offset: number;
}

export interface CreateGrowthPlanRequest {
    userId: string;
    title: string;
    description?: string;
    period: string;
    goals?: JsonValue;
    skills?: JsonValue;
    resources?: JsonValue;
    progress?: number;
}

export interface UpdateGrowthPlanRequest {
    title?: string;
    description?: string;
    period?: string;
    status?: string;
    goals?: JsonValue;
    skills?: JsonValue;
    resources?: JsonValue;
    progress?: number;
}

export const growthPlansApi = {
    async listGrowthPlans(params: {
        userId: string;
        status?: string;
        period?: string;
        limit?: number;
        offset?: number;
    }): Promise<ListGrowthPlansResponse> {
        const response = await apiClient.get<ListGrowthPlansResponse>('/growth-plans', { params });
        return response.data;
    },

    async createGrowthPlan(body: CreateGrowthPlanRequest): Promise<GrowthPlanResponse> {
        const response = await apiClient.post<GrowthPlanResponse>('/growth-plans', body);
        return response.data;
    },

    async getGrowthPlan(id: string): Promise<GrowthPlanResponse> {
        const response = await apiClient.get<GrowthPlanResponse>(`/growth-plans/${id}`);
        return response.data;
    },

    async updateGrowthPlan(id: string, body: UpdateGrowthPlanRequest): Promise<GrowthPlanResponse> {
        const response = await apiClient.put<GrowthPlanResponse>(`/growth-plans/${id}`, body);
        return response.data;
    },

    async completeGrowthPlan(id: string): Promise<GrowthPlanResponse> {
        const response = await apiClient.post<GrowthPlanResponse>(`/growth-plans/${id}/complete`);
        return response.data;
    },
};
