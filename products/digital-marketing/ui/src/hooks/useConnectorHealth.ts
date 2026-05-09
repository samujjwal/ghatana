/**
 * Hook for connector health status on dashboard.
 *
 * @doc.type hook
 * @doc.purpose Fetch and normalize connector health from DMOS health endpoint
 * @doc.layer frontend
 */

import { useQuery } from '@tanstack/react-query';
import { getConnectorHealth, type DmosBridgeStatusResponse } from '@/api/connectors';

export interface ConnectorHealth {
  name: string;
  status: 'healthy' | 'degraded' | 'unhealthy' | 'not_configured';
  lastSync?: string;
  detail?: string;
}

function toConnectorStatus(
  status: string,
): ConnectorHealth['status'] {
  const normalized = status.trim().toUpperCase();
  if (normalized === 'UP') {
    return 'healthy';
  }
  if (normalized === 'DEGRADED') {
    return 'degraded';
  }
  if (normalized === 'DOWN') {
    return 'unhealthy';
  }
  return 'not_configured';
}

function toDisplayName(bridgeId: string): string {
  return bridgeId
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function mapBridgeToConnector(
  bridgeId: string,
  bridge: DmosBridgeStatusResponse,
): ConnectorHealth {
  return {
    name: toDisplayName(bridgeId),
    status: toConnectorStatus(bridge.status),
    lastSync: bridge.updatedAt,
    detail: bridge.reason,
  };
}

export function useConnectorHealth(workspaceId: string | null): {
  connectors: ConnectorHealth[];
  isLoading: boolean;
  isError: boolean;
  error: Error | null;
  source: string;
  lastUpdated: string | null;
  unavailableReason: string | undefined;
} {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['connector-health', workspaceId],
    queryFn: getConnectorHealth,
    enabled: workspaceId !== null,
    staleTime: 30_000,
  });

  const bridges = data?.checks?.kernelBridge?.bridges;
  const connectors = bridges
    ? Object.entries(bridges).map(([bridgeId, bridge]) => mapBridgeToConnector(bridgeId, bridge))
    : [];

  return {
    connectors,
    isLoading,
    isError,
    error: error ?? null,
    source: 'DMOS Health API /health',
    lastUpdated: data?.timestamp ?? null,
    unavailableReason:
      connectors.length === 0
        ? 'No connector bridge health signals are currently available for this workspace.'
        : undefined,
  };
}
