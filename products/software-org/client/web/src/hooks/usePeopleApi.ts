/**
 * People API Hooks
 *
 * React Query hooks for the PEOPLE section:
 * - Performance Reviews
 * - Growth Plans (ICs)
 *
 * @doc.type hooks
 * @doc.purpose People API integration
 * @doc.layer product
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/services/api';

// =============================================================================
// Types - Performance Reviews
// =============================================================================

export interface PerformanceReview {
    id: string;
    employeeId: string;
    reviewerId: string;
    period: string; // e.g., "Q4-2024"
    status: 'draft' | 'in_progress' | 'submitted' | 'completed';
    overallRating: number; // 1-5
    ratings?: Record<string, number>;
    goals?: Array<{
        id: string;
        title: string;
        status: 'not_started' | 'in_progress' | 'completed';
        progress: number;
    }>;
    feedback?: string;
    strengths?: string[];
    improvements?: string[];
    metadata?: Record<string, unknown>;
    employee?: {
        id: string;
        name: string | null;
        email?: string;
    };
    reviewer?: {
        id: string;
        name: string | null;
    };
    createdAt: string;
    updatedAt: string;
    submittedAt?: string | null;
    completedAt?: string | null;
}

export interface CreateReviewRequest {
    employeeId: string;
    reviewerId?: string;
    period: string;
    status?: string;
    overallRating?: number;
    metadata?: Record<string, unknown>;
}

export interface UpdateReviewRequest {
    overallRating?: number;
    ratings?: Record<string, number>;
    goals?: Array<{
        id: string;
        title: string;
        status: string;
        progress: number;
    }>;
    feedback?: string;
    strengths?: string[];
    improvements?: string[];
    metadata?: Record<string, unknown>;
}

// =============================================================================
// Types - Growth Plans
// =============================================================================

export interface GrowthPlan {
    id: string;
    userId: string;
    title: string;
    description: string | null;
    period: string; // e.g., "2024"
    status: 'active' | 'completed' | 'archived';
    goals: Array<{
        id: string;
        title: string;
        description?: string;
        targetDate?: string;
        status: 'not_started' | 'in_progress' | 'completed';
        progress: number;
    }>;
    skills: Array<{
        name: string;
        currentLevel: number;
        targetLevel: number;
        progress: number;
    }>;
    resources: Array<{
        type: 'course' | 'book' | 'mentorship' | 'project' | 'other';
        title: string;
        url?: string;
        completed: boolean;
    }>;
    progress: number; // 0-100 overall progress
    createdAt: string;
    updatedAt: string;
    completedAt?: string | null;
}

export interface CreateGrowthPlanRequest {
    userId: string;
    title: string;
    description?: string;
    period: string;
    goals?: GrowthPlan['goals'];
    skills?: GrowthPlan['skills'];
    resources?: GrowthPlan['resources'];
}

export interface UpdateGrowthPlanRequest {
    title?: string;
    description?: string;
    status?: GrowthPlan['status'];
    goals?: GrowthPlan['goals'];
    skills?: GrowthPlan['skills'];
    resources?: GrowthPlan['resources'];
    progress?: number;
}

// =============================================================================
// Query Keys
// =============================================================================

export const peopleQueryKeys = {
    reviews: {
        all: ['performance-reviews'] as const,
        list: (params?: { employeeId?: string; reviewerId?: string; period?: string; status?: string }) => 
            [...peopleQueryKeys.reviews.all, 'list', params] as const,
        detail: (id: string) => [...peopleQueryKeys.reviews.all, 'detail', id] as const,
        due: () => [...peopleQueryKeys.reviews.all, 'due'] as const,
        myReviews: (userId: string) => [...peopleQueryKeys.reviews.all, 'my', userId] as const,
    },
    growth: {
        all: ['growth-plans'] as const,
        list: (userId?: string) => [...peopleQueryKeys.growth.all, 'list', userId] as const,
        detail: (id: string) => [...peopleQueryKeys.growth.all, 'detail', id] as const,
        active: (userId: string) => [...peopleQueryKeys.growth.all, 'active', userId] as const,
    },
};

// =============================================================================
// Performance Review Hooks
// =============================================================================

/**
 * Fetch performance reviews
 */
export function usePerformanceReviews(params?: {
    employeeId?: string;
    reviewerId?: string;
    period?: string;
    status?: string;
    tenantId?: string;
}) {
    return useQuery({
        queryKey: peopleQueryKeys.reviews.list(params),
        queryFn: async () => {
            const searchParams = new URLSearchParams();
            if (params?.employeeId) searchParams.append('employeeId', params.employeeId);
            if (params?.reviewerId) searchParams.append('reviewerId', params.reviewerId);
            if (params?.period) searchParams.append('period', params.period);
            if (params?.status) searchParams.append('status', params.status);
            if (params?.tenantId) searchParams.append('tenantId', params.tenantId);
            
            const response = await apiClient.get(`/performance-reviews?${searchParams}`);
            return (response.data?.data || response.data || []) as PerformanceReview[];
        },
    });
}

/**
 * Fetch single performance review
 */
export function usePerformanceReview(id: string) {
    return useQuery({
        queryKey: peopleQueryKeys.reviews.detail(id),
        queryFn: async () => {
            const response = await apiClient.get(`/performance-reviews/${id}`);
            return (response.data?.data || response.data) as PerformanceReview;
        },
        enabled: !!id,
    });
}

/**
 * Fetch upcoming/due reviews
 */
export function useDueReviews(tenantId?: string) {
    return useQuery({
        queryKey: [...peopleQueryKeys.reviews.due(), tenantId],
        queryFn: async () => {
            const params = new URLSearchParams();
            if (tenantId) params.append('tenantId', tenantId);
            const response = await apiClient.get(`/performance-reviews/due?${params}`);
            return (response.data?.data || response.data || []) as PerformanceReview[];
        },
    });
}

/**
 * Create performance review
 */
export function useCreatePerformanceReview() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (data: CreateReviewRequest) => {
            const response = await apiClient.post('/performance-reviews', data);
            return response.data as PerformanceReview;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: peopleQueryKeys.reviews.all });
        },
    });
}

/**
 * Update performance review
 */
export function useUpdatePerformanceReview() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ id, data }: { id: string; data: UpdateReviewRequest }) => {
            const response = await apiClient.patch(`/performance-reviews/${id}`, data);
            return response.data as PerformanceReview;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: peopleQueryKeys.reviews.detail(variables.id) });
            queryClient.invalidateQueries({ queryKey: peopleQueryKeys.reviews.all });
        },
    });
}

/**
 * Submit review for approval
 */
export function useSubmitReview() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ id, data }: { id: string; data?: UpdateReviewRequest }) => {
            const response = await apiClient.post(`/performance-reviews/${id}/submit`, data || {});
            return response.data as PerformanceReview;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: peopleQueryKeys.reviews.detail(variables.id) });
            queryClient.invalidateQueries({ queryKey: peopleQueryKeys.reviews.all });
        },
    });
}

/**
 * Generate AI insights for review
 */
export function useGenerateReviewInsights() {
    return useMutation({
        mutationFn: async (reviewId: string) => {
            const response = await apiClient.post(`/performance-reviews/${reviewId}/ai-insights`);
            return response.data as {
                strengths: string[];
                improvements: string[];
                summary: string;
                recommendations: string[];
            };
        },
    });
}

// =============================================================================
// Growth Plan Hooks
// =============================================================================

/**
 * Fetch growth plans for a user
 */
export function useGrowthPlans(userId?: string, status?: string) {
    return useQuery({
        queryKey: [...peopleQueryKeys.growth.list(userId), status],
        queryFn: async () => {
            const params = new URLSearchParams();
            if (userId) params.append('userId', userId);
            if (status) params.append('status', status);
            const response = await apiClient.get(`/growth-plans?${params}`);
            return (response.data?.data || response.data || []) as GrowthPlan[];
        },
        enabled: !!userId,
    });
}

/**
 * Fetch single growth plan
 */
export function useGrowthPlan(id: string) {
    return useQuery({
        queryKey: peopleQueryKeys.growth.detail(id),
        queryFn: async () => {
            const response = await apiClient.get(`/growth-plans/${id}`);
            return (response.data?.data || response.data) as GrowthPlan;
        },
        enabled: !!id,
    });
}

/**
 * Create growth plan
 */
export function useCreateGrowthPlan() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (data: CreateGrowthPlanRequest) => {
            const response = await apiClient.post('/growth-plans', data);
            return response.data as GrowthPlan;
        },
        onSuccess: (created) => {
            queryClient.invalidateQueries({ queryKey: peopleQueryKeys.growth.list(created.userId) });
        },
    });
}

/**
 * Update growth plan
 */
export function useUpdateGrowthPlan() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({ id, data }: { id: string; data: UpdateGrowthPlanRequest }) => {
            const response = await apiClient.put(`/growth-plans/${id}`, data);
            return response.data as GrowthPlan;
        },
        onSuccess: (updated, variables) => {
            queryClient.invalidateQueries({ queryKey: peopleQueryKeys.growth.detail(variables.id) });
            queryClient.invalidateQueries({ queryKey: peopleQueryKeys.growth.list(updated.userId) });
        },
    });
}

/**
 * Complete growth plan
 */
export function useCompleteGrowthPlan() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (id: string) => {
            const response = await apiClient.post(`/growth-plans/${id}/complete`);
            return response.data as GrowthPlan;
        },
        onSuccess: (updated) => {
            queryClient.invalidateQueries({ queryKey: peopleQueryKeys.growth.detail(updated.id) });
            queryClient.invalidateQueries({ queryKey: peopleQueryKeys.growth.list(updated.userId) });
        },
    });
}
