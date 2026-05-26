import React from 'react';
import { Navigate, createBrowserRouter } from 'react-router-dom';
import { AppShell } from './layout/AppShell';
import { usePhrAccess } from './auth/PhrAccessContext';
import { usePhrSession } from './auth/PhrSessionContext';
import { isRouteAllowedForRole, phrRouteContracts } from './routeManifest';
import { attachPhrRouteElement, type PhrRouteManifestEntry } from './phrRouteElements';
import { LoginPage } from './pages/LoginPage';

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

  if (!isRouteAllowedForRole(route, role)) {
    return (
      <section className="hero-panel" role="alert">
        <p className="eyebrow">Permission denied</p>
        <h1>This route is not available for the current persona.</h1>
        <p className="muted">
          The route manifest hides navigation and blocks direct URL access for roles that do not meet the minimum
          visibility contract.
        </p>
      </section>
    );
  }

  return route.element;
}

function protectedRoute(route: PhrRouteManifestEntry): { path: string; element: React.ReactElement } {
  return {
    path: route.path.replace(/^\//, ''),
    element: <ProtectedPhrRoute route={route} />,
  };
}

const phrRouteManifest = phrRouteContracts.map(attachPhrRouteElement);

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <AppShell />,
    children: [
      ...phrRouteManifest.map(protectedRoute),
    ],
  },
]);
