import { useEffect, Suspense } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAtomValue } from 'jotai';
import { userAtom, isAuthenticatedAtom } from '../stores/authStore';
import { wsConnectedAtom } from '../stores/eventsStore';
import { authService } from '../services/auth.service';
import { websocketService } from '../services/websocket.service';
import { lazyWithRetry } from '../utils/lazyLoad';
import { useCanSeeSections, useRole, SectionGuard, DashboardUtils } from '@ghatana/dcmaar-dashboard-core';
import { ChildRequests } from '../components/ChildRequests';

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
    <div className="bg-white dark:bg-slate-800 shadow rounded-lg p-6 animate-pulse">
      <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-1/4 mb-4"></div>
      <div className="space-y-3">
        <div className="h-3 bg-slate-200 dark:bg-slate-700 rounded"></div>
        <div className="h-3 bg-slate-200 dark:bg-slate-700 rounded w-5/6"></div>
      </div>
    </div>
  );
}

function CoreFeaturesSection() {
  const { role } = useRole();
  const features = DashboardUtils.getFeaturesForRole(role);

  const DevicesFeature = DashboardUtils.getFeatureComponent('devices');
  const UsageFeature = DashboardUtils.getFeatureComponent('usage');
  const AlertsFeature = DashboardUtils.getFeatureComponent('alerts');
  const PoliciesFeature = DashboardUtils.getFeatureComponent('policies');

  return (
    <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-2">
      {features.includes('devices') && (
        <Suspense fallback={<ComponentLoader />}>
          <SectionGuard section="devices">
            <DevicesFeature />
          </SectionGuard>
        </Suspense>
      )}

      {features.includes('usage') && (
        <Suspense fallback={<ComponentLoader />}>
          <SectionGuard section="usage">
            <UsageFeature />
          </SectionGuard>
        </Suspense>
      )}

      {features.includes('alerts') && (
        <Suspense fallback={<ComponentLoader />}>
          <SectionGuard section="alerts">
            <AlertsFeature />
          </SectionGuard>
        </Suspense>
      )}

      {features.includes('policies') && (
        <Suspense fallback={<ComponentLoader />}>
          <SectionGuard section="policies">
            <PoliciesFeature />
          </SectionGuard>
        </Suspense>
      )}
    </div>
  );
}

export function Dashboard() {
  const navigate = useNavigate();
  const user = useAtomValue(userAtom);
  const isAuthenticated = useAtomValue(isAuthenticatedAtom);
  const wsConnected = useAtomValue(wsConnectedAtom);

  const canSeeUsage = useCanSeeSections('usage');
  const canSeeAlerts = useCanSeeSections('alerts');
  const canSeePolicies = useCanSeeSections('policies');
  const canSeeDevices = useCanSeeSections('devices');
  const canSeeAnalytics = useCanSeeSections('analytics');

  useEffect(() => {
    // Ensure WebSocket is connected
    if (isAuthenticated && !wsConnected && authService.isAuthenticated()) {
      websocketService.connect();
    }
  }, [isAuthenticated, wsConnected]);

  const handleLogout = async () => {
    await authService.logout();
    websocketService.disconnect();
    navigate('/login');
  };

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-900">
      <nav className="bg-white dark:bg-slate-800 shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center">
              <h1 className="text-xl font-bold text-slate-900 dark:text-white">Guardian Dashboard</h1>
            </div>
            <div className="flex items-center space-x-4">
              <div className="flex items-center space-x-2">
                <span className={`h-2 w-2 rounded-full ${wsConnected ? 'bg-green-500' : 'bg-red-500'}`}></span>
                <span className="text-sm text-slate-600 dark:text-slate-400">
                  {wsConnected ? 'Connected' : 'Disconnected'}
                </span>
              </div>
              <span className="text-sm text-slate-600 dark:text-slate-400">
                {user?.email}
              </span>
              <button
                onClick={handleLogout}
                className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-md hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
              >
                Logout
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          {/* Usage Monitor Component */}
          {canSeeUsage && (
            <Suspense fallback={<ComponentLoader />}>
              <UsageMonitor />
            </Suspense>
          )}

          {/* Block Notifications Component */}
          <div className="mt-6">
            {canSeeAlerts && (
              <Suspense fallback={<ComponentLoader />}>
                <BlockNotifications />
              </Suspense>
            )}
          </div>

          {/* Policy Management Component */}
          <div className="mt-6">
            {canSeePolicies && (
              <Suspense fallback={<ComponentLoader />}>
                <PolicyManagement />
              </Suspense>
            )}
          </div>

          {/* Device Management Component */}
          <div className="mt-6">
            {canSeeDevices && (
              <Suspense fallback={<ComponentLoader />}>
                <DeviceManagement />
              </Suspense>
            )}
          </div>

          {/* Analytics Component */}
          <div className="mt-6">
            {canSeeAnalytics && (
              <Suspense fallback={<ComponentLoader />}>
                <Analytics />
              </Suspense>
            )}
          </div>

          <div className="mt-6 bg-white dark:bg-slate-800 overflow-hidden shadow rounded-lg">
            <div className="px-4 py-5 sm:p-6">
              <h2 className="text-2xl font-bold text-slate-900 dark:text-white mb-4">
                Dashboard Overview
              </h2>

              <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
                <div className="bg-indigo-50 overflow-hidden shadow rounded-lg">
                  <div className="px-4 py-5 sm:p-6">
                    <dt className="text-sm font-medium text-indigo-600 truncate">
                      WebSocket Status
                    </dt>
                    <dd className="mt-1 text-3xl font-semibold text-slate-900 dark:text-white">
                      {wsConnected ? '✅ Online' : '❌ Offline'}
                    </dd>
                  </div>
                </div>

                <div className="bg-green-50 overflow-hidden shadow rounded-lg">
                  <div className="px-4 py-5 sm:p-6">
                    <dt className="text-sm font-medium text-green-600 truncate">
                      User Role
                    </dt>
                    <dd className="mt-1 text-3xl font-semibold text-slate-900 dark:text-white">
                      {user?.role || 'Parent'}
                    </dd>
                  </div>
                </div>

                <div className="bg-purple-50 overflow-hidden shadow rounded-lg">
                  <div className="px-4 py-5 sm:p-6">
                    <dt className="text-sm font-medium text-purple-600 truncate">
                      Status
                    </dt>
                    <dd className="mt-1 text-3xl font-semibold text-slate-900 dark:text-white">
                      Active
                    </dd>
                  </div>
                </div>
              </div>

              <div className="mt-6">
                <h3 className="text-lg font-medium text-slate-900 dark:text-white mb-2">
                  Week 3 Progress
                </h3>
                <ul className="space-y-2 text-slate-600 dark:text-slate-400">
                  <li>✅ Day 1: Authentication & WebSocket Connection</li>
                  <li>✅ Day 2: Real-time Usage Monitoring</li>
                  <li>✅ Day 3: Block Event Notifications</li>
                  <li>✅ Day 4: Policy Management</li>
                  <li>✅ Day 5: Device Management</li>
                  <li>✅ Day 6: Analytics & Insights</li>
                </ul>
              </div>
            </div>
          </div>

          <div className="mt-6">
            <ChildRequests />
          </div>

          <CoreFeaturesSection />
        </div>
      </main>
    </div>
  );
}
