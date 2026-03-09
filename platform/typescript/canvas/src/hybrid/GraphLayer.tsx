/**
 * @ghatana/canvas Graph Layer
 *
 * ReactFlow layer for graph-based node/edge rendering.
 *
 * @doc.type component
 * @doc.purpose Graph rendering layer
 * @doc.layer core
 * @doc.pattern Component
 */

import React, {
  useCallback,
  useEffect,
  useMemo,
  forwardRef,
  useImperativeHandle,
  type ComponentType,
} from "react";
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Panel,
  useReactFlow,
  useNodesState,
  useEdgesState,
  addEdge,
  applyNodeChanges,
  applyEdgeChanges,
  type Node,
  type Edge,
  type OnNodesChange,
  type OnEdgesChange,
  type OnConnect,
  type NodeTypes,
  type EdgeTypes,
  type Viewport,
  type Connection,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useAtom, useAtomValue, useSetAtom } from "jotai";
import type { CanvasNode, CanvasEdge, ViewportState } from "./types";
import { nodesAtom, edgesAtom, viewportAtom, selectionAtom } from "./state";

export interface GraphLayerProps {
  /** Custom node types */
  nodeTypes?: NodeTypes;
  /** Custom edge types */
  edgeTypes?: EdgeTypes;
  /** Show background pattern */
  showBackground?: boolean;
  /** Show controls (zoom buttons) */
  showControls?: boolean;
  /** Show minimap */
  showMinimap?: boolean;
  /** Read-only mode */
  readOnly?: boolean;
  /** Node click handler */
  onNodeClick?: (node: CanvasNode, event: React.MouseEvent) => void;
  /** Node double-click handler */
  onNodeDoubleClick?: (node: CanvasNode, event: React.MouseEvent) => void;
  /** Edge click handler */
  onEdgeClick?: (edge: CanvasEdge, event: React.MouseEvent) => void;
  /** Connection handler */
  onConnect?: (connection: Connection) => void;
  /** Pane click handler */
  onPaneClick?: (event: React.MouseEvent) => void;
  /** Class name */
  className?: string;
  /** Children (additional ReactFlow components) */
  children?: React.ReactNode;
}

export interface GraphLayerRef {
  /** Get ReactFlow instance */
  getReactFlowInstance(): ReturnType<typeof useReactFlow> | null;
  /** Fit view to content */
  fitView(): void;
  /** Zoom to level */
  zoomTo(level: number): void;
  /** Center on node */
  centerOnNode(nodeId: string): void;
}

/**
 * Inner component with access to ReactFlow hooks
 */
const GraphLayerInner = forwardRef<GraphLayerRef, GraphLayerProps>(
  function GraphLayerInner(
    {
      nodeTypes: customNodeTypes,
      edgeTypes: customEdgeTypes,
      showBackground = false, // Disabled by default since LayerContainer handles grid
      showControls = false,
      showMinimap = false,
      readOnly = false,
      onNodeClick,
      onNodeDoubleClick,
      onEdgeClick,
      onConnect: onConnectProp,
      onPaneClick,
      className = "",
      children,
    },
    ref,
  ) {
    const reactFlow = useReactFlow();
    const [nodes, setNodes] = useAtom(nodesAtom);
    const [edges, setEdges] = useAtom(edgesAtom);
    const [viewport, setViewport] = useAtom(viewportAtom);
    const setSelection = useSetAtom(selectionAtom);

    // Expose imperative methods
    useImperativeHandle(ref, () => ({
      getReactFlowInstance: () => reactFlow,
      fitView: () => reactFlow.fitView(),
      zoomTo: (level: number) => reactFlow.zoomTo(level),
      centerOnNode: (nodeId: string) => {
        const node = nodes.find((n) => n.id === nodeId);
        if (node) {
          reactFlow.setCenter(
            node.position.x + (node.measured?.width ?? 100) / 2,
            node.position.y + (node.measured?.height ?? 50) / 2,
            { zoom: viewport.zoom },
          );
        }
      },
    }));

    // Sync viewport from ReactFlow to our state
    const handleMove = useCallback(
      (_: unknown, newViewport: Viewport) => {
        setViewport((prev) => ({
          ...prev,
          x: newViewport.x,
          y: newViewport.y,
          zoom: newViewport.zoom,
        }));
      },
      [setViewport],
    );

    // Handle node changes
    const handleNodesChange: OnNodesChange = useCallback(
      (changes) => {
        setNodes((nds) => applyNodeChanges(changes, nds) as CanvasNode[]);

        // Update selection for selection changes
        for (const change of changes) {
          if (change.type === "select") {
            setSelection((sel) => {
              if (change.selected) {
                return {
                  ...sel,
                  nodeIds: [...new Set([...sel.nodeIds, change.id])],
                };
              } else {
                return {
                  ...sel,
                  nodeIds: sel.nodeIds.filter((id) => id !== change.id),
                };
              }
            });
          }
        }
      },
      [setNodes, setSelection],
    );

    // Handle edge changes
    const handleEdgesChange: OnEdgesChange = useCallback(
      (changes) => {
        setEdges((eds) => applyEdgeChanges(changes, eds) as CanvasEdge[]);

        // Update selection for selection changes
        for (const change of changes) {
          if (change.type === "select") {
            setSelection((sel) => {
              if (change.selected) {
                return {
                  ...sel,
                  edgeIds: [...new Set([...sel.edgeIds, change.id])],
                };
              } else {
                return {
                  ...sel,
                  edgeIds: sel.edgeIds.filter((id) => id !== change.id),
                };
              }
            });
          }
        }
      },
      [setEdges, setSelection],
    );

    // Handle new connections
    const handleConnect: OnConnect = useCallback(
      (connection) => {
        if (onConnectProp) {
          onConnectProp(connection);
        } else {
          const newEdge: CanvasEdge = {
            id: `edge-${Date.now()}`,
            source: connection.source!,
            target: connection.target!,
            sourceHandle: connection.sourceHandle ?? undefined,
            targetHandle: connection.targetHandle ?? undefined,
          };
          setEdges((eds) => addEdge(newEdge, eds) as CanvasEdge[]);
        }
      },
      [onConnectProp, setEdges],
    );

    // Handle node click
    const handleNodeClick = useCallback(
      (event: React.MouseEvent, node: Node) => {
        onNodeClick?.(node as CanvasNode, event);
      },
      [onNodeClick],
    );

    // Handle node double-click
    const handleNodeDoubleClick = useCallback(
      (event: React.MouseEvent, node: Node) => {
        onNodeDoubleClick?.(node as CanvasNode, event);
      },
      [onNodeDoubleClick],
    );

    // Handle edge click
    const handleEdgeClick = useCallback(
      (event: React.MouseEvent, edge: Edge) => {
        onEdgeClick?.(edge as CanvasEdge, event);
      },
      [onEdgeClick],
    );

    // Handle pane click
    const handlePaneClick = useCallback(
      (event: React.MouseEvent) => {
        // Clear selection if not shift-clicking
        if (!event.shiftKey) {
          setSelection((sel) => ({
            ...sel,
            nodeIds: [],
            edgeIds: [],
          }));
        }
        onPaneClick?.(event);
      },
      [setSelection, onPaneClick],
    );

    // Convert viewport to ReactFlow format
    const reactFlowViewport: Viewport = useMemo(
      () => ({
        x: viewport.x,
        y: viewport.y,
        zoom: viewport.zoom,
      }),
      [viewport],
    );

    return (
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={customNodeTypes}
        edgeTypes={customEdgeTypes}
        onNodesChange={readOnly ? undefined : handleNodesChange}
        onEdgesChange={readOnly ? undefined : handleEdgesChange}
        onConnect={readOnly ? undefined : handleConnect}
        onNodeClick={handleNodeClick}
        onNodeDoubleClick={handleNodeDoubleClick}
        onEdgeClick={handleEdgeClick}
        onPaneClick={handlePaneClick}
        onMove={handleMove}
        defaultViewport={reactFlowViewport}
        minZoom={viewport.minZoom}
        maxZoom={viewport.maxZoom}
        nodesDraggable={!readOnly}
        nodesConnectable={!readOnly}
        elementsSelectable={!readOnly}
        panOnDrag={true}
        zoomOnScroll={true}
        zoomOnPinch={true}
        selectNodesOnDrag={false}
        className={`ghatana-graph-layer ${className}`}
        proOptions={{ hideAttribution: true }}
        fitView={false}
      >
        {showBackground && <Background />}
        {showControls && <Controls />}
        {showMinimap && <MiniMap />}
        {children}
      </ReactFlow>
    );
  },
);

/**
 * Graph Layer Component
 *
 * Wraps ReactFlow for graph-based node/edge rendering.
 * Must be wrapped in ReactFlowProvider when used standalone.
 */
export const GraphLayer = forwardRef<GraphLayerRef, GraphLayerProps>(
  function GraphLayer(props, ref) {
    return <GraphLayerInner ref={ref} {...props} />;
  },
);

export default GraphLayer;
