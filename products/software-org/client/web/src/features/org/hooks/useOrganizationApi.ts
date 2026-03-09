/**
 * Hook for fetching organization data from backend API
 *
 * <p><b>Purpose</b><br>
 * TanStack Query hooks for organization configuration, hierarchy graph,
 * and node operations.
 *
 * <p><b>Features</b><br>
 * - Get organization configuration
 * - Get hierarchy graph
 * - Move nodes in hierarchy
 * - List departments and agents
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { data: config } = useOrgConfig();
 * const { data: graph } = useOrgGraph();
 * const { mutate: moveNode } = useMoveNode();
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Organization API data fetching
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { organizationApi, type OrgConfig, type OrgGraph, type MoveRequest, type MoveResult } from "../../../services/api/organizationApi";

/**
 * Fetch organization configuration
 */
export function useOrgConfig(options: { enabled?: boolean } = {}) {
    const { enabled = true } = options;

    return useQuery({
        queryKey: ["organization", "config"],
        queryFn: () => organizationApi.getConfig(),
        staleTime: 1000 * 60 * 10, // 10 minutes
        gcTime: 1000 * 60 * 30, // 30 minutes
        enabled,
        retry: 2,
    });
}

/**
 * Fetch organization hierarchy graph
 */
export function useOrgGraph(options: { enabled?: boolean } = {}) {
    const { enabled = true } = options;

    return useQuery({
        queryKey: ["organization", "graph"],
        queryFn: () => organizationApi.getGraph(),
        staleTime: 1000 * 60 * 5, // 5 minutes
        gcTime: 1000 * 60 * 15, // 15 minutes
        enabled,
        retry: 2,
    });
}

/**
 * Fetch organization departments
 */
export function useOrgDepartments(options: { enabled?: boolean } = {}) {
    const { enabled = true } = options;

    return useQuery({
        queryKey: ["organization", "departments"],
        queryFn: () => organizationApi.listDepartments(),
        staleTime: 1000 * 60 * 5,
        gcTime: 1000 * 60 * 10,
        enabled,
        retry: 2,
    });
}

/**
 * Fetch organization agents
 */
export function useOrgAgents(options: { enabled?: boolean } = {}) {
    const { enabled = true } = options;

    return useQuery({
        queryKey: ["organization", "agents"],
        queryFn: () => organizationApi.listAgents(),
        staleTime: 1000 * 60 * 2, // 2 minutes for agent status
        gcTime: 1000 * 60 * 5,
        enabled,
        retry: 2,
    });
}

/**
 * Move a node in the hierarchy
 */
export function useMoveNode() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (request: MoveRequest) => organizationApi.moveNode(request),
        onSuccess: () => {
            // Invalidate graph and related queries
            queryClient.invalidateQueries({ queryKey: ["organization", "graph"] });
            queryClient.invalidateQueries({ queryKey: ["organization", "departments"] });
            queryClient.invalidateQueries({ queryKey: ["organization", "agents"] });
        },
    });
}

export default {
    useOrgConfig,
    useOrgGraph,
    useOrgDepartments,
    useOrgAgents,
    useMoveNode,
};
