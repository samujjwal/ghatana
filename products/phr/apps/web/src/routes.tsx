import React from 'react';
import { Navigate, createBrowserRouter } from 'react-router-dom';
import { AppShell } from './layout/AppShell';
import { usePhrAccess } from './auth/PhrAccessContext';
import { isRouteAllowedForRole, phrRouteContracts } from './routeManifest';
import { attachPhrRouteElement, type PhrRouteManifestEntry } from './phrRouteElements';
import { LoginPage } from './pages/LoginPage';

export function ProtectedPhrRoute({ route }: { route: PhrRouteManifestEntry }): React.ReactElement {
  const { role } = usePhrAccess();

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
