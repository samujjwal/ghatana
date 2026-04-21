// Compatibility shim for canvas during incremental migration.
// Re-export the small public surface expected by the app from canonical packages.
// Updated: Now uses @ghatana/canvas core library

import type { ReactNode } from 'react';

// Re-export ReactFlow components from @xyflow/react (used directly now)
export {
  ReactFlow as Diagram,
  ReactFlowProvider as DiagramProvider,
  useReactFlow as useDiagram,
} from '@xyflow/react';

export interface GraphLayerProps {
  children?: ReactNode;
}

export interface FreeformLayerProps {
  children?: ReactNode;
}

export interface HybridCanvasProps {
  children?: ReactNode;
}

export const GraphLayer = (_props: GraphLayerProps) => null;
export const FreeformLayer = (_props: FreeformLayerProps) => null;
export const HybridCanvas = (_props: HybridCanvasProps) => null;

// CanvasFlow / CanvasFlowShim removed — keep the shim mapped to GraphLayer locally.
export const CanvasFlow = GraphLayer;

// Default node types placeholder (product extensions should provide their own)
export const nodeTypes = {};
export const DefaultNode = () => null;
export const InputNode = () => null;
export const OutputNode = () => null;
