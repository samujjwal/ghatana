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

import { useState, useEffect } from 'react';
import type {
  PhaseCockpitPacket,
  PhasePacketRequest,
} from '../types/phasePacket';

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
  refetch: () => void;
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

  const fetchPacket = async () => {
    setIsLoading(true);
    setError(null);

    try {
      // TODO: Replace with actual API call to phase packet endpoint
      // For now, this is a placeholder that will be implemented when the backend endpoint is ready
      const response = await fetch('/api/v1/phase/packet', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch phase packet: ${response.statusText}`);
      }

      const data = await response.json();
      setPacket(data);
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Failed to fetch phase packet'));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchPacket();
  }, [request.phase, request.projectId, request.workspaceId]);

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
 * Checks if a phase action is enabled based on capabilities.
 */
export function isActionEnabled(
  action: { enabled: boolean; disabledReason?: string },
  capabilities: { canRead: boolean; canCreate: boolean; canUpdate: boolean; canDelete: boolean }
): boolean {
  if (!action.enabled) return false;
  if (action.disabledReason) return false;
  
  // Additional capability checks can be added here based on action type
  return capabilities.canRead;
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
