/**
 * Canvas Manager Component
 * 
 * Central component managing the canvas layout and overlays.
 * 
 * UI/UX Improvements:
 * - Removed redundant floating components (PersonaQuickActions, CanvasQuickActions)
 * - Actions consolidated into toolbar
 * - Cleaner layout with fewer overlapping elements
 * - Status bar hidden when canvas is empty (empty state handles this)
 * 
 * @doc.type component
 * @doc.purpose Canvas layout orchestrator
 * @doc.layer product
 * @doc.pattern Layout Component
 */

import React from 'react';
import { Box } from '@ghatana/ui';
import { Panel } from '@xyflow/react';
import { Background } from '@reactflow/background';
import { Controls } from '@reactflow/controls';
import { MiniMap } from '@reactflow/minimap';

import { ReactFlowWrapper } from '@/components/canvas/ReactFlowWrapper';
import { ModeContentRenderer } from '@/components/canvas/modes';
import { EnhancedSketchLayer } from '@/components/canvas/sketch/EnhancedSketchLayer';
import { CanvasToolbar } from './CanvasToolbar';
import { CanvasStatusBar } from '@/components/canvas/CanvasStatusBar';
import { GhostNodeLayer } from '@/components/canvas/ai/GhostNode';
import { ImprovedEmptyState } from "../components/ImprovedEmptyState";
import { HistoryToolbar } from '@/components/canvas/HistoryToolbar';
import { AICommandBar } from '@/components/ai/AICommandBar';

export interface CanvasManagerProps {
  // Refs & Layout
  containerRef: (node: HTMLDivElement | null) => void;
  onKeyDown: (event: React.KeyboardEvent) => void;
  stageSize: { width: number; height: number };

  // React Flow Props
  nodes: unknown[];
  edges: unknown[];
  onInit: (reactFlowInstance: unknown) => void;
  onNodesChange: (changes: unknown) => void;
  onEdgesChange: (changes: unknown) => void;
  onConnect: (connection: unknown) => void;
  onSelectionChange: (params: unknown) => void;
  onNodeDoubleClick: (event: React.MouseEvent, node: unknown) => void;
  getNodeColor: (node: unknown) => string;

  // Mode & Abstraction
  currentMode: unknown;
  abstractionLevel: unknown;
  projectId: string;
  canvasId?: string;
  isReadOnlyPhase: boolean;
  onDrillDown: () => void;
  onZoomOut: () => void;
  onAskAI: () => void;
  onStartBlank: () => void;
  onUseTemplate: () => void;

  // Toolbar Props (Pass-through to CanvasToolbar)
  toolbarProps: unknown; // Using any to simplify passing the huge props object

  // Status Bar Props
  statusBarProps: {
    currentPhase: unknown;
    phases: unknown[];
    technologies: unknown[];
    onPhaseClick: (phase: unknown) => void;
  };

  // AI & Ghost Nodes
  ghostNodes: unknown[];
  onAcceptAISuggestion: (id: string) => void;
  onDismissSuggestion: (id: string) => void;
  onAISubmit?: (prompt: string, options?: unknown) => Promise<void>;

  // Sketch Tool
  activeSketchTool: unknown;

  children?: React.ReactNode; // For Performance/Accessibility types that overlay
}

// Styles
const BACKGROUND_STYLE = { pointerEvents: 'none' } as const;
const MINIMAP_STYLE = { backgroundColor: 'rgba(255,255,255,0.9)', border: '1px solid #ccc' } as const;
const PANEL_STYLE = { zIndex: 20 } as const;
const PANEL_BOX_STYLE: React.CSSProperties = {
  padding: 8,
  backgroundColor: 'rgba(255,255,255,0.9)',
  borderRadius: 4,
  fontSize: '0.8em',
};
const PANEL_BOX_LEFT_STYLE: React.CSSProperties = {
  padding: 8,
  backgroundColor: 'rgba(255,255,255,0.9)',
  borderRadius: 4,
};

export function CanvasManager({
  containerRef,
  onKeyDown,
  stageSize,
  nodes,
  edges,
  onInit,
  onNodesChange,
  onEdgesChange,
  onConnect,
  onSelectionChange,
  onNodeDoubleClick,
  getNodeColor,
  currentMode,
  abstractionLevel,
  projectId,
  canvasId,
  isReadOnlyPhase,
  onDrillDown,
  onZoomOut,
  onAskAI,
  onStartBlank,
  onUseTemplate,
  toolbarProps,
  statusBarProps,
  ghostNodes,
  onAcceptAISuggestion,
  onDismissSuggestion,
  onAISubmit,
  activeSketchTool,
  children
}: CanvasManagerProps) {
  // Determine if canvas has content
  const hasContent = nodes.length > 0;

  return (
    <Box
      id="canvas-drop-zone"
      data-testid="canvas-drop-zone"
      ref={containerRef}
      className="flex-1 h-full relative overflow-hidden bg-gray-50 dark:bg-gray-950"
    >
      <Box
        data-testid="react-flow-wrapper"
        className="absolute pt-[48px] pb-[40px] inset-0" tabIndex={0}
        role="application"
        aria-label="Canvas workspace for creating and editing diagrams"
        onKeyDown={onKeyDown}
      >
        {/* Mode-specific Content Renderer - wraps ReactFlow with mode context */}
        <ModeContentRenderer
          mode={currentMode}
          level={abstractionLevel}
          projectId={projectId}
          canvasId={canvasId}
          hasContent={hasContent}
          onAskAI={onAskAI}
          onGetStarted={onUseTemplate}
          onDrillDown={onDrillDown}
          onZoomOut={onZoomOut}
          readOnly={isReadOnlyPhase}
        >
          <ReactFlowWrapper
            nodes={nodes}
            edges={edges}
            onInit={onInit}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onSelectionChange={onSelectionChange}
            onNodeDoubleClick={onNodeDoubleClick}
          >
            <Background
              color="rgba(0, 0, 0, 0.05)"
              gap={50}
              size={2}
              style={BACKGROUND_STYLE}
            />
            <Controls />
            <MiniMap
              nodeStrokeWidth={3}
              nodeColor={getNodeColor}
              style={MINIMAP_STYLE}
            />

            <Panel position="top-right" style={PANEL_STYLE}>
              <Box style={PANEL_BOX_STYLE}>
                Nodes: {nodes.length} | Edges: {edges.length}
              </Box>
            </Panel>

            <Panel position="top-left" style={PANEL_STYLE}>
              <Box style={PANEL_BOX_LEFT_STYLE}>
                <HistoryToolbar projectId={projectId} canvasId={canvasId || 'main-canvas'} size="small" />
              </Box>
            </Panel>
          </ReactFlowWrapper>
        </ModeContentRenderer>

        <EnhancedSketchLayer
          width={stageSize.width}
          height={stageSize.height}
          activeTool={activeSketchTool}
        />

        {/* Unified Canvas Toolbar - Always visible */}
        <CanvasToolbar {...toolbarProps} />

        {/* AI Command Bar - Persistent usage */}
        {onAISubmit && (
          <Box className="absolute bottom-[80px] left-[50%] z-[100] w-[600px] max-w-[90%]" style={{ transform: 'translateX(-50%)' }} >
            <AICommandBar
              currentMode={currentMode}
              currentPhase={statusBarProps.currentPhase}
              isProcessing={toolbarProps.isAnalyzing || toolbarProps.isGenerating}
              onSubmit={onAISubmit}
              onOpenFullPanel={onAskAI}
            />
          </Box>
        )}

        {/* Bottom Status Bar - Only show when canvas has content */}
        {hasContent && (
          <CanvasStatusBar
            currentPhase={statusBarProps.currentPhase}
            phases={statusBarProps.phases}
            technologies={statusBarProps.technologies}
            onPhaseClick={statusBarProps.onPhaseClick}
            defaultExpanded={false}
          />
        )}

        {/* Ghost Node Layer for AI Suggestions - Only when relevant */}
        {ghostNodes.length > 0 && (
          <GhostNodeLayer
            nodes={ghostNodes}
            onAccept={onAcceptAISuggestion}
            onDismiss={onDismissSuggestion}
          />
        )}

        {/* Empty Canvas State - Clean centered UI when no content */}
        {!hasContent && (
          <ImprovedEmptyState
            onSelectTemplate={onUseTemplate}
            onBlankCanvas={onStartBlank}
            onAIAssistant={onAskAI}
          />
        )}
      </Box>

      {/* Render children (like Performance Panel) inside the container */}
      {children}
    </Box>
  );
}
