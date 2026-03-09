/**
 * useConversationContext Hook
 *
 * Manages conversation context for the AI tutor, including
 * history, learning objectives, and pedagogical state.
 *
 * @doc.type hook
 * @doc.purpose Manage conversation context for AI tutoring
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useRef, useEffect } from "react";

// =============================================================================
// Types
// =============================================================================

export interface ConversationMessage {
  id: string;
  role: "user" | "tutor" | "system";
  content: string;
  timestamp: number;
  metadata?: MessageMetadata;
}

export interface MessageMetadata {
  /** Learning concepts mentioned */
  concepts?: string[];
  /** Misconceptions identified */
  misconceptions?: string[];
  /** Hints provided */
  hints?: string[];
  /** Confidence level (0-1) */
  confidence?: number;
  /** Whether this was a clarification */
  isClarification?: boolean;
  /** Related simulation step */
  stepIndex?: number;
}

export interface LearningObjective {
  id: string;
  description: string;
  status: "not_started" | "in_progress" | "achieved" | "struggling";
  attempts: number;
  lastAttemptAt?: number;
}

export interface MisconceptionRecord {
  id: string;
  concept: string;
  description: string;
  identifiedAt: number;
  addressedAt?: number;
  status: "active" | "addressed" | "recurring";
  occurrences: number;
}

export interface ConversationContext {
  /** Unique session ID */
  sessionId: string;
  /** Simulation ID */
  simulationId: string;
  /** User ID (if authenticated) */
  userId?: string;
  /** Conversation messages */
  messages: ConversationMessage[];
  /** Learning objectives */
  objectives: LearningObjective[];
  /** Identified misconceptions */
  misconceptions: MisconceptionRecord[];
  /** Topics covered */
  topicsCovered: string[];
  /** Current engagement level */
  engagementLevel: "low" | "medium" | "high";
  /** Session start time */
  startedAt: number;
  /** Total interaction count */
  interactionCount: number;
}

export interface UseConversationContextOptions {
  simulationId: string;
  userId?: string;
  initialObjectives?: LearningObjective[];
  maxHistoryLength?: number;
  onContextChange?: (context: ConversationContext) => void;
}

export interface UseConversationContextReturn {
  /** Current conversation context */
  context: ConversationContext;
  /** Add a message to the conversation */
  addMessage: (message: Omit<ConversationMessage, "id" | "timestamp">) => void;
  /** Update a learning objective */
  updateObjective: (id: string, status: LearningObjective["status"]) => void;
  /** Record a misconception */
  recordMisconception: (concept: string, description: string) => void;
  /** Mark a misconception as addressed */
  addressMisconception: (id: string) => void;
  /** Add a covered topic */
  addTopic: (topic: string) => void;
  /** Update engagement level */
  updateEngagement: (level: ConversationContext["engagementLevel"]) => void;
  /** Get context summary for AI prompt */
  getContextSummary: () => string;
  /** Get recent messages for context window */
  getRecentMessages: (count?: number) => ConversationMessage[];
  /** Clear conversation history */
  clearHistory: () => void;
  /** Reset entire context */
  reset: () => void;
}

// =============================================================================
// Helper Functions
// =============================================================================

function generateId(): string {
  return `${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

function generateSessionId(): string {
  return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
}

// =============================================================================
// Hook Implementation
// =============================================================================

export function useConversationContext({
  simulationId,
  userId,
  initialObjectives = [],
  maxHistoryLength = 50,
  onContextChange,
}: UseConversationContextOptions): UseConversationContextReturn {
  const [context, setContext] = useState<ConversationContext>(() => ({
    sessionId: generateSessionId(),
    simulationId,
    userId,
    messages: [],
    objectives: initialObjectives.map((obj) => ({
      ...obj,
      status: "not_started" as const,
      attempts: 0,
    })),
    misconceptions: [],
    topicsCovered: [],
    engagementLevel: "medium",
    startedAt: Date.now(),
    interactionCount: 0,
  }));

  const contextRef = useRef(context);
  contextRef.current = context;

  // Notify on context change
  useEffect(() => {
    if (onContextChange) {
      onContextChange(context);
    }
  }, [context, onContextChange]);

  // Add a message
  const addMessage = useCallback(
    (message: Omit<ConversationMessage, "id" | "timestamp">) => {
      setContext((prev) => {
        const newMessage: ConversationMessage = {
          ...message,
          id: generateId(),
          timestamp: Date.now(),
        };

        // Trim history if needed
        const messages = [...prev.messages, newMessage];
        if (messages.length > maxHistoryLength) {
          messages.splice(0, messages.length - maxHistoryLength);
        }

        // Update engagement based on interaction pattern
        const recentUserMessages = messages
          .filter((m) => m.role === "user")
          .slice(-5);
        let engagementLevel = prev.engagementLevel;
        if (recentUserMessages.length >= 3) {
          const avgInterval =
            recentUserMessages.reduce((sum, m, i, arr) => {
              if (i === 0) return sum;
              return sum + (m.timestamp - arr[i - 1].timestamp);
            }, 0) / (recentUserMessages.length - 1);

          if (avgInterval < 30000) {
            engagementLevel = "high";
          } else if (avgInterval < 120000) {
            engagementLevel = "medium";
          } else {
            engagementLevel = "low";
          }
        }

        return {
          ...prev,
          messages,
          interactionCount: prev.interactionCount + (message.role === "user" ? 1 : 0),
          engagementLevel,
        };
      });
    },
    [maxHistoryLength]
  );

  // Update learning objective
  const updateObjective = useCallback(
    (id: string, status: LearningObjective["status"]) => {
      setContext((prev) => ({
        ...prev,
        objectives: prev.objectives.map((obj) =>
          obj.id === id
            ? {
                ...obj,
                status,
                attempts: obj.attempts + 1,
                lastAttemptAt: Date.now(),
              }
            : obj
        ),
      }));
    },
    []
  );

  // Record misconception
  const recordMisconception = useCallback(
    (concept: string, description: string) => {
      setContext((prev) => {
        // Check if misconception already exists
        const existing = prev.misconceptions.find((m) => m.concept === concept);

        if (existing) {
          return {
            ...prev,
            misconceptions: prev.misconceptions.map((m) =>
              m.concept === concept
                ? {
                    ...m,
                    occurrences: m.occurrences + 1,
                    status: m.status === "addressed" ? "recurring" : m.status,
                  }
                : m
            ),
          };
        }

        return {
          ...prev,
          misconceptions: [
            ...prev.misconceptions,
            {
              id: generateId(),
              concept,
              description,
              identifiedAt: Date.now(),
              status: "active" as const,
              occurrences: 1,
            },
          ],
        };
      });
    },
    []
  );

  // Address misconception
  const addressMisconception = useCallback((id: string) => {
    setContext((prev) => ({
      ...prev,
      misconceptions: prev.misconceptions.map((m) =>
        m.id === id
          ? { ...m, status: "addressed" as const, addressedAt: Date.now() }
          : m
      ),
    }));
  }, []);

  // Add topic
  const addTopic = useCallback((topic: string) => {
    setContext((prev) => {
      if (prev.topicsCovered.includes(topic)) {
        return prev;
      }
      return {
        ...prev,
        topicsCovered: [...prev.topicsCovered, topic],
      };
    });
  }, []);

  // Update engagement
  const updateEngagement = useCallback(
    (level: ConversationContext["engagementLevel"]) => {
      setContext((prev) => ({ ...prev, engagementLevel: level }));
    },
    []
  );

  // Get context summary for AI prompt
  const getContextSummary = useCallback(() => {
    const { objectives, misconceptions, topicsCovered, engagementLevel, interactionCount } =
      contextRef.current;

    const parts: string[] = [];

    // Interaction summary
    parts.push(`Session has ${interactionCount} interactions, engagement: ${engagementLevel}.`);

    // Objectives status
    const achievedCount = objectives.filter((o) => o.status === "achieved").length;
    const strugglingCount = objectives.filter((o) => o.status === "struggling").length;
    if (objectives.length > 0) {
      parts.push(
        `Learning objectives: ${achievedCount}/${objectives.length} achieved, ${strugglingCount} struggling.`
      );
    }

    // Active misconceptions
    const activeMisconceptions = misconceptions.filter((m) => m.status === "active");
    if (activeMisconceptions.length > 0) {
      parts.push(
        `Active misconceptions: ${activeMisconceptions.map((m) => m.concept).join(", ")}.`
      );
    }

    // Topics covered
    if (topicsCovered.length > 0) {
      parts.push(`Topics discussed: ${topicsCovered.join(", ")}.`);
    }

    return parts.join(" ");
  }, []);

  // Get recent messages
  const getRecentMessages = useCallback(
    (count: number = 10) => {
      return contextRef.current.messages.slice(-count);
    },
    []
  );

  // Clear history
  const clearHistory = useCallback(() => {
    setContext((prev) => ({
      ...prev,
      messages: [],
      interactionCount: 0,
    }));
  }, []);

  // Reset context
  const reset = useCallback(() => {
    setContext({
      sessionId: generateSessionId(),
      simulationId,
      userId,
      messages: [],
      objectives: initialObjectives.map((obj) => ({
        ...obj,
        status: "not_started" as const,
        attempts: 0,
      })),
      misconceptions: [],
      topicsCovered: [],
      engagementLevel: "medium",
      startedAt: Date.now(),
      interactionCount: 0,
    });
  }, [simulationId, userId, initialObjectives]);

  // Reset on simulation change
  useEffect(() => {
    reset();
  }, [simulationId, reset]);

  return {
    context,
    addMessage,
    updateObjective,
    recordMisconception,
    addressMisconception,
    addTopic,
    updateEngagement,
    getContextSummary,
    getRecentMessages,
    clearHistory,
    reset,
  };
}

export default useConversationContext;
