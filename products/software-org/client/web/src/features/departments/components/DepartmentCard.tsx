/**
 * Department Card Component
 *
 * <p><b>Purpose</b><br>
 * Displays a single department with key metrics, agent count, and automation level.
 * Used in department directory list.
 *
 * <p><b>Features</b><br>
 * - Department name and description
 * - KPI highlights (deployment frequency, MTTR, CFR)
 * - Active agents count
 * - Automation level progress bar
 * - Click to navigate to detail view
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <DepartmentCard
 *   department={dept}
 *   onClick={() => navigate(`/departments/${dept.id}`)}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Department card in list view
 * @doc.layer product
 * @doc.pattern Molecule
 */
import { Department } from "../hooks/useDepartments";
import { StatusBadge } from '@/shared/components';

interface DepartmentCardProps {
    department: Department;
    onClick?: () => void;
}

// Use StatusBadge for department health status
export const DepartmentCard = ({ department, onClick }: DepartmentCardProps) => {
    return (
        <div
            onClick={onClick}
            className={`bg-white dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg p-6 transition-all ${onClick ? "cursor-pointer hover:shadow-lg hover:border-slate-300 dark:hover:border-slate-700" : ""
                }`}
        >
            {/* Header */}
            <div className="flex justify-between items-start mb-4">
                <div className="flex-1">
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                        {department.name}
                    </h3>
                    <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                        {department.description}
                    </p>
                </div>
                <span
                    className={`px-2 py-1 text-xs font-semibold rounded ${department.status === "active"
                        ? "bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400"
                        : "bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-neutral-400"
                        }`}
                >
                    {department.status}
                </span>
            </div>

            {/* KPI Grid */}
            <div className="grid grid-cols-4 gap-2 mb-4">
                <div className="text-center">
                    <p className="text-xs text-slate-600 dark:text-neutral-400">Deploys</p>
                    <p className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                        {department.kpis.deploymentFrequency}
                    </p>
                </div>
                <div className="text-center">
                    <p className="text-xs text-slate-600 dark:text-neutral-400">Lead Time</p>
                    <p className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                        {department.kpis.leadTime}
                    </p>
                </div>
                <div className="text-center">
                    <p className="text-xs text-slate-600 dark:text-neutral-400">MTTR</p>
                    <p className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                        {department.kpis.mttr}
                    </p>
                </div>
                <div className="text-center">
                    <p className="text-xs text-slate-600 dark:text-neutral-400">CFR</p>
                    <p className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                        {department.kpis.changeFailureRate}
                    </p>
                </div>
            </div>

            {/* Footer Stats */}
            <div className="flex items-center justify-between text-sm border-t border-slate-200 dark:border-slate-800 pt-4">
                <div className="flex gap-4">
                    <div>
                        <p className="text-xs text-slate-600 dark:text-neutral-400">Teams</p>
                        <p className="font-semibold text-slate-900 dark:text-neutral-100">
                            {department.teams}
                        </p>
                    </div>
                    <div>
                        <p className="text-xs text-slate-600 dark:text-neutral-400">
                            Active Agents
                        </p>
                        <p className="font-semibold text-slate-900 dark:text-neutral-100">
                            {department.activeAgents}
                        </p>
                    </div>
                </div>
                <div className="flex-1 ml-4">
                    <div className="flex justify-between items-center mb-1">
                        <p className="text-xs text-slate-600 dark:text-neutral-400">
                            Automation
                        </p>
                        <p className="text-xs font-semibold text-slate-900 dark:text-neutral-100">
                            {department.automationLevel}%
                        </p>
                    </div>
                    <div className="w-full h-2 bg-slate-200 dark:bg-neutral-800 rounded-full overflow-hidden">
                        <div
                            className="h-full bg-gradient-to-r from-blue-500 to-purple-500"
                            style={{ width: `${department.automationLevel}%` }}
                        />
                    </div>
                </div>
            </div>
        </div>
    );
}

export default DepartmentCard;
