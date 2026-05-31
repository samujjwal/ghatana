/**
 * Type stub for @ghatana/canvas/flow.
 * Re-exports the necessary types from @xyflow/react until the canvas package
 * dist includes the flow subpath.
 */
declare module "@ghatana/canvas/flow" {
  import type { Node, Edge } from "@xyflow/react";
  import type React from "react";

  export {
    Handle,
    Panel,
    Position,
    MarkerType,
    addEdge,
    useNodesState,
    useEdgesState,
    useReactFlow,
    type Node,
    type Edge,
    type NodeProps,
    type EdgeProps,
    type Connection,
    type OnConnect,
    type OnNodesChange,
    type OnEdgesChange,
    type ReactFlowInstance,
  } from "@xyflow/react";

  export { FlowCanvas, type FlowCanvasProps } from "@ghatana/canvas";

  export type FlowNode = Node;
  export type FlowEdge = Edge;

  export interface FlowControlsProps {
    showMiniMap?: boolean;
    showControls?: boolean;
    showZoom?: boolean;
    showFitView?: boolean;
    showInteractive?: boolean;
    position?: "top-left" | "top-right" | "bottom-left" | "bottom-right";
    controlsPosition?:
      | "top-left"
      | "top-right"
      | "bottom-left"
      | "bottom-right";
  }

  export const FlowControls: React.NamedExoticComponent<FlowControlsProps>;
}
