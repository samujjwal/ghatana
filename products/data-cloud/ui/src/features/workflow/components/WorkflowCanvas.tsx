/**
 * Workflow canvas component using ReactFlow.
 *
 * <p><b>Purpose</b><br>
 * Visual workflow editor with node and edge management.
 * Provides drag-drop, selection, and connection validation.
 *
 * <p><b>Architecture</b><br>
 * - ReactFlow integration
 * - Custom node types
 * - Edge validation
 * - Selection management
 *
 * @doc.type component
 * @doc.purpose Workflow visual editor
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React, { useCallback, useMemo } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import {
  ReactFlow,
  Node,
  Edge,
  Connection,
  NodeChange,
  useNodesState,
  useEdgesState,
  Background,
  Controls,
  MiniMap,
  addEdge,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {
  workflowAtom,
  selectedNodeIdAtom,
  addNodeAtom,
  updateNodeAtom,
  deleteNodeAtom,
  addEdgeAtom,
  deleteEdgeAtom,
} from '../stores/workflow.store';
import { ApiCallNode } from './nodes/ApiCallNode';
import { DecisionNode } from './nodes/DecisionNode';
import { ApprovalNode } from './nodes/ApprovalNode';
import { StartNode } from './nodes/StartNode';
import { EndNode } from './nodes/EndNode';
import type { WorkflowNode as WorkflowNodeType, NodeType } from '../types/workflow.types';

/**
 * Node types mapping.
 *
 * @doc.type constant
 */
const nodeTypes = {
  start: StartNode,
  end: EndNode,
  apiCall: ApiCallNode,
  decision: DecisionNode,
  approval: ApprovalNode,
  // Add more node types as needed
};

/**
 * WorkflowCanvas component props.
 *
 * @doc.type interface
 */
export interface WorkflowCanvasProps {
  readOnly?: boolean;
  onNodeSelect?: (nodeId: string | null) => void;
}

/**
 * WorkflowCanvas component.
 *
 * Renders an interactive workflow editor with ReactFlow.
 *
 * @param props component props
 * @returns JSX element
 *
 * @doc.type function
 */
export const WorkflowCanvas: React.FC<WorkflowCanvasProps> = ({
  readOnly = false,
  onNodeSelect,
}) => {
  const [workflow] = useAtom(workflowAtom);
  const [selectedNodeId, setSelectedNodeId] = useAtom(selectedNodeIdAtom);
  const _addNode = useSetAtom(addNodeAtom);
  const updateNode = useSetAtom(updateNodeAtom);
  const deleteNode = useSetAtom(deleteNodeAtom);
  const addEdge_ = useSetAtom(addEdgeAtom);
  const _deleteEdge = useSetAtom(deleteEdgeAtom);

  // Convert workflow nodes to ReactFlow nodes
  const nodes: Node[] = useMemo(
    () =>
      workflow.nodes.map((node: WorkflowNodeType) => ({
        id: node.id,
        data: node.data,
        position: node.position,
        type: node.type,
        selected: node.id === selectedNodeId,
      })),
    [workflow.nodes, selectedNodeId]
  );

  // Convert workflow edges to ReactFlow edges
  const edges: Edge[] = useMemo(
    () =>
      workflow.edges.map((edge) => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        label: edge.label,
        animated: edge.animated,
        data: edge.data,
      })),
    [workflow.edges]
  );

  const [reactFlowNodes, _setNodes, onNodesChange] = useNodesState(nodes);
  const [reactFlowEdges, setEdges, onEdgesChange] = useEdgesState(edges);

  /**
   * Handles node click.
   */
  const handleNodeClick = useCallback(
    (event: React.MouseEvent, node: Node) => {
      event.stopPropagation();
      setSelectedNodeId(node.id);
      onNodeSelect?.(node.id);
    },
    [setSelectedNodeId, onNodeSelect]
  );

  /**
   * Handles canvas click.
   */
  const handleCanvasClick = useCallback(() => {
    setSelectedNodeId(null);
    if (onNodeSelect) onNodeSelect(null);
  }, [setSelectedNodeId, onNodeSelect]);

  /**
   * Handles nodes change.
   */
  const handleNodesChange = useCallback(
    (changes: NodeChange[]) => {
      onNodesChange(changes);

      // Update node positions
      changes.forEach((change) => {
        if (change.type === 'position' && change.position) {
          updateNode(change.id, {
            position: change.position,
          });
        }
      });
    },
    [onNodesChange, updateNode]
  );

  /**
   * Handles connection.
   */
  const handleConnect = useCallback(
    (connection: Connection) => {
      if (!connection.source || !connection.target) return;

      const newEdge = {
        id: `${connection.source}-${connection.target}`,
        source: connection.source,
        target: connection.target,
      };

      addEdge_(newEdge as any);
      setEdges((eds) => addEdge(connection, eds));
    },
    [addEdge_, setEdges]
  );

  /**
   * Handles key down.
   */
  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      if (readOnly) return;

      if (event.key === 'Delete' && selectedNodeId) {
        deleteNode(selectedNodeId);
      }
    },
    [readOnly, selectedNodeId, deleteNode]
  );

  React.useEffect(() => {
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handleKeyDown]);

  return (
    <div className="w-full h-full bg-gray-50">
      <ReactFlow
        nodes={reactFlowNodes}
        edges={reactFlowEdges}
        nodeTypes={nodeTypes}
        onNodesChange={handleNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={handleConnect}
        onNodeClick={handleNodeClick}
        onPaneClick={handleCanvasClick}
        fitView
        deleteKeyCode={readOnly ? null : 'Delete'}
      >
        <Background color="#aaa" gap={16} />
        <Controls />
        <MiniMap />
      </ReactFlow>
    </div>
  );
};

export default WorkflowCanvas;
