/**
 * DashboardGrid Component - Customizable Widget Layout
 *
 * <p><b>Purpose</b><br>
 * Provides drag-and-drop, resizable grid layout for persona dashboard widgets.
 * Supports responsive breakpoints, layout persistence, and slot-based widget rendering.
 *
 * <p><b>Features</b><br>
 * - Drag-and-drop widget repositioning
 * - Resize widgets with constraints
 * - Responsive breakpoints (lg, md, sm, xs)
 * - Persist layout to localStorage (Phase 2) / API (Phase 3)
 * - Slot-based widget loading with PluginSlot
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { DashboardGrid } from '@/components/DashboardGrid';
 * import { usePersonaComposition } from '@/hooks/usePersonaComposition';
 *
 * function Dashboard() {
 *   const { merged } = usePersonaComposition();
 *   const widgets = merged?.widgets ?? [];
 *
 *   return (
 *     <DashboardGrid
 *       widgets={widgets}
 *       editable={true}
 *       onLayoutChange={(layout) => saveLayout(layout)}
 *     />
 *   );
 * }
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Customizable drag-and-drop dashboard grid
 * @doc.layer product
 * @doc.pattern Container
 */

import { useState, useCallback, useMemo } from 'react';
import { Responsive, WidthProvider, Layout as RGLLayout } from 'react-grid-layout';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import type { WidgetConfig } from '@/schemas/persona.schema';

const ResponsiveGridLayout = WidthProvider(Responsive);

/**
 * Breakpoint definitions for responsive layout
 */
const BREAKPOINTS = {
    lg: 1200,
    md: 996,
    sm: 768,
    xs: 480,
};

/**
 * Column counts per breakpoint
 */
const COLS = {
    lg: 12,
    md: 10,
    sm: 6,
    xs: 4,
};

/**
 * Default widget dimensions if not specified
 */
const DEFAULT_WIDGET_SIZE = {
    w: 4, // 4 columns wide (1/3 of 12-column grid)
    h: 4, // 4 rows tall
    minW: 2,
    minH: 2,
    maxW: 12,
    maxH: 10,
};

export interface DashboardGridProps {
    /**
     * Widget configurations to render
     */
    widgets: WidgetConfig[];

    /**
     * Enable drag-and-drop and resize
     * @default false
     */
    editable?: boolean;

    /**
     * Callback when layout changes (drag/resize)
     */
    onLayoutChange?: (layout: RGLLayout[], widgets: WidgetConfig[]) => void;

    /**
     * Initial layouts per breakpoint (for restoration)
     */
    savedLayouts?: Record<string, RGLLayout[]>;

    /**
     * Grid row height in pixels
     * @default 80
     */
    rowHeight?: number;

    /**
     * CSS class name for container
     */
    className?: string;
}

/**
 * DashboardGrid - Responsive grid layout with drag-and-drop widgets
 */
export function DashboardGrid({
    widgets,
    editable = false,
    onLayoutChange,
    savedLayouts,
    rowHeight = 80,
    className = '',
}: DashboardGridProps) {
    // Convert WidgetConfig to react-grid-layout format
    const initialLayouts = useMemo(() => {
        if (savedLayouts) {
            return savedLayouts;
        }

        // Generate default layouts from widget configs
        const layouts: Record<string, RGLLayout[]> = {};
        const breakpoints = Object.keys(COLS);

        breakpoints.forEach((bp) => {
            layouts[bp] = widgets.map((widget, index) => {
                const layout = widget.layout?.[bp] ?? widget.layout?.lg;
                return {
                    i: widget.id,
                    x: layout?.x ?? (index % 3) * 4, // Default to 3 columns
                    y: layout?.y ?? Math.floor(index / 3) * 4,
                    w: layout?.w ?? DEFAULT_WIDGET_SIZE.w,
                    h: layout?.h ?? DEFAULT_WIDGET_SIZE.h,
                    minW: layout?.minW ?? DEFAULT_WIDGET_SIZE.minW,
                    minH: layout?.minH ?? DEFAULT_WIDGET_SIZE.minH,
                    maxW: layout?.maxW ?? DEFAULT_WIDGET_SIZE.maxW,
                    maxH: layout?.maxH ?? DEFAULT_WIDGET_SIZE.maxH,
                };
            });
        });

        return layouts;
    }, [widgets, savedLayouts]);

    const [layouts, setLayouts] = useState<Record<string, RGLLayout[]>>(initialLayouts);

    /**
     * Handle layout change from drag/resize
     */
    const handleLayoutChange = useCallback(
        (currentLayout: RGLLayout[], allLayouts: Record<string, RGLLayout[]>) => {
            setLayouts(allLayouts);

            if (onLayoutChange) {
                // Merge layout info back into widgets
                const updatedWidgets = widgets.map((widget) => {
                    const layoutForWidget: Record<string, any> = {};
                    Object.keys(allLayouts).forEach((bp) => {
                        const layoutItem = allLayouts[bp].find((item) => item.i === widget.id);
                        if (layoutItem) {
                            layoutForWidget[bp] = {
                                x: layoutItem.x,
                                y: layoutItem.y,
                                w: layoutItem.w,
                                h: layoutItem.h,
                            };
                        }
                    });
                    return {
                        ...widget,
                        layout: layoutForWidget,
                    };
                });

                onLayoutChange(currentLayout, updatedWidgets);
            }
        },
        [widgets, onLayoutChange]
    );

    return (
        <div className={`dashboard-grid ${className}`}>
            <ResponsiveGridLayout
                className="layout"
                layouts={layouts}
                breakpoints={BREAKPOINTS}
                cols={COLS}
                rowHeight={rowHeight}
                isDraggable={editable}
                isResizable={editable}
                onLayoutChange={handleLayoutChange}
                draggableHandle=".widget-drag-handle"
                margin={[16, 16]}
                containerPadding={[0, 0]}
                compactType="vertical"
            >
                {widgets.map((widget) => (
                    <div
                        key={widget.id}
                        className="dashboard-widget bg-white dark:bg-neutral-800 rounded-lg shadow-sm border border-slate-200 dark:border-neutral-600 overflow-hidden"
                    >
                        {/* Widget Drag Handle (only visible if editable) */}
                        {editable && (
                            <div className="widget-drag-handle cursor-move bg-slate-100 dark:bg-neutral-700 p-2 border-b border-slate-200 dark:border-neutral-600 flex items-center gap-2">
                                <svg
                                    className="w-4 h-4 text-slate-400"
                                    fill="none"
                                    stroke="currentColor"
                                    viewBox="0 0 24 24"
                                >
                                    <path
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        strokeWidth={2}
                                        d="M4 8h16M4 16h16"
                                    />
                                </svg>
                                <span className="text-sm font-medium text-slate-600 dark:text-neutral-300">
                                    {widget.title}
                                </span>
                            </div>
                        )}

                        {/* Widget Content - Placeholder for PluginSlot integration */}
                        <div className="widget-content p-4">
                            {/* TODO: Replace with <PluginSlot slot={widget.slot} pluginId={widget.pluginId} config={widget.config} /> */}
                            <div className="text-center py-8">
                                <p className="text-sm text-slate-500 dark:text-neutral-400">
                                    Widget: {widget.title}
                                </p>
                                <p className="text-xs text-slate-400 dark:text-slate-500 dark:text-neutral-400 mt-1">
                                    Slot: {widget.slot ?? 'default'}
                                </p>
                                <p className="text-xs text-slate-400 dark:text-slate-500 dark:text-neutral-400">
                                    Type: {widget.type}
                                </p>
                            </div>
                        </div>
                    </div>
                ))}
            </ResponsiveGridLayout>
        </div>
    );
}

/**
 * Hook to manage layout persistence
 *
 * Saves layout to localStorage and provides restore functionality.
 * In Phase 3, this will be replaced with API persistence.
 *
 * @param key - LocalStorage key for persistence
 * @returns [savedLayouts, saveLayout, clearLayout]
 */
export function useLayoutPersistence(key: string) {
    const [savedLayouts, setSavedLayouts] = useState<Record<string, RGLLayout[]> | undefined>(() => {
        try {
            const stored = localStorage.getItem(key);
            return stored ? JSON.parse(stored) : undefined;
        } catch {
            return undefined;
        }
    });

    const saveLayout = useCallback(
        (layout: RGLLayout[], widgets: WidgetConfig[]) => {
            // Save to state
            const layoutsByBreakpoint: Record<string, RGLLayout[]> = {};
            Object.keys(COLS).forEach((bp) => {
                layoutsByBreakpoint[bp] = layout;
            });
            setSavedLayouts(layoutsByBreakpoint);

            // Persist to localStorage
            try {
                localStorage.setItem(key, JSON.stringify(layoutsByBreakpoint));
            } catch (error) {
                console.error('Failed to save layout:', error);
            }
        },
        [key]
    );

    const clearLayout = useCallback(() => {
        setSavedLayouts(undefined);
        try {
            localStorage.removeItem(key);
        } catch (error) {
            console.error('Failed to clear layout:', error);
        }
    }, [key]);

    return [savedLayouts, saveLayout, clearLayout] as const;
}
