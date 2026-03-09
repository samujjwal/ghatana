import { useQuery } from '@tanstack/react-query';
import { useAtom } from 'jotai';
import { orgDataAtom } from '../state/orgState';

/**
 * Hook for fetching organization metadata and health status.
 * 
 * Fetches:
 * - Organization name, tenantId, configuration
 * - Department list and health status
 * - Overall platform health
 * - Polling interval: 30 seconds for health checks
 */
export function useOrgData() {
    const [orgData, setOrgData] = useAtom(orgDataAtom);

    const { data: orgMetadata, isLoading: isOrgLoading, error: orgError } = useQuery({
        queryKey: ['org-metadata'],
        queryFn: async () => {
            const response = await fetch('/api/v1/org/metadata', {
                headers: {
                    'X-Tenant-Id': orgData?.tenantId || 'default',
                    'Content-Type': 'application/json',
                },
            });
            if (!response.ok) throw new Error('Failed to fetch org metadata');
            return response.json();
        },
        staleTime: 60000, // 1 minute
        refetchInterval: 30000, // 30 seconds
    });

    const { data: health, isLoading: isHealthLoading, error: healthError } = useQuery({
        queryKey: ['org-health'],
        queryFn: async () => {
            const response = await fetch('/api/v1/health', {
                headers: {
                    'X-Tenant-Id': orgData?.tenantId || 'default',
                },
            });
            if (!response.ok) throw new Error('Failed to fetch health');
            return response.json();
        },
        staleTime: 15000, // 15 seconds
        refetchInterval: 10000, // 10 seconds
    });

    const { data: departments, isLoading: isDeptLoading } = useQuery({
        queryKey: ['departments'],
        queryFn: async () => {
            const response = await fetch('/api/v1/org/departments', {
                headers: {
                    'X-Tenant-Id': orgData?.tenantId || 'default',
                },
            });
            if (!response.ok) throw new Error('Failed to fetch departments');
            return response.json();
        },
        staleTime: 60000,
    });

    return {
        orgMetadata,
        health,
        departments,
        isLoading: isOrgLoading || isHealthLoading || isDeptLoading,
        error: orgError || healthError,
        setOrgData,
    };
}
