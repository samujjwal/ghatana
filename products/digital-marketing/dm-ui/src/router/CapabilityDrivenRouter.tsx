import React, { useEffect, useState, Suspense, lazy } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useBackendCapabilities } from '../hooks/useBackendCapabilities';
import { useFeatureFlags } from '../hooks/useFeatureFlags';
import { LoadingSpinner } from '../components/LoadingSpinner';
import { FeatureUnavailablePage } from '../pages/FeatureUnavailablePage';

/**
 * P1-016: Backend-Capability Driven Routes
 *
 * Dynamic routing system that adapts based on backend capabilities:
 * - Conditionally renders routes based on backend availability
 * - Feature-flagged route access
 * - Capability-based component selection
 * - Graceful degradation when features unavailable
 * - Route preloading based on capabilities
 */

// Route capability requirements
interface RouteCapability {
  path: string;
  requiredCapabilities: string[];
  requiredFeatureFlag?: string;
  fallbackComponent?: React.ComponentType;
  preload?: boolean;
}

// Lazy load route components
const DashboardCommandCenter = lazy(() => import('../pages/DashboardCommandCenter'));
const CampaignList = lazy(() => import('../pages/CampaignList'));
const CampaignDetail = lazy(() => import('../pages/CampaignDetail'));
const CampaignWizard = lazy(() => import('../pages/CampaignWizard'));
const StrategyGenerator = lazy(() => import('../pages/StrategyGenerator'));
const BudgetPlanner = lazy(() => import('../pages/BudgetPlanner'));
const ApprovalQueue = lazy(() => import('../pages/ApprovalQueue'));
const ApprovalDetail = lazy(() => import('../pages/ApprovalDetail'));
const AnalyticsDashboard = lazy(() => import('../pages/AnalyticsDashboard'));
const Settings = lazy(() => import('../pages/Settings'));
const AdminPanel = lazy(() => import('../pages/AdminPanel'));
const GoogleAdsConnector = lazy(() => import('../pages/GoogleAdsConnector'));
const AiAssistant = lazy(() => import('../pages/AiAssistant'));
const AuditLog = lazy(() => import('../pages/AuditLog'));

// Route definitions with capability requirements
const ROUTE_CAPABILITIES: RouteCapability[] = [
  {
    path: '/dashboard',
    requiredCapabilities: ['campaigns.read'],
    preload: true
  },
  {
    path: '/campaigns',
    requiredCapabilities: ['campaigns.read'],
    preload: true
  },
  {
    path: '/campaigns/new',
    requiredCapabilities: ['campaigns.create'],
    requiredFeatureFlag: 'campaign-creation-enabled'
  },
  {
    path: '/campaigns/:id',
    requiredCapabilities: ['campaigns.read']
  },
  {
    path: '/campaigns/:id/edit',
    requiredCapabilities: ['campaigns.update']
  },
  {
    path: '/strategies/generate',
    requiredCapabilities: ['strategies.create', 'ai.generation'],
    requiredFeatureFlag: 'ai-strategy-generation'
  },
  {
    path: '/budgets',
    requiredCapabilities: ['budgets.read']
  },
  {
    path: '/budgets/plan',
    requiredCapabilities: ['budgets.create', 'ai.generation'],
    requiredFeatureFlag: 'ai-budget-planning'
  },
  {
    path: '/approvals',
    requiredCapabilities: ['approvals.read']
  },
  {
    path: '/approvals/:id',
    requiredCapabilities: ['approvals.read', 'approvals.decide']
  },
  {
    path: '/analytics',
    requiredCapabilities: ['analytics.read'],
    requiredFeatureFlag: 'advanced-analytics',
    fallbackComponent: () => <FeatureUnavailablePage 
      featureName="Analytics Dashboard" 
      explanation="Advanced analytics requires premium plan or backend upgrade"
    />
  },
  {
    path: '/google-ads',
    requiredCapabilities: ['google-ads.read', 'connectors.manage'],
    requiredFeatureFlag: 'google-ads-integration'
  },
  {
    path: '/ai-assistant',
    requiredCapabilities: ['ai.chat', 'ai.generation'],
    requiredFeatureFlag: 'ai-assistant-enabled'
  },
  {
    path: '/audit-log',
    requiredCapabilities: ['audit.read'],
    requiredFeatureFlag: 'audit-log-viewer'
  },
  {
    path: '/admin',
    requiredCapabilities: ['admin.access'],
    requiredFeatureFlag: 'admin-panel-enabled'
  },
  {
    path: '/settings',
    requiredCapabilities: ['settings.read']
  }
];

/**
 * P1-016: CapabilityGuard component
 * 
 * Guards routes based on backend capabilities and feature flags
 */
interface CapabilityGuardProps {
  children: React.ReactNode;
  requiredCapabilities: string[];
  requiredFeatureFlag?: string;
  fallbackComponent?: React.ComponentType;
}

const CapabilityGuard: React.FC<CapabilityGuardProps> = ({
  children,
  requiredCapabilities,
  requiredFeatureFlag,
  fallbackComponent: FallbackComponent
}) => {
  const { capabilities, isLoading, error } = useBackendCapabilities();
  const { isEnabled, isLoading: flagsLoading } = useFeatureFlags();
  const location = useLocation();

  if (isLoading || flagsLoading) {
    return <LoadingSpinner message="Checking permissions..." />;
  }

  if (error) {
    return (
      <div className="p-8 text-center">
        <h2 className="text-xl font-bold text-red-600 mb-2">Unable to Load</h2>
        <p className="text-gray-600">Could not verify access permissions. Please try again.</p>
      </div>
    );
  }

  // Check feature flag
  if (requiredFeatureFlag && !isEnabled(requiredFeatureFlag)) {
    if (FallbackComponent) {
      return <FallbackComponent />;
    }
    return (
      <FeatureUnavailablePage
        featureName={location.pathname.split('/').pop() || 'This feature'}
        explanation="This feature is currently disabled or not available for your plan."
      />
    );
  }

  // Check capabilities
  const missingCapabilities = requiredCapabilities.filter(
    cap => !capabilities.includes(cap)
  );

  if (missingCapabilities.length > 0) {
    if (FallbackComponent) {
      return <FallbackComponent />;
    }
    return (
      <FeatureUnavailablePage
        featureName={location.pathname.split('/').pop() || 'This feature'}
        explanation={`This feature requires: ${missingCapabilities.join(', ')}`}
        actionText="Contact Support"
        onAction={() => window.open('/support', '_blank')}
      />
    );
  }

  return <>{children}</>;
};

/**
 * P1-016: Main capability-driven router
 */
export const CapabilityDrivenRouter: React.FC = () => {
  const { capabilities, isLoading } = useBackendCapabilities();
  const [preloadedComponents, setPreloadedComponents] = useState<Set<string>>(new Set());

  // P1-016: Preload components based on capabilities
  useEffect(() => {
    if (isLoading) return;

    const toPreload = ROUTE_CAPABILITIES.filter(
      route => 
        route.preload && 
        route.requiredCapabilities.every(cap => capabilities.includes(cap))
    );

    toPreload.forEach(route => {
      // Preload the component
      switch (route.path) {
        case '/dashboard':
          import('../pages/DashboardCommandCenter');
          break;
        case '/campaigns':
          import('../pages/CampaignList');
          break;
      }
      setPreloadedComponents(prev => new Set(prev).add(route.path));
    });
  }, [capabilities, isLoading]);

  // Generate routes based on capabilities
  const generateRoutes = () => {
    return ROUTE_CAPABILITIES.map((routeConfig) => {
      const Component = getComponentForRoute(routeConfig.path);

      return (
        <Route
          key={routeConfig.path}
          path={routeConfig.path}
          element={
            <CapabilityGuard
              requiredCapabilities={routeConfig.requiredCapabilities}
              requiredFeatureFlag={routeConfig.requiredFeatureFlag}
              fallbackComponent={routeConfig.fallbackComponent}
            >
              <Suspense fallback={<LoadingSpinner message="Loading..." />}>
                <Component />
              </Suspense>
            </CapabilityGuard>
          }
        />
      );
    });
  };

  return (
    <Routes>
      {/* Default redirect */}
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      
      {/* Capability-driven routes */}
      {generateRoutes()}
      
      {/* 404 - Feature not found */}
      <Route 
        path="*" 
        element={
          <FeatureUnavailablePage
            featureName="Page"
            explanation="The page you're looking for doesn't exist or you don't have access to it."
            actionText="Go to Dashboard"
            onAction={() => window.location.href = '/dashboard'}
          />
        } 
      />
    </Routes>
  );
};

/**
 * P1-016: Get component for route path
 */
const getComponentForRoute = (path: string): React.ComponentType => {
  switch (path) {
    case '/dashboard':
      return DashboardCommandCenter;
    case '/campaigns':
      return CampaignList;
    case '/campaigns/new':
      return CampaignWizard;
    case '/campaigns/:id':
      return CampaignDetail;
    case '/campaigns/:id/edit':
      return CampaignWizard;
    case '/strategies/generate':
      return StrategyGenerator;
    case '/budgets':
      return BudgetPlanner;
    case '/budgets/plan':
      return BudgetPlanner;
    case '/approvals':
      return ApprovalQueue;
    case '/approvals/:id':
      return ApprovalDetail;
    case '/analytics':
      return AnalyticsDashboard;
    case '/google-ads':
      return GoogleAdsConnector;
    case '/ai-assistant':
      return AiAssistant;
    case '/audit-log':
      return AuditLog;
    case '/admin':
      return AdminPanel;
    case '/settings':
      return Settings;
    default:
      return () => (
        <FeatureUnavailablePage
          featureName="Unknown"
          explanation="Route not configured"
        />
      );
  }
};

/**
 * P1-016: Hook for checking route accessibility
 */
export const useRouteAccessibility = (path: string): {
  isAccessible: boolean;
  missingCapabilities: string[];
  isFeatureFlagEnabled: boolean;
} => {
  const { capabilities } = useBackendCapabilities();
  const { isEnabled } = useFeatureFlags();

  const routeConfig = ROUTE_CAPABILITIES.find(r => {
    // Handle parameterized routes
    const routePattern = r.path.replace(/:\w+/g, '[^/]+');
    const regex = new RegExp(`^${routePattern}$`);
    return regex.test(path);
  });

  if (!routeConfig) {
    return {
      isAccessible: false,
      missingCapabilities: [],
      isFeatureFlagEnabled: false
    };
  }

  const missingCapabilities = routeConfig.requiredCapabilities.filter(
    cap => !capabilities.includes(cap)
  );

  const isFeatureFlagEnabled = routeConfig.requiredFeatureFlag 
    ? isEnabled(routeConfig.requiredFeatureFlag)
    : true;

  return {
    isAccessible: missingCapabilities.length === 0 && isFeatureFlagEnabled,
    missingCapabilities,
    isFeatureFlagEnabled
  };
};

/**
 * P1-016: Navigation component that filters by capabilities
 */
export const CapabilityBasedNavigation: React.FC = () => {
  const { capabilities } = useBackendCapabilities();
  const { isEnabled } = useFeatureFlags();
  const location = useLocation();

  const navItems = [
    { path: '/dashboard', label: 'Dashboard', icon: 'LayoutDashboard', capabilities: ['campaigns.read'] },
    { path: '/campaigns', label: 'Campaigns', icon: 'Campaign', capabilities: ['campaigns.read'] },
    { path: '/strategies/generate', label: 'AI Strategy', icon: 'Wand2', capabilities: ['strategies.create'], featureFlag: 'ai-strategy-generation' },
    { path: '/budgets', label: 'Budgets', icon: 'Wallet', capabilities: ['budgets.read'] },
    { path: '/approvals', label: 'Approvals', icon: 'CheckCircle', capabilities: ['approvals.read'] },
    { path: '/analytics', label: 'Analytics', icon: 'BarChart3', capabilities: ['analytics.read'], featureFlag: 'advanced-analytics' },
    { path: '/google-ads', label: 'Google Ads', icon: 'ExternalLink', capabilities: ['google-ads.read'], featureFlag: 'google-ads-integration' },
    { path: '/settings', label: 'Settings', icon: 'Settings', capabilities: ['settings.read'] },
  ];

  const visibleNavItems = navItems.filter(item => {
    const hasCapabilities = item.capabilities.every(cap => capabilities.includes(cap));
    const hasFeatureFlag = item.featureFlag ? isEnabled(item.featureFlag) : true;
    return hasCapabilities && hasFeatureFlag;
  });

  return (
    <nav className="space-y-1">
      {visibleNavItems.map((item) => (
        <a
          key={item.path}
          href={item.path}
          className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-colors ${
            location.pathname === item.path
              ? 'bg-blue-100 text-blue-700'
              : 'text-gray-700 hover:bg-gray-100'
          }`}
        >
          <span>{item.label}</span>
          {location.pathname === item.path && (
            <span className="ml-auto w-1.5 h-1.5 bg-blue-600 rounded-full" />
          )}
        </a>
      ))}
    </nav>
  );
};

export default CapabilityDrivenRouter;
