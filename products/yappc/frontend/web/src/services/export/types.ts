/**
 * Export service types
 */

import type { CanvasState } from '../../components/canvas/workspace/canvasAtoms';

/**
 *
 */
export type ExportFormat = 'png' | 'svg' | 'json' | 'jsx';

/**
 *
 */
export interface ExportOptions {
  format: ExportFormat;
  quality?: number; // For PNG (0-1)
  scale?: number; // For PNG/SVG
  width?: number;
  height?: number;
  includeBackground?: boolean;
  backgroundColor?: string;
  filename?: string;
  download?: boolean;
}

/**
 *
 */
export interface ExportResult {
  success: boolean;
  data?: string | Blob;
  filename?: string;
  format?: ExportFormat;
  metadata?: Record<string, unknown>;
  error?: string;
  correlationId?: string;
}

/**
 *
 */
export interface Template {
  id: string;
  name: string;
  description: string;
  thumbnail?: string;
  category: string;
  tags: string[];
  canvasState: CanvasState;
  metadata: {
    author?: string;
    version: string;
    createdAt: string;
    updatedAt: string;
  };
}

/**
 *
 */
export interface Layer {
  id: string;
  name: string;
  visible: boolean;
  locked: boolean;
  opacity: number;
  zIndex: number;
  elementIds: string[];
}

/**
 *
 */
export interface GroupDefinition {
  id: string;
  name: string;
  elementIds: string[];
  locked: boolean;
  collapsed: boolean;
}
