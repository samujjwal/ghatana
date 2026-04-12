/**
 * AI integration module index
 *
 * @doc.type module
 * @doc.purpose Public exports for AI/ML canvas integration
 * @doc.layer platform
 * @doc.pattern Index
 */

export type {
  AISuggestionKind,
  AISuggestion,
  CanvasAIContext,
  AIGenerateElementResult,
  AILayoutResult,
  AISummarizeResult,
  AICodeResult,
  AIResult,
  CanvasAIAdapter,
  CanvasAICapabilities,
  CanvasAIState,
} from "./types.js";

export {
  AICanvasProvider,
  useCanvasAI,
  useCanvasAISuggestions,
  useCanvasAILoading,
  type AICanvasProviderProps,
  type CanvasAIContextValue,
} from "./ai-canvas-provider.js";

// AI-native capability group
export type {
  AICanvasCapability,
  AICanvasCapabilities,
  CanvasAISuggestion,
  CanvasSuggestionPreview,
  CanvasAIRequest,
  CanvasAIResponse,
  CanvasAIService,
} from './capabilities.js';

export {
  DEFAULT_AI_CAPABILITIES,
  hasCapability,
} from './capabilities.js';

