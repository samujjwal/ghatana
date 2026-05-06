/**
 * Main App Component
 * Handles routing and authentication flow
 */

import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAtomValue } from 'jotai';
import { isAuthenticatedAtom } from './store/atoms';
import { useCurrentUser } from './hooks/use-api';
import {
  flashitRouteManifest,
  type FlashItRouteManifestEntry,
} from './routeManifest';
import { isRouteAllowedForRole, resolveFlashitRole } from './routeAccess';
import { FlashitProductShell } from './components/FlashitProductShell';

// Pages
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';

// Loading component for auth state
function AuthLoading() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 to-primary-100">
      <div className="text-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600 mx-auto mb-4"></div>
        <p className="text-gray-600">Loading...</p>
      </div>
    </div>
  );
}

function FlashitAccessDenied({
  route,
}: {
  route: FlashItRouteManifestEntry;
}) {
  return (
    <FlashitProductShell>
      <section
        data-testid="flashit-access-denied"
        className="mx-auto max-w-3xl rounded-3xl border border-amber-200 bg-amber-50/80 px-6 py-8 text-slate-900 shadow-sm"
      >
        <p className="text-sm font-semibold uppercase tracking-[0.2em] text-amber-700">Permission denied</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight">{route.label} needs a higher subscription tier.</h1>
        <p className="mt-3 text-base leading-7 text-slate-700">
          The shared FlashIt route entitlement contract hides this route from navigation and blocks direct URL access
          when the current account does not meet the required role.
        </p>
        <p className="mt-4 text-sm text-slate-600">
          Minimum role: <strong>{route.minimumRole ?? 'member'}</strong>
        </p>
      </section>
    </FlashitProductShell>
  );
}

function PrivateRoute({
  route,
  children,
}: {
  route: FlashItRouteManifestEntry;
  children: React.ReactNode;
}) {
  const isAuthenticated = useAtomValue(isAuthenticatedAtom);

  // Check localStorage directly as fallback
  const hasTokenInStorage = !!localStorage.getItem('flashit_token');

  // Only call useCurrentUser if we have some form of authentication
  const shouldFetchUser = isAuthenticated || hasTokenInStorage;
  const currentUserQuery = useCurrentUser({ enabled: shouldFetchUser });
  const { isLoading, error, data } = currentUserQuery;

  // If not authenticated and no token in storage, redirect to login immediately
  if (!isAuthenticated && !hasTokenInStorage) {
    return <Navigate to="/login" replace />;
  }

  // Show loading while checking authentication (only if we have a token and are fetching)
  if (shouldFetchUser && isLoading) {
    return <AuthLoading />;
  }

  // If we have data, user is authenticated
  if (data) {
    const currentRole = resolveFlashitRole(data);
    if (!isRouteAllowedForRole(route, currentRole)) {
      return <FlashitAccessDenied route={route} />;
    }
    return <>{children}</>;
  }

  // If there's an error (invalid token, expired, etc.), clear token and redirect to login
  if (error) {
    // Clear invalid token from storage
    localStorage.removeItem('flashit_token');
    return <Navigate to="/login" replace />;
  }

  // Default case - if we have a token but no data yet, show loading
  if (hasTokenInStorage) {
    return <AuthLoading />;
  }

  // Otherwise redirect to login
  return <Navigate to="/login" replace />;
}

function PublicRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAtomValue(isAuthenticatedAtom);

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Public routes */}
        <Route
          path="/login"
          element={
            <PublicRoute>
              <LoginPage />
            </PublicRoute>
          }
        />
        <Route
          path="/register"
          element={
            <PublicRoute>
              <RegisterPage />
            </PublicRoute>
          }
        />

        {/* Private routes */}
        {flashitRouteManifest.map((route) => (
          <Route
            key={route.path}
            path={route.path}
            element={<PrivateRoute route={route}>{route.element}</PrivateRoute>}
          />
        ))}

        {/* Catch all */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
