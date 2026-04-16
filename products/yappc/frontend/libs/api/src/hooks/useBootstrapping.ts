/**
 * Bootstrapping Phase React Hooks
 *
 * @description Custom React hooks for bootstrapping phase operations
 * including session management, AI conversation, and canvas manipulation.
 *
 * @doc.type hooks
 * @doc.purpose Bootstrapping Phase Data Access
 * @doc.layer presentation
 */

import {
  useQuery,
  useLazyQuery,
  useMutation,
  useSubscription,
} from '@apollo/client/react';
import { useApolloClient } from '@apollo/client';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import { useCallback, useMemo } from 'react';

import {
  sessionAtom,
  conversationHistoryAtom,
  canvasNodesAtom,
  canvasEdgesAtom,
  validationReportAtom,
  collaboratorsAtom,
  addConversationTurnAction,
  addCanvasNodeAction,
  updateCanvasNodeAction,
  removeCanvasNodeAction,
  undoCanvasAction as undoAction,
  redoCanvasAction as redoAction,
} from '@yappc/state';
import {
  GET_BOOTSTRAP_SESSION,
  LIST_BOOTSTRAP_SESSIONS,
  GET_CONVERSATION_HISTORY,
  GET_CANVAS_STATE,
  GET_VALIDATION_REPORT,
  GET_AI_SUGGESTIONS,
  GET_SESSION_TEMPLATES,
  CREATE_BOOTSTRAP_SESSION,
  UPDATE_BOOTSTRAP_SESSION,
  DELETE_BOOTSTRAP_SESSION,
  SEND_CONVERSATION_MESSAGE,
  REGENERATE_AI_RESPONSE,
  ADD_CANVAS_NODE,
  UPDATE_CANVAS_NODE,
  DELETE_CANVAS_NODE,
  ADD_CANVAS_EDGE,
  DELETE_CANVAS_EDGE,
  UNDO_CANVAS_ACTION,
  REDO_CANVAS_ACTION,
  VALIDATE_SESSION,
  AUTO_FIX_VALIDATION_ISSUES,
  APPLY_SESSION_TEMPLATE,
  EXPORT_SESSION,
  IMPORT_SESSION,
  INVITE_COLLABORATOR,
  REMOVE_COLLABORATOR,
  UPDATE_COLLABORATOR_ROLE,
  FINALIZE_BOOTSTRAP,
  SUBSCRIBE_TO_SESSION_UPDATES,
  SUBSCRIBE_TO_CONVERSATION,
  SUBSCRIBE_TO_CANVAS_CHANGES,
  SUBSCRIBE_TO_COLLABORATOR_PRESENCE,
  SUBSCRIBE_TO_AI_THINKING,
  SUBSCRIBE_TO_VALIDATION_UPDATES,
  type CreateBootstrapSessionInput,
  type UpdateBootstrapSessionInput,
  type ConversationMessageInput,
  type AddCanvasNodeInput,
  type UpdateCanvasNodeInput,
  type AddCanvasEdgeInput,
  type InviteCollaboratorInput,
  type ValidationOptions,
  type AISuggestionContext,
  type RegenerateOptions,
  type BootstrapSessionFilter,
} from '@yappc/core/api';

// =============================================================================
// Session Hooks
// =============================================================================

/**
 * Hook for fetching a single bootstrap session
 */
export function useBootstrapSession(sessionId: string | undefined) {
  const [, setSessionValue] = useAtom(sessionAtom as never);
  const setSession = setSessionValue as (session: unknown) => void;

  type BootstrapSessionQueryResult = {
    bootstrapSession?: unknown;
  };

  type BootstrapSessionQueryState = {
    data?: BootstrapSessionQueryResult;
    loading: boolean;
    error: unknown;
    refetch: () => Promise<unknown>;
  };

  const runBootstrapSessionQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => BootstrapSessionQueryState;

  const { data, loading, error, refetch } = runBootstrapSessionQuery(
    GET_BOOTSTRAP_SESSION,
    {
      variables: { id: sessionId },
      skip: !sessionId,
      onCompleted: (queryData: BootstrapSessionQueryResult) => {
        if (queryData?.bootstrapSession) {
          setSession(queryData.bootstrapSession);
        }
      },
    }
  );

  return {
    session: data?.bootstrapSession,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook for listing bootstrap sessions with pagination
 */
export function useBootstrapSessions(options?: {
  filter?: BootstrapSessionFilter;
  pageSize?: number;
}) {
  const { filter, pageSize = 20 } = options ?? {};

  type BootstrapSessionEdge = {
    node?: unknown;
  };

  type BootstrapSessionsListResult = {
    bootstrapSessions?: {
      edges?: BootstrapSessionEdge[];
      pageInfo?: {
        hasNextPage?: boolean;
        endCursor?: string | null;
      };
    };
  };

  type BootstrapSessionsQueryState = {
    data?: BootstrapSessionsListResult;
    loading: boolean;
    error: unknown;
    fetchMore: (options: {
      variables: {
        pagination: {
          first: number;
          after?: string | null;
        };
      };
    }) => Promise<unknown>;
    refetch: () => Promise<unknown>;
  };

  const runBootstrapSessionsQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => BootstrapSessionsQueryState;

  const { data, loading, error, fetchMore, refetch } =
    runBootstrapSessionsQuery(LIST_BOOTSTRAP_SESSIONS, {
      variables: {
        filter,
        pagination: { first: pageSize },
      },
      notifyOnNetworkStatusChange: true,
    });

  const loadMore = useCallback(() => {
    if (!data?.bootstrapSessions?.pageInfo?.hasNextPage) return;

    void fetchMore({
      variables: {
        pagination: {
          first: pageSize,
          after: data.bootstrapSessions.pageInfo.endCursor,
        },
      },
    });
  }, [data, fetchMore, pageSize]);

  return {
    sessions: data?.bootstrapSessions?.edges?.map((edge) => edge.node) ?? [],
    pageInfo: data?.bootstrapSessions?.pageInfo,
    loading,
    error,
    loadMore,
    refetch,
  };
}

/**
 * Hook for creating a new bootstrap session
 */
export function useCreateBootstrapSession() {
  const setSessionValue = useSetAtom(sessionAtom as never);
  const setSession = setSessionValue as (session: unknown) => void;

  type CreateBootstrapSessionMutationResult = {
    createBootstrapSession?: {
      session?: unknown;
    };
  };

  type CreateBootstrapSessionMutationState = {
    loading: boolean;
    error: unknown;
  };

  type BootstrapSessionsCache = {
    modify: (config: {
      fields: {
        bootstrapSessions: (existing?: { edges: Array<unknown> }) => {
          edges: Array<unknown>;
        };
      };
    }) => void;
  };

  const runCreateBootstrapSessionMutation = useMutation as unknown as (
    mutation: unknown,
    options: {
      onCompleted: (data?: CreateBootstrapSessionMutationResult) => void;
      update: (
        cache: BootstrapSessionsCache,
        result: { data?: CreateBootstrapSessionMutationResult }
      ) => void;
    }
  ) => [
    (args: {
      variables: { input: CreateBootstrapSessionInput };
    }) => Promise<{ data?: CreateBootstrapSessionMutationResult }>,
    CreateBootstrapSessionMutationState,
  ];

  const [createSession, { loading, error }] = runCreateBootstrapSessionMutation(
    CREATE_BOOTSTRAP_SESSION,
    {
      onCompleted: (data) => {
        if (data?.createBootstrapSession?.session) {
          setSession(data.createBootstrapSession.session);
        }
      },
      update: (cache, { data }) => {
        // Add to sessions list cache
        cache.modify({
          fields: {
            bootstrapSessions: (existing = { edges: [] }) => ({
              ...existing,
              edges: [
                {
                  node: data?.createBootstrapSession?.session,
                  __typename: 'BootstrapSessionEdge',
                },
                ...existing.edges,
              ],
            }),
          },
        });
      },
    }
  );

  const create = useCallback(
    async (input: CreateBootstrapSessionInput) => {
      const result = await createSession({ variables: { input } });
      return result.data?.createBootstrapSession;
    },
    [createSession]
  );

  return { create, loading, error };
}

/**
 * Hook for updating a bootstrap session
 */
export function useUpdateBootstrapSession() {
  type UpdateBootstrapSessionMutationResult = {
    updateBootstrapSession?: unknown;
  };

  type UpdateBootstrapSessionMutationState = {
    loading: boolean;
    error: unknown;
  };

  const runUpdateBootstrapSessionMutation = useMutation as unknown as (
    mutation: unknown
  ) => [
    (args: {
      variables: { id: string; input: UpdateBootstrapSessionInput };
    }) => Promise<{ data?: UpdateBootstrapSessionMutationResult }>,
    UpdateBootstrapSessionMutationState,
  ];

  const [updateSession, { loading, error }] = runUpdateBootstrapSessionMutation(
    UPDATE_BOOTSTRAP_SESSION
  );

  const update = useCallback(
    async (id: string, input: UpdateBootstrapSessionInput) => {
      const result = await updateSession({ variables: { id, input } });
      return result.data?.updateBootstrapSession;
    },
    [updateSession]
  );

  return { update, loading, error };
}

/**
 * Hook for deleting a bootstrap session
 */
export function useDeleteBootstrapSession() {
  type DeleteBootstrapSessionMutationResult = {
    deleteBootstrapSession?: {
      success?: boolean;
    };
  };

  type DeleteBootstrapSessionMutationState = {
    loading: boolean;
    error: unknown;
  };

  type DeleteBootstrapSessionCache = {
    evict: (options: { id: string }) => void;
    identify: (value: { __typename: string; id: string }) => string;
    gc: () => void;
  };

  const runDeleteBootstrapSessionMutation = useMutation as unknown as (
    mutation: unknown,
    options: {
      update: (
        cache: DeleteBootstrapSessionCache,
        result: { data?: DeleteBootstrapSessionMutationResult },
        context: { variables?: { id?: string } }
      ) => void;
    }
  ) => [
    (args: {
      variables: { id: string };
    }) => Promise<{ data?: DeleteBootstrapSessionMutationResult }>,
    DeleteBootstrapSessionMutationState,
  ];

  const [deleteSession, { loading, error }] = runDeleteBootstrapSessionMutation(
    DELETE_BOOTSTRAP_SESSION,
    {
      update: (cache, { data }, { variables }) => {
        if (data?.deleteBootstrapSession?.success && variables?.id) {
          cache.evict({
            id: cache.identify({
              __typename: 'BootstrapSession',
              id: variables.id,
            }),
          });
          cache.gc();
        }
      },
    }
  );

  const remove = useCallback(
    async (id: string) => {
      const result = await deleteSession({ variables: { id } });
      return result.data?.deleteBootstrapSession?.success;
    },
    [deleteSession]
  );

  return { remove, loading, error };
}

// =============================================================================
// Conversation Hooks
// =============================================================================

/**
 * Hook for managing conversation with AI
 */
export function useConversation(sessionId: string) {
  type ConversationTurn = {
    id: string;
    [key: string]: unknown;
  };

  const [conversationHistory, setConversationHistoryValue] = useAtom(
    conversationHistoryAtom as never
  );
  const setConversationHistory = setConversationHistoryValue as (
    value: unknown[] | ((prev: ConversationTurn[]) => ConversationTurn[])
  ) => void;
  const addTurnValue = useSetAtom(addConversationTurnAction as never);
  const addTurn = addTurnValue as (turn: unknown) => void;

  type ConversationEdge = {
    node?: unknown;
  };

  type ConversationHistoryQueryResult = {
    conversationHistory?: {
      edges?: ConversationEdge[];
    };
  };

  type ConversationHistoryQueryState = {
    data?: ConversationHistoryQueryResult;
    loading: boolean;
  };

  type SendConversationMessageResult = {
    sendConversationMessage?: {
      userMessage?: unknown;
      aiResponse?: unknown;
    };
  };

  type RegenerateConversationResult = {
    regenerateAIResponse?: {
      newResponse?: ConversationTurn;
    };
  };

  type MutationState = {
    loading: boolean;
  };

  type ConversationUpdatePayload = {
    conversationUpdated?: {
      message?: unknown;
    };
  };

  type ConversationSubscriptionState = {
    data?: ConversationUpdatePayload;
  };

  const runConversationHistoryQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => ConversationHistoryQueryState;

  const runConversationMutation = useMutation as unknown as (
    mutation: unknown
  ) => [
    (args: {
      variables: Record<string, unknown>;
    }) => Promise<{ data?: SendConversationMessageResult }>,
    MutationState,
  ];

  const runRegenerateMutation = useMutation as unknown as (
    mutation: unknown
  ) => [
    (args: {
      variables: Record<string, unknown>;
    }) => Promise<{ data?: RegenerateConversationResult }>,
    MutationState,
  ];

  const runConversationSubscription = useSubscription as unknown as (
    subscription: unknown,
    options: unknown
  ) => ConversationSubscriptionState;

  const { data, loading: historyLoading } = runConversationHistoryQuery(
    GET_CONVERSATION_HISTORY,
    {
      variables: { sessionId, pagination: { first: 100 } },
      skip: !sessionId,
      onCompleted: (queryData: ConversationHistoryQueryResult) => {
        const turns =
          queryData?.conversationHistory?.edges?.map((edge) => edge.node) ?? [];
        setConversationHistory(turns);
      },
    }
  );

  const [sendMessage, { loading: sending }] = runConversationMutation(
    SEND_CONVERSATION_MESSAGE
  );
  const [regenerateResponse, { loading: regenerating }] = runRegenerateMutation(
    REGENERATE_AI_RESPONSE
  );

  const send = useCallback(
    async (input: ConversationMessageInput) => {
      const result = await sendMessage({ variables: { sessionId, input } });
      const response = result.data?.sendConversationMessage;

      if (response) {
        if (response.userMessage) {
          addTurn(response.userMessage);
        }
        if (response.aiResponse) {
          addTurn(response.aiResponse);
        }
      }

      return response;
    },
    [sessionId, sendMessage, addTurn]
  );

  const regenerate = useCallback(
    async (messageId: string, options?: RegenerateOptions) => {
      const result = await regenerateResponse({
        variables: { sessionId, messageId, options },
      });
      const response = result.data?.regenerateAIResponse;

      if (response?.newResponse) {
        // Update the conversation history with the new response
        setConversationHistory((prev) =>
          prev.map((turn) =>
            turn.id === messageId ? response.newResponse : turn
          )
        );
      }

      return response;
    },
    [sessionId, regenerateResponse, setConversationHistory]
  );

  // Subscribe to conversation updates
  runConversationSubscription(SUBSCRIBE_TO_CONVERSATION, {
    variables: { sessionId },
    skip: !sessionId,
    onData: ({ data }: { data: ConversationSubscriptionState }) => {
      const update = data.data?.conversationUpdated;
      if (update?.message) {
        addTurn(update.message);
      }
    },
  });

  return {
    history: conversationHistory,
    loading: historyLoading,
    sending,
    regenerating,
    send,
    regenerate,
  };
}

/**
 * Hook for AI thinking indicator subscription
 */
export function useAIThinking(sessionId: string) {
  type AIThinkingPayload = {
    aiThinking?: {
      isThinking?: boolean;
      stage?: unknown;
      progress?: unknown;
      currentTask?: unknown;
      estimatedTimeRemaining?: unknown;
    };
  };

  type AIThinkingSubscriptionState = {
    data?: AIThinkingPayload;
  };

  const runAIThinkingSubscription = useSubscription as unknown as (
    subscription: unknown,
    options: unknown
  ) => AIThinkingSubscriptionState;

  const { data } = runAIThinkingSubscription(SUBSCRIBE_TO_AI_THINKING, {
    variables: { sessionId },
    skip: !sessionId,
  });

  return {
    isThinking: data?.aiThinking?.isThinking ?? false,
    stage: data?.aiThinking?.stage,
    progress: data?.aiThinking?.progress,
    currentTask: data?.aiThinking?.currentTask,
    estimatedTimeRemaining: data?.aiThinking?.estimatedTimeRemaining,
  };
}

// =============================================================================
// Canvas Hooks
// =============================================================================

/**
 * Hook for managing canvas state
 */
export function useCanvas(sessionId: string) {
  type CanvasNode = {
    id: string;
    [key: string]: unknown;
  };

  type CanvasEdge = {
    id: string;
    [key: string]: unknown;
  };

  type CanvasStateQueryResult = {
    canvasState?: {
      nodes?: CanvasNode[];
      edges?: CanvasEdge[];
    };
  };

  type CanvasQueryState = {
    loading: boolean;
  };

  type CanvasMutationState = {
    loading?: boolean;
  };

  type AddNodeMutationResult = {
    addCanvasNode?: {
      node?: CanvasNode;
    };
  };

  type UpdateNodeMutationResult = {
    updateCanvasNode?: {
      node?: CanvasNode;
    };
  };

  type DeleteNodeMutationResult = {
    deleteCanvasNode?: {
      success?: boolean;
    };
  };

  type AddEdgeMutationResult = {
    addCanvasEdge?: {
      edge?: CanvasEdge;
    };
  };

  type DeleteEdgeMutationResult = {
    deleteCanvasEdge?: {
      success?: boolean;
    };
  };

  type CanvasActionMutationResult = {
    success?: boolean;
  };

  type UndoMutationResult = {
    undoCanvasAction?: CanvasActionMutationResult;
  };

  type RedoMutationResult = {
    redoCanvasAction?: CanvasActionMutationResult;
  };

  type CanvasChangedPayload = {
    canvasChanged?: {
      type?: string;
      node?: CanvasNode;
      edge?: CanvasEdge;
    };
  };

  type CanvasSubscriptionState = {
    data?: CanvasChangedPayload;
  };

  const nodes = useAtomValue(canvasNodesAtom as never);
  const setNodesValue = useSetAtom(canvasNodesAtom as never);
  const setNodes = setNodesValue as (
    value: CanvasNode[] | ((prev: CanvasNode[]) => CanvasNode[])
  ) => void;

  const edges = useAtomValue(canvasEdgesAtom as never);
  const setEdgesValue = useSetAtom(canvasEdgesAtom as never);
  const setEdges = setEdgesValue as (
    value: CanvasEdge[] | ((prev: CanvasEdge[]) => CanvasEdge[])
  ) => void;

  const addNodeValue = useSetAtom(addCanvasNodeAction as never);
  const addNode = addNodeValue as (node: CanvasNode) => void;
  const updateNodeValue = useSetAtom(updateCanvasNodeAction as never);
  const updateNode = updateNodeValue as (node: CanvasNode) => void;
  const removeNodeValue = useSetAtom(removeCanvasNodeAction as never);
  const removeNode = removeNodeValue as (nodeId: string) => void;
  const undoValue = useSetAtom(undoAction as never);
  const undo = undoValue as () => void;
  const redoValue = useSetAtom(redoAction as never);
  const redo = redoValue as () => void;

  const runCanvasQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => CanvasQueryState;

  const runCanvasMutation = useMutation as unknown as (
    mutation: unknown
  ) => [
    (args: {
      variables: Record<string, unknown>;
    }) => Promise<{ data?: unknown }>,
    CanvasMutationState,
  ];

  const runCanvasSubscription = useSubscription as unknown as (
    subscription: unknown,
    options: unknown
  ) => CanvasSubscriptionState;

  const { loading } = runCanvasQuery(GET_CANVAS_STATE, {
    variables: { sessionId },
    skip: !sessionId,
    onCompleted: (queryData: CanvasStateQueryResult) => {
      if (queryData?.canvasState) {
        setNodes(queryData.canvasState.nodes ?? []);
        setEdges(queryData.canvasState.edges ?? []);
      }
    },
  });

  const [addNodeMutationValue] = runCanvasMutation(ADD_CANVAS_NODE);
  const addNodeMutation = addNodeMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: AddNodeMutationResult }>;
  const [updateNodeMutationValue] = runCanvasMutation(UPDATE_CANVAS_NODE);
  const updateNodeMutation = updateNodeMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: UpdateNodeMutationResult }>;
  const [deleteNodeMutationValue] = runCanvasMutation(DELETE_CANVAS_NODE);
  const deleteNodeMutation = deleteNodeMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: DeleteNodeMutationResult }>;
  const [addEdgeMutationValue] = runCanvasMutation(ADD_CANVAS_EDGE);
  const addEdgeMutation = addEdgeMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: AddEdgeMutationResult }>;
  const [deleteEdgeMutationValue] = runCanvasMutation(DELETE_CANVAS_EDGE);
  const deleteEdgeMutation = deleteEdgeMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: DeleteEdgeMutationResult }>;
  const [undoMutationValue] = runCanvasMutation(UNDO_CANVAS_ACTION);
  const undoMutation = undoMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: UndoMutationResult }>;
  const [redoMutationValue] = runCanvasMutation(REDO_CANVAS_ACTION);
  const redoMutation = redoMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: RedoMutationResult }>;

  const addCanvasNode = useCallback(
    async (input: AddCanvasNodeInput) => {
      const result = await addNodeMutation({ variables: { sessionId, input } });
      const newNode = result.data?.addCanvasNode?.node;
      if (newNode) {
        addNode(newNode);
      }
      return newNode;
    },
    [sessionId, addNodeMutation, addNode]
  );

  const updateCanvasNode = useCallback(
    async (nodeId: string, input: UpdateCanvasNodeInput) => {
      const result = await updateNodeMutation({
        variables: { sessionId, nodeId, input },
      });
      const updatedNode = result.data?.updateCanvasNode?.node;
      if (updatedNode) {
        updateNode(updatedNode);
      }
      return updatedNode;
    },
    [sessionId, updateNodeMutation, updateNode]
  );

  const deleteCanvasNode = useCallback(
    async (nodeId: string) => {
      const result = await deleteNodeMutation({
        variables: { sessionId, nodeId },
      });
      if (result.data?.deleteCanvasNode?.success) {
        removeNode(nodeId);
      }
      return result.data?.deleteCanvasNode?.success;
    },
    [sessionId, deleteNodeMutation, removeNode]
  );

  const addCanvasEdge = useCallback(
    async (input: AddCanvasEdgeInput) => {
      const result = await addEdgeMutation({ variables: { sessionId, input } });
      const newEdge = result.data?.addCanvasEdge?.edge;
      if (newEdge) {
        setEdges((prev) => [...prev, newEdge]);
      }
      return newEdge;
    },
    [sessionId, addEdgeMutation, setEdges]
  );

  const deleteCanvasEdge = useCallback(
    async (edgeId: string) => {
      const result = await deleteEdgeMutation({
        variables: { sessionId, edgeId },
      });
      if (result.data?.deleteCanvasEdge?.success) {
        setEdges((prev) => prev.filter((e) => e.id !== edgeId));
      }
      return result.data?.deleteCanvasEdge?.success;
    },
    [sessionId, deleteEdgeMutation, setEdges]
  );

  const undoCanvas = useCallback(async () => {
    const result = await undoMutation({ variables: { sessionId } });
    if (result.data?.undoCanvasAction?.success) {
      undo();
    }
    return result.data?.undoCanvasAction;
  }, [sessionId, undoMutation, undo]);

  const redoCanvas = useCallback(async () => {
    const result = await redoMutation({ variables: { sessionId } });
    if (result.data?.redoCanvasAction?.success) {
      redo();
    }
    return result.data?.redoCanvasAction;
  }, [sessionId, redoMutation, redo]);

  // Subscribe to canvas changes
  runCanvasSubscription(SUBSCRIBE_TO_CANVAS_CHANGES, {
    variables: { sessionId },
    skip: !sessionId,
    onData: ({ data }: { data: CanvasSubscriptionState }) => {
      const change = data.data?.canvasChanged;
      if (!change) return;

      switch (change.type) {
        case 'NODE_ADDED':
          if (change.node) addNode(change.node);
          break;
        case 'NODE_UPDATED':
          if (change.node) updateNode(change.node);
          break;
        case 'NODE_DELETED':
          if (change.node?.id) removeNode(change.node.id);
          break;
        case 'EDGE_ADDED':
          if (change.edge) setEdges((prev) => [...prev, change.edge]);
          break;
        case 'EDGE_DELETED':
          if (change.edge?.id)
            setEdges((prev) => prev.filter((e) => e.id !== change.edge.id));
          break;
      }
    },
  });

  return {
    nodes,
    edges,
    loading,
    addNode: addCanvasNode,
    updateNode: updateCanvasNode,
    deleteNode: deleteCanvasNode,
    addEdge: addCanvasEdge,
    deleteEdge: deleteCanvasEdge,
    undo: undoCanvas,
    redo: redoCanvas,
    setNodes,
    setEdges,
  };
}

// =============================================================================
// Validation Hooks
// =============================================================================

/**
 * Hook for session validation
 */
export function useValidation(sessionId: string) {
  type ValidationReport = {
    [key: string]: unknown;
  };

  type ValidationQueryResult = {
    validationReport?: ValidationReport;
  };

  type ValidationQueryState = {
    loading: boolean;
    refetch: () => Promise<unknown>;
  };

  type ValidateMutationResult = {
    validateSession?: ValidationReport;
  };

  type AutoFixMutationResult = {
    autoFixValidationIssues?: unknown;
  };

  type ValidationMutationState = {
    loading: boolean;
  };

  type ValidationSubscriptionState = {
    data?: {
      validationUpdated?: ValidationReport;
    };
  };

  const validationReport = useAtomValue(validationReportAtom as never);
  const setValidationReportValue = useSetAtom(validationReportAtom as never);
  const setValidationReport = setValidationReportValue as (
    report: ValidationReport
  ) => void;

  const runValidationQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => ValidationQueryState;

  const runValidationMutation = useMutation as unknown as (
    mutation: unknown
  ) => [
    (args: {
      variables: Record<string, unknown>;
    }) => Promise<{ data?: unknown }>,
    ValidationMutationState,
  ];

  const runValidationSubscription = useSubscription as unknown as (
    subscription: unknown,
    options: unknown
  ) => ValidationSubscriptionState;

  const { loading: reportLoading, refetch } = runValidationQuery(
    GET_VALIDATION_REPORT,
    {
      variables: { sessionId },
      skip: !sessionId,
      onCompleted: (queryData: ValidationQueryResult) => {
        if (queryData?.validationReport) {
          setValidationReport(queryData.validationReport);
        }
      },
    }
  );

  const [validateMutationValue, { loading: validating }] =
    runValidationMutation(VALIDATE_SESSION);
  const validateMutation = validateMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: ValidateMutationResult }>;
  const [autoFixMutationValue, { loading: fixing }] = runValidationMutation(
    AUTO_FIX_VALIDATION_ISSUES
  );
  const autoFixMutation = autoFixMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: AutoFixMutationResult }>;

  const validate = useCallback(
    async (options?: ValidationOptions) => {
      const result = await validateMutation({
        variables: { sessionId, options },
      });
      const report = result.data?.validateSession;
      if (report) {
        setValidationReport(report);
      }
      return report;
    },
    [sessionId, validateMutation, setValidationReport]
  );

  const autoFix = useCallback(
    async (issueIds: string[]) => {
      const result = await autoFixMutation({
        variables: { sessionId, issueIds },
      });
      const fixResult = result.data?.autoFixValidationIssues;
      if (fixResult) {
        // Revalidate after fixing
        await validate();
      }
      return fixResult;
    },
    [sessionId, autoFixMutation, validate]
  );

  // Subscribe to validation updates
  runValidationSubscription(SUBSCRIBE_TO_VALIDATION_UPDATES, {
    variables: { sessionId },
    skip: !sessionId,
    onData: ({ data }: { data: ValidationSubscriptionState }) => {
      if (data.data?.validationUpdated) {
        setValidationReport(data.data.validationUpdated);
      }
    },
  });

  return {
    report: validationReport,
    loading: reportLoading,
    validating,
    fixing,
    validate,
    autoFix,
    refetch,
  };
}

// =============================================================================
// AI Suggestions Hooks
// =============================================================================

/**
 * Hook for fetching AI suggestions
 */
export function useAISuggestions(
  sessionId: string,
  context: AISuggestionContext
) {
  type AISuggestion = { [key: string]: unknown };
  type AISuggestionsQueryResult = { aiSuggestions?: AISuggestion[] };
  type AISuggestionsQueryState = {
    data?: AISuggestionsQueryResult;
    loading: boolean;
    error?: unknown;
  };
  const runAISuggestionsLazyQuery = useLazyQuery as unknown as (
    query: unknown
  ) => [
    (options: unknown) => Promise<{ data?: AISuggestionsQueryResult }>,
    AISuggestionsQueryState,
  ];

  const [getSuggestionsValue, { data, loading, error }] =
    runAISuggestionsLazyQuery(GET_AI_SUGGESTIONS);
  const getSuggestions = getSuggestionsValue as (
    options: unknown
  ) => Promise<{ data?: AISuggestionsQueryResult }>;

  const fetchSuggestions = useCallback(() => {
    return getSuggestions({ variables: { sessionId, context } });
  }, [sessionId, context, getSuggestions]);

  return {
    suggestions: (data?.aiSuggestions as AISuggestion[]) ?? [],
    loading,
    error,
    fetch: fetchSuggestions,
  };
}

// =============================================================================
// Template Hooks
// =============================================================================

/**
 * Hook for session templates
 */
export function useSessionTemplates(options?: {
  category?: string;
  search?: string;
}) {
  type SessionTemplate = { [key: string]: unknown };
  type SessionTemplatesQueryResult = { sessionTemplates?: SessionTemplate[] };
  type SessionTemplatesQueryState = {
    data?: SessionTemplatesQueryResult;
    loading: boolean;
    error?: unknown;
    refetch: () => Promise<unknown>;
  };
  type ApplyTemplateMutationResult = { applySessionTemplate?: unknown };
  type TemplateMutationState = { loading: boolean };
  const runSessionTemplatesQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => SessionTemplatesQueryState;
  const runApplyTemplateMutation = useMutation as unknown as (
    mutation: unknown
  ) => [
    (args: {
      variables: Record<string, unknown>;
    }) => Promise<{ data?: ApplyTemplateMutationResult }>,
    TemplateMutationState,
  ];

  const { data, loading, error, refetch } = runSessionTemplatesQuery(
    GET_SESSION_TEMPLATES,
    {
      variables: options,
    }
  );

  const [applyTemplateValue, { loading: applying }] = runApplyTemplateMutation(
    APPLY_SESSION_TEMPLATE
  );
  const applyTemplate = applyTemplateValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: ApplyTemplateMutationResult }>;

  const apply = useCallback(
    async (sessionId: string, templateId: string) => {
      const result = await applyTemplate({
        variables: { sessionId, templateId },
      });
      return result.data?.applySessionTemplate;
    },
    [applyTemplate]
  );

  return {
    templates: ((data?.sessionTemplates as unknown as any[]) ??
      []) as unknown[],
    loading,
    error,
    refetch,
    apply,
    applying,
  };
}

// =============================================================================
// Export/Import Hooks
// =============================================================================

/**
 * Hook for exporting sessions
 */
export function useExportSession() {
  type ExportSessionMutationResult = { exportSession?: unknown };
  type ExportMutationState = { loading: boolean; error?: unknown };
  const runExportMutation = useMutation as unknown as (
    mutation: unknown
  ) => [
    (args: {
      variables: Record<string, unknown>;
    }) => Promise<{ data?: ExportSessionMutationResult }>,
    ExportMutationState,
  ];
  const [exportMutationValue, { loading, error }] =
    runExportMutation(EXPORT_SESSION);
  const exportMutation = exportMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: ExportSessionMutationResult }>;

  const exportSession = useCallback(
    async (sessionId: string, format: 'json' | 'yaml' | 'pdf' | 'markdown') => {
      const result = await exportMutation({ variables: { sessionId, format } });
      return result.data?.exportSession;
    },
    [exportMutation]
  );

  return { export: exportSession, loading, error };
}

/**
 * Hook for importing sessions
 */
export function useImportSession() {
  type ImportSessionMutationResult = { importSession?: unknown };
  type ImportMutationState = { loading: boolean; error?: unknown };
  const runImportMutation = useMutation as unknown as (
    mutation: unknown
  ) => [
    (args: {
      variables: Record<string, unknown>;
    }) => Promise<{ data?: ImportSessionMutationResult }>,
    ImportMutationState,
  ];
  const [importMutationValue, { loading, error }] =
    runImportMutation(IMPORT_SESSION);
  const importMutation = importMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: ImportSessionMutationResult }>;

  const importSession = useCallback(
    async (
      file: File,
      format: 'json' | 'yaml' | 'yappc',
      conflictResolution?: string
    ) => {
      const result = await importMutation({
        variables: {
          input: { file, format, conflictResolution },
        },
      });
      return result.data?.importSession;
    },
    [importMutation]
  );

  return { import: importSession, loading, error };
}

// =============================================================================
// Collaboration Hooks
// =============================================================================

/**
 * Hook for managing collaborators
 */
export function useCollaborators(sessionId: string) {
  type Collaborator = { id: string; [key: string]: unknown };
  type InviteCollaboratorMutationResult = {
    inviteCollaborator?: { collaborator?: Collaborator };
  };
  type RemoveCollaboratorMutationResult = {
    removeCollaborator?: { success?: boolean };
  };
  type UpdateRoleMutationResult = { updateCollaboratorRole?: unknown };
  type CollaboratorMutationState = { loading: boolean };
  const runCollaboratorMutation = useMutation as unknown as (
    mutation: unknown
  ) => [
    (args: {
      variables: Record<string, unknown>;
    }) => Promise<{ data?: unknown }>,
    CollaboratorMutationState,
  ];
  const collaboratorsValue = useAtomValue(collaboratorsAtom as never);
  const collaborators = collaboratorsValue as never as Collaborator[];
  const setCollaboratorsValue = useSetAtom(collaboratorsAtom as never);
  const setCollaborators = setCollaboratorsValue as (
    value: Collaborator[] | ((prev: Collaborator[]) => Collaborator[])
  ) => void;
  const [inviteMutationValue, { loading: inviting }] =
    runCollaboratorMutation(INVITE_COLLABORATOR);
  const inviteMutation = inviteMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: InviteCollaboratorMutationResult }>;
  const [removeMutationValue, { loading: removing }] =
    runCollaboratorMutation(REMOVE_COLLABORATOR);
  const removeMutation = removeMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: RemoveCollaboratorMutationResult }>;
  const [updateRoleMutationValue, { loading: updatingRole }] =
    runCollaboratorMutation(UPDATE_COLLABORATOR_ROLE);
  const updateRoleMutation = updateRoleMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: UpdateRoleMutationResult }>;

  const invite = useCallback(
    async (input: InviteCollaboratorInput) => {
      const result = await inviteMutation({ variables: { sessionId, input } });
      const newCollaborator = result.data?.inviteCollaborator?.collaborator;
      if (newCollaborator) {
        setCollaborators((prev) => [...prev, newCollaborator]);
      }
      return result.data?.inviteCollaborator;
    },
    [sessionId, inviteMutation, setCollaborators]
  );

  const remove = useCallback(
    async (collaboratorId: string) => {
      const result = await removeMutation({
        variables: { sessionId, collaboratorId },
      });
      if (result.data?.removeCollaborator?.success) {
        setCollaborators((prev) => prev.filter((c) => c.id !== collaboratorId));
      }
      return result.data?.removeCollaborator?.success;
    },
    [sessionId, removeMutation, setCollaborators]
  );

  const updateRole = useCallback(
    async (collaboratorId: string, role: 'viewer' | 'editor' | 'admin') => {
      const result = await updateRoleMutation({
        variables: { sessionId, collaboratorId, role },
      });
      const updated = result.data?.updateCollaboratorRole?.collaborator as
        | Collaborator
        | undefined;
      if (updated) {
        setCollaborators((prev) =>
          prev.map((c) => (c.id === collaboratorId ? { ...c, role } : c))
        );
      }
      return updated;
    },
    [sessionId, updateRoleMutation, setCollaborators]
  );

  // Subscribe to collaborator presence
  type CollaboratorPresencePayload = {
    collaboratorPresence?: {
      userId?: string;
      status?: unknown;
      cursor?: unknown;
    };
  };
  type CollaboratorPresenceSubscriptionState = {
    data?: CollaboratorPresencePayload;
  };
  const runCollaboratorPresenceSubscription = useSubscription as unknown as (
    subscription: unknown,
    options: unknown
  ) => CollaboratorPresenceSubscriptionState;

  runCollaboratorPresenceSubscription(SUBSCRIBE_TO_COLLABORATOR_PRESENCE, {
    variables: { sessionId },
    skip: !sessionId,
    onData: ({ data }: { data: CollaboratorPresenceSubscriptionState }) => {
      const presence = data.data?.collaboratorPresence;
      if (presence) {
        setCollaborators((prev) =>
          prev.map((c) =>
            c.id === presence.userId
              ? { ...c, status: presence.status, cursor: presence.cursor }
              : c
          )
        );
      }
    },
  });

  return {
    collaborators,
    invite,
    inviting,
    remove,
    removing,
    updateRole,
    updatingRole,
  };
}

// =============================================================================
// Finalization Hook
// =============================================================================

/**
 * Hook for finalizing bootstrap and proceeding to initialization
 */
export function useFinalizeBootstrap() {
  type FinalizeMutationResult = { finalizeBootstrap?: unknown };
  type FinalizeMutationState = { loading: boolean; error?: unknown };
  const runFinalizeMutation = useMutation as unknown as (
    mutation: unknown
  ) => [
    (args: {
      variables: Record<string, unknown>;
    }) => Promise<{ data?: FinalizeMutationResult }>,
    FinalizeMutationState,
  ];

  const [finalizeMutationValue, { loading, error }] =
    runFinalizeMutation(FINALIZE_BOOTSTRAP);
  const finalizeMutation = finalizeMutationValue as (args: {
    variables: Record<string, unknown>;
  }) => Promise<{ data?: FinalizeMutationResult }>;

  const finalize = useCallback(
    async (sessionId: string) => {
      const result = await finalizeMutation({ variables: { sessionId } });
      return result.data?.finalizeBootstrap;
    },
    [finalizeMutation]
  );

  return { finalize, loading, error };
}
