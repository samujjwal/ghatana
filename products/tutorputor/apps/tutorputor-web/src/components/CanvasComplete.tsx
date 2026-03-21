import React, { useCallback, useState, useEffect } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  addEdge,
  Connection,
  Edge,
  Node,
  Panel,
  ReactFlowProvider,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Provider, useAtom } from 'jotai';
import { canvasNodesAtom, canvasEdgesAtom, selectedToolAtom, collaborationStateAtom } from '../state/canvasAtoms';
import { CanvasToolbar } from './CanvasToolbar';
import { CollaborationPresence } from './CollaborationPresence';
import { AIAssistant } from './AIAssistant';
import { useCanvasActions } from '../hooks/useCanvasActions';

import './CanvasComplete.css';

export interface CanvasCompleteProps {
  /** Unique canvas identifier */
  canvasId?: string;
  /** Enable real-time collaboration */
  enableCollaboration?: boolean;
  /** Enable AI assistant features */
  enableAI?: boolean;
  /** Initial nodes */
  initialNodes?: Node[];
  /** Initial edges */
  initialEdges?: Edge[];
  /** Callback when canvas changes */
  onChange?: (nodes: Node[], edges: Edge[]) => void;
  /** Read-only mode */
  readOnly?: boolean;
}

/**
 * CanvasComplete - Main canvas component with collaboration and AI features
 */
function CanvasCompleteInner({
  canvasId = 'default',
  enableCollaboration = false,
  enableAI = false,
  initialNodes = [],
  initialEdges = [],
  onChange,
  readOnly = false,
}: CanvasCompleteProps) {
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
  const [selectedTool] = useAtom(selectedToolAtom);
  const [collaborationState] = useAtom(collaborationStateAtom);
  const canvasActions = useCanvasActions();

  // Sync with external state
  useEffect(() => {
    if (onChange) {
      onChange(nodes, edges);
    }
  }, [nodes, edges, onChange]);

  const onConnect = useCallback(
    (connection: Connection) => {
      if (!readOnly) {
        setEdges((eds) => addEdge(connection, eds));
      }
    },
    [setEdges, readOnly]
  );

  const onNodeClick = useCallback(
    (_: React.MouseEvent, node: Node) => {
      if (selectedTool === 'select') {
        // Handle selection
        canvasActions.selectNode(node.id);
      }
    },
    [selectedTool, canvasActions]
  );

  const onPaneClick = useCallback(() => {
    canvasActions.clearSelection();
  }, [canvasActions]);

  return (
    <div className="canvas-complete" data-testid="canvas-complete">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onNodeClick={onNodeClick}
        onPaneClick={onPaneClick}
        fitView
        attributionPosition="bottom-left"
      >
        <Background />
        <Controls />
        <MiniMap />
        
        {/* Toolbar */}
        <Panel position="top-left">
          <CanvasToolbar />
        </Panel>

        {/* Collaboration Presence */}
        {enableCollaboration && collaborationState.enabled && (
          <Panel position="top-right">
            <CollaborationPresence state={collaborationState} />
          </Panel>
        )}

        {/* AI Assistant */}
        {enableAI && (
          <Panel position="bottom-right">
            <AIAssistant />
          </Panel>
        )}
      </ReactFlow>
    </div>
  );
}

/**
 * Wrapped component with providers
 */
export function CanvasComplete(props: CanvasCompleteProps) {
  return (
    <Provider>
      <ReactFlowProvider>
        <CanvasCompleteInner {...props} />
      </ReactFlowProvider>
    </Provider>
  );
}

export default CanvasComplete;
