/**
 * Ghatana Studio - Unified Product Development Experience
 *
 * @doc.type component
 * @doc.purpose Customer-facing Studio shell for product ideation, lifecycle execution, health, learning, and evolution
 * @doc.layer platform
 */

import type { MouseEvent, ReactElement, ReactNode } from 'react';
import { useMemo } from 'react';
import { Link, Route, Routes, useLocation } from 'react-router';
import { Badge, DashboardLayout, EmptyState, ErrorBoundary } from '@ghatana/design-system';

import AgentsPage from './routes/AgentsPage';
import ArtifactsPage from './routes/ArtifactsPage';
import BlueprintsPage from './routes/BlueprintsPage';
import CanvasPage from './routes/CanvasPage';
import DeploymentsPage from './routes/DeploymentsPage';
import DevelopPage from './routes/DevelopPage';
import HealthPage from './routes/HealthPage';
import HomePage from './routes/HomePage';
import IdeasPage from './routes/IdeasPage';
import LearnPage from './routes/LearnPage';
import LifecyclePage from './routes/LifecyclePage';
import {
  findStudioNavItemFromItems,
  resolveStudioNavItems,
  resolveStudioRouteCapabilityState,
  type StudioNavItem,
  type StudioRouteStatus,
} from './navigation/studioNavigation';
import { useStudioTranslation } from './i18n/studioTranslations';
import { STUDIO_ENVIRONMENT_CONFIG } from './config/studioEnvironment';
import { studioLogger } from './logging/studioLogger';
import { useStudioLifecycleData } from './data/StudioLifecycleDataContext';

interface RouteShellProps {
  readonly title: string;
  readonly description: string;
  readonly status: StudioRouteStatus;
}

function Sidebar(): ReactElement {
  const location = useLocation();
  const t = useStudioTranslation();
  const lifecycleData = useStudioLifecycleData();
  const runtimeConfigured = lifecycleData.authenticatedUserId !== undefined;

  const navItems = useMemo(
    () =>
      resolveStudioNavItems(
        resolveStudioRouteCapabilityState({
          runtimeConfigured,
          lifecycleStatus: lifecycleData.snapshot.status,
          productUnit: lifecycleData.snapshot.productUnit,
        }),
      ),
    [runtimeConfigured, lifecycleData.snapshot.status, lifecycleData.snapshot.productUnit],
  );

  return (
    <div className="h-full border-r border-gray-200 bg-white p-4">
      <div className="mb-6">
        <h1 className="text-xl font-bold text-gray-900">{t('studio.brand.title')}</h1>
        <p className="text-sm text-gray-500">{t('studio.brand.subtitle')}</p>
      </div>

      <nav className="space-y-1" aria-label="Studio navigation">
        {navItems.filter((item: StudioNavItem) => item.isCustomerVisible && item.exposure !== 'hidden').map(
          (item: StudioNavItem) => {
            const isActive = location.pathname === item.path;
            const isDisabled = item.exposure === 'disabled';

            return (
              <Link
                key={item.id}
                to={item.path}
                aria-current={isActive ? 'page' : undefined}
                className={`flex items-center justify-between gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 ${
                  isActive
                    ? 'bg-blue-50 text-blue-700'
                    : isDisabled
                      ? 'text-gray-400 cursor-not-allowed'
                      : 'text-gray-700 hover:bg-gray-100'
                }`}
                {...(isDisabled ? { onClick: (e: MouseEvent) => e.preventDefault(), 'aria-disabled': true as const } : {})}
              >
                <span>{t(item.labelKey)}</span>
                <div className="flex items-center gap-2">
                  {item.exposure === 'preview' && (
                    <span className="text-xs font-normal text-purple-600">Preview</span>
                  )}
                  {item.status !== 'ready' ? (
                    <span className="text-xs font-normal text-gray-500">
                      {t(`studio.status.${item.status}`)}
                    </span>
                  ) : null}
                </div>
              </Link>
            );
          },
        )}
      </nav>
    </div>
  );
}

function StudioHeader(): ReactElement {
  const location = useLocation();
  const t = useStudioTranslation();
  const lifecycleData = useStudioLifecycleData();
  const runtimeConfigured = lifecycleData.authenticatedUserId !== undefined;
  const activeItem = findStudioNavItemFromItems(
    location.pathname,
    resolveStudioNavItems(
      resolveStudioRouteCapabilityState({
        runtimeConfigured,
        lifecycleStatus: lifecycleData.snapshot.status,
        productUnit: lifecycleData.snapshot.productUnit,
      }),
    ),
  );
  const pageTitle = activeItem === undefined ? t('studio.route.notFound.title') : t(activeItem.labelKey);

  return (
    <div className="flex items-center justify-between gap-4 border-b border-gray-200 px-6 py-4">
      <div>
        <h2 className="text-lg font-semibold text-gray-900">{pageTitle}</h2>
        <p className="text-sm text-gray-500">
          {activeItem ? `${activeItem.ownership} ${t('studio.header.ownershipSuffix')}` : t('studio.header.unknownRoute')}
        </p>
      </div>
      <div className="flex items-center gap-4 text-sm text-gray-500">
        <span>{STUDIO_ENVIRONMENT_CONFIG.version}</span>
        <a
          href={STUDIO_ENVIRONMENT_CONFIG.docsUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="text-blue-600 hover:text-blue-700"
        >
          {t('studio.header.documentation')}
        </a>
      </div>
    </div>
  );
}

interface RouteAccessGuardProps {
  readonly navItem: StudioNavItem;
  readonly children: ReactNode;
}

function RouteAccessGuard(props: RouteAccessGuardProps): ReactElement {
  const { navItem, children } = props;
  const t = useStudioTranslation();

  if (navItem.exposure === 'visible' || navItem.exposure === 'preview') {
    return <>{children}</>;
  }

  if (navItem.exposure === 'hidden') {
    return (
      <RouteShell
        title={t('studio.route.notFound.title')}
        description={t('studio.route.notFound.description')}
        status="blocked"
      />
    );
  }

  return (
    <section className="mb-4 rounded-md border border-amber-300 bg-amber-50 p-4" aria-live="polite">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-amber-800">
          {t('studio.route.guard.accessDisabled')}
        </p>
        <Badge tone="warning" variant="soft">
          {t(`studio.status.${navItem.status}`)}
        </Badge>
      </div>
      <p className="mt-2 text-sm text-amber-800">
        {t('studio.route.guard.reasonPrefix')} {navItem.requiredCapability}
      </p>
      <a
        className="mt-3 inline-flex text-sm font-medium text-amber-900 underline"
        href={STUDIO_ENVIRONMENT_CONFIG.docsUrl}
        target="_blank"
        rel="noopener noreferrer"
      >
        {t('studio.header.documentation')}
      </a>
    </section>
  );
}

function RouteShell(props: RouteShellProps): ReactElement {
  const { title, description, status } = props;
  const t = useStudioTranslation();
  const titleId = `${title.toLowerCase().replaceAll(' ', '-')}-route-title`;

  return (
    <section className="studio-section" aria-labelledby={titleId}>
      <div className="studio-card">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 id={titleId} className="text-xl font-semibold text-gray-950">
            {title}
          </h2>
          <Badge tone={status === 'blocked' ? 'danger' : status === 'degraded' ? 'warning' : 'neutral'}>
            {t(`studio.status.${status}`)}
          </Badge>
        </div>
        <EmptyState title={title} description={description} size="md" />
      </div>
    </section>
  );
}

function NotFoundRoute(): ReactElement {
  const t = useStudioTranslation();

  return (
    <RouteShell
      title={t('studio.route.notFound.title')}
      description={t('studio.route.notFound.description')}
      status="blocked"
    />
  );
}

function SettingsRoute(): ReactElement {
  const t = useStudioTranslation();

  return (
    <RouteShell
      title={t('studio.route.settings.title')}
      description={t('studio.route.settings.description')}
      status="ready"
    />
  );
}

function logStudioError(errorContext: { readonly error: Error }): void {
  studioLogger.error('Ghatana Studio route error', {
    message: errorContext.error.message,
    stack: errorContext.error.stack,
  });
}

export default function App(): ReactElement {
  const t = useStudioTranslation();
  const lifecycleData = useStudioLifecycleData();
  const runtimeConfigured = lifecycleData.authenticatedUserId !== undefined;
  const navItems = resolveStudioNavItems(
    resolveStudioRouteCapabilityState({
      runtimeConfigured,
      lifecycleStatus: lifecycleData.snapshot.status,
      productUnit: lifecycleData.snapshot.productUnit,
    }),
  );
  const routeById = Object.fromEntries(navItems.map((item) => [item.id, item])) as Record<string, StudioNavItem>;

  return (
    <ErrorBoundary onError={logStudioError} resetButtonText={t('studio.app.retryStudio')}>
      <DashboardLayout
        sidebar={<Sidebar />}
        header={<StudioHeader />}
        padding="lg"
        contentClassName="bg-gray-50"
      >
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/ideas" element={<RouteAccessGuard navItem={routeById.ideas}><IdeasPage /></RouteAccessGuard>} />
          <Route path="/blueprints" element={<RouteAccessGuard navItem={routeById.blueprints}><BlueprintsPage /></RouteAccessGuard>} />
          <Route path="/canvas" element={<RouteAccessGuard navItem={routeById.canvas}><CanvasPage /></RouteAccessGuard>} />
          <Route path="/develop" element={<RouteAccessGuard navItem={routeById.develop}><DevelopPage /></RouteAccessGuard>} />
          <Route path="/lifecycle" element={<RouteAccessGuard navItem={routeById.lifecycle}><LifecyclePage /></RouteAccessGuard>} />
          <Route path="/agents" element={<RouteAccessGuard navItem={routeById.agents}><AgentsPage /></RouteAccessGuard>} />
          <Route path="/artifacts" element={<RouteAccessGuard navItem={routeById.artifacts}><ArtifactsPage /></RouteAccessGuard>} />
          <Route path="/deployments" element={<RouteAccessGuard navItem={routeById.deployments}><DeploymentsPage /></RouteAccessGuard>} />
          <Route path="/health" element={<RouteAccessGuard navItem={routeById.health}><HealthPage /></RouteAccessGuard>} />
          <Route path="/learn" element={<RouteAccessGuard navItem={routeById.learn}><LearnPage /></RouteAccessGuard>} />
          <Route path="/settings" element={<RouteAccessGuard navItem={routeById.settings}><SettingsRoute /></RouteAccessGuard>} />
          <Route path="*" element={<NotFoundRoute />} />
        </Routes>
      </DashboardLayout>
    </ErrorBoundary>
  );
}
