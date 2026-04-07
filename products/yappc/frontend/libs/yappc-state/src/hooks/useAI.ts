/**
 * useAI Hook
 *
 * React hooks that expose AI copilot interactions and insight data via
 * the GraphQL API, keeping local Jotai state in sync.
 *
 * @module hooks/useAI
 * @doc.type module
 * @doc.purpose AI copilot and insights data-fetching hooks
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { useCallback } from 'react';

import {
  copilotSessionAtom,
  copilotLoadingAtom,
  copilotErrorAtom,
  aiInsightsAtom,
  aiInsightsLoadingAtom,
  aiPredictionsAtom,
  appendCopilotMessageAtom,
  clearCopilotSessionAtom,
  dismissInsightAtom,
  type CopilotSession,
  type CopilotMessage,
  type AIInsight,
  type AIPrediction,
} from '../store/aiAtoms';

// ============================================================================
// GraphQL documents
// ============================================================================

const COPILOT_SESSION_QUERY = /* GraphQL */ `
  query GetCopilotSession($sessionId: ID!) {
    copilotSession(sessionId: $sessionId) {
      id
      messages {
        id
        role
        content
        createdAt
      }
      context
      createdAt
      updatedAt
    }
  }
`;

const AI_INSIGHTS_QUERY = /* GraphQL */ `
  query GetAIInsights($projectId: ID, $category: String, $severity: String) {
    aiInsights(
      filter: {
        projectId: $projectId
        category: $category
        severity: $severity
      }
    ) {
      id
      type
      category
      severity
      title
      description
      confidence
      actionItems
      projectId
      itemId
      createdAt
    }
  }
`;

const AI_PREDICTIONS_QUERY = /* GraphQL */ `
  query GetAIPredictions($projectId: ID) {
    predictions(filter: { projectId: $projectId }) {
      id
      type
      target
      probability
      timeframe
      description
      factors
      createdAt
    }
  }
`;

const SEND_COPILOT_MESSAGE_MUTATION = /* GraphQL */ `
  mutation SendCopilotMessage(
    $sessionId: ID
    $message: String!
    $projectId: ID
    $context: JSON
  ) {
    sendCopilotMessage(
      sessionId: $sessionId
      message: $message
      projectId: $projectId
      context: $context
    ) {
      sessionId
      userMessage {
        id
        role
        content
        createdAt
      }
      assistantMessage {
        id
        role
        content
        createdAt
      }
    }
  }
`;

// ============================================================================
// GraphQL client helper
// ============================================================================

async function gqlFetch<T>(
  query: string,
  variables?: Record<string, unknown>
): Promise<T> {
  const apiUrl =
    (typeof window !== 'undefined' &&
      (window as Window & { __GRAPHQL_URL__?: string }).__GRAPHQL_URL__) ||
    '/graphql';

  const response = await fetch(apiUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, variables }),
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`GraphQL request failed: ${response.statusText}`);
  }

  const json = (await response.json()) as {
    data?: T;
    errors?: { message: string }[];
  };

  if (json.errors?.length) {
    throw new Error(json.errors.map((e) => e.message).join('; '));
  }

  return json.data as T;
}

// ============================================================================
// useCopilot hook
// ============================================================================

/**
 * Manages the active AI Copilot session — loading, sending messages, and
 * clearing the session. Keeps Jotai atoms in sync with the GraphQL backend.
 *
 * @doc.type function
 * @doc.purpose Copilot session management hook
 * @doc.layer product
 * @doc.pattern Hook
 *
 * @example
 * ```tsx
 * const { session, sendMessage, clearSession, isLoading } = useCopilot();
 * await sendMessage('How can I improve this component?', { projectId });
 * ```
 */
export function useCopilot(): {
  session: CopilotSession | undefined;
  isLoading: boolean;
  error: Error | null;
  sendMessage: (input: { message: string; projectId?: string; context?: Record<string, unknown> }) => Promise<{
    sessionId: string;
    userMessage: CopilotMessage;
    assistantMessage: CopilotMessage;
  }>;
  isSending: boolean;
  loadSession: (sessionId: string) => Promise<void>;
  clearSession: () => void;
} {
  const queryClient = useQueryClient();

  const [session, setSession] = useAtom(copilotSessionAtom);
  const setLoading = useSetAtom(copilotLoadingAtom);
  const [error, setError] = useAtom(copilotErrorAtom);
  const appendMessage = useSetAtom(appendCopilotMessageAtom);
  const clearSession = useSetAtom(clearCopilotSessionAtom);

  // ------------------------------------------------------------------
  // Load an existing session by ID (optional — called when resuming)
  // ------------------------------------------------------------------

  const loadSession = useCallback(
    async (sessionId: string) => {
      setLoading(true);
      setError(null);
      try {
        const data = await gqlFetch<{ copilotSession: CopilotSession }>(
          COPILOT_SESSION_QUERY,
          { sessionId }
        );
        setSession(data.copilotSession);
      } catch (err) {
        setError(err instanceof Error ? err : new Error(String(err)));
      } finally {
        setLoading(false);
      }
    },
    [setLoading, setError, setSession]
  );

  // ------------------------------------------------------------------
  // Send a message
  // ------------------------------------------------------------------

  const sendMessageMutation = useMutation({
    mutationFn: async (input: {
      message: string;
      projectId?: string;
      context?: Record<string, unknown>;
    }) => {
      setLoading(true);
      setError(null);
      try {
        type SendResult = {
          sendCopilotMessage: {
            sessionId: string;
            userMessage: CopilotMessage;
            assistantMessage: CopilotMessage;
          };
        };
        const data = await gqlFetch<SendResult>(SEND_COPILOT_MESSAGE_MUTATION, {
          sessionId: session?.id ?? undefined,
          message: input.message,
          projectId: input.projectId,
          context: input.context,
        });
        return data.sendCopilotMessage;
      } finally {
        setLoading(false);
      }
    },
    onSuccess: (result) => {
      // If we didn't have a session, create a skeleton one
      if (!session) {
        const newSession: CopilotSession = {
          id: result.sessionId,
          messages: [result.userMessage, result.assistantMessage],
          createdAt: result.userMessage.createdAt,
          updatedAt: result.assistantMessage.createdAt,
        };
        setSession(newSession);
      } else {
        appendMessage(result.userMessage);
        appendMessage(result.assistantMessage);
      }
      void queryClient.invalidateQueries({
        queryKey: ['copilotSession', session?.id],
      });
    },
    onError: (err) => {
      setError(err instanceof Error ? err : new Error(String(err)));
    },
  });

  return {
    session,
    isLoading: sendMessageMutation.isPending,
    error,
    sendMessage: sendMessageMutation.mutateAsync,
    isSending: sendMessageMutation.isPending,
    loadSession,
    clearSession,
  };
}

// ============================================================================
// useAIInsights hook
// ============================================================================

/**
 * Fetches and caches AI insights for a given project from the GraphQL API.
 *
 * @doc.type function
 * @doc.purpose AI insights data access hook
 * @doc.layer product
 * @doc.pattern Hook
 *
 * @example
 * ```tsx
 * const { insights, dismissInsight } = useAIInsights('project-id');
 * ```
 */
export function useAIInsights(
  projectId?: string | null
): {
  insights: AIInsight[];
  isLoading: boolean;
  error: Error | null;
  dismissInsight: (insightId: string) => void;
  refetch: () => Promise<AIInsight[] | undefined>;
} {
  const setInsights = useSetAtom(aiInsightsAtom);
  const setLoading = useSetAtom(aiInsightsLoadingAtom);
  const insights = useAtomValue(aiInsightsAtom);
  const dismiss = useSetAtom(dismissInsightAtom);

  const fetchQuery = useQuery({
    queryKey: ['aiInsights', projectId],
    queryFn: async () => {
      setLoading(true);
      try {
        const data = await gqlFetch<{ aiInsights: AIInsight[] }>(
          AI_INSIGHTS_QUERY,
          { projectId }
        );
        setInsights(data.aiInsights);
        return data.aiInsights;
      } finally {
        setLoading(false);
      }
    },
  });

  return {
    insights: insights ?? [],
    isLoading: fetchQuery.isLoading,
    error: fetchQuery.error,
    dismissInsight: dismiss,
    refetch: fetchQuery.refetch,
  };
}

// ============================================================================
// useAIPredictions hook
// ============================================================================

/**
 * Fetches and caches AI predictions for a given project.
 *
 * @doc.type function
 * @doc.purpose AI predictions data access hook
 * @doc.layer product
 * @doc.pattern Hook
 *
 * @example
 * ```tsx
 * const { predictions } = useAIPredictions('project-id');
 * ```
 */
export function useAIPredictions(
  projectId?: string | null
): {
  predictions: AIPrediction[];
  isLoading: boolean;
  error: Error | null;
  refetch: () => Promise<AIPrediction[] | undefined>;
} {
  const setPredictions = useSetAtom(aiPredictionsAtom);
  const predictions = useAtomValue(aiPredictionsAtom);

  const fetchQuery = useQuery({
    queryKey: ['aiPredictions', projectId],
    queryFn: async () => {
      const data = await gqlFetch<{ predictions: AIPrediction[] }>(
        AI_PREDICTIONS_QUERY,
        { projectId }
      );
      setPredictions(data.predictions);
      return data.predictions;
    },
  });

  return {
    predictions: predictions ?? [],
    isLoading: fetchQuery.isLoading,
    error: fetchQuery.error,
    refetch: fetchQuery.refetch,
  };
}
