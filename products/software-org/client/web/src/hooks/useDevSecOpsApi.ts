/**
 * DevSecOps API Hooks
 *
 * React Query hooks for fetching DevSecOps items and stage health.
 *
 * @doc.type hooks
 * @doc.purpose DevSecOps API integration
 */

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/services/api';
import type { DevSecOpsItem } from '@/lib/devsecops/mapWorkItemToDevSecOpsItem';
import type { StageHealth } from '@/types/devsecops';

// ============================================================================
// API Functions
// ============================================================================

interface FetchDevSecOpsItemsParams {
    stage?: string;
    tenantId?: string;
    status?: string;
    priority?: string;
}

interface DevSecOpsItemsResponse {
    success: boolean;
    data: DevSecOpsItem[];
    meta: {
        total: number;
        filters: FetchDevSecOpsItemsParams;
    };
}

interface StageHealthResponse {
    success: boolean;
    data: StageHealth;
}

async function fetchDevSecOpsItems(params: FetchDevSecOpsItemsParams): Promise<DevSecOpsItem[]> {
    const searchParams = new URLSearchParams();
    if (params.stage) searchParams.append('stage', params.stage);
    if (params.tenantId) searchParams.append('tenantId', params.tenantId);
    if (params.status) searchParams.append('status', params.status);
    if (params.priority) searchParams.append('priority', params.priority);

    const response = await apiClient.get<DevSecOpsItemsResponse>(
        `/devsecops/items?${searchParams.toString()}`
    );
    return response.data.data;
}

async function fetchStageHealth(stageKey: string, tenantId?: string): Promise<StageHealth> {
    const searchParams = new URLSearchParams();
    if (tenantId) searchParams.append('tenantId', tenantId);

    const response = await apiClient.get<StageHealthResponse>(
        `/devsecops/stage-health/${stageKey}?${searchParams.toString()}`
    );
    return response.data.data;
}

// ============================================================================
// React Query Hooks
// ============================================================================

/**
 * Fetch DevSecOps items with optional filtering
 */
export function useDevSecOpsItems(params: FetchDevSecOpsItemsParams) {
    return useQuery({
        queryKey: ['devsecops-items', params],
        queryFn: () => fetchDevSecOpsItems(params),
        enabled: !!params.stage, // Only fetch if stage is provided
        staleTime: 30 * 1000, // 30 seconds
    });
}

/**
 * Fetch stage health metrics
 */
export function useStageHealth(stageKey: string, tenantId?: string) {
    return useQuery({
        queryKey: ['stage-health', stageKey, tenantId],
        queryFn: () => fetchStageHealth(stageKey, tenantId),
        enabled: !!stageKey,
        staleTime: 30 * 1000, // 30 seconds
    });
}
