/**
 * Manager Layout Component
 *
 * Layout wrapper for Manager persona dashboard and pages.
 * Uses @ghatana/design-system DashboardLayout with manager-specific navigation.
 *
 * @package @ghatana/software-org-web
 */

import { useNavigate, useLocation } from 'react-router';
import { DashboardLayout, AppSidebar } from '@ghatana/design-system';

/**
 * Navigation sections for Manager persona
 */
const managerSections = [
  {
    title: 'Overview',
    items: [
      { id: 'dashboard', label: 'Dashboard', icon: '📊', active: false },
      { id: 'my-team', label: 'My Team', icon: '👥', active: false },
      { id: 'workload', label: 'Workload', icon: '📋', active: false },
    ],
  },
  {
    title: 'Team',
    items: [
      { id: 'blockers', label: 'Blockers', icon: '🚫', badge: 2, active: false },
      { id: 'metrics', label: 'Team Metrics', icon: '📈', active: false },
      { id: 'department', label: 'Department', icon: '🏢', active: false },
    ],
  },
  {
    title: 'Requests',
    items: [
      { id: 'requests-pending', label: 'Pending Decisions', icon: '⏳', badge: 3, active: false },
      { id: 'requests-restructure', label: 'Restructure', icon: '✏️', active: false },
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
  'my-team': '/team',
  workload: '/team/workload',
  blockers: '/team/blockers',
  metrics: '/team/metrics',
  department: '/department',
  'requests-pending': '/requests/pending',
  'requests-restructure': '/requests/restructure',
  reports: '/reports',
  settings: '/settings',
};

export interface ManagerLayoutProps {
  /** Child components to render in main content area */
  children: React.ReactNode;
}

/**
 * Manager Layout Component
 *
 * Provides the standard layout for Manager persona pages.
 * Includes sidebar navigation, header, and content area.
 *
 * @example
 * ```tsx
 * <ManagerLayout>
 *   <ManagerDashboard />
 * </ManagerLayout>
 * ```
 */
export function ManagerLayout({ children }: ManagerLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();

  // Determine active item based on current path
  const activeItemId = Object.entries(ROUTE_MAP).find(
    ([, path]) => path === location.pathname
  )?.[0];

  // Update sections with active state
  const sectionsWithActive = managerSections.map((section) => ({
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
          subtitle="Manager"
          sections={sectionsWithActive}
          onNavigate={handleNavigate}
        />
      }
    >
      {children}
    </DashboardLayout>
  );
}
