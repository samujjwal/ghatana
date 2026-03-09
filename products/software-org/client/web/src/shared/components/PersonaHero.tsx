import type { UserProfile, PendingTasks } from '@/state/jotai/atoms';

/**
 * PersonaHero - Personalized hero section for landing page
 *
 * <p><b>Purpose</b><br>
 * Displays personalized greeting, user role, and pending task notifications.
 * Adapts content based on time of day and user's role.
 *
 * <p><b>Features</b><br>
 * - Time-based greeting (Good morning/afternoon/evening)
 * - User name and role badge
 * - Role-specific tagline
 * - Notification badges for pending tasks
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <PersonaHero
 *   user={userProfile}
 *   pendingTasks={pendingTasks}
 *   greeting="Good morning, Alice"
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Personalized hero section for persona-driven landing
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

export interface PersonaHeroProps {
    user: UserProfile;
    pendingTasks?: PendingTasks;
    greeting?: string;
    className?: string;
}

/**
 * Get role-specific badge color
 */
function getRoleBadgeColor(role: string): string {
    const colors: Record<string, string> = {
        admin: 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200',
        lead: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200',
        engineer: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200',
        viewer: 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200',
    };
    return colors[role] || 'bg-slate-100 text-slate-800 dark:bg-neutral-800 dark:text-slate-200';
}

/**
 * Get role display name
 */
function getRoleDisplayName(role: string): string {
    const names: Record<string, string> = {
        admin: 'Platform Administrator',
        lead: 'Technical Lead',
        engineer: 'Software Engineer',
        viewer: 'Analyst / Observer',
    };
    return names[role] || role;
}

/**
 * Calculate total pending tasks
 */
function getTotalPendingTasks(tasks?: PendingTasks): number {
    if (!tasks) return 0;
    return tasks.hitlApprovals + tasks.securityAlerts + tasks.failedWorkflows + tasks.modelAlerts;
}

export function PersonaHero({ user, pendingTasks, greeting, className = '' }: PersonaHeroProps) {
    if (import.meta.env.DEV) {
        console.debug('[PersonaHero] Rendering user role:', user.role, 'user.name:', user.name);
    }
    const totalPending = getTotalPendingTasks(pendingTasks);
    const hasUrgentTasks = (pendingTasks?.hitlApprovals || 0) > 0 || (pendingTasks?.securityAlerts || 0) > 0;

    return (
        <div className={`mb-12 ${className}`}>
            <div className="text-center max-w-4xl mx-auto">
                {/* Greeting */}
                <h1 className="text-4xl sm:text-5xl font-bold text-slate-900 dark:text-neutral-100 mb-4">
                    {greeting || `Welcome, ${user.name}`}
                </h1>

                {/* Role Badge */}
                <div className="flex items-center justify-center gap-4 mb-6">
                    <span className={`px-4 py-2 rounded-full text-sm font-semibold ${getRoleBadgeColor(user.role)}`}>
                        {getRoleDisplayName(user.role)}
                    </span>

                    {/* Pending Tasks Badge */}
                    {totalPending > 0 && (
                        <span
                            className={`px-4 py-2 rounded-full text-sm font-semibold flex items-center gap-2 ${hasUrgentTasks
                                ? 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
                                : 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200'
                                }`}
                        >
                            <span className="relative flex h-2 w-2">
                                <span
                                    className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${hasUrgentTasks ? 'bg-red-400' : 'bg-amber-400'
                                        }`}
                                ></span>
                                <span
                                    className={`relative inline-flex rounded-full h-2 w-2 ${hasUrgentTasks ? 'bg-red-500' : 'bg-amber-500'
                                        }`}
                                ></span>
                            </span>
                            {totalPending} Pending Task{totalPending !== 1 ? 's' : ''}
                        </span>
                    )}
                </div>

                {/* Pending Tasks Breakdown (if any) */}
                {totalPending > 0 && pendingTasks && (
                    <div className="flex items-center justify-center gap-4 text-sm text-slate-600 dark:text-neutral-400 mb-6">
                        {pendingTasks.hitlApprovals > 0 && (
                            <span className="flex items-center gap-1">
                                <span className="font-semibold text-red-600 dark:text-rose-400">
                                    {pendingTasks.hitlApprovals}
                                </span>{' '}
                                approvals
                            </span>
                        )}
                        {pendingTasks.securityAlerts > 0 && (
                            <span className="flex items-center gap-1">
                                <span className="font-semibold text-red-600 dark:text-rose-400">
                                    {pendingTasks.securityAlerts}
                                </span>{' '}
                                security alerts
                            </span>
                        )}
                        {pendingTasks.failedWorkflows > 0 && (
                            <span className="flex items-center gap-1">
                                <span className="font-semibold text-amber-600 dark:text-amber-400">
                                    {pendingTasks.failedWorkflows}
                                </span>{' '}
                                failed workflows
                            </span>
                        )}
                        {pendingTasks.modelAlerts > 0 && (
                            <span className="flex items-center gap-1">
                                <span className="font-semibold text-amber-600 dark:text-amber-400">
                                    {pendingTasks.modelAlerts}
                                </span>{' '}
                                model alerts
                            </span>
                        )}
                    </div>
                )}

                {/* User Email (subtle) */}
                <p className="text-sm text-slate-500 dark:text-slate-500">{user.email}</p>
            </div>
        </div>
    );
}
