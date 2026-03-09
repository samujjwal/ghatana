import type { QuickAction } from '@/config/personaConfig';
import type { PendingTasks } from '@/state/jotai/atoms';

/**
 * Quick action card component for persona-driven landing page.
 *
 * <p><b>Purpose</b><br>
 * Displays an actionable card with icon, title, description, badge (dynamic count),
 * primary CTA, and optional keyboard shortcut hint. Supports 4 visual variants
 * (primary, secondary, warning, success) and dark mode.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <QuickActionCard
 *     action={approveHitlAction}
 *     pendingTasks={tasks}
 *     variant="warning"
 *     onClick={handleActionClick}
 * />
 * }</pre>
 *
 * <p><b>Features</b><br>
 * - Dynamic badge resolution from pendingTasks
 * - Visual variants: primary (blue), secondary (slate), warning (amber), success (green)
 * - Hover effects with scale animation
 * - Keyboard shortcut display
 * - Dark mode support
 * - Responsive design
 *
 * @doc.type component
 * @doc.purpose Quick action card for persona-driven workflows
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

export interface QuickActionCardProps {
    /** Action configuration from personaConfig */
    action: QuickAction;
    /** Pending tasks for badge count resolution */
    pendingTasks?: PendingTasks;
    /** Visual variant (default: primary) */
    variant?: 'primary' | 'secondary' | 'warning' | 'success';
    /** Click handler */
    onClick?: () => void;
    /** Additional CSS classes */
    className?: string;
}

/**
 * Renders a quick action card with icon, badge, and CTA.
 */
export function QuickActionCard({
    action,
    pendingTasks,
    variant = 'primary',
    onClick,
    className = '',
}: QuickActionCardProps) {
    const badgeCount = resolveBadgeCount(action.badgeKey, pendingTasks);
    const variantClasses = getVariantClasses(variant);

    return (
        <div
            className={`
                relative rounded-xl border-2 p-6 
                transition-all duration-200 ease-in-out
                hover:scale-105 hover:shadow-lg
                cursor-pointer
                ${variantClasses.container}
                ${className}
            `}
            onClick={onClick}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    onClick?.();
                }
            }}
        >
            {/* Badge (if count > 0) */}
            {badgeCount > 0 && (
                <div className="absolute -top-2 -right-2">
                    <span
                        className={`
                            inline-flex items-center justify-center
                            px-3 py-1 rounded-full text-xs font-bold
                            ${variantClasses.badge}
                        `}
                    >
                        {badgeCount}
                    </span>
                </div>
            )}

            {/* Icon */}
            <div className={`flex items-center justify-center w-12 h-12 rounded-lg mb-4 ${variantClasses.icon}`}>
                <span className="text-2xl">{action.icon}</span>
            </div>

            {/* Title */}
            <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-200 mb-2">{action.title}</h3>

            {/* Description */}
            <p className="text-sm text-slate-600 dark:text-neutral-400 mb-4">{action.description}</p>

            {/* CTA Button */}
            <button
                className={`
                    w-full px-4 py-2 rounded-lg font-medium text-sm
                    transition-colors duration-150
                    ${variantClasses.button}
                `}
                onClick={(e) => {
                    e.stopPropagation();
                    onClick?.();
                }}
            >
                Go to {action.title}
            </button>

            {/* Keyboard Shortcut (if available) */}
            {action.shortcut && (
                <div className="mt-3 text-xs text-slate-500 dark:text-slate-500 text-center">
                    Press{' '}
                    <kbd className="px-2 py-1 bg-slate-100 dark:bg-neutral-800 rounded border border-slate-300 dark:border-neutral-600 font-mono">
                        {action.shortcut}
                    </kbd>
                </div>
            )}
        </div>
    );
}

/**
 * Resolves badge count from pendingTasks based on badge identifier.
 */
function resolveBadgeCount(badge: string | undefined, pendingTasks?: PendingTasks): number {
    if (!badge || !pendingTasks) return 0;

    const badgeMap: Record<string, keyof PendingTasks> = {
        hitl: 'hitlApprovals',
        security: 'securityAlerts',
        workflows: 'failedWorkflows',
        models: 'modelAlerts',
    };

    const taskKey = badgeMap[badge];
    return taskKey ? pendingTasks[taskKey] : 0;
}

/**
 * Returns Tailwind classes for each variant.
 */
function getVariantClasses(variant: QuickActionCardProps['variant']) {
    const variants = {
        primary: {
            container:
                'border-blue-200 dark:border-blue-800 bg-white dark:bg-slate-900 hover:border-blue-400 dark:hover:border-blue-600',
            icon: 'bg-blue-100 dark:bg-blue-900 text-blue-600 dark:text-blue-300',
            badge: 'bg-blue-600 text-white',
            button:
                'bg-blue-600 hover:bg-blue-700 text-white dark:bg-blue-700 dark:hover:bg-blue-600',
        },
        secondary: {
            container:
                'border-slate-200 dark:border-neutral-600 bg-white dark:bg-slate-900 hover:border-slate-400 dark:hover:border-slate-600',
            icon: 'bg-slate-100 dark:bg-neutral-800 text-slate-600 dark:text-neutral-300',
            badge: 'bg-slate-600 text-white',
            button:
                'bg-slate-600 hover:bg-slate-700 text-white dark:bg-neutral-700 dark:hover:bg-slate-600',
        },
        warning: {
            container:
                'border-amber-200 dark:border-amber-800 bg-white dark:bg-slate-900 hover:border-amber-400 dark:hover:border-amber-600',
            icon: 'bg-amber-100 dark:bg-amber-900 text-amber-600 dark:text-amber-300',
            badge: 'bg-amber-600 text-white',
            button:
                'bg-amber-600 hover:bg-amber-700 text-white dark:bg-amber-700 dark:hover:bg-amber-600',
        },
        success: {
            container:
                'border-green-200 dark:border-green-800 bg-white dark:bg-slate-900 hover:border-green-400 dark:hover:border-green-600',
            icon: 'bg-green-100 dark:bg-green-900 text-green-600 dark:text-green-300',
            badge: 'bg-green-600 text-white',
            button:
                'bg-green-600 hover:bg-green-700 text-white dark:bg-green-700 dark:hover:bg-green-600',
        },
    };

    return variants[variant || 'primary'];
}
