import { useState } from "react";
import { useNavigate } from "react-router";
import { useDepartments } from "./hooks/useDepartments";
import { DepartmentCard } from "./components/DepartmentCard";

/**
 * Departments Directory Page
 *
 * <p><b>Purpose</b><br>
 * Displays list of all departments with search, filtering, and quick access.
 * Users can view department metrics and navigate to detail pages.
 *
 * <p><b>Features</b><br>
 * - Department search by name or description
 * - Card grid layout with 2 columns (responsive)
 * - Quick stats: total departments, active agents, automation level
 * - Sort options: by name, activity, automation
 * - Filter by status (active/inactive)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <Route path="/departments" element={<DepartmentList />} />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Department directory page
 * @doc.layer product
 * @doc.pattern Page
 */
export function DepartmentList() {
    const navigate = useNavigate();
    const [search, setSearch] = useState("");
    const [sortBy, setSortBy] = useState<"name" | "activity" | "automation">(
        "name"
    );
    const [statusFilter, setStatusFilter] = useState<"all" | "active">("all");

    const { data: departments, isLoading, error } = useDepartments({ search });

    // Sort departments
    const sortedDepartments = departments
        ? [...departments].sort((a, b) => {
            switch (sortBy) {
                case "activity":
                    return b.activeAgents - a.activeAgents;
                case "automation":
                    return b.automationLevel - a.automationLevel;
                case "name":
                default:
                    return a.name.localeCompare(b.name);
            }
        })
        : [];

    // Filter by status
    const filteredDepartments =
        statusFilter === "active"
            ? sortedDepartments.filter((d) => d.status === "active")
            : sortedDepartments;

    return (
        <div className="space-y-8">
            {/* Page Header */}
            <div className="flex flex-col md:flex-row md:justify-between md:items-center gap-4">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
                        Departments
                    </h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">
                        {filteredDepartments.length} department
                        {filteredDepartments.length !== 1 ? "s" : ""} •{" "}
                        {departments?.reduce((sum, d) => sum + d.activeAgents, 0) || 0}{" "}
                        active agents
                    </p>
                </div>
                <button
                    onClick={() => navigate('/org?type=department')}
                    className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
                >
                    <span>🏗️</span>
                    <span>Open in Org Builder</span>
                </button>
            </div>

            {/* Search and Filters */}
            <div className="space-y-4 bg-white dark:bg-slate-950 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                {/* Search */}
                <div>
                    <label className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                        Search departments
                    </label>
                    <input
                        type="text"
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder="Search by name or description..."
                        className="mt-2 w-full px-4 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-slate-900 text-slate-900 dark:text-neutral-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                </div>

                {/* Filter and Sort */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <label className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                            Filter by Status
                        </label>
                        <select
                            value={statusFilter}
                            onChange={(e) =>
                                setStatusFilter(e.target.value as "all" | "active")
                            }
                            className="mt-2 w-full px-4 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-slate-900 text-slate-900 dark:text-neutral-100"
                        >
                            <option value="all">All Departments</option>
                            <option value="active">Active Only</option>
                        </select>
                    </div>

                    <div>
                        <label className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                            Sort By
                        </label>
                        <select
                            value={sortBy}
                            onChange={(e) =>
                                setSortBy(
                                    e.target.value as "name" | "activity" | "automation"
                                )
                            }
                            className="mt-2 w-full px-4 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-slate-900 text-slate-900 dark:text-neutral-100"
                        >
                            <option value="name">Name</option>
                            <option value="activity">Activity Level</option>
                            <option value="automation">Automation Level</option>
                        </select>
                    </div>
                </div>
            </div>

            {/* Departments Grid */}
            {isLoading ? (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    {Array(4)
                        .fill(0)
                        .map((_, i) => (
                            <div
                                key={i}
                                className="h-64 bg-slate-200 dark:bg-neutral-800 rounded-lg animate-pulse"
                            />
                        ))}
                </div>
            ) : error ? (
                <div className="p-4 bg-red-50 dark:bg-rose-600/30 border border-red-200 dark:border-red-800 rounded-lg text-red-700 dark:text-red-200">
                    Failed to load departments. Please try again.
                </div>
            ) : filteredDepartments.length === 0 ? (
                <div className="text-center py-12">
                    <p className="text-slate-600 dark:text-neutral-400">
                        No departments found. Try adjusting your search or filters.
                    </p>
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    {filteredDepartments.map((dept) => (
                        <DepartmentCard
                            key={dept.id}
                            department={dept}
                            onClick={() => navigate(`/departments/${dept.id}`)}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}

export default DepartmentList;
