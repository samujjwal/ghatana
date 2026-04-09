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

export interface Point {
  x: number;
  y: number;
}

export interface PressurePoint extends Point {
  pressure: number;
  tiltX?: number;
  tiltY?: number;
  timestamp?: number;
}

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
  points?: number[];
}

export interface SketchToolConfig {
  color: string;
  strokeWidth: number;
  fill?: string;
  opacity?: number;
  fontSize?: number;
  fontFamily?: string;
}

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