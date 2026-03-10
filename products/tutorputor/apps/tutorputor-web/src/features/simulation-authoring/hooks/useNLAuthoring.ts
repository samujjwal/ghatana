/**
 * useNLAuthoring Hook
 *
 * Custom hook for managing natural language simulation authoring workflow.
 * Handles API calls, state management, and conversation history.
 *
 * @doc.type hook
 * @doc.purpose NL authoring state management and API integration
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo, useRef } from "react";
import { useMutation } from "@tanstack/react-query";
import type {
  SimulationManifest,
  SimulationDomain,
  GenerateManifestRequest as ContractGenerateRequest,
  GenerateManifestResult,
  RefineManifestRequest as ContractRefineRequest,
  SuggestParametersRequest as ContractSuggestRequest,
  SuggestParametersResult,
} from "@ghatana/tutorputor-contracts/v1/simulation/types";

// Client-facing request types omit server-injected fields
type GenerateManifestRequest = Omit<
  ContractGenerateRequest,
  "tenantId" | "userId"
>;
type RefineManifestRequest = Omit<
  ContractRefineRequest,
  "tenantId" | "userId"
> & {
  feedback?: string;
};
type SuggestParametersRequest = Omit<ContractSuggestRequest, "tenantId"> & {
  currentManifest?: SimulationManifest;
};

// =============================================================================
// Types
// =============================================================================

export interface NLAuthoringState {
  /**
   * Currently selected domain.
   */
  domain: SimulationDomain;

  /**
   * Current manifest being authored.
   */
  manifest: SimulationManifest | null;

  /**
   * Conversation history.
   */
  history: AuthoringMessage[];

  /**
   * Generation constraints.
   */
  constraints: GenerationConstraints;

  /**
   * Whether currently loading.
   */
  isLoading: boolean;

  /**
   * Current error, if any.
   */
  error: Error | null;

  /**
   * Confidence of last generation.
   */
  confidence: number | null;

  /**
   * Suggested parameters from AI.
   */
  suggestions: ParameterSuggestion[] | null;
}

export interface AuthoringMessage {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  timestamp: Date;
  manifestSnapshot?: SimulationManifest;
  confidence?: number;
  type: "generate" | "refine" | "suggest" | "error";
}

export interface GenerationConstraints {
  maxSteps: number;
  maxEntities: number;
  targetDuration: number;
  difficultyLevel?: "beginner" | "intermediate" | "advanced";
}

export interface ParameterSuggestion {
  name: string;
  value: unknown;
  explanation: string;
  confidence: number;
}

export interface UseNLAuthoringOptions {
  /**
   * API base URL.
   */
  apiBaseUrl?: string;

  /**
   * Initial domain.
   */
  initialDomain?: SimulationDomain;

  /**
   * Initial manifest for editing mode.
   */
  initialManifest?: SimulationManifest;

  /**
   * Callback when manifest changes.
   */
  onManifestChange?: (manifest: SimulationManifest | null) => void;

  /**
   * Enable auto-suggestions.
   */
  enableSuggestions?: boolean;

  /**
   * Cache key prefix.
   */
  cacheKeyPrefix?: string;
}

export interface UseNLAuthoringReturn extends NLAuthoringState {
  // Actions
  setDomain: (domain: SimulationDomain) => void;
  setConstraints: (constraints: Partial<GenerationConstraints>) => void;
  generate: (prompt: string) => Promise<SimulationManifest | null>;
  refine: (feedback: string) => Promise<SimulationManifest | null>;
  suggestParameters: (context: string) => Promise<ParameterSuggestion[]>;
  clearHistory: () => void;
  reset: () => void;
  revertToSnapshot: (messageId: string) => void;

  // Computed
  canRefine: boolean;
  historySnapshots: Array<{ id: string; title: string; timestamp: Date }>;
}

// =============================================================================
// API Client
// =============================================================================

interface SimAuthorAPIClient {
  generate: (req: GenerateManifestRequest) => Promise<GenerateManifestResult>;
  refine: (req: RefineManifestRequest) => Promise<GenerateManifestResult>;
  suggest: (req: SuggestParametersRequest) => Promise<SuggestParametersResult>;
}

function createAPIClient(baseUrl: string): SimAuthorAPIClient {
  const fetchJSON = async <T>(endpoint: string, body: unknown): Promise<T> => {
    const response = await fetch(`${baseUrl}${endpoint}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const error = await response
        .json()
        .catch(() => ({ message: "Unknown error" }));
      throw new Error(error.message || `HTTP ${response.status}`);
    }

    return response.json();
  };

  return {
    generate: (req) => fetchJSON("/api/sim-author/generate", req),
    refine: (req) => fetchJSON("/api/sim-author/refine", req),
    suggest: (req) => fetchJSON("/api/sim-author/suggest", req),
  };
}

// =============================================================================
// Hook Implementation
// =============================================================================

const DEFAULT_CONSTRAINTS: GenerationConstraints = {
  maxSteps: 10,
  maxEntities: 8,
  targetDuration: 30,
  difficultyLevel: "intermediate",
};

export function useNLAuthoring(
  options: UseNLAuthoringOptions = {},
): UseNLAuthoringReturn {
  const {
    apiBaseUrl = "/api",
    initialDomain = "CS_DISCRETE",
    initialManifest = null,
    onManifestChange,
  } = options;

  const apiClient = useMemo(() => createAPIClient(apiBaseUrl), [apiBaseUrl]);
  const messageIdRef = useRef(0);

  // State
  const [domain, setDomain] = useState<SimulationDomain>(
    initialManifest?.domain || initialDomain,
  );
  const [manifest, setManifest] = useState<SimulationManifest | null>(
    initialManifest,
  );
  const [history, setHistory] = useState<AuthoringMessage[]>([]);
  const [constraints, setConstraintsState] =
    useState<GenerationConstraints>(DEFAULT_CONSTRAINTS);
  const [error, setError] = useState<Error | null>(null);
  const [confidence, setConfidence] = useState<number | null>(null);
  const [suggestions, setSuggestions] = useState<ParameterSuggestion[] | null>(
    null,
  );

  // Generate unique message ID
  const generateMessageId = useCallback(() => {
    messageIdRef.current += 1;
    return `msg_${Date.now()}_${messageIdRef.current}`;
  }, []);

  // Add message to history
  const addMessage = useCallback(
    (
      role: AuthoringMessage["role"],
      content: string,
      type: AuthoringMessage["type"],
      extras?: Partial<AuthoringMessage>,
    ): AuthoringMessage => {
      const message: AuthoringMessage = {
        id: generateMessageId(),
        role,
        content,
        timestamp: new Date(),
        type,
        ...extras,
      };
      setHistory((prev) => [...prev, message]);
      return message;
    },
    [generateMessageId],
  );

  // Update manifest and notify
  const updateManifest = useCallback(
    (newManifest: SimulationManifest | null) => {
      setManifest(newManifest);
      if (newManifest) {
        setDomain(newManifest.domain);
      }
      onManifestChange?.(newManifest);
    },
    [onManifestChange],
  );

  // Generate mutation
  const generateMutation = useMutation({
    mutationFn: async (prompt: string): Promise<GenerateManifestResult> => {
      const request: GenerateManifestRequest = {
        domain,
        prompt,
        constraints: {
          maxSteps: constraints.maxSteps,
          maxEntities: constraints.maxEntities,
          targetDuration: constraints.targetDuration,
        },
      };
      return apiClient.generate(request);
    },
    onSuccess: (result) => {
      setConfidence(result.confidence ?? null);
      if (result.manifest) {
        updateManifest(result.manifest);
        addMessage(
          "assistant",
          `Created "${result.manifest.title}" with ${result.manifest.steps.length} steps.`,
          "generate",
          {
            manifestSnapshot: result.manifest,
            confidence: result.confidence,
          },
        );
      }
      setError(null);
    },
    onError: (err: Error) => {
      setError(err);
      addMessage("assistant", `Error: ${err.message}`, "error");
    },
  });

  // Refine mutation
  const refineMutation = useMutation({
    mutationFn: async (feedback: string): Promise<GenerateManifestResult> => {
      if (!manifest) {
        throw new Error("No manifest to refine");
      }
      const request: RefineManifestRequest = {
        manifest,
        refinement: feedback,
        feedback,
      };
      return apiClient.refine(request);
    },
    onSuccess: (result) => {
      setConfidence(result.confidence ?? null);
      if (result.manifest) {
        updateManifest(result.manifest);
        addMessage(
          "assistant",
          `Refined to "${result.manifest.title}" with ${result.manifest.steps.length} steps.`,
          "refine",
          {
            manifestSnapshot: result.manifest,
            confidence: result.confidence,
          },
        );
      }
      setError(null);
    },
    onError: (err: Error) => {
      setError(err);
      addMessage("assistant", `Refinement error: ${err.message}`, "error");
    },
  });

  // Suggest mutation
  const suggestMutation = useMutation({
    mutationFn: async (context: string): Promise<SuggestParametersResult> => {
      const request: SuggestParametersRequest = {
        domain,
        context,
        currentManifest: manifest || undefined,
      };
      return apiClient.suggest(request);
    },
    onSuccess: (result) => {
      const paramSuggestions: ParameterSuggestion[] = result.suggestions.map(
        (s) => ({
          name: s.parameterName,
          value: s.suggestedValue,
          explanation: s.explanation,
          confidence: s.confidence,
        }),
      );
      setSuggestions(paramSuggestions);
      addMessage(
        "assistant",
        `Suggested ${paramSuggestions.length} parameters.`,
        "suggest",
      );
    },
    onError: (err: Error) => {
      setError(err);
    },
  });

  // Public actions
  const generate = useCallback(
    async (prompt: string): Promise<SimulationManifest | null> => {
      addMessage("user", prompt, "generate");
      const result = await generateMutation.mutateAsync(prompt);
      return result.manifest || null;
    },
    [addMessage, generateMutation],
  );

  const refine = useCallback(
    async (feedback: string): Promise<SimulationManifest | null> => {
      addMessage("user", `Refine: ${feedback}`, "refine");
      const result = await refineMutation.mutateAsync(feedback);
      return result.manifest || null;
    },
    [addMessage, refineMutation],
  );

  const suggestParameters = useCallback(
    async (context: string): Promise<ParameterSuggestion[]> => {
      const result = await suggestMutation.mutateAsync(context);
      return result.suggestions.map((s) => ({
        name: s.parameterName,
        value: s.suggestedValue,
        explanation: s.explanation,
        confidence: s.confidence,
      }));
    },
    [suggestMutation],
  );

  const setConstraints = useCallback(
    (updates: Partial<GenerationConstraints>) => {
      setConstraintsState((prev) => ({ ...prev, ...updates }));
    },
    [],
  );

  const clearHistory = useCallback(() => {
    setHistory([]);
    setError(null);
    setConfidence(null);
    setSuggestions(null);
  }, []);

  const reset = useCallback(() => {
    setManifest(null);
    setHistory([]);
    setError(null);
    setConfidence(null);
    setSuggestions(null);
    setConstraintsState(DEFAULT_CONSTRAINTS);
    setDomain(initialDomain);
  }, [initialDomain]);

  const revertToSnapshot = useCallback(
    (messageId: string) => {
      const message = history.find((m) => m.id === messageId);
      if (message?.manifestSnapshot) {
        updateManifest(message.manifestSnapshot);
        addMessage(
          "system",
          `Reverted to snapshot from ${message.timestamp.toLocaleTimeString()}`,
          "refine",
        );
      }
    },
    [history, updateManifest, addMessage],
  );

  // Computed values
  const canRefine = manifest !== null;

  const historySnapshots = useMemo(() => {
    return history
      .filter((m) => m.manifestSnapshot)
      .map((m) => ({
        id: m.id,
        title: m.manifestSnapshot!.title,
        timestamp: m.timestamp,
      }));
  }, [history]);

  const isLoading =
    generateMutation.isPending ||
    refineMutation.isPending ||
    suggestMutation.isPending;

  return {
    // State
    domain,
    manifest,
    history,
    constraints,
    isLoading,
    error,
    confidence,
    suggestions,

    // Actions
    setDomain,
    setConstraints,
    generate,
    refine,
    suggestParameters,
    clearHistory,
    reset,
    revertToSnapshot,

    // Computed
    canRefine,
    historySnapshots,
  };
}

export default useNLAuthoring;
