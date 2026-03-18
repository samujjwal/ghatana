/**
 * CanvasReactFlowSurface
 *
 * Encapsulates the ReactFlow canvas surface: the ReactFlow instance, spatial
 * zones, ghost nodes, grid, controls, minimap, alignment guides, sketch
 * overlay, and mode-specific toolbars. Extracted from CanvasWorkspace to keep
 * the orchestrator file focused on data wiring.
 *
 * @doc.type component
 * @doc.purpose ReactFlow canvas surface with overlays and mode toolbars
 * @doc.layer product
 * @doc.pattern Extracted Component
 */

import React from 'react';
import { Box } from '@ghatana/design-system';
import {
    ReactFlow, Panel, Background, Controls, MiniMap,
    type Node, type Edge, type NodeTypes, type EdgeTypes,
    type ReactFlowInstance, type NodeChange, type EdgeChange, type Connection,
} from '@xyflow/react';
import { type LifecyclePhase } from '@/types/lifecycle';
import { SpatialZones, GhostNodes } from './workspace';
import { type ArtifactNodeData } from './nodes/ArtifactNode';
import { type ComputedViewResult } from './hooks/useComputedView';
import { AlignmentGuides } from './AlignmentGuides';
import { AlignmentToolbar } from './toolbar/AlignmentToolbar';
import { EnhancedSketchLayer } from './sketch/EnhancedSketchLayer';
import { SketchToolbar } from './toolbar/SketchToolbar';
import { DiagramToolbar } from './toolbar/DiagramToolbar';

// ── Types ─────────────────────────────────────────────────────────────────

interface DragDropSurface {
    isDragOver: boolean;
    handleCanvasDrop: (e: React.DragEvent) => void;
    handleCanvasDragOver: (e: React.DragEvent) => void;
    handleCanvasDragLeave: (e: React.DragEvent) => void;
    onNodeDragStart: (e: React.MouseEvent, node: Node) => void;
    onNodeDrag: (e: React.MouseEvent, node: Node) => void;
    onNodeDragStop: (e: React.MouseEvent, node: Node) => void;
}

export interface CanvasReactFlowSurfaceProps {
    // ── Refs & dimensions ────────────────────────────────────────────────
    canvasSurfaceRef: React.RefObject<HTMLDivElement>;
    canvasSize: { width: number; height: number };
    // ── Interaction mode ─────────────────────────────────────────────────
    interactionMode: 'navigate' | 'sketch' | 'code' | 'diagram';
    currentPhase: LifecyclePhase;
    // ── Theme ────────────────────────────────────────────────────────────
    minimapMaskColor: string;
    // ── Sketch layer ─────────────────────────────────────────────────────
    sketchTool: string;
    sketchColor: string;
    sketchStrokeWidth: number;
    // ── ReactFlow data ───────────────────────────────────────────────────
    styledNodes: Node[];
    computedView: ComputedViewResult;
    nodeTypes: NodeTypes;
    edgeTypes: EdgeTypes;
    // ── ReactFlow callbacks ──────────────────────────────────────────────
    onNodesChange: (changes: NodeChange[]) => void;
    onEdgesChange: (changes: EdgeChange[]) => void;
    onConnect: (connection: Connection) => void;
    handleNodeClick: (event: React.MouseEvent, node: Node) => void;
    handleNodeContextMenu: (event: React.MouseEvent, node: Node) => void;
    handleCanvasDoubleClick: (event: React.MouseEvent) => void;
    setSelectedNodes: (ids: string[]) => void;
    setReactFlowInstance: (instance: ReactFlowInstance) => void;
    setCamera: (camera: { x: number; y: number; zoom: number; initialized: boolean }) => void;
    // ── Aggregated hook refs ─────────────────────────────────────────────
    dragDrop: DragDropSurface;
    handleZoomToPhase: (phase: LifecyclePhase) => void;
    handleGhostNodeCreate: (template: unknown, position: { x: number; y: number }) => void;
    setIsAIModalOpen: (open: boolean) => void;
}

// ── Component ─────────────────────────────────────────────────────────────

export const CanvasReactFlowSurface: React.FC<CanvasReactFlowSurfaceProps> = ({
    canvasSurfaceRef,
    canvasSize,
    interactionMode,
    currentPhase,
    minimapMaskColor,
    sketchTool,
    sketchColor,
    sketchStrokeWidth,
    styledNodes,
    computedView,
    nodeTypes,
    edgeTypes,
    onNodesChange,
    onEdgesChange,
    onConnect,
    handleNodeClick,
    handleNodeContextMenu,
    handleCanvasDoubleClick,
    setSelectedNodes,
    setReactFlowInstance,
    setCamera,
    dragDrop,
    handleZoomToPhase,
    handleGhostNodeCreate,
    setIsAIModalOpen,
}) => (
    <Box
        id="canvas-surface"
        ref={canvasSurfaceRef}
        className={`flex-1 relative ${dragDrop.isDragOver ? 'ring-2 ring-primary-400 ring-inset' : ''}`}
        onDoubleClick={handleCanvasDoubleClick}
        onDrop={interactionMode === 'navigate' ? dragDrop.handleCanvasDrop : undefined}
        onDragOver={interactionMode === 'navigate' ? dragDrop.handleCanvasDragOver : undefined}
        onDragLeave={interactionMode === 'navigate' ? dragDrop.handleCanvasDragLeave : undefined}
        tabIndex={0}
        aria-label="Canvas surface — use Tab to navigate nodes, arrow keys to move them"
    >
            <ReactFlow
                nodes={styledNodes}
                edges={computedView.visibleEdges as unknown as Edge[]}
                nodeTypes={nodeTypes}
                edgeTypes={edgeTypes}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                onNodeClick={handleNodeClick}
                onNodeContextMenu={handleNodeContextMenu}
                onNodeDragStart={dragDrop.onNodeDragStart}
                onNodeDrag={dragDrop.onNodeDrag}
                onNodeDragStop={dragDrop.onNodeDragStop}
                onSelectionChange={({ nodes: sel }) => {
                    setSelectedNodes(sel.map(n => n.id));
                }}
                onlyRenderVisibleElements
                nodesFocusable
                onMove={(_, vp) => setCamera({ ...vp, initialized: true })}
                onInit={(rf) => {
                    setReactFlowInstance(rf);
                    const vp = rf.getViewport();
                    setCamera({ ...vp, initialized: true });
                }}
                fitView
                snapToGrid
                snapGrid={[16, 16]}
                minZoom={0.1}
                maxZoom={2}
                zoomOnPinch
                panOnDrag={interactionMode === 'navigate'}
                panOnScroll
                zoomOnScroll
                zoomActivationKeyCode="Meta"
                nodesDraggable={interactionMode === 'navigate'}
                nodesConnectable={interactionMode === 'navigate'}
                elementsSelectable={interactionMode === 'navigate'}
                selectNodesOnDrag={false}
                noDragClassName="nodrag"
                noWheelClassName="nowheel"
                className="dark:bg-gray-950"
                style={{
                    opacity: interactionMode === 'sketch' ? 0.6 : 1,
                    transition: 'opacity 200ms ease',
                }}
            >
                <SpatialZones currentPhase={currentPhase} onZoneClick={handleZoomToPhase} />

                <GhostNodes
                    currentPhase={currentPhase}
                    artifactCount={computedView.totalNodes}
                    onCreateArtifact={handleGhostNodeCreate as Parameters<typeof GhostNodes>[0]['onCreateArtifact']}
                    onAISuggestion={() => setIsAIModalOpen(true)}
                />

                {/* Grid — dark-mode aware */}
                <Background
                    color="var(--canvas-grid-color, #aaa)"
                    gap={16}
                    className="dark:[--canvas-grid-color:#444]"
                />

                <Controls
                    style={{
                        margin: 16, display: 'flex', gap: 4, border: 'none',
                        boxShadow: '0 4px 12px rgba(0,0,0,0.1)', borderRadius: 12,
                        overflow: 'hidden',
                    }}
                    className="bg-white dark:bg-gray-800"
                    showInteractive={false}
                    aria-label="Zoom controls"
                />

                <MiniMap
                    pannable
                    zoomable
                    nodeColor={(node) => {
                        const data = node.data as unknown as ArtifactNodeData;
                        if (data.status === 'blocked') return 'var(--color-error, #ef5350)';
                        if (data.status === 'complete') return 'var(--color-success, #66bb6a)';
                        if (data.status === 'in-progress') return 'var(--color-info, #42a5f5)';
                        return 'var(--color-muted, #e0e0e0)';
                    }}
                    maskColor={minimapMaskColor}
                    className="dark:bg-gray-900/90"
                    style={{
                        border: '1px solid rgba(0,0,0,0.05)', borderRadius: 16,
                        margin: 20, height: 120, width: 180,
                        boxShadow: '0 8px 24px rgba(0,0,0,0.12)',
                    }}
                    aria-label="Minimap overview"
                />

                {/* Context-sensitive alignment toolbar — appears when ≥2 nodes selected */}
                <Panel position="top-center">
                    <AlignmentToolbar />
                </Panel>
            </ReactFlow>

            <AlignmentGuides />

            {interactionMode === 'sketch' && (
                <Box className="absolute inset-0 pointer-events-auto z-[30]">
                    <EnhancedSketchLayer
                        width={canvasSize.width}
                        height={canvasSize.height}
                        activeTool={sketchTool}
                        config={{ color: sketchColor, strokeWidth: sketchStrokeWidth, fill: 'transparent' }}
                    />
                </Box>
            )}
        {interactionMode === 'sketch' && <SketchToolbar />}
        {interactionMode === 'diagram' && <DiagramToolbar />}
    </Box>
);
