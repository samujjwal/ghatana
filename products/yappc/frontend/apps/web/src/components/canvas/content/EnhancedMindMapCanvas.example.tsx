/**
 * Enhanced Mind Map Canvas (Integration Example)
 * 
 * Demonstrates integration of CanvasToolbar with undo/redo and export.
 * This is an example of how to enhance existing canvases.
 * 
 * @doc.type component
 * @doc.purpose Example of canvas enhancement integration
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo, useRef, useEffect } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Button,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { CanvasToolbar, useCanvasToolbar } from '../CanvasToolbar';
import { createCommand } from '../../../utils/canvasHistory';

interface MindMapNode {
    id: string;
    label: string;
    level: number;
    parentId: string | null;
    children: string[];
    color: string;
    notes?: string;
    tags?: string[];
    position: { x: number; y: number };
}

// Initial mock data
const INITIAL_NODES: MindMapNode[] = [
    {
        id: 'root',
        label: 'E-Commerce Platform',
        level: 0,
        parentId: null,
        children: ['auth', 'products'],
        color: '#6366F1',
        notes: 'Central platform concept',
        tags: ['core', 'system'],
        position: { x: 50, y: 15 },
    },
    {
        id: 'auth',
        label: 'Authentication',
        level: 1,
        parentId: 'root',
        children: [],
        color: '#8B5CF6',
        notes: 'User identity management',
        tags: ['security', 'user'],
        position: { x: 30, y: 35 },
    },
    {
        id: 'products',
        label: 'Product Catalog',
        level: 1,
        parentId: 'root',
        children: [],
        color: '#10B981',
        notes: 'Product management',
        position: { x: 70, y: 35 },
    },
];

export const EnhancedMindMapCanvas = () => {
    const canvasRef = useRef<HTMLDivElement>(null);
    const [nodes, setNodes] = useState<MindMapNode[]>(INITIAL_NODES);
    const [selectedNode, setSelectedNode] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [editingNode, setEditingNode] = useState<string | null>(null);

    // Setup toolbar with history and export
    const { history, handleExport } = useCanvasToolbar(
        nodes,
        canvasRef,
        'brainstorm',
        'system'
    );

    // Computed values
    const filteredNodes = useMemo(() => {
        if (!searchQuery) return nodes;
        return nodes.filter(
            node =>
                node.label.toLowerCase().includes(searchQuery.toLowerCase()) ||
                node.notes?.toLowerCase().includes(searchQuery.toLowerCase())
        );
    }, [nodes, searchQuery]);

    const connections = useMemo(() => {
        const conns: Array<{ from: { x: number; y: number }; to: { x: number; y: number } }> = [];
        nodes.forEach(node => {
            if (node.parentId) {
                const parent = nodes.find(n => n.id === node.parentId);
                if (parent) {
                    conns.push({
                        from: parent.position,
                        to: node.position,
                    });
                }
            }
        });
        return conns;
    }, [nodes]);

    // Operations with history tracking
    const addNode = () => {
        const newNode: MindMapNode = {
            id: `node-${Date.now()}`,
            label: 'New Idea',
            level: 1,
            parentId: selectedNode || 'root',
            children: [],
            color: '#6366F1',
            position: { x: Math.random() * 80 + 10, y: Math.random() * 60 + 20 },
        };

        const updatedNodes = history.execute(
            createCommand(
                nodes,
                [...nodes, newNode],
                'Add node'
            )
        );
        setNodes(updatedNodes);
    };

    const deleteNode = (nodeId: string) => {
        if (nodeId === 'root') return; // Can't delete root

        const updatedNodes = history.execute(
            createCommand(
                nodes,
                nodes.filter(n => n.id !== nodeId && n.parentId !== nodeId),
                'Delete node'
            )
        );
        setNodes(updatedNodes);
        if (selectedNode === nodeId) {
            setSelectedNode(null);
        }
    };

    const updateNodeLabel = (nodeId: string, newLabel: string) => {
        const updatedNodes = history.execute(
            createCommand(
                nodes,
                nodes.map(n => n.id === nodeId ? { ...n, label: newLabel } : n),
                'Update node label'
            )
        );
        setNodes(updatedNodes);
    };

    const hasContent = nodes.length > 0;

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Create Mind Map',
                    onClick: () => setNodes(INITIAL_NODES),
                },
            }}
        >
            {/* Integrated Toolbar with Undo/Redo/Export */}
            <CanvasToolbar
                history={history}
                onUndo={(newState) => newState && setNodes(newState)}
                onRedo={(newState) => newState && setNodes(newState)}
                onExport={handleExport}
                showHistory={true}
                showExport={true}
            />

            <Box className="flex h-full flex-col">
                {/* Search and Actions Bar */}
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)]">
                    <Box className="flex gap-4 items-center">
                        <TextField
                            size="small"
                            placeholder="Search nodes..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="flex-1"
                        />
                        <Button
                            variant="contained"
                            size="small"
                            onClick={addNode}
                        >
                            + Add Node
                        </Button>
                        <Typography variant="caption" color="text.secondary">
                            {nodes.length} nodes
                        </Typography>
                    </Box>
                </Box>

                {/* Canvas Area - Now with ref for export */}
                <Box
                    ref={canvasRef}
                    className="flex-1 relative overflow-auto bg-[#F9FAFB] p-4"
                >
                    {/* SVG for connections */}
                    <svg
                        style={{
                            position: 'absolute',
                            top: 0,
                            left: 0,
                            width: '100%',
                            height: '100%',
                            pointerEvents: 'none',
                        }}
                    >
                        {connections.map((conn, idx) => (
                            <line
                                key={idx}
                                x1={`${conn.from.x}%`}
                                y1={`${conn.from.y}%`}
                                x2={`${conn.to.x}%`}
                                y2={`${conn.to.y}%`}
                                stroke="#94A3B8"
                                strokeWidth="2"
                                strokeDasharray="5,5"
                            />
                        ))}
                    </svg>

                    {/* Nodes */}
                    {filteredNodes.map(node => (
                        <Paper
                            key={node.id}
                            elevation={selectedNode === node.id ? 8 : 2}
                            onClick={() => setSelectedNode(node.id)}
                            onDoubleClick={() => setEditingNode(node.id)}
                            className="absolute" style={{ left: `${node.position.x, color: 'node.color' }}
                        >
                            {editingNode === node.id ? (
                                <TextField
                                    autoFocus
                                    value={node.label}
                                    onChange={(e) => updateNodeLabel(node.id, e.target.value)}
                                    onBlur={() => setEditingNode(null)}
                                    onKeyDown={(e) => {
                                        if (e.key === 'Enter') setEditingNode(null);
                                    }}
                                    size="small"
                                    fullWidth
                                />
                            ) : (
                                <Typography
                                    variant="subtitle2"
                                    className="font-bold mb-2" >
                                    {node.label}
                                </Typography>
                            )}

                            {node.notes && (
                                <Typography variant="caption" color="text.secondary" className="block mb-2">
                                    {node.notes}
                                </Typography>
                            )}

                            {node.tags && node.tags.length > 0 && (
                                <Box className="flex gap-1 flex-wrap mt-2">
                                    {node.tags.map(tag => (
                                        <Chip
                                            key={tag}
                                            label={tag}
                                            size="small"
                                            className="h-[20px] text-[0.7rem]"
                                        />
                                    ))}
                                </Box>
                            )}

                            {selectedNode === node.id && node.id !== 'root' && (
                                <Button
                                    size="small"
                                    color="error"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        deleteNode(node.id);
                                    }}
                                    className="mt-2"
                                >
                                    Delete
                                </Button>
                            )}
                        </Paper>
                    ))}
                </Box>

                {/* Node Details Panel */}
                {selectedNode && (
                    <Box className="p-4 bg-white dark:bg-gray-900" style={{ borderTop: '1px solid rgba(0, 0, 0, 0.12)' }} >
                        <Typography variant="subtitle2" gutterBottom>
                            Selected: {nodes.find(n => n.id === selectedNode)?.label}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                            Level: {nodes.find(n => n.id === selectedNode)?.level} |
                            Children: {nodes.find(n => n.id === selectedNode)?.children.length}
                        </Typography>
                    </Box>
                )}
            </Box>
        </BaseCanvasContent>
    );
};

export default EnhancedMindMapCanvas;
