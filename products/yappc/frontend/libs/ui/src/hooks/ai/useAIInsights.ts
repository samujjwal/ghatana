// Re-export the modular implementation (keeps existing import paths stable)
// Note: reference the folder index explicitly to avoid resolving to this file
export { useAIInsights } from './useAIInsights/index';

// Re-export types for convenience
export type {
  AIInsightsOptions,
  UseAIInsightsResult,
} from './useAIInsights/types';
