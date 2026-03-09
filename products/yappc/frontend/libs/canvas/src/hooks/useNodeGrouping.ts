/**
 * useNodeGrouping Hook
 *
 * React hook for managing node grouping operations.
 * Integrates with React Flow state management.
 *
 * @doc.type hook
 * @doc.purpose Node grouping state management
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useCallback, useMemo } from 'react';
import { useReactFlow, useNodes } from '@xyflow/react';
import type { Node } from '@xyflow/react';
import type { GroupStatus, NodeGroupData } from '../components/NodeGroup';
import {
    createGroupFromNodes,
    ungroupNodes,
    updateGroupStatus,
    validateGrouping,
    generateGroupLabel,
    getGroupChildren,
} from '../utils/grouping';

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

export interface UseNodeGroupingResult {
    selectedNodes: Node[];
    canGroup: boolean;
    canUngroup: boolean;
    groupSelectedNodes: (label?: string, status?: GroupStatus) => void;
    ungroupSelectedNodes: () => void;
    changeGroupStatus: (status: GroupStatus) => void;
    getGroupById: (groupId: string) => Node<NodeGroupData> | undefined;
    getGroupChildren: (groupId: string) => Node[];
}

export interface UseNodeGroupingOptions {
    onGroupCreated?: (groupNode: Node<NodeGroupData>) => void;
    onGroupRemoved?: (groupId: string) => void;
    onStatusChanged?: (groupId: string, status: GroupStatus) => void;
}

// ============================================================================
// HOOK
// ============================================================================

/**
 * Hook for managing node grouping operations
 *
 * @param options - Hook options
 * @returns Grouping operations and state
 */
export function useNodeGrouping(
    options: UseNodeGroupingOptions = {}
): UseNodeGroupingResult {
    const { setNodes, getNodes } = useReactFlow();
    const allNodes = useNodes();

    // ========================================================================
    // COMPUTED VALUES
    // ========================================================================

    const selectedNodes = useMemo(() => {
        return allNodes.filter((n) => n.selected);
    }, [allNodes]);

    const canGroup = useMemo(() => {
        const validation = validateGrouping(selectedNodes);
        return validation.valid;
    }, [selectedNodes]);

    const canUngroup = useMemo(() => {
        return selectedNodes.length === 1 && selectedNodes[0].type === 'nodeGroup';
    }, [selectedNodes]);

    // ========================================================================
    // GROUPING OPERATIONS
    // ========================================================================

    const groupSelectedNodes = useCallback(
        (label?: string, status: GroupStatus = 'unknown') => {
            const validation = validateGrouping(selectedNodes);
            if (!validation.valid) {
                console.warn(`Cannot group nodes: ${validation.error}`);
                return;
            }

            const groupLabel = label || generateGroupLabel(selectedNodes);
            const groupNode = createGroupFromNodes(selectedNodes, {
                label: groupLabel,
                status,
            });

            setNodes((nodes) => {
                // Deselect grouped nodes
                const updatedNodes = nodes.map((n) => {
                    if (selectedNodes.some((sn) => sn.id === n.id)) {
                        return { ...n, selected: false };
                    }
                    return n;
                });

                // Add the new group node (selected)
                return [...updatedNodes, { ...groupNode, selected: true }];
            });

            options.onGroupCreated?.(groupNode);
        },
        [selectedNodes, setNodes, options]
    );

    const ungroupSelectedNodes = useCallback(() => {
        if (!canUngroup) {
            console.warn('Cannot ungroup: no group selected');
            return;
        }

        const groupNode = selectedNodes[0] as Node<NodeGroupData>;
        const groupId = groupNode.id;

        setNodes((nodes) => {
            const ungrouped = ungroupNodes(groupNode, nodes);
            return ungrouped;
        });

        options.onGroupRemoved?.(groupId);
    }, [canUngroup, selectedNodes, setNodes, options]);

    const changeGroupStatus = useCallback(
        (status: GroupStatus) => {
            if (!canUngroup) {
                console.warn('Cannot change status: no group selected');
                return;
            }

            const groupNode = selectedNodes[0] as Node<NodeGroupData>;

            setNodes((nodes) =>
                nodes.map((n) =>
                    n.id === groupNode.id ? updateGroupStatus(n as Node<NodeGroupData>, status) : n
                )
            );

            options.onStatusChanged?.(groupNode.id, status);
        },
        [canUngroup, selectedNodes, setNodes, options]
    );

    // ========================================================================
    // QUERY OPERATIONS
    // ========================================================================

    const getGroupById = useCallback(
        (groupId: string): Node<NodeGroupData> | undefined => {
            const node = allNodes.find((n) => n.id === groupId && n.type === 'nodeGroup');
            return node as Node<NodeGroupData> | undefined;
        },
        [allNodes]
    );

    const getGroupChildrenById = useCallback(
        (groupId: string): Node[] => {
            const groupNode = getGroupById(groupId);
            if (!groupNode) return [];
            return getGroupChildren(groupNode, allNodes);
        },
        [allNodes, getGroupById]
    );

    // ========================================================================
    // RETURN
    // ========================================================================

    return {
        selectedNodes,
        canGroup,
        canUngroup,
        groupSelectedNodes,
        ungroupSelectedNodes,
        changeGroupStatus,
        getGroupById,
        getGroupChildren: getGroupChildrenById,
    };
}

export default useNodeGrouping;
