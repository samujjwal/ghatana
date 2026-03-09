/**
 * AI Copilot Hook
 *
 * React hook for AI copilot chat interactions.
 * Manages conversation state and streams responses from the Java AI backend.
 *
 * @module ai/hooks/useAICopilot
 * @doc.type hook
 * @doc.purpose AI copilot conversation management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import {
  AIAgentClientFactory,
  type CopilotInput,
  type CopilotOutput,
} from '../agents';

/**
 * Message in a copilot conversation
 */
export interface CopilotMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: Date;
  suggestions?: string[];
  codeSnippets?: Array<{
    language: string;
    code: string;
    fileName?: string;
  }>;
  actionableItems?: string[];
}

/**
 * Session state for copilot conversations
 */
export interface CopilotSession {
  sessionId: string;
  messages: CopilotMessage[];
  isLoading: boolean;
  error: string | null;
  lastUpdated: Date;
}

/**
 * Copilot hook options
 */
export interface UseAICopilotOptions {
  /**
   * Workspace ID for context
   */
  workspaceId?: string;

  /**
   * Item ID for context (if chatting about a specific item)
   */
  itemId?: string;

  /**
   * API base URL
   * @default 'http://localhost:8080'
   */
  baseUrl?: string;

  /**
   * Session ID for resuming conversations
   */
  sessionId?: string;

  /**
   * System prompt to set the copilot's behavior
   */
  systemPrompt?: string;

  /**
   * Enable auto-save of conversation
   * @default true
   */
  autoSave?: boolean;
}

/**
 * Hook return type
 */
export interface UseAICopilotReturn {
  /**
   * Current session state
   */
  session: CopilotSession;

  /**
   * Send a message to the copilot
   */
  sendMessage: (message: string) => Promise<void>;

  /**
   * Clear the conversation history
   */
  clearHistory: () => void;

  /**
   * Reset the session (start new conversation)
   */
  resetSession: () => void;

  /**
   * Set the active item context
   */
  setItemContext: (itemId: string | null) => void;

  /**
   * Get suggestions for the current context
   */
  getSuggestions: () => Promise<string[]>;

  /**
   * Whether the copilot is ready
   */
  isReady: boolean;

  /**
   * Last error message
   */
  error: string | null;
}

/**
 * Hook for AI copilot interactions
 *
 * @example
 * ```tsx
 * function CopilotChat() {
 *   const { session, sendMessage, clearHistory } = useAICopilot({
 *     workspaceId: 'ws-123',
 *   });
 *
 *   return (
 *     <div>
 *       {session.messages.map(msg => (
 *         <div key={msg.id}>{msg.content}</div>
 *       ))}
 *       <input
 *         onKeyPress={e => e.key === 'Enter' && sendMessage(e.target.value)}
 *       />
 *     </div>
 *   );
 * }
 * ```
 */
export function useAICopilot(
  options: UseAICopilotOptions = {}
): UseAICopilotReturn {
  const {
    workspaceId,
    itemId: initialItemId,
    baseUrl = import.meta.env.DEV
      ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}`
      : '',
    sessionId: initialSessionId,
    systemPrompt,
    autoSave = true,
  } = options;

  const [session, setSession] = useState<CopilotSession>(() => ({
    sessionId: initialSessionId || generateSessionId(),
    messages: systemPrompt
      ? [
          {
            id: generateMessageId(),
            role: 'system',
            content: systemPrompt,
            timestamp: new Date(),
          },
        ]
      : [],
    isLoading: false,
    error: null,
    lastUpdated: new Date(),
  }));

  const [itemId, setItemId] = useState<string | null>(initialItemId || null);
  const [isReady, setIsReady] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clientRef = useRef<ReturnType<
    AIAgentClientFactory['createCopilotClient']
  > | null>(null);

  // Initialize client
  useEffect(() => {
    const factory = new AIAgentClientFactory({ baseUrl });
    clientRef.current = factory.createCopilotClient();
    setIsReady(true);

    return () => {
      clientRef.current = null;
    };
  }, [baseUrl]);

  // Auto-save to localStorage
  useEffect(() => {
    if (autoSave && session.messages.length > 0) {
      const key = `copilot-session-${session.sessionId}`;
      localStorage.setItem(
        key,
        JSON.stringify({
          messages: session.messages,
          lastUpdated: session.lastUpdated,
        })
      );
    }
  }, [session, autoSave]);

  const sendMessage = useCallback(
    async (message: string): Promise<void> => {
      if (!clientRef.current || !message.trim()) {
        return;
      }

      const userMessage: CopilotMessage = {
        id: generateMessageId(),
        role: 'user',
        content: message,
        timestamp: new Date(),
      };

      setSession((prev) => ({
        ...prev,
        messages: [...prev.messages, userMessage],
        isLoading: true,
        error: null,
      }));

      try {
        const input: CopilotInput = {
          sessionId: session.sessionId,
          message,
          workspaceId: workspaceId || undefined,
          itemId: itemId || undefined,
          conversationHistory: session.messages.map((m) => ({
            role: m.role,
            content: m.content,
          })),
        };

        const result = await clientRef.current.execute(input, {
          userId: 'current-user', // NOTE: Get from auth context
          workspaceId,
          traceId: generateTraceId(),
          spanId: generateSpanId(),
        });

        if (result.success && result.data) {
          const assistantMessage: CopilotMessage = {
            id: generateMessageId(),
            role: 'assistant',
            content: result.data.response,
            timestamp: new Date(),
            suggestions: result.data.suggestedActions || [],
            codeSnippets: result.data.codeSnippets || [],
            actionableItems: result.data.actionItems || [],
          };

          setSession((prev) => ({
            ...prev,
            messages: [...prev.messages, assistantMessage],
            isLoading: false,
            lastUpdated: new Date(),
          }));
        } else {
          throw new Error(result.error?.message || 'Unknown error');
        }
      } catch (err) {
        const errorMessage =
          err instanceof Error ? err.message : 'Failed to get response';
        setSession((prev) => ({
          ...prev,
          isLoading: false,
          error: errorMessage,
        }));
        setError(errorMessage);
      }
    },
    [session, workspaceId, itemId]
  );

  const clearHistory = useCallback(() => {
    setSession((prev) => ({
      ...prev,
      messages: prev.messages.filter((m) => m.role === 'system'),
      lastUpdated: new Date(),
    }));
  }, []);

  const resetSession = useCallback(() => {
    const newSessionId = generateSessionId();
    setSession({
      sessionId: newSessionId,
      messages: systemPrompt
        ? [
            {
              id: generateMessageId(),
              role: 'system',
              content: systemPrompt,
              timestamp: new Date(),
            },
          ]
        : [],
      isLoading: false,
      error: null,
      lastUpdated: new Date(),
    });
  }, [systemPrompt]);

  const setItemContext = useCallback((newItemId: string | null) => {
    setItemId(newItemId);
  }, []);

  const getSuggestions = useCallback(async (): Promise<string[]> => {
    if (!clientRef.current) {
      return [];
    }

    try {
      const input: CopilotInput = {
        sessionId: session.sessionId,
        message: 'What should I work on next?',
        workspaceId: workspaceId || undefined,
        itemId: itemId || undefined,
      };

      const result = await clientRef.current.execute(input, {
        userId: 'current-user',
        workspaceId,
        traceId: generateTraceId(),
        spanId: generateSpanId(),
      });

      return result.data?.suggestedActions || [];
    } catch {
      return [];
    }
  }, [session.sessionId, workspaceId, itemId]);

  return {
    session,
    sendMessage,
    clearHistory,
    resetSession,
    setItemContext,
    getSuggestions,
    isReady,
    error,
  };
}

// Helper functions
function generateSessionId(): string {
  return `session-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
}

function generateMessageId(): string {
  return `msg-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
}

function generateTraceId(): string {
  return `trace-${Date.now()}-${Math.random().toString(36).substring(2, 15)}`;
}

function generateSpanId(): string {
  return Math.random().toString(36).substring(2, 18);
}
