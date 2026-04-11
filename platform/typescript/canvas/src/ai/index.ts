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
