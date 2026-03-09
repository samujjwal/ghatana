/**
 * Example Plugin: Custom Metric Widget
 *
 * <p><b>Purpose</b><br>
 * Demonstrates plugin system with a working metric widget example.
 * Shows how to create plugins with configuration, permissions, and slot rendering.
 *
 * <p><b>Features</b><br>
 * - Configurable threshold and format
 * - Permission-based visibility
 * - Slot-based rendering (dashboard.metrics)
 * - Live data updates with React Query
 * - Error handling and loading states
 *
 * <p><b>Registration</b><br>
 * <pre>{@code
 * import { pluginRegistry } from '@/lib/persona/PluginRegistry';
 * import { customMetricWidgetManifest } from '@/plugins/CustomMetricWidget';
 *
 * pluginRegistry.register(
 *   customMetricWidgetManifest,
 *   () => import('@/plugins/CustomMetricWidget')
 * );
 * }</pre>
 *
 * @doc.type plugin
 * @doc.purpose Example metric widget plugin
 * @doc.layer product
 * @doc.pattern Plugin
 */

import { useQuery } from '@tanstack/react-query';
import type { PluginManifest } from '@/schemas/persona.schema';

/**
 * Plugin configuration interface
 */
export interface CustomMetricWidgetConfig {
    /**
     * Metric data key (e.g., 'deployments.count')
     */
    metricKey: string;

    /**
     * Display title
     */
    title: string;

    /**
     * Value format
     */
    format?: 'number' | 'percentage' | 'currency' | 'duration';

    /**
     * Warning threshold
     */
    threshold?: {
        warning: number;
        critical: number;
    };

    /**
     * Icon name
     */
    icon?: string;

    /**
     * Color theme
     */
    color?: string;

    /**
     * Refresh interval in milliseconds
     */
    refreshInterval?: number;
}

/**
 * Plugin context (passed by PluginSlot)
 */
export interface PluginContext {
    userId?: string;
    tenantId?: string;
    [key: string]: unknown;
}

/**
 * Plugin props
 */
export interface CustomMetricWidgetProps {
    config: CustomMetricWidgetConfig;
    context?: PluginContext;
    manifest: PluginManifest;
}

/**
 * Format value based on format type
 */
function formatValue(value: number, format: string = 'number'): string {
    switch (format) {
        case 'percentage':
            return `${value.toFixed(1)}%`;
        case 'currency':
            return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value);
        case 'duration':
            return `${value}ms`;
        default:
            return value.toLocaleString();
    }
}

/**
 * Get status color based on threshold
 */
function getStatusColor(value: number, threshold?: { warning: number; critical: number }): string {
    if (!threshold) return 'text-slate-700 dark:text-neutral-300';

    if (value >= threshold.critical) {
        return 'text-red-600 dark:text-rose-400';
    } else if (value >= threshold.warning) {
        return 'text-yellow-600 dark:text-yellow-400';
    }
    return 'text-green-600 dark:text-green-400';
}

/**
 * CustomMetricWidget Component
 *
 * Displays a metric value with optional threshold indicators.
 */
export function CustomMetricWidget({ config, context }: CustomMetricWidgetProps) {
    // Fetch metric data (mock for now, replace with real API)
    const { data, isLoading, error } = useQuery({
        queryKey: ['metric', config.metricKey, context?.userId],
        queryFn: async () => {
            // Mock data - replace with real API call
            await new Promise((resolve) => setTimeout(resolve, 500));
            return {
                value: Math.floor(Math.random() * 200),
                trend: Math.random() > 0.5 ? 'up' : 'down',
                change: Math.floor(Math.random() * 20),
            };
        },
        refetchInterval: config.refreshInterval ?? 30000, // Default 30s
    });

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-full">
                <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600"></div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="flex items-center justify-center h-full text-red-600 dark:text-rose-400 text-sm">
                Failed to load metric
            </div>
        );
    }

    const statusColor = getStatusColor(data?.value ?? 0, config.threshold);
    const formattedValue = formatValue(data?.value ?? 0, config.format);

    return (
        <div className="flex flex-col h-full p-4">
            {/* Header */}
            <div className="flex items-center gap-2 mb-2">
                {config.icon && (
                    <span className="text-2xl" role="img" aria-label={config.icon}>
                        {config.icon}
                    </span>
                )}
                <h3 className="text-sm font-medium text-slate-600 dark:text-neutral-400">{config.title}</h3>
            </div>

            {/* Value */}
            <div className={`text-3xl font-bold ${statusColor} mb-2`}>{formattedValue}</div>

            {/* Trend */}
            {data && (
                <div className="flex items-center gap-1 text-xs">
                    {data.trend === 'up' ? (
                        <svg className="w-4 h-4 text-green-500" fill="currentColor" viewBox="0 0 20 20">
                            <path
                                fillRule="evenodd"
                                d="M5.293 9.707a1 1 0 010-1.414l4-4a1 1 0 011.414 0l4 4a1 1 0 01-1.414 1.414L11 7.414V15a1 1 0 11-2 0V7.414L6.707 9.707a1 1 0 01-1.414 0z"
                                clipRule="evenodd"
                            />
                        </svg>
                    ) : (
                        <svg className="w-4 h-4 text-red-500" fill="currentColor" viewBox="0 0 20 20">
                            <path
                                fillRule="evenodd"
                                d="M14.707 10.293a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 111.414-1.414L9 12.586V5a1 1 0 012 0v7.586l2.293-2.293a1 1 0 011.414 0z"
                                clipRule="evenodd"
                            />
                        </svg>
                    )}
                    <span className={data.trend === 'up' ? 'text-green-600' : 'text-red-600'}>
                        {data.change}% vs last period
                    </span>
                </div>
            )}

            {/* Threshold indicator */}
            {config.threshold && (
                <div className="mt-auto pt-2 border-t border-slate-200 dark:border-neutral-600">
                    <div className="text-xs text-slate-500 dark:text-neutral-400">
                        Warning: {formatValue(config.threshold.warning, config.format)} | Critical:{' '}
                        {formatValue(config.threshold.critical, config.format)}
                    </div>
                </div>
            )}
        </div>
    );
}

/**
 * Plugin manifest for registration
 */
export const customMetricWidgetManifest: PluginManifest = {
    id: 'custom-metric-widget',
    name: 'Custom Metric Widget',
    version: '1.0.0',
    type: 'metric',
    author: 'Ghatana Team',
    description: 'Displays a customizable metric with threshold indicators',
    slots: ['dashboard.metrics', 'dashboard.overview'],
    permissions: ['metrics.read'],
    priority: 0,
    enabled: true,
    config: {
        metricKey: 'deployments.count',
        title: 'Deployments',
        format: 'number',
        threshold: {
            warning: 80,
            critical: 100,
        },
        icon: '🚀',
        color: 'blue',
        refreshInterval: 30000,
    },
    component: 'lazy', // Loaded via pluginRegistry.loadComponent()
};

// Default export for lazy loading
export default CustomMetricWidget;
