/**
 * AI Canvas Context & Provider
 *
 * @doc.type module
 * @doc.purpose React context and hooks for AI/ML integration in the canvas
 * @doc.layer platform
 * @doc.pattern Provider/Hook
 *
 * Usage:
 * ```tsx
 * // In your product app, wrap the canvas with AICanvasProvider:
 * <AICanvasProvider adapter={myYappcAIAdapter}>
 *   <HybridCanvas ... />
 * </AICanvasProvider>
 *
 * // Inside canvas components:
 * const { getSuggestions, autoLayout, query, state } = useCanvasAI();
 * ```
 */

import React, {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useReducer,
} from "react";

import type {
  CanvasAIAdapter,
  CanvasAICapabilities,
  CanvasAIContext,
  CanvasAIState,
  AISuggestion,
  AIResult,
  AILayoutResult,
  AIGenerateElementResult,
} from "./types.js";

// ---------------------------------------------------------------------------
// No-op default adapter (platform does not provide a real one)
// ---------------------------------------------------------------------------

const noopAdapter: CanvasAIAdapter = {
  async getSuggestions() { return []; },
  async acceptSuggestion(_s, _ctx) { return { kind: "error", message: "No AI adapter configured", retryable: false }; },
  async dismissSuggestion() { /* noop */ },
  async query(_ctx) { return { kind: "error", message: "No AI adapter configured", retryable: false }; },
  async autoLayout(_ctx) { return { positions: {} }; },
  async generateElements(_desc, _ctx) { return []; },
};

const defaultCapabilities: CanvasAICapabilities = {
  suggestions: false,
  autoLayout: false,
  generateElements: false,
  summarize: false,
  codeGeneration: false,
};

// ---------------------------------------------------------------------------
// State reducer
// ---------------------------------------------------------------------------

type Action =
  | { type: "SET_LOADING"; payload: boolean }
  | { type: "SET_SUGGESTIONS"; payload: AISuggestion[] }
  | { type: "REMOVE_SUGGESTION"; payload: string }
  | { type: "SET_ERROR"; payload: string | null }
  | { type: "SET_PROMPT"; payload: string }
  | { type: "SET_PROMPT_OPEN"; payload: boolean }
  | { type: "SET_CAPABILITIES"; payload: CanvasAICapabilities };

const initialState: CanvasAIState = {
  loading: false,
  pendingSuggestions: [],
  error: null,
  promptText: "",
  promptOpen: false,
  capabilities: defaultCapabilities,
};

function reducer(state: CanvasAIState, action: Action): CanvasAIState {
  switch (action.type) {
    case "SET_LOADING":
      return { ...state, loading: action.payload };
    case "SET_SUGGESTIONS":
      return { ...state, pendingSuggestions: action.payload, loading: false };
    case "REMOVE_SUGGESTION":
      return {
        ...state,
        pendingSuggestions: state.pendingSuggestions.filter(
          (s) => s.id !== action.payload,
        ),
      };
    case "SET_ERROR":
      return { ...state, error: action.payload, loading: false };
    case "SET_PROMPT":
      return { ...state, promptText: action.payload };
    case "SET_PROMPT_OPEN":
      return { ...state, promptOpen: action.payload };
    case "SET_CAPABILITIES":
      return { ...state, capabilities: action.payload };
    default:
      return state;
  }
}

// ---------------------------------------------------------------------------
// Context value
// ---------------------------------------------------------------------------

export interface CanvasAIContextValue {
  state: CanvasAIState;
  adapter: CanvasAIAdapter;

  /** Trigger suggestion fetch */
  getSuggestions(context: CanvasAIContext): Promise<void>;
  /** Accept a suggestion */
  acceptSuggestion(suggestion: AISuggestion, context: CanvasAIContext): Promise<AIResult>;
  /** Dismiss a suggestion */
  dismissSuggestion(suggestionId: string): Promise<void>;
  /** Open the AI prompt bar with optional initial text */
  openPrompt(initialText?: string): void;
  /** Close the AI prompt bar */
  closePrompt(): void;
  /** Submit the current prompt */
  submitPrompt(context: CanvasAIContext): Promise<AIResult>;
  /** Trigger auto-layout */
  autoLayout(context: CanvasAIContext): Promise<AILayoutResult>;
  /** Generate new elements */
  generateElements(description: string, context: CanvasAIContext): Promise<AIGenerateElementResult[]>;
  /** Update the prompt text */
  setPromptText(text: string): void;
}

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

const CanvasAICtx = createContext<CanvasAIContextValue | null>(null);

// ---------------------------------------------------------------------------
// Provider
// ---------------------------------------------------------------------------

export interface AICanvasProviderProps {
  adapter?: CanvasAIAdapter;
  capabilities?: Partial<CanvasAICapabilities>;
  children: React.ReactNode;
}

export function AICanvasProvider({
  adapter = noopAdapter,
  capabilities,
  children,
}: AICanvasProviderProps): React.ReactElement {
  const [state, dispatch] = useReducer(reducer, {
    ...initialState,
    capabilities: { ...defaultCapabilities, ...capabilities },
  });

  const getSuggestions = useCallback(
    async (context: CanvasAIContext): Promise<void> => {
      dispatch({ type: "SET_LOADING", payload: true });
      dispatch({ type: "SET_ERROR", payload: null });
      try {
        const suggestions = await adapter.getSuggestions(context);
        dispatch({ type: "SET_SUGGESTIONS", payload: suggestions });
      } catch (e) {
        dispatch({
          type: "SET_ERROR",
          payload: e instanceof Error ? e.message : "AI error",
        });
      }
    },
    [adapter],
  );

  const acceptSuggestion = useCallback(
    async (suggestion: AISuggestion, context: CanvasAIContext): Promise<AIResult> => {
      dispatch({ type: "SET_LOADING", payload: true });
      try {
        const result = await adapter.acceptSuggestion(suggestion, context);
        dispatch({ type: "REMOVE_SUGGESTION", payload: suggestion.id });
        dispatch({ type: "SET_LOADING", payload: false });
        return result;
      } catch (e) {
        const msg = e instanceof Error ? e.message : "AI error";
        dispatch({ type: "SET_ERROR", payload: msg });
        return { kind: "error", message: msg, retryable: true };
      }
    },
    [adapter],
  );

  const dismissSuggestion = useCallback(
    async (suggestionId: string): Promise<void> => {
      dispatch({ type: "REMOVE_SUGGESTION", payload: suggestionId });
      await adapter.dismissSuggestion(suggestionId);
    },
    [adapter],
  );

  const openPrompt = useCallback((initialText?: string): void => {
    if (initialText !== undefined) {
      dispatch({ type: "SET_PROMPT", payload: initialText });
    }
    dispatch({ type: "SET_PROMPT_OPEN", payload: true });
  }, []);

  const closePrompt = useCallback((): void => {
    dispatch({ type: "SET_PROMPT_OPEN", payload: false });
  }, []);

  const setPromptText = useCallback((text: string): void => {
    dispatch({ type: "SET_PROMPT", payload: text });
  }, []);

  const submitPrompt = useCallback(
    async (context: CanvasAIContext): Promise<AIResult> => {
      dispatch({ type: "SET_LOADING", payload: true });
      dispatch({ type: "SET_ERROR", payload: null });
      try {
        const ctx: CanvasAIContext = { ...context, userQuery: state.promptText };
        const result = await adapter.query(ctx);
        dispatch({ type: "SET_LOADING", payload: false });
        dispatch({ type: "SET_PROMPT_OPEN", payload: false });
        return result;
      } catch (e) {
        const msg = e instanceof Error ? e.message : "AI error";
        dispatch({ type: "SET_ERROR", payload: msg });
        return { kind: "error", message: msg, retryable: true };
      }
    },
    [adapter, state.promptText],
  );

  const autoLayout = useCallback(
    async (context: CanvasAIContext): Promise<AILayoutResult> => {
      dispatch({ type: "SET_LOADING", payload: true });
      try {
        const result = await adapter.autoLayout(context);
        dispatch({ type: "SET_LOADING", payload: false });
        return result;
      } catch (e) {
        const msg = e instanceof Error ? e.message : "AI error";
        dispatch({ type: "SET_ERROR", payload: msg });
        return { positions: {} };
      }
    },
    [adapter],
  );

  const generateElements = useCallback(
    async (description: string, context: CanvasAIContext): Promise<AIGenerateElementResult[]> => {
      dispatch({ type: "SET_LOADING", payload: true });
      try {
        const elements = await adapter.generateElements(description, context);
        dispatch({ type: "SET_LOADING", payload: false });
        return elements;
      } catch (e) {
        const msg = e instanceof Error ? e.message : "AI error";
        dispatch({ type: "SET_ERROR", payload: msg });
        return [];
      }
    },
    [adapter],
  );

  const value = useMemo<CanvasAIContextValue>(
    () => ({
      state,
      adapter,
      getSuggestions,
      acceptSuggestion,
      dismissSuggestion,
      openPrompt,
      closePrompt,
      setPromptText,
      submitPrompt,
      autoLayout,
      generateElements,
    }),
    [
      state,
      adapter,
      getSuggestions,
      acceptSuggestion,
      dismissSuggestion,
      openPrompt,
      closePrompt,
      setPromptText,
      submitPrompt,
      autoLayout,
      generateElements,
    ],
  );

  return React.createElement(CanvasAICtx.Provider, { value }, children);
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * useCanvasAI — access the AI canvas context from any canvas component.
 *
 * Throws if used outside `AICanvasProvider`.
 */
export function useCanvasAI(): CanvasAIContextValue {
  const ctx = useContext(CanvasAICtx);
  if (!ctx) {
    throw new Error("useCanvasAI must be used within AICanvasProvider");
  }
  return ctx;
}

/**
 * useCanvasAISuggestions — convenience hook that only subscribes to pending suggestions.
 */
export function useCanvasAISuggestions(): AISuggestion[] {
  return useCanvasAI().state.pendingSuggestions;
}

/**
 * useCanvasAILoading — returns true while any AI operation is in-flight.
 */
export function useCanvasAILoading(): boolean {
  return useCanvasAI().state.loading;
}
