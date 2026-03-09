import React, { useState } from 'react';

/**
 * NavigationSidebar - Main navigation menu component.
 *
 * <p><b>Purpose</b><br>
 * Provides collapsible sidebar navigation with feature and section grouping.
 * Highlights current route and supports nested navigation.
 *
 * <p><b>Features</b><br>
 * - Collapsible menu sections
 * - Current route highlighting
 * - Nested navigation items
 * - Responsive collapse on mobile
 * - Dark mode support
 * - Icon support for each item
 *
 * <p><b>Usage</b><br>
 * ```tsx
 * <NavigationSidebar
 *   currentPath="/dashboard"
 *   onNavigate={(path) => navigate(path)}
 * />
 * ```
 *
 * @doc.type component
 * @doc.purpose Main navigation sidebar
 * @doc.layer product
 * @doc.pattern Organism
 */

interface NavItem {
    label: string;
    icon: string;
    path?: string;
    items?: NavItem[];
}

interface NavigationSidebarProps {
    currentPath?: string;
    onNavigate?: (path: string) => void;
    collapsed?: boolean;
}

const navItems: NavItem[] = [
    {
        label: 'Dashboard',
        icon: '📊',
        path: '/dashboard',
    },
    {
        label: 'Organization',
        icon: '🏢',
        items: [
            { label: 'Departments', icon: '👥', path: '/departments' },
            { label: 'Teams', icon: '👨‍💼', path: '/teams' },
            { label: 'Structure', icon: '📐', path: '/org-structure' },
        ],
    },
    {
        label: 'Operations',
        icon: '⚙️',
        items: [
            { label: 'Workflows', icon: '🔄', path: '/workflows' },
            { label: 'Events', icon: '📡', path: '/events' },
            { label: 'Incidents', icon: '🚨', path: '/incidents' },
        ],
    },
    {
        label: 'Analytics',
        icon: '📈',
        items: [
            { label: 'Reports', icon: '📋', path: '/reporting' },
            { label: 'Audit Trail', icon: '📜', path: '/audit' },
            { label: 'Metrics', icon: '📊', path: '/metrics' },
            { label: 'KPIs', icon: '🎯', path: '/kpis' },
        ],
    },
    {
        label: 'ML & AI',
        icon: '🤖',
        items: [
            { label: 'Models', icon: '🧠', path: '/models' },
            { label: 'Simulator', icon: '⚗️', path: '/simulator' },
            { label: 'Insights', icon: '💡', path: '/insights' },
        ],
    },
    {
        label: 'Security',
        icon: '🔒',
        path: '/security',
    },
    {
        label: 'Settings',
        icon: '⚙️',
        path: '/settings',
    },
    {
        label: 'Help',
        icon: '❓',
        path: '/help',
    },
];

interface NavItemRendererProps {
    item: NavItem;
    currentPath?: string;
    onNavigate?: (path: string) => void;
    collapsed?: boolean;
    level?: number;
}

const NavItemRenderer = React.memo(function NavItemRenderer({
    item,
    currentPath,
    onNavigate,
    collapsed = false,
    level = 0,
}: NavItemRendererProps) {
    const [expanded, setExpanded] = useState(false);
    const isActive = currentPath === item.path;

    if (item.items) {
        return (
            <div key={item.label}>
                <button
                    onClick={() => setExpanded(!expanded)}
                    className={`w-full flex items-center gap-3 px-4 py-2 text-sm rounded-lg transition-colors ${collapsed
                        ? 'justify-center'
                        : 'justify-between hover:bg-slate-100 dark:hover:bg-slate-700'
                        } text-slate-700 dark:text-neutral-300`}
                    aria-expanded={expanded}
                >
                    <div className="flex items-center gap-3">
                        <span className="text-lg">{item.icon}</span>
                        {!collapsed && <span>{item.label}</span>}
                    </div>
                    {!collapsed && <span className="text-xs">{expanded ? '▼' : '▶'}</span>}
                </button>

                {expanded && !collapsed && (
                    <div className="pl-4 space-y-1">
                        {item.items.map((subItem) => (
                            <NavItemRenderer
                                key={subItem.label}
                                item={subItem}
                                currentPath={currentPath}
                                onNavigate={onNavigate}
                                collapsed={collapsed}
                                level={(level || 0) + 1}
                            />
                        ))}
                    </div>
                )}
            </div>
        );
    }

    return (
        <button
            key={item.label}
            onClick={() => {
                onNavigate?.(item.path || '');
            }}
            className={`w-full flex items-center gap-3 px-4 py-2 text-sm rounded-lg transition-colors ${isActive
                ? 'bg-blue-500 text-white'
                : 'text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-700'
                } ${collapsed ? 'justify-center' : ''}`}
            title={collapsed ? item.label : undefined}
        >
            <span className="text-lg">{item.icon}</span>
            {!collapsed && <span>{item.label}</span>}
        </button>
    );
});

export const NavigationSidebar = React.memo(function NavigationSidebar({
    currentPath,
    onNavigate,
    collapsed: initialCollapsed = false,
}: NavigationSidebarProps) {
    const [collapsed, setCollapsed] = useState(initialCollapsed);

    return (
        <aside
            className={`flex flex-col gap-4 bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-neutral-600 transition-all duration-300 ${collapsed ? 'w-20' : 'w-64'
                }`}
        >
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-4 border-b border-slate-200 dark:border-neutral-600">
                {!collapsed && (
                    <h1 className="font-bold text-lg text-slate-900 dark:text-neutral-100">Ghatana</h1>
                )}
                <button
                    onClick={() => setCollapsed(!collapsed)}
                    className="p-2 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
                    aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
                >
                    {collapsed ? '→' : '←'}
                </button>
            </div>

            {/* Navigation Items */}
            <nav className="flex-1 px-2 space-y-1 overflow-y-auto">
                {navItems.map((item) => (
                    <NavItemRenderer
                        key={item.label}
                        item={item}
                        currentPath={currentPath}
                        onNavigate={onNavigate}
                        collapsed={collapsed}
                    />
                ))}
            </nav>

            {/* Footer */}
            <div className="border-t border-slate-200 dark:border-neutral-600 px-4 py-4">
                <button
                    className={`w-full flex items-center gap-3 px-4 py-2 text-sm rounded-lg text-slate-700 dark:text-neutral-300 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors ${collapsed ? 'justify-center' : ''
                        }`}
                    title={collapsed ? 'Profile' : undefined}
                >
                    <span className="text-lg">👤</span>
                    {!collapsed && <span>Profile</span>}
                </button>
            </div>
        </aside>
    );
});

export default NavigationSidebar;
