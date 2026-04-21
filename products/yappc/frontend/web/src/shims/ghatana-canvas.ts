/**
 * Ghatana Canvas Shim
 * Stub implementation for canvas package imports
 */
import type { ReactNode } from 'react';

export type AISuggestionKind = 'content' | 'layout' | 'element' | 'workflow';

export interface AISuggestion {
  id: string;
  kind: AISuggestionKind;
  title: string;
  description?: string;
  confidence?: number;
  targetElementIds?: string[];
  payload?: Record<string, unknown>;
}

export interface CanvasAIContext {
  selectedElementIds?: string[];
  activeLayer?: string;
  visibleElementIds?: string[];
  userQuery?: string;
  productMetadata?: Record<string, unknown>;
}

export interface AIGenerateElementResult {
  id?: string;
  type?: string;
  label?: string;
  position?: { x: number; y: number };
  [key: string]: unknown;
}

export interface AILayoutResult {
  positions: Record<string, unknown>;
}

export type AIResult =
  | { kind: 'suggestions'; suggestions: AISuggestion[] }
  | { kind: 'layout'; result: AILayoutResult }
  | { kind: 'generate-elements'; elements: AIGenerateElementResult[] }
  | { kind: 'error'; message: string; retryable?: boolean };

export interface CanvasAIAdapter {
  getSuggestions(context: CanvasAIContext): Promise<AISuggestion[]>;
  acceptSuggestion(suggestion: AISuggestion, context: CanvasAIContext): Promise<AIResult>;
  dismissSuggestion(suggestionId: string): Promise<void>;
  query(context: CanvasAIContext): Promise<AIResult>;
  autoLayout(context: CanvasAIContext): Promise<AILayoutResult>;
  generateElements(description: string, context: CanvasAIContext): Promise<AIGenerateElementResult[]>;
}

export interface AICanvasProviderProps {
  children: ReactNode;
  adapter?: CanvasAIAdapter;
}

export interface CanvasAIContextValue {
  adapter: CanvasAIAdapter | null;
  getSuggestions: (context: CanvasAIContext) => Promise<void>;
  acceptSuggestion: (suggestion: AISuggestion, context: CanvasAIContext) => Promise<AIResult>;
  submitPrompt: (context: CanvasAIContext) => Promise<AIResult>;
  autoLayout: (context: CanvasAIContext) => Promise<AILayoutResult>;
  generateElements: (description: string, context: CanvasAIContext) => Promise<AIGenerateElementResult[]>;
  suggestions: AISuggestion[];
  isLoading: boolean;
}

export interface ActionContext {
  projectId?: string;
  workspaceId?: string;
  userId?: string;
  phase?: string;
  selection?: string[];
  metadata?: Record<string, unknown>;
}

export interface ActionDefinition {
  id: string;
  label: string;
  description?: string;
  icon?: string;
  shortcut?: string;
  category?: string;
  priority?: number;
  handler?: (context: ActionContext) => Promise<void> | void;
  isEnabled?: (context: ActionContext) => boolean;
  isVisible?: (context: ActionContext) => boolean;
  execute?: (context: ActionContext) => Promise<void> | void;
  enabled?: (context: ActionContext) => boolean;
}

export const Canvas = () => null;
export const CanvasProvider = ({ children }: { children: ReactNode }) => children;
export const useCanvas = () => ({});
export const CanvasManager = () => null;
export const ReactComponentRenderer = () => null;
export const AICanvasProvider = ({ children }: AICanvasProviderProps) => children;

// Layout components
export const CanvasChromeLayout = ({ children }: { children: ReactNode }) => children;

// Hooks
export const useCanvasCommands = () => ({
  zoomIn: () => {},
  zoomOut: () => {},
  fitView: () => {},
  centerView: () => {},
});
export const useCanvasTelemetry = () => ({
  trackEvent: () => {},
  trackError: () => {},
});
export const useCanvasAI = (): CanvasAIContextValue => ({
  adapter: null,
  getSuggestions: async () => {},
  acceptSuggestion: async () => ({ kind: 'error', message: 'Canvas AI shim', retryable: false }),
  submitPrompt: async () => ({ kind: 'error', message: 'Canvas AI shim', retryable: false }),
  autoLayout: async () => ({ positions: {} }),
  generateElements: async () => [],
  suggestions: [],
  isLoading: false,
});
export const useCanvasAISuggestions = (): AISuggestion[] => [];
export const useCanvasAILoading = (): boolean => false;

// Atoms/state
export const chromeCalmModeAtom = { init: false };
export const chromeInspectorVisibleAtom = { init: false };
export const chromeLeftRailVisibleAtom = { init: true };
export const chromeMinimapVisibleAtom = { init: true };
export const chromeZoomLevelAtom = { init: 1 };

// Default export
export default Canvas;
