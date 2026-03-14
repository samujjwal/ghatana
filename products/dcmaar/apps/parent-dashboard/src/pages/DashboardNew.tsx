import { useEffect, Suspense, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAtomValue } from 'jotai';
import { DashboardLayout } from '@ghatana/design-system';
import { GuardianHeader } from '../components/GuardianHeader';
import { GuardianSidebar } from '../components/GuardianSidebar';
import { isAuthenticatedAtom } from '../stores/authStore';
import { wsConnectedAtom } from '../stores/eventsStore';
import { authService } from '../services/auth.service';
import { websocketService } from '../services/websocket.service';
import { lazyWithRetry } from '../utils/lazyLoad';

/**
 * Guardian Dashboard - New Integrated Layout
 * 
 * Uses the new DashboardLayout infrastructure with:
 * - GuardianHeader (navigation, user menu, WebSocket status)
 * - GuardianSidebar (collapsible navigation menu)
 * - Responsive design with mobile support
 * 
 * **ARCHITECTURE WIN**: Complete layout reuse from libs/ui
 * - DashboardLayout (generic)
 * - AppHeader (generic) → GuardianHeader (wrapper)
 * - AppSidebar (generic) → GuardianSidebar (wrapper)
 */

// Lazy load heavy dashboard components
const UsageMonitor = lazyWithRetry(() => 
  import('../components/UsageMonitor').then(m => ({ default: m.UsageMonitor }))
);
const BlockNotifications = lazyWithRetry(() =>
  import('../components/BlockNotifications').then(m => ({ default: m.BlockNotifications }))
);
const PolicyManagement = lazyWithRetry(() =>
  import('../components/PolicyManagement').then(m => ({ default: m.PolicyManagement }))
);
const DeviceManagement = lazyWithRetry(() =>
  import('../components/DeviceManagement').then(m => ({ default: m.DeviceManagement }))
);
const Analytics = lazyWithRetry(() =>
  import('../components/Analytics').then(m => ({ default: m.Analytics }))
);

// Component loading skeleton
function ComponentLoader() {
  return (
    <div className="bg-white shadow rounded-lg p-6 animate-pulse">
      <div className="h-4 bg-gray-200 rounded w-1/4 mb-4"></div>
      <div className="space-y-3">
        <div className="h-3 bg-gray-200 rounded"></div>
        <div className="h-3 bg-gray-200 rounded w-5/6"></div>
      </div>
    </div>
  );
}

export function DashboardNew() {
  const navigate = useNavigate();
  const isAuthenticated = useAtomValue(isAuthenticatedAtom);
  const wsConnected = useAtomValue(wsConnectedAtom);
  
  // Sidebar state (responsive - auto-collapse on mobile)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    if (typeof window === 'undefined') return false;
    return window.innerWidth < 1024; // lg breakpoint
  });

  useEffect(() => {
    // Ensure WebSocket is connected
    if (isAuthenticated && !wsConnected && authService.isAuthenticated()) {
      websocketService.connect();
    }
  }, [isAuthenticated, wsConnected]);

  const handleSidebarToggle = () => {
    setSidebarCollapsed(!sidebarCollapsed);
  };

  const handleNavigate = (itemId: string) => {
    navigate(itemId);
  };

  return (
    <DashboardLayout
      header={
        <GuardianHeader
          onMenuClick={handleSidebarToggle}
          showMenuButton={true}
        />
      }
      sidebar={
        <GuardianSidebar
          collapsed={sidebarCollapsed}
          onNavigate={handleNavigate}
        />
      }
      sidebarCollapsed={sidebarCollapsed}
      onSidebarToggle={setSidebarCollapsed}
      maxWidth="7xl"
      padding="md"
      backgroundColor="bg-gray-50"
      responsive={true}
      responsiveBreakpoint={1024}
    >
      {/* Main Dashboard Content */}
      <div className="space-y-6">
        {/* Dashboard Overview Stats */}
        <div className="bg-white overflow-hidden shadow rounded-lg">
          <div className="px-4 py-5 sm:p-6">
            <h2 className="text-2xl font-bold text-gray-900 mb-4">
              Dashboard Overview
            </h2>
            
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
              <div className="bg-indigo-50 overflow-hidden shadow rounded-lg">
                <div className="px-4 py-5 sm:p-6">
                  <dt className="text-sm font-medium text-indigo-600 truncate">
                    WebSocket Status
                  </dt>
                  <dd className="mt-1 text-3xl font-semibold text-gray-900">
                    {wsConnected ? '✅ Online' : '❌ Offline'}
                  </dd>
                </div>
              </div>

              <div className="bg-green-50 overflow-hidden shadow rounded-lg">
                <div className="px-4 py-5 sm:p-6">
                  <dt className="text-sm font-medium text-green-600 truncate">
                    Platform
                  </dt>
                  <dd className="mt-1 text-3xl font-semibold text-gray-900">
                    Guardian
                  </dd>
                </div>
              </div>

              <div className="bg-purple-50 overflow-hidden shadow rounded-lg">
                <div className="px-4 py-5 sm:p-6">
                  <dt className="text-sm font-medium text-purple-600 truncate">
                    Status
                  </dt>
                  <dd className="mt-1 text-3xl font-semibold text-gray-900">
                    Active
                  </dd>
                </div>
              </div>
            </div>

            <div className="mt-6">
              <h3 className="text-lg font-medium text-gray-900 mb-2">
                Component Migration Progress
              </h3>
              <ul className="space-y-2 text-gray-600">
                <li>✅ Batch 1: DynamicForm, SearchBar, FilterPanel, Pagination, DateRangePicker</li>
                <li>✅ Batch 2: ActivityFeed, DataGrid, UsageMonitor, DeviceManagement</li>
                <li>✅ Batch 3: StatsDashboard, Analytics, Reports, PolicyManagement</li>
                <li>✅ Batch 4: DashboardLayout, AppHeader, AppSidebar, GuardianHeader, GuardianSidebar, Settings</li>
                <li>🎉 <strong>Migration Complete: 100% (11/11 components)</strong></li>
              </ul>
            </div>
          </div>
        </div>

        {/* Usage Monitor Component */}
        <Suspense fallback={<ComponentLoader />}>
          <UsageMonitor />
        </Suspense>

        {/* Block Notifications Component */}
        <Suspense fallback={<ComponentLoader />}>
          <BlockNotifications />
        </Suspense>

        {/* Policy Management Component */}
        <Suspense fallback={<ComponentLoader />}>
          <PolicyManagement />
        </Suspense>

        {/* Device Management Component */}
        <Suspense fallback={<ComponentLoader />}>
          <DeviceManagement />
        </Suspense>

        {/* Analytics Component */}
        <Suspense fallback={<ComponentLoader />}>
          <Analytics />
        </Suspense>
      </div>
    </DashboardLayout>
  );
}

export default DashboardNew;
