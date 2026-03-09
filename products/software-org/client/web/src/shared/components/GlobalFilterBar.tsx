import { useAtom } from "jotai";
import { tenantAtom, timeRangeAtom, compareEnabledAtom } from "@/state/jotai/atoms";
import { atom } from "jotai";
import type { PersonaId } from "@/shared/types/org";

/**
 * Global filter atoms for cross-page state
 */
export const personaFilterAtom = atom<PersonaId | 'all'>('all');
export const departmentFilterAtom = atom<string | 'all'>('all');
export const environmentFilterAtom = atom<'development' | 'staging' | 'production' | 'all'>('all');

/**
 * GlobalFilterBar Props
 */
export interface GlobalFilterBarProps {
    /** Show persona filter */
    showPersonaFilter?: boolean;
    /** Show department filter */
    showDepartmentFilter?: boolean;
    /** Show environment filter */
    showEnvironmentFilter?: boolean;
    /** Show tenant filter */
    showTenantFilter?: boolean;
    /** Show time range filter */
    showTimeRangeFilter?: boolean;
    /** Show compare mode toggle */
    showCompareMode?: boolean;
    /** Compact mode (single row) */
    compact?: boolean;
    /** Additional CSS classes */
    className?: string;
    /** Departments list for filter */
    departments?: { id: string; name: string }[];
}

/**
 * GlobalFilterBar - Cross-cutting filter component
 *
 * <p><b>Purpose</b><br>
 * Provides consistent filtering controls across all pages for tenant,
 * time range, persona, department, and environment selection.
 *
 * <p><b>Features</b><br>
 * - Persona filter (engineer, lead, sre, security, admin)
 * - Department filter (from org config)
 * - Environment filter (dev, staging, prod)
 * - Tenant filter (multi-tenant support)
 * - Time range filter (7d, 30d, 90d, custom)
 * - Compare mode toggle
 * - Compact mode for embedded use
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * ```tsx
 * <GlobalFilterBar
 *   showPersonaFilter
 *   showDepartmentFilter
 *   departments={departments}
 * />
 * ```
 *
 * @doc.type component
 * @doc.purpose Cross-cutting global filter bar
 * @doc.layer shared
 * @doc.pattern Filter Component
 */
export function GlobalFilterBar({
    showPersonaFilter = false,
    showDepartmentFilter = false,
    showEnvironmentFilter = false,
    showTenantFilter = true,
    showTimeRangeFilter = true,
    showCompareMode = true,
    compact = false,
    className = '',
    departments = [],
}: GlobalFilterBarProps) {
    const [tenant, setTenant] = useAtom(tenantAtom);
    const [timeRange, setTimeRange] = useAtom(timeRangeAtom);
    const [compareEnabled, setCompareEnabled] = useAtom(compareEnabledAtom);
    const [personaFilter, setPersonaFilter] = useAtom(personaFilterAtom);
    const [departmentFilter, setDepartmentFilter] = useAtom(departmentFilterAtom);
    const [environmentFilter, setEnvironmentFilter] = useAtom(environmentFilterAtom);

    const selectClasses = compact
        ? "w-full px-2 py-1.5 text-xs border border-slate-300 dark:border-neutral-600 rounded-md bg-white dark:bg-slate-900 text-slate-900 dark:text-neutral-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
        : "mt-1 w-full md:w-40 px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg bg-white dark:bg-slate-900 text-slate-900 dark:text-neutral-100 focus:outline-none focus:ring-2 focus:ring-blue-500";

    const labelClasses = compact
        ? "text-[10px] font-medium text-slate-500 dark:text-neutral-400 uppercase tracking-wide"
        : "text-sm font-medium text-slate-700 dark:text-neutral-300";

    return (
        <div className={`sticky top-0 z-40 bg-white dark:bg-slate-950 border-b border-slate-200 dark:border-slate-800 ${compact ? 'p-2' : 'p-4'} rounded-lg ${className}`}>
            <div className={`flex flex-wrap ${compact ? 'gap-2' : 'gap-4'} items-end`}>
                {/* Persona Filter */}
                {showPersonaFilter && (
                    <div className={compact ? 'w-28' : 'flex-1 md:flex-initial'}>
                        <label className={labelClasses}>Persona</label>
                        <select
                            value={personaFilter}
                            onChange={(e) => setPersonaFilter(e.target.value as PersonaId | 'all')}
                            className={selectClasses}
                        >
                            <option value="all">All Personas</option>
                            <option value="engineer">👨‍💻 Engineer</option>
                            <option value="lead">👔 Lead</option>
                            <option value="sre">🔧 SRE</option>
                            <option value="security">🛡️ Security</option>
                            <option value="admin">⚡ Admin</option>
                        </select>
                    </div>
                )}

                {/* Department Filter */}
                {showDepartmentFilter && (
                    <div className={compact ? 'w-32' : 'flex-1 md:flex-initial'}>
                        <label className={labelClasses}>Department</label>
                        <select
                            value={departmentFilter}
                            onChange={(e) => setDepartmentFilter(e.target.value)}
                            className={selectClasses}
                        >
                            <option value="all">All Departments</option>
                            {departments.map((dept) => (
                                <option key={dept.id} value={dept.id}>
                                    {dept.name}
                                </option>
                            ))}
                        </select>
                    </div>
                )}

                {/* Environment Filter */}
                {showEnvironmentFilter && (
                    <div className={compact ? 'w-28' : 'flex-1 md:flex-initial'}>
                        <label className={labelClasses}>Environment</label>
                        <select
                            value={environmentFilter}
                            onChange={(e) => setEnvironmentFilter(e.target.value as typeof environmentFilter)}
                            className={selectClasses}
                        >
                            <option value="all">All Envs</option>
                            <option value="development">🔧 Dev</option>
                            <option value="staging">🧪 Staging</option>
                            <option value="production">🚀 Prod</option>
                        </select>
                    </div>
                )}

                {/* Tenant Filter */}
                {showTenantFilter && (
                    <div className={compact ? 'w-28' : 'flex-1 md:flex-initial'}>
                        <label className={labelClasses}>Tenant</label>
                        <select
                            value={tenant}
                            onChange={(e) => setTenant(e.target.value)}
                            className={selectClasses}
                        >
                            <option value="all-tenants">All Tenants</option>
                            <option value="tenant-1">Tenant A</option>
                            <option value="tenant-2">Tenant B</option>
                        </select>
                    </div>
                )}

                {/* Time Range Filter */}
                {showTimeRangeFilter && (
                    <div className={compact ? 'w-28' : 'flex-1 md:flex-initial'}>
                        <label className={labelClasses}>Time Range</label>
                        <select
                            value={timeRange}
                            onChange={(e) =>
                                setTimeRange(e.target.value as "7d" | "30d" | "90d" | "custom")
                            }
                            className={selectClasses}
                        >
                            <option value="7d">Last 7 days</option>
                            <option value="30d">Last 30 days</option>
                            <option value="90d">Last 90 days</option>
                            <option value="custom">Custom</option>
                        </select>
                    </div>
                )}

                {/* Compare Mode Toggle */}
                {showCompareMode && (
                    <div className="flex items-center h-full">
                        <label className="flex items-center gap-2 cursor-pointer">
                            <input
                                type="checkbox"
                                checked={compareEnabled}
                                onChange={(e) => setCompareEnabled(e.target.checked)}
                                className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500"
                            />
                            <span className={compact ? 'text-xs text-slate-600 dark:text-neutral-400' : 'text-sm font-medium text-slate-700 dark:text-neutral-300'}>
                                Compare
                            </span>
                        </label>
                    </div>
                )}
            </div>
        </div>
    );
}

export default GlobalFilterBar;
