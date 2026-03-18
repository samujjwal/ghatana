/**
 * Mode Renderer Type Definitions
 * 
 * @doc.type types
 * @doc.purpose Define interfaces for mode-specific canvas renderers
 * @doc.layer product
 * @doc.pattern TypeDefinitions
 */

import type { ReactNode } from 'react';
import type { CanvasMode } from '../../../types/canvas';

/**
 * Abstraction levels for drilling down into canvas content
 */
export type AbstractionLevel = 'system' | 'component' | 'file' | 'code';

/**
 * Common props passed to all mode renderers
 */
export interface ModeRendererProps {
  /** Current abstraction level */
  level: AbstractionLevel;
  /** Project identifier */
  projectId: string;
  /** Canvas identifier */
  canvasId?: string;
  /** Whether canvas has existing content */
  hasContent: boolean;
  /** Callback when user asks AI for help */
  onAskAI?: () => void;
  /** Callback to get started with the mode */
  onGetStarted?: () => void;
  /** Callback when user drills down to a deeper level */
  onDrillDown?: (targetId?: string) => void;
  /** Callback when user zooms out to a higher level */
  onZoomOut?: () => void;
  /** Optional children to render within the mode */
  children?: ReactNode;
  /** Whether the canvas is in read-only mode */
  readOnly?: boolean;
}

/**
 * Props for the unified mode content renderer
 */
export interface ModeContentProps extends ModeRendererProps {
  /** Current canvas mode */
  mode: CanvasMode;
}

/**
 * Configuration for each mode×level combination
 */
export interface ModeLevelConfig {
  /** Description of what this mode×level shows */
  description: string;
  /** Available tools for this combination */
  tools: string[];
  /** Message shown when canvas is empty */
  emptyMessage: string;
  /** AI action suggestion */
  aiSuggestion: string;
  /** Icon component for this state */
  icon: ReactNode;
}

/**
 * Node type for canvas elements
 */
export interface CanvasNodeData {
  id: string;
  label: string;
  type?: string;
  status?: 'idle' | 'active' | 'completed' | 'error';
  [key: string]: unknown;
}
