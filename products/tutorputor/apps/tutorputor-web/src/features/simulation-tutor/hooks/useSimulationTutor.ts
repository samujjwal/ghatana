/**
 * useSimulationTutor Hook
 *
 * React hook for interacting with the simulation tutor service.
 * Handles streaming responses and context management.
 *
 * @doc.type module
 * @doc.purpose React hook for simulation tutor integration
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useRef } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";

// Local type definitions (mirroring contracts to avoid build order issues)
export interface SimulationManifest {
  id: string;
  title: string;
  domain: string;
  steps: SimulationStep[];
  entities?: SimEntity[];
  metadata?: Record<string, unknown>;
}

export interface SimulationStep {
  id: string;
  label: string;
  duration?: number;
  actions?: SimAction[];
}

export interface SimEntity {
  id: string;
  type: string;
  label: string;
  properties?: Record<string, unknown>;
}

export interface SimAction {
  type: string;
  targetEntityId?: string;
  parameters?: Record<string, unknown>;
}

export interface SimKeyframe {
  stepIndex: number;
  time: number;
  entities: Record<string, unknown>;
  parameters?: Record<string, unknown>;
}

// =============================================================================
// Types
// =============================================================================

export interface TutorMessage {
  id: string;
  role: "user" | "tutor";
  content: string;
  timestamp: number;
  hints?: string[];
  suggestions?: string[];
  followUpQuestions?: string[];
  isStreaming?: boolean;
}

export interface SimulationTutorState {
  messages: TutorMessage[];
  isLoading: boolean;
  isStreaming: boolean;
  error: string | null;
  hints: string[];
  suggestions: string[];
}

export interface SimulationTutorHookOptions {
  simulationId: string;
  manifest: SimulationManifest;
  onError?: (error: Error) => void;
}

export interface TrackedUserAction {
  timestamp: number;
  actionType: string;
  targetEntityId?: string;
  parameters?: Record<string, unknown>;
}

// =============================================================================
// API Functions
// =============================================================================

async function sendTutorQuestion(params: {
  simulationId: string;
  question: string;
  manifest: SimulationManifest;
  currentKeyframe?: SimKeyframe;
  recentActions?: TrackedUserAction[];
}): Promise<{
  answer: string;
  followUpQuestions: string[];
  hints: string[];
  suggestedActions: string[];
  confidence: number;
}> {
  const response = await fetch("/api/tutor/simulation", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(params),
  });

  if (!response.ok) {
    throw new Error("Failed to get tutor response");
  }

  return response.json();
}

async function streamTutorQuestion(
  params: {
    simulationId: string;
    question: string;
    manifest: SimulationManifest;
    currentKeyframe?: SimKeyframe;
    recentActions?: TrackedUserAction[];
  },
  onChunk: (chunk: { type: string; content?: string }) => void
): Promise<void> {
  const response = await fetch("/api/tutor/simulation/stream", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(params),
  });

  if (!response.ok) {
    throw new Error("Failed to start stream");
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error("No response body");
  }

  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() ?? "";

    for (const line of lines) {
      if (line.startsWith("data: ")) {
        try {
          const data = JSON.parse(line.slice(6));
          onChunk(data);
        } catch {
          // Ignore parse errors
        }
      }
    }
  }
}

// =============================================================================
// Hook
// =============================================================================

/**
 * Hook for simulation tutor functionality.
 *
 * @param options - Hook options including simulation ID and manifest
 * @returns Tutor state and functions
 */
export function useSimulationTutor(options: SimulationTutorHookOptions) {
  const { simulationId, manifest, onError } = options;

  // State
  const [messages, setMessages] = useState<TutorMessage[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [hints, setHints] = useState<string[]>([]);
  const [suggestions, setSuggestions] = useState<string[]>([]);

  // Refs for tracking simulation state
  const currentKeyframeRef = useRef<SimKeyframe | null>(null);
  const recentActionsRef = useRef<TrackedUserAction[]>([]);

  // Generate unique message ID
  const generateId = () => `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

  /**
   * Update the current simulation keyframe.
   */
  const updateKeyframe = useCallback((keyframe: SimKeyframe) => {
    currentKeyframeRef.current = keyframe;
  }, []);

  /**
   * Record a user action.
   */
  const recordAction = useCallback((action: TrackedUserAction) => {
    recentActionsRef.current.push(action);
    // Keep only last 20 actions
    if (recentActionsRef.current.length > 20) {
      recentActionsRef.current = recentActionsRef.current.slice(-20);
    }
  }, []);

  /**
   * Send a question to the tutor (non-streaming).
   */
  const askMutation = useMutation({
    mutationFn: async (question: string) => {
      return sendTutorQuestion({
        simulationId,
        question,
        manifest,
        currentKeyframe: currentKeyframeRef.current ?? undefined,
        recentActions: recentActionsRef.current,
      });
    },
    onSuccess: (data, question) => {
      // Add user message
      const userMessage: TutorMessage = {
        id: generateId(),
        role: "user",
        content: question,
        timestamp: Date.now(),
      };

      // Add tutor response
      const tutorMessage: TutorMessage = {
        id: generateId(),
        role: "tutor",
        content: data.answer,
        timestamp: Date.now(),
        hints: data.hints,
        suggestions: data.suggestedActions,
        followUpQuestions: data.followUpQuestions,
      };

      setMessages((prev) => [...prev, userMessage, tutorMessage]);
      setHints(data.hints);
      setSuggestions(data.suggestedActions);
    },
    onError: (error) => {
      onError?.(error as Error);
    },
  });

  /**
   * Send a question with streaming response.
   */
  const askWithStream = useCallback(
    async (question: string) => {
      const userMessage: TutorMessage = {
        id: generateId(),
        role: "user",
        content: question,
        timestamp: Date.now(),
      };

      const tutorMessageId = generateId();
      const tutorMessage: TutorMessage = {
        id: tutorMessageId,
        role: "tutor",
        content: "",
        timestamp: Date.now(),
        isStreaming: true,
      };

      setMessages((prev) => [...prev, userMessage, tutorMessage]);
      setIsStreaming(true);

      const newHints: string[] = [];
      const newSuggestions: string[] = [];

      try {
        await streamTutorQuestion(
          {
            simulationId,
            question,
            manifest,
            currentKeyframe: currentKeyframeRef.current ?? undefined,
            recentActions: recentActionsRef.current,
          },
          (chunk) => {
            switch (chunk.type) {
              case "text":
                setMessages((prev) =>
                  prev.map((msg) =>
                    msg.id === tutorMessageId
                      ? { ...msg, content: msg.content + (chunk.content ?? "") }
                      : msg
                  )
                );
                break;
              case "hint":
                if (chunk.content) newHints.push(chunk.content);
                break;
              case "suggestion":
                if (chunk.content) newSuggestions.push(chunk.content);
                break;
              case "done":
                setMessages((prev) =>
                  prev.map((msg) =>
                    msg.id === tutorMessageId
                      ? {
                          ...msg,
                          isStreaming: false,
                          hints: newHints,
                          suggestions: newSuggestions,
                        }
                      : msg
                  )
                );
                setHints(newHints);
                setSuggestions(newSuggestions);
                setIsStreaming(false);
                break;
            }
          }
        );
      } catch (error) {
        onError?.(error as Error);
        setIsStreaming(false);
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === tutorMessageId
              ? { ...msg, isStreaming: false, content: "Sorry, I encountered an error." }
              : msg
          )
        );
      }
    },
    [simulationId, manifest, onError]
  );

  /**
   * Clear all messages.
   */
  const clearMessages = useCallback(() => {
    setMessages([]);
    setHints([]);
    setSuggestions([]);
  }, []);

  /**
   * Ask a follow-up question.
   */
  const askFollowUp = useCallback(
    (question: string) => {
      askWithStream(question);
    },
    [askWithStream]
  );

  return {
    // State
    messages,
    isLoading: askMutation.isPending,
    isStreaming,
    error: askMutation.error?.message ?? null,
    hints,
    suggestions,

    // Actions
    ask: askMutation.mutate,
    askAsync: askMutation.mutateAsync,
    askWithStream,
    askFollowUp,
    clearMessages,
    updateKeyframe,
    recordAction,
  };
}

// =============================================================================
// Context Query Hook
// =============================================================================

/**
 * Hook to fetch current simulation tutor context.
 */
export function useSimulationTutorContext(simulationId: string | null) {
  return useQuery({
    queryKey: ["simulation-tutor-context", simulationId],
    queryFn: async () => {
      const response = await fetch(`/api/tutor/simulation/${simulationId}/context`);
      if (!response.ok) {
        throw new Error("Failed to fetch context");
      }
      return response.json();
    },
    enabled: !!simulationId,
    staleTime: 10 * 1000, // 10 seconds
    refetchInterval: 30 * 1000, // 30 seconds
  });
}
