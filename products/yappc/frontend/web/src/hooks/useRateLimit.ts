/**
 * Rate Limit Custom Hook
 *
 * <p><b>Purpose</b><br>
 * Custom React hook for managing rate limit operations including status
 * tracking, tier management, and upgrade workflows. Integrates with React
 * Query for automatic caching and refetching.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const {
 *   status,
 *   isLoading,
 *   requestUpgrade,
 *   resetLimit,
 * } = useRateLimit();
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Rate limit data management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, useCallback, useEffect } from 'react';

/**
 * Rate limit status interface
 */
export interface RateLimitStatus {
  identifier: string;
  tier: string;
  used: number;
  limit: number;
  remaining: number;
  percentage: number;
  resetTime: Date;
  isLimited: boolean;
  lastRequestAt?: Date;
  statusColor: string;
  statusLabel: string;
}

/**
 * Rate limit tier info
 */
export interface RateLimitTier {
  name: string;
  description: string;
  requestsPerHour: number;
  requestsPerDay: number;
  burstSize: number;
  monthlyCost: number;
  features: string[];
}

/**
 * Upgrade request
 */
export interface UpgradeRequest {
  id: string;
  userId: string;
  requestedTier: string;
  currentTier: string;
  status: string;
  createdAt: Date;
  processedAt?: Date;
}

/**
 * Hook options
 */
interface UseRateLimitOptions {
  userId?: string;
  autoRefresh?: boolean;
  refreshInterval?: number;
  onLimitExceeded?: () => void;
  onApproachingLimit?: (percentage: number) => void;
}

/**
 * useRateLimit custom hook
 */
export const useRateLimit = (options: UseRateLimitOptions = {}) => {
  const {
    userId,
    autoRefresh = true,
    refreshInterval = 10000, // 10 seconds
    onLimitExceeded,
    onApproachingLimit,
  } = options;

  const queryClient = useQueryClient();
  const [previousPercentage, setPreviousPercentage] = useState(0);

  /**
   * Fetch current user's rate limit status
   */
  const {
    data: status,
    isLoading,
    error,
    refetch,
  } = useQuery<RateLimitStatus>({
    queryKey: ['rateLimitStatus', userId || 'me'],
    queryFn: async () => {
      const url = userId
        ? `/api/rate-limit/status/${userId}`
        : '/api/rate-limit/status/me';
      const response = await fetch(url);
      if (!response.ok) throw new Error('Failed to fetch rate limit status');
      return response.json();
    },
    refetchInterval: autoRefresh ? refreshInterval : false,
  });

  /**
   * Fetch available tiers
   */
  const {
    data: tiers,
    isLoading: tiersLoading,
  } = useQuery<RateLimitTier[]>({
    queryKey: ['rateLimitTiers'],
    queryFn: async () => {
      const response = await fetch('/api/rate-limit/tiers');
      if (!response.ok) throw new Error('Failed to fetch tiers');
      return response.json();
    },
  });

  /**
   * Fetch user's upgrade requests
   */
  const {
    data: upgradeRequests,
    isLoading: requestsLoading,
  } = useQuery<UpgradeRequest[]>({
    queryKey: ['upgradeRequests', userId],
    queryFn: async () => {
      const response = await fetch('/api/rate-limit/upgrade-requests');
      if (!response.ok) throw new Error('Failed to fetch upgrade requests');
      return response.json();
    },
    enabled: !!userId,
  });

  /**
   * Request tier upgrade mutation
   */
  const requestUpgrade = useMutation({
    mutationFn: async (requestedTier: string) => {
      const response = await fetch('/api/rate-limit/upgrade', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ requestedTier }),
      });
      if (!response.ok) throw new Error('Failed to request upgrade');
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['upgradeRequests'] });
      queryClient.invalidateQueries({ queryKey: ['rateLimitStatus'] });
    },
  });

  /**
   * Reset rate limit mutation (admin only)
   */
  const resetLimit = useMutation({
    mutationFn: async (targetUserId?: string) => {
      const response = await fetch('/api/rate-limit/reset', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: targetUserId }),
      });
      if (!response.ok) throw new Error('Failed to reset limit');
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rateLimitStatus'] });
    },
  });

  /**
   * Downgrade to free tier mutation
   */
  const downgradeToFree = useMutation({
    mutationFn: async () => {
      const response = await fetch('/api/rate-limit/downgrade', {
        method: 'POST',
      });
      if (!response.ok) throw new Error('Failed to downgrade');
      return response.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rateLimitStatus'] });
    },
  });

  /**
   * Triggers callbacks based on status changes
   */
  useEffect(() => {
    if (!status) return;

    // Check if limit was just exceeded
    if (status.isLimited && !previousPercentage) {
      onLimitExceeded?.();
    }

    // Check if approaching limit (80% threshold)
    if (
      status.percentage >= 80 &&
      previousPercentage < 80 &&
      !status.isLimited
    ) {
      onApproachingLimit?.(status.percentage);
    }

    setPreviousPercentage(status.percentage);
  }, [status, previousPercentage, onLimitExceeded, onApproachingLimit]);

  /**
   * Checks if user can upgrade
   */
  const canUpgrade = useCallback(() => {
    if (!status) return false;
    return status.tier.toLowerCase() !== 'enterprise';
  }, [status]);

  /**
   * Gets next tier recommendation
   */
  const getRecommendedTier = useCallback(() => {
    if (!status || !tiers) return null;

    const currentTierIndex = tiers.findIndex(
      (t) => t.name.toLowerCase() === status.tier.toLowerCase()
    );

    if (currentTierIndex === -1 || currentTierIndex === tiers.length - 1) {
      return null;
    }

    return tiers[currentTierIndex + 1];
  }, [status, tiers]);

  /**
   * Calculates time until reset
   */
  const getTimeUntilReset = useCallback(() => {
    if (!status) return null;

    const now = new Date();
    const reset = new Date(status.resetTime);
    const diff = reset.getTime() - now.getTime();

    if (diff <= 0) return null;

    const minutes = Math.floor(diff / 60000);
    const seconds = Math.floor((diff % 60000) / 1000);

    return { minutes, seconds, totalSeconds: Math.floor(diff / 1000) };
  }, [status]);

  /**
   * Checks if approaching limit
   */
  const isApproachingLimit = useCallback(
    (threshold: number = 80) => {
      return status ? status.percentage >= threshold && !status.isLimited : false;
    },
    [status]
  );

  /**
   * Gets usage summary
   */
  const getUsageSummary = useCallback(() => {
    if (!status) return null;

    return {
      used: status.used,
      limit: status.limit,
      remaining: status.remaining,
      percentage: status.percentage,
      isLimited: status.isLimited,
      statusColor: status.statusColor,
      statusLabel: status.statusLabel,
    };
  }, [status]);

  /**
   * Gets pending upgrade request
   */
  const getPendingUpgradeRequest = useCallback(() => {
    return upgradeRequests?.find((r) => r.status === 'pending');
  }, [upgradeRequests]);

  /**
   * Checks if upgrade is pending
   */
  const hasUpgradePending = useCallback(() => {
    return !!getPendingUpgradeRequest();
  }, [getPendingUpgradeRequest]);

  return {
    // Data
    status,
    tiers: tiers || [],
    upgradeRequests: upgradeRequests || [],
    isLoading: isLoading || tiersLoading,
    requestsLoading,
    error,

    // Actions
    refetch,
    requestUpgrade: requestUpgrade.mutate,
    resetLimit: resetLimit.mutate,
    downgradeToFree: downgradeToFree.mutate,

    // Mutation states
    isRequestingUpgrade: requestUpgrade.isPending,
    isResetting: resetLimit.isPending,
    isDowngrading: downgradeToFree.isPending,

    // Helpers
    canUpgrade: canUpgrade(),
    getRecommendedTier: getRecommendedTier(),
    getTimeUntilReset: getTimeUntilReset(),
    isApproachingLimit,
    getUsageSummary: getUsageSummary(),
    getPendingUpgradeRequest: getPendingUpgradeRequest(),
    hasUpgradePending: hasUpgradePending(),
  };
};

export default useRateLimit;

