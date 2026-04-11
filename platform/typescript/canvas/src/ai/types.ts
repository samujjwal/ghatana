/**
 * AI Canvas Types
 *
 * @doc.type module
 * @doc.purpose Type definitions for AI/ML integration within the canvas platform
 * @doc.layer platform
 * @doc.pattern Types
 *
 * These types define the platform-level AI interface.  Product implementations
 * (e.g., YAPPC Canvas AI Service) plug in by providing a concrete `CanvasAIAdapter`.
 * The platform itself never calls any AI API directly — it only defines contracts.
 */

import type { CanvasElementType } from "../types/index.js";

// ---------------------------------------------------------------------------
// Suggestion types
// ---------------------------------------------------------------------------

export type AISuggestionKind =
  | "layout"        // Rearrange / align elements
  | "content"       // Fill in or enhance element content
  | "connection"    // Suggest connectors between elements
  | "element"       // Suggest adding a new element
  | "label"         // Suggest labels / titles
  | "summarize"     // Summarize selected content
  | "diagram"       // Generate a diagram from description
  | "code"          // Generate or explain code
  | "search";       // Find related elements

export interface AISuggestion {
  /** Unique suggestion identifier */
  id: string;
  kind: AISuggestionKind;
  /** Short human-readable description */
  title: string;
  /** Longer explanation */
  description?: string;
  /** Confidence score 0–1 */
  confidence: number;
  /**
   * Opaque payload interpreted by the accept handler.
   * Layout suggestions carry element-position maps;
   * content suggestions carry patch objects, etc.
   */
  payload: Record<string, unknown>;
  /** IDs of elements this suggestion acts on */
  targetElementIds?: string[];
  /** Suggested new element type (for "element" suggestions) */
  suggestedElementType?: CanvasElementType;
}

// ---------------------------------------------------------------------------
// Context the platform provides to the AI
// ---------------------------------------------------------------------------

export interface CanvasAIContext {
  /** Currently selected element IDs */
  selectedElementIds: string[];
  /** Active layer name */
  activeLayer: string;
  /** All visible element IDs */
  visibleElementIds: string[];
  /** Free-form user query (optional, from command palette or prompt bar) */
  userQuery?: string;
  /** Product-specific metadata (passed through from the product layer) */
  productMetadata?: Record<string, unknown>;
}

// ---------------------------------------------------------------------------
// Result types
// ---------------------------------------------------------------------------

export interface AIGenerateElementResult {
  /** Element type to create */
  elementType: CanvasElementType;
  /** Initial props for the new element */
  props: Record<string, unknown>;
  /** Suggested position on canvas (optional) */
  suggestedPosition?: { x: number; y: number };
}

export interface AILayoutResult {
  /** Map of element ID → new {x, y} position */
  positions: Record<string, { x: number; y: number }>;
  /** Optional: map of element ID → new {width, height} */
  sizes?: Record<string, { width: number; height: number }>;
}

export interface AISummarizeResult {
  markdown: string;
  plainText: string;
}

export interface AICodeResult {
  code: string;
  language: string;
  explanation?: string;
}

// Union of possible results
export type AIResult =
  | { kind: "suggestions"; suggestions: AISuggestion[] }
  | { kind: "layout"; result: AILayoutResult }
  | { kind: "generate-elements"; elements: AIGenerateElementResult[] }
  | { kind: "summarize"; result: AISummarizeResult }
  | { kind: "code"; result: AICodeResult }
  | { kind: "error"; message: string; retryable: boolean };

// ---------------------------------------------------------------------------
// Adapter interface — implemented by products
// ---------------------------------------------------------------------------

/**
 * CanvasAIAdapter
 *
 * Products implement this interface and pass it to `AICanvasProvider`.
 * This keeps the platform dependency-free from specific AI backends
 * (gRPC, REST, WebSocket, local WASM, etc.).
 */
export interface CanvasAIAdapter {
  /**
   * Request AI suggestions given the current canvas context.
   * Called when the user explicitly triggers "AI Suggest" or when the
   * platform detects an appropriate moment (idle after bulk edit, etc.).
   */
  getSuggestions(context: CanvasAIContext): Promise<AISuggestion[]>;

  /**
   * Accept a suggestion — the adapter is responsible for executing the
   * underlying action (layout rearrangement, content patch, etc.).
   * Returns the updated suggestion with applied status, or throws.
   */
  acceptSuggestion(
    suggestion: AISuggestion,
    context: CanvasAIContext,
  ): Promise<AIResult>;

  /**
   * Dismiss a suggestion (the adapter may use this for feedback).
   */
  dismissSuggestion(suggestionId: string): Promise<void>;

  /**
   * Process an explicit user query from the AI prompt bar.
   */
  query(context: CanvasAIContext): Promise<AIResult>;

  /**
   * Request auto-layout for the current set of elements.
   */
  autoLayout(context: CanvasAIContext): Promise<AILayoutResult>;

  /**
   * Generate new elements from a text description.
   */
  generateElements(
    description: string,
    context: CanvasAIContext,
  ): Promise<AIGenerateElementResult[]>;
}

// ---------------------------------------------------------------------------
// Feature flags exposed by the adapter
// ---------------------------------------------------------------------------

export interface CanvasAICapabilities {
  suggestions: boolean;
  autoLayout: boolean;
  generateElements: boolean;
  summarize: boolean;
  codeGeneration: boolean;
}

// ---------------------------------------------------------------------------
// State managed by the AI provider
// ---------------------------------------------------------------------------

export interface CanvasAIState {
  /** Whether the adapter is currently processing a request */
  loading: boolean;
  /** Active suggestions waiting for user action */
  pendingSuggestions: AISuggestion[];
  /** Last error message, if any */
  error: string | null;
  /** AI prompt bar text */
  promptText: string;
  /** Whether the AI prompt bar is open */
  promptOpen: boolean;
  /** Capabilities reported by the adapter */
  capabilities: CanvasAICapabilities;
}
