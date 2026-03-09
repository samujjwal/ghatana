/**
 * Agent Framework Integration for Data-Cloud
 *
 * Bridges @ghatana/agent-framework with Data-Cloud's Brain system.
 * Provides hooks and providers for Brain agent visualization and management.
 *
 * @doc.type module
 * @doc.purpose Agent framework integration for Data-Cloud Brain
 * @doc.layer frontend
 * @doc.pattern Integration
 */

import * as React from 'react';
import { createContext, useContext, useMemo, useCallback, type ReactNode } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

// ============================================
// BASE TYPES (Local definitions to avoid import issues)
// ============================================

/** Agent status */
export type AgentStatus = 'active' | 'idle' | 'paused' | 'error' | 'terminated';

/** Agent descriptor */
export interface AgentDescriptor {
    id: string;
    name: string;
    description?: string;
    type: 'reactive' | 'proactive' | 'hybrid';
    capabilities: string[];
    version: string;
}

/** Agent configuration */
export interface AgentConfig {
    [key: string]: unknown;
}

/** Agent metrics */
export interface AgentMetrics {
    tasksCompleted: number;
    successRate: number;
    avgResponseTime: number;
    memoryUsage: number;
}

/** Base agent instance */
export interface AgentInstance {
    id: string;
    descriptor: AgentDescriptor;
    config: AgentConfig;
    status: AgentStatus;
    createdAt: string;
    lastActive: string;
    metrics?: AgentMetrics;
}

/** Intervention priority */
export type InterventionPriority = 'low' | 'medium' | 'high' | 'critical';

/** Intervention request */
export interface InterventionRequest {
    id: string;
    agentId: string;
    type: 'action' | 'configuration' | 'escalation';
    title: string;
    description: string;
    reasoning?: string;
    proposedAction: Record<string, unknown>;
    confidence: number;
    priority: InterventionPriority;
    status: 'pending' | 'approved' | 'rejected' | 'expired';
    createdAt: string;
    expiresAt?: string;
}

/** Intervention decision */
export interface InterventionDecision {
    approved: boolean;
    modifications?: Record<string, unknown>;
    feedback?: string;
}

/** Agent state */
export type AgentState =
    | 'idle'
    | 'initializing'
    | 'ready'
    | 'thinking'
    | 'executing'
    | 'waiting'
    | 'paused'
    | 'error'
    | 'terminated';

/** Memory entry */
export interface MemoryEntry {
    id: string;
    key: string;
    value: unknown;
    type: 'fact' | 'experience' | 'rule' | 'context';
    timestamp: string;
    importance: number;
    source?: string;
}

/** Agent memory */
export interface AgentMemory {
    shortTerm: MemoryEntry[];
    longTerm: MemoryEntry[];
    working: MemoryEntry[];
}

/** Agent interaction */
export interface AgentInteraction {
    id: string;
    sourceAgentId: string;
    targetAgentId: string;
    type: 'request' | 'response' | 'broadcast' | 'delegation';
    message: {
        intent: string;
        payload: Record<string, unknown>;
    };
    timestamp: string;
    status: 'pending' | 'delivered' | 'acknowledged' | 'failed';
}

// ============================================
// DATA-CLOUD SPECIFIC TYPES
// ============================================

/**
 * Brain Agent - Data-Cloud's intelligent agent type
 * Extends the generic AgentInstance with Brain-specific properties
 */
export interface BrainAgent extends AgentInstance {
    /** Brain subsystem this agent belongs to */
    subsystem: 'spotlight' | 'autonomy' | 'learning' | 'governance' | 'optimization';
    /** Current agent state */
    state: AgentState;
    /** Current confidence level (0-1) */
    confidence: number;
    /** Number of entities this agent is monitoring */
    entityCount: number;
    /** Last decision made */
    lastDecision?: {
        type: string;
        timestamp: string;
        outcome: 'success' | 'pending' | 'rejected';
    };
}

/**
 * Brain Intervention - Data-Cloud specific intervention requests
 */
export interface BrainIntervention extends InterventionRequest {
    /** Affected entity IDs */
    affectedEntities?: string[];
    /** Predicted impact */
    impact?: {
        scope: 'low' | 'medium' | 'high';
        description: string;
    };
    /** Rollback available */
    rollbackAvailable?: boolean;
}

// ============================================
// API SERVICE
// ============================================

const BRAIN_API_BASE = '/api/brain';

async function fetchBrainAgents(): Promise<BrainAgent[]> {
    const response = await fetch(`${BRAIN_API_BASE}/agents`);
    if (!response.ok) throw new Error('Failed to fetch brain agents');
    return response.json();
}

async function fetchBrainAgent(agentId: string): Promise<BrainAgent> {
    const response = await fetch(`${BRAIN_API_BASE}/agents/${agentId}`);
    if (!response.ok) throw new Error('Failed to fetch brain agent');
    return response.json();
}

async function updateAgentState(
    agentId: string,
    action: 'pause' | 'resume' | 'terminate' | 'restart'
): Promise<BrainAgent> {
    const response = await fetch(`${BRAIN_API_BASE}/agents/${agentId}/${action}`, {
        method: 'POST',
    });
    if (!response.ok) throw new Error(`Failed to ${action} agent`);
    return response.json();
}

async function fetchInterventions(): Promise<BrainIntervention[]> {
    const response = await fetch(`${BRAIN_API_BASE}/interventions`);
    if (!response.ok) throw new Error('Failed to fetch interventions');
    return response.json();
}

async function resolveIntervention(
    interventionId: string,
    decision: 'approve' | 'reject',
    feedback?: string
): Promise<void> {
    const response = await fetch(`${BRAIN_API_BASE}/interventions/${interventionId}/resolve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ decision, feedback }),
    });
    if (!response.ok) throw new Error('Failed to resolve intervention');
}

async function fetchAgentMemory(agentId: string): Promise<AgentMemory> {
    const response = await fetch(`${BRAIN_API_BASE}/agents/${agentId}/memory`);
    if (!response.ok) throw new Error('Failed to fetch agent memory');
    return response.json();
}

async function fetchAgentInteractions(): Promise<AgentInteraction[]> {
    const response = await fetch(`${BRAIN_API_BASE}/interactions`);
    if (!response.ok) throw new Error('Failed to fetch agent interactions');
    return response.json();
}

// ============================================
// HOOKS
// ============================================

/**
 * Hook for fetching and managing Brain agents
 *
 * @example
 * ```tsx
 * const { agents, isLoading, pauseAgent, resumeAgent } = useBrainAgents();
 *
 * // With the AgentDashboard component
 * <AgentDashboard
 *   agents={agents}
 *   type="brain"
 *   onAction={(id, action) => {
 *     if (action === 'pause') pauseAgent(id);
 *     if (action === 'resume') resumeAgent(id);
 *   }}
 * />
 * ```
 */
export function useBrainAgents(options?: { subsystem?: BrainAgent['subsystem'] }) {
    const queryClient = useQueryClient();

    const {
        data: agents = [],
        isLoading,
        error,
        refetch,
    } = useQuery({
        queryKey: ['brain', 'agents', options?.subsystem],
        queryFn: async () => {
            const allAgents = await fetchBrainAgents();
            if (options?.subsystem) {
                return allAgents.filter((a) => a.subsystem === options.subsystem);
            }
            return allAgents;
        },
        refetchInterval: 5000, // Poll every 5 seconds for real-time updates
    });

    const pauseMutation = useMutation({
        mutationFn: (agentId: string) => updateAgentState(agentId, 'pause'),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['brain', 'agents'] });
        },
    });

    const resumeMutation = useMutation({
        mutationFn: (agentId: string) => updateAgentState(agentId, 'resume'),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['brain', 'agents'] });
        },
    });

    const terminateMutation = useMutation({
        mutationFn: (agentId: string) => updateAgentState(agentId, 'terminate'),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['brain', 'agents'] });
        },
    });

    const restartMutation = useMutation({
        mutationFn: (agentId: string) => updateAgentState(agentId, 'restart'),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['brain', 'agents'] });
        },
    });

    // Convert BrainAgent to AgentInstance for compatibility with @ghatana/agent-framework
    const agentsAsInstances: AgentInstance[] = useMemo(() => agents, [agents]);

    // Stats
    const stats = useMemo(() => {
        const byState: Record<AgentState, number> = {
            idle: 0,
            initializing: 0,
            ready: 0,
            thinking: 0,
            executing: 0,
            waiting: 0,
            paused: 0,
            error: 0,
            terminated: 0,
        };
        const bySubsystem: Record<string, number> = {};
        let totalConfidence = 0;

        agents.forEach((agent) => {
            byState[agent.state]++;
            bySubsystem[agent.subsystem] = (bySubsystem[agent.subsystem] ?? 0) + 1;
            totalConfidence += agent.confidence;
        });

        return {
            total: agents.length,
            active: byState.thinking + byState.executing,
            ready: byState.ready,
            paused: byState.paused,
            error: byState.error,
            avgConfidence: agents.length > 0 ? totalConfidence / agents.length : 0,
            byState,
            bySubsystem,
        };
    }, [agents]);

    return {
        agents: agentsAsInstances,
        brainAgents: agents,
        stats,
        isLoading,
        error,
        refetch,
        pauseAgent: pauseMutation.mutateAsync,
        resumeAgent: resumeMutation.mutateAsync,
        terminateAgent: terminateMutation.mutateAsync,
        restartAgent: restartMutation.mutateAsync,
        isPausing: pauseMutation.isPending,
        isResuming: resumeMutation.isPending,
    };
}

/**
 * Hook for Brain intervention requests (human-in-the-loop)
 *
 * @example
 * ```tsx
 * const { interventions, approveIntervention, rejectIntervention } = useBrainInterventions();
 *
 * <AgentInterventionConsole
 *   requests={interventions}
 *   onApprove={(id, feedback) => approveIntervention({ id, feedback })}
 *   onReject={(id, reason) => rejectIntervention({ id, reason })}
 * />
 * ```
 */
export function useBrainInterventions() {
    const queryClient = useQueryClient();

    const {
        data: interventions = [],
        isLoading,
        error,
        refetch,
    } = useQuery({
        queryKey: ['brain', 'interventions'],
        queryFn: fetchInterventions,
        refetchInterval: 3000, // Poll more frequently for interventions
    });

    const approveMutation = useMutation({
        mutationFn: ({ id, feedback }: { id: string; feedback?: string }) =>
            resolveIntervention(id, 'approve', feedback),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['brain', 'interventions'] });
        },
    });

    const rejectMutation = useMutation({
        mutationFn: ({ id, reason }: { id: string; reason: string }) =>
            resolveIntervention(id, 'reject', reason),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['brain', 'interventions'] });
        },
    });

    const bulkApproveMutation = useMutation({
        mutationFn: async (ids: string[]) => {
            await Promise.all(ids.map((id) => resolveIntervention(id, 'approve')));
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['brain', 'interventions'] });
        },
    });

    const bulkRejectMutation = useMutation({
        mutationFn: async ({ ids, reason }: { ids: string[]; reason: string }) => {
            await Promise.all(ids.map((id) => resolveIntervention(id, 'reject', reason)));
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['brain', 'interventions'] });
        },
    });

    // Convert to InterventionRequest for compatibility
    const interventionsAsRequests: InterventionRequest[] = useMemo(() => interventions, [interventions]);

    // Stats
    const stats = useMemo(() => {
        const pending = interventions.filter((i) => i.status === 'pending');
        const critical = pending.filter((i) => i.priority === 'critical');
        const high = pending.filter((i) => i.priority === 'high');
        return {
            total: interventions.length,
            pending: pending.length,
            critical: critical.length,
            high: high.length,
        };
    }, [interventions]);

    return {
        interventions: interventionsAsRequests,
        brainInterventions: interventions,
        stats,
        isLoading,
        error,
        refetch,
        approveIntervention: approveMutation.mutateAsync,
        rejectIntervention: rejectMutation.mutateAsync,
        bulkApprove: bulkApproveMutation.mutateAsync,
        bulkReject: bulkRejectMutation.mutateAsync,
        isApproving: approveMutation.isPending,
        isRejecting: rejectMutation.isPending,
    };
}

/**
 * Hook for viewing agent memory
 *
 * @example
 * ```tsx
 * const { memory, isLoading } = useBrainMemory(agentId);
 *
 * <AgentMemoryViewer
 *   memory={memory}
 *   agentName={agent.descriptor.name}
 * />
 * ```
 */
export function useBrainMemory(agentId: string | undefined) {
    const {
        data: memory,
        isLoading,
        error,
        refetch,
    } = useQuery({
        queryKey: ['brain', 'agents', agentId, 'memory'],
        queryFn: () => fetchAgentMemory(agentId!),
        enabled: !!agentId,
        refetchInterval: 10000,
    });

    const defaultMemory: AgentMemory = {
        shortTerm: [],
        longTerm: [],
        working: [],
    };

    return {
        memory: memory ?? defaultMemory,
        isLoading,
        error,
        refetch,
    };
}

/**
 * Hook for agent interaction network
 *
 * @example
 * ```tsx
 * const { interactions, agents } = useBrainInteractions();
 *
 * <AgentInteractionNetwork
 *   agents={agents}
 *   interactions={interactions}
 *   realtime
 * />
 * ```
 */
export function useBrainInteractions() {
    const { agents } = useBrainAgents();

    const {
        data: interactions = [],
        isLoading,
        error,
        refetch,
    } = useQuery({
        queryKey: ['brain', 'interactions'],
        queryFn: fetchAgentInteractions,
        refetchInterval: 5000,
    });

    return {
        agents,
        interactions,
        isLoading,
        error,
        refetch,
    };
}

// ============================================
// CONTEXT & PROVIDER
// ============================================

interface DataCloudAgentContextValue {
    agents: AgentInstance[];
    interventions: InterventionRequest[];
    isLoading: boolean;
    pauseAgent: (id: string) => Promise<BrainAgent>;
    resumeAgent: (id: string) => Promise<BrainAgent>;
    approveIntervention: (args: { id: string; feedback?: string }) => Promise<void>;
    rejectIntervention: (args: { id: string; reason: string }) => Promise<void>;
}

const DataCloudAgentContext = createContext<DataCloudAgentContextValue | null>(null);

/**
 * Provider for Data-Cloud agent integration
 *
 * @example
 * ```tsx
 * function App() {
 *   return (
 *     <DataCloudAgentProvider>
 *       <BrainDashboard />
 *     </DataCloudAgentProvider>
 *   );
 * }
 * ```
 */
export function DataCloudAgentProvider({ children }: { children: ReactNode }) {
    const agentHook = useBrainAgents();
    const interventionHook = useBrainInterventions();

    const value: DataCloudAgentContextValue = useMemo(
        () => ({
            agents: agentHook.agents,
            interventions: interventionHook.interventions,
            isLoading: agentHook.isLoading || interventionHook.isLoading,
            pauseAgent: agentHook.pauseAgent,
            resumeAgent: agentHook.resumeAgent,
            approveIntervention: interventionHook.approveIntervention,
            rejectIntervention: interventionHook.rejectIntervention,
        }),
        [agentHook, interventionHook]
    );

    return (
        <DataCloudAgentContext.Provider value={value}>
            {children}
        </DataCloudAgentContext.Provider>
    );
}

/**
 * Hook to access Data-Cloud agent context
 */
export function useDataCloudAgentContext() {
    const context = useContext(DataCloudAgentContext);
    if (!context) {
        throw new Error('useDataCloudAgentContext must be used within DataCloudAgentProvider');
    }
    return context;
}
