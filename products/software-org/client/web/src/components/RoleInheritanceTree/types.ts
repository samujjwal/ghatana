/**
 * Type definitions for Role Inheritance Tree components
 */

import type { Node, Edge } from '@xyflow/react';

/**
 * Persona data with inheritance information
 */
export interface PersonaTreeNode {
    id: string;
    label: string;
    inherits?: string[];
    permissions: string[];
    children?: PersonaTreeNode[];
    level?: number;
}

/**
 * Role node data for React Flow
 */
export interface RoleNodeData {
    label: string;
    permissions: string[];
    isHighlighted?: boolean;
    permissionCount?: number;
    level?: number;
}

/**
 * Props for RoleInheritanceTree component
 */
export interface RoleInheritanceTreeProps {
    /** Root persona to visualize */
    personaId: string;

    /** Optional: Specific workspace context */
    workspaceId?: string;

    /** Optional: Highlight specific permission */
    highlightPermission?: string;

    /** Optional: Interactive mode (hover, click) */
    interactive?: boolean;

    /** Optional: Export callback */
    onExport?: (format: 'png' | 'svg' | 'json') => void;

    /** Optional: Node click handler */
    onNodeClick?: (roleId: string) => void;

    /** Optional: Layout direction */
    layout?: 'vertical' | 'horizontal';

    /** Optional: Max depth to display */
    maxDepth?: number;

    /** Optional: Loading state */
    isLoading?: boolean;

    /** Optional: Error message */
    error?: string;
}

/**
 * React Flow node with role data
 */
export type RoleFlowNode = Node<RoleNodeData>;

/**
 * React Flow edge for inheritance links
 */
export type InheritanceEdge = Edge;
