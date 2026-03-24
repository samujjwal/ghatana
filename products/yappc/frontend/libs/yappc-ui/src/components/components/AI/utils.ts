/**
 * AI Insights Dashboard - Utility Functions
 * @module components/AI/utils
 */

import type { RecommendationCounts, RecommendationsByType } from './types';
import type { OptimizationRecommendation } from '../../hooks/ai/useAIInsights/types';

/**
 * Utility functions for AI Insights Dashboard
 *
 * @class AIInsightsDashboardUtils
 */
class AIInsightsDashboardUtils {
  /**
   * Get color for priority level
   *
   * @param priority - Priority level
   * @returns MUI color prop value
   *
   * @example
   * ```typescript
   * const color = AIInsightsDashboardUtils.getPriorityColor('critical');
   * // Returns: 'error'
   * ```
   */
  static getPriorityColor(
    priority: OptimizationRecommendation['priority']
  ): 'error' | 'warning' | 'info' | 'default' {
    switch (priority) {
      case 'critical':
        return 'error';
      case 'high':
        return 'warning';
      case 'medium':
        return 'info';
      default:
        return 'default';
    }
  }

  /**
   * Get color for risk level
   *
   * @param riskLevel - Risk level
   * @returns MUI color value
   */
  static getRiskColor(riskLevel: string): string {
    switch (riskLevel) {
      case 'critical':
        return '#d32f2f'; // error
      case 'high':
        return '#f57c00'; // warning
      case 'medium':
        return '#1976d2'; // info
      default:
        return '#388e3c'; // success
    }
  }

  /**
   * Group recommendations by type
   *
   * @param recommendations - Array of recommendations
   * @returns Recommendations grouped by type
   *
   * @example
   * ```typescript
   * const grouped = AIInsightsDashboardUtils.groupByType(recommendations);
   * // Returns: { performance: [...], resource: [...] }
   * ```
   */
  static groupByType(
    recommendations: OptimizationRecommendation[]
  ): RecommendationsByType {
    const grouped: RecommendationsByType = {};

    recommendations.forEach((rec) => {
      if (!grouped[rec.type]) {
        grouped[rec.type] = [];
      }
      grouped[rec.type].push(rec);
    });

    return grouped;
  }

  /**
   * Count recommendations by priority level
   *
   * @param recommendations - Array of recommendations
   * @returns Object with counts per priority level
   *
   * @example
   * ```typescript
   * const counts = AIInsightsDashboardUtils.countByPriority(recommendations);
   * // Returns: { critical: 2, high: 5, medium: 3, low: 1, total: 11 }
   * ```
   */
  static countByPriority(
    recommendations: OptimizationRecommendation[]
  ): RecommendationCounts {
    return {
      critical: recommendations.filter((r) => r.priority === 'critical').length,
      high: recommendations.filter((r) => r.priority === 'high').length,
      medium: recommendations.filter((r) => r.priority === 'medium').length,
      low: recommendations.filter((r) => r.priority === 'low').length,
      total: recommendations.length,
    };
  }

  /**
   * Calculate average model accuracy from multiple models
   *
   * @param models - Array of models with accuracy
   * @returns Average accuracy as percentage (0-100)
   *
   * @example
   * ```typescript
   * const avg = AIInsightsDashboardUtils.getAverageAccuracy(models);
   * // Returns: 92.5
   * ```
   */
  static getAverageAccuracy(models: Array<{ accuracy: number }>): number {
    if (models.length === 0) return 0;
    const sum = models.reduce((acc, m) => acc + m.accuracy, 0);
    return (sum / models.length) * 100;
  }

  /**
   * Format date to localized string
   *
   * @param date - Date to format
   * @returns Localized date string
   *
   * @example
   * ```typescript
   * const formatted = AIInsightsDashboardUtils.formatDate(new Date());
   * // Returns: "10/16/2025, 12:30:45 PM"
   * ```
   */
  static formatDate(date: Date): string {
    return date.toLocaleString();
  }

  /**
   * Truncate text to specific length with ellipsis
   *
   * @param text - Text to truncate
   * @param maxLength - Maximum length before truncation
   * @returns Truncated text with ellipsis if needed
   */
  static truncateText(text: string, maxLength: number): string {
    if (text.length <= maxLength) return text;
    return `${text.substring(0, maxLength)}...`;
  }

  /**
   * Convert confidence decimal to percentage string
   *
   * @param confidence - Confidence value (0-1)
   * @returns Formatted percentage string
   *
   * @example
   * ```typescript
   * const percent = AIInsightsDashboardUtils.confidenceToPercent(0.95);
   * // Returns: "95%"
   * ```
   */
  static confidenceToPercent(confidence: number): string {
    return `${(confidence * 100).toFixed(0)}%`;
  }
}

export { AIInsightsDashboardUtils };
