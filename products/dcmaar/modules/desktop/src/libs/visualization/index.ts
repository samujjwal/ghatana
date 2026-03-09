/**
 * Visualization Library
 * 
 * Provides reusable visualization components for charts, dashboards, and data display.
 * Follows DCMaar design system and performance best practices.
 * 
 * @module visualization
 */

export * from './charts';
export { DashboardLayout, DashboardManager, Widget } from './dashboard';
export { HistoricalChart, HistoricalDataService, historicalDataService } from './historical';
export type { ChartConfig, TimeSeriesData, DataPoint, ExportOptions, Widget as WidgetType } from './types';
export * from './utils';
