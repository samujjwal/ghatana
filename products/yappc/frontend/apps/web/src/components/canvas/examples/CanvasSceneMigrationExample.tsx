/**
 * Example: Migrating CanvasScene to use Generic Canvas
 * This demonstrates Phase 2 migration of an existing canvas implementation
 */

import {
  Box,
  Toolbar,
  Typography,
  Surface as Paper,
} from '@ghatana/ui';
import React, { useState } from 'react';

import { REGISTRY_NAMESPACES } from '../../../services/registry/RegistryMigration';
import { UnifiedRegistry } from '../../../services/registry/UnifiedRegistry';
import { GenericCanvas } from '../core/GenericCanvas';

import type { BaseItem } from '../core/types';

// Define SceneNode interface extending BaseItem
/**
 *
 */
export interface SceneNode extends BaseItem {
    type: 'scene-node';
    position: { x: number; y: number };
    data: {
        label: string;
        nodeType: 'component' | 'container' | 'text' | 'media';
        properties: Record<string, unknown>;
        style?: {
            width?: number;
            height?: number;
            backgroundColor?: string;
            borderColor?: string;
            borderRadius?: number;
        };
    };
}

// Legacy CanvasScene interface (before migration)
/**
 *
 */
interface LegacyCanvasSceneProps {
    initialNodes?: SceneNode[];
    onNodesChange?: (nodes: SceneNode[]) => void;
    enableDragDrop?: boolean;
    enableSelection?: boolean;
    readonly?: boolean;
}

// Legacy CanvasScene component (BEFORE migration)
const LegacyCanvasScene: React.FC<LegacyCanvasSceneProps> = ({
    initialNodes = [],
    onNodesChange,
    enableDragDrop = true,
    enableSelection = true,
    readonly = false
}) => {
    const [nodes, setNodes] = useState<SceneNode[]>(initialNodes);
    const [selectedNodes, setSelectedNodes] = useState<string[]>([]);

    // Legacy implementation with separate state management
    const handleNodeAdd = (node: Omit<SceneNode, 'id'>) => {
        const newNode: SceneNode = {
            ...node,
            id: `node_${Date.now()}`,
            metadata: {
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString()
            }
        };
        const updatedNodes = [...nodes, newNode];
        setNodes(updatedNodes);
        onNodesChange?.(updatedNodes);
    };

    const handleNodeUpdate = (id: string, updates: Partial<SceneNode>) => {
        const updatedNodes = nodes.map(node =>
            node.id === id
                ? { ...node, ...updates, metadata: { ...node.metadata, updatedAt: new Date().toISOString() } }
                : node
        );
        setNodes(updatedNodes);
        onNodesChange?.(updatedNodes);
    };

    const handleNodeDelete = (id: string) => {
        const updatedNodes = nodes.filter(node => node.id !== id);
        setNodes(updatedNodes);
        setSelectedNodes(prev => prev.filter(nodeId => nodeId !== id));
        onNodesChange?.(updatedNodes);
    };

    const handleNodeSelect = (id: string, multi?: boolean) => {
        if (multi) {
            setSelectedNodes(prev =>
                prev.includes(id) ? prev.filter(nodeId => nodeId !== id) : [...prev, id]
            );
        } else {
            setSelectedNodes([id]);
        }
    };

    return (
        <Paper className="h-full flex flex-col">
            <Toolbar variant="dense">
                <Typography variant="h6">Legacy Canvas Scene</Typography>
                <Typography variant="body2" className="ml-4 text-gray-500 dark:text-gray-400">
                    {nodes.length} nodes, {selectedNodes.length} selected
                </Typography>
            </Toolbar>

            <Box className="flex-1 relative overflow-hidden">
                {/* Legacy canvas implementation would go here */}
                <Box className="p-4">
                    <Typography variant="body2" color="text.secondary">
                        Legacy Canvas Implementation (separate state management)
                    </Typography>
                </Box>
            </Box>
        </Paper>
    );
};

// Migrated CanvasScene interface (Phase 2)
/**
 *
 */
interface MigratedCanvasSceneProps {
    initialNodes?: SceneNode[];
    onNodesChange?: (nodes: SceneNode[]) => void;
    capabilities?: {
        dragDrop?: boolean;
        selection?: boolean;
        keyboard?: boolean;
        persistence?: boolean;
        undo?: boolean;
    };
    readonly?: boolean;
}

// MIGRATED CanvasScene component using GenericCanvas (Phase 2)
const MigratedCanvasScene: React.FC<MigratedCanvasSceneProps> = ({
    initialNodes = [],
    onNodesChange,
    capabilities = {
        dragDrop: true,
        selection: true,
        keyboard: true,
        persistence: true,
        undo: true
    },
    readonly = false
}) => {
    // Use generic canvas with scene-specific configuration
    return (
        <GenericCanvas<SceneNode>
            items={initialNodes}
            onItemsChange={onNodesChange || (() => { })}
            capabilities={capabilities}
            viewModes={[
                {
                    id: 'canvas',
                    label: 'Canvas View',
                    icon: '🎨',
                    component: SceneCanvasView
                },
                {
                    id: 'list',
                    label: 'Node List',
                    icon: '📋',
                    component: SceneListView
                },
                {
                    id: 'properties',
                    label: 'Properties',
                    icon: '⚙️',
                    component: ScenePropertiesView
                }
            ]}
            defaultViewMode="canvas"
            itemRenderer={(node) => <SceneNodeRenderer node={node} />}
            toolbarActions={[
                {
                    id: 'add-component',
                    label: 'Add Component',
                    icon: '➕',
                    onClick: (context) => {
                        const components = componentRegistry.list(REGISTRY_NAMESPACES.CANVAS_SCENE);
                        // Show component picker dialog
                        console.log('Available components:', components);
                    }
                },
                {
                    id: 'duplicate',
                    label: 'Duplicate',
                    icon: '📋',
                    onClick: (context) => {
                        context.selectedItems.forEach(item => {
                            context.canvasAPI.createItem({
                                ...item,
                                position: { x: item.position.x + 20, y: item.position.y + 20 },
                                data: { ...item.data, label: `${item.data.label} Copy` }
                            });
                        });
                    },
                    disabled: (context) => context.selectedItems.length === 0
                }
            ]}
            plugins={[
                {
                    id: 'scene-grid',
                    name: 'Scene Grid',
                    component: SceneGridPlugin
                },
                {
                    id: 'scene-rulers',
                    name: 'Scene Rulers',
                    component: SceneRulersPlugin
                }
            ]}
            readonly={readonly}
            persistenceKey="canvas-scene"
        />
    );
};

// Scene-specific view components
const SceneCanvasView: React.FC<{ items: SceneNode[]; onItemSelect: (id: string) => void }> = ({
    items,
    onItemSelect
}) => (
    <Box className="relative w-full h-full" style={{ background: 'linear-gradient(45deg, backgroundSize: '20px 20px' }} >
        {items.map(node => (
            <Box
                key={node.id}
                onClick={() => onItemSelect(node.id)}
                className="absolute border-[2px_solid] cursor-pointer flex items-center justify-center shadow-sm hover:border-blue-600" style={{ left: node.position.x, top: node.position.y, width: node.data.style?.width || 100, height: node.data.style?.height || 60, backgroundColor: node.data.style?.backgroundColor || '#fff', borderColor: node.data.style?.borderColor || '#ddd', borderRadius: node.data.style?.borderRadius || 4 }}
            >
                <Typography variant="body2">{node.data.label}</Typography>
            </Box>
        ))}
    </Box>
);

const SceneListView: React.FC<{ items: SceneNode[]; onItemSelect: (id: string) => void }> = ({
    items,
    onItemSelect
}) => (
    <Box className="p-4">
        <Typography variant="h6" gutterBottom>Scene Nodes</Typography>
        {items.map(node => (
            <Box
                key={node.id}
                onClick={() => onItemSelect(node.id)}
                className="p-4 mb-2 border border-gray-200 dark:border-gray-700 rounded cursor-pointer hover:bg-gray-100"
            >
                <Typography variant="body1">{node.data.label}</Typography>
                <Typography variant="body2" color="text.secondary">
                    Type: {node.data.nodeType} | Position: ({node.position.x}, {node.position.y})
                </Typography>
            </Box>
        ))}
    </Box>
);

const ScenePropertiesView: React.FC<{ items: SceneNode[]; selectedItems: string[] }> = ({
    items,
    selectedItems
}) => {
    const selectedNode = selectedItems.length === 1 ? items.find(item => item.id === selectedItems[0]) : null;

    return (
        <Box className="p-4">
            <Typography variant="h6" gutterBottom>Properties</Typography>
            {selectedNode ? (
                <Box>
                    <Typography variant="body2"><strong>ID:</strong> {selectedNode.id}</Typography>
                    <Typography variant="body2"><strong>Label:</strong> {selectedNode.data.label}</Typography>
                    <Typography variant="body2"><strong>Type:</strong> {selectedNode.data.nodeType}</Typography>
                    <Typography variant="body2">
                        <strong>Position:</strong> ({selectedNode.position.x}, {selectedNode.position.y})
                    </Typography>
                    <Typography variant="body2">
                        <strong>Size:</strong> {selectedNode.data.style?.width || 100} × {selectedNode.data.style?.height || 60}
                    </Typography>
                </Box>
            ) : (
                <Typography variant="body2" color="text.secondary">
                    Select a node to view properties
                </Typography>
            )}
        </Box>
    );
};

// Scene-specific renderer component
const SceneNodeRenderer: React.FC<{ node: SceneNode }> = ({ node }) => (
    <Box
        className="border-[2px_solid] flex items-center justify-center shadow-sm" style={{ width: node.data.style?.width || 100, height: node.data.style?.height || 60, backgroundColor: node.data.style?.backgroundColor || '#fff', borderColor: node.data.style?.borderColor || '#ddd', borderRadius: node.data.style?.borderRadius || 4, background: 'radial-gradient(circle, backgroundSize: '20px 20px' }}
    >
        <Typography variant="body2">{node.data.label}</Typography>
    </Box>
);

// Scene-specific plugins
const SceneGridPlugin: React.FC = () => (
    <Box className="absolute pointer-events-none opacity-[0.3] inset-0" />
);

const SceneRulersPlugin: React.FC = () => (
    <>
        {/* Horizontal ruler */}
        <Box className="absolute top-[0px] left-[0px] right-[0px] h-[20px] bg-[#f0f0f0] border-gray-200 dark:border-gray-700 border-b" />
        {/* Vertical ruler */}
        <Box className="absolute top-[0px] left-[0px] bottom-[0px] w-[20px] bg-[#f0f0f0] border-r border-gray-200 dark:border-gray-700" />
    </>
);

// Migration comparison component
const CanvasSceneMigrationDemo: React.FC = () => {
    const [nodes, setNodes] = useState<SceneNode[]>([
        {
            id: 'node1',
            type: 'scene-node',
            position: { x: 100, y: 100 },
            data: {
                label: 'Component A',
                nodeType: 'component',
                properties: {},
                style: { width: 120, height: 80, backgroundColor: '#e3f2fd' }
            },
            metadata: {
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString()
            }
        },
        {
            id: 'node2',
            type: 'scene-node',
            position: { x: 300, y: 150 },
            data: {
                label: 'Container B',
                nodeType: 'container',
                properties: {},
                style: { width: 140, height: 100, backgroundColor: '#f3e5f5' }
            },
            metadata: {
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString()
            }
        }
    ]);

    return (
        <Box className="h-screen flex">
            {/* Legacy implementation */}
            <Box className="flex-1 border-r border-gray-200 dark:border-gray-700">
                <Typography variant="h6" className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                    Legacy CanvasScene (Before Migration)
                </Typography>
                <LegacyCanvasScene
                    initialNodes={nodes}
                    onNodesChange={setNodes}
                    enableDragDrop
                    enableSelection
                />
            </Box>

            {/* Migrated implementation */}
            <Box className="flex-1">
                <Typography variant="h6" className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                    Migrated CanvasScene (Phase 2)
                </Typography>
                <MigratedCanvasScene
                    initialNodes={nodes}
                    onNodesChange={setNodes}
                    capabilities={{
                        dragDrop: true,
                        selection: true,
                        keyboard: true,
                        persistence: true,
                        undo: true
                    }}
                />
            </Box>
        </Box>
    );
};

export {
    LegacyCanvasScene,
    MigratedCanvasScene,
    CanvasSceneMigrationDemo,
    type SceneNode
};