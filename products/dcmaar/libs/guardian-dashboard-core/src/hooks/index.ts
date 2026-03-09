import { useQueries, useQuery } from "@tanstack/react-query";
import { useRole } from "../roles";
import type { PermissionSet } from "../roles";
import { getDashboardFetch } from "../utils/apiClient";

export function useDashboardData() {
    const { permissions } = useRole();
    const fetcher = getDashboardFetch();

    const queries = useQueries({
        queries: [
            {
                queryKey: ["devices"],
                queryFn: async () => fetcher("/api/devices"),
                enabled: permissions.canViewDevices
            },
            {
                queryKey: ["usage"],
                queryFn: async () => fetcher("/api/usage"),
                enabled: permissions.canViewUsage
            },
            {
                queryKey: ["policies"],
                queryFn: async () => fetcher("/api/policies"),
                enabled: permissions.canManagePolicies
            },
            {
                queryKey: ["alerts"],
                queryFn: async () => fetcher("/api/alerts"),
                enabled: permissions.canViewAlerts
            }
        ]
    });

    return {
        devices: queries[0]?.data,
        usage: queries[1]?.data,
        policies: queries[2]?.data,
        alerts: queries[3]?.data,
        isLoading: queries.some((q) => q.isLoading)
    };
}

export function useRoleQuery<TData = unknown>(
    queryKey: string[],
    permission: keyof PermissionSet
) {
    const { permissions } = useRole();
    const canAccess = permissions[permission];
    const fetcher = getDashboardFetch();

    return useQuery<TData>({
        queryKey,
        enabled: Boolean(canAccess),
        queryFn: async () => {
            const path = `/api/${queryKey.join("/")}`;
            return fetcher(path);
        }
    });
}

export function useDevicesData<TData = unknown>() {
    return useRoleQuery<TData>(["devices"], "canViewDevices");
}

export function useUsageOverview<TData = unknown>() {
    return useRoleQuery<TData>(["usage"], "canViewUsage");
}

export function usePoliciesData<TData = unknown>() {
    return useRoleQuery<TData>(["policies"], "canManagePolicies");
}

export function useAlertsData<TData = unknown>() {
    return useRoleQuery<TData>(["alerts"], "canViewAlerts");
}
