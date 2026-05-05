/**
 * Main App Component
 * Handles routing and authentication flow
 */

import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAtomValue } from 'jotai';
import { isAuthenticatedAtom } from './store/atoms';
import { useCurrentUser } from './hooks/use-api';
import { flashitRouteManifest } from './routeManifest';

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

function PrivateRoute({ children }: { children: React.ReactNode }) {
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
            element={<PrivateRoute>{route.element}</PrivateRoute>}
          />
        ))}

        {/* Catch all */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
