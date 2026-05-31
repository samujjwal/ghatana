/**
 * Workflow canvas component using FlowCanvas.
 *
 * <p><b>Purpose</b><br>
 * Visual workflow editor with node and edge management.
 * Provides drag-drop, selection, and connection validation.
 *
 * <p><b>Architecture</b><br>
 * - @ghatana/canvas/flow integration
 * - Custom node types
 * - Edge validation
 * - Selection management
 *
 * @doc.type component
 * @doc.purpose Workflow visual editor
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import type {
  Connection,
  Edge,
  Node,
  OnNodesChange,
} from "@ghatana/canvas/flow";
import {
  addEdge,
  FlowCanvas,
  useEdgesState,
  useNodesState,
} from "@ghatana/canvas/flow";
import { useAtom, useSetAtom } from "jotai";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  addEdgeAtom,
  addNodeAtom,
  deleteEdgeAtom,
  deleteNodeAtom,
  selectedNodeIdAtom,
  updateNodeAtom,
  workflowAtom,
} from "../stores/workflow.store";
import type { WorkflowNode as WorkflowNodeType } from "../types/workflow.types";
import { ApiCallNode } from "./nodes/ApiCallNode";
import { ApprovalNode } from "./nodes/ApprovalNode";
import { DecisionNode } from "./nodes/DecisionNode";
import { EndNode } from "./nodes/EndNode";
import { StartNode } from "./nodes/StartNode";

export interface WorkflowValidationIssue {
  id: string;
  severity: "error" | "warning" | "suggestion";
  nodeId?: string;
  message: string;
  suggestedFix?: string;
}

export interface WorkflowValidationResult {
  isValid: boolean;
  issues: WorkflowValidationIssue[];
}

export function validateWorkflow(
  nodes: WorkflowNodeType[],
  edges: Edge[],
): WorkflowValidationResult {
  const issues: WorkflowValidationIssue[] = [];
  const nodeIds = new Set(nodes.map((n) => n.id));
  const startNodes = nodes.filter((n) => n.type === "start");
  const endNodes = nodes.filter((n) => n.type === "end");

  // Check for start node
  if (startNodes.length === 0) {
    issues.push({
      id: "no-start-node",
      severity: "error",
      message: "Workflow must have at least one start node.",
      suggestedFix: "Add a Start node to define the workflow entry point.",
    });
  } else if (startNodes.length > 1) {
    issues.push({
      id: "multiple-start-nodes",
      severity: "warning",
      message:
        "Workflow has multiple start nodes. Consider consolidating to a single entry point.",
      suggestedFix:
        "Remove duplicate Start nodes or use a Decision node to route between them.",
    });
  }

  // Check for end node
  if (endNodes.length === 0) {
    issues.push({
      id: "no-end-node",
      severity: "error",
      message: "Workflow must have at least one end node.",
      suggestedFix: "Add an End node to define the workflow termination point.",
    });
  }

  // Check for disconnected nodes
  const connectedNodeIds = new Set<string>();
  edges.forEach((edge) => {
    connectedNodeIds.add(edge.source);
    connectedNodeIds.add(edge.target);
  });

  nodes.forEach((node) => {
    if (!connectedNodeIds.has(node.id) && nodes.length > 1) {
      issues.push({
        id: `disconnected-node-${node.id}`,
        severity: "warning",
        nodeId: node.id,
        message: `Node "${node.data.label || node.id}" is disconnected from the workflow.`,
        suggestedFix:
          "Connect this node to other nodes or remove it if not needed.",
      });
    }
  });

  // Check for nodes without outgoing edges (except end nodes)
  const nodesWithOutgoing = new Set(edges.map((e) => e.source));
  nodes.forEach((node) => {
    if (
      node.type !== "end" &&
      !nodesWithOutgoing.has(node.id) &&
      startNodes.length > 0
    ) {
      issues.push({
        id: `no-outgoing-${node.id}`,
        severity: "warning",
        nodeId: node.id,
        message: `Node "${node.data.label || node.id}" has no outgoing edges (not an end node).`,
        suggestedFix:
          "Add an edge to a downstream node or convert this to an End node.",
      });
    }
  });

  // Check for API call nodes with missing configuration
  nodes.forEach((node) => {
    if (node.type === "apiCall") {
      const config = (node.data.config as Record<string, unknown>) || {};
      if (!config.endpoint) {
        issues.push({
          id: `missing-endpoint-${node.id}`,
          severity: "error",
          nodeId: node.id,
          message: `API Call node "${node.data.label || node.id}" is missing an endpoint URL.`,
          suggestedFix: "Configure the endpoint URL in the node settings.",
        });
      }
    }

    if (node.type === "decision") {
      const config = (node.data.config as Record<string, unknown>) || {};
      if (!config.condition) {
        issues.push({
          id: `missing-condition-${node.id}`,
          severity: "error",
          nodeId: node.id,
          message: `Decision node "${node.data.label || node.id}" is missing a condition expression.`,
          suggestedFix: "Define the condition logic in the node settings.",
        });
      }
    }
  });

  // Check for cycles (self-referential or complex)
  const edgeMap = new Map<string, string[]>();
  edges.forEach((edge) => {
    if (!edgeMap.has(edge.source)) {
      edgeMap.set(edge.source, []);
    }
    edgeMap.get(edge.source)!.push(edge.target);
  });

  // Simple cycle detection
  const visited = new Set<string>();
  const recursionStack = new Set<string>();

  function hasCycle(nodeId: string): boolean {
    if (recursionStack.has(nodeId)) return true;
    if (visited.has(nodeId)) return false;

    visited.add(nodeId);
    recursionStack.add(nodeId);

    const neighbors = edgeMap.get(nodeId) || [];
    for (const neighbor of neighbors) {
      if (hasCycle(neighbor)) return true;
    }

    recursionStack.delete(nodeId);
    return false;
  }

  for (const nodeId of nodeIds) {
    if (hasCycle(nodeId)) {
      issues.push({
        id: "cycle-detected",
        severity: "error",
        message: "Workflow contains a cycle. Workflows should be acyclic DAGs.",
        suggestedFix: "Break the cycle by reorganizing the node connections.",
      });
      break;
    }
  }

  return {
    isValid: issues.filter((i) => i.severity === "error").length === 0,
    issues,
  };
}

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
  showValidation?: boolean;
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
  showValidation = true,
}) => {
  const [workflow] = useAtom(workflowAtom);
  const [selectedNodeId, setSelectedNodeId] = useAtom(selectedNodeIdAtom);
  const _addNode = useSetAtom(addNodeAtom);
  const updateNode = useSetAtom(updateNodeAtom);
  const deleteNode = useSetAtom(deleteNodeAtom);
  const addEdge_ = useSetAtom(addEdgeAtom);
  const _deleteEdge = useSetAtom(deleteEdgeAtom);
  const [validationResult, setValidationResult] =
    useState<WorkflowValidationResult | null>(null);

  // Auto-validate workflow on changes
  useEffect(() => {
    if (showValidation) {
      const result = validateWorkflow(workflow.nodes, workflow.edges);
      setValidationResult(result);
    }
  }, [workflow, showValidation]);

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
    [workflow.nodes, selectedNodeId],
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
    [workflow.edges],
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
    [setSelectedNodeId, onNodeSelect],
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
    (changes: Parameters<OnNodesChange>[0]) => {
      onNodesChange(changes as any);

      // Update node positions
      changes.forEach((change) => {
        if (change.type === "position" && change.position) {
          updateNode(change.id, {
            position: change.position,
          });
        }
      });
    },
    [onNodesChange, updateNode],
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
    [addEdge_, setEdges],
  );

  /**
   * Handles key down.
   */
  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      if (readOnly) return;

      if (event.key === "Delete" && selectedNodeId) {
        deleteNode(selectedNodeId);
      }
    },
    [readOnly, selectedNodeId, deleteNode],
  );

  React.useEffect(() => {
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [handleKeyDown]);

  const handleApplyFix = useCallback(
    (issue: WorkflowValidationIssue) => {
      if (issue.nodeId && issue.suggestedFix) {
        // Auto-apply simple fixes or select the node for manual editing
        setSelectedNodeId(issue.nodeId);
        onNodeSelect?.(issue.nodeId);
      }
    },
    [setSelectedNodeId, onNodeSelect],
  );

  return (
    <div className="flex flex-col h-full">
      {showValidation &&
        validationResult &&
        validationResult.issues.length > 0 && (
          <div className="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 p-4 mb-4 rounded-lg">
            <div className="flex items-center gap-2 mb-3">
              <span
                className={`font-medium ${
                  validationResult.isValid
                    ? "text-amber-700 dark:text-amber-300"
                    : "text-red-700 dark:text-red-300"
                }`}
              >
                {validationResult.isValid
                  ? "Workflow Warnings"
                  : "Workflow Errors"}
              </span>
              <span className="text-sm text-gray-600 dark:text-gray-400">
                ({validationResult.issues.length}{" "}
                {validationResult.issues.length === 1 ? "issue" : "issues"})
              </span>
            </div>
            <div className="space-y-2 max-h-48 overflow-y-auto">
              {validationResult.issues.map((issue) => (
                <div
                  key={issue.id}
                  className={`flex items-start gap-3 p-2 rounded ${
                    issue.severity === "error"
                      ? "bg-red-100 dark:bg-red-900/30"
                      : "bg-yellow-100 dark:bg-yellow-900/30"
                  }`}
                >
                  <div
                    className={`w-2 h-2 rounded-full mt-2 flex-shrink-0 ${
                      issue.severity === "error"
                        ? "bg-red-500"
                        : "bg-yellow-500"
                    }`}
                  />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                      {issue.message}
                    </p>
                    {issue.suggestedFix && (
                      <div className="flex items-center gap-2 mt-1">
                        <p className="text-xs text-gray-600 dark:text-gray-400">
                          {issue.suggestedFix}
                        </p>
                        {issue.nodeId && (
                          <button
                            onClick={() => handleApplyFix(issue)}
                            className="text-xs text-blue-600 dark:text-blue-400 hover:underline"
                          >
                            Select node
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      <div className="flex-1">
        <div className="w-full h-full bg-gray-50">
          <FlowCanvas
            nodes={reactFlowNodes}
            edges={reactFlowEdges}
            additionalNodeTypes={nodeTypes as any}
            onNodesChange={handleNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={handleConnect}
            onNodeClick={handleNodeClick}
            onPaneClick={handleCanvasClick}
          />
        </div>
      </div>
    </div>
  );
};

export default WorkflowCanvas;
