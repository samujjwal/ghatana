import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

/**
 * Guardian Application Sidebar
 * 
 * Guardian-specific sidebar navigation with:
 * - Guardian routes and navigation structure
 * - Guardian icons for each section
 * - Active route detection
 * 
 * @example
 * ```tsx
 * <GuardianSidebar
 *   collapsed={isCollapsed}
 *   onNavigate={(id) => navigate(id)}
 * />
 * ```
 */

interface GuardianSidebarProps {
  /** Whether sidebar is collapsed */
  collapsed?: boolean;
  
  /** Callback when navigation item is clicked */
  onNavigate?: (itemId: string) => void;
}

/**
 * DashboardIcon
 */
const DashboardIcon: React.FC = () => (
  <svg
    className="h-5 w-5"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <rect x="3" y="3" width="7" height="7" />
    <rect x="14" y="3" width="7" height="7" />
    <rect x="14" y="14" width="7" height="7" />
    <rect x="3" y="14" width="7" height="7" />
  </svg>
);

/**
 * AnalyticsIcon
 */
const AnalyticsIcon: React.FC = () => (
  <svg
    className="h-5 w-5"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M3 3v18h18" />
    <path d="m19 9-5 5-4-4-3 3" />
  </svg>
);

/**
 * MonitorIcon
 */
const MonitorIcon: React.FC = () => (
  <svg
    className="h-5 w-5"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
  </svg>
);

/**
 * BlockIcon
 */
const BlockIcon: React.FC = () => (
  <svg
    className="h-5 w-5"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <circle cx="12" cy="12" r="10" />
    <line x1="4.93" y1="4.93" x2="19.07" y2="19.07" />
  </svg>
);

/**
 * DevicesIcon
 */
const DevicesIcon: React.FC = () => (
  <svg
    className="h-5 w-5"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <rect x="5" y="2" width="14" height="20" rx="2" ry="2" />
    <line x1="12" y1="18" x2="12.01" y2="18" />
  </svg>
);

/**
 * PolicyIcon
 */
const PolicyIcon: React.FC = () => (
  <svg
    className="h-5 w-5"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
  </svg>
);

/**
 * ReportsIcon
 */
const ReportsIcon: React.FC = () => (
  <svg
    className="h-5 w-5"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
    <path d="M14 2v6h6" />
    <path d="M16 13H8" />
    <path d="M16 17H8" />
    <path d="M10 9H8" />
  </svg>
);

/**
 * SettingsIcon
 */
const SettingsIcon: React.FC = () => (
  <svg
    className="h-5 w-5"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth={2}
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
  </svg>
);

/**
 * GuardianLogo
 */
const GuardianLogo: React.FC = () => (
  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-indigo-600 shadow-md">
    <svg
      className="h-6 w-6 text-white"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
      <path d="M9 12l2 2 4-4" />
    </svg>
  </div>
);

export const GuardianSidebar: React.FC<GuardianSidebarProps> = ({
  collapsed = false,
  onNavigate,
}) => {
  const location = useLocation();
  const navigate = useNavigate();

  const handleNavigate = (itemId: string) => {
    if (onNavigate) {
      onNavigate(itemId);
    } else {
      navigate(itemId);
    }
  };

  const navItems = [
    {
      id: '/dashboard',
      label: 'Dashboard',
      icon: <DashboardIcon />,
      active: location.pathname === '/dashboard',
    },
    {
      id: '/analytics',
      label: 'Analytics',
      icon: <AnalyticsIcon />,
      active: location.pathname.startsWith('/analytics'),
    },
    {
      id: '/monitor',
      label: 'Monitor',
      icon: <MonitorIcon />,
      active: location.pathname.startsWith('/monitor'),
    },
    {
      id: '/block',
      label: 'Block',
      icon: <BlockIcon />,
      active: location.pathname.startsWith('/block'),
    },
    {
      id: '/devices',
      label: 'Devices',
      icon: <DevicesIcon />,
      active: location.pathname.startsWith('/devices'),
    },
    {
      id: '/policies',
      label: 'Policies',
      icon: <PolicyIcon />,
      active: location.pathname.startsWith('/policies'),
    },
    {
      id: '/reports',
      label: 'Reports',
      icon: <ReportsIcon />,
      active: location.pathname.startsWith('/reports'),
    },
  ];

  const footerItems = [
    {
      id: '/settings',
      label: 'Settings',
      icon: <SettingsIcon />,
      active: location.pathname.startsWith('/settings'),
    },
  ];

  interface NavItem {
    id: string;
    label: string;
    icon: React.ReactElement;
    active: boolean;
  }

  const renderNavItem = (item: NavItem) => {
    return (
      <li key={item.id}>
        <button
          onClick={() => handleNavigate(item.id)}
          className={`flex items-center w-full px-4 py-3 text-sm font-medium rounded-md transition-colors ${
            item.active
              ? 'bg-indigo-50 text-indigo-700'
              : 'text-gray-700 hover:bg-gray-100'
          } ${collapsed ? 'justify-center' : 'justify-start'}`}
          title={collapsed ? item.label : ''}
        >
          <span className={!collapsed ? 'mr-3' : ''}>
            {React.isValidElement(item.icon) && React.cloneElement(
              item.icon as React.ReactElement<{ className?: string }>,
              {
                className: `h-5 w-5 ${item.active ? 'text-indigo-600' : 'text-gray-500'}`,
              }
            )}
          </span>
          {!collapsed && <span>{item.label}</span>}
        </button>
      </li>
    );
  };

  // Define monitoringItems if not already defined
  const monitoringItems: NavItem[] = [
    // Add your monitoring items here
  ];

  return (
    <div className={`flex flex-col h-full bg-white border-r border-gray-200 ${collapsed ? 'w-16' : 'w-64'}`}>
      {/* Header */}
      <div className="flex items-center justify-center h-16 px-4 border-b border-gray-200">
        <GuardianLogo />
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-2 py-4 space-y-1 overflow-y-auto">
        <div className="px-3 py-2">
          <h3 className={`text-xs font-semibold text-gray-500 uppercase tracking-wider ${collapsed ? 'hidden' : ''}`}>
            Main
          </h3>
          <ul className="mt-2 space-y-1">
            {navItems.map(renderNavItem)}
          </ul>
        </div>

        {monitoringItems.length > 0 && (
          <div className="px-3 py-2">
            <h3 className={`text-xs font-semibold text-gray-500 uppercase tracking-wider ${collapsed ? 'hidden' : ''}`}>
              Monitoring
            </h3>
            <ul className="mt-2 space-y-1">
              {monitoringItems.map(renderNavItem)}
            </ul>
          </div>
        )}
      </nav>

      {/* Footer */}
      <div className="p-2 border-t border-gray-200">
        <ul className="space-y-1">
          {footerItems.map(renderNavItem)}
        </ul>
      </div>
    </div>
  );
};

export default GuardianSidebar;
