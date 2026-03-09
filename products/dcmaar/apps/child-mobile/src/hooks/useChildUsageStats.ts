import { useEffect, useState } from 'react';
import type { ChildUsageStats } from '@/native/usageTrackerBridge';
import { getTodayUsageStats } from '@/native/usageTrackerBridge';

interface UseChildUsageStatsResult {
    stats: ChildUsageStats | null;
    totalScreenTimeSeconds: number;
    totalScreenTimeMinutes: number;
    deviceCount: number;
    isLoading: boolean;
    error: Error | null;
}

export function useChildUsageStats(): UseChildUsageStatsResult {
    const [stats, setStats] = useState<ChildUsageStats | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<Error | null>(null);

    useEffect(() => {
        let isMounted = true;

        const fetchStats = async () => {
            try {
                const result = await getTodayUsageStats();
                if (!isMounted) return;
                setStats(result ?? null);
            } catch (err) {
                if (!isMounted) return;
                const errorInstance = err instanceof Error ? err : new Error('Unknown error');
                setError(errorInstance);
            } finally {
                if (isMounted) {
                    setIsLoading(false);
                }
            }
        };

        fetchStats();

        return () => {
            isMounted = false;
        };
    }, []);

    const totalScreenTimeSeconds = stats?.totalScreenTimeSeconds ?? 0;
    const totalScreenTimeMinutes = Math.round(totalScreenTimeSeconds / 60);
    const deviceCount = stats?.deviceIds.length ?? 0;

    return {
        stats,
        totalScreenTimeSeconds,
        totalScreenTimeMinutes,
        deviceCount,
        isLoading,
        error,
    };
}
