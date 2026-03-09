/**
 * Utility functions for building Role Inheritance Tree
 */

import type { PersonaTreeNode, RoleFlowNode, InheritanceEdge } from './types';

/**
 * Build inheritance tree from persona composition data
 * 
 * @param personaId - Root persona ID
 * @param personasData - Map of persona ID to persona data
 * @param maxDepth - Maximum depth to traverse
 * @returns Tree structure with inheritance information
 */
export function buildInheritanceTree(
    personaId: string,
    personasData: Map<string, any>,
    maxDepth: number = 10
): PersonaTreeNode | null {
    const visited = new Set<string>();

    function buildNode(id: string, level: number = 0): PersonaTreeNode | null {
        if (level > maxDepth || visited.has(id)) {
            return null;
        }

        visited.add(id);
        const persona = personasData.get(id);

        if (!persona) {
            return null;
        }

        const node: PersonaTreeNode = {
            id,
            label: persona.name || id,
            permissions: persona.permissions || [],
            inherits: persona.inherits || [],
            level,
            children: []
        };

        // Build children from inherited personas
        if (persona.inherits && Array.isArray(persona.inherits)) {
            node.children = persona.inherits
                .map((inheritedId: string) => buildNode(inheritedId, level + 1))
                .filter((child: PersonaTreeNode | null): child is PersonaTreeNode => child !== null);
        }

        return node;
    }

    return buildNode(personaId);
}

/**
 * Convert tree structure to React Flow nodes
 * 
 * @param tree - Tree structure
 * @param layout - Layout direction ('vertical' | 'horizontal')
 * @param highlightPermission - Permission to highlight
 * @returns Array of React Flow nodes
 */
export function treeToNodes(
    tree: PersonaTreeNode | null,
    layout: 'vertical' | 'horizontal' = 'vertical',
    highlightPermission?: string
): RoleFlowNode[] {
    if (!tree) return [];

    const nodes: RoleFlowNode[] = [];
    const nodeSpacing = { x: 250, y: 150 };

    function traverse(node: PersonaTreeNode, x: number, y: number, siblingIndex: number) {
        const isHighlighted = highlightPermission
            ? node.permissions.includes(highlightPermission)
            : false;

        const position = layout === 'vertical'
            ? { x: x + siblingIndex * nodeSpacing.x, y }
            : { x: y, y: x + siblingIndex * nodeSpacing.y };

        const flowNode: RoleFlowNode = {
            id: node.id,
            type: 'roleNode',
            position,
            data: {
                label: node.label,
                permissions: node.permissions,
                isHighlighted,
                permissionCount: node.permissions.length,
                level: node.level
            }
        };

        nodes.push(flowNode);

        // Process children
        if (node.children && node.children.length > 0) {
            const childY = layout === 'vertical' ? y + nodeSpacing.y : y;
            const childX = layout === 'vertical' ? x : x + nodeSpacing.x;

            node.children.forEach((child, index) => {
                const childSiblingOffset = (node.children!.length - 1) / 2;
                traverse(child, childX, childY, index - childSiblingOffset);
            });
        }
    }

    traverse(tree, 0, 0, 0);
    return nodes;
}

/**
 * Convert tree structure to React Flow edges
 * 
 * @param tree - Tree structure
 * @returns Array of React Flow edges
 */
export function treeToEdges(tree: PersonaTreeNode | null): InheritanceEdge[] {
    if (!tree) return [];

    const edges: InheritanceEdge[] = [];

    function traverse(node: PersonaTreeNode) {
        if (node.children && node.children.length > 0) {
            node.children.forEach(child => {
                edges.push({
                    id: `${node.id}-${child.id}`,
                    source: node.id,
                    target: child.id,
                    type: 'inheritanceLink',
                    animated: false,
                    style: { stroke: '#3b82f6', strokeWidth: 2 }
                });

                traverse(child);
            });
        }
    }

    traverse(tree);
    return edges;
}

/**
 * Export tree as JSON
 * 
 * @param tree - Tree structure
 * @returns JSON string
 */
export function exportAsJSON(tree: PersonaTreeNode | null): string {
    return JSON.stringify(tree, null, 2);
}

/**
 * Calculate tree statistics
 * 
 * @param tree - Tree structure
 * @returns Statistics object
 */
export function calculateTreeStats(tree: PersonaTreeNode | null) {
    if (!tree) {
        return { totalNodes: 0, maxDepth: 0, totalPermissions: 0 };
    }

    let totalNodes = 0;
    let maxDepth = 0;
    const permissionsSet = new Set<string>();

    function traverse(node: PersonaTreeNode, depth: number) {
        totalNodes++;
        maxDepth = Math.max(maxDepth, depth);
        node.permissions.forEach(perm => permissionsSet.add(perm));

        if (node.children) {
            node.children.forEach(child => traverse(child, depth + 1));
        }
    }

    traverse(tree, 0);

    return {
        totalNodes,
        maxDepth,
        totalPermissions: permissionsSet.size
    };
}
