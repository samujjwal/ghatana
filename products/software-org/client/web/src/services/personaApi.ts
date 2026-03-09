/**
 * Persona API Client
 *
 * <p><b>Purpose</b><br>
 * API client for fetching persona-specific data: pending tasks, activities,
 * metrics, and pinned features. Supports real-time polling with React Query.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { personaApi } from '@/services/personaApi';
 *
 * const tasks = await personaApi.getPendingTasks();
 * const activities = await personaApi.getRecentActivities(5);
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose API client for persona data
 * @doc.layer product
 * @doc.pattern API Client
 */

import type { UserProfile, Activity, PendingTasks, WorkSession, PersonaWorkItem } from '@/state/jotai/atoms';
import type { Feature } from '@/shared/components/FeatureGrid';
import type { UserRole } from '@/config/personaConfig';
import type {
    Persona,
    GrowthGoal,
    PlannedAbsence,
    ExecutionContext,
    DevSecOpsPhaseId,
} from '@/shared/types/org';

/**
 * Base API configuration
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';
const DEFAULT_TIMEOUT = 10000; // 10 seconds

/**
 * API Error class for structured error handling
 */
export class ApiError extends Error {
    constructor(
        message: string,
        public status: number,
        public code?: string
    ) {
        super(message);
        this.name = 'ApiError';
    }
}

/**
 * Fetch wrapper with timeout and error handling
 */
async function fetchWithTimeout(url: string, options: RequestInit = {}, timeout = DEFAULT_TIMEOUT): Promise<Response> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);

    try {
        const response = await fetch(url, {
            ...options,
            signal: controller.signal,
            headers: {
                'Content-Type': 'application/json',
                ...options.headers,
            },
        });

        clearTimeout(timeoutId);

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new ApiError(
                errorData.message || `HTTP ${response.status}: ${response.statusText}`,
                response.status,
                errorData.code
            );
        }

        return response;
    } catch (error) {
        clearTimeout(timeoutId);

        if (error instanceof ApiError) {
            throw error;
        }

        if (error instanceof Error && error.name === 'AbortError') {
            throw new ApiError('Request timeout', 408, 'TIMEOUT');
        }

        throw new ApiError('Network error', 0, 'NETWORK_ERROR');
    }
}

/**
 * Persona API Client
 */
export const personaApi = {
    /**
     * Fetches current user's pending tasks count.
     *
     * @returns Promise<PendingTasks> - Task counts by type
     */
    async getPendingTasks(): Promise<PendingTasks> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/persona/pending-tasks`);
        return response.json();
    },

    /**
     * Fetches recent user activities.
     *
     * @param limit - Maximum number of activities to return (default: 5)
     * @returns Promise<Activity[]> - Recent activities
     */
    async getRecentActivities(limit = 5): Promise<Activity[]> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/persona/activities?limit=${limit}`);
        const data: Array<Omit<Activity, 'timestamp'> & { timestamp: string }> = await response.json();

        // Convert timestamp strings to Date objects
        return data.map((activity) => ({
            ...activity,
            timestamp: new Date(activity.timestamp),
        }));
    },

    /**
     * Fetches metrics data for a specific role.
     *
     * @param role - User role (admin, lead, engineer, viewer)
     * @returns Promise<Record<string, number>> - Metric values keyed by dataKey
     */
    async getMetrics(role: UserRole): Promise<Record<string, number>> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/persona/metrics?role=${role}`);
        return response.json();
    },

    /**
     * Fetches user's pinned features.
     *
     * @returns Promise<Feature[]> - Pinned features
     */
    async getPinnedFeatures(): Promise<Feature[]> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/persona/pinned-features`);
        return response.json();
    },

    /**
     * Updates user's pinned features.
     *
     * @param features - Array of features to pin
     * @returns Promise<void>
     */
    async updatePinnedFeatures(features: Feature[]): Promise<void> {
        await fetchWithTimeout(`${API_BASE_URL}/persona/pinned-features`, {
            method: 'PUT',
            body: JSON.stringify(features),
        });
    },

    /**
     * Pins a feature by title.
     *
     * @param featureTitle - Title of feature to pin
     * @returns Promise<void>
     */
    async pinFeature(featureTitle: string): Promise<void> {
        await fetchWithTimeout(`${API_BASE_URL}/persona/pinned-features/${encodeURIComponent(featureTitle)}`, {
            method: 'POST',
        });
    },

    /**
     * Unpins a feature by title.
     *
     * @param featureTitle - Title of feature to unpin
     * @returns Promise<void>
     */
    async unpinFeature(featureTitle: string): Promise<void> {
        await fetchWithTimeout(`${API_BASE_URL}/persona/pinned-features/${encodeURIComponent(featureTitle)}`, {
            method: 'DELETE',
        });
    },

    /**
     * Fetches current user profile.
     *
     * @returns Promise<UserProfile> - User profile with role and permissions
     */
    async getUserProfile(): Promise<UserProfile> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/auth/profile`);
        return response.json();
    },

    /**
     * Marks a pending task as completed.
     *
     * @param taskType - Type of task (hitl, security, workflow, model)
     * @param taskId - Task ID
     * @returns Promise<void>
     */
    async completeTask(taskType: 'hitl' | 'security' | 'workflow' | 'model', taskId: string): Promise<void> {
        await fetchWithTimeout(`${API_BASE_URL}/persona/tasks/${taskType}/${taskId}/complete`, {
            method: 'POST',
        });
    },

    // =========================================================================
    // UNIFIED PERSONA API (Human/Agent Agnostic)
    // =========================================================================

    /**
     * Fetches the current persona entity with full details.
     *
     * @returns Promise<Persona> - Unified persona with capacity, availability, growth
     */
    async getCurrentPersona(): Promise<Persona> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/persona/current`);
        return response.json();
    },

    /**
     * Fetches work items assigned to the current persona.
     *
     * @param phase - Optional filter by DevSecOps phase
     * @returns Promise<PersonaWorkItem[]> - Work items for persona
     */
    async getPersonaWorkItems(phase?: DevSecOpsPhaseId): Promise<PersonaWorkItem[]> {
        const url = phase
            ? `${API_BASE_URL}/persona/work-items?phase=${phase}`
            : `${API_BASE_URL}/persona/work-items`;
        const response = await fetchWithTimeout(url);
        return response.json();
    },

    /**
     * Starts a work session for the current persona.
     *
     * @param workItemId - Optional work item to focus on
     * @returns Promise<WorkSession> - New work session
     */
    async startWorkSession(workItemId?: string): Promise<WorkSession> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/persona/work-session/start`, {
            method: 'POST',
            body: JSON.stringify({ workItemId }),
        });
        return response.json();
    },

    /**
     * Ends the current work session.
     *
     * @param sessionId - Session ID to end
     * @returns Promise<WorkSession> - Completed session with metrics
     */
    async endWorkSession(sessionId: string): Promise<WorkSession> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/persona/work-session/${sessionId}/end`, {
            method: 'POST',
        });
        return response.json();
    },

    /**
     * Fetches growth goals for the current persona.
     *
     * @returns Promise<GrowthGoal[]> - Active and completed goals
     */
    async getGrowthGoals(): Promise<GrowthGoal[]> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/persona/growth/goals`);
        return response.json();
    },

    /**
     * Updates progress on a growth goal.
     *
     * @param goalId - Goal ID
     * @param progress - New progress value (0-100)
     * @returns Promise<GrowthGoal> - Updated goal
     */
    async updateGrowthGoalProgress(goalId: string, progress: number): Promise<GrowthGoal> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/persona/growth/goals/${goalId}/progress`, {
            method: 'PATCH',
            body: JSON.stringify({ progress }),
        });
        return response.json();
    },

    /**
     * Fetches planned absences (PTO for humans, maintenance for agents).
     *
     * @returns Promise<PlannedAbsence[]> - Planned absences
     */
    async getPlannedAbsences(): Promise<PlannedAbsence[]> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/persona/absences`);
        return response.json();
    },

    /**
     * Requests a new planned absence.
     *
     * @param absence - Absence details
     * @returns Promise<PlannedAbsence> - Created absence request
     */
    async requestAbsence(absence: Omit<PlannedAbsence, 'id' | 'status'>): Promise<PlannedAbsence> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/persona/absences`, {
            method: 'POST',
            body: JSON.stringify(absence),
        });
        return response.json();
    },

    /**
     * Fetches execution context for a work item.
     * Includes all tools and integrations needed to complete the work.
     *
     * @param workItemId - Work item ID
     * @returns Promise<ExecutionContext> - Execution context with tools
     */
    async getExecutionContext(workItemId: string): Promise<ExecutionContext> {
        const response = await fetchWithTimeout(`${API_BASE_URL}/persona/work-items/${workItemId}/execution-context`);
        return response.json();
    },

    /**
     * Updates persona availability status.
     *
     * @param status - New availability status
     * @param message - Optional status message
     * @returns Promise<void>
     */
    async updateAvailabilityStatus(
        status: 'available' | 'busy' | 'away' | 'offline' | 'maintenance',
        message?: string
    ): Promise<void> {
        await fetchWithTimeout(`${API_BASE_URL}/persona/availability`, {
            method: 'PATCH',
            body: JSON.stringify({ status, message }),
        });
    },
};

/**
 * Mock API implementation for development/testing
 * Falls back to mock data when API endpoints are not available
 */
export const mockPersonaApi = {
    async getPendingTasks(): Promise<PendingTasks> {
        const { getMockPendingTasks } = await import('@/config/mockPersonaData');
        return Promise.resolve(getMockPendingTasks());
    },

    async getRecentActivities(limit = 5): Promise<Activity[]> {
        const { getMockRecentActivities } = await import('@/config/mockPersonaData');
        return Promise.resolve(getMockRecentActivities(limit));
    },

    async getMetrics(role: UserRole): Promise<Record<string, number>> {
        const { getMockMetricData } = await import('@/config/mockPersonaData');
        return Promise.resolve(getMockMetricData(role));
    },

    async getPinnedFeatures(): Promise<Feature[]> {
        const { getMockPinnedFeatures } = await import('@/config/mockPersonaData');
        return Promise.resolve(getMockPinnedFeatures(4));
    },

    async updatePinnedFeatures(features: Feature[]): Promise<void> {
        console.log('Mock: Updated pinned features', features);
        return Promise.resolve();
    },

    async pinFeature(featureTitle: string): Promise<void> {
        console.log('Mock: Pinned feature', featureTitle);
        return Promise.resolve();
    },

    async unpinFeature(featureTitle: string): Promise<void> {
        console.log('Mock: Unpinned feature', featureTitle);
        return Promise.resolve();
    },

    async getUserProfile(): Promise<UserProfile> {
        const { getMockUserProfile } = await import('@/config/mockPersonaData');
        return Promise.resolve(getMockUserProfile('engineer'));
    },

    async completeTask(taskType: string, taskId: string): Promise<void> {
        console.log('Mock: Completed task', { taskType, taskId });
        return Promise.resolve();
    },

    // =========================================================================
    // UNIFIED PERSONA MOCK API (Human/Agent Agnostic)
    // =========================================================================

    async getCurrentPersona(): Promise<Persona> {
        const { getMockPersona } = await import('@/config/mockPersonaData');
        return Promise.resolve(getMockPersona('engineer'));
    },

    async getPersonaWorkItems(phase?: DevSecOpsPhaseId): Promise<PersonaWorkItem[]> {
        const { getMockPersonaWorkItems } = await import('@/config/mockPersonaData');
        return Promise.resolve(getMockPersonaWorkItems(phase));
    },

    async startWorkSession(workItemId?: string): Promise<WorkSession> {
        return Promise.resolve({
            id: `session-${Date.now()}`,
            startedAt: new Date().toISOString(),
            activeWorkItemId: workItemId || null,
            currentPhase: 'build',
            focusMode: false,
            breakTimeMinutes: 0,
            productiveTimeMinutes: 0,
        });
    },

    async endWorkSession(sessionId: string): Promise<WorkSession> {
        return Promise.resolve({
            id: sessionId,
            startedAt: new Date(Date.now() - 3600000).toISOString(),
            activeWorkItemId: null,
            currentPhase: null,
            focusMode: false,
            breakTimeMinutes: 15,
            productiveTimeMinutes: 45,
        });
    },

    async getGrowthGoals(): Promise<GrowthGoal[]> {
        const { getMockGrowthGoals } = await import('@/config/mockPersonaData');
        return Promise.resolve(getMockGrowthGoals());
    },

    async updateGrowthGoalProgress(goalId: string, progress: number): Promise<GrowthGoal> {
        console.log('Mock: Updated goal progress', { goalId, progress });
        return Promise.resolve({
            id: goalId,
            title: 'Mock Goal',
            description: 'Mock goal description',
            targetDate: '2025-12-31',
            progress,
            status: progress >= 100 ? 'completed' : 'in-progress',
            relatedCapabilities: [],
            milestones: [],
        });
    },

    async getPlannedAbsences(): Promise<PlannedAbsence[]> {
        const { getMockPlannedAbsences } = await import('@/config/mockPersonaData');
        return Promise.resolve(getMockPlannedAbsences());
    },

    async requestAbsence(absence: Omit<PlannedAbsence, 'id' | 'status'>): Promise<PlannedAbsence> {
        console.log('Mock: Requested absence', absence);
        return Promise.resolve({
            id: `absence-${Date.now()}`,
            ...absence,
            status: 'pending',
        });
    },

    async getExecutionContext(workItemId: string): Promise<ExecutionContext> {
        const { getMockExecutionContext } = await import('@/config/mockPersonaData');
        return Promise.resolve(getMockExecutionContext(workItemId));
    },

    async updateAvailabilityStatus(
        status: 'available' | 'busy' | 'away' | 'offline' | 'maintenance',
        message?: string
    ): Promise<void> {
        console.log('Mock: Updated availability', { status, message });
        return Promise.resolve();
    },
};

/**
 * API client that automatically falls back to mock data in development
 */
export const api = import.meta.env.MODE === 'development' && !import.meta.env.VITE_USE_REAL_API
    ? mockPersonaApi
    : personaApi;
