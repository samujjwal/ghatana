/**
 * Owner Layout Component
 *
 * Layout wrapper for Owner persona dashboard and pages.
 * Uses @ghatana/design-system DashboardLayout with owner-specific navigation.
 *
 * @package @ghatana/software-org-web
 */

import { useNavigate, useLocation } from 'react-router';
import { DashboardLayout, AppSidebar } from '@ghatana/design-system';

/**
 * Navigation sections for Owner persona
 */
const ownerSections = [
  {
    title: 'Overview',
    items: [
      { id: 'dashboard', label: 'Dashboard', icon: '📊', active: false },
      { id: 'org-structure', label: 'Org Structure', icon: '🏢', active: false },
      { id: 'restructure', label: 'Restructure', icon: '✏️', active: false },
    ],
  },
  {
    title: 'Management',
    items: [
      { id: 'personas', label: 'Personas & Roles', icon: '👥', active: false },
      { id: 'access-matrix', label: 'Access Matrix', icon: '🔒', active: false },
    ],
  },
  {
    title: 'Insights',
    items: [
      { id: 'insights', label: 'Staffing', icon: '📈', active: false },
      { id: 'initiatives', label: 'Initiatives', icon: '🎯', active: false },
      { id: 'risks', label: 'Risks', icon: '⚠️', active: false },
    ],
  },
  {
    title: 'Actions',
    items: [
      { id: 'approvals', label: 'Approvals', icon: '✅', badge: 3, active: false },
      { id: 'audit', label: 'Audit Log', icon: '📜', active: false },
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
  'org-structure': '/org/overview',
  restructure: '/org/restructure',
  personas: '/org/personas',
  'access-matrix': '/org/access-matrix',
  insights: '/insights/staffing',
  initiatives: '/insights/initiatives',
  risks: '/insights/risks',
  approvals: '/approvals',
  audit: '/org/audit',
  settings: '/settings',
};

export interface OwnerLayoutProps {
  /** Child components to render in main content area */
  children: React.ReactNode;
}

/**
 * Owner Layout Component
 *
 * Provides the standard layout for Owner persona pages.
 * Includes sidebar navigation, header, and content area.
 *
 * @example
 * ```tsx
 * <OwnerLayout>
 *   <OwnerDashboard />
 * </OwnerLayout>
 * ```
 */
export function OwnerLayout({ children }: OwnerLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();

  // Determine active item based on current path
  const activeItemId = Object.entries(ROUTE_MAP).find(
    ([, path]) => path === location.pathname
  )?.[0];

  // Update sections with active state
  const sectionsWithActive = ownerSections.map((section) => ({
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
          subtitle="Owner"
          sections={sectionsWithActive}
          onNavigate={handleNavigate}
        />
      }
    >
      {children}
    </DashboardLayout>
  );
}
