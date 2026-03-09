/**
 * Canvas API Module - Composable Component API
 *
 * Exports for embedding canvas functionality with controlled/uncontrolled modes.
 *
 * @module canvas/api
 */

export {
  // Main components
  CanvasProvider,
  CanvasFlow,
  
  // Hooks
  useCanvas,
  
  // Types
  type CanvasProviderProps,
  type CanvasFlowProps,
  type CanvasContextValue,
  type CanvasAPI,
  
  // Event types
  type CanvasChangeEvent,
  type CanvasChange,
  type CanvasElementEvent,
  type CanvasSelectionEvent,
  type CanvasViewportEvent,
  type CanvasInteractionEvent,
} from './canvasContext';
