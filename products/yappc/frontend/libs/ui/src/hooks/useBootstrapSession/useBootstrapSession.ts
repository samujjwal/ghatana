/**
 * useBootstrapSession Hook
 *
 * @description Manages bootstrap session lifecycle including creation,
 * loading, saving, and phase transitions. Integrates with GraphQL API
 * and local state atoms.
 *
 * @doc.type hook
 * @doc.purpose Bootstrap session management
 * @doc.layer feature
 * @doc.phase bootstrapping
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import { useQuery, useMutation, useSubscription } from '@apollo/client';

import type {
  BootstrapSession,
  BootstrapPhase,
  ConversationTurn,
  CanvasNode,
  ValidationReport,
} from '@ghatana/yappc-canvas';
import {
  bootstrapSessionAtom,
  sessionIdAtom,
  currentPhaseAtom,
  sessionStatusAtom,
  conversationHistoryAtom,
  canvasNodesAtom,
  canvasEdgesAtom,
  confidenceScoreAtom,
  validationReportAtom,
  agentStatusAtom,
  sessionAutoSaveAtom,
  lastSavedAtAtom,
} from '@ghatana/yappc-canvas';

// GraphQL operations would be imported from @ghatana/yappc-api
// import {
//   GET_BOOTSTRAP_SESSION,
//   CREATE_BOOTSTRAP_SESSION,
//   UPDATE_BOOTSTRAP_SESSION,
//   SUBSCRIBE_SESSION_UPDATES,
// } from '@ghatana/yappc-api';

// =============================================================================
// Types
// =============================================================================

export interface UseBootstrapSessionOptions {
  /** Session ID to load (if existing) */
  sessionId?: string;
  /** Auto-save interval in milliseconds (default: 30000) */
  autoSaveInterval?: number;
  /** Enable auto-save (default: true) */
  enableAutoSave?: boolean;
  /** Called when session is created */
  onSessionCreated?: (session: BootstrapSession) => void;
  /** Called when session is loaded */
  onSessionLoaded?: (session: BootstrapSession) => void;
  /** Called when phase changes */
  onPhaseChange?: (phase: BootstrapPhase) => void;
  /** Called on error */
  onError?: (error: Error) => void;
}

export interface UseBootstrapSessionReturn {
  /** Current session data */
  session: BootstrapSession | null;
  /** Current phase */
  currentPhase: BootstrapPhase;
  /** Loading state */
  isLoading: boolean;
  /** Saving state */
  isSaving: boolean;
  /** Error state */
  error: Error | null;
  /** Validation report */
  validationReport: ValidationReport | null;
  /** Confidence score (0-100) */
  confidenceScore: number;
  /** Create a new session */
  createSession: (projectName: string, description?: string) => Promise<BootstrapSession>;
  /** Load an existing session */
  loadSession: (sessionId: string) => Promise<BootstrapSession>;
  /** Save current session */
  saveSession: () => Promise<void>;
  /** Advance to next phase */
  advancePhase: () => Promise<void>;
  /** Go back to previous phase */
  previousPhase: () => Promise<void>;
  /** Update session metadata */
  updateSession: (updates: Partial<BootstrapSession>) => Promise<void>;
  /** Add conversation turn */
  addConversationTurn: (turn: Omit<ConversationTurn, 'id' | 'timestamp'>) => void;
  /** Complete the session */
  completeSession: () => Promise<void>;
  /** Abandon the session */
  abandonSession: () => Promise<void>;
}

// =============================================================================
// Phase Order
// =============================================================================

const PHASE_ORDER: BootstrapPhase[] = ['enter', 'explore', 'refine', 'validate', 'complete'];

const getNextPhase = (current: BootstrapPhase): BootstrapPhase | null => {
  const index = PHASE_ORDER.indexOf(current);
  return index < PHASE_ORDER.length - 1 ? PHASE_ORDER[index + 1] : null;
};

const getPreviousPhase = (current: BootstrapPhase): BootstrapPhase | null => {
  const index = PHASE_ORDER.indexOf(current);
  return index > 0 ? PHASE_ORDER[index - 1] : null;
};

// =============================================================================
// Hook Implementation
// =============================================================================

export function useBootstrapSession(
  options: UseBootstrapSessionOptions = {}
): UseBootstrapSessionReturn {
  const {
    sessionId: initialSessionId,
    autoSaveInterval = 30000,
    enableAutoSave = true,
    onSessionCreated,
    onSessionLoaded,
    onPhaseChange,
    onError,
  } = options;

  // Local state
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  // Atoms
  const [session, setSession] = useAtom(bootstrapSessionAtom);
  const [sessionIdValue, setSessionId] = useAtom(sessionIdAtom);
  const [currentPhase, setCurrentPhase] = useAtom(currentPhaseAtom);
  const setSessionStatus = useSetAtom(sessionStatusAtom);
  const [conversationHistory, setConversationHistory] = useAtom(conversationHistoryAtom);
  const [nodes, setNodes] = useAtom(canvasNodesAtom);
  const [edges, setEdges] = useAtom(canvasEdgesAtom);
  const [confidenceScore, setConfidenceScore] = useAtom(confidenceScoreAtom);
  const validationReport = useAtomValue(validationReportAtom);
  const setAgentStatus = useSetAtom(agentStatusAtom);
  const [autoSaveStatus, setAutoSaveStatus] = useAtom(sessionAutoSaveAtom);
  const setLastSavedAt = useSetAtom(lastSavedAtAtom);

  const isSaving = autoSaveStatus === 'saving';

  // Create new session
  const createSession = useCallback(
    async (projectName: string, description?: string): Promise<BootstrapSession> => {
      setIsLoading(true);
      setError(null);

      try {
        // TODO: Replace with actual GraphQL mutation
        const newSession: BootstrapSession = {
          id: `session-${Date.now()}`,
          projectName,
          description: description || '',
          phase: 'enter',
          status: 'active',
          progress: 0,
          confidenceScore: 0,
          questionsAnswered: 0,
          totalQuestions: 10,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
          ownerId: 'current-user',
          collaboratorIds: [],
          features: [],
          techStack: [],
          timeline: {
            mvpWeeks: 0,
            totalWeeks: 0,
            phases: [],
            milestones: [],
          },
        };

        setSession(newSession);
        setSessionId(newSession.id);
        setCurrentPhase('enter');
        setSessionStatus('active');
        setConversationHistory([]);
        setNodes([]);
        setEdges([]);
        setConfidenceScore(0);

        onSessionCreated?.(newSession);
        return newSession;
      } catch (err) {
        const error = err instanceof Error ? err : new Error('Failed to create session');
        setError(error);
        onError?.(error);
        throw error;
      } finally {
        setIsLoading(false);
      }
    },
    [
      setSession,
      setSessionId,
      setCurrentPhase,
      setSessionStatus,
      setConversationHistory,
      setNodes,
      setEdges,
      setConfidenceScore,
      onSessionCreated,
      onError,
    ]
  );

  // Load existing session
  const loadSession = useCallback(
    async (sessionId: string): Promise<BootstrapSession> => {
      setIsLoading(true);
      setError(null);

      try {
        // TODO: Replace with actual GraphQL query
        // const { data } = await client.query({ query: GET_BOOTSTRAP_SESSION, variables: { id: sessionId } });
        // const loadedSession = data.bootstrapSession;

        // Mock for now - in real implementation, this would fetch from API
        const loadedSession: BootstrapSession = {
          id: sessionId,
          projectName: 'Loaded Project',
          description: '',
          phase: 'explore',
          status: 'active',
          progress: 20,
          confidenceScore: 35,
          questionsAnswered: 3,
          totalQuestions: 10,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
          ownerId: 'current-user',
          collaboratorIds: [],
          features: [],
          techStack: [],
          timeline: {
            mvpWeeks: 0,
            totalWeeks: 0,
            phases: [],
            milestones: [],
          },
        };

        setSession(loadedSession);
        setSessionId(loadedSession.id);
        setCurrentPhase(loadedSession.phase);
        setSessionStatus(loadedSession.status);
        setConfidenceScore(loadedSession.confidenceScore);

        onSessionLoaded?.(loadedSession);
        return loadedSession;
      } catch (err) {
        const error = err instanceof Error ? err : new Error('Failed to load session');
        setError(error);
        onError?.(error);
        throw error;
      } finally {
        setIsLoading(false);
      }
    },
    [
      setSession,
      setSessionId,
      setCurrentPhase,
      setSessionStatus,
      setConfidenceScore,
      onSessionLoaded,
      onError,
    ]
  );

  // Save current session
  const saveSession = useCallback(async (): Promise<void> => {
    if (!session) return;

    setAutoSaveStatus('saving');

    try {
      // TODO: Replace with actual GraphQL mutation
      // await client.mutate({
      //   mutation: UPDATE_BOOTSTRAP_SESSION,
      //   variables: {
      //     id: session.id,
      //     input: {
      //       phase: currentPhase,
      //       confidenceScore,
      //       conversationHistory,
      //       nodes,
      //       edges,
      //     },
      //   },
      // });

      setLastSavedAt(new Date());
      setAutoSaveStatus('saved');
    } catch (err) {
      setAutoSaveStatus('error');
      const error = err instanceof Error ? err : new Error('Failed to save session');
      setError(error);
      onError?.(error);
    }
  }, [
    session,
    currentPhase,
    confidenceScore,
    setAutoSaveStatus,
    setLastSavedAt,
    onError,
  ]);

  // Advance to next phase
  const advancePhase = useCallback(async (): Promise<void> => {
    const nextPhase = getNextPhase(currentPhase);
    if (!nextPhase) return;

    setAgentStatus('thinking');

    try {
      // TODO: Validate current phase is complete before advancing
      setCurrentPhase(nextPhase);
      
      if (session) {
        setSession({
          ...session,
          phase: nextPhase,
          updatedAt: new Date().toISOString(),
        });
      }

      onPhaseChange?.(nextPhase);
      await saveSession();
    } finally {
      setAgentStatus('idle');
    }
  }, [currentPhase, session, setCurrentPhase, setSession, setAgentStatus, saveSession, onPhaseChange]);

  // Go to previous phase
  const previousPhase = useCallback(async (): Promise<void> => {
    const prevPhase = getPreviousPhase(currentPhase);
    if (!prevPhase) return;

    setCurrentPhase(prevPhase);
    
    if (session) {
      setSession({
        ...session,
        phase: prevPhase,
        updatedAt: new Date().toISOString(),
      });
    }

    onPhaseChange?.(prevPhase);
    await saveSession();
  }, [currentPhase, session, setCurrentPhase, setSession, saveSession, onPhaseChange]);

  // Update session
  const updateSession = useCallback(
    async (updates: Partial<BootstrapSession>): Promise<void> => {
      if (!session) return;

      const updatedSession = {
        ...session,
        ...updates,
        updatedAt: new Date().toISOString(),
      };

      setSession(updatedSession);
      await saveSession();
    },
    [session, setSession, saveSession]
  );

  // Add conversation turn
  const addConversationTurn = useCallback(
    (turn: Omit<ConversationTurn, 'id' | 'timestamp'>): void => {
      const newTurn: ConversationTurn = {
        ...turn,
        id: `turn-${Date.now()}`,
        timestamp: new Date().toISOString(),
      };

      setConversationHistory((prev) => [...prev, newTurn]);
    },
    [setConversationHistory]
  );

  // Complete session
  const completeSession = useCallback(async (): Promise<void> => {
    if (!session) return;

    setSession({
      ...session,
      phase: 'complete',
      status: 'completed',
      updatedAt: new Date().toISOString(),
    });

    setCurrentPhase('complete');
    setSessionStatus('completed');
    await saveSession();
  }, [session, setSession, setCurrentPhase, setSessionStatus, saveSession]);

  // Abandon session
  const abandonSession = useCallback(async (): Promise<void> => {
    if (!session) return;

    setSession({
      ...session,
      status: 'abandoned',
      updatedAt: new Date().toISOString(),
    });

    setSessionStatus('abandoned');
    await saveSession();
  }, [session, setSession, setSessionStatus, saveSession]);

  // Auto-save effect
  useEffect(() => {
    if (!enableAutoSave || !session) return;

    const interval = setInterval(() => {
      saveSession();
    }, autoSaveInterval);

    return () => clearInterval(interval);
  }, [enableAutoSave, autoSaveInterval, session, saveSession]);

  // Load initial session if provided
  useEffect(() => {
    if (initialSessionId && !session) {
      loadSession(initialSessionId);
    }
  }, [initialSessionId, session, loadSession]);

  return {
    session,
    currentPhase,
    isLoading,
    isSaving,
    error,
    validationReport,
    confidenceScore,
    createSession,
    loadSession,
    saveSession,
    advancePhase,
    previousPhase,
    updateSession,
    addConversationTurn,
    completeSession,
    abandonSession,
  };
}

export default useBootstrapSession;
