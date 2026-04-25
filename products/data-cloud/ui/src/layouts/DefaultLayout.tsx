/**
 * Default Layout
 * 
 * Global layout wrapper with header, left sidebar navigation, and content area.
 * Provides consistent navigation and branding across all Data Cloud pages.
 * 
 * @doc.type layout
 * @doc.purpose Global page layout with sidebar navigation
 * @doc.layer layouts
 */

import React, { useState } from 'react';
import { Outlet, NavLink } from 'react-router';
import {
    Database,
    Workflow,
    Shield,
    Bell,
    Settings,
    Search,
    ChevronLeft,
    ChevronRight,
    Menu,
    X,
    Home,
    Brain,
    Terminal,
    Package,
    Command,
    Activity,
    Box,
    Network,
    Bot,
    ChevronDown,
} from 'lucide-react';
import { cn, bgStyles, borderStyles, textStyles } from '../lib/theme';
import SessionBootstrap, {
    type ProductViewMode,
    type ShellRole,
    PRODUCT_VIEW_MODES,
    PRODUCT_VIEW_MODE_DESCRIPTIONS,
    PRODUCT_VIEW_MODE_LABELS,
    SHELL_ROLE_CONTROL_LABEL,
    SHELL_ROLE_CONTROL_TITLE,
    SHELL_ROLE_DESCRIPTIONS,
    SHELL_ROLE_DISCLOSURE_NOTE,
    SHELL_ROLE_LABELS,
    SHELL_ROLES,
} from '../lib/auth/session';
import { GlobalSearch, useGlobalSearch } from '../components/common/GlobalSearch';
import { KeyboardShortcuts, useKeyboardShortcuts } from '../components/common/KeyboardShortcuts';
import { AiAssistant, useAiAssistant, AiAssistantTrigger } from '../components/ai/AiAssistant';
import { useNotificationCenter, NotificationTrigger, NotificationPanel } from '../components/notifications/NotificationCenter';
import { useWebSocketAutoConnect, useWebSocketState } from '../lib/websocket';
import { getDiscoverableRoutes } from '../lib/routing/RouteCapabilityRegistry';
import { OperationsProvider } from '../contexts/OperationsContext';
import { ActiveOperationsBar } from '../components/common/ActiveOperationsBar';

/**
 * Navigation section configuration
 */
interface NavSection {
    title: string;
    items: NavItem[];
}

interface NavItem {
    to: string;
    label: string;
    icon: React.ReactNode;
    exact?: boolean;
    minimumShellRole?: ShellRole;
}

function mapProductViewModeToShellRole(mode: ProductViewMode): ShellRole {
    if (mode === 'admin') {
        return 'admin';
    }
    if (mode === 'operator' || mode === 'steward' || mode === 'auditor') {
        return 'operator';
    }
    return 'primary-user';
}

/**
 * Navigation sections for Data Cloud — derived from canonical route registry.
 *
 * NOTE: Settings is removed from navigation and only accessible via direct link (/settings)
 * because it's a boundary shell with no writable backed features. See unsupportedSurfaceRegistry.
 *
 * Operator surfaces (Events, Alerts, Memory, Entities, Context, Fabric, Agents)
 * are restored as canonical first-class routes. Navigation is now generated from
 * the canonical RouteCapabilityRegistry to ensure shell disclosure always matches
 * route capability truth (RBAC-001).
 */
const navSections: NavSection[] = [
    {
        title: 'Core',
        items: [
            { to: '/', label: 'Home', icon: <Home className="h-4 w-4" />, exact: true },
            { to: '/data', label: 'Data', icon: <Database className="h-4 w-4" /> },
            { to: '/pipelines', label: 'Pipelines', icon: <Workflow className="h-4 w-4" /> },
            { to: '/query', label: 'Query', icon: <Terminal className="h-4 w-4" /> },
        ],
    },
    {
        // DC-UX-040: renamed from 'Intelligence' to 'Observability' — these are operational views, not an AI product surface
        title: 'Observability',
        items: [
            { to: '/insights', label: 'Insights', icon: <Brain className="h-4 w-4" />, minimumShellRole: 'operator' },
            { to: '/trust', label: 'Trust', icon: <Shield className="h-4 w-4" />, minimumShellRole: 'operator' },
            { to: '/events', label: 'Events', icon: <Activity className="h-4 w-4" />, minimumShellRole: 'operator' },
            { to: '/alerts', label: 'Alerts', icon: <Bell className="h-4 w-4" />, minimumShellRole: 'operator' },
        ],
    },
    {
        title: 'Manage',
        items: [
            { to: '/plugins', label: 'Plugins', icon: <Package className="h-4 w-4" />, minimumShellRole: 'operator' },
            { to: '/operations', label: 'Operations', icon: <Settings className="h-4 w-4" />, minimumShellRole: 'admin' },
        ],
    },
];

/**
 * Build navigation items from canonical route registry for a given role.
 * Ensures navigation always matches route capability truth (RBAC-001).
 */
export function buildNavFromRegistry(shellRole: ShellRole): NavSection[] {
    const discoverable = getDiscoverableRoutes(shellRole);

    const corePaths = new Set(['/', '/data', '/pipelines', '/query']);
    const intelPaths = new Set(['/insights', '/trust', '/events', '/alerts']);
    const managePaths = new Set(['/plugins', '/operations']);

    const coreItems: NavItem[] = discoverable
        .filter((r) => corePaths.has(r.path))
        .map((r) => ({
            to: r.path,
            label: r.label,
            icon: <Activity className="h-4 w-4" />,
            exact: r.path === '/',
        }));

    const intelItems: NavItem[] = discoverable
        .filter((r) => intelPaths.has(r.path))
        .map((r) => ({
            to: r.path,
            label: r.label,
            icon: <Activity className="h-4 w-4" />,
            minimumShellRole: r.minimumShellRole as ShellRole,
        }));

    const manageItems: NavItem[] = discoverable
        .filter((r) => managePaths.has(r.path))
        .map((r) => ({
            to: r.path,
            label: r.label,
            icon: <Activity className="h-4 w-4" />,
            minimumShellRole: r.minimumShellRole as ShellRole,
        }));

    return [
        ...(coreItems.length > 0 ? [{ title: 'Core', items: coreItems }] : []),
        ...(intelItems.length > 0 ? [{ title: 'Observability', items: intelItems }] : []),
        ...(manageItems.length > 0 ? [{ title: 'Manage', items: manageItems }] : []),
    ];
}

/**
 * DC-UX-002: Access control derived from canonical route registry.
 * `getDiscoverableRoutes` is the single source of truth for which paths are
 * visible for a given shell role. Presentation data (icons, labels, grouping)
 * stays in `navSections`; the static `minimumShellRole` on nav items is ignored.
 */
export function getNavigationSectionsForShellRole(role: ShellRole): NavSection[] {
    const accessiblePaths = new Set(getDiscoverableRoutes(role).map((r) => r.path));

    return navSections
        .map((section: NavSection) => ({
            ...section,
            items: section.items.filter((item: NavItem) => accessiblePaths.has(item.to)),
        }))
        .filter((section: NavSection) => section.items.length > 0);
}

/**
 * Sidebar component
 */
function Sidebar({
    isCollapsed,
    onToggle,
    isMobileOpen,
    onMobileClose,
    shellRole,
}: {
    isCollapsed: boolean;
    onToggle: () => void;
    isMobileOpen: boolean;
    onMobileClose: () => void;
    shellRole: ShellRole;
}) {
    const visibleSections = getNavigationSectionsForShellRole(shellRole);

    return (
        <>
            {/* Mobile overlay */}
            {isMobileOpen && (
                <div
                    className="fixed inset-0 bg-black/50 z-40 lg:hidden"
                    onClick={onMobileClose}
                />
            )}

            {/* Sidebar */}
            <aside
                className={cn(
                    'fixed top-0 left-0 z-50 h-full transition-all duration-300 flex flex-col border-r',
                    bgStyles.surface,
                    borderStyles.divider,
                    isCollapsed ? 'w-16' : 'w-64',
                    // Mobile: slide in/out
                    'lg:translate-x-0',
                    isMobileOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'
                )}
            >
                {/* Header */}
                <div className={cn('h-16 flex items-center justify-between px-4 border-b', borderStyles.divider)}>
                    {!isCollapsed && (
                        <div className="flex items-center gap-3">
                            <div className="w-8 h-8 bg-primary-600 rounded-lg flex items-center justify-center">
                                <Database className="h-4 w-4 text-white" />
                            </div>
                            <span className={cn(textStyles.h4, 'font-semibold')}>
                                Data Cloud
                            </span>
                        </div>
                    )}
                    {isCollapsed && (
                        <div className="w-8 h-8 bg-primary-600 rounded-lg flex items-center justify-center mx-auto">
                            <Database className="h-4 w-4 text-white" />
                        </div>
                    )}

                    {/* Mobile close button */}
                    <button
                        type="button"
                        onClick={onMobileClose}
                        className={cn('lg:hidden p-1 rounded-md hover:bg-gray-100 dark:hover:bg-gray-800', textStyles.muted)}
                    >
                        <X className="h-5 w-5" />
                    </button>
                </div>

                {/* Navigation */}
                <nav className="flex-1 overflow-y-auto py-4 px-2">
                    {visibleSections.map((section) => (
                        <div key={section.title} className="mb-6">
                            {!isCollapsed && (
                                <h3 className={cn(textStyles.xs, 'px-3 mb-2 font-semibold uppercase tracking-wider')}>
                                    {section.title}
                                </h3>
                            )}
                            <div className="space-y-1">
                                {section.items.map((item) => (
                                    <NavLink
                                        key={item.to}
                                        to={item.to}
                                        end={item.exact}
                                        onClick={onMobileClose}
                                        className={({ isActive }) =>
                                            cn(
                                                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                                                isActive
                                                    ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/50 dark:text-primary-300'
                                                    : cn(textStyles.body, 'hover:bg-gray-100 dark:hover:bg-gray-800'),
                                                isCollapsed && 'justify-center'
                                            )
                                        }
                                        title={isCollapsed ? item.label : undefined}
                                    >
                                        {item.icon}
                                        {!isCollapsed && <span>{item.label}</span>}
                                    </NavLink>
                                ))}
                            </div>
                        </div>
                    ))}
                </nav>

                {/* Collapse toggle (desktop only) */}
                <div className={cn('hidden lg:block p-2 border-t', borderStyles.divider)}>
                    <button
                        type="button"
                        onClick={onToggle}
                        className={cn(
                            'w-full flex items-center justify-center gap-2 px-3 py-2 rounded-lg transition-colors hover:bg-gray-100 dark:hover:bg-gray-800',
                            textStyles.small
                        )}
                    >
                        {isCollapsed ? (
                            <ChevronRight className="h-4 w-4" />
                        ) : (
                            <>
                                <ChevronLeft className="h-4 w-4" />
                                <span>Collapse</span>
                            </>
                        )}
                    </button>
                </div>
            </aside>
        </>
    );
}

/**
 * Header component
 */
function Header({
    onMenuClick,
    onSearchClick,
    notificationCenter,
    shellRole,
    onShellRoleChange,
    productViewMode,
    onProductViewModeChange,
}: {
    onMenuClick: () => void;
    onSearchClick: () => void;
    notificationCenter: ReturnType<typeof useNotificationCenter>;
    shellRole: ShellRole;
    onShellRoleChange: (role: ShellRole) => void;
    productViewMode: ProductViewMode;
    onProductViewModeChange: (mode: ProductViewMode) => void;
}) {
    const [isRoleMenuOpen, setIsRoleMenuOpen] = useState(false);

    return (
        <header className={cn('h-16 sticky top-0 z-30 border-b', bgStyles.surface, borderStyles.divider)}>
            <div className="h-full px-4 flex items-center justify-between">
                {/* Left side */}
                <div className="flex items-center gap-4">
                    {/* Mobile menu button */}
                    <button
                        type="button"
                        onClick={onMenuClick}
                        className={cn('lg:hidden p-2 rounded-md hover:bg-gray-100 dark:hover:bg-gray-800', textStyles.muted)}
                    >
                        <Menu className="h-5 w-5" />
                    </button>

                    {/* Search button */}
                    <button
                        type="button"
                        onClick={onSearchClick}
                        className={cn(
                            'flex items-center gap-2 px-3 py-1.5 text-sm rounded-lg transition-colors',
                            textStyles.muted,
                            bgStyles.surfaceSecondary,
                            'hover:bg-gray-200 dark:hover:bg-gray-600'
                        )}
                    >
                        <Search className="h-4 w-4" />
                        <span className="hidden sm:inline">Search...</span>
                        <kbd className="hidden sm:inline-flex items-center gap-1 px-1.5 py-0.5 text-xs font-medium bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded">
                            <Command className="h-3 w-3" />K
                        </kbd>
                    </button>
                </div>

                {/* Right side */}
                <div className="flex items-center gap-3">
                    {/* Notification Center */}
                    <div className="relative">
                        <NotificationTrigger
                            unreadCount={notificationCenter.unreadCount}
                            onClick={() => notificationCenter.setIsOpen(!notificationCenter.isOpen)}
                        />
                        {notificationCenter.isOpen && (
                            <NotificationPanel
                                notifications={notificationCenter.notifications}
                                onRead={notificationCenter.markAsRead}
                                onReadAll={notificationCenter.markAllAsRead}
                                onDismiss={notificationCenter.dismiss}
                                onClose={() => notificationCenter.setIsOpen(false)}
                            />
                        )}
                    </div>

                    {/* User menu */}
                    <div className="relative">
                        <button
                            type="button"
                            className="flex h-8 items-center gap-2 rounded-full bg-gray-200 px-3 text-gray-600 transition-colors hover:bg-gray-300 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600"
                            aria-label={SHELL_ROLE_CONTROL_LABEL}
                            onClick={() => setIsRoleMenuOpen((current) => !current)}
                        >
                            <span className="text-sm font-medium">{PRODUCT_VIEW_MODE_LABELS[productViewMode]}</span>
                            <ChevronDown className="h-4 w-4" />
                        </button>

                        {isRoleMenuOpen && (
                            <div className="absolute right-0 mt-2 w-64 rounded-xl border border-gray-200 bg-white p-2 shadow-lg dark:border-gray-700 dark:bg-gray-800">
                                <div className="px-2 pb-2 pt-1">
                                    <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
                                        {SHELL_ROLE_CONTROL_TITLE}
                                    </p>
                                    <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                                        {SHELL_ROLE_DISCLOSURE_NOTE}
                                    </p>
                                </div>
                                <div className="space-y-1">
                                    <div className="px-2 pb-1 pt-2">
                                        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
                                            Product mode
                                        </p>
                                        <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                                            Product mode is a UI focus preset. It does not grant backend permissions.
                                        </p>
                                    </div>
                                    {PRODUCT_VIEW_MODES.map((mode) => {
                                        const isSelected = mode === productViewMode;
                                        return (
                                            <button
                                                key={mode}
                                                type="button"
                                                onClick={() => {
                                                    onProductViewModeChange(mode);
                                                    setIsRoleMenuOpen(false);
                                                }}
                                                className={cn(
                                                    'w-full rounded-lg px-3 py-2 text-left text-sm transition-colors',
                                                    isSelected
                                                        ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300'
                                                        : 'text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-700'
                                                )}
                                            >
                                                <div className="font-medium">{PRODUCT_VIEW_MODE_LABELS[mode]}</div>
                                                <div className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                                                    {PRODUCT_VIEW_MODE_DESCRIPTIONS[mode]}
                                                </div>
                                            </button>
                                        );
                                    })}

                                    <div className="my-1 h-px bg-gray-200 dark:bg-gray-700" />

                                    {SHELL_ROLES.map((role) => {
                                        const isSelected = role === shellRole;
                                        return (
                                            <button
                                                key={role}
                                                type="button"
                                                onClick={() => {
                                                    onShellRoleChange(role);
                                                    setIsRoleMenuOpen(false);
                                                }}
                                                className={cn(
                                                    'w-full rounded-lg px-3 py-2 text-left text-sm transition-colors',
                                                    isSelected
                                                        ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/40 dark:text-primary-300'
                                                        : 'text-gray-700 hover:bg-gray-100 dark:text-gray-200 dark:hover:bg-gray-700'
                                                )}
                                            >
                                                <div className="font-medium">{SHELL_ROLE_LABELS[role]}</div>
                                                <div className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                                                    {SHELL_ROLE_DESCRIPTIONS[role]}
                                                </div>
                                            </button>
                                        );
                                    })}
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </header>
    );
}

/**
 * Default Layout Component
 * 
 * Provides the main application layout with:
 * - Collapsible left sidebar navigation
 * - Top header with search and user menu
 * - Main content area with outlet
 * - Global features (search, shortcuts, AI assistant)
 */
export default function DefaultLayout(): React.ReactElement {
    const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
    const [shellRole, setShellRole] = useState<ShellRole>(() => SessionBootstrap.getShellRole());
    const [productViewMode, setProductViewMode] = useState<ProductViewMode>(() => SessionBootstrap.getProductViewMode());

    const globalSearch = useGlobalSearch();
    const keyboardShortcuts = useKeyboardShortcuts();
    const aiAssistant = useAiAssistant();
    const notificationCenter = useNotificationCenter();
    const wsState = useWebSocketState();

    const wsEnabled = import.meta.env.PROD || Boolean(import.meta.env.VITE_WS_URL);

    // Auto-connect WebSocket
    useWebSocketAutoConnect();

    return (
        <OperationsProvider>
        <div className={cn('min-h-screen', bgStyles.page)}>
            {/* Sidebar */}
            <Sidebar
                isCollapsed={sidebarCollapsed}
                onToggle={() => setSidebarCollapsed(!sidebarCollapsed)}
                isMobileOpen={mobileMenuOpen}
                onMobileClose={() => setMobileMenuOpen(false)}
                shellRole={shellRole}
            />

            {/* Main area */}
            <div
                className={cn(
                    'transition-all duration-300',
                    sidebarCollapsed ? 'lg:ml-16' : 'lg:ml-64'
                )}
            >
                {/* Header */}
                <Header
                    onMenuClick={() => setMobileMenuOpen(true)}
                    onSearchClick={globalSearch.open}
                    notificationCenter={notificationCenter}
                    shellRole={shellRole}
                    onShellRoleChange={(role) => {
                        SessionBootstrap.setShellRole(role);
                        setShellRole(role);
                    }}
                    productViewMode={productViewMode}
                    onProductViewModeChange={(mode) => {
                        const mappedRole = mapProductViewModeToShellRole(mode);
                        SessionBootstrap.setProductViewMode(mode);
                        SessionBootstrap.setShellRole(mappedRole);
                        setProductViewMode(mode);
                        setShellRole(mappedRole);
                    }}
                />

                {/* Main content */}
                <main className="p-6">
                    <Outlet />
                </main>

                {/* Footer */}
                <footer className={cn('px-6 py-4 border-t', borderStyles.divider)}>
                    <p className={cn(textStyles.small, 'text-center')}>
                        Data Cloud • Ghatana Platform
                    </p>
                </footer>
            </div>

            {/* Global Search Modal */}
            <GlobalSearch
                isOpen={globalSearch.isOpen}
                onClose={globalSearch.close}
            />

            {/* Keyboard Shortcuts Modal */}
            <KeyboardShortcuts
                isOpen={keyboardShortcuts.isOpen}
                onClose={keyboardShortcuts.close}
            />

            {/* AI Assistant */}
            {aiAssistant.isOpen ? (
                <AiAssistant
                    isOpen={aiAssistant.isOpen}
                    onClose={aiAssistant.close}
                />
            ) : (
                <AiAssistantTrigger onClick={aiAssistant.open} />
            )}

            {/* WebSocket Status Indicator (dev only) */}
            {import.meta.env.DEV && wsEnabled && wsState !== 'connected' && (
                <div className={cn(
                    'fixed bottom-4 left-4 z-40 px-3 py-1.5 rounded-full text-xs font-medium',
                    sidebarCollapsed ? 'lg:left-20' : 'lg:left-68',
                    wsState === 'connecting' && 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/50 dark:text-yellow-300',
                    wsState === 'reconnecting' && 'bg-orange-100 text-orange-800 dark:bg-orange-900/50 dark:text-orange-300',
                    wsState === 'disconnected' && 'bg-red-100 text-red-800 dark:bg-red-900/50 dark:text-red-300'
                )}>
                    WS: {wsState}
                </div>
            )}

            {/* Active Operations Bar (DC-UX-046) */}
            <ActiveOperationsBar />
        </div>
        </OperationsProvider>
    );
}

export { DefaultLayout };
