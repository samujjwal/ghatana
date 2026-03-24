/**
 * @ghatana/yappc-sketch - Type Definitions
 *
 * Production-grade type system for sketch and whiteboard functionality.
 *
 * @doc.type module
 * @doc.purpose Type definitions for sketch library
 * @doc.layer shared
 * @doc.pattern Type System
 */

/**
 * Available sketch tools
 */
export type SketchTool =
  | 'select'
  | 'pen'
  | 'highlighter'
  | 'eraser'
  | 'rectangle'
  | 'ellipse'
  | 'line'
  | 'arrow'
  | 'text'
  | 'sticky'
  | 'image';

/**
 * 2D point representation
 */
export interface Point {
  x: number;
  y: number;
}

/**
 * Pressure-sensitive point for stylus support
 */
export interface PressurePoint extends Point {
  pressure: number;
  tiltX?: number;
  tiltY?: number;
  timestamp?: number;
}

/**
 * Stroke data for freehand drawing
 */
export interface StrokeData {
  id: string;
  points: number[];
  color: string;
  strokeWidth: number;
  tool: 'pen' | 'highlighter' | 'eraser';
  opacity?: number;
  smoothed?: boolean;
  pressurePoints?: PressurePoint[];
}

/**
 * Shape data for geometric shapes
 */
export interface ShapeData {
  id: string;
  type: 'rectangle' | 'ellipse' | 'line' | 'arrow' | 'polygon';
  x: number;
  y: number;
  width: number;
  height: number;
  fill?: string;
  stroke?: string;
  strokeWidth?: number;
  rotation?: number;
  cornerRadius?: number;
  points?: number[]; // For polygons and lines
}

/**
 * Text element data
 */
export interface TextData {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  content: string;
  fontSize: number;
  fontFamily: string;
  fontWeight?: 'normal' | 'bold';
  fontStyle?: 'normal' | 'italic';
  textAlign?: 'left' | 'center' | 'right';
  color: string;
  backgroundColor?: string;
}

/**
 * Sticky note data
 */
export interface StickyNoteData {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  content: string;
  color: string;
  fontSize?: number;
  author?: string;
  createdAt?: number;
  updatedAt?: number;
}

/**
 * Image element data
 */
export interface ImageData {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  src: string;
  alt?: string;
  opacity?: number;
  rotation?: number;
}

/**
 * Union type for all sketch element data
 */
export type SketchElementData =
  | StrokeData
  | ShapeData
  | TextData
  | StickyNoteData
  | ImageData;

/**
 * Sketch element wrapper
 */
export interface SketchElement {
  id: string;
  kind: 'stroke' | 'shape' | 'text' | 'sticky' | 'image';
  type: string;
  position: Point;
  data: SketchElementData;
  zIndex?: number;
  locked?: boolean;
  visible?: boolean;
  groupId?: string;
}

/**
 * Tool configuration
 */
export interface SketchToolConfig {
  color: string;
  strokeWidth: number;
  fill?: string;
  opacity?: number;
  fontSize?: number;
  fontFamily?: string;
}

/**
 * Default tool configurations
 */
export const DEFAULT_TOOL_CONFIGS: Record<SketchTool, SketchToolConfig> = {
  select: { color: '#000000', strokeWidth: 1 },
  pen: { color: '#000000', strokeWidth: 2, opacity: 1 },
  highlighter: { color: '#FFFF00', strokeWidth: 20, opacity: 0.4 },
  eraser: { color: '#FFFFFF', strokeWidth: 20 },
  rectangle: { color: '#000000', strokeWidth: 2, fill: 'transparent' },
  ellipse: { color: '#000000', strokeWidth: 2, fill: 'transparent' },
  line: { color: '#000000', strokeWidth: 2 },
  arrow: { color: '#000000', strokeWidth: 2 },
  text: { color: '#000000', strokeWidth: 1, fontSize: 16, fontFamily: 'Inter' },
  sticky: { color: '#FFF59D', strokeWidth: 1, fontSize: 14 },
  image: { color: '#000000', strokeWidth: 1 },
};

/**
 * Alignment guide for snapping
 */
export interface AlignmentGuide {
  id: string;
  type: 'vertical' | 'horizontal';
  position: number;
  elements: string[];
}

/**
 * Viewport state
 */
export interface SketchViewport {
  x: number;
  y: number;
  zoom: number;
  minZoom?: number;
  maxZoom?: number;
}

/**
 * Sketch layer for organization
 */
export interface SketchLayer {
  id: string;
  name: string;
  visible: boolean;
  locked: boolean;
  opacity: number;
  elements: string[];
}

/**
 * Sketch document state
 */
export interface SketchDocument {
  id: string;
  name: string;
  elements: SketchElement[];
  layers: SketchLayer[];
  viewport: SketchViewport;
  background?: string;
  gridEnabled?: boolean;
  gridSize?: number;
  snapToGrid?: boolean;
  createdAt: number;
  updatedAt: number;
}

/**
 * History entry for undo/redo
 */
export interface SketchHistoryEntry {
  id: string;
  type: 'add' | 'update' | 'delete' | 'batch';
  elements: SketchElement[];
  timestamp: number;
  description?: string;
}

/**
 * Export options
 */
export interface SketchExportOptions {
  format: 'png' | 'svg' | 'pdf' | 'json';
  quality?: number;
  scale?: number;
  background?: string;
  includeGrid?: boolean;
  selection?: string[];
}

/**
 * Event types for sketch operations
 */
export type SketchEventType =
  | 'element:add'
  | 'element:update'
  | 'element:delete'
  | 'element:select'
  | 'viewport:change'
  | 'tool:change'
  | 'history:undo'
  | 'history:redo'
  | 'document:save'
  | 'document:load';

/**
 * Sketch event payload
 */
export interface SketchEvent<T = unknown> {
  type: SketchEventType;
  payload: T;
  timestamp: number;
}
