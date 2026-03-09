/**
 * @ghatana/canvas Hybrid Canvas Component
 *
 * Main hybrid canvas component combining freeform and graph layers.
 *
 * @doc.type component
 * @doc.purpose Main canvas component
 * @doc.layer core
 * @doc.pattern Component
 */

import React, {
  useCallback,
  useEffect,
  useRef,
  forwardRef,
  useImperativeHandle,
  type ComponentType,
} from "react";
import { ReactFlowProvider } from "@xyflow/react";
import { Provider as JotaiProvider, useSetAtom } from "jotai";
import type {
  HybridCanvasProps,
  CanvasElement,
  CanvasNode,
  CanvasEdge,
  Point,
  ViewportState,
  SelectionState,
  RenderingMode,
} from "./types";
import {
  hybridCanvasStateAtom,
  elementsAtom,
  nodesAtom,
  edgesAtom,
  viewportAtom,
  selectionAtom,
  gridAtom,
} from "./state";
import { LayerContainer } from "./LayerContainer";
import { FreeformLayer, type FreeformLayerRef } from "./FreeformLayer";
import { GraphLayer, type GraphLayerRef } from "./GraphLayer";
import {
  getHybridCanvasController,
  type HybridCanvasAPI,
} from "./hybrid-canvas-controller";
import { useCanvasKeyboardShortcuts, useCanvasDrop } from "./hooks";
import { getPluginManager } from "../plugins/plugin-manager";

export interface HybridCanvasRef extends HybridCanvasAPI {
  /** Get the container element */
  getContainer(): HTMLDivElement | null;
  /** Get the freeform layer ref */
  getFreeformLayer(): FreeformLayerRef | null;
  /** Get the graph layer ref */
  getGraphLayer(): GraphLayerRef | null;
}

/**
 * Inner component that initializes state
 */
const HybridCanvasInner = forwardRef<HybridCanvasRef, HybridCanvasProps>(
  function HybridCanvasInner(
    {
      mode = "hybrid-freeform",
      elements: initialElements,
      nodes: initialNodes,
      edges: initialEdges,
      viewport: initialViewport,
      grid: initialGrid,
      readOnly = false,
      width = "100%",
      height = "100%",
      className = "",
      nodeTypes,
      edgeTypes,
      children,
      onElementsChange,
      onNodesChange,
      onEdgesChange,
      onSelectionChange,
      onViewportChange,
      onModeChange,
      onConnect,
      onElementClick,
      onNodeClick,
      onEdgeClick,
      onCanvasClick,
      onDrop,
    },
    ref,
  ) {
    const containerRef = useRef<HTMLDivElement>(null);
    const freeformRef = useRef<FreeformLayerRef>(null);
    const graphRef = useRef<GraphLayerRef>(null);

    const setElements = useSetAtom(elementsAtom);
    const setNodes = useSetAtom(nodesAtom);
    const setEdges = useSetAtom(edgesAtom);
    const setViewport = useSetAtom(viewportAtom);
    const setSelection = useSetAtom(selectionAtom);
    const setGrid = useSetAtom(gridAtom);

    const controller = getHybridCanvasController();

    // Initialize state from props
    useEffect(() => {
      if (initialElements) setElements(initialElements);
      if (initialNodes) setNodes(initialNodes);
      if (initialEdges) setEdges(initialEdges);
      if (initialViewport) {
        setViewport((prev) => ({ ...prev, ...initialViewport }));
      }
      if (initialGrid) {
        setGrid((prev) => ({ ...prev, ...initialGrid }));
      }
    }, []); // Only on mount

    // Set container ref on controller
    useEffect(() => {
      controller.setContainer(containerRef.current);
    }, [controller]);

    // Set canvas API on plugin manager
    useEffect(() => {
      const pluginManager = getPluginManager();
      pluginManager.setCanvasAPI(controller as unknown as Parameters<typeof pluginManager.setCanvasAPI>[0]);
    }, [controller]);

    // Keyboard shortcuts
    useCanvasKeyboardShortcuts(!readOnly);

    // Drop handling
    const handleDrop = useCallback(
      (type: string, data: unknown, position: Point) => {
        // Create element based on type
        if (type.startsWith("element:")) {
          const elementType = type.replace("element:", "");
          controller.addElement({
            type: elementType,
            position,
            size: { width: 200, height: 100 },
            data: (data as Record<string, unknown>) ?? {},
          });
        } else if (type.startsWith("node:")) {
          const nodeType = type.replace("node:", "");
          controller.addNode({
            type: nodeType,
            position,
            data: (data as Record<string, unknown>) ?? {},
          });
        }

        onDrop?.(new DragEvent("drop"), position);
      },
      [controller, onDrop],
    );

    const { setDropRef } = useCanvasDrop(handleDrop);

    // Set drop ref
    useEffect(() => {
      setDropRef(containerRef.current);
    }, [setDropRef]);

    // Sync state changes to callbacks
    useEffect(() => {
      // TODO: Subscribe to state changes and call callbacks
      // This would use a subscription mechanism
    }, [
      onElementsChange,
      onNodesChange,
      onEdgesChange,
      onSelectionChange,
      onViewportChange,
    ]);

    // Expose imperative methods
    useImperativeHandle(
      ref,
      () => {
        return Object.assign(
          Object.create(Object.getPrototypeOf(controller)),
          controller,
          {
            getContainer: () => containerRef.current,
            getFreeformLayer: () => freeformRef.current,
            getGraphLayer: () => graphRef.current,
          },
        ) as HybridCanvasRef;
      },
      [controller],
    );

    // Element drag handling
    const handleElementDrag = useCallback(
      (elements: CanvasElement[], delta: Point) => {
        for (const element of elements) {
          controller.updateElement(element.id, {
            position: {
              x: element.position.x + delta.x,
              y: element.position.y + delta.y,
            },
          });
        }
      },
      [controller],
    );

    const handleElementDragEnd = useCallback(() => {
      controller.pushHistory("Move elements");
    }, [controller]);

    return (
      <LayerContainer
        ref={containerRef}
        width={width}
        height={height}
        mode={mode}
        className={className}
        freeformLayer={
          <FreeformLayer
            ref={freeformRef}
            readOnly={readOnly}
            onElementClick={onElementClick}
            onCanvasClick={(point, event) => onCanvasClick?.(point)}
            onDrag={handleElementDrag}
            onDragEnd={handleElementDragEnd}
          />
        }
        graphLayer={
          <GraphLayer
            ref={graphRef}
            nodeTypes={nodeTypes as React.ComponentProps<typeof GraphLayer>['nodeTypes']}
            edgeTypes={edgeTypes as React.ComponentProps<typeof GraphLayer>['edgeTypes']}
            readOnly={readOnly}
            onNodeClick={onNodeClick}
            onEdgeClick={onEdgeClick}
            onConnect={onConnect as React.ComponentProps<typeof GraphLayer>['onConnect']}
            onPaneClick={(e) => {
              const rect = containerRef.current?.getBoundingClientRect();
              if (rect) {
                const point = controller.screenToCanvas({
                  x: e.clientX - rect.left,
                  y: e.clientY - rect.top,
                });
                onCanvasClick?.(point);
              }
            }}
          />
        }
      >
        {children}
      </LayerContainer>
    );
  },
);

/**
 * Hybrid Canvas Component
 *
 * Main entry point for the hybrid canvas.
 * Provides both freeform canvas and ReactFlow graph capabilities.
 */
export const HybridCanvas = forwardRef<HybridCanvasRef, HybridCanvasProps>(
  function HybridCanvas(props, ref) {
    return (
      <ReactFlowProvider>
        <HybridCanvasInner ref={ref} {...props} />
      </ReactFlowProvider>
    );
  },
);

export default HybridCanvas;
