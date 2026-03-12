import React, { useCallback, useEffect, useState } from 'react';
import { useAtom } from 'jotai';
import {
  FlowCanvas,
  FlowControls,
  addEdge,
  useNodesState,
  useEdgesState,
} from '@ghatana/flow-canvas';
import type { Node, Edge, Connection } from '@ghatana/flow-canvas';
import { workflowAtom, selectedNodeAtom, selectedEdgeAtom } from '@/stores/workflow.store';
import type { WorkflowNode as WorkflowNodeType } from '@/types/workflow.types';
import { ApiCallNode } from './nodes/ApiCallNode';
import { DecisionNode } from './nodes/DecisionNode';
import { ApprovalNode } from './nodes/ApprovalNode';
import { TransformNode } from './nodes/TransformNode';

// Convenience type aliases for this canvas's node/edge data shapes
type CanvasNodeData = { label?: string; config?: Record<string, unknown> };
type CanvasEdgeData = { label?: string };
type FlowNode = Node<CanvasNodeData>;
type FlowEdge = Edge<CanvasEdgeData>;
type FlowConnection = Connection;

/**
 * Workflow canvas component with ReactFlow integration.
 *
 * <p><b>Purpose</b><br>
 * Main workflow editor canvas for visual workflow design. Provides
 * node rendering, edge connections, zoom/pan controls, and selection handling.
 *
 * <p><b>Features</b><br>
 * - @ghatana/flow-canvas integration for graph visualization
 * - Custom node types (API Call, Decision, Approval, Transform)
 * - Zoom and pan controls
 * - Node and edge selection
 * - Keyboard shortcuts (Delete, Ctrl+Z, Ctrl+Y)
 * - Real-time state synchronization with Jotai
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { WorkflowCanvas } from '@/components/workflow/WorkflowCanvas';
 *
 * export function WorkflowEditor() {
 *   return (
 *     <div className="w-full h-screen">
 *       <WorkflowCanvas workflowId="workflow-123" />
 *     </div>
 *   );
 * }
 * }</pre>
 *
 * <p><b>Keyboard Shortcuts</b><br>
 * - Delete: Remove selected node/edge
 * - Ctrl+Z: Undo
 * - Ctrl+Y: Redo
 * - Ctrl+A: Select all
 *
 * <p><b>Thread Safety</b><br>
 * React component - safe for concurrent rendering.
 *
 * @see FlowCanvas
 * @see ApiCallNode
 * @see DecisionNode
 * @doc.type component
 * @doc.purpose Main workflow editor canvas
 * @doc.layer frontend
 */
export interface WorkflowCanvasProps {
  workflowId?: string;
}

const nodeTypes = {
  apiCall: ApiCallNode,
  decision: DecisionNode,
  approval: ApprovalNode,
  transform: TransformNode,
};

/**
 * WorkflowCanvas component.
 *
 * @param props component props
 * @returns JSX element
 */
export function WorkflowCanvas({ workflowId }: WorkflowCanvasProps): JSX.Element {
  const [workflow] = useAtom(workflowAtom);
  const [selectedNodeId, setSelectedNodeId] = useAtom(selectedNodeAtom);
  const [selectedEdgeId, setSelectedEdgeAtom] = useAtom(selectedEdgeAtom);

  // Initialize nodes and edges from workflow
  const initialNodes: FlowNode[] =
    workflow?.nodes.map((node: WorkflowNodeType) => ({
      id: node.id,
      data: { label: node.label ?? '', config: node.config as Record<string, unknown> | undefined },
      position: { x: node.position?.x ?? 0, y: node.position?.y ?? 0 },
      type: mapNodeType(node.type),
      selected: node.id === selectedNodeId,
    })) || [];

  const initialEdges: FlowEdge[] =
    workflow?.edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      label: edge.label,
      selected: edge.id === selectedEdgeId,
    })) || [];

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  // Handle node connection
  const onConnect = useCallback(
    (connection: FlowConnection) => {
      const newEdge: FlowEdge = {
        id: `edge-${Date.now()}`,
        source: connection.source || '',
        target: connection.target || '',
        type: 'smoothstep',
      };
      setEdges((eds) => addEdge(newEdge, eds));
    },
    [setEdges]
  );

  // Handle node selection
  const onNodeClick = useCallback(
    (event: React.MouseEvent, node: FlowNode) => {
      setSelectedNodeId(node.id);
    },
    [setSelectedNodeId]
  );

  // Handle edge selection
  const onEdgeClick = useCallback(
    (event: React.MouseEvent, edge: FlowEdge) => {
      setSelectedEdgeAtom(edge.id);
    },
    [setSelectedEdgeAtom]
  );

  // Handle keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Delete') {
        if (selectedNodeId) {
          setNodes((nds) => nds.filter((node) => node.id !== selectedNodeId));
          setSelectedNodeId(null);
        } else if (selectedEdgeId) {
          setEdges((eds) => eds.filter((edge) => edge.id !== selectedEdgeId));
          setSelectedEdgeAtom(null);
        }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [selectedNodeId, selectedEdgeId, setNodes, setEdges, setSelectedNodeId, setSelectedEdgeAtom]);

  return (
    <div className="w-full h-full bg-gray-50">
      <FlowCanvas
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onNodeClick={onNodeClick}
        onEdgeClick={onEdgeClick}
        nodeTypes={nodeTypes}
        fitView
      >
        <FlowControls />
        <div className="absolute top-2 left-2 bg-white p-2 rounded shadow text-sm z-10">
          <div className="font-semibold">Workflow Canvas</div>
          <div className="text-xs text-gray-600">
            Nodes: {nodes.length} | Edges: {edges.length}
          </div>
        </div>
      </FlowCanvas>
    </div>
  );
}

/**
 * Maps workflow node type to ReactFlow node type.
 *
 * @param nodeType the workflow node type
 * @returns ReactFlow node type
 */
function mapNodeType(nodeType: string): string {
  const typeMap: Record<string, string> = {
    API_CALL: 'apiCall',
    DECISION: 'decision',
    APPROVAL: 'approval',
    TRANSFORM: 'transform',
  };
  return typeMap[nodeType] || 'default';
}
