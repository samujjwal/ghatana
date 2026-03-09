/**
 * Executive Layout Component
 *
 * Layout wrapper for Executive persona (CTO, CPO, etc.) dashboard and pages.
 * Uses @ghatana/ui DashboardLayout with executive-specific navigation.
 *
 * @package @ghatana/software-org-web
 */

import React from 'react';
import { useNavigate, useLocation } from 'react-router';
import { DashboardLayout, AppSidebar } from '@ghatana/ui';

/**
 * Navigation sections for Executive persona
 */
const executiveSections = [
    {
        title: 'Overview',
        items: [
            { id: 'dashboard', label: 'Dashboard', icon: '📊', active: false },
            { id: 'org-overview', label: 'Organization', icon: '🏢', active: false },
            { id: 'departments', label: 'Departments', icon: '🏛️', active: false },
        ],
    },
    {
        title: 'Strategy',
        items: [
            { id: 'strategy', label: 'Strategy', icon: '🎯', active: false },
            { id: 'roadmap', label: 'Roadmap', icon: '🗺️', active: false },
            { id: 'budget', label: 'Budget', icon: '💰', active: false },
        ],
    },
    {
        title: 'Operations',
        items: [
            { id: 'staffing', label: 'Staffing', icon: '👥', active: false },
            { id: 'performance', label: 'Performance', icon: '📈', active: false },
            { id: 'risks', label: 'Risks', icon: '⚠️', active: false },
        ],
    },
    {
        title: 'Actions',
        items: [
            { id: 'approvals', label: 'Approvals', icon: '✅', badge: 3, active: false },
            { id: 'escalations', label: 'Escalations', icon: '🔺', badge: 2, active: false },
        ],
    },
    {
        title: 'Other',
        items: [
            { id: 'reports', label: 'Reports', icon: '📄', active: false },
            { id: 'settings', label: 'Settings', icon: '⚙️', active: false },
        ],
    },
];

/** Route mapping for navigation items */
const ROUTE_MAP: Record<string, string> = {
    dashboard: '/',
    'org-overview': '/org/overview',
    departments: '/departments',
    strategy: '/strategy',
    roadmap: '/roadmap',
    budget: '/budget',
    staffing: '/staffing',
    performance: '/performance',
    risks: '/risks',
    approvals: '/approvals',
    escalations: '/escalations',
    reports: '/reports',
    settings: '/settings',
};

export interface ExecutiveLayoutProps {
    /** Child components to render in main content area */
    children: React.ReactNode;
}

/**
 * Executive Layout Component
 *
 * Provides the standard layout for Executive persona pages.
 * Includes sidebar navigation, header, and content area.
 *
 * @example
 * ```tsx
 * <ExecutiveLayout>
 *   <ExecutiveDashboard />
 * </ExecutiveLayout>
 * ```
 */
export function ExecutiveLayout({ children }: ExecutiveLayoutProps) {
    const navigate = useNavigate();
    const location = useLocation();

    // Determine active item based on current path
    const activeItemId = Object.entries(ROUTE_MAP).find(
        ([, path]) => path === location.pathname
    )?.[0];

    // Update sections with active state
    const sectionsWithActive = executiveSections.map((section) => ({
        ...section,
        items: section.items.map((item) => ({
            ...item,
            active: item.id === activeItemId,
        })),
    }));

    /**
     * Handle navigation item click
     */
    const handleNavigate = (itemId: string) => {
        const path = ROUTE_MAP[itemId];
        if (path) {
            navigate(path);
        }
    };

    return (
        <DashboardLayout
            sidebar={
                <AppSidebar
                    title="Software-Org"
                    subtitle="Executive"
                    sections={sectionsWithActive}
                    onNavigate={handleNavigate}
                />
            }
        >
            {children}
        </DashboardLayout>
    );
}
