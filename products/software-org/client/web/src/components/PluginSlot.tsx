/**
 * PluginSlot Component - Lazy Plugin Renderer
 *
 * <p><b>Purpose</b><br>
 * Renders plugins by slot with lazy loading, permission filtering,
 * error boundaries, and loading states. Integrates with PluginRegistry.
 *
 * <p><b>Features</b><br>
 * - Lazy load plugin components on-demand
 * - Filter by user permissions
 * - Error boundaries for plugin failures
 * - Suspense loading states
 * - Pass config and context to plugins
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { PluginSlot } from '@/components/PluginSlot';
 *
 * // Render all plugins in a slot
 * <PluginSlot slot="dashboard.metrics" />
 *
 * // Render specific plugin with config
 * <PluginSlot
 *   pluginId="custom-metric"
 *   config={{ threshold: 100 }}
 *   context={{ userId: '123' }}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Lazy plugin renderer with error boundaries
 * @doc.layer product
 * @doc.pattern Container
 */

import { Suspense, useState, useEffect, ComponentType, useCallback, useMemo } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { pluginRegistry, type PluginEvent, type PluginEventListener, type RegisteredPlugin } from '@/lib/persona/PluginRegistry';
import type { PluginManifest } from '@/schemas/persona.schema';

/**
 * Props for PluginSlot component
 */
export interface PluginSlotProps {
    /**
     * Slot name to render plugins from (e.g., 'dashboard.metrics')
     * If provided, renders all plugins in slot.
     * Mutually exclusive with pluginId.
     */
    slot?: string;

    /**
     * Specific plugin ID to render
     * If provided, renders only this plugin.
     * Mutually exclusive with slot.
     */
    pluginId?: string;

    /**
     * User permissions for filtering plugins
     * If not provided, uses all enabled plugins.
     */
    userPermissions?: string[];

    /**
     * Configuration to pass to plugin(s)
     */
    config?: Record<string, unknown>;

    /**
     * Additional context to pass to plugin(s)
     */
    context?: Record<string, unknown>;

    /**
     * Custom loading component
     */
    loadingComponent?: ComponentType;

    /**
     * Custom error fallback component
     */
    errorFallback?: ComponentType<{ error: Error; resetErrorBoundary: () => void }>;

    /**
     * CSS class name for container
     */
    className?: string;
}

/**
 * Default loading spinner
 */
function DefaultLoadingSpinner() {
    return (
        <div className="flex items-center justify-center p-8">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
        </div>
    );
}

/**
 * Default error fallback
 */
function DefaultErrorFallback({ error, resetErrorBoundary }: { error: Error; resetErrorBoundary: () => void }) {
    return (
        <div className="bg-red-50 dark:bg-rose-600/30 border border-red-200 dark:border-red-800 rounded-lg p-4">
            <div className="flex items-start gap-3">
                <svg
                    className="w-5 h-5 text-red-600 dark:text-rose-400 flex-shrink-0 mt-0.5"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                >
                    <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                    />
                </svg>
                <div className="flex-1">
                    <h3 className="text-sm font-medium text-red-800 dark:text-red-200">Plugin Error</h3>
                    <p className="text-xs text-red-700 dark:text-red-300 mt-1">{error.message}</p>
                    <button
                        onClick={resetErrorBoundary}
                        className="text-xs text-red-600 dark:text-rose-400 hover:underline mt-2"
                    >
                        Try again
                    </button>
                </div>
            </div>
        </div>
    );
}

/**
 * Single plugin renderer with lazy loading
 */
function PluginRenderer({
    plugin,
    config,
    context,
}: {
    plugin: RegisteredPlugin & { id: string };
    config?: Record<string, unknown>;
    context?: Record<string, unknown>;
}) {
    const [Component, setComponent] = useState<ComponentType<any> | null>(null);
    const [error, setError] = useState<Error | null>(null);

    useEffect(() => {
        let mounted = true;

        const loadPlugin = async () => {
            try {
                const component = await pluginRegistry.loadComponent(plugin.id);
                if (mounted && component) {
                    setComponent(() => component);
                }
            } catch (err) {
                if (mounted) {
                    setError(err instanceof Error ? err : new Error('Failed to load plugin'));
                }
            }
        };

        loadPlugin();

        return () => {
            mounted = false;
        };
    }, [plugin.id]);

    if (error) {
        throw error; // Will be caught by ErrorBoundary
    }

    if (!Component) {
        return <DefaultLoadingSpinner />;
    }

    return <Component config={config} context={context} manifest={plugin} />;
}

/**
 * PluginSlot - Renders plugins with lazy loading and error handling
 */
export function PluginSlot({
    slot,
    pluginId,
    userPermissions,
    config,
    context,
    loadingComponent: LoadingComponent = DefaultLoadingSpinner,
    errorFallback: ErrorFallback = DefaultErrorFallback,
    className = '',
}: PluginSlotProps) {
    const [plugins, setPlugins] = useState<Array<RegisteredPlugin & { id: string }>>([]);

    // Stabilize userPermissions array reference to prevent infinite re-renders
    // Use JSON serialization for stable comparison (avoids array recreation issues)
    const permissionsKey = useMemo(
        () => (userPermissions ? JSON.stringify(userPermissions.sort()) : null),
        [userPermissions]
    );

    // Load plugins on mount
    useEffect(() => {
        if (pluginId) {
            // Load specific plugin
            const plugin = pluginRegistry.get(pluginId);
            if (plugin) {
                setPlugins([{ ...plugin, id: pluginId }]);
            }
        } else if (slot) {
            // Load all plugins in slot
            const slotPlugins = pluginRegistry.getBySlot(slot);
            const enabledPlugins = userPermissions
                ? pluginRegistry.getEnabled(userPermissions).filter((p) => slotPlugins?.some((s) => s.id === p.id))
                : slotPlugins;

            setPlugins(enabledPlugins.map((p) => ({ ...p, id: p.id })));
        }
    }, [slot, pluginId, permissionsKey, userPermissions]);

    if (plugins.length === 0) {
        return null; // No plugins to render
    }

    return (
        <div className={`plugin-slot ${className}`}>
            {plugins.map((plugin) => (
                <ErrorBoundary
                    key={plugin.id}
                    FallbackComponent={ErrorFallback}
                    onReset={() => {
                        // Reload plugin on reset
                        window.location.reload();
                    }}
                >
                    <Suspense fallback={<LoadingComponent />}>
                        <PluginRenderer plugin={plugin} config={config} context={context} />
                    </Suspense>
                </ErrorBoundary>
            ))}
        </div>
    );
}

/**
 * Hook to manage plugin lifecycle in a slot
 *
 * Provides utilities to add/remove plugins dynamically at runtime.
 */
export function usePluginSlot(slot: string, userPermissions?: string[]) {
    const [plugins, setPlugins] = useState<Array<RegisteredPlugin & { id: string }>>([]);

    // Stabilize userPermissions array reference
    const permissionsKey = useMemo(
        () => (userPermissions ? JSON.stringify(userPermissions.sort()) : null),
        [userPermissions]
    );

    const refresh = useCallback(() => {
        const slotPlugins = pluginRegistry.getBySlot(slot);
        const enabledPlugins = userPermissions
            ? pluginRegistry.getEnabled(userPermissions).filter((p) => slotPlugins?.some((s) => s.id === p.id))
            : slotPlugins;

        setPlugins(enabledPlugins.map((p) => ({ ...p, id: p.id })));
    }, [slot, permissionsKey, userPermissions]);

    useEffect(() => {
        refresh();

        // Listen to plugin registry changes
        const handleChange: PluginEventListener = (_plugin, event: PluginEvent) => {
            if (event === 'registered' || event === 'enabled' || event === 'disabled') {
                refresh();
            }
        };
        pluginRegistry.on(handleChange);

        return () => {
            pluginRegistry.off(handleChange);
        };
    }, [refresh]);

    return {
        plugins,
        refresh,
    };
}
