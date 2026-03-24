/**
 * AI Insights Dashboard - Type Definitions
 * @module components/AI/types
 */

import type { OptimizationRecommendation } from '../../hooks/ai/useAIInsights/types';

/**
 * Props for AIInsightsDashboard component
 *
 * @interface AIInsightsDashboardProps
 * @property {string} [title] - Dashboard title
 * @property {boolean} [showAllSections] - Whether to show all sections
 * @property {number} [refreshInterval] - Auto-refresh interval in milliseconds
 * @property {string} [className] - Custom CSS classes
 *
 * @example
 * ```typescript
 * <AIInsightsDashboard
 *   title="AI Insights"
 *   refreshInterval={60000}
 *   showAllSections={true}
 * />
 * ```
 */
export interface AIInsightsDashboardProps {
  /** Dashboard title */
  title?: string;
  /** Whether to show all sections */
  showAllSections?: boolean;
  /** Auto-refresh interval in milliseconds */
  refreshInterval?: number;
  /** Custom styling */
  className?: string;
}

/**
 * Props for RecommendationCard component
 *
 * @interface RecommendationCardProps
 * @property {OptimizationRecommendation} recommendation - Recommendation data
 * @property {(id: string) => void} onImplement - Callback when implement button clicked
 * @property {(id: string) => void} onDismiss - Callback when dismiss button clicked
 * @property {boolean} [isImplementing] - Whether recommendation is being implemented
 */
export interface RecommendationCardProps {
  recommendation: OptimizationRecommendation;
  onImplement: (id: string) => void;
  onDismiss: (id: string) => void;
  isImplementing?: boolean;
}

/**
 * Recommendation counts grouped by priority level
 *
 * @interface RecommendationCounts
 * @property {number} critical - Count of critical priority recommendations
 * @property {number} high - Count of high priority recommendations
 * @property {number} medium - Count of medium priority recommendations
 * @property {number} low - Count of low priority recommendations
 * @property {number} total - Total count of all recommendations
 */
export interface RecommendationCounts {
  critical: number;
  high: number;
  medium: number;
  low: number;
  total: number;
}

/**
 * Recommendations grouped by type
 *
 * @typedef {Record<string, OptimizationRecommendation[]>} RecommendationsByType
 */
export type RecommendationsByType = Record<
  string,
  OptimizationRecommendation[]
>;

/**
 * Tab indices for dashboard tabs
 *
 * @enum {number}
 */
export enum DashboardTab {
  Recommendations = 0,
  Predictions = 1,
  RiskAssessment = 2,
  PatternAnalysis = 3,
}
