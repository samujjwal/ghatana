import { QuickActionCard } from './QuickActionCard';
import type { QuickAction } from '@/config/personaConfig';
import type { PendingTasks } from '@/state/jotai/atoms';

/**
 * Grid layout for quick action cards.
 *
 * <p><b>Purpose</b><br>
 * Responsive grid container that displays 4-6 quick action cards
 * with proper spacing, alignment, and responsive breakpoints.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <QuickActionsGrid
 *     actions={personaConfig.quickActions}
 *     pendingTasks={tasks}
 *     columns={3}
 *     onActionClick={handleActionClick}
 * />
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Responsive grid: 1 col (mobile), 2 cols (tablet), 3+ cols (desktop)
 * - Automatic gap spacing and alignment
 * - Click handling with action routing
 * - Dark mode support
 *
 * @doc.type component
 * @doc.purpose Grid container for quick action cards
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

export interface QuickActionsGridProps {
    /** Array of quick actions to display */
    actions: QuickAction[];
    /** Pending tasks for badge resolution */
    pendingTasks?: PendingTasks;
    /** Number of columns (default: 3) */
    columns?: 2 | 3 | 4;
    /** Click handler for actions */
    onActionClick?: (action: QuickAction) => void;
    /** Additional CSS classes */
    className?: string;
}

/**
 * Renders a responsive grid of quick action cards.
 */
export function QuickActionsGrid({
    actions,
    pendingTasks,
    columns = 3,
    onActionClick,
    className = '',
}: QuickActionsGridProps) {
    const gridColumns = getGridColumns(columns);
    if (import.meta.env.DEV) {
        console.debug('[QuickActionsGrid] actions length:', actions?.length, 'first action:', actions?.[0]?.id);
    }

    return (
        <div className={`mb-12 ${className}`}>
            {/* Section Title */}
            <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-200 mb-6">Quick Actions</h2>

            {/* Grid */}
            <div className={`grid gap-6 ${gridColumns}`}>
                {actions.map((action) => (
                    <QuickActionCard
                        key={action.id}
                        action={action}
                        pendingTasks={pendingTasks}
                        variant={action.variant}
                        onClick={() => onActionClick?.(action)}
                    />
                ))}
            </div>

            {/* Empty State */}
            {actions.length === 0 && (
                <div className="text-center py-12 text-slate-500 dark:text-neutral-400">
                    <p className="text-lg">No quick actions available</p>
                    <p className="text-sm mt-2">Contact your administrator to configure quick actions for your role.</p>
                </div>
            )}
        </div>
    );
}

/**
 * Returns responsive grid column classes.
 */
function getGridColumns(columns: QuickActionsGridProps['columns']): string {
    const gridMap = {
        2: 'grid-cols-1 md:grid-cols-2',
        3: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3',
        4: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-4',
    };

    return gridMap[columns || 3];
}
