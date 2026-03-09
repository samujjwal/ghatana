/**
 * KPICard Component Types
 *
 * Type definitions for the KPICard component - a card component for displaying
 * Key Performance Indicators with optional trend indicators and progress tracking.
 *
 * @module DevSecOps/KPICard/types
 */

/**
 * Trend direction indicator
 */
export type TrendDirection = 'up' | 'down' | 'neutral';

/**
 * Trend configuration for KPI visualization
 *
 * @property direction - Direction of the trend (up, down, or neutral)
 * @property value - Percentage change value (can be positive or negative)
 *
 * @example
 * ```typescript
 * const trend: KPITrend = {
 *   direction: 'up',
 *   value: 12.5 // +12.5% increase
 * };
 * ```
 */
export interface KPITrend {
  direction: TrendDirection;
  value: number;
}

/**
 * Props for the KPICard component
 *
 * @property title - The metric name/title (e.g., "System Uptime")
 * @property value - The current value of the KPI (number or formatted string)
 * @property unit - Optional unit suffix (e.g., "%", "ms", "/week")
 * @property target - Optional target value for progress calculation
 * @property trend - Optional trend indicator with direction and percentage change
 * @property showProgress - Whether to display a progress bar (requires target)
 * @property sparklineData - Optional array of historical data points for sparkline chart
 *
 * @example
 * ```typescript
 * <KPICard
 *   title="System Uptime"
 *   value={99.8}
 *   unit="%"
 *   trend={{ direction: 'up', value: 2.3 }}
 *   showProgress
 *   target={99.9}
 * />
 * ```
 */
export interface KPICardProps {
  /** The metric name */
  title: string;

  /** The current value */
  value: number | string;

  /** Target value for progress tracking */
  target?: number;

  /** Unit suffix (%, ms, etc.) */
  unit?: string;

  /** Trend indicator configuration */
  trend?: KPITrend;

  /** Show progress bar */
  showProgress?: boolean;

  /** Historical data for sparkline */
  sparklineData?: number[];
}
