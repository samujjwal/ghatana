/**
 * Computed View Hook
 * 
 * Implements the Combinatorial View Composition strategy from CANVAS_UX_DESIGN_SPEC.md.
 * Filters and transforms canvas nodes based on multiple selectors:
 * - Project scope
 * - Phase/Zone relevance (viewport culling)
 * - Persona filter
 * - Role-based permissions (RBAC)
 * - Validation/Gate status overlay
 * - Task assignment ("My Work" mode)
 * 
 * Formula: FinalView = BaseCanvas + (ProjectContext ∩ PhaseContext) + PersonaFilter + RolePermissions + ValidationOverlay
 * 
 * @doc.type hook
 * @doc.purpose Combinatorial view filtering for canvas
 * @doc.layer product
 * @doc.pattern Computed State
 */

import { useMemo } from 'react';
import { useAtomValue } from 'jotai';
import { type Node, type Edge } from '@xyflow/react';
import { nodesAtom, edgesAtom, cameraAtom, activePersonaAtom, ghostNodesAtom } from '../workspace/canvasAtoms';
import { projectAtom } from '@/state/atoms/projectAtom';
import { workspaceAtom } from '@/state/atoms/workspaceAtom';
import { gatesAtom } from '@/state/atoms/gatesAtom';
import { LifecyclePhase } from '@/types/lifecycle';
import type { ArtifactNodeData } from '../nodes/ArtifactNode';
import type { DependencyEdgeData } from '../edges';

// ============================================================================
// Types
// ============================================================================

export type UserRole = 'admin' | 'manager' | 'developer' | 'designer' | 'viewer';

export type ViewMode = 'all' | 'my-work' | 'blockers' | 'critical-path';

export interface ViewModeConfig {
    mode: ViewMode;
    highlightBlockers: boolean;
    dimUnassigned: boolean;
    showCriticalPath: boolean;
}

export interface ComputedViewContext {
    projectId: string | null;
    workspaceId: string | null;
    activePhases: LifecyclePhase[];
    activePersona: string | null;
    userRole: UserRole;
    userId: string | null;
    viewMode: ViewModeConfig;
}

export interface ComputedViewResult {
    /** Filtered nodes to render */
    visibleNodes: Node<ArtifactNodeData>[];
    /** Filtered edges to render */
    visibleEdges: Edge<DependencyEdgeData>[];
    /** Nodes that are dimmed (visible but de-emphasized) */
    dimmedNodeIds: Set<string>;
    /** Nodes that are highlighted (emphasized) */
    highlightedNodeIds: Set<string>;
    /** Nodes with blocker overlay */
    blockedNodeIds: Set<string>;
    /** Nodes locked due to incomplete gates */
    lockedNodeIds: Set<string>;
    /** Total nodes before filtering */
    totalNodes: number;
    /** Culling statistics */
    stats: {
        culledByProject: number;
        culledByPhase: number;
        culledByPersona: number;
        culledByPermissions: number;
    };
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Get the lifecycle phase from a node's x position
 */
function getPhaseFromPosition(x: number): LifecyclePhase {
    if (x < 800) return LifecyclePhase.INTENT;
    if (x < 1700) return LifecyclePhase.SHAPE;
    if (x < 2700) return LifecyclePhase.VALIDATE;
    if (x < 3500) return LifecyclePhase.GENERATE;
    if (x < 4200) return LifecyclePhase.RUN;
    if (x < 4900) return LifecyclePhase.OBSERVE;
    return LifecyclePhase.IMPROVE;
}

/**
 * Check if a node is within the visible viewport phases
 */
function isNodeInVisiblePhase(
    node: Node<ArtifactNodeData>,
    viewport: { x: number; y: number; zoom: number },
    padding: number = 500
): boolean {
    // Calculate visible X range based on viewport
    const viewportWidth = typeof window !== 'undefined' ? window.innerWidth : 1920;
    const visibleLeft = -viewport.x / viewport.zoom - padding;
    const visibleRight = (-viewport.x + viewportWidth) / viewport.zoom + padding;

    const nodeX = node.position.x;
    const nodeWidth = node.width || 200;

    return nodeX + nodeWidth >= visibleLeft && nodeX <= visibleRight;
}

/**
 * Check if a node is relevant to the active persona
 */
function isNodeRelevantToPersona(
    node: Node<ArtifactNodeData>,
    persona: string | null
): boolean {
    // null persona means "show all"
    if (!persona) return true;

    // Check node's assigned persona
    const nodePersona = node.data?.persona;
    if (!nodePersona) return true; // Unassigned nodes are visible to all

    return nodePersona.toLowerCase() === persona.toLowerCase();
}

/**
 * Check if a node is visible based on user role
 */
function isNodeVisibleToRole(
    node: Node<ArtifactNodeData>,
    role: UserRole
): boolean {
    // Admins see everything
    if (role === 'admin') return true;

    // Check if node is marked as private/restricted
    const isPrivate = node.data?.isPrivate ?? false;
    const isConfidential = node.data?.isConfidential ?? false;

    if (isConfidential && role !== 'manager') return false;
    if (isPrivate && role === 'viewer') return false;

    return true;
}

/**
 * Check if a node is assigned to the current user
 */
function isNodeAssignedToUser(
    node: Node<ArtifactNodeData>,
    userId: string | null
): boolean {
    if (!userId) return false;
    const assignee = node.data?.assignedTo;
    return assignee === userId;
}

/**
 * Check if a node is blocked
 */
function isNodeBlocked(node: Node<ArtifactNodeData>): boolean {
    return node.data?.status === 'blocked' || (node.data?.blockerCount ?? 0) > 0;
}

/**
 * Check if a node is in a locked phase (gate not passed)
 */
function isNodeInLockedPhase(
    node: Node<ArtifactNodeData>,
    currentPhase: LifecyclePhase,
    incompleteGatePhases: Set<LifecyclePhase>
): boolean {
    const nodePhase = node.data?.phase;
    if (!nodePhase) return false;

    // Phases after the current one are locked if their gate hasn't passed
    const phaseOrder: LifecyclePhase[] = [
        LifecyclePhase.INTENT,
        LifecyclePhase.SHAPE,
        LifecyclePhase.VALIDATE,
        LifecyclePhase.GENERATE,
        LifecyclePhase.RUN,
        LifecyclePhase.OBSERVE,
        LifecyclePhase.IMPROVE,
    ];

    const currentIndex = phaseOrder.indexOf(currentPhase);
    const nodeIndex = phaseOrder.indexOf(nodePhase);

    // Nodes in future phases are locked if gates are incomplete
    if (nodeIndex > currentIndex && incompleteGatePhases.has(nodePhase)) {
        return true;
    }

    return false;
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Default context when user info not available
 */
const DEFAULT_CONTEXT: Partial<ComputedViewContext> = {
    userRole: 'developer',
    viewMode: {
        mode: 'all',
        highlightBlockers: true,
        dimUnassigned: false,
        showCriticalPath: false,
    },
};

export function useComputedView(
    contextOverrides?: Partial<ComputedViewContext>
): ComputedViewResult {
    // Get state from atoms
    const allNodesRaw = useAtomValue(nodesAtom);
    const ghostNodes = useAtomValue(ghostNodesAtom);
    const allNodes = useMemo(() => [...allNodesRaw, ...ghostNodes], [allNodesRaw, ghostNodes]);
    const allEdges = useAtomValue(edgesAtom);
    const viewport = useAtomValue(cameraAtom);
    const activePersona = useAtomValue(activePersonaAtom);
    const project = useAtomValue(projectAtom);
    const workspace = useAtomValue(workspaceAtom);
    const gates = useAtomValue(gatesAtom);

    // Build context from atoms + overrides
    const context: ComputedViewContext = useMemo(() => ({
        projectId: project.metadata?.id ?? null,
        workspaceId: workspace.currentWorkspace?.id ?? null,
        activePhases: [], // Derived from viewport
        activePersona,
        userRole: contextOverrides?.userRole ?? DEFAULT_CONTEXT.userRole!,
        userId: contextOverrides?.userId ?? null,
        viewMode: contextOverrides?.viewMode ?? DEFAULT_CONTEXT.viewMode!,
    }), [
        project.metadata?.id,
        workspace.currentWorkspace?.id,
        activePersona,
        contextOverrides?.userRole,
        contextOverrides?.userId,
        contextOverrides?.viewMode,
    ]);

    // Compute incomplete gate phases
    const incompleteGatePhases = useMemo(() => {
        const incomplete = new Set<LifecyclePhase>();
        gates.gates.forEach(gate => {
            if (gate.status !== 'passed') {
                // Map gate types to phases (simplified - would need real mapping)
                // For now, assume gates map to BUILD phase
                incomplete.add(LifecyclePhase.GENERATE);
            }
        });
        return incomplete;
    }, [gates.gates]);

    // Compute the current phase based on project state
    const currentPhase = useMemo(() => {
        // Could come from project or be inferred from viewport
        return project.metadata ? LifecyclePhase.GENERATE : LifecyclePhase.INTENT;
    }, [project.metadata]);

    // Main filtering logic
    return useMemo(() => {
        const stats = {
            culledByProject: 0,
            culledByPhase: 0,
            culledByPersona: 0,
            culledByPermissions: 0,
        };

        const dimmedNodeIds = new Set<string>();
        const highlightedNodeIds = new Set<string>();
        const blockedNodeIds = new Set<string>();
        const lockedNodeIds = new Set<string>();

        // Step 1: Filter nodes
        const visibleNodes = allNodes.filter(node => {
            // 1. Project Scope (skip if node belongs to different project)
            if (context.projectId && node.data?.projectId && node.data.projectId !== context.projectId) {
                stats.culledByProject++;
                return false;
            }

            // 2. Phase Relevance (Viewport Culling)
            if (!isNodeInVisiblePhase(node, viewport)) {
                stats.culledByPhase++;
                return false;
            }

            // 3. Persona Relevance
            if (!isNodeRelevantToPersona(node, context.activePersona)) {
                stats.culledByPersona++;
                return false;
            }

            // 4. Role Permissions (Visibility)
            if (!isNodeVisibleToRole(node, context.userRole)) {
                stats.culledByPermissions++;
                return false;
            }

            // Node is visible - now determine overlay states
            const nodeId = node.id;

            // Check if blocked
            if (isNodeBlocked(node)) {
                blockedNodeIds.add(nodeId);
                if (context.viewMode.highlightBlockers) {
                    highlightedNodeIds.add(nodeId);
                }
            }

            // Check if in locked phase
            if (isNodeInLockedPhase(node, currentPhase, incompleteGatePhases)) {
                lockedNodeIds.add(nodeId);
            }

            // View Mode specific handling
            if (context.viewMode.mode === 'my-work') {
                if (!isNodeAssignedToUser(node, context.userId)) {
                    dimmedNodeIds.add(nodeId);
                } else {
                    highlightedNodeIds.add(nodeId);
                }
            } else if (context.viewMode.mode === 'blockers') {
                if (!isNodeBlocked(node)) {
                    dimmedNodeIds.add(nodeId);
                }
            }

            return true;
        });

        // Step 2: Filter edges (only include edges where both source and target are visible)
        const visibleNodeIds = new Set(visibleNodes.map(n => n.id));
        const visibleEdges = allEdges.filter(edge => {
            return visibleNodeIds.has(edge.source) && visibleNodeIds.has(edge.target);
        });

        return {
            visibleNodes,
            visibleEdges,
            dimmedNodeIds,
            highlightedNodeIds,
            blockedNodeIds,
            lockedNodeIds,
            totalNodes: allNodes.length,
            stats,
        };
    }, [
        allNodes,
        allEdges,
        viewport,
        context,
        currentPhase,
        incompleteGatePhases,
    ]);
}

/**
 * Selector atoms for use in components
 */
export { activePersonaAtom } from '../workspace/canvasAtoms';

/**
 * User role atom (would typically come from auth context)
 */
import { atom } from 'jotai';

export const userRoleAtom = atom<UserRole>('developer');
export const userIdAtom = atom<string | null>(null);
export const viewModeAtom = atom<ViewModeConfig>({
    mode: 'all',
    highlightBlockers: true,
    dimUnassigned: false,
    showCriticalPath: false,
});

/**
 * My Tasks filter atom
 */
export const myTasksFilterAtom = atom<boolean>(false);

/**
 * Derived atom: My assigned tasks
 */
export const myTasksAtom = atom((get) => {
    const nodes = get(nodesAtom);
    const userId = get(userIdAtom);

    if (!userId) return [];

    return nodes.filter(node => node.data?.assignedTo === userId);
});
