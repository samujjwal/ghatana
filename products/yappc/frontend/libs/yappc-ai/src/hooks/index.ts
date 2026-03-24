/**
 * React AI Hooks
 *
 * Custom React hooks for AI-powered features.
 * These hooks provide convenient access to AI agents via the Java backend.
 *
 * @module ai/hooks
 * @doc.type module
 * @doc.purpose React hooks for AI agent integration
 * @doc.layer product
 * @doc.pattern Hook
 */

export * from './useAICopilot';
export * from './useAIInsights';
export * from './usePredictions';
export * from './useAnomalyAlerts';
export * from './useSemanticSearch';
export * from './useRecommendations';

// GraphQL-based hooks (recommended)
export * from './useAI.graphql';

// WebSocket-based real-time hooks
export * from './useWebSocket';
