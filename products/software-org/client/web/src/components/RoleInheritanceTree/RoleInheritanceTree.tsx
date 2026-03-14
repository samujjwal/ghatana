/**
 * RoleInheritanceTree Component
 * 
 * Main component for visualizing role inheritance hierarchy with permissions
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import ReactFlow, {
    Controls,
    Background,
    BackgroundVariant,
    useNodesState,
    useEdgesState,
    type NodeTypes,
    type EdgeTypes,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { RoleNode } from './RoleNode';
import { InheritanceLink } from './InheritanceLink';
import { PermissionTooltip } from './PermissionTooltip';
import { buildInheritanceTree, treeToNodes, treeToEdges, exportAsJSON, calculateTreeStats } from './utils';
import type { RoleInheritanceTreeProps, PersonaTreeNode } from './types';

// Custom node and edge types
const nodeTypes: NodeTypes = {
    roleNode: RoleNode,
};

const edgeTypes: EdgeTypes = {
    inheritanceLink: InheritanceLink,
};

/**
 * RoleInheritanceTree - Visual component showing role hierarchy
 * 
 * Features:
 * - Interactive graph visualization using react-flow
 * - Permission tracking across inheritance chain
 * - Hover states with permission tooltips
 * - Export functionality (PNG, SVG, JSON)
 * - Responsive layout (vertical/horizontal)
 * - Search and highlight specific permissions
 */
export function RoleInheritanceTree({
    personaId,
    highlightPermission,
    interactive = true,
    onExport,
    onNodeClick,
    layout = 'vertical',
    maxDepth = 10,
    isLoading = false,
    error,
}: RoleInheritanceTreeProps) {
    const [tree, setTree] = useState<PersonaTreeNode | null>(null);
    const [nodes, setNodes, onNodesChange] = useNodesState([]);
    const [edges, setEdges, onEdgesChange] = useEdgesState([]);
    const [tooltipData, setTooltipData] = useState<{
        permissions: string[];
        roleName: string;
        position: { x: number; y: number };
        visible: boolean;
    }>({
        permissions: [],
        roleName: '',
        position: { x: 0, y: 0 },
        visible: false,
    });

    // Mock persona data for now - in real app, this would come from usePersonaComposition
    const personasData = useMemo(() => {
        const data = new Map();

        // Sample data structure
        data.set('admin', {
            name: 'Admin',
            permissions: ['admin.read', 'admin.write', 'admin.delete', 'user.read', 'user.write'],
            inherits: ['moderator', 'user'],
        });

        data.set('moderator', {
            name: 'Moderator',
            permissions: ['content.moderate', 'user.read', 'content.read'],
            inherits: ['user'],
        });

        data.set('user', {
            name: 'User',
            permissions: ['profile.read', 'profile.write', 'content.read'],
            inherits: [],
        });

        data.set('developer', {
            name: 'Developer',
            permissions: ['code.read', 'code.write', 'deploy.read'],
            inherits: ['user'],
        });

        data.set('engineer', {
            name: 'Engineer',
            permissions: ['code.read', 'code.write', 'deploy.read', 'deploy.write', 'infra.read'],
            inherits: ['developer', 'user'],
        });

        return data;
    }, []);

    // Build tree structure
    useEffect(() => {
        if (!personaId || isLoading) {
            setTree(null);
            return;
        }

        try {
            const builtTree = buildInheritanceTree(personaId, personasData, maxDepth);
            setTree(builtTree);
        } catch (err) {
            console.error('Error building inheritance tree:', err);
            setTree(null);
        }
    }, [personaId, personasData, maxDepth, isLoading]);

    // Convert tree to nodes and edges
    useEffect(() => {
        if (!tree) {
            setNodes([]);
            setEdges([]);
            return;
        }

        const flowNodes = treeToNodes(tree, layout, highlightPermission);
        const flowEdges = treeToEdges(tree);

        setNodes(flowNodes);
        setEdges(flowEdges);
    }, [tree, layout, highlightPermission, setNodes, setEdges]);

    // Handle node hover for tooltip
    const handleNodeMouseEnter = useCallback(
        (event: React.MouseEvent, node: any) => {
            if (!interactive) return;

            setTooltipData({
                permissions: node.data.permissions,
                roleName: node.data.label,
                position: { x: event.clientX, y: event.clientY },
                visible: true,
            });
        },
        [interactive]
    );

    const handleNodeMouseLeave = useCallback(() => {
        setTooltipData(prev => ({ ...prev, visible: false }));
    }, []);

    // Handle node click
    const handleNodeClick = useCallback(
        (_event: React.MouseEvent, node: any) => {
            if (onNodeClick) {
                onNodeClick(node.id);
            }
        },
        [onNodeClick]
    );

    // Export functionality
    const handleExport = useCallback(
        (format: 'png' | 'svg' | 'json') => {
            if (format === 'json' && tree) {
                const jsonData = exportAsJSON(tree);
                const blob = new Blob([jsonData], { type: 'application/json' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `${personaId}-inheritance-tree.json`;
                a.click();
                URL.revokeObjectURL(url);
            }

            if (onExport) {
                onExport(format);
            }
        },
        [tree, personaId, onExport]
    );

    // Calculate stats
    const stats = useMemo(() => {
        return tree ? calculateTreeStats(tree) : null;
    }, [tree]);

    // Loading state
    if (isLoading) {
        return (
            <div className="w-full h-96 flex items-center justify-center bg-slate-50 dark:bg-neutral-800 rounded-lg border-2 border-dashed border-slate-300 dark:border-neutral-600">
                <div className="text-center">
                    <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
                    <div className="text-slate-600 dark:text-neutral-400">Loading inheritance tree...</div>
                </div>
            </div>
        );
    }

    // Error state
    if (error) {
        return (
            <div className="w-full h-96 flex items-center justify-center bg-red-50 dark:bg-rose-600/30 rounded-lg border-2 border-red-300 dark:border-red-800">
                <div className="text-center text-red-600 dark:text-rose-400">
                    <div className="text-4xl mb-2">⚠️</div>
                    <div className="font-bold">Error loading inheritance tree</div>
                    <div className="text-sm mt-2">{error}</div>
                </div>
            </div>
        );
    }

    // Empty state
    if (!tree || nodes.length === 0) {
        return (
            <div className="w-full h-96 flex items-center justify-center bg-slate-50 dark:bg-neutral-800 rounded-lg border-2 border-dashed border-slate-300 dark:border-neutral-600">
                <div className="text-center text-slate-600 dark:text-neutral-400">
                    <div className="text-4xl mb-2">🔍</div>
                    <div className="font-bold">No inheritance tree found</div>
                    <div className="text-sm mt-2">
                        The persona "{personaId}" does not exist or has no inheritance relationships.
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="w-full h-full flex flex-col">
            {/* Stats bar */}
            {stats && (
                <div className="flex gap-4 p-3 bg-slate-50 border-b">
                    <div className="text-sm">
                        <span className="font-semibold">Nodes:</span> {stats.totalNodes}
                    </div>
                    <div className="text-sm">
                        <span className="font-semibold">Max Depth:</span> {stats.maxDepth}
                    </div>
                    <div className="text-sm">
                        <span className="font-semibold">Total Permissions:</span> {stats.totalPermissions}
                    </div>

                    {/* Export buttons */}
                    <div className="ml-auto flex gap-2">
                        <button
                            onClick={() => handleExport('json')}
                            className="px-3 py-1 text-sm bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
                        >
                            Export JSON
                        </button>
                    </div>
                </div>
            )}

            {/* React Flow diagram */}
            <div className="flex-1 bg-slate-100">
                <ReactFlow
                    nodes={nodes}
                    edges={edges}
                    onNodesChange={onNodesChange}
                    onEdgesChange={onEdgesChange}
                    onNodeMouseEnter={handleNodeMouseEnter}
                    onNodeMouseLeave={handleNodeMouseLeave}
                    onNodeClick={handleNodeClick}
                    nodeTypes={nodeTypes}
                    edgeTypes={edgeTypes}
                    fitView
                    attributionPosition="bottom-left"
                    minZoom={0.1}
                    maxZoom={2}
                    defaultEdgeOptions={{
                        type: 'inheritanceLink',
                        animated: false,
                    }}
                >
                    <Controls />
                    <Background variant={BackgroundVariant.Dots} gap={12} size={1} />
                </ReactFlow>
            </div>

            {/* Permission tooltip */}
            <PermissionTooltip
                permissions={tooltipData.permissions}
                roleName={tooltipData.roleName}
                position={tooltipData.position}
                visible={tooltipData.visible}
            />
        </div>
    );
}
