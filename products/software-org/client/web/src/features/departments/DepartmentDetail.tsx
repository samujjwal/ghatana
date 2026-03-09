import { useState } from "react";
import { useParams, useNavigate } from "react-router";
import { useDepartments } from "./hooks/useDepartments";
import { KpiCard } from "@/components/ui";
import { KpiGrid } from "@/shared/components/KpiGrid";
import { PlaybookDrawer } from "./components/PlaybookDrawer";

/**
 * Department Detail Page
 *
 * <p><b>Purpose</b><br>
 * Displays comprehensive department information with tabs for overview,
 * agents, workflows, and automation playbooks.
 *
 * <p><b>Features</b><br>
 * - Department overview with key metrics
 * - Tabbed interface: Overview | Agents | Workflows | Playbooks
 * - KPI cards with detailed drill-down
 * - Playbook drawer for automation configuration
 * - Department actions (edit, archive)
 * - Real-time agent status
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <Route path="/departments/:id" element={<DepartmentDetail />} />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Department detail page with tabs
 * @doc.layer product
 * @doc.pattern Page
 */
export function DepartmentDetail() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [activeTab, setActiveTab] = useState<
        "overview" | "agents" | "workflows" | "playbooks"
    >("overview");
    const [showPlaybookDrawer, setShowPlaybookDrawer] = useState(false);
    const [automationEnabled, setAutomationEnabled] = useState(false);

    const { data: departments, isLoading, error } = useDepartments();

    const department = departments?.find((d) => d.id === id);

    if (isLoading) {
        return (
            <div className="space-y-6">
                <div className="h-32 bg-slate-200 dark:bg-neutral-800 rounded-lg animate-pulse" />
            </div>
        );
    }

    if (error || !department) {
        return (
            <div className="p-6 bg-red-50 dark:bg-rose-600/30 border border-red-200 dark:border-red-800 rounded-lg text-red-700 dark:text-red-200">
                <p className="font-semibold">Failed to load department</p>
                <button
                    onClick={() => navigate("/departments")}
                    className="mt-2 text-red-700 dark:text-red-200 hover:underline"
                >
                    Back to Departments
                </button>
            </div>
        );
    }

    return (
        <div className="space-y-8">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div className="flex-1">
                    <button
                        onClick={() => navigate("/departments")}
                        className="text-sm text-blue-600 dark:text-indigo-400 hover:underline mb-2"
                    >
                        ← Back to Departments
                    </button>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
                        {department.name}
                    </h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">
                        {department.description}
                    </p>
                </div>
                <button
                    onClick={() => setShowPlaybookDrawer(true)}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition-colors"
                >
                    Configure Playbook
                </button>
            </div>

            {/* Quick Stats */}
            <div className="grid grid-cols-4 gap-4 bg-white dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                <div>
                    <p className="text-xs text-slate-600 dark:text-neutral-400">Teams</p>
                    <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                        {department.teams}
                    </p>
                </div>
                <div>
                    <p className="text-xs text-slate-600 dark:text-neutral-400">
                        Active Agents
                    </p>
                    <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                        {department.activeAgents}
                    </p>
                </div>
                <div>
                    <p className="text-xs text-slate-600 dark:text-neutral-400">
                        Automation
                    </p>
                    <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                        {department.automationLevel}%
                    </p>
                </div>
                <div>
                    <p className="text-xs text-slate-600 dark:text-neutral-400">Status</p>
                    <p className="text-2xl font-bold text-green-600 dark:text-green-400">
                        Active
                    </p>
                </div>
            </div>

            {/* Tabs */}
            <div className="border-b border-slate-200 dark:border-slate-800">
                <div className="flex gap-8">
                    {(
                        ["overview", "agents", "workflows", "playbooks"] as const
                    ).map((tab) => (
                        <button
                            key={tab}
                            onClick={() => setActiveTab(tab)}
                            className={`py-3 px-1 font-medium capitalize border-b-2 transition-colors ${activeTab === tab
                                ? "border-blue-600 dark:border-blue-400 text-blue-600 dark:text-indigo-400"
                                : "border-transparent text-slate-600 dark:text-neutral-400 hover:text-slate-900 dark:hover:text-white"
                                }`}
                        >
                            {tab}
                        </button>
                    ))}
                </div>
            </div>

            {/* Tab Content */}
            <div className="space-y-8">
                {activeTab === "overview" && (
                    <div className="space-y-6">
                        <div>
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                                Key Performance Indicators
                            </h2>
                            <KpiGrid>
                                <KpiCard
                                    title="Deployment Frequency"
                                    value={department.kpis.deploymentFrequency}
                                    subtitle="/week"
                                    trend="up"
                                    trendValue="+12%"
                                />
                                <KpiCard
                                    title="Lead Time"
                                    value={department.kpis.leadTime}
                                    trend="down"
                                    trendValue="-8%"
                                />
                                <KpiCard
                                    title="Mean Time to Recovery"
                                    value={department.kpis.mttr}
                                    trend="down"
                                    trendValue="-15%"
                                />
                                <KpiCard
                                    title="Change Failure Rate"
                                    value={department.kpis.changeFailureRate}
                                    trend="down"
                                    trendValue="-5%"
                                />
                                <KpiCard
                                    title="Team Size"
                                    value={department.teams}
                                    subtitle="people"
                                />
                                <KpiCard
                                    title="Active Agents"
                                    value={department.activeAgents}
                                    subtitle="running"
                                />
                            </KpiGrid>
                        </div>

                        {/* Automation Settings */}
                        <div className="bg-white dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                            <div className="flex items-center justify-between">
                                <div>
                                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                        Automation Status
                                    </h3>
                                    <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                                        {automationEnabled
                                            ? "Automated workflows are enabled"
                                            : "Enable automated workflows for this department"}
                                    </p>
                                </div>
                                <label className="flex items-center gap-2 cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={automationEnabled}
                                        onChange={(e) => setAutomationEnabled(e.target.checked)}
                                        className="w-4 h-4 rounded"
                                    />
                                    <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                                        Enable
                                    </span>
                                </label>
                            </div>
                        </div>
                    </div>
                )}

                {activeTab === "agents" && (
                    <div className="text-center py-12">
                        <p className="text-slate-600 dark:text-neutral-400">
                            {department.activeAgents} agents active - Agent list coming soon
                        </p>
                    </div>
                )}

                {activeTab === "workflows" && (
                    <div className="text-center py-12">
                        <p className="text-slate-600 dark:text-neutral-400">
                            Workflow explorer coming soon
                        </p>
                    </div>
                )}

                {activeTab === "playbooks" && (
                    <div className="text-center py-12">
                        <p className="text-slate-600 dark:text-neutral-400">
                            Playbook editor coming soon
                        </p>
                        <button
                            onClick={() => setShowPlaybookDrawer(true)}
                            className="mt-4 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition-colors"
                        >
                            Add Playbook
                        </button>
                    </div>
                )}
            </div>

            {/* Playbook Drawer */}
            {showPlaybookDrawer && (
                <PlaybookDrawer
                    departmentName={department.name}
                    onClose={() => setShowPlaybookDrawer(false)}
                />
            )}
        </div>
    );
}

export default DepartmentDetail;
