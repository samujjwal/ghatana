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
    Sparkles,
    Home,
    Brain,
    Terminal,
    Package,
    Command,
    Activity,
    Box,
    Network,
    Bot,
} from 'lucide-react';
import { cn, bgStyles, borderStyles, textStyles } from '../lib/theme';
import { GlobalSearch, useGlobalSearch } from '../components/common/GlobalSearch';
import { KeyboardShortcuts, useKeyboardShortcuts } from '../components/common/KeyboardShortcuts';
import { AiAssistant, useAiAssistant, AiAssistantTrigger } from '../components/ai/AiAssistant';
import { useWebSocketAutoConnect, useWebSocketState } from '../lib/websocket';

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
}

/**
 * Navigation sections for Data Cloud
 * Simplified structure matching consolidated routes
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
        title: 'Intelligence',
        items: [
            { to: '/trust', label: 'Trust', icon: <Shield className="h-4 w-4" /> },
            { to: '/insights', label: 'Insights', icon: <Brain className="h-4 w-4" /> },
        ],
    },
    {
        title: 'Operations',
        items: [
            { to: '/alerts', label: 'Alerts', icon: <Bell className="h-4 w-4" /> },
            { to: '/plugins', label: 'Plugins', icon: <Package className="h-4 w-4" /> },
            { to: '/settings', label: 'Settings', icon: <Settings className="h-4 w-4" /> },
        ],
    },
    {
        title: 'Agentic',
        items: [
            { to: '/events',   label: 'Events',   icon: <Activity className="h-4 w-4" /> },
            { to: '/memory',   label: 'Memory',   icon: <Box className="h-4 w-4" /> },
            { to: '/entities', label: 'Entities', icon: <Database className="h-4 w-4" /> },
            { to: '/fabric',   label: 'Fabric',   icon: <Network className="h-4 w-4" /> },
            { to: '/agents',   label: 'Agents',   icon: <Bot className="h-4 w-4" /> },
        ],
    },
];

/**
 * Sidebar component
 */
function Sidebar({
    isCollapsed,
    onToggle,
    isMobileOpen,
    onMobileClose,
}: {
    isCollapsed: boolean;
    onToggle: () => void;
    isMobileOpen: boolean;
    onMobileClose: () => void;
}) {
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
                    {navSections.map((section) => (
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
}: {
    onMenuClick: () => void;
    onSearchClick: () => void;
}) {
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
                    {/* AI Assistant indicator */}
                    <div className="flex items-center gap-2 px-2 py-1 text-xs font-medium text-primary-600 dark:text-primary-400 bg-primary-50 dark:bg-primary-900/30 rounded-full">
                        <Sparkles className="h-3 w-3" />
                        <span className="hidden sm:inline">AI Powered</span>
                    </div>

                    {/* User menu */}
                    <button
                        type="button"
                        className="w-8 h-8 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center text-gray-600 dark:text-gray-300 hover:bg-gray-300 dark:hover:bg-gray-600 transition-colors"
                        aria-label="User menu"
                    >
                        <span className="text-sm font-medium">U</span>
                    </button>
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

    const globalSearch = useGlobalSearch();
    const keyboardShortcuts = useKeyboardShortcuts();
    const aiAssistant = useAiAssistant();
    const wsState = useWebSocketState();

    const wsEnabled = import.meta.env.PROD || Boolean(import.meta.env.VITE_WS_URL);

    // Auto-connect WebSocket
    useWebSocketAutoConnect();

    return (
        <div className={cn('min-h-screen', bgStyles.page)}>
            {/* Sidebar */}
            <Sidebar
                isCollapsed={sidebarCollapsed}
                onToggle={() => setSidebarCollapsed(!sidebarCollapsed)}
                isMobileOpen={mobileMenuOpen}
                onMobileClose={() => setMobileMenuOpen(false)}
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
        </div>
    );
}

export { DefaultLayout };
