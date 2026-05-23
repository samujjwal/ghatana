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
  useMemo,
  type ComponentType,
} from "react";
import { ReactFlowProvider } from "@xyflow/react";
import { Provider as JotaiProvider, useSetAtom, useAtomValue } from "jotai";
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
  HybridCanvasController,
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
 * Inner component that initializes state.
 * Memoized to prevent unnecessary re-renders from parent prop changes that do
 * not affect the canvas itself.
 */
const HybridCanvasInner = React.memo(
  forwardRef<HybridCanvasRef, HybridCanvasProps>(
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
        onElementDoubleClick,
        onNodeClick,
        onEdgeClick,
        onCanvasClick,
        onDrop,
        onPortalEnter,
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

      // Subscribe to state atoms for outbound callbacks
      const elements = useAtomValue(elementsAtom);
      const nodes = useAtomValue(nodesAtom);
      const edges = useAtomValue(edgesAtom);
      const viewport = useAtomValue(viewportAtom);
      const selection = useAtomValue(selectionAtom);

      const controller = useMemo(() => new HybridCanvasController(), []);

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

      // Fire outbound callbacks when the corresponding state atoms change.
      // Skip the very first render (mount) by tracking whether initialization has
      // completed — we don't want to echo the initial prop values back.
      const mountedRef = useRef(false);
      useEffect(() => {
        if (!mountedRef.current) return;
        onElementsChange?.(elements);
      }, [elements]); // eslint-disable-line react-hooks/exhaustive-deps

      useEffect(() => {
        if (!mountedRef.current) return;
        onNodesChange?.(nodes);
      }, [nodes]); // eslint-disable-line react-hooks/exhaustive-deps

      useEffect(() => {
        if (!mountedRef.current) return;
        onEdgesChange?.(edges);
      }, [edges]); // eslint-disable-line react-hooks/exhaustive-deps

      useEffect(() => {
        if (!mountedRef.current) return;
        onSelectionChange?.(selection);
      }, [selection]); // eslint-disable-line react-hooks/exhaustive-deps

      useEffect(() => {
        if (!mountedRef.current) return;
        onViewportChange?.(viewport);
      }, [viewport]); // eslint-disable-line react-hooks/exhaustive-deps

      // Mark the component as mounted after all initialization effects have run.
      useEffect(() => {
        mountedRef.current = true;
      }, []);

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
        const elements = controller.getElements();
        const nodes = controller.getNodes();
        const edges = controller.getEdges();
        controller.pushHistory({
          action: "Move elements",
          snapshot: { elements, nodes, edges },
        });
      }, [controller]);

      const handleKeyboardMove = useCallback(
        (delta: Point) => {
          const selectedElementIds = new Set(selection.elementIds);
          const selectedNodeIds = new Set(selection.nodeIds);
          if (selectedElementIds.size === 0 && selectedNodeIds.size === 0) {
            return;
          }

          setElements((current) =>
            current.map((element) =>
              selectedElementIds.has(element.id) && element.locked !== true
                ? {
                    ...element,
                    position: {
                      x: element.position.x + delta.x,
                      y: element.position.y + delta.y,
                    },
                  }
                : element,
            ),
          );
          setNodes((current) =>
            current.map((node) =>
              selectedNodeIds.has(node.id)
                ? {
                    ...node,
                    position: {
                      x: node.position.x + delta.x,
                      y: node.position.y + delta.y,
                    },
                  }
                : node,
            ),
          );
        },
        [selection.elementIds, selection.nodeIds, setElements, setNodes],
      );

      const handleKeyboardDelete = useCallback(() => {
        const selectedElementIds = new Set(selection.elementIds);
        const selectedNodeIds = new Set(selection.nodeIds);
        const selectedEdgeIds = new Set(selection.edgeIds);
        if (
          selectedElementIds.size === 0 &&
          selectedNodeIds.size === 0 &&
          selectedEdgeIds.size === 0
        ) {
          return;
        }

        setElements((current) =>
          current.filter((element) => !selectedElementIds.has(element.id)),
        );
        setNodes((current) =>
          current.filter((node) => !selectedNodeIds.has(node.id)),
        );
        setEdges((current) =>
          current.filter(
            (edge) =>
              !selectedEdgeIds.has(edge.id) &&
              !selectedNodeIds.has(edge.source) &&
              !selectedNodeIds.has(edge.target),
          ),
        );
        setSelection({
          elementIds: [],
          nodeIds: [],
          edgeIds: [],
          isMultiSelect: false,
          bounds: null,
        });
      }, [selection, setEdges, setElements, setNodes, setSelection]);

      const handleContainerKeyDown = useCallback(
        (event: React.KeyboardEvent<HTMLDivElement>) => {
          if (readOnly || isEditableKeyboardTarget(event.target)) {
            return;
          }

          if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "a") {
            event.preventDefault();
            setSelection({
              elementIds: elements.filter((element) => element.locked !== true).map((element) => element.id),
              nodeIds: nodes.map((node) => node.id),
              edgeIds: edges.map((edge) => edge.id),
              isMultiSelect: true,
              bounds: null,
            });
            return;
          }

          if (event.key === "Escape") {
            event.preventDefault();
            setSelection({
              elementIds: [],
              nodeIds: [],
              edgeIds: [],
              isMultiSelect: false,
              bounds: null,
            });
            return;
          }

          if (event.key === "Delete" || event.key === "Backspace") {
            event.preventDefault();
            handleKeyboardDelete();
            return;
          }

          const step = event.shiftKey ? 10 : 1;
          const deltaByKey: Partial<Record<string, Point>> = {
            ArrowUp: { x: 0, y: -step },
            ArrowDown: { x: 0, y: step },
            ArrowLeft: { x: -step, y: 0 },
            ArrowRight: { x: step, y: 0 },
          };
          const delta = deltaByKey[event.key];
          if (delta !== undefined) {
            event.preventDefault();
            handleKeyboardMove(delta);
          }
        },
        [
          edges,
          elements,
          handleKeyboardDelete,
          handleKeyboardMove,
          nodes,
          readOnly,
          setSelection,
        ],
      );

      return (
        <LayerContainer
          ref={containerRef}
          width={width}
          height={height}
          mode={mode}
          className={className}
          ariaLabel="Hybrid canvas surface"
          onKeyDown={handleContainerKeyDown}
          freeformLayer={
            <FreeformLayer
              ref={freeformRef}
              readOnly={readOnly}
              onElementClick={onElementClick}
              onElementDoubleClick={(element, _event) => {
                onElementDoubleClick?.(element);
                // If this is a portal element, fire the portal-enter callback
                if ((element.data as Record<string, unknown>)?.targetDocumentId) {
                  onPortalEnter?.(
                    element.id,
                    String((element.data as Record<string, unknown>).targetDocumentId),
                  );
                }
              }}
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
  ));

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

function isEditableKeyboardTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  const tagName = target.tagName.toLowerCase();
  return (
    tagName === "input" ||
    tagName === "textarea" ||
    tagName === "select" ||
    target.isContentEditable
  );
}

export default HybridCanvas;
