/**
 * AI Features Hook - GraphQL Version
 *
 * React hook for fetching AI features using GraphQL API.
 * Replaces direct Java backend calls with GraphQL queries.
 *
 * @module ai/hooks/useAI
 * @doc.type hook
 * @doc.purpose AI features via GraphQL
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';

/**
 * GraphQL client configuration
 */
interface GraphQLClientConfig {
    endpoint: string;
    headers?: Record<string, string>;
}

/**
 * Default GraphQL client
 */
const defaultConfig: GraphQLClientConfig = {
    endpoint: import.meta.env.VITE_GRAPHQL_ENDPOINT || 'http://localhost:7003/graphql',
};

/**
 * Execute GraphQL query
 */
async function graphql<T = unknown>(
    query: string,
    variables?: Record<string, unknown>,
    config: GraphQLClientConfig = defaultConfig
): Promise<T> {
    const response = await fetch(config.endpoint, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            ...config.headers,
        },
        body: JSON.stringify({ query, variables }),
    });

    if (!response.ok) {
        throw new Error(`GraphQL request failed: ${response.statusText}`);
    }

    const json = await response.json();

    if (json.errors) {
        throw new Error(json.errors[0].message);
    }

    return json.data;
}

/**
 * Hook for fetching AI insights
 */
export function useAIInsightsGraphQL(filter?: {
    type?: string[];
    category?: string[];
    severity?: string[];
    itemId?: string;
    limit?: number;
}) {
    return useQuery({
        queryKey: ['aiInsights', filter],
        queryFn: async () => {
            const data = await graphql<{ aiInsights: unknown[] }>(
                `
                query GetAIInsights($filter: InsightFilterInput) {
                    aiInsights(filter: $filter) {
                        id
                        type
                        category
                        title
                        description
                        confidence
                        severity
                        actionable
                        suggestedActions
                        relatedItems
                        expiresAt
                        createdAt
                        model
                        reasoning
                        itemId
                    }
                }
                `,
                { filter }
            );
            return data.aiInsights;
        },
        staleTime: 30000, // 30 seconds
        refetchInterval: 60000, // 1 minute
    });
}

/**
 * Hook for fetching predictions
 */
export function usePredictionsGraphQL(filter?: {
    type?: string[];
    phaseId?: string;
    minProbability?: number;
    limit?: number;
}) {
    return useQuery({
        queryKey: ['predictions', filter],
        queryFn: async () => {
            const data = await graphql<{ predictions: unknown[] }>(
                `
                query GetPredictions($filter: PredictionFilterInput) {
                    predictions(filter: $filter) {
                        id
                        type
                        probability
                        timeline
                        affectedItems
                        suggestedMitigation
                        model
                        confidence
                        createdAt
                        expiresAt
                        phaseId
                    }
                }
                `,
                { filter }
            );
            return data.predictions;
        },
        staleTime: 30000,
        refetchInterval: 60000,
    });
}

/**
 * Hook for fetching anomalies
 */
export function useAnomaliesGraphQL(filter?: {
    type?: string[];
    severity?: string[];
    acknowledged?: boolean;
    limit?: number;
}) {
    return useQuery({
        queryKey: ['anomalies', filter],
        queryFn: async () => {
            const data = await graphql<{ anomalies: unknown[] }>(
                `
                query GetAnomalies($filter: AnomalyFilterInput) {
                    anomalies(filter: $filter) {
                        id
                        type
                        severity
                        title
                        description
                        detectedAt
                        affectedItems
                        baselineValue
                        currentValue
                        deviationPercent
                        suggestedActions
                        acknowledged
                        acknowledgedAt
                        acknowledgedBy
                        resolvedAt
                        falsePositive
                        confidence
                        modelVersion
                    }
                }
                `,
                { filter }
            );
            return data.anomalies;
        },
        staleTime: 15000, // 15 seconds for anomalies (more critical)
        refetchInterval: 30000, // 30 seconds
    });
}

/**
 * Hook for acknowledging anomaly
 */
export function useAcknowledgeAnomaly() {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async (anomalyId: string) => {
            const data = await graphql<{ acknowledgeAnomaly: unknown }>(
                `
                mutation AcknowledgeAnomaly($anomalyId: String!) {
                    acknowledgeAnomaly(anomalyId: $anomalyId) {
                        id
                        acknowledged
                        acknowledgedAt
                        acknowledgedBy
                    }
                }
                `,
                { anomalyId }
            );
            return data.acknowledgeAnomaly;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['anomalies'] });
        },
    });
}

/**
 * Hook for AI Copilot
 */
export function useAICopilotGraphQL(sessionId: string) {
    const queryClient = useQueryClient();

    const sendMessage = useMutation({
        mutationFn: async (input: { message: string; context?: Record<string, unknown> }) => {
            const data = await graphql<{ sendCopilotMessage: unknown }>(
                `
                mutation SendCopilotMessage($input: CopilotMessageInput!) {
                    sendCopilotMessage(input: $input) {
                        sessionId
                        message
                        tokensUsed
                    }
                }
                `,
                {
                    input: {
                        sessionId,
                        ...input,
                    },
                }
            );
            return data.sendCopilotMessage;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['copilotSession', sessionId] });
        },
    });

    const session = useQuery({
        queryKey: ['copilotSession', sessionId],
        queryFn: async () => {
            const data = await graphql<{ copilotSession: unknown }>(
                `
                query GetCopilotSession($sessionId: String!) {
                    copilotSession(sessionId: $sessionId) {
                        id
                        userId
                        persona
                        messages
                        context
                        startedAt
                        endedAt
                        tokensUsed
                        costUSD
                        satisfactionRating
                    }
                }
                `,
                { sessionId }
            );
            return data.copilotSession;
        },
        enabled: !!sessionId,
    });

    const rateSession = useMutation({
        mutationFn: async (rating: number) => {
            const data = await graphql<{ rateCopilotSession: unknown }>(
                `
                mutation RateCopilotSession($sessionId: String!, $rating: Int!) {
                    rateCopilotSession(sessionId: $sessionId, rating: $rating) {
                        id
                        satisfactionRating
                        endedAt
                    }
                }
                `,
                { sessionId, rating }
            );
            return data.rateCopilotSession;
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['copilotSession', sessionId] });
        },
    });

    return {
        session: session.data,
        isLoading: session.isLoading,
        error: session.error,
        sendMessage: useCallback(
            (message: string, context?: Record<string, unknown>) =>
                sendMessage.mutateAsync({ message, context }),
            [sendMessage]
        ),
        rateSession: useCallback((rating: number) => rateSession.mutateAsync(rating), [rateSession]),
    };
}

/**
 * Hook for AI metrics summary
 */
export function useAIMetrics(timeRangeHours: number = 24) {
    return useQuery({
        queryKey: ['aiMetrics', timeRangeHours],
        queryFn: async () => {
            const data = await graphql<{ aiMetricsSummary: unknown }>(
                `
                query GetAIMetricsSummary($timeRangeHours: Int) {
                    aiMetricsSummary(timeRangeHours: $timeRangeHours) {
                        totalRequests
                        successRate
                        totalTokens
                        totalCost
                        avgLatency
                    }
                }
                `,
                { timeRangeHours }
            );
            return data.aiMetricsSummary;
        },
        staleTime: 60000, // 1 minute
    });
}
