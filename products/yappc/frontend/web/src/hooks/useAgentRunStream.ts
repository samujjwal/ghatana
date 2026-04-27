/**
 * useAgentRunStream Hook
 *
 * Provides real-time agent run records for a given project by connecting to
 * the LifecycleWebSocketService. Falls back to the supplied seeded runs
 * whenever the WebSocket is unavailable.
 *
 * The hook merges live `agent_result` updates from the WebSocket into the run
 * list so the UI always shows the most recent state.
 *
 * @doc.type hook
 * @doc.purpose Real-time agent run streaming with seeded fallback
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useEffect, useRef, useState, useCallback } from 'react';
import {
  LifecycleWebSocketService,
  type LifecycleStateUpdate,
} from '../services/LifecycleWebSocketService';
import type {
  AgentRunRecord,
  AgentRunStatus,
} from '../components/agents/AgentRunViewer';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function mergeRunUpdate(
  existing: AgentRunRecord[],
  update: LifecycleStateUpdate,
): AgentRunRecord[] {
  if (update.type !== 'agent_result') {
    return existing;
  }

  const incoming = update.data as Partial<AgentRunRecord> & { id?: string };
  if (!incoming?.id) {
    return existing;
  }

  const idx = existing.findIndex((r) => r.id === incoming.id);
  if (idx === -1) {
    // New run — append it
    return [
      ...existing,
      {
        id: incoming.id,
        agentName: incoming.agentName ?? 'UnknownAgent',
        status: (incoming.status as AgentRunStatus) ?? 'QUEUED',
        stage: incoming.stage ?? 'UNKNOWN',
        retryCount: incoming.retryCount ?? 0,
        createdAt: incoming.createdAt ?? update.timestamp,
        startedAt: incoming.startedAt,
        completedAt: incoming.completedAt,
        errorMessage: incoming.errorMessage,
      },
    ];
  }

  // Update existing run
  const updated = [...existing];
  updated[idx] = { ...existing[idx], ...incoming };
  return updated;
}

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface UseAgentRunStreamOptions {
  /** Initial seeded runs to show before any live data arrives. */
  seededRuns: AgentRunRecord[];
}

export interface UseAgentRunStreamResult {
  /** Current list of agent run records (seeded + live updates merged). */
  runs: AgentRunRecord[];
  /** Whether the WebSocket is currently connected. */
  isConnected: boolean;
  /** Replace all runs (e.g. for retry state updates that happen locally). */
  setRuns: React.Dispatch<React.SetStateAction<AgentRunRecord[]>>;
}

// ---------------------------------------------------------------------------
// Hook
// ---------------------------------------------------------------------------

/**
 * @example
 * ```tsx
 * const { runs, setRuns, isConnected } = useAgentRunStream({
 *   seededRuns: buildSeededRuns(projectId),
 * });
 * ```
 */
export function useAgentRunStream(
  projectId: string,
  options: UseAgentRunStreamOptions,
): UseAgentRunStreamResult {
  const [runs, setRuns] = useState<AgentRunRecord[]>(options.seededRuns);
  const [isConnected, setIsConnected] = useState(false);
  const serviceRef = useRef<LifecycleWebSocketService | null>(null);

  const handleUpdate = useCallback((update: LifecycleStateUpdate) => {
    setRuns((current) => mergeRunUpdate(current, update));
  }, []);

  const handleConnectionChange = useCallback((connected: boolean) => {
    setIsConnected(connected);
  }, []);

  useEffect(() => {
    const service = new LifecycleWebSocketService();
    serviceRef.current = service;

    const unsubUpdate = service.onUpdate(handleUpdate);
    const unsubConn = service.onConnectionChange(handleConnectionChange);

    service.connect(projectId);

    return () => {
      unsubUpdate();
      unsubConn();
      service.disconnect();
      serviceRef.current = null;
    };
  }, [projectId, handleUpdate, handleConnectionChange]);

  // Reset to new seeded runs when the projectId changes
  useEffect(() => {
    setRuns(options.seededRuns);
    // options.seededRuns is intentionally excluded from deps: we only want to
    // reset when the project changes, not when the caller re-creates the array.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  return { runs, setRuns, isConnected };
}
