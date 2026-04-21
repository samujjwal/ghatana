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
import { Box, Spinner as CircularProgress, Typography } from '@ghatana/design-system';
import { ReactFlowProvider, ReactFlow, Background, Controls, MiniMap, MarkerType, applyNodeChanges, applyEdgeChanges, type Edge, type EdgeTypes, type NodeChange, type EdgeChange, type Connection, type NodeTypes } from '@xyflow/react';
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

type CanvasDependencyEdgeData = DependencyEdgeData & Record<string, unknown>;

type ArtifactDependency = {
    id: string;
};

type ArtifactWithDependencies = {
    id: string;
    dependencies?: ArtifactDependency[];
};

export interface SimplifiedCanvasWorkspaceProps {
    projectId: string;
    currentPhase: LifecyclePhase;
    flowStage: FOWStage;
}

/**
 * Simplified Canvas Workspace - Unified approach only
 * 
 * Removes mode switching and focuses on direct content manipulation.
 */
export const SimplifiedCanvasWorkspace: React.FC<SimplifiedCanvasWorkspaceProps> = ({
    projectId,
    currentPhase,
    flowStage
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
    const { data: gateStatus } = useGateStatus(projectId, flowStage);
    const { data: nextTask } = useNextBestTask(projectId, currentPhase);
    const { data: personaData } = useDerivedPersona({ projectId, phase: currentPhase, flowStage });
    const { data: artifacts } = useArtifacts(projectId);
    const { mutateAsync: createArtifact } = useCreateArtifact();

    // Show loading fallback while FOW stage is not available
    if (flowStage === undefined) {
        return (
            <Box className="w-full h-full flex items-center justify-center flex-col">
                <CircularProgress />
                <Typography className="mt-4">Loading workspace…</Typography>
            </Box>
        );
    }

    // ReactFlow change handlers
    const onNodesChange = useCallback(
        (changes: NodeChange[]) => {
            setNodesAtom((nds) => applyNodeChanges(changes, nds) as typeof nds);
        },
        [setNodesAtom]
    );

    const onEdgesChange = useCallback(
        (changes: EdgeChange[]) => {
            setEdgesAtom((eds) => applyEdgeChanges(changes, eds) as typeof eds);
        },
        [setEdgesAtom]
    );

    const onConnect = useCallback(
        (connection: Connection) => {
            if (!connection.source || !connection.target) {
                return;
            }

            const newEdge: Edge<CanvasDependencyEdgeData> = {
                id: `edge-${connection.source}-${connection.target}`,
                source: connection.source,
                target: connection.target,
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
            setEdgesAtom((eds) => [...eds, newEdge as typeof eds[number]]);
        },
        [setEdgesAtom]
    );

    // Node types registry - Enhanced unified nodes
    const nodeTypes = useMemo<NodeTypes>(() => ({
        artifact: ArtifactNode,
        simpleUnified: SimpleUnifiedNode,
        enhancedUnified: EnhancedUnifiedNode,
    }) as NodeTypes, []);

    // Edge types registry
    const edgeTypes = useMemo<EdgeTypes>(() => ({
        dependency: DependencyEdge,
    }) as EdgeTypes, []);

    // Create dependency edges from artifact relationships
    const generatedEdges = useMemo(() => {
        if (!artifacts) return [];

        const artifactItems = artifacts as ArtifactWithDependencies[];

        return artifactItems
            .filter(artifact => artifact.dependencies && artifact.dependencies.length > 0)
            .flatMap(artifact =>
                artifact.dependencies!.map((dep: ArtifactDependency) => ({
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
                }) as Edge<CanvasDependencyEdgeData>)
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
