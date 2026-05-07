/**
 * P1-016: React hook for backend capabilities.
 *
 * Provides runtime capability checking from the backend.
 *
 * @doc.type hook
 * @doc.purpose React hook for backend capability queries
 * @doc.layer frontend
 */

import { useQuery } from '@tanstack/react-query';
import {
  getWorkspaceCapabilities,
  isCapabilityEnabled as checkCapabilityEnabled,
  type Capability,
} from '@/api/capabilities';

interface UseCapabilitiesOptions {
  enabled?: boolean;
  refetchInterval?: number;
}

/**
 * Fetches all capabilities for a workspace.
 */
export function useCapabilities(
  workspaceId: string | null,
  options: UseCapabilitiesOptions = {},
) {
  return useQuery({
    queryKey: ['capabilities', workspaceId],
    queryFn: () => (workspaceId ? getWorkspaceCapabilities(workspaceId) : null),
    enabled: options.enabled !== false && !!workspaceId,
    refetchInterval: options.refetchInterval ?? 300000, // 5 minutes default
    staleTime: 60000, // 1 minute
  });
}

/**
 * Checks if a specific capability is enabled.
 * P1-011: Returns null while loading to avoid "feature unavailable" flicker.
 */
export function useCapabilityEnabled(
  workspaceId: string | null,
  capabilityKey: string,
): boolean | null {
  const { data, isLoading } = useCapabilities(workspaceId);

  if (isLoading) {
    return null; // Loading state - caller should show skeleton/loading UI
  }

  if (!data) {
    return false; // Fail closed
  }

  const capability = data.capabilities.find((c) => c.key === capabilityKey);
  return capability?.enabled ?? false;
}

/**
 * Checks if multiple capabilities are enabled.
 */
export function useCapabilitiesEnabled(
  workspaceId: string | null,
  capabilityKeys: string[],
): Record<string, boolean> {
  const { data } = useCapabilities(workspaceId);

  if (!data) {
    return capabilityKeys.reduce(
      (acc, key) => ({ ...acc, [key]: false }),
      {},
    );
  }

  return capabilityKeys.reduce(
    (acc, key) => ({
      ...acc,
      [key]: data.capabilities.find((c) => c.key === key)?.enabled ?? false,
    }),
    {},
  );
}
