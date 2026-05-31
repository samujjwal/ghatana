import React from 'react';
import { Navigate, createBrowserRouter } from 'react-router-dom';
import { AppShell } from './layout/AppShell';
import { usePhrAccess } from './auth/PhrAccessContext';
import { usePhrSession } from './auth/PhrSessionContext';
import { phrRoutePlugin } from './routeManifest';
import { attachPhrRouteElement, type PhrRouteManifestEntry } from './phrRouteElements';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';

/**
 * Guards a route by checking the session is authenticated and the role
 * meets the route minimum. Unauthenticated requests redirect to /login.
 */
export function ProtectedPhrRoute({ route }: { route: PhrRouteManifestEntry }): React.ReactElement {
  const { role } = usePhrAccess();
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

  if (!phrRoutePlugin.isAllowedForRole(route, role)) {
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
