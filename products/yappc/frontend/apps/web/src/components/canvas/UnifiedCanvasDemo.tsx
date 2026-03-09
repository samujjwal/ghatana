/**
 * Unified Canvas Demo
 * 
 * Demonstrates the new unified canvas approach where users can directly
 * add and interact with different content types without mode switching.
 * 
 * This component shows how the unified system would replace the current
 * mode-based approach.
 * 
 * @doc.type component
 * @doc.purpose Demo of unified canvas functionality
 * @doc.layer product
 * @doc.pattern Demo Component
 */

import React, { useCallback, useMemo } from 'react';
import { Box, Typography, Surface as Paper, Switch, FormControlLabel, Divider } from '@ghatana/ui';
import { ReactFlowProvider, ReactFlow, Background, Controls, MiniMap, type Node, type Edge } from '@xyflow/react';
import { useAtom } from 'jotai';

// Import unified components
import { UnifiedCanvasNode, type UnifiedNodeData } from './unified/UnifiedCanvasNode';
import { UnifiedCanvasToolbar } from './unified/UnifiedCanvasToolbar';
import { nodesAtom, edgesAtom } from './workspace/canvasAtoms';

// Import existing components for comparison
import { CanvasWorkspace } from './CanvasWorkspace';

// Define node types for ReactFlow
const nodeTypes = {
    unified: UnifiedCanvasNode,
};

interface UnifiedCanvasDemoProps {
    projectId: string;
    currentPhase: unknown;
    fowStage: unknown;
}

export const UnifiedCanvasDemo: React.FC<UnifiedCanvasDemoProps> = ({
    projectId,
    currentPhase,
    fowStage
}) => {
    const [nodes, setNodes] = useAtom(nodesAtom);
    const [edges, setEdges] = useAtom(edgesAtom);
    const [showUnified, setShowUnified] = React.useState(true);

    // ReactFlow handlers
    const onNodesChange = useCallback(
        (changes: unknown[]) => {
            setNodes((nds) => {
                // Apply changes to nodes
                const updatedNodes = nds.map(node => {
                    const change = changes.find(c => c.id === node.id);
                    if (change) {
                        return { ...node, ...change };
                    }
                    return node;
                });
                return updatedNodes;
            });
        },
        [setNodes]
    );

    const onEdgesChange = useCallback(
        (changes: unknown[]) => {
            setEdges((eds) => {
                // Apply changes to edges
                const updatedEdges = eds.map(edge => {
                    const change = changes.find(c => c.id === edge.id);
                    if (change) {
                        return { ...edge, ...change };
                    }
                    return edge;
                });
                return updatedEdges;
            });
        },
        [setEdges]
    );

    const onConnect = useCallback(
        (connection: unknown) => {
            const newEdge: Edge = {
                id: `edge-${connection.source}-${connection.target}`,
                source: connection.source!,
                target: connection.target!,
                type: 'smoothstep',
            };
            setEdges((eds) => [...eds, newEdge]);
        },
        [setEdges]
    );

    // Zoom handlers
    const handleZoomIn = useCallback(() => {
        // Implementation would use ReactFlow's zoom controls
        console.log('Zoom in');
    }, []);

    const handleZoomOut = useCallback(() => {
        // Implementation would use ReactFlow's zoom controls
        console.log('Zoom out');
    }, []);

    const handleFitView = useCallback(() => {
        // Implementation would use ReactFlow's fit view
        console.log('Fit view');
    }, []);

    // Demo content
    const demoNodes = useMemo(() => {
        if (showUnified) {
            return nodes;
        }

        // Return empty for original canvas demo
        return [];
    }, [nodes, showUnified]);

    return (
        <Box className="w-full h-screen relative">
            {/* Toggle between unified and original approach */}
            <Paper className="absolute p-4 flex items-center gap-4 top-[16px] right-[16px] z-[1000]">
                <Typography as="p" className="text-sm">
                    Unified Canvas
                </Typography>
                <Switch
                    checked={showUnified}
                    onChange={(e) => setShowUnified(e.target.checked)}
                    size="sm"
                />
                <Typography as="p" className="text-sm">
                    Original
                </Typography>
            </Paper>

            {/* Unified Canvas Approach */}
            {showUnified ? (
                <ReactFlowProvider>
                    <Box className="w-full h-full">
                        <ReactFlow
                            nodes={demoNodes}
                            edges={edges}
                            onNodesChange={onNodesChange}
                            onEdgesChange={onEdgesChange}
                            onConnect={onConnect}
                            nodeTypes={nodeTypes}
                            fitView
                            attributionPosition="bottom-left"
                        >
                            <Background />
                            <Controls />
                            <MiniMap />
                        </ReactFlow>

                        {/* Unified Toolbar */}
                        <UnifiedCanvasToolbar
                            onZoomIn={handleZoomIn}
                            onZoomOut={handleZoomOut}
                            onFitView={handleFitView}
                        />
                    </Box>
                </ReactFlowProvider>
            ) : (
                /* Original Canvas Approach (for comparison) */
                <CanvasWorkspace
                    projectId={projectId}
                    currentPhase={currentPhase}
                    fowStage={fowStage}
                />
            )}

            {/* Info Panel */}
            <Paper className="absolute p-4 bottom-[16px] left-[16px] max-w-[300px] z-[1000]">
                <Typography as="h6" gutterBottom>
                    {showUnified ? 'Unified Canvas' : 'Original Canvas'}
                </Typography>
                <Divider className="my-2" />

                {showUnified ? (
                    <Typography as="p" className="text-sm" color="text.secondary">
                        <strong>Unified Approach:</strong><br />
                        • Click toolbar buttons to add content<br />
                        • Edit content directly in nodes<br />
                        • No mode switching required<br />
                        • All content types coexist<br />
                        • Drag to connect and arrange
                    </Typography>
                ) : (
                    <Typography as="p" className="text-sm" color="text.secondary">
                        <strong>Original Approach:</strong><br />
                        • Switch between modes (navigate, sketch, diagram, code)<br />
                        • Each mode has separate overlay<br />
                        • Mode switching required for different content<br />
                        • Complex state management
                    </Typography>
                )}
            </Paper>
        </Box>
    );
};
