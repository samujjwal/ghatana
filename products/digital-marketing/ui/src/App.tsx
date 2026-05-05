/**
 * DMOS App — root router.
 *
 * @doc.type component
 * @doc.purpose Root routing shell for the DMOS console
 * @doc.layer frontend
 */
import React, { Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { AuthCallbackPage } from './pages/AuthCallbackPage';
import FeatureFlaggedRoute from './components/FeatureFlaggedRoute';
import { FeatureUnavailablePage } from './pages/FeatureUnavailablePage';
import { dmosRouteManifest, isRouteAllowedForRoles, type DmosRouteManifestEntry } from './routeManifest';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1 },
  },
});

function GuardedProductRoute({
  route,
}: {
  route: DmosRouteManifestEntry;
}): React.ReactElement {
  const { isAuthenticated, roles } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (!isRouteAllowedForRoles(route, roles)) {
    return (
      <FeatureUnavailablePage
        featureName={route.label}
        reason={`requires the ${route.minimumRole ?? 'viewer'} route entitlement.`}
      />
    );
  }

  if (route.capabilityKey) {
    return (
      <FeatureFlaggedRoute capabilityKey={route.capabilityKey} featureName={route.label}>
        {route.element}
      </FeatureFlaggedRoute>
    );
  }

  return route.element;
}

export function App(): React.ReactElement {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Suspense fallback={<div data-testid="app-loading">Loading…</div>}>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/auth/callback" element={<AuthCallbackPage />} />
              {dmosRouteManifest.map((route) => (
                <Route
                  key={route.path}
                  path={route.path}
                  element={<GuardedProductRoute route={route} />}
                />
              ))}
              <Route path="/" element={<Navigate to="/login" replace />} />
            </Routes>
          </Suspense>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
