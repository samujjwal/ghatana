import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/services/api';

/**
 * Operate API Hooks
 *
 * React Query hooks for Operate module:
 * - Dashboard stats and activity
 * - Incident management
 * - Work queue HITL approvals
 * - Live activity feed
 * - Task management
 * - AI Insights
 *
 * @doc.type hooks
 * @doc.purpose Operate API integration
 * @doc.layer product
 */

// =============================================================================
// Types
// =============================================================================

export interface DashboardStats {
    activeIncidents: number;
    pendingApprovals: number;
    workflowsRunning: number;
    systemHealth: number;
    deploymentFrequency: number;
    avgLeadTime: number;
    mttr: number;
    changeFailureRate: number;
}

export interface Activity {
    id: string;
    type: 'workflow' | 'alert' | 'approval' | 'agent' | 'incident' | 'deployment';
    message: string;
    status: 'success' | 'warning' | 'pending' | 'error' | 'critical';
    time: string;
    timestamp: string;
    actor?: string;
    metadata?: Record<string, unknown>;
}

export interface Incident {
    id: string;
    tenantId: string;
    title: string;
    description: string;
    severity: 'critical' | 'high' | 'medium' | 'low';
    status: 'active' | 'investigating' | 'mitigating' | 'resolved';
    assignee: string | null;
    assigneeId: string | null;
    affectedServices: string[];
    affectedServicesIds: string[];
    rootCause: string | null;
    mitigation: string | null;
    timeline: Array<{
        timestamp: string;
        event: string;
        actor: string;
    }>;
    relatedWorkflows: string[];
    relatedMetrics: string[];
    createdAt: string;
    updatedAt: string;
}

export interface QueueItem {
    id: string;
    tenantId: string;
    type: 'approval' | 'hitl' | 'review' | 'access';
    title: string;
    description: string;
    priority: 'high' | 'medium' | 'low';
    requestedBy: string;
    requestedById: string;
    context: Record<string, unknown>;
    dueIn?: string;
    createdAt: string;
}

export interface Task {
    id: string;
    title: string;
    description: string;
    status: 'pending' | 'in-progress' | 'completed';
    priority: 'low' | 'medium' | 'high';
    assignee?: {
        id: string;
        name: string;
        avatar?: string;
    };
    dueDate: string;
    tags: string[];
    createdAt: string;
}

export interface InsightMessage {
    id: string;
    role: 'user' | 'assistant';
    content: string;
    timestamp: string;
    sources?: Array<{
        type: 'metric' | 'log' | 'incident' | 'workflow';
        title: string;
        reference: string;
    }>;
}

export interface InsightQuery {
    query: string;
    context?: {
        tenantId?: string;
        timeRange?: string;
        focusArea?: string;
    };
}

// =============================================================================
// Query Keys
// =============================================================================

export const operateQueryKeys = {
    dashboard: {
        all: ['operate', 'dashboard'] as const,
        stats: (tenantId: string) => [...operateQueryKeys.dashboard.all, 'stats', tenantId] as const,
        activity: (tenantId: string) => [...operateQueryKeys.dashboard.all, 'activity', tenantId] as const,
    },
    incidents: {
        all: ['operate', 'incidents'] as const,
        list: (tenantId: string, status?: string, severity?: string) => 
            [...operateQueryKeys.incidents.all, tenantId, status, severity] as const,
        detail: (id: string) => [...operateQueryKeys.incidents.all, 'detail', id] as const,
    },
    queue: {
        all: ['operate', 'queue'] as const,
        list: (tenantId: string, type?: string, priority?: string) => 
            [...operateQueryKeys.queue.all, tenantId, type, priority] as const,
        detail: (id: string) => [...operateQueryKeys.queue.all, 'detail', id] as const,
    },
    liveFeed: {
        all: ['operate', 'live-feed'] as const,
        stream: (tenantId: string) => [...operateQueryKeys.liveFeed.all, 'stream', tenantId] as const,
    },
    tasks: {
        all: ['operate', 'tasks'] as const,
        list: (tenantId: string, status?: string, priority?: string) => 
            [...operateQueryKeys.tasks.all, tenantId, status, priority] as const,
        detail: (id: string) => [...operateQueryKeys.tasks.all, 'detail', id] as const,
        myTasks: (userId: string) => [...operateQueryKeys.tasks.all, 'my', userId] as const,
    },
    insights: {
        all: ['operate', 'insights'] as const,
        conversation: (sessionId: string) => [...operateQueryKeys.insights.all, 'conversation', sessionId] as const,
    },
};

// =============================================================================
// Dashboard Hooks
// =============================================================================

/**
 * Fetch dashboard stats
 */
export function useDashboardStats(tenantId: string) {
    return useQuery({
        queryKey: ['operate', 'dashboard', 'stats', tenantId],
        queryFn: async () => {
            const response = await apiClient.get(`/operate/dashboard/stats?tenantId=${tenantId}`);
            return response.data as { data: DashboardStats };
        },
        enabled: !!tenantId,
    });
}

/**
 * Fetch recent activity
 */
export function useRecentActivity(tenantId: string) {
    return useQuery({
        queryKey: ['operate', 'dashboard', 'activity', tenantId],
        queryFn: async () => {
            const response = await apiClient.get(`/operate/dashboard/activity?tenantId=${tenantId}`);
            return response.data as { data: Activity[] };
        },
        enabled: !!tenantId,
    });
}

// =============================================================================
// Incident Hooks
// =============================================================================

/**
 * Fetch incidents with optional filters
 */
export function useIncidents(tenantId: string, status?: string, severity?: string) {
    return useQuery({
        queryKey: ['operate', 'incidents', tenantId, status, severity],
        queryFn: async () => {
            const params = new URLSearchParams({ tenantId });
            if (status) params.append('status', status);
            if (severity) params.append('severity', severity);
            
            const response = await apiClient.get(`/operate/incidents?${params}`);
            return response.data as { data: Incident[]; pagination: { page: number; pageSize: number; total: number } };
        },
        enabled: !!tenantId,
    });
}

/**
 * Fetch a single incident by ID
 */
export function useIncident(incidentId: string, tenantId: string) {
    return useQuery({
        queryKey: ['operate', 'incidents', incidentId],
        queryFn: async () => {
            const response = await apiClient.get(`/operate/incidents/${incidentId}?tenantId=${tenantId}`);
            return response.data.data as Incident;
        },
        enabled: !!incidentId && !!tenantId,
    });
}

/**
 * Acknowledge an incident
 */
export function useAcknowledgeIncident() {
    const queryClient = useQueryClient();
    
    return useMutation({
        mutationFn: async ({ incidentId, tenantId, userId }: { incidentId: string; tenantId: string; userId: string }) => {
            const response = await apiClient.post(`/operate/incidents/${incidentId}/acknowledge`, {
                tenantId,
                userId,
            });
            return response.data;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ['operate', 'incidents', variables.incidentId] });
            queryClient.invalidateQueries({ queryKey: ['operate', 'incidents', variables.tenantId] });
        },
    });
}

/**
 * Assign an incident
 */
export function useAssignIncident() {
    const queryClient = useQueryClient();
    
    return useMutation({
        mutationFn: async ({ incidentId, tenantId, assigneeId }: { incidentId: string; tenantId: string; assigneeId: string }) => {
            const response = await apiClient.post(`/operate/incidents/${incidentId}/assign`, {
                tenantId,
                assigneeId,
            });
            return response.data;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ['operate', 'incidents', variables.incidentId] });
            queryClient.invalidateQueries({ queryKey: ['operate', 'incidents', variables.tenantId] });
        },
    });
}

/**
 * Update incident status
 */
export function useUpdateIncidentStatus() {
    const queryClient = useQueryClient();
    
    return useMutation({
        mutationFn: async ({
            incidentId,
            tenantId,
            status,
            summary,
        }: {
            incidentId: string;
            tenantId: string;
            status: string;
            summary?: string;
        }) => {
            const response = await apiClient.post(`/operate/incidents/${incidentId}/status`, {
                tenantId,
                status,
                summary,
            });
            return response.data;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ['operate', 'incidents', variables.incidentId] });
            queryClient.invalidateQueries({ queryKey: ['operate', 'incidents', variables.tenantId] });
            queryClient.invalidateQueries({ queryKey: ['operate', 'dashboard'] });
        },
    });
}

// =============================================================================
// Queue Hooks
// =============================================================================

/**
 * Fetch queue items with optional filters
 */
export function useQueueItems(tenantId: string, type?: string, priority?: string) {
    return useQuery({
        queryKey: ['operate', 'queue', tenantId, type, priority],
        queryFn: async () => {
            const params = new URLSearchParams({ tenantId });
            if (type) params.append('type', type);
            if (priority) params.append('priority', priority);
            
            const response = await apiClient.get(`/operate/queue?${params}`);
            return response.data as { data: QueueItem[]; pagination: { page: number; pageSize: number; total: number } };
        },
        enabled: !!tenantId,
    });
}

/**
 * Fetch a single queue item by ID
 */
export function useQueueItem(itemId: string, tenantId: string) {
    return useQuery({
        queryKey: ['operate', 'queue', itemId],
        queryFn: async () => {
            const response = await apiClient.get(`/operate/queue/${itemId}?tenantId=${tenantId}`);
            return response.data.data as QueueItem;
        },
        enabled: !!itemId && !!tenantId,
    });
}

/**
 * Approve a queue item
 */
export function useApproveQueueItem() {
    const queryClient = useQueryClient();
    
    return useMutation({
        mutationFn: async ({
            itemId,
            tenantId,
            userId,
            comment,
        }: {
            itemId: string;
            tenantId: string;
            userId: string;
            comment?: string;
        }) => {
            const response = await apiClient.post(`/operate/queue/${itemId}/approve`, {
                tenantId,
                userId,
                comment,
            });
            return response.data;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ['operate', 'queue', variables.itemId] });
            queryClient.invalidateQueries({ queryKey: ['operate', 'queue', variables.tenantId] });
            queryClient.invalidateQueries({ queryKey: ['operate', 'dashboard'] });
        },
    });
}

/**
 * Reject a queue item
 */
export function useRejectQueueItem() {
    const queryClient = useQueryClient();
    
    return useMutation({
        mutationFn: async ({
            itemId,
            tenantId,
            userId,
            reason,
        }: {
            itemId: string;
            tenantId: string;
            userId: string;
            reason: string;
        }) => {
            const response = await apiClient.post(`/operate/queue/${itemId}/reject`, {
                tenantId,
                userId,
                reason,
            });
            return response.data;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: ['operate', 'queue', variables.itemId] });
            queryClient.invalidateQueries({ queryKey: ['operate', 'queue', variables.tenantId] });
            queryClient.invalidateQueries({ queryKey: ['operate', 'dashboard'] });
        },
    });
}

// =============================================================================
// Live Feed Hooks
// =============================================================================

export interface LiveFeedItem {
  id: string;
  type: 'commit' | 'alert' | 'task' | 'discussion' | 'deployment';
  content: string;
  actor: {
    id: string;
    name: string;
    avatar?: string;
  };
  timestamp: string;
  action: string;
  target: string;
  metadata?: Record<string, any>;
}

/**
 * Fetch live activity stream
 */
export function useLiveFeed(tenantId?: string) {
    return useQuery({
        queryKey: ['operate', 'live-feed', tenantId],
        queryFn: async () => {
            const response = await apiClient.get('/operate/live-feed');
            return (response.data?.data || []) as LiveFeedItem[];
        },
        refetchInterval: 10000,
    });
}

// =============================================================================
// Task Hooks
// =============================================================================

/**
 * Fetch tasks with optional filters
 */
export function useTasks(tenantId?: string, status?: string, priority?: string) {
    return useQuery({
        queryKey: ['operate', 'tasks', tenantId, status, priority],
        queryFn: async () => {
            const response = await apiClient.get('/operate/tasks');
            return (response.data?.data || []) as Task[];
        },
    });
}

/**
 * Fetch single task
 */
export function useTask(taskId: string, tenantId: string) {
    return useQuery({
        queryKey: operateQueryKeys.tasks.detail(taskId),
        queryFn: async () => {
            const response = await apiClient.get(`/operate/queue/${taskId}?tenantId=${tenantId}`);
            const item = response.data?.data || response.data;
            return {
                ...item,
                status: 'pending',
                actions: [
                    { id: 'approve', label: 'Approve', type: 'approve', primary: true },
                    { id: 'reject', label: 'Reject', type: 'reject' },
                ],
            } as Task;
        },
        enabled: !!taskId && !!tenantId,
    });
}

/**
 * Execute task action (approve, reject, defer)
 */
export function useExecuteTaskAction() {
    const queryClient = useQueryClient();
    
    return useMutation({
        mutationFn: async ({
            taskId,
            tenantId,
            actionId,
            userId,
            comment,
        }: {
            taskId: string;
            tenantId: string;
            actionId: string;
            userId: string;
            comment?: string;
        }) => {
            const endpoint = actionId === 'approve' ? 'approve' : 'reject';
            const response = await apiClient.post(`/operate/queue/${taskId}/${endpoint}`, {
                tenantId,
                userId,
                comment,
                reason: comment,
            });
            return response.data;
        },
        onSuccess: (_, variables) => {
            queryClient.invalidateQueries({ queryKey: operateQueryKeys.tasks.all });
            queryClient.invalidateQueries({ queryKey: operateQueryKeys.queue.all });
            queryClient.invalidateQueries({ queryKey: operateQueryKeys.dashboard.all });
        },
    });
}

export function useTaskUpdate() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async ({ taskId, updates }: { taskId: string; updates: Partial<Task> }) => {
            const response = await apiClient.post(`/operate/tasks/${taskId}/status`, { updates });
            return response.data;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['operate', 'tasks'] });
        },
    });
}

export function useTaskComplete() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (taskId: string) => {
            const response = await apiClient.post(`/operate/tasks/${taskId}/complete`);
            return response.data;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['operate', 'tasks'] });
        },
    });
}

// =============================================================================
// AI Insights Hooks
// =============================================================================

export interface InsightConversation {
  id: string;
  messages: InsightMessage[];
  context: Record<string, any>;
}

export interface InsightMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: string;
  metadata?: {
    chartType?: 'bar' | 'line' | 'pie';
    data?: any;
    sources?: string[];
  };
}

export function useInsightConversation() {
    return useQuery({
        queryKey: ['operate', 'insights', 'conversation'],
        queryFn: async () => {
            const response = await apiClient.get('/operate/insights/conversation');
            return response.data?.data as InsightConversation;
        },
    });
}

export function useSendInsightMessage() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: async (message: string) => {
            const response = await apiClient.post('/operate/insights/message', { message });
            return response.data;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['operate', 'insights', 'conversation'] });
        },
    });
}

/**
 * Send query to AI insights engine
 */
export function useAskInsights() {
    return useMutation({
        mutationFn: async (query: InsightQuery) => {
            const response = await apiClient.post('/operate/insights/query', query);
            return response.data as InsightMessage;
        },
    });
}

/**
 * Get insights suggestions based on current data
 */
export function useInsightsSuggestions(tenantId: string) {
    return useQuery({
        queryKey: [...operateQueryKeys.insights.all, 'suggestions', tenantId],
        queryFn: async () => {
            const response = await apiClient.get(`/operate/insights/suggestions?tenantId=${tenantId}`);
            return (response.data?.data || response.data || []) as string[];
        },
        enabled: !!tenantId,
    });
}
