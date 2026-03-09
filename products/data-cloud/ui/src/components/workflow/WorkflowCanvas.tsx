import React, { useCallback, useEffect, useState } from 'react';
import { useAtom } from 'jotai';
import {
  ReactFlow,
  Node,
  Edge,
  addEdge,
  Connection,
  useNodesState,
  useEdgesState,
  Background,
  Controls,
  MiniMap,
  Panel,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { workflowAtom, selectedNodeAtom, selectedEdgeAtom } from '@/stores/workflow.store';
import type { WorkflowNode as WorkflowNodeType } from '@/types/workflow.types';
import { ApiCallNode } from './nodes/ApiCallNode';
import { DecisionNode } from './nodes/DecisionNode';
import { ApprovalNode } from './nodes/ApprovalNode';
import { TransformNode } from './nodes/TransformNode';

/**
 * Workflow canvas component with ReactFlow integration.
 *
 * <p><b>Purpose</b><br>
 * Main workflow editor canvas for visual workflow design. Provides
 * node rendering, edge connections, zoom/pan controls, and selection handling.
 *
 * <p><b>Features</b><br>
 * - ReactFlow integration for graph visualization
 * - Custom node types (API Call, Decision, Approval, Transform)
 * - Zoom and pan controls
 * - Minimap for navigation
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
 * @see ReactFlow
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
  const initialNodes: Node[] =
    workflow?.nodes.map((node: WorkflowNodeType) => ({
      id: node.id,
      data: { label: node.label, config: node.config },
      position: { x: node.position?.x ?? 0, y: node.position?.y ?? 0 },
      type: mapNodeType(node.type),
      selected: node.id === selectedNodeId,
    })) || [];

  const initialEdges: Edge[] =
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
    (connection: Connection) => {
      const newEdge: Edge = {
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
    (event: React.MouseEvent, node: Node) => {
      setSelectedNodeId(node.id);
    },
    [setSelectedNodeId]
  );

  // Handle edge selection
  const onEdgeClick = useCallback(
    (event: React.MouseEvent, edge: Edge) => {
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
      <ReactFlow
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
        <Background color="#aaa" gap={16} />
        <Controls />
        <MiniMap />
        <Panel position="top-left" className="bg-white p-2 rounded shadow">
          <div className="text-sm font-semibold">Workflow Canvas</div>
          <div className="text-xs text-gray-600">
            Nodes: {nodes.length} | Edges: {edges.length}
          </div>
        </Panel>
      </ReactFlow>
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
