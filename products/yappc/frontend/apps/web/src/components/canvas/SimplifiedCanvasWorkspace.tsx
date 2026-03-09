/**
 * Simplified Canvas Workspace
 * 
 * A simplified version of CanvasWorkspace that removes the old mode-based system
 * and focuses purely on the unified approach. This is Phase 3 of the migration.
 * 
 * @doc.type component
 * @doc.purpose Simplified canvas without mode switching
 * @doc.layer product
 * @doc.pattern Refactored Component
 */

import React, { useCallback, useMemo, useState } from 'react';
import { Box, Spinner as CircularProgress, Typography } from '@ghatana/ui';
import { ReactFlowProvider, ReactFlow, Background, Controls, MiniMap, type Node, type Edge, MarkerType, applyNodeChanges, applyEdgeChanges, type NodeChange, type EdgeChange, type Connection } from '@xyflow/react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { useGateStatus, useNextBestTask, useDerivedPersona, useArtifacts, useCreateArtifact } from '@/hooks/useLifecycleData';
import { LifecyclePhase } from '@/types/lifecycle';
import { FOWStage } from '@/types/fow-stages';
import type { AbstractionLevel } from '../../types/abstractionLevel';

import {
    nodesAtom, edgesAtom, suppressGeneratedSyncAtom, selectedNodesAtom,
    activePersonaAtom, isAIModalOpenAtom, isProjectSwitcherOpenAtom, isInspectorOpenAtom,
    selectedArtifactAtom, isSearchOpenAtom, quickCreateMenuPositionAtom,
    draggedTemplateAtom, copiedNodesAtom, ghostNodesAtom,
} from './workspace';
import { UnifiedLeftPanel } from './UnifiedLeftPanel';
import { ArtifactNode, type ArtifactNodeData } from './nodes/ArtifactNode';
import { SimpleUnifiedNode } from './unified/SimpleUnifiedNode';
import { EnhancedUnifiedNode } from './unified/EnhancedUnifiedNode';
import { DependencyEdge, type DependencyEdgeData } from './edges';
import { EnhancedSketchLayer } from './sketch/EnhancedSketchLayer';
import { SketchToolbar } from './toolbar/SketchToolbar';
import { MermaidDiagram } from './diagram/MermaidDiagram';
import { DiagramToolbar } from './toolbar/DiagramToolbar';
import { UnifiedPlaygroundToolbar } from './UnifiedPlaygroundToolbar';

export interface SimplifiedCanvasWorkspaceProps {
    projectId: string;
    currentPhase: LifecyclePhase;
    fowStage: FOWStage;
}

/**
 * Simplified Canvas Workspace - Unified approach only
 * 
 * Removes mode switching and focuses on direct content manipulation.
 */
export const SimplifiedCanvasWorkspace: React.FC<SimplifiedCanvasWorkspaceProps> = ({
    projectId,
    currentPhase,
    fowStage
}) => {
    // State management for canvas atoms
    const setNodesAtom = useSetAtom(nodesAtom);
    const setEdgesAtom = useSetAtom(edgesAtom);
    const setSuppress = useSetAtom(suppressGeneratedSyncAtom);
    const setSelected = useSetAtom(selectedNodesAtom);
    const setGhosts = useSetAtom(ghostNodesAtom);

    // Canvas state
    const nodes = useAtomValue(nodesAtom);
    const edges = useAtomValue(edgesAtom);
    const [reactFlowInstance, setReactFlowInstance] = useState<unknown>(null);
    const suppressGeneratedSync = useAtomValue(suppressGeneratedSyncAtom);

    // Data hooks
    const { data: gateStatus } = useGateStatus(projectId, fowStage);
    const { data: nextTask } = useNextBestTask(projectId, currentPhase);
    const { data: personaData } = useDerivedPersona({ projectId, phase: currentPhase, fowStage });
    const { data: artifacts } = useArtifacts(projectId);
    const { mutateAsync: createArtifact } = useCreateArtifact();

    // Show loading fallback while FOW stage is not available
    if (fowStage === undefined) {
        return (
            <Box className="w-full h-full flex items-center justify-center flex-col">
                <CircularProgress />
                <Typography as="p" className="mt-4">Loading workspace…</Typography>
            </Box>
        );
    }

    // ReactFlow change handlers
    const onNodesChange = useCallback(
        (changes: NodeChange[]) => {
            setNodesAtom((nds) => applyNodeChanges(changes, nds));
        },
        [setNodesAtom]
    );

    const onEdgesChange = useCallback(
        (changes: EdgeChange[]) => {
            setEdgesAtom((eds) => applyEdgeChanges(changes, eds));
        },
        [setEdgesAtom]
    );

    const onConnect = useCallback(
        (connection: Connection) => {
            const newEdge: Edge<DependencyEdgeData> = {
                id: `edge-${connection.source}-${connection.target}`,
                source: connection.source!,
                target: connection.target!,
                type: 'dependency',
                markerEnd: {
                    type: MarkerType.ArrowClosed,
                    color: '#1976d2',
                },
                data: {
                    label: 'requires',
                    type: 'requires'
                }
            };
            setEdgesAtom((eds) => [...eds, newEdge]);
        },
        [setEdgesAtom]
    );

    // Node types registry - Enhanced unified nodes
    const nodeTypes = useMemo(() => ({
        artifact: ArtifactNode,
        simpleUnified: SimpleUnifiedNode,
        enhancedUnified: EnhancedUnifiedNode,
    }), []);

    // Edge types registry
    const edgeTypes = useMemo(() => ({
        dependency: DependencyEdge,
    }), []);

    // Create dependency edges from artifact relationships
    const generatedEdges: Edge<DependencyEdgeData>[] = useMemo(() => {
        if (!artifacts) return [];

        return artifacts
            .filter(artifact => artifact.dependencies && artifact.dependencies.length > 0)
            .flatMap(artifact =>
                artifact.dependencies.map(dep => ({
                    id: `edge-${dep.id}-${artifact.id}`,
                    source: dep.id,
                    target: artifact.id,
                    type: 'dependency' as const,
                    markerEnd: {
                        type: MarkerType.ArrowClosed,
                        color: '#1976d2',
                    },
                    data: {
                        label: 'requires',
                        type: 'requires'
                    }
                }))
            );
    }, [artifacts]);

    // Apply styling to nodes
    const styledNodes = useMemo(() => {
        return nodes.map(node => ({
            ...node,
            style: {
                ...node.style,
                transition: 'opacity 200ms ease',
            },
        }));
    }, [nodes]);

    return (
        <Box className="w-full h-full relative overflow-hidden">
            <ReactFlowProvider>
                <ReactFlow
                    nodes={styledNodes}
                    edges={generatedEdges}
                    nodeTypes={nodeTypes}
                    edgeTypes={edgeTypes}
                    onNodesChange={onNodesChange}
                    onEdgesChange={onEdgesChange}
                    onConnect={onConnect}
                    onMove={(_, viewport) => console.log('Viewport changed:', viewport)}
                    onInit={setReactFlowInstance}
                    fitView
                    minZoom={0.1}
                    maxZoom={2}
                    panOnDrag={true}
                    panOnScroll={true}
                    zoomOnScroll={true}
                    nodesDraggable={true}
                    nodesConnectable={true}
                    elementsSelectable={true}
                    selectNodesOnDrag={false}
                    style={{
                        backgroundColor: 'background.default',
                    }}
                >
                    <Background />
                    <Controls />
                    <MiniMap />
                </ReactFlow>

                {/* Unified Playground Toolbar */}
                <UnifiedPlaygroundToolbar />
            </ReactFlowProvider>
        </Box>
    );
};
