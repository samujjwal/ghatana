/**
 * @ghatana/yappc-sketch - Production-Grade Sketch & Whiteboard Library
 *
 * A comprehensive library for freehand drawing, whiteboarding, and annotation
 * capabilities within the YAPPC canvas ecosystem.
 *
 * Features:
 * - 🎨 Freehand drawing with pressure sensitivity
 * - 📐 Geometric shapes (rectangle, ellipse, line, arrow)
 * - 📝 Text and sticky notes
 * - 🖼️ Image annotations
 * - ↩️ Undo/redo support
 * - 📤 Export to PNG, SVG, PDF
 * - 🎯 Snap-to-grid and alignment guides
 * - 🔄 Real-time collaboration ready
 *
 * @doc.type module
 * @doc.purpose Sketch and whiteboard library
 * @doc.layer product
 * @doc.pattern Library
 *
 * @example
 * ```tsx
 * import { SketchCanvas, SketchToolbar, useSketchTools } from '@ghatana/yappc-canvas/sketch';
 *
 * function Whiteboard() {
 *   const [tool, setTool] = useState<SketchTool>('pen');
 *   const [elements, setElements] = useState<SketchElement[]>([]);
 *
 *   return (
 *     <div>
 *       <SketchToolbar activeTool={tool} onToolChange={setTool} />
 *       <SketchCanvas
 *         width={800}
 *         height={600}
 *         activeTool={tool}
 *         elements={elements}
 *         onElementAdd={(el) => setElements([...elements, el])}
 *       />
 *     </div>
 *   );
 * }
 * ```
 */

// Types
export type {
  SketchTool,
  Point,
  PressurePoint,
  StrokeData,
  ShapeData,
  TextData,
  StickyNoteData,
  ImageData,
  SketchElementData,
  SketchElement,
  SketchToolConfig,
  AlignmentGuide,
  SketchViewport,
  SketchLayer,
  SketchDocument,
  SketchHistoryEntry,
  SketchExportOptions,
  SketchEventType,
  SketchEvent,
} from './types';

export { DEFAULT_TOOL_CONFIGS } from './types';

// Components
export {
  SketchCanvas,
  SketchToolbar,
  StickyNote,
  STICKY_COLORS,
} from './components';
export type {
  SketchCanvasProps,
  SketchCanvasRef,
  SketchToolbarProps,
  StickyNoteProps,
} from './components';

// Hooks
export { useSketchTools } from './hooks/useSketchTools';
export type { UseSketchToolsParams, UseSketchToolsResult } from './hooks/useSketchTools';

export { useSketchKeyboard } from './hooks/useSketchKeyboard';
export type { UseSketchKeyboardParams } from './hooks/useSketchKeyboard';

// Utilities
export {
  getSmoothStrokePath,
  simplifyPoints,
  pointsToInputPoints,
  pathToPoints,
  getPointsBounds,
} from './utils/smoothStroke';
export type { SmoothStrokeOptions } from './utils/smoothStroke';

// Version
export const SKETCH_VERSION = '1.0.0';
