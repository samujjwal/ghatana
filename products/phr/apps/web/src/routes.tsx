import React from 'react';
import { Navigate, createBrowserRouter } from 'react-router-dom';
import { AppShell } from './layout/AppShell';
import { usePhrAccess } from './auth/PhrAccessContext';
import { usePhrSession } from './auth/PhrSessionContext';
import { phrRoutePlugin, attachPhrRouteElement, type PhrRouteManifestEntry } from './routeManifest';
import { createRouteAccessEvaluator, type ProductRouteCapability } from '@ghatana/product-shell';
import { phrRouteContracts, PHR_ROLE_ORDER } from './phrRouteContracts';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';

/**
 * Guards a route by checking the session is authenticated and delegating
 * authorization to Kernel entitlement/policy state. Unauthenticated requests
 * redirect to /login. Authorization uses Kernel route access evaluator which
 * considers role, persona, tier, and policy state.
 */
export function ProtectedPhrRoute({ route }: { route: PhrRouteManifestEntry }): React.ReactElement {
  const { role, persona, tier } = usePhrAccess();
  const { isAuthenticated } = usePhrSession();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (route.stability === 'hidden' || route.stability === 'deferred' || route.stability === 'removed' || route.hidden === true) {
    return <Navigate to="/not-found" replace />;
  }

  if (route.stability === 'blocked' || route.stability === 'preview' || route.blocked === true) {
    return <Navigate to="/forbidden" replace />;
  }

  // Delegate authorization to Kernel entitlement/policy state
  // Use Kernel route access evaluator which considers role, persona, tier
  const kernelAccessEvaluator = createRouteAccessEvaluator(PHR_ROLE_ORDER);
  const routeCapability: ProductRouteCapability = {
    path: route.path,
    label: route.label,
    minimumRole: route.minimumRole,
    personas: route.personas,
    tiers: route.tiers,
  };

  if (!kernelAccessEvaluator.isRouteAllowed(routeCapability, role)) {
    return <Navigate to="/forbidden" replace />;
  }

  // Additional persona and tier checks via Kernel evaluator
  if (persona && route.personas && !route.personas.includes(persona as any)) {
    return <Navigate to="/forbidden" replace />;
  }

  if (tier && route.tiers && !route.tiers.includes(tier)) {
    return <Navigate to="/forbidden" replace />;
  }

  return route.element;
}

function protectedRoute(route: PhrRouteManifestEntry): { path: string; element: React.ReactElement } {
  return {
    path: route.path.replace(/^\//, ''),
    element: <ProtectedPhrRoute route={route} />,
  };
}

export const phrBrowserRouteManifest = phrRoutePlugin
  .getBrowserRoutes()
  .map(attachPhrRouteElement);

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <AppShell />,
    children: [
      ...phrBrowserRouteManifest.map(protectedRoute),
      // R-009: Catch-all route for unknown paths
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
