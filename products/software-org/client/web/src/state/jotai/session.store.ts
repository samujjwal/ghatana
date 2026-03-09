import { atom, type Getter } from "jotai";
import { themeAtom as coreThemeAtom } from "@/state/jotai/atoms";

/**
 * Jotai global application state atoms.
 *
 * <p><b>Purpose</b><br>
 * Centralized Jotai atoms for app-scoped state (session, theme, filters).
 * Persisted state uses atomWithStorage for localStorage integration.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { useAtom } from 'jotai';
 * import { themeAtom, selectedTenantAtom } from '@/state/jotai/session.store';
 *
 * function MyComponent() {
 *   const [theme, setTheme] = useAtom(themeAtom);
 *   const [tenant, setTenant] = useAtom(selectedTenantAtom);
 *   return <div>{theme} - {tenant}</div>;
 * }
 * }</pre>
 *
 * @doc.type module
 * @doc.purpose Jotai global state atoms
 * @doc.layer product
 * @doc.pattern Atoms
 */

/**
 * User authentication state atom.
 * Format: { userId, token, roles }
 */
export const sessionAtom = atom<{
    userId: string | null;
    token: string | null;
    roles: string[];
} | null>(null);

/**
 * Theme preference atom (light/dark/system).
 *
 * This is an alias to the canonical persisted themeAtom defined in
 * state/jotai/atoms.ts so that all parts of the app (ThemeProvider,
 * MainLayout, useGlobalState, etc.) share a single source of truth.
 */
export const themeAtom = atom(
    (get) => get(coreThemeAtom),
    (_get, set, newTheme: "light" | "dark" | "system") => set(coreThemeAtom, newTheme)
);

/**
 * Selected tenant atom for multi-tenant filtering.
 * Persisted to localStorage via MainLayout.tsx
 * Default: read from localStorage or null
 */
export const selectedTenantAtom = atom<string | null>(
    null
);

/**
 * Selected environment atom for environment filtering.
 * Persisted to localStorage via MainLayout.tsx
 * Default: read from localStorage or "production"
 */
export const selectedEnvironmentAtom = atom<string>(
    "production"
);

/**
 * Time range filter atom (e.g., "last_7d", "last_30d", "custom").
 */
export const timeRangeAtom = atom<{
    type: "last_7d" | "last_30d" | "last_90d" | "custom";
    startDate?: Date;
    endDate?: Date;
}>({
    type: "last_7d",
});

/**
 * Comparison mode toggle (for side-by-side KPI comparisons).
 */
export const compareEnabledAtom = atom(false);

/**
 * Active department filter for department-specific views.
 */
export const activeDepartmentAtom = atom<string | null>(null);

/**
 * Selected event IDs for workflow inspection.
 */
export const selectedEventIdsAtom = atom<string[]>([]);

/**
 * HITL action queue filters (status, priority, assignee).
 */
export const hitlQueueFiltersAtom = atom<{
    status?: "pending" | "approved" | "rejected" | "deferred";
    priority?: "low" | "medium" | "high" | "critical";
    assignee?: string;
}>({});

/**
 * Sidebar collapsed state.
 * Persisted to localStorage via MainLayout.tsx
 * Default: read from localStorage or false (expanded)
 */
export const sidebarCollapsedAtom = atom<boolean>(
    false
);

/**
 * Derived atom: is authenticated.
 */
export const isAuthenticatedAtom = atom((get: Getter) => {
    const session = get(sessionAtom);
    return session?.token !== null && session?.userId !== null;
});

/**
 * Derived atom: current user ID.
 */
export const currentUserIdAtom = atom((get: Getter) => {
    return get(sessionAtom)?.userId ?? null;
});

/**
 * Derived atom: user roles.
 */
export const currentUserRolesAtom = atom((get: Getter) => {
    return get(sessionAtom)?.roles ?? [];
});
