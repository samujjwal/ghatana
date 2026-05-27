/**
 * usePhasePacket Hook — YAPPC Web.
 *
 * Fetches and provides the canonical PhaseCockpitPacket from the backend.
 * This hook enables packet-driven rendering of phase cockpit UI components.
 *
 * ## Usage
 * ```tsx
 * const { packet, isLoading, error } = usePhasePacket({
 *   phase: 'generate',
 *   projectId: 'project-123',
 *   workspaceId: 'workspace-456'
 * });
 *
 * if (isLoading) return <LoadingSpinner />;
 * if (error) return <ErrorBanner error={error} />;
 *
 * return <PhaseCockpit packet={packet} />;
 * ```
 *
 * @doc.type hook
 * @doc.purpose Fetch canonical PhaseCockpitPacket from backend
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useCallback, useEffect, useState } from 'react';

import { LifecycleService } from '../clients/generated/api';
import type {
  PhaseCockpitPacket,
  PhasePacketRequest,
} from '../types/phasePacket';

export function createPhasePacketCorrelationId(request: PhasePacketRequest): string {
  if (request.correlationId && request.correlationId.trim().length > 0) {
    return request.correlationId;
  }

  const randomPart =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : Math.random().toString(36).slice(2);
  return `phase-packet:${request.phase}:${request.projectId}:${Date.now()}:${randomPart}`;
}

// ============================================================================
// Hook Return Type
// ============================================================================

export interface UsePhasePacketResult {
  /** The phase packet from the backend */
  packet: PhaseCockpitPacket | null;
  /** Whether the packet is being fetched */
  isLoading: boolean;
  /** Error that occurred during fetch */
  error: Error | null;
  /** Refetch the phase packet */
  refetch: () => Promise<void>;
}

// ============================================================================
// Hook
// ============================================================================

/**
 * Fetches the canonical PhaseCockpitPacket from the backend.
 *
 * @param request The phase packet request parameters
 * @returns The phase packet, loading state, and error
 */
export function usePhasePacket(request: PhasePacketRequest): UsePhasePacketResult {
  const [packet, setPacket] = useState<PhaseCockpitPacket | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetchPacket = useCallback(async (): Promise<void> => {
    if (!request.projectId || !request.workspaceId) {
      setPacket(null);
      setIsLoading(false);
      setError(null);
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const correlationId = createPhasePacketCorrelationId(request);
      // Use generated client with GET method (query parameters)
      // Task 5.A.2: Ensure GET/POST parity between manifest, OpenAPI, backend, frontend
      const data = await LifecycleService.getPhasePacket(
        request.phase as 'intent' | 'shape' | 'validate' | 'generate' | 'run' | 'observe' | 'learn' | 'evolve',
        request.projectId,
        request.workspaceId,
        correlationId
      );
      setPacket(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch phase packet'));
    } finally {
      setIsLoading(false);
    }
  }, [request.correlationId, request.phase, request.projectId, request.workspaceId]);

  useEffect(() => {
    void fetchPacket();
  }, [fetchPacket]);

  return {
    packet,
    isLoading,
    error,
    refetch: fetchPacket,
  };
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Checks if a phase action is enabled by the backend action contract.
 */
export function isActionEnabled(
  action: { enabled: boolean; disabledReason?: string }
): boolean {
  if (!action.enabled) return false;
  if (action.disabledReason) return false;
  return true;
}

/**
 * Gets the primary action from the dashboard action classification.
 */
export function getPrimaryAction(
  dashboardActions: { primaryAction: string; blockedActions: readonly string[] },
  availableActions: readonly { actionId: string; label: string; enabled: boolean }[]
): { actionId: string; label: string; enabled: boolean } | null {
  const action = availableActions.find(a => a.actionId === dashboardActions.primaryAction);
  return action || null;
}

/**
 * Gets blocked actions with their labels.
 */
export function getBlockedActions(
  dashboardActions: { blockedActions: readonly string[] },
  availableActions: readonly { actionId: string; label: string }[]
): Array<{ actionId: string; label: string }> {
  return dashboardActions.blockedActions
    .map(actionId => availableActions.find(a => a.actionId === actionId))
    .filter((a): a is { actionId: string; label: string } => a !== undefined);
}

/**
 * Checks if the phase can advance based on readiness.
 */
export function canPhaseAdvance(readiness: { canAdvance: boolean; isDegraded: boolean }): boolean {
  return readiness.canAdvance && !readiness.isDegraded;
}
