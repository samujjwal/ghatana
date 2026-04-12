/**
 * @fileoverview AI-native canvas capability group - platform-level AI integration.
 *
 * @doc.type module
 * @doc.purpose Provides AI capabilities for canvas operations with visibility contracts.
 * @doc.layer platform
 */

import type {
  AIVisibilityContract,
  AIChangeDescriptor,
  AutonomyExecutionMode,
} from '@ghatana/platform-events';

// ============================================================================
// AI Capability Types
// ============================================================================

export type AICanvasCapability =
  | 'autolayout'
  | 'suggest-elements'
  | 'suggest-connections'
  | 'generate-from-description'
  | 'summarize'
  | 'explain'
  | 'optimize'
  | 'review';

export interface AICanvasCapabilities {
  readonly autolayout: boolean;
  readonly suggestElements: boolean;
  readonly suggestConnections: boolean;
  readonly generateFromDescription: boolean;
  readonly summarize: boolean;
  readonly explain: boolean;
  readonly optimize: boolean;
  readonly review: boolean;
}

// ============================================================================
// AI Suggestion Types
// ============================================================================

export interface CanvasAISuggestion {
  readonly id: string;
  readonly capability: AICanvasCapability;
  readonly title: string;
  readonly description: string;
  readonly visibilityContract: AIVisibilityContract;
  readonly preview?: CanvasSuggestionPreview;
  readonly apply: () => Promise<void>;
  readonly reject: () => Promise<void>;
}

export interface CanvasSuggestionPreview {
  readonly type: 'highlight' | 'overlay' | 'ghost' | 'diff';
  readonly data: unknown;
}

// ============================================================================
// Canvas AI Request/Response
// ============================================================================

export interface CanvasAIRequest {
  readonly capability: AICanvasCapability;
  readonly canvasContext: CanvasAIContext;
  readonly prompt?: string;
  readonly autonomyMode: AutonomyExecutionMode;
}

export interface CanvasAIContext {
  readonly selectedElementIds: readonly string[];
  readonly visibleElementIds: readonly string[];
  readonly viewport: { readonly x: number; readonly y: number; readonly zoom: number };
  readonly canvasMode: 'edit' | 'preview' | 'review';
  readonly userPermissions: readonly string[];
}

export interface CanvasAIResponse {
  readonly suggestions: readonly CanvasAISuggestion[];
  readonly error?: string;
}

// ============================================================================
// Canvas AI Service Interface
// ============================================================================

export interface CanvasAIService {
  readonly capabilities: AICanvasCapabilities;
  readonly isAvailable: boolean;
  
  request(request: CanvasAIRequest): Promise<CanvasAIResponse>;
  
  streamSuggestions(
    request: CanvasAIRequest,
    onSuggestion: (suggestion: CanvasAISuggestion) => void,
  ): Promise<void>;
  
  cancelPending(): void;
}

// ============================================================================
// Default Capabilities
// ============================================================================

export const DEFAULT_AI_CAPABILITIES: AICanvasCapabilities = {
  autolayout: true,
  suggestElements: true,
  suggestConnections: true,
  generateFromDescription: true,
  summarize: false,
  explain: true,
  optimize: true,
  review: true,
};

// ============================================================================
// Capability Checking
// ============================================================================

export function hasCapability(
  capabilities: AICanvasCapabilities,
  capability: AICanvasCapability,
): boolean {
  switch (capability) {
    case 'autolayout': return capabilities.autolayout;
    case 'suggest-elements': return capabilities.suggestElements;
    case 'suggest-connections': return capabilities.suggestConnections;
    case 'generate-from-description': return capabilities.generateFromDescription;
    case 'summarize': return capabilities.summarize;
    case 'explain': return capabilities.explain;
    case 'optimize': return capabilities.optimize;
    case 'review': return capabilities.review;
    default: return false;
  }
}
