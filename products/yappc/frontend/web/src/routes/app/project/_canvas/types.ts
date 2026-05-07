/**
 * Canvas route types
 *
 * Shared types used across the decomposed canvas modules.
 *
 * @doc.type types
 * @doc.purpose Canvas route type definitions
 * @doc.layer product
 * @doc.pattern ValueObject
 */

import type { CanvasSyncStatus as SharedCanvasSyncStatus } from '@/services/canvas/canvasSyncStatus';

export type DrawingTool = 'pen' | 'pencil' | 'marker' | 'highlighter' | 'eraser';

export interface NodeContextMenuState {
  x: number;
  y: number;
  nodeId: string;
}

export interface Point {
  x: number;
  y: number;
}

export type CanvasSyncStatus = SharedCanvasSyncStatus;
