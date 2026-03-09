// Compatibility shim for canvas during incremental migration.
// Re-export the small public surface expected by the app from canonical packages.
// Updated: Now uses @ghatana/canvas core library

// Re-export ReactFlow components from @xyflow/react (used directly now)
export {
  ReactFlow as Diagram,
  ReactFlowProvider as DiagramProvider,
  useReactFlow as useDiagram,
} from '@xyflow/react';

// Re-export hybrid canvas components from @ghatana/canvas
export {
  GraphLayer,
  FreeformLayer,
  HybridCanvas,
  type GraphLayerProps,
  type FreeformLayerProps,
  type HybridCanvasProps,
} from '@ghatana/canvas/hybrid';

// CanvasFlow / CanvasFlowShim removed — use GraphLayer from @ghatana/canvas/hybrid directly
export { GraphLayer as CanvasFlow } from '@ghatana/canvas/hybrid';

// Default node types placeholder (product extensions should provide their own)
export const nodeTypes = {};
export const DefaultNode = () => null;
export const InputNode = () => null;
export const OutputNode = () => null;
