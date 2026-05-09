/**
 * F-024 / SIMP-10: Fetches the server-driven capability manifest from
 * `/api/v1/surfaces` and exposes it as a typed hook.
 * DC-P1.12: Migrated from /api/v1/capabilities to canonical /api/v1/surfaces endpoint.
 *
 * The manifest is used throughout the cockpit to gate pages and actions on
 * capabilities that are actually wired in the running backend — preventing
 * "dead" action buttons that silently 404.
 *
 * @doc.type hook
 * @doc.purpose Server-driven capability flag resolution for UI gating
 * @doc.layer frontend
 */
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/http-client';

export interface AepCapabilities {
  dataCloud: boolean;
  redis: boolean;
  analyticsStore: boolean;
  aiSuggestions: boolean;
  nlpParse: boolean;
  gdprCompliance: boolean;
  soc2Compliance: boolean;
  piiEnforcement: boolean;
  killSwitch: boolean;
  gracefulDegradation: boolean;
  policyEngine: boolean;
  episodeLearning: boolean;
  humanInTheLoop: boolean;
  serverSideConsent: boolean;
  durableSessions: boolean;
  sseStreaming: boolean;
}

interface CapabilitiesResponse {
  tenantId: string;
  capabilities: AepCapabilities;
  generatedAt: string;
}

const DEFAULT_CAPABILITIES: AepCapabilities = {
  dataCloud: false,
  redis: false,
  analyticsStore: false,
  aiSuggestions: true,
  nlpParse: true,
  gdprCompliance: false,
  soc2Compliance: false,
  piiEnforcement: true,
  killSwitch: true,
  gracefulDegradation: true,
  policyEngine: true,
  episodeLearning: false,
  humanInTheLoop: true,
  serverSideConsent: true,
  durableSessions: false,
  sseStreaming: true,
};

async function fetchCapabilities(): Promise<CapabilitiesResponse> {
  const response = await apiClient.get<CapabilitiesResponse>('/api/v1/surfaces');
  return response.data;
}

/**
 * Returns the live server capability manifest.
 *
 * Falls back to conservative defaults when the backend is unreachable so the
 * UI does not crash — degraded capabilities are surfaced in the runtime banner.
 */
export function useCapabilities(): {
  capabilities: AepCapabilities;
  isLoading: boolean;
  isDegraded: boolean;
  generatedAt: string | null;
} {
  const { data, isLoading, isError } = useQuery<CapabilitiesResponse>({
    queryKey: ['aep', 'capabilities'],
    queryFn: fetchCapabilities,
    staleTime: 60_000,
  });

  const capabilities = data?.capabilities ?? DEFAULT_CAPABILITIES;
  const isDegraded = isError || (!isLoading && !data?.capabilities.dataCloud);

  return {
    capabilities,
    isLoading,
    isDegraded,
    generatedAt: data?.generatedAt ?? null,
  };
}

