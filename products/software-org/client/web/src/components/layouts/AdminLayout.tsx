/**
 * Admin Layout Component
 *
 * Layout wrapper for Admin persona dashboard and pages.
 * System administration and maintenance focus.
 *
 * @package @ghatana/software-org-web
 */

import { useNavigate, useLocation } from 'react-router';
import { DashboardLayout, AppSidebar } from '@ghatana/design-system';

/**
 * Navigation sections for Admin persona
 */
const adminSections = [
  {
    title: 'System',
    items: [
      { id: 'dashboard', label: 'Dashboard', icon: '📊', active: false },
      { id: 'system-health', label: 'System Health', icon: '💓', active: false },
      { id: 'access-control', label: 'Access Control', icon: '🔐', active: false },
      { id: 'data-integrity', label: 'Data Integrity', icon: '✓', active: false },
    ],
  },
  {
    title: 'Configuration',
    items: [
      { id: 'org-templates', label: 'Org Templates', icon: '📋', active: false },
      { id: 'persona-rules', label: 'Persona Rules', icon: '📜', active: false },
      { id: 'config', label: 'Configuration', icon: '🔧', active: false },
    ],
  },
  {
    title: 'Monitoring',
    items: [
      { id: 'audit-log', label: 'Audit Log', icon: '📜', active: false },
      { id: 'pending-approvals', label: 'Approvals', icon: '⏳', badge: 4, active: false },
      { id: 'simulator', label: 'Event Simulator', icon: '🧪', active: false },
      { id: 'monitoring', label: 'Real-time Monitor', icon: '📡', active: false },
    ],
  },
  {
    title: 'Other',
    items: [
      { id: 'settings', label: 'Settings', icon: '⚙️', active: false },
    ],
  },
];

/** Route mapping for navigation items */
const ROUTE_MAP: Record<string, string> = {
  dashboard: '/',
  'system-health': '/system/health',
  'access-control': '/system/access',
  'data-integrity': '/system/integrity',
  'org-templates': '/org-templates',
  'persona-rules': '/persona-rules',
  config: '/system/config',
  'audit-log': '/audit',
  'pending-approvals': '/approvals',
  simulator: '/simulator',
  monitoring: '/realtime-monitor',
  settings: '/settings',
};

export interface AdminLayoutProps {
  /** Child components to render in main content area */
  children: React.ReactNode;
}

/**
 * Admin Layout Component
 *
 * System administration layout with comprehensive monitoring
 * and maintenance tools.
 *
 * @example
 * ```tsx
 * <AdminLayout>
 *   <AdminDashboard />
 * </AdminLayout>
 * ```
 */
export function AdminLayout({ children }: AdminLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();

  // Determine active item based on current path
  const activeItemId = Object.entries(ROUTE_MAP).find(
    ([, path]) => path === location.pathname
  )?.[0];

  // Update sections with active state
  const sectionsWithActive = adminSections.map((section) => ({
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
          subtitle="Admin"
          sections={sectionsWithActive}
          onNavigate={handleNavigate}
        />
      }
    >
      {children}
    </DashboardLayout>
  );
}
