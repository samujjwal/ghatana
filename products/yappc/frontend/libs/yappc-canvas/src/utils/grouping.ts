/**
 * Node Grouping Utilities
 *
 * Helper functions for creating, managing, and manipulating node groups.
 * Implements Journey 1.1 PM workflow: select nodes → group → label → set status.
 *
 * @doc.type utility
 * @doc.purpose Node grouping operations
 * @doc.layer product
 * @doc.pattern Utility Functions
 */

import type { Node, Edge, XYPosition } from '@xyflow/react';
import type { NodeGroupData, GroupStatus } from '../components/NodeGroup';

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

export interface GroupingOptions {
    label?: string;
    description?: string;
    status?: GroupStatus;
    assignee?: string;
    tags?: string[];
    padding?: number; // Padding around grouped nodes
}

export interface GroupBounds {
    x: number;
    y: number;
    width: number;
    height: number;
}

// ============================================================================
// GROUPING UTILITIES
// ============================================================================

/**
 * Create a new group node from selected nodes
 *
 * @param selectedNodes - Nodes to group
 * @param options - Group configuration
 * @returns New group node
 */
export function createGroupFromNodes(
    selectedNodes: Node[],
    options: GroupingOptions = {}
): Node<NodeGroupData> {
    if (selectedNodes.length === 0) {
        throw new Error('Cannot create group: no nodes selected');
    }

    const bounds = calculateGroupBounds(selectedNodes, options.padding || 20);
    const childIds = selectedNodes.map((n) => n.id);

    const groupId = `group-${Date.now()}`;

    return {
        id: groupId,
        type: 'nodeGroup',
        position: { x: bounds.x, y: bounds.y },
        data: {
            label: options.label || 'New Group',
            description: options.description,
            status: options.status || 'unknown',
            children: childIds,
            collapsed: false,
            assignee: options.assignee,
            tags: options.tags,
        },
        style: {
            width: bounds.width,
            height: bounds.height,
        },
        draggable: true,
        selectable: true,
    };
}

/**
 * Calculate bounding box for a set of nodes
 *
 * @param nodes - Nodes to calculate bounds for
 * @param padding - Extra padding around nodes
 * @returns Bounding box coordinates and dimensions
 */
export function calculateGroupBounds(
    nodes: Node[],
    padding: number = 20
): GroupBounds {
    if (nodes.length === 0) {
        return { x: 0, y: 0, width: 300, height: 150 };
    }

    // Find min/max coordinates
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;

    nodes.forEach((node) => {
        const nodeWidth = (node.width as number) || 200;
        const nodeHeight = (node.height as number) || 100;

        minX = Math.min(minX, node.position.x);
        minY = Math.min(minY, node.position.y);
        maxX = Math.max(maxX, node.position.x + nodeWidth);
        maxY = Math.max(maxY, node.position.y + nodeHeight);
    });

    return {
        x: minX - padding,
        y: minY - padding,
        width: maxX - minX + padding * 2,
        height: maxY - minY + padding * 2,
    };
}

/**
 * Update positions of child nodes relative to group
 *
 * @param groupNode - The group node
 * @param childNodes - Child nodes to reposition
 * @returns Updated child nodes with new positions
 */
export function updateChildNodePositions(
    groupNode: Node<NodeGroupData>,
    childNodes: Node[]
): Node[] {
    const groupX = groupNode.position.x;
    const groupY = groupNode.position.y;

    return childNodes.map((node) => ({
        ...node,
        position: {
            x: node.position.x - groupX,
            y: node.position.y - groupY,
        },
        parentNode: groupNode.id,
        extent: 'parent' as const,
    }));
}

/**
 * Ungroup a group node - extract child nodes
 *
 * @param groupNode - Group to ungroup
 * @param allNodes - All nodes in the canvas
 * @returns Updated nodes with children extracted
 */
export function ungroupNodes(
    groupNode: Node<NodeGroupData>,
    allNodes: Node[]
): Node[] {
    const childIds = groupNode.data.children || [];
    const groupX = groupNode.position.x;
    const groupY = groupNode.position.y;

    return allNodes
        .filter((n) => n.id !== groupNode.id) // Remove group node
        .map((node) => {
            if (childIds.includes(node.id)) {
                // Reset child node position to absolute coordinates
                return {
                    ...node,
                    position: {
                        x: node.position.x + groupX,
                        y: node.position.y + groupY,
                    },
                    parentNode: undefined,
                    extent: undefined,
                };
            }
            return node;
        });
}

/**
 * Update group status
 *
 * @param groupNode - Group node to update
 * @param status - New status
 * @returns Updated group node
 */
export function updateGroupStatus(
    groupNode: Node<NodeGroupData>,
    status: GroupStatus
): Node<NodeGroupData> {
    return {
        ...groupNode,
        data: {
            ...groupNode.data,
            status,
        },
    };
}

/**
 * Check if a node is inside a group's bounds
 *
 * @param node - Node to check
 * @param groupNode - Group node
 * @returns True if node is inside group bounds
 */
export function isNodeInsideGroup(
    node: Node,
    groupNode: Node<NodeGroupData>
): boolean {
    const nodeWidth = (node.width as number) || 200;
    const nodeHeight = (node.height as number) || 100;
    const groupWidth = (groupNode.style?.width as number) || 300;
    const groupHeight = (groupNode.style?.height as number) || 150;

    const nodeCenterX = node.position.x + nodeWidth / 2;
    const nodeCenterY = node.position.y + nodeHeight / 2;

    return (
        nodeCenterX >= groupNode.position.x &&
        nodeCenterX <= groupNode.position.x + groupWidth &&
        nodeCenterY >= groupNode.position.y &&
        nodeCenterY <= groupNode.position.y + groupHeight
    );
}

/**
 * Auto-update group bounds based on child positions
 *
 * @param groupNode - Group node to update
 * @param childNodes - Current child nodes
 * @param padding - Padding around children
 * @returns Updated group node with new bounds
 */
export function autoResizeGroup(
    groupNode: Node<NodeGroupData>,
    childNodes: Node[],
    padding: number = 20
): Node<NodeGroupData> {
    if (childNodes.length === 0) {
        return groupNode;
    }

    const bounds = calculateGroupBounds(childNodes, padding);

    return {
        ...groupNode,
        position: { x: bounds.x, y: bounds.y },
        style: {
            ...groupNode.style,
            width: bounds.width,
            height: bounds.height,
        },
    };
}

/**
 * Find all nodes that belong to a group
 *
 * @param groupNode - Group node
 * @param allNodes - All nodes in the canvas
 * @returns Child nodes of the group
 */
export function getGroupChildren(
    groupNode: Node<NodeGroupData>,
    allNodes: Node[]
): Node[] {
    const childIds = groupNode.data.children || [];
    return allNodes.filter((n) => childIds.includes(n.id));
}

/**
 * Create edges connecting to a group's children
 * (updates edge sources/targets when grouping)
 *
 * @param edges - Existing edges
 * @param groupNode - Newly created group
 * @param childIds - IDs of nodes now in the group
 * @returns Updated edges
 */
export function updateEdgesForGroup(
    edges: Edge[],
    groupNode: Node<NodeGroupData>,
    childIds: string[]
): Edge[] {
    // For now, keep edges as-is
    // Future: Could optionally redirect external edges to the group node
    return edges;
}

/**
 * Select all nodes within a group
 *
 * @param groupNode - Group node
 * @param allNodes - All nodes in the canvas
 * @returns Array of selected node IDs
 */
export function selectGroupChildren(
    groupNode: Node<NodeGroupData>,
    allNodes: Node[]
): string[] {
    return groupNode.data.children || [];
}

/**
 * Validate group creation
 *
 * @param selectedNodes - Nodes to group
 * @returns Validation result with error message if invalid
 */
export function validateGrouping(
    selectedNodes: Node[]
): { valid: boolean; error?: string } {
    if (selectedNodes.length === 0) {
        return { valid: false, error: 'No nodes selected' };
    }

    if (selectedNodes.length === 1) {
        return { valid: false, error: 'Cannot group a single node' };
    }

    // Check if any selected nodes are already groups
    const hasGroupNodes = selectedNodes.some((n) => n.type === 'nodeGroup');
    if (hasGroupNodes) {
        return { valid: false, error: 'Cannot nest groups (selected nodes include a group)' };
    }

    return { valid: true };
}

/**
 * Generate a smart group label based on child node types
 *
 * @param childNodes - Nodes being grouped
 * @returns Suggested label
 */
export function generateGroupLabel(childNodes: Node[]): string {
    if (childNodes.length === 0) return 'New Group';

    // Count node types
    const typeCounts = childNodes.reduce((acc, node) => {
        const type = node.type || 'default';
        acc[type] = (acc[type] || 0) + 1;
        return acc;
    }, {} as Record<string, number>);

    // Find most common type
    const dominantType = Object.entries(typeCounts)
        .sort(([, a], [, b]) => b - a)[0]?.[0];

    // Generate label based on dominant type
    const typeLabels: Record<string, string> = {
        database: 'Data Layer',
        service: 'Service Layer',
        api: 'API Module',
        uiScreen: 'UI Module',
        aiPrompt: 'AI Feature',
        requirement: 'Requirements',
        test: 'Test Suite',
        code: 'Code Module',
    };

    return typeLabels[dominantType] || `Group of ${childNodes.length} nodes`;
}
