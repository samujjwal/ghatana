import { useQuery } from '@tanstack/react-query';

/**
 * Natural language query hook for dashboard analytics.
 *
 * <p><b>Purpose</b><br>
 * Enables users to ask questions about dashboard data in natural language.
 * Converts NL queries to analytics queries and returns interpreted results.
 *
 * <p><b>Example</b><br>
 * <pre>{@code
 * const { data, isLoading } = useNLQuery("Why is MTTR increasing?");
 * // Returns: Analysis of MTTR trends with root cause insights
 * }</pre>
 *
 * @param query - Natural language question about metrics
 * @param options - React Query options (enabled, refetchInterval, etc.)
 * @returns Query result with analysis and recommendations
 *
 * @doc.type hook
 * @doc.purpose Natural language query interface
 * @doc.layer product
 * @doc.pattern Query Hook
 */
export interface NLQueryResponse {
    question: string;
    analysis: string;
    confidence: number;
    suggestedMetrics: string[];
    recommendations: string[];
    relatedQueries: string[];
}

export interface UseNLQueryOptions {
    enabled?: boolean;
    refetchInterval?: number;
}

export function useNLQuery(query: string | null, options: UseNLQueryOptions = {}) {
    const { enabled = !!query, refetchInterval = 30000 } = options;

    return useQuery<NLQueryResponse>({
        queryKey: ['nlQuery', query],
        queryFn: async () => {
            if (!query) {
                throw new Error('Query is required');
            }

            // TODO: Replace with actual API endpoint when available
            // POST /api/v1/analytics/nl-query
            // Headers: Content-Type: application/json, X-Correlation-ID: uuid
            // Body: { question: string, tenant: string, timeRange: string }

            // Placeholder response for development
            return {
                question: query,
                analysis: `Analysis for: "${query}". Check metrics dashboard for detailed trends.`,
                confidence: 0.85,
                suggestedMetrics: ['MTTR', 'Lead Time', 'Deployment Frequency'],
                recommendations: [
                    'Investigate recent infrastructure changes',
                    'Review incident logs for patterns',
                    'Analyze deployment pipeline metrics'
                ],
                relatedQueries: [
                    'What caused the last incident?',
                    'Show deployment frequency trend',
                    'Compare MTTR across teams'
                ],
            };
        },
        enabled,
        staleTime: 5 * 60 * 1000, // 5 minutes
        gcTime: 10 * 60 * 1000, // 10 minutes
        retry: 2,
        refetchInterval,
    });
}
