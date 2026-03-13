/**
 * useAgents — TanStack Query hook for the agent registry list.
 *
 * Returns a paginated, filterable list of registered agents for the active tenant.
 * Automatically refetches every 30 s while the tab is focused.
 *
 * @doc.type hook
 * @doc.purpose Fetch and cache agent registry data with TanStack Query
 * @doc.layer frontend
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { tenantIdAtom } from '@/stores/tenant.store';
import {
  listAgents,
  getAgent,
  deregisterAgent,
  type AgentRegistration,
} from '@/api/aep.api';
import type { AgentFilters } from '@/types/agent.types';

export const AGENTS_QUERY_KEY = 'agents';

/**
 * Returns the full agent list for the current tenant, filtered client-side.
 *
 * Filtering is intentionally client-side because the list is typically small
 * (< 200 agents) and avoids extra server round-trips for each keystroke.
 */
export function useAgents(filters: AgentFilters = {}) {
  const tenantId = useAtomValue(tenantIdAtom);

  const query = useQuery({
    queryKey: [AGENTS_QUERY_KEY, tenantId],
    queryFn: () => listAgents(tenantId),
    staleTime: 30_000,
    refetchInterval: 30_000,
  });

  const data = query.data ?? [];

  const filtered = data.filter((agent: AgentRegistration) => {
    if (filters.status && agent.status !== filters.status) return false;
    if (filters.capability && !agent.capabilities.includes(filters.capability)) return false;
    if (filters.search) {
      const q = filters.search.toLowerCase();
      if (!agent.name.toLowerCase().includes(q) && !agent.id.toLowerCase().includes(q)) return false;
    }
    return true;
  });

  return { ...query, data: filtered, total: data.length };
}

/** Returns a single agent by ID. */
export function useAgent(agentId: string) {
  const tenantId = useAtomValue(tenantIdAtom);

  return useQuery({
    queryKey: [AGENTS_QUERY_KEY, tenantId, agentId],
    queryFn: () => getAgent(agentId, tenantId),
    enabled: Boolean(agentId),
    staleTime: 15_000,
  });
}

/** Mutation to deregister an agent. Invalidates the agent list on success. */
export function useDeregisterAgent() {
  const tenantId = useAtomValue(tenantIdAtom);
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (agentId: string) => deregisterAgent(agentId, tenantId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [AGENTS_QUERY_KEY, tenantId] });
    },
  });
}
