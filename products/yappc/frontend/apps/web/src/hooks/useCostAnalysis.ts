import { useQuery } from '@apollo/client';
import { useCallback, useMemo } from 'react';
import { CostAnalysis, CostRecommendation, CostForecast, CostAlert } from '../types/cost';

/**
 * GraphQL query for cost analysis with filters
 *
 * @doc.type hook
 * @doc.purpose Provides cost analysis data with caching and refetching
 * @doc.layer frontend
 * @doc.pattern Custom Hook
 */

interface UseCostAnalysisOptions {
  readonly tenantId: string;
  readonly dateRange: { start: Date; end: Date };
  readonly provider?: string | null;
}

interface UseCostAnalysisReturn {
  readonly costAnalysis: CostAnalysis | undefined;
  readonly recommendations: ReadonlyArray<CostRecommendation> | undefined;
  readonly forecast: CostForecast | undefined;
  readonly alerts: ReadonlyArray<CostAlert> | undefined;
  readonly loading: boolean;
  readonly error: Error | undefined;
  readonly refetch: () => Promise<void>;
}

/**
 * Custom hook for cost analysis queries
 *
 * Features:
 * - Automatic query caching via Apollo Client
 * - Real-time data refresh
 * - Error handling with clear messages
 * - Support for date range and provider filtering
 * - Memoized return values for performance
 *
 * @example
 * const { costAnalysis, recommendations, loading } = useCostAnalysis({
 *   tenantId: 'tenant-123',
 *   dateRange: { start: new Date('2024-11-01'), end: new Date('2024-11-30') },
 *   provider: 'AWS'
 * });
 *
 * @param options - Configuration options
 * @returns Cost analysis data with loading/error states
 */
export const useCostAnalysis = (options: UseCostAnalysisOptions): UseCostAnalysisReturn => {
  const { tenantId, dateRange, provider } = options;

  // Query GraphQL endpoint for cost analysis
  const { data, loading, error, refetch: apolloRefetch } = useQuery(
    gql`
      query GetCostAnalysis(
        $tenantId: ID!
        $startDate: Date!
        $endDate: Date!
        $provider: String
      ) {
        costAnalysis(
          tenantId: $tenantId
          startDate: $startDate
          endDate: $endDate
          provider: $provider
        ) {
          totalCost
          currency
          period {
            start
            end
          }
          costByService {
            service
            amount
            percentage
          }
          costByProvider {
            provider
            amount
            percentage
          }
          dailyTrend {
            date
            amount
          }
          anomalies {
            date
            amount
            threshold
            severity
          }
          metrics {
            averageDailyCost
            minimumDailyCost
            maximumDailyCost
            standardDeviation
            percentageChange
          }
        }
        costRecommendations(
          tenantId: $tenantId
          limit: 10
          minSavings: 100
        ) {
          id
          title
          description
          savings
          annualSavings
          effort
          implementation
          resourceIds
          status
          estimatedMonthsSavings
        }
        costForecast(
          tenantId: $tenantId
          months: 12
          bufferPercent: 10
        ) {
          period {
            start
            end
          }
          projectedCost
          confidence
          monthlyProjections {
            month
            projectedCost
            confidenceLower
            confidenceUpper
          }
          seasonalityFactors {
            month
            factor
          }
          risks {
            description
            probability
            impact
          }
          recommendations {
            title
            savings
          }
        }
        costAlerts(tenantId: $tenantId, limit: 20) {
          id
          type
          severity
          message
          threshold
          currentValue
          triggeredAt
        }
      }
    `,
    {
      variables: {
        tenantId,
        startDate: dateRange.start.toISOString(),
        endDate: dateRange.end.toISOString(),
        provider: provider || null,
      },
      pollInterval: 30000, // Auto-refresh every 30 seconds
      fetchPolicy: 'cache-and-network', // Use cache but fetch fresh data
      errorPolicy: 'all', // Return partial data on error
    }
  );

  // Memoized refetch function
  const refetch = useCallback(async () => {
    await apolloRefetch({
      tenantId,
      startDate: dateRange.start.toISOString(),
      endDate: dateRange.end.toISOString(),
      provider: provider || null,
    });
  }, [apolloRefetch, tenantId, dateRange, provider]);

  // Memoize parsed results
  const results = useMemo<UseCostAnalysisReturn>(
    () => ({
      costAnalysis: data?.costAnalysis,
      recommendations: data?.costRecommendations,
      forecast: data?.costForecast,
      alerts: data?.costAlerts,
      loading,
      error: error ? new Error(error.message) : undefined,
      refetch,
    }),
    [data, loading, error, refetch]
  );

  return results;
};
