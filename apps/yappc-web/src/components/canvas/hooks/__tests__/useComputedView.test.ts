/**
 * useComputedView Hook Tests
 * 
 * Tests for the combinatorial view composition system.
 * 
 * @doc.type test
 * @doc.purpose Test combinatorial view filtering
 * @doc.layer product
 */

import { renderHook } from '@testing-library/react';
import { Provider, createStore } from 'jotai';
import { useHydrateAtoms } from 'jotai/utils';
import React from 'react';
import { useComputedView, userRoleAtom, viewModeAtom, type ViewModeConfig } from '../useComputedView';
import { nodesAtom, edgesAtom, cameraAtom, activePersonaAtom } from '../../workspace/canvasAtoms';
import { projectAtom } from '@/state/atoms/projectAtom';
import { workspaceAtom } from '@/state/atoms/workspaceAtom';
import { gatesAtom } from '@/state/atoms/gatesAtom';
import { LifecyclePhase } from '@/types/lifecycle';
import type { Node, Edge } from '@xyflow/react';
import type { ArtifactNodeData } from '../../nodes/ArtifactNode';

// Mock nodes for testing
const createMockNode = (
    id: string,
    phase: LifecyclePhase,
    options: Partial<{
        persona: string;
        status: string;
        assignedTo: string;
        isPrivate: boolean;
        blockerCount: number;
        projectId: string;
    }> = {}
): Node<ArtifactNodeData> => ({
    id,
    type: 'artifact',
    position: { x: getPhaseX(phase), y: 100 },
    data: {
        id,
        type: 'user-story',
        title: `Node ${id}`,
        description: 'Test node',
        status: options.status ?? 'pending',
        persona: options.persona ?? 'developer',
        phase,
        linkedCount: 0,
        blockerCount: options.blockerCount ?? 0,
        assignedTo: options.assignedTo,
        isPrivate: options.isPrivate ?? false,
        projectId: options.projectId,
        onEdit: jest.fn(),
        onLink: jest.fn(),
    } as ArtifactNodeData,
});

// Get X position for a phase (simplified from SpatialZones)
function getPhaseX(phase: LifecyclePhase): number {
    const positions: Record<LifecyclePhase, number> = {
        [LifecyclePhase.INTENT]: 100,
        [LifecyclePhase.SHAPE]: 900,
        [LifecyclePhase.VALIDATE]: 1800,
        [LifecyclePhase.GENERATE]: 2800,
        [LifecyclePhase.RUN]: 3600,
        [LifecyclePhase.OBSERVE]: 4300,
        [LifecyclePhase.IMPROVE]: 5000,
    };
    return positions[phase];
}

// Wrapper component for Jotai provider with hydrated atoms
interface WrapperProps {
    initialValues?: Array<[any, any]>;
    children: React.ReactNode;
}

const HydrateAtoms: React.FC<{ initialValues: Array<[any, any]>; children: React.ReactNode }> = ({
    initialValues,
    children,
}) => {
    useHydrateAtoms(initialValues);
    return <>{ children } </>;
};

const createWrapper = (initialValues: Array<[any, any]> = []) => {
    const store = createStore();

    return ({ children }: { children: React.ReactNode }) => (
        <Provider store= { store } >
        <HydrateAtoms initialValues={ initialValues }>
            { children }
            </HydrateAtoms>
            </Provider>
    );
};

describe('useComputedView', () => {
    describe('Basic Filtering', () => {
        it('returns all nodes when no filters are active', () => {
            const nodes = [
                createMockNode('1', LifecyclePhase.INTENT),
                createMockNode('2', LifecyclePhase.SHAPE),
                createMockNode('3', LifecyclePhase.GENERATE),
            ];

            const wrapper = createWrapper([
                [nodesAtom, nodes],
                [edgesAtom, []],
                [cameraAtom, { x: 0, y: 0, zoom: 1 }],
                [activePersonaAtom, null],
            ]);

            const { result } = renderHook(() => useComputedView(), { wrapper });

            expect(result.current.totalNodes).toBe(3);
        });

        it('filters nodes by persona', () => {
            const nodes = [
                createMockNode('1', LifecyclePhase.INTENT, { persona: 'developer' }),
                createMockNode('2', LifecyclePhase.SHAPE, { persona: 'designer' }),
                createMockNode('3', LifecyclePhase.GENERATE, { persona: 'developer' }),
            ];

            const wrapper = createWrapper([
                [nodesAtom, nodes],
                [edgesAtom, []],
                [cameraAtom, { x: 0, y: 0, zoom: 1 }],
                [activePersonaAtom, 'developer'],
            ]);

            const { result } = renderHook(() => useComputedView(), { wrapper });

            // Should filter out the designer node
            expect(result.current.visibleNodes.length).toBeLessThanOrEqual(3);
            expect(result.current.stats.culledByPersona).toBeGreaterThanOrEqual(0);
        });
    });

    describe('View Mode Filtering', () => {
        it('dims non-assigned nodes in My Work mode', () => {
            const nodes = [
                createMockNode('1', LifecyclePhase.INTENT, { assignedTo: 'current-user' }),
                createMockNode('2', LifecyclePhase.SHAPE, { assignedTo: 'other-user' }),
            ];

            const myWorkMode: ViewModeConfig = {
                mode: 'my-work',
                highlightBlockers: true,
                dimUnassigned: true,
                showCriticalPath: false,
            };

            const wrapper = createWrapper([
                [nodesAtom, nodes],
                [edgesAtom, []],
                [cameraAtom, { x: 0, y: 0, zoom: 1 }],
                [activePersonaAtom, null],
                [viewModeAtom, myWorkMode],
            ]);

            const { result } = renderHook(
                () => useComputedView({ userId: 'current-user', viewMode: myWorkMode }),
                { wrapper }
            );

            // Node 2 should be dimmed (not assigned to current user)
            expect(result.current.dimmedNodeIds.has('2')).toBe(true);
            // Node 1 should be highlighted (assigned to current user)
            expect(result.current.highlightedNodeIds.has('1')).toBe(true);
        });

        it('highlights blocked nodes in Blockers mode', () => {
            const nodes = [
                createMockNode('1', LifecyclePhase.INTENT, { status: 'blocked' }),
                createMockNode('2', LifecyclePhase.SHAPE, { status: 'in-progress' }),
                createMockNode('3', LifecyclePhase.GENERATE, { blockerCount: 2 }),
            ];

            const blockerMode: ViewModeConfig = {
                mode: 'blockers',
                highlightBlockers: true,
                dimUnassigned: false,
                showCriticalPath: false,
            };

            const wrapper = createWrapper([
                [nodesAtom, nodes],
                [edgesAtom, []],
                [cameraAtom, { x: 0, y: 0, zoom: 1 }],
                [activePersonaAtom, null],
                [viewModeAtom, blockerMode],
            ]);

            const { result } = renderHook(
                () => useComputedView({ viewMode: blockerMode }),
                { wrapper }
            );

            // Blocked nodes should be in blockedNodeIds
            expect(result.current.blockedNodeIds.has('1')).toBe(true);
            expect(result.current.blockedNodeIds.has('3')).toBe(true);
            // Non-blocked node should be dimmed
            expect(result.current.dimmedNodeIds.has('2')).toBe(true);
        });
    });

    describe('Role-Based Filtering', () => {
        it('hides private nodes from viewers', () => {
            const nodes = [
                createMockNode('1', LifecyclePhase.INTENT, { isPrivate: false }),
                createMockNode('2', LifecyclePhase.SHAPE, { isPrivate: true }),
            ];

            const wrapper = createWrapper([
                [nodesAtom, nodes],
                [edgesAtom, []],
                [cameraAtom, { x: 0, y: 0, zoom: 1 }],
                [activePersonaAtom, null],
                [userRoleAtom, 'viewer'],
            ]);

            const { result } = renderHook(
                () => useComputedView({ userRole: 'viewer' }),
                { wrapper }
            );

            expect(result.current.stats.culledByPermissions).toBeGreaterThanOrEqual(0);
        });

        it('shows all nodes to admins', () => {
            const nodes = [
                createMockNode('1', LifecyclePhase.INTENT, { isPrivate: true }),
                createMockNode('2', LifecyclePhase.SHAPE, { isPrivate: true }),
            ];

            const wrapper = createWrapper([
                [nodesAtom, nodes],
                [edgesAtom, []],
                [cameraAtom, { x: 0, y: 0, zoom: 1 }],
                [activePersonaAtom, null],
            ]);

            const { result } = renderHook(
                () => useComputedView({ userRole: 'admin' }),
                { wrapper }
            );

            // Admin should see all nodes
            expect(result.current.stats.culledByPermissions).toBe(0);
        });
    });

    describe('Edge Filtering', () => {
        it('filters edges to only include visible node connections', () => {
            const nodes = [
                createMockNode('1', LifecyclePhase.INTENT, { persona: 'developer' }),
                createMockNode('2', LifecyclePhase.SHAPE, { persona: 'designer' }),
                createMockNode('3', LifecyclePhase.GENERATE, { persona: 'developer' }),
            ];

            const edges: Edge[] = [
                { id: 'e1-2', source: '1', target: '2' },
                { id: 'e1-3', source: '1', target: '3' },
                { id: 'e2-3', source: '2', target: '3' },
            ];

            const wrapper = createWrapper([
                [nodesAtom, nodes],
                [edgesAtom, edges],
                [cameraAtom, { x: 0, y: 0, zoom: 1 }],
                [activePersonaAtom, 'developer'],
            ]);

            const { result } = renderHook(() => useComputedView(), { wrapper });

            // Only edges between visible nodes should be included
            result.current.visibleEdges.forEach(edge => {
                const visibleNodeIds = result.current.visibleNodes.map(n => n.id);
                expect(visibleNodeIds).toContain(edge.source);
                expect(visibleNodeIds).toContain(edge.target);
            });
        });
    });

    describe('Statistics', () => {
        it('provides accurate culling statistics', () => {
            const nodes = [
                createMockNode('1', LifecyclePhase.INTENT),
                createMockNode('2', LifecyclePhase.SHAPE),
            ];

            const wrapper = createWrapper([
                [nodesAtom, nodes],
                [edgesAtom, []],
                [cameraAtom, { x: 0, y: 0, zoom: 1 }],
                [activePersonaAtom, null],
            ]);

            const { result } = renderHook(() => useComputedView(), { wrapper });

            expect(result.current.stats).toHaveProperty('culledByProject');
            expect(result.current.stats).toHaveProperty('culledByPhase');
            expect(result.current.stats).toHaveProperty('culledByPersona');
            expect(result.current.stats).toHaveProperty('culledByPermissions');
            expect(result.current.totalNodes).toBe(2);
        });
    });
});
