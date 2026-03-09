import React, { useEffect, useState } from "react";
import { NavLink, Link } from "react-router";
import { useAtom } from "jotai";
import {
    sidebarCollapsedAtom,
    themeAtom,
    selectedTenantAtom,
    selectedEnvironmentAtom,
} from "@/state/jotai/session.store";
import { DashboardLayout } from "../components/ui";
import { GlobalSearch } from "@/features/search/GlobalSearch";
import { Breadcrumbs } from "@/features/navigation/Breadcrumbs";
import { usePermissions } from "@/hooks/usePermissions";
import { usePersona } from "@/hooks/usePersona";
import { EntryPointSelector } from "@/components/navigation/EntryPointSelector";
import { SkipLink } from "@/components/ui/SkipLink";
import "@ghatana/tokens/safe-area.css";

/**
 * Main application layout with responsive sidebar and header
 *
 * <p><b>Purpose</b><br>
 * Root layout component providing consistent navigation, theme, and sidebar state
 * across all pages. Persists sidebar and theme preferences to localStorage.
 *
 * <p><b>Features</b><br>
 * - Responsive sidebar (collapsible on mobile)
 * - Dark/light theme toggle with persistence
 * - Tenant selector for multi-tenant filtering
 * - Global navigation menu
 * - Sidebar collapse state persistence to localStorage
 *
 * <p><b>Component Structure</b><br>
 * - Header: Theme toggle, tenant selector, user menu
 * - Sidebar: Navigation menu (desktop), collapse button (mobile)
 * - Content: Page outlet with responsive padding
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <MainLayout>
 *   <RouterOutlet /> // rendered via children
 * </MainLayout>
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Main application layout
 * @doc.layer product
 * @doc.pattern Layout Component
 */
export function MainLayout({
    children,
    title,
    subtitle,
}: {
    children: React.ReactNode;
    title?: string;
    subtitle?: string;
}) {
    const [sidebarCollapsed, setSidebarCollapsed] = useAtom(sidebarCollapsedAtom);
    const [theme, setTheme] = useAtom(themeAtom);
    const [tenant, setTenant] = useAtom(selectedTenantAtom);
    const [environment, setEnvironment] = useAtom(selectedEnvironmentAtom);

    // Initialize persisted preferences from localStorage on client after first render
    useEffect(() => {
        if (typeof window === "undefined") return;

        const storedTenant = localStorage.getItem("selected-tenant");
        if (storedTenant) {
            setTenant(storedTenant);
        }

        const storedEnvironment = localStorage.getItem("selected-environment");
        if (storedEnvironment) {
            setEnvironment(storedEnvironment);
        }

        const storedSidebar = localStorage.getItem("sidebar-collapsed");
        if (storedSidebar !== null) {
            setSidebarCollapsed(storedSidebar === "true");
        }
    }, [setTheme, setTenant, setEnvironment, setSidebarCollapsed]);

    // Persist sidebar state to localStorage on change
    useEffect(() => {
        localStorage.setItem("sidebar-collapsed", JSON.stringify(sidebarCollapsed));
    }, [sidebarCollapsed]);

    // Persist tenant to localStorage on change
    useEffect(() => {
        if (tenant) {
            localStorage.setItem("selected-tenant", tenant);
        }
    }, [tenant]);

    // Persist environment to localStorage on change
    useEffect(() => {
        localStorage.setItem("selected-environment", environment);
    }, [environment]);

    const toggleSidebar = () => setSidebarCollapsed(!sidebarCollapsed);

    const handleThemeChange = (newTheme: "light" | "dark" | "system") => {
        setTheme(newTheme);
    };

    return (
        <>
            {/* Skip Link for keyboard/screen reader users - WCAG 2.4.1 */}
            <SkipLink targetId="main-content" />

            <DashboardLayout
                header={
                    <HeaderContent
                        theme={theme}
                        tenant={tenant}
                        environment={environment}
                        setTenant={setTenant}
                        setEnvironment={setEnvironment}
                        handleThemeChange={handleThemeChange}
                    />
                }
                sidebar={
                    <SidebarContent
                        sidebarCollapsed={sidebarCollapsed}
                        toggleSidebar={toggleSidebar}
                    />
                }
                sidebarCollapsed={sidebarCollapsed}
                onSidebarToggle={setSidebarCollapsed}
                padding="md"
            >
                {/* Global Context Banner */}
                <GlobalContextBanner
                    tenant={tenant}
                    environment={environment}
                />

                {/* Main Content */}
                <main id="main-content" className="p-6" role="main">
                    {(title || subtitle) && (
                        <div className="mb-4">
                            {title && (
                                <h1 className="text-2xl font-semibold text-slate-900 dark:text-[#e6edf3]">
                                    {title}
                                </h1>
                            )}
                            {subtitle && (
                                <p className="mt-1 text-sm text-slate-600 dark:text-[#8b949e]">
                                    {subtitle}
                                </p>
                            )}
                        </div>
                    )}
                    {/* Breadcrumb Navigation */}
                    <Breadcrumbs />

                    {children}
                </main>
            </DashboardLayout>
        </>
    );
}

function HeaderContent({
    theme,
    tenant,
    environment,
    setTenant,
    setEnvironment,
    handleThemeChange,
}: {
    theme: "light" | "dark" | "system";
    tenant: string | null;
    environment: string;
    setTenant: (value: string | null) => void;
    setEnvironment: (value: string) => void;
    handleThemeChange: (mode: "light" | "dark" | "system") => void;
}) {
    const [showSecondaryMenu, setShowSecondaryMenu] = useState(false);
    const { persona, isRootUser, loginAsRootUser } = usePersona();

    return (
        <div className="bg-white dark:bg-neutral-800 border-b border-slate-200 dark:border-neutral-600 px-6 py-4">
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-4">
                    <h1 className="text-xl font-semibold text-slate-900 dark:text-neutral-100">
                        AI-First DevSecOps
                    </h1>
                    {/* Root User Badge */}
                    {isRootUser && (
                        <span className="px-2 py-1 text-xs font-bold bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300 rounded-full border border-red-300 dark:border-red-700">
                            ROOT USER
                        </span>
                    )}
                </div>

                <div className="flex items-center gap-3">
                    {/* Entry Point Selector - Shows all entry points for root_user */}
                    <EntryPointSelector />

                    {/* Global Search */}
                    <GlobalSearch />

                    {/* Real-Time Monitor Quick Access */}
                    <Link
                        to="/realtime-monitor"
                        className="px-3 py-2 text-sm font-medium text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-700 rounded transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-800"
                        title="Real-Time Monitor"
                        aria-label="Real-Time Monitor"
                    >
                        ⏱️
                    </Link>

                    {/* Help Quick Access */}
                    <Link
                        to="/help"
                        className="px-3 py-2 text-sm font-medium text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-700 rounded transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-slate-800"
                        title="Help Center"
                        aria-label="Help Center"
                    >
                        ❓
                    </Link>

                    {/* Environment Selector */}
                    <select
                        value={environment}
                        onChange={(e) => setEnvironment(e.target.value)}
                        className="px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded text-sm bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        aria-label="Select environment"
                    >
                        <option value="production">Production</option>
                        <option value="staging">Staging</option>
                        <option value="development">Development</option>
                    </select>

                    {/* Tenant Selector */}
                    <select
                        value={tenant || ""}
                        onChange={(e) => setTenant(e.target.value || null)}
                        className="px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded text-sm bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        aria-label="Select tenant"
                    >
                        <option value="">All Tenants</option>
                        <option value="tenant-1">Tenant 1</option>
                        <option value="tenant-2">Tenant 2</option>
                    </select>

                    {/* Theme Toggle */}
                    <select
                        value={theme}
                        onChange={(e) => handleThemeChange(e.target.value as "light" | "dark" | "system")}
                        className="px-3 py-2 border border-slate-300 dark:border-neutral-600 rounded text-sm bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        aria-label="Select theme"
                    >
                        <option value="system">System</option>
                        <option value="light">Light</option>
                        <option value="dark">Dark</option>
                    </select>

                    {/* Settings / User Menu */}
                    <div className="relative">
                        <button
                            onClick={() => setShowSecondaryMenu(!showSecondaryMenu)}
                            className="px-4 py-2 text-sm font-medium text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-700 rounded transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500"
                            aria-label="More options menu"
                        >
                            ⋯
                        </button>
                        {showSecondaryMenu && (
                            <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-neutral-700 border border-slate-200 dark:border-neutral-600 rounded shadow-lg z-50">
                                <Link
                                    to="/settings"
                                    className="block px-4 py-2 text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-600 focus:outline-none focus:bg-slate-100 dark:focus:bg-slate-600"
                                    onClick={() => setShowSecondaryMenu(false)}
                                >
                                    ⚙️ Settings
                                </Link>
                                <Link
                                    to="/export"
                                    className="block px-4 py-2 text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-600 focus:outline-none focus:bg-slate-100 dark:focus:bg-slate-600"
                                    onClick={() => setShowSecondaryMenu(false)}
                                >
                                    📤 Data Export
                                </Link>
                                <hr className="my-2 border-slate-200 dark:border-neutral-600" />
                                <button
                                    onClick={() => setShowSecondaryMenu(false)}
                                    className="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-600 focus:outline-none focus:bg-slate-100 dark:focus:bg-slate-600"
                                >
                                    👤 Account
                                </button>
                                {/* Development: Login as Root User */}
                                {import.meta.env.DEV && !isRootUser && (
                                    <>
                                        <hr className="my-2 border-slate-200 dark:border-neutral-600" />
                                        <button
                                            onClick={() => {
                                                loginAsRootUser();
                                                setShowSecondaryMenu(false);
                                            }}
                                            className="w-full text-left px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 focus:outline-none focus:bg-red-50 dark:focus:bg-red-900/20"
                                        >
                                            🔑 Login as Root User
                                        </button>
                                    </>
                                )}
                                {/* Show current persona */}
                                {persona && (
                                    <>
                                        <hr className="my-2 border-slate-200 dark:border-neutral-600" />
                                        <div className="px-4 py-2 text-xs text-slate-500 dark:text-slate-400">
                                            <div>Logged in as:</div>
                                            <div className="font-semibold text-slate-700 dark:text-slate-300">{persona.name}</div>
                                            <div className="capitalize">{persona.type}</div>
                                        </div>
                                    </>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}

function SidebarContent({
    sidebarCollapsed,
    toggleSidebar,
}: {
    sidebarCollapsed: boolean;
    toggleSidebar: () => void;
}) {
    const { canAccessRoute, hasAnyRole } = usePermissions();

    // Define navigation items with permission requirements
    // RESTRUCTURED: CEO Cockpit Architecture - Create, Manage, Operate, People

    const createItems = [
        { to: "/genesis", icon: "✨", label: "Genesis", show: hasAnyRole(['admin', 'lead']) },
    ];

    const manageItems = [
        { to: "/manage/org-chart", icon: "🏢", label: "Org Chart", show: hasAnyRole(['admin', 'lead']) },
        { to: "/manage/norms", icon: "📜", label: "Norms", show: hasAnyRole(['admin', 'lead']) },
        { to: "/manage/agents", icon: "🤖", label: "Agents", show: hasAnyRole(['admin', 'lead', 'engineer']) },
        { to: "/manage/budget", icon: "💰", label: "Budget", show: hasAnyRole(['admin', 'lead']) },
    ];

    const operateItems = [
        { to: "/", icon: "📊", label: "Dashboard", show: true },
        { to: "/operate/live-feed", icon: "📡", label: "Live Feed", show: true },
        { to: "/operate/tasks", icon: "📋", label: "Tasks", show: true },
        { to: "/operate/insights", icon: "💡", label: "Insights", show: true },
        { to: "/operate/incidents", icon: "🚨", label: "Incidents", show: true },
    ];

    const peopleItems = [
        { to: "/people/reviews", icon: "⭐", label: "Reviews", show: hasAnyRole(['admin', 'lead']) },
        { to: "/people/growth", icon: "🌱", label: "Growth", show: true },
    ];

    // Legacy config items (for admin access)
    const configItems = [
        { to: "/config/agents", icon: "⚙️", label: "Config: Agents", show: canAccessRoute('/admin') },
        { to: "/config/workflows", icon: "🔗", label: "Config: Workflows", show: canAccessRoute('/admin') },
        { to: "/admin/settings", icon: "🔧", label: "Settings", show: canAccessRoute('/admin') },
    ];

    // Filter items based on permissions
    const visibleCreateItems = createItems.filter(item => item.show);
    const visibleManageItems = manageItems.filter(item => item.show);
    const visibleOperateItems = operateItems.filter(item => item.show);
    const visiblePeopleItems = peopleItems.filter(item => item.show);
    const visibleConfigItems = configItems.filter(item => item.show);

    return (
        <>
            {/* Sidebar Header */}
            <div className="p-4 border-b border-slate-200 dark:border-neutral-600 bg-white dark:bg-neutral-800">
                {!sidebarCollapsed && (
                    <div className="font-bold text-lg text-slate-900 dark:text-neutral-100">
                        Ghatana
                    </div>
                )}
            </div>

            {/* Sidebar Navigation - CEO Cockpit Architecture */}
            <nav
                className="flex-1 p-4 space-y-6 overflow-y-auto bg-white dark:bg-neutral-800"
                aria-label="Main navigation"
                role="navigation"
            >
                {/* CREATE - Organization Genesis */}
                {visibleCreateItems.length > 0 && (
                    <div className="space-y-1">
                        {!sidebarCollapsed && (
                            <div className="text-xs font-semibold text-slate-500 dark:text-neutral-400 px-3 uppercase tracking-wide mb-2">
                                Create
                            </div>
                        )}
                        {visibleCreateItems.map(item => (
                            <NavLinkItem key={item.to} to={item.to} icon={item.icon} label={item.label} collapsed={sidebarCollapsed} />
                        ))}
                    </div>
                )}

                {/* MANAGE - Structure & Resources */}
                {visibleManageItems.length > 0 && (
                    <div className="space-y-1 border-t border-slate-200 dark:border-neutral-600 pt-4">
                        {!sidebarCollapsed && (
                            <div className="text-xs font-semibold text-slate-500 dark:text-neutral-400 px-3 uppercase tracking-wide mb-2">
                                Manage
                            </div>
                        )}
                        {visibleManageItems.map(item => (
                            <NavLinkItem key={item.to} to={item.to} icon={item.icon} label={item.label} collapsed={sidebarCollapsed} />
                        ))}
                    </div>
                )}

                {/* OPERATE - Day-to-day operations */}
                {visibleOperateItems.length > 0 && (
                    <div className="space-y-1 border-t border-slate-200 dark:border-neutral-600 pt-4">
                        {!sidebarCollapsed && (
                            <div className="text-xs font-semibold text-slate-500 dark:text-neutral-400 px-3 uppercase tracking-wide mb-2">
                                Operate
                            </div>
                        )}
                        {visibleOperateItems.map(item => (
                            <NavLinkItem key={item.to} to={item.to} icon={item.icon} label={item.label} collapsed={sidebarCollapsed} />
                        ))}
                    </div>
                )}

                {/* PEOPLE - Reviews & Growth */}
                {visiblePeopleItems.length > 0 && (
                    <div className="space-y-1 border-t border-slate-200 dark:border-neutral-600 pt-4">
                        {!sidebarCollapsed && (
                            <div className="text-xs font-semibold text-slate-500 dark:text-neutral-400 px-3 uppercase tracking-wide mb-2">
                                People
                            </div>
                        )}
                        {visiblePeopleItems.map(item => (
                            <NavLinkItem key={item.to} to={item.to} icon={item.icon} label={item.label} collapsed={sidebarCollapsed} />
                        ))}
                    </div>
                )}

                {/* CONFIG - Admin Settings (collapsed section) */}
                {visibleConfigItems.length > 0 && (
                    <div className="space-y-1 border-t border-slate-200 dark:border-neutral-600 pt-4">
                        {!sidebarCollapsed && (
                            <div className="text-xs font-semibold text-slate-500 dark:text-neutral-400 px-3 uppercase tracking-wide mb-2">
                                Admin
                            </div>
                        )}
                        {visibleConfigItems.map(item => (
                            <NavLinkItem key={item.to} to={item.to} icon={item.icon} label={item.label} collapsed={sidebarCollapsed} />
                        ))}
                    </div>
                )}
            </nav>

            {/* Sidebar Footer */}
            <div className="p-4 border-t border-slate-200 dark:border-neutral-600 bg-white dark:bg-neutral-800">
                <button
                    onClick={toggleSidebar}
                    className="w-full px-3 py-2 rounded text-sm font-medium text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-inset"
                    title={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
                    aria-label={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
                >
                    {sidebarCollapsed ? "→" : "←"}
                </button>
            </div>
        </>
    );
}

/**
 * Global context banner showing current tenant, environment, and region
 *
 * @doc.type component
 * @doc.purpose Display global context information
 * @doc.layer product
 * @doc.pattern Context Banner
 */
function GlobalContextBanner({
    tenant,
    environment,
}: {
    tenant: string | null;
    environment: string;
}) {
    // Only show if tenant is selected (not "All Tenants")
    if (!tenant) {
        return null;
    }

    // Map tenant ID to friendly name
    const tenantName = tenant === "tenant-1" ? "Tenant A" : tenant === "tenant-2" ? "Tenant B" : tenant;

    // Capitalize environment
    const envName = environment.charAt(0).toUpperCase() + environment.slice(1);

    return (
        <div className="bg-blue-50 dark:bg-indigo-600/30 border-b border-blue-200 dark:border-blue-800 px-6 py-2">
            <p className="text-sm text-blue-900 dark:text-blue-300">
                <span className="font-medium">Viewing:</span> {tenantName} • {envName}
            </p>
        </div>
    );
}

/**
 * Navigation link component for sidebar with active state highlighting
 *
 * @doc.type component
 * @doc.purpose Sidebar navigation link with active state
 * @doc.layer product
 * @doc.pattern Navigation Item
 */
function NavLinkItem({
    to,
    icon,
    label,
    collapsed,
}: {
    to: string;
    icon: string;
    label: string;
    collapsed: boolean;
}) {
    return (
        <NavLink
            to={to}
            className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2 rounded text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-inset ${isActive
                    ? "bg-blue-100 dark:bg-blue-900/50 text-blue-900 dark:text-blue-100"
                    : "text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-700"
                }`
            }
            title={collapsed ? label : undefined}
            end={to === "/"}
        >
            <span>{icon}</span>
            {!collapsed && <span>{label}</span>}
        </NavLink>
    );
}

export default MainLayout;
