/**
 * Guardian API Service Layer
 * 
 * Composable service layer that provides typed access to all Guardian backend APIs.
 * Reuses the centralized apiClient for HTTP communication.
 * 
 * REUSES: lib/api.ts (apiClient singleton)
 * NO DUPLICATION: All services share the same HTTP client and types
 */

import { apiClient } from '../lib/api';

// ============================================================================
// Shared Types (matching backend schemas)
// ============================================================================

export interface ApiResponse<T> {
    success: boolean;
    data?: T;
    error?: string;
    message?: string;
}

export interface PaginatedResponse<T> extends ApiResponse<T[]> {
    count: number;
    page?: number;
    limit?: number;
}

// ============================================================================
// Child Types
// ============================================================================

export interface Child {
    id: string;
    name: string;
    age: number;
    avatar_url?: string;
    is_active: boolean;
    created_at: string;
    updated_at: string;
}

export interface CreateChildInput {
    name: string;
    age: number;
    avatar_url?: string;
}

export interface UpdateChildInput {
    name?: string;
    age?: number;
    avatar_url?: string;
    is_active?: boolean;
}

// ============================================================================
// Device Types
// ============================================================================

export interface Device {
    id: string;
    child_id?: string;
    device_type: 'desktop' | 'mobile' | 'extension';
    device_name: string;
    device_fingerprint?: string;
    is_active: boolean;
    last_seen_at?: string;
    created_at: string;
    updated_at: string;
}

export interface RegisterDeviceInput {
    child_id?: string;
    device_type: 'desktop' | 'mobile' | 'extension';
    device_name: string;
    device_fingerprint?: string;
}

export type ImmediateAction =
    | 'lock_device'
    | 'unlock_device'
    | 'sound_alarm'
    | 'request_location'
    | 'force_sync';

export interface DeviceActionParams {
    reason?: string;
    message?: string;
    duration_minutes?: number;
    duration_seconds?: number;
}

// ============================================================================
// Policy Types
// ============================================================================

export interface Policy {
    id: string;
    name: string;
    policy_type: string;
    priority: number;
    enabled: boolean;
    config: Record<string, unknown>;
    child_id?: string;
    device_id?: string;
    created_at: string;
    updated_at: string;
}

export interface CreatePolicyInput {
    name: string;
    policy_type: string;
    priority?: number;
    enabled?: boolean;
    config: Record<string, unknown>;
    child_id?: string;
    device_id?: string;
}

export interface UpdatePolicyInput {
    name?: string;
    priority?: number;
    enabled?: boolean;
    config?: Record<string, unknown>;
}

// ============================================================================
// Agent Sync Types (matching backend AgentSyncPayload)
// ============================================================================

export interface SyncPolicy {
    id: string;
    name: string;
    policy_type: string;
    priority: number;
    enabled: boolean;
    config: Record<string, unknown>;
    scope: 'device' | 'child' | 'global';
}

export interface GuardianCommand {
    schema_version: number;
    command_id: string;
    kind: string;
    action: string;
    target?: {
        device_id?: string;
        child_id?: string;
    };
    params?: Record<string, unknown>;
    issued_by: {
        actor_type: 'parent' | 'child' | 'system';
        user_id?: string;
    };
    created_at: string;
    expires_at?: string;
}

export interface AgentSyncPayload {
    schema_version: number;
    device_id: string;
    child_id?: string;
    synced_at: string;
    sync_version: string;
    policies: {
        version: string;
        items: SyncPolicy[];
        count: number;
    };
    commands: {
        items: GuardianCommand[];
        count: number;
    };
    next_sync_seconds: number;
}

// ============================================================================
// Child Request Types
// ============================================================================

export interface ChildRequest {
    id: string;
    child_id: string;
    type: 'extend_session' | 'unblock';
    status: 'pending' | 'approved' | 'denied';
    resource?: string;
    minutes_requested?: number;
    reason?: string;
    created_at: string;
    decided_at?: string;
}

export interface RequestDecisionInput {
    type: 'extend_session' | 'unblock';
    decision: 'approved' | 'denied';
    minutes_granted?: number;
    device_id?: string;
    session_id?: string;
}

// ============================================================================
// Children Service
// ============================================================================

export const childrenService = {
    async list(): Promise<Child[]> {
        const response = await apiClient.get<ApiResponse<Child[]>>('/children');
        return response.data || [];
    },

    async get(id: string): Promise<Child | null> {
        const response = await apiClient.get<ApiResponse<Child>>(`/children/${id}`);
        return response.data || null;
    },

    async create(input: CreateChildInput): Promise<Child> {
        const response = await apiClient.post<ApiResponse<Child>>('/children', input as unknown as Record<string, unknown>);
        if (!response.data) throw new Error(response.error || 'Failed to create child');
        return response.data;
    },

    async update(id: string, input: UpdateChildInput): Promise<Child> {
        const response = await apiClient.put<ApiResponse<Child>>(`/children/${id}`, input as unknown as Record<string, unknown>);
        if (!response.data) throw new Error(response.error || 'Failed to update child');
        return response.data;
    },

    async delete(id: string): Promise<void> {
        await apiClient.delete(`/children/${id}`);
    },

    async getRequests(childId: string): Promise<ChildRequest[]> {
        const response = await apiClient.get<ApiResponse<ChildRequest[]>>(`/children/${childId}/requests`);
        return response.data || [];
    },

    async decideRequest(childId: string, requestId: string, decision: RequestDecisionInput): Promise<void> {
        await apiClient.post(`/children/${childId}/requests/${requestId}/decision`, decision as unknown as Record<string, unknown>);
    },
};

// ============================================================================
// Devices Service
// ============================================================================

export const devicesService = {
    async list(): Promise<Device[]> {
        const response = await apiClient.get<ApiResponse<Device[]>>('/devices');
        return response.data || [];
    },

    async get(id: string): Promise<Device | null> {
        const response = await apiClient.get<ApiResponse<Device>>(`/devices/${id}`);
        return response.data || null;
    },

    async register(input: RegisterDeviceInput): Promise<Device> {
        const response = await apiClient.post<ApiResponse<Device>>('/devices/register', input as unknown as Record<string, unknown>);
        if (!response.data) throw new Error(response.error || 'Failed to register device');
        return response.data;
    },

    async update(id: string, input: Partial<RegisterDeviceInput>): Promise<Device> {
        const response = await apiClient.put<ApiResponse<Device>>(`/devices/${id}`, input as unknown as Record<string, unknown>);
        if (!response.data) throw new Error(response.error || 'Failed to update device');
        return response.data;
    },

    async delete(id: string): Promise<void> {
        await apiClient.delete(`/devices/${id}`);
    },

    async toggle(id: string, isActive: boolean): Promise<Device> {
        const response = await apiClient.put<ApiResponse<Device>>(`/devices/${id}/toggle`, { is_active: isActive });
        if (!response.data) throw new Error(response.error || 'Failed to toggle device');
        return response.data;
    },

    async pairWithChild(deviceId: string, childId: string): Promise<Device> {
        const response = await apiClient.post<ApiResponse<Device>>(`/devices/${deviceId}/pair`, { child_id: childId });
        if (!response.data) throw new Error(response.error || 'Failed to pair device');
        return response.data;
    },

    async unpair(deviceId: string): Promise<Device> {
        const response = await apiClient.post<ApiResponse<Device>>(`/devices/${deviceId}/unpair`);
        if (!response.data) throw new Error(response.error || 'Failed to unpair device');
        return response.data;
    },

    // Device actions (immediate commands)
    async sendAction(deviceId: string, action: ImmediateAction, params?: DeviceActionParams): Promise<{ command_id: string }> {
        const response = await apiClient.post<ApiResponse<{ command_id: string }>>(`/devices/${deviceId}/actions`, {
            action,
            params: params || {},
        });
        if (!response.data) throw new Error(response.error || 'Failed to send action');
        return response.data;
    },

    // Get pending commands for device
    async getCommands(deviceId: string): Promise<GuardianCommand[]> {
        const response = await apiClient.get<ApiResponse<GuardianCommand[]>>(`/devices/${deviceId}/commands`);
        return response.data || [];
    },

    // Get unified sync payload (policies + commands)
    async getSync(deviceId: string): Promise<AgentSyncPayload | null> {
        const response = await apiClient.get<ApiResponse<AgentSyncPayload>>(`/devices/${deviceId}/sync`);
        return response.data || null;
    },

    // Pairing code management
    async generatePairingCode(childId: string): Promise<{ code: string; expires_at: string }> {
        const response = await apiClient.post<ApiResponse<{ code: string; expires_at: string }>>('/devices/pairing/generate', { child_id: childId });
        if (!response.data) throw new Error(response.error || 'Failed to generate pairing code');
        return response.data;
    },

    async getPairingCode(childId: string): Promise<{ code: string; expires_at: string } | null> {
        try {
            const response = await apiClient.get<ApiResponse<{ code: string; expires_at: string }>>(`/devices/pairing/${childId}`);
            return response.data || null;
        } catch {
            return null;
        }
    },
};

// ============================================================================
// Policies Service
// ============================================================================

export const policiesService = {
    async list(filters?: { child_id?: string; device_id?: string; policy_type?: string }): Promise<Policy[]> {
        const params = new URLSearchParams();
        if (filters?.child_id) params.set('child_id', filters.child_id);
        if (filters?.device_id) params.set('device_id', filters.device_id);
        if (filters?.policy_type) params.set('policy_type', filters.policy_type);

        const url = params.toString() ? `/policies?${params}` : '/policies';
        const response = await apiClient.get<ApiResponse<Policy[]>>(url);
        return response.data || [];
    },

    async get(id: string): Promise<Policy | null> {
        const response = await apiClient.get<ApiResponse<Policy>>(`/policies/${id}`);
        return response.data || null;
    },

    async create(input: CreatePolicyInput): Promise<Policy> {
        const response = await apiClient.post<ApiResponse<Policy>>('/policies', input as unknown as Record<string, unknown>);
        if (!response.data) throw new Error(response.error || 'Failed to create policy');
        return response.data;
    },

    async update(id: string, input: UpdatePolicyInput): Promise<Policy> {
        const response = await apiClient.put<ApiResponse<Policy>>(`/policies/${id}`, input as unknown as Record<string, unknown>);
        if (!response.data) throw new Error(response.error || 'Failed to update policy');
        return response.data;
    },

    async delete(id: string): Promise<void> {
        await apiClient.delete(`/policies/${id}`);
    },

    async toggle(id: string, enabled: boolean): Promise<Policy> {
        const response = await apiClient.put<ApiResponse<Policy>>(`/policies/${id}`, { enabled });
        if (!response.data) throw new Error(response.error || 'Failed to toggle policy');
        return response.data;
    },

    async bulkToggle(policyIds: string[], enabled: boolean): Promise<{ count: number }> {
        const response = await apiClient.post<ApiResponse<{ count: number }>>('/policies/bulk/toggle', {
            policy_ids: policyIds,
            enabled,
        });
        return { count: response.data?.count || 0 };
    },

    // Get policies for a specific device (for agent sync preview)
    async getForDevice(deviceId: string): Promise<SyncPolicy[]> {
        const response = await apiClient.get<ApiResponse<SyncPolicy[]>>(`/policies/device/${deviceId}`);
        return response.data || [];
    },
};

// ============================================================================
// Reports Service
// ============================================================================

export interface UsageReport {
    date: string;
    total_screen_time_minutes: number;
    app_usage: Array<{
        app_name: string;
        category: string;
        minutes: number;
    }>;
    blocks_count: number;
}

export interface ReportFilters {
    child_id?: string;
    start_date?: string;
    end_date?: string;
    days?: number;
}

export const reportsService = {
    async getUsageReport(filters?: ReportFilters): Promise<UsageReport[]> {
        const params = new URLSearchParams();
        if (filters?.child_id) params.set('child_id', filters.child_id);
        if (filters?.start_date) params.set('start_date', filters.start_date);
        if (filters?.end_date) params.set('end_date', filters.end_date);
        if (filters?.days) params.set('days', filters.days.toString());

        const url = params.toString() ? `/reports/usage?${params}` : '/reports/usage';
        const response = await apiClient.get<ApiResponse<UsageReport[]>>(url);
        return response.data || [];
    },

    async exportCsv(filters?: ReportFilters): Promise<Blob> {
        const params = new URLSearchParams();
        if (filters?.child_id) params.set('child_id', filters.child_id);
        if (filters?.start_date) params.set('start_date', filters.start_date);
        if (filters?.end_date) params.set('end_date', filters.end_date);

        const url = params.toString() ? `/reports/export?${params}` : '/reports/export';
        const response = await apiClient.get<Blob>(url);
        return response;
    },
};

// ============================================================================
// Risk Assessment Types
// ============================================================================

export interface RiskDimensions {
    digitalOveruse: number;
    scheduleCompliance: number;
    contentRisk: number;
    socialRisk: number;
}

export interface RiskFactor {
    dimension: keyof RiskDimensions;
    factor: string;
    severity: 'low' | 'medium' | 'high';
    description: string;
    value?: number;
}

export interface ChildRiskAssessment {
    childId: string;
    childName: string;
    overallScore: number;
    riskBucket: 'low' | 'medium' | 'high' | 'critical';
    dimensions: RiskDimensions;
    factors: RiskFactor[];
    insights: string[];
    recommendations: string[];
    assessedAt: string;
    dataWindow: {
        start: string;
        end: string;
    };
}

export interface RiskOverview {
    assessments: ChildRiskAssessment[];
    summary: {
        totalChildren: number;
        highRiskCount: number;
        avgScore: number;
    };
    dataWindow: {
        days: number;
        end: string;
    };
}

export const riskService = {
    async getOverview(days: number = 7): Promise<RiskOverview> {
        const response = await apiClient.get<ApiResponse<RiskOverview>>(`/risk/overview?days=${days}`);
        return response.data!;
    },

    async getChildRisk(childId: string, days: number = 7): Promise<ChildRiskAssessment> {
        const response = await apiClient.get<ApiResponse<ChildRiskAssessment>>(`/risk/children/${childId}?days=${days}`);
        return response.data!;
    },

    async getChildFactors(childId: string, days: number = 7, severity?: 'low' | 'medium' | 'high'): Promise<{
        childId: string;
        childName: string;
        overallScore: number;
        riskBucket: string;
        factors: RiskFactor[];
        factorCount: number;
    }> {
        const params = new URLSearchParams({ days: days.toString() });
        if (severity) params.set('severity', severity);
        const response = await apiClient.get<ApiResponse<any>>(`/risk/children/${childId}/factors?${params}`);
        return response.data!;
    },

    async getChildRecommendations(childId: string, days: number = 7): Promise<{
        childId: string;
        childName: string;
        riskBucket: string;
        insights: string[];
        recommendations: string[];
    }> {
        const response = await apiClient.get<ApiResponse<any>>(`/risk/children/${childId}/recommendations?days=${days}`);
        return response.data!;
    },
};

// ============================================================================
// Notification Types
// ============================================================================

export type NotificationPriority = 'low' | 'medium' | 'high' | 'critical';
export type NotificationType =
    | 'block_event'
    | 'risk_alert'
    | 'child_request'
    | 'request_decision'
    | 'usage_alert'
    | 'device_offline'
    | 'policy_violation'
    | 'system';

export interface Notification {
    id: string;
    user_id: string;
    child_id?: string;
    device_id?: string;
    type: NotificationType;
    priority: NotificationPriority;
    title: string;
    message: string;
    metadata?: Record<string, unknown>;
    is_read: boolean;
    created_at: string;
    read_at?: string;
}

export interface NotificationFilters {
    child_id?: string;
    type?: NotificationType;
    priority?: NotificationPriority;
    is_read?: boolean;
    limit?: number;
    offset?: number;
}

export interface UnreadCount {
    total: number;
    high: number;
    critical: number;
}

export const notificationService = {
    async getNotifications(filters?: NotificationFilters): Promise<{
        notifications: Notification[];
        count: number;
        unread: UnreadCount;
    }> {
        const params = new URLSearchParams();
        if (filters?.child_id) params.set('child_id', filters.child_id);
        if (filters?.type) params.set('type', filters.type);
        if (filters?.priority) params.set('priority', filters.priority);
        if (filters?.is_read !== undefined) params.set('is_read', String(filters.is_read));
        if (filters?.limit) params.set('limit', String(filters.limit));
        if (filters?.offset) params.set('offset', String(filters.offset));

        const url = params.toString() ? `/notifications?${params}` : '/notifications';
        const response = await apiClient.get<any>(url);
        return {
            notifications: response.data || [],
            count: response.count,
            unread: response.unread,
        };
    },

    async getUnreadCount(childId?: string): Promise<UnreadCount> {
        const url = childId ? `/notifications/unread-count?child_id=${childId}` : '/notifications/unread-count';
        const response = await apiClient.get<ApiResponse<UnreadCount>>(url);
        return response.data!;
    },

    async markAsRead(notificationId: string): Promise<void> {
        await apiClient.post(`/notifications/${notificationId}/read`);
    },

    async markAllAsRead(childId?: string): Promise<number> {
        const response = await apiClient.post<ApiResponse<{ marked_read: number }>>('/notifications/read-all', {
            child_id: childId,
        });
        return response.data?.marked_read || 0;
    },

    async getDigest(hours: number = 24): Promise<{
        totalNotifications: number;
        byType: Record<NotificationType, number>;
        byPriority: Record<NotificationPriority, number>;
        highlights: Notification[];
        period: { hours: number; since: string };
    }> {
        const response = await apiClient.get<ApiResponse<any>>(`/notifications/digest?hours=${hours}`);
        return response.data!;
    },
};

// ============================================================================
// Export all services as a unified API
// ============================================================================

export const api = {
    children: childrenService,
    devices: devicesService,
    policies: policiesService,
    reports: reportsService,
    risk: riskService,
    notifications: notificationService,
};

export default api;
