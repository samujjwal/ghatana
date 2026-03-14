/**
 * IC (Individual Contributor) Layout Component
 *
 * Layout wrapper for IC persona dashboard and pages.
 * Simple, focused navigation for individual contributors.
 *
 * @package @ghatana/software-org-web
 */

import { useNavigate, useLocation } from 'react-router';
import { DashboardLayout, AppSidebar } from '@ghatana/design-system';

/**
 * Navigation sections for IC persona
 * Keep it simple and focused on individual work
 */
const icSections = [
  {
    title: 'Work',
    items: [
      { id: 'dashboard', label: 'Dashboard', icon: '📊', active: false },
      { id: 'tasks', label: 'My Tasks', icon: '✅', badge: 5, active: false },
    ],
  },
  {
    title: 'Growth & Time',
    items: [
      { id: 'growth', label: 'Growth Plan', icon: '📈', active: false },
      { id: 'time-off', label: 'Time Off', icon: '📅', active: false },
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
  dashboard: '/ic',
  tasks: '/ic/tasks',
  growth: '/ic/growth',
  'time-off': '/ic/time-off',
  settings: '/ic/settings',
};

export interface ICLayoutProps {
  /** Child components to render in main content area */
  children: React.ReactNode;
}

/**
 * IC Layout Component
 *
 * Clean, simple layout for individual contributors.
 * Focus on personal productivity and tasks.
 *
 * @example
 * ```tsx
 * <ICLayout>
 *   <ICDashboard />
 * </ICLayout>
 * ```
 */
export function ICLayout({ children }: ICLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();

  // Determine active item based on current path
  const activeItemId = Object.entries(ROUTE_MAP).find(
    ([, path]) => path === location.pathname
  )?.[0];

  // Update sections with active state
  const sectionsWithActive = icSections.map((section) => ({
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
          subtitle="Contributor"
          sections={sectionsWithActive}
          onNavigate={handleNavigate}
        />
      }
    >
      {children}
    </DashboardLayout>
  );
}
