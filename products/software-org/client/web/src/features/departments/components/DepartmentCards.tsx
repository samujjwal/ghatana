import React from 'react';
import { useDepartments } from '../hooks/useDepartments';

/**
 * Department Cards - Displays departments with health status and KPIs.
 *
 * <p><b>Features</b><br>
 * - Status indicator (active/inactive)
 * - Team size and agent count
 * - Key metrics (deployment frequency, lead time, MTTR)
 * - Automation level
 * - Click to drill into department detail
 *
 * @doc.type component
 * @doc.purpose Department grid display
 * @doc.layer product
 * @doc.pattern Organism
 */
export const DepartmentCards = React.memo(() => {
    const { data: departments, isLoading } = useDepartments();

    if (isLoading) {
        return (
            <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
                {Array.from({ length: 3 }).map((_, i) => (
                    <div key={i} className="h-32 bg-slate-200 rounded animate-pulse dark:bg-neutral-700" />
                ))}
            </div>
        );
    }

    if (!departments || departments.length === 0) {
        return <div className="text-center py-8 text-slate-600 dark:text-neutral-400">No departments found</div>;
    }

    return (
        <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
            {departments.map((dept) => (
                <div
                    key={dept.id}
                    className="rounded-lg border border-slate-200 p-4 hover:border-slate-300 hover:shadow-md transition-all cursor-pointer dark:border-neutral-600 dark:hover:border-slate-600 dark:bg-neutral-800"
                >
                    <div className="mb-2 flex items-start justify-between">
                        <div>
                            <h3 className="font-semibold text-slate-900 dark:text-neutral-100">{dept.name}</h3>
                            <p className="text-xs text-slate-600 dark:text-neutral-400">
                                {dept.teams} teams • {dept.activeAgents} agents
                            </p>
                        </div>
                        <span className="text-lg">
                            {dept.status === 'active' ? '✅' : '⏸'}
                        </span>
                    </div>

                    <div className="space-y-1 text-sm text-slate-600 dark:text-neutral-400">
                        <div>📊 Deployments: {dept.kpis.deploymentFrequency}/mo</div>
                        <div>⏱️ Lead Time: {dept.kpis.leadTime}</div>
                        <div>🔧 MTTR: {dept.kpis.mttr}</div>
                        <div className="mt-2">
                            <div className="text-xs font-medium text-slate-700 dark:text-neutral-300 mb-1">
                                Automation: {dept.automationLevel}%
                            </div>
                            <div className="h-1.5 w-full bg-slate-300 rounded-full overflow-hidden dark:bg-slate-600">
                                <div
                                    className="h-full bg-blue-500"
                                    style={{ width: `${dept.automationLevel}%` }}
                                />
                            </div>
                        </div>
                    </div>
                </div>
            ))}
        </div>
    );
});

DepartmentCards.displayName = 'DepartmentCards';

export default DepartmentCards;
