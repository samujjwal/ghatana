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

import { useCallback, useMemo } from 'react';
import {
  useQuery,
  useLazyQuery,
  useMutation,
  useSubscription,
  useApolloClient,
} from '@apollo/client';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';

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
} from '@ghatana/yappc-api';

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
} from '@ghatana/yappc-canvas';

// =============================================================================
// Session Hooks
// =============================================================================

/**
 * Hook for fetching a single bootstrap session
 */
export function useBootstrapSession(sessionId: string | undefined) {
  const [, setSession] = useAtom(sessionAtom);
  
  const { data, loading, error, refetch } = useQuery(GET_BOOTSTRAP_SESSION, {
    variables: { id: sessionId },
    skip: !sessionId,
    onCompleted: (data) => {
      if (data?.bootstrapSession) {
        setSession(data.bootstrapSession);
      }
    },
  });

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

  const { data, loading, error, fetchMore, refetch } = useQuery(LIST_BOOTSTRAP_SESSIONS, {
    variables: {
      filter,
      pagination: { first: pageSize },
    },
    notifyOnNetworkStatusChange: true,
  });

  const loadMore = useCallback(() => {
    if (!data?.bootstrapSessions?.pageInfo?.hasNextPage) return;

    fetchMore({
      variables: {
        pagination: {
          first: pageSize,
          after: data.bootstrapSessions.pageInfo.endCursor,
        },
      },
    });
  }, [data, fetchMore, pageSize]);

  return {
    sessions: data?.bootstrapSessions?.edges?.map((e: unknown) => e.node) ?? [],
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
  const setSession = useSetAtom(sessionAtom);
  
  const [createSession, { loading, error }] = useMutation(CREATE_BOOTSTRAP_SESSION, {
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
              { node: data?.createBootstrapSession?.session, __typename: 'BootstrapSessionEdge' },
              ...existing.edges,
            ],
          }),
        },
      });
    },
  });

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
  const [updateSession, { loading, error }] = useMutation(UPDATE_BOOTSTRAP_SESSION);

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
  const [deleteSession, { loading, error }] = useMutation(DELETE_BOOTSTRAP_SESSION, {
    update: (cache, { data }, { variables }) => {
      if (data?.deleteBootstrapSession?.success && variables?.id) {
        cache.evict({ id: cache.identify({ __typename: 'BootstrapSession', id: variables.id }) });
        cache.gc();
      }
    },
  });

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
  const [conversationHistory, setConversationHistory] = useAtom(conversationHistoryAtom);
  const addTurn = useSetAtom(addConversationTurnAction);

  const { data, loading: historyLoading } = useQuery(GET_CONVERSATION_HISTORY, {
    variables: { sessionId, pagination: { first: 100 } },
    skip: !sessionId,
    onCompleted: (data) => {
      const turns = data?.conversationHistory?.edges?.map((e: unknown) => e.node) ?? [];
      setConversationHistory(turns);
    },
  });

  const [sendMessage, { loading: sending }] = useMutation(SEND_CONVERSATION_MESSAGE);
  const [regenerateResponse, { loading: regenerating }] = useMutation(REGENERATE_AI_RESPONSE);

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
  useSubscription(SUBSCRIBE_TO_CONVERSATION, {
    variables: { sessionId },
    skip: !sessionId,
    onData: ({ data }) => {
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
  const { data } = useSubscription(SUBSCRIBE_TO_AI_THINKING, {
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
  const [nodes, setNodes] = useAtom(canvasNodesAtom);
  const [edges, setEdges] = useAtom(canvasEdgesAtom);
  const addNode = useSetAtom(addCanvasNodeAction);
  const updateNode = useSetAtom(updateCanvasNodeAction);
  const removeNode = useSetAtom(removeCanvasNodeAction);
  const undo = useSetAtom(undoAction);
  const redo = useSetAtom(redoAction);

  const { loading } = useQuery(GET_CANVAS_STATE, {
    variables: { sessionId },
    skip: !sessionId,
    onCompleted: (data) => {
      if (data?.canvasState) {
        setNodes(data.canvasState.nodes ?? []);
        setEdges(data.canvasState.edges ?? []);
      }
    },
  });

  const [addNodeMutation] = useMutation(ADD_CANVAS_NODE);
  const [updateNodeMutation] = useMutation(UPDATE_CANVAS_NODE);
  const [deleteNodeMutation] = useMutation(DELETE_CANVAS_NODE);
  const [addEdgeMutation] = useMutation(ADD_CANVAS_EDGE);
  const [deleteEdgeMutation] = useMutation(DELETE_CANVAS_EDGE);
  const [undoMutation] = useMutation(UNDO_CANVAS_ACTION);
  const [redoMutation] = useMutation(REDO_CANVAS_ACTION);

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
      const result = await updateNodeMutation({ variables: { sessionId, nodeId, input } });
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
      const result = await deleteNodeMutation({ variables: { sessionId, nodeId } });
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
      const result = await deleteEdgeMutation({ variables: { sessionId, edgeId } });
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
  useSubscription(SUBSCRIBE_TO_CANVAS_CHANGES, {
    variables: { sessionId },
    skip: !sessionId,
    onData: ({ data }) => {
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
          if (change.edge?.id) setEdges((prev) => prev.filter((e) => e.id !== change.edge.id));
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
  const [validationReport, setValidationReport] = useAtom(validationReportAtom);

  const { loading: reportLoading, refetch } = useQuery(GET_VALIDATION_REPORT, {
    variables: { sessionId },
    skip: !sessionId,
    onCompleted: (data) => {
      if (data?.validationReport) {
        setValidationReport(data.validationReport);
      }
    },
  });

  const [validateMutation, { loading: validating }] = useMutation(VALIDATE_SESSION);
  const [autoFixMutation, { loading: fixing }] = useMutation(AUTO_FIX_VALIDATION_ISSUES);

  const validate = useCallback(
    async (options?: ValidationOptions) => {
      const result = await validateMutation({ variables: { sessionId, options } });
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
      const result = await autoFixMutation({ variables: { sessionId, issueIds } });
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
  useSubscription(SUBSCRIBE_TO_VALIDATION_UPDATES, {
    variables: { sessionId },
    skip: !sessionId,
    onData: ({ data }) => {
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
export function useAISuggestions(sessionId: string, context: AISuggestionContext) {
  const [getSuggestions, { data, loading, error }] = useLazyQuery(GET_AI_SUGGESTIONS);

  const fetchSuggestions = useCallback(() => {
    return getSuggestions({ variables: { sessionId, context } });
  }, [sessionId, context, getSuggestions]);

  return {
    suggestions: data?.aiSuggestions ?? [],
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
export function useSessionTemplates(options?: { category?: string; search?: string }) {
  const { data, loading, error, refetch } = useQuery(GET_SESSION_TEMPLATES, {
    variables: options,
  });

  const [applyTemplate, { loading: applying }] = useMutation(APPLY_SESSION_TEMPLATE);

  const apply = useCallback(
    async (sessionId: string, templateId: string) => {
      const result = await applyTemplate({ variables: { sessionId, templateId } });
      return result.data?.applySessionTemplate;
    },
    [applyTemplate]
  );

  return {
    templates: data?.sessionTemplates ?? [],
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
  const [exportMutation, { loading, error }] = useMutation(EXPORT_SESSION);

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
  const [importMutation, { loading, error }] = useMutation(IMPORT_SESSION);

  const importSession = useCallback(
    async (file: File, format: 'json' | 'yaml' | 'yappc', conflictResolution?: string) => {
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
  const [collaborators, setCollaborators] = useAtom(collaboratorsAtom);

  const [inviteMutation, { loading: inviting }] = useMutation(INVITE_COLLABORATOR);
  const [removeMutation, { loading: removing }] = useMutation(REMOVE_COLLABORATOR);
  const [updateRoleMutation, { loading: updatingRole }] = useMutation(UPDATE_COLLABORATOR_ROLE);

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
      const result = await removeMutation({ variables: { sessionId, collaboratorId } });
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
      const updated = result.data?.updateCollaboratorRole?.collaborator;
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
  useSubscription(SUBSCRIBE_TO_COLLABORATOR_PRESENCE, {
    variables: { sessionId },
    skip: !sessionId,
    onData: ({ data }) => {
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
  const [finalizeMutation, { loading, error }] = useMutation(FINALIZE_BOOTSTRAP);

  const finalize = useCallback(
    async (sessionId: string) => {
      const result = await finalizeMutation({ variables: { sessionId } });
      return result.data?.finalizeBootstrap;
    },
    [finalizeMutation]
  );

  return { finalize, loading, error };
}
