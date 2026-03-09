import { useAtom } from 'jotai';
import {
    tenantAtom,
    timeRangeAtom,
    customDateRangeAtom,
    themeAtom,
    sidebarCollapsedAtom,
    userRoleAtom,
    isDarkModeAtom,
    timeRangeDisplayAtom,
} from '@/state/jotai/atoms';

/**
 * Custom hook for accessing global app state (tenant, time range, theme, etc.)
 *
 * <p><b>Purpose</b><br>
 * Provides convenient access to frequently-used global state atoms across all components.
 * Simplifies component code by bundling related atoms into a single hook.
 *
 * <p><b>Usage</b><br>
 * ```typescript
 * import { useGlobalState } from '@/hooks/useGlobalState';
 *
 * function MyComponent() {
 *   const { tenant, setTenant, timeRange, theme, isDarkMode } = useGlobalState();
 *   return (
 *     <div>
 *       <select value={tenant} onChange={(e) => setTenant(e.target.value)}>
 *         <option value="all-tenants">All Tenants</option>
 *       </select>
 *       <span>Theme: {theme}</span>
 *       <span>Dark mode: {isDarkMode ? 'On' : 'Off'}</span>
 *     </div>
 *   );
 * }
 * ```
 *
 * @doc.type hook
 * @doc.purpose Global state accessor hook
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useGlobalState() {
    const [tenant, setTenant] = useAtom(tenantAtom);
    const [timeRange, setTimeRange] = useAtom(timeRangeAtom);
    const [customDateRange, setCustomDateRange] = useAtom(customDateRangeAtom);
    const [theme, setTheme] = useAtom(themeAtom);
    const [sidebarCollapsed, setSidebarCollapsed] = useAtom(sidebarCollapsedAtom);
    const [userRole] = useAtom(userRoleAtom);
    const [isDarkMode] = useAtom(isDarkModeAtom);
    const [timeRangeDisplay] = useAtom(timeRangeDisplayAtom);

    return {
        // Tenant
        tenant,
        setTenant,

        // Time range
        timeRange,
        setTimeRange,
        customDateRange,
        setCustomDateRange,
        timeRangeDisplay,

        // Theme
        theme,
        setTheme,
        isDarkMode,

        // UI State
        sidebarCollapsed,
        setSidebarCollapsed,

        // User info (read-only)
        userRole,
    };
}
