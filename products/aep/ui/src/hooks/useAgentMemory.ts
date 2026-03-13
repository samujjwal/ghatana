/**
 * useAgentMemory — TanStack Query hooks for agent memory data.
 *
 * Provides separate queries for episodes, policies (procedural memory), and
 * the memory summary (counts by type). Each hook is keyed by agentId + tenantId.
 *
 * @doc.type hook
 * @doc.purpose Fetch agent episodic/procedural memory data with TanStack Query
 * @doc.layer frontend
 */
import { useQuery } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import {
  listEpisodes,
  listPolicies,
  getAgentMemory,
  getAgentEpisodes,
  getAgentFacts,
  getAgentPolicies,
} from '@/api/aep.api';

export const AGENT_MEMORY_QUERY_KEY = 'agent-memory';
export const EPISODES_QUERY_KEY = 'episodes';
export const POLICIES_QUERY_KEY = 'policies';

/** Fetch paginated episode history for an agent. */
export function useEpisodes(agentId?: string, limit = 50) {
  const tenantId = useAtomValue(tenantIdAtom);

  return useQuery({
    queryKey: [EPISODES_QUERY_KEY, tenantId, agentId, limit],
    queryFn: () => listEpisodes(tenantId, limit),
    enabled: Boolean(agentId),
    staleTime: 15_000,
  });
}

/** Fetch all tenant-level episodes (not scoped to a specific agent). */
export function useAllEpisodes(limit = 50) {
  const tenantId = useAtomValue(tenantIdAtom);

  return useQuery({
    queryKey: [EPISODES_QUERY_KEY, tenantId, limit],
    queryFn: () => listEpisodes(tenantId, limit),
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

/** Fetch all learned policies for the tenant. */
export function usePolicies() {
  const tenantId = useAtomValue(tenantIdAtom);

  return useQuery({
    queryKey: [POLICIES_QUERY_KEY, tenantId],
    queryFn: () => listPolicies(tenantId),
    staleTime: 30_000,
  });
}

/** Fetch memory summary (counts by type) for a specific agent. */
export function useAgentMemorySummary(agentId?: string) {
  const tenantId = useAtomValue(tenantIdAtom);

  return useQuery({
    queryKey: [AGENT_MEMORY_QUERY_KEY, tenantId, agentId],
    queryFn: () => getAgentMemory(agentId!, tenantId),
    enabled: Boolean(agentId),
    staleTime: 30_000,
  });
}

/** Fetch agent-scoped episodic memory records from dc_memory. */
export function useAgentEpisodes(agentId?: string, limit = 50) {
  const tenantId = useAtomValue(tenantIdAtom);

  return useQuery({
    queryKey: ['agent-episodes', tenantId, agentId, limit],
    queryFn: () => getAgentEpisodes(agentId!, tenantId, limit),
    enabled: Boolean(agentId),
    staleTime: 30_000,
  });
}

/** Fetch agent-scoped semantic facts from dc_memory. */
export function useAgentFacts(agentId?: string, limit = 100) {
  const tenantId = useAtomValue(tenantIdAtom);

  return useQuery({
    queryKey: ['agent-facts', tenantId, agentId, limit],
    queryFn: () => getAgentFacts(agentId!, tenantId, limit),
    enabled: Boolean(agentId),
    staleTime: 60_000,
  });
}

/** Fetch agent-scoped procedural policies from dc_memory. */
export function useAgentPolicies(agentId?: string, limit = 50) {
  const tenantId = useAtomValue(tenantIdAtom);

  return useQuery({
    queryKey: ['agent-policies', tenantId, agentId, limit],
    queryFn: () => getAgentPolicies(agentId!, tenantId, limit),
    enabled: Boolean(agentId),
    staleTime: 60_000,
  });
}
