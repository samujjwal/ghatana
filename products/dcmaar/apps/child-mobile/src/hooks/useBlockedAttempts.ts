import type { Alert } from '@/types';
import { useChildBlocks } from './useChildUsage';

interface UseBlockedAttemptsResult {
    data: Alert[];
    isLoading: boolean;
    isError: boolean;
    error: unknown;
    refetch: () => Promise<unknown> | void;
}

export function useBlockedAttempts(): UseBlockedAttemptsResult {
    const blocksQuery = useChildBlocks();
    const { data, isLoading, isError, error, refetch } = blocksQuery;

    const blockedAlerts: Alert[] = (data ?? []).filter((alert) => alert.type === 'policy_violation');

    return {
        data: blockedAlerts,
        isLoading,
        isError,
        error,
        refetch,
    };
}
