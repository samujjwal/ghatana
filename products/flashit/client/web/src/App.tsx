/**
 * Main App Component
 * Handles routing and authentication flow
 */

import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAtomValue } from 'jotai';
import { isAuthenticatedAtom } from './store/atoms';
import { useCurrentUser } from './hooks/use-api';

// Pages
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import CapturePage from './pages/CapturePage';
import MomentsPage from './pages/MomentsPage';
import SpheresPage from './pages/SpheresPage';
import SearchPage from './pages/SearchPage';
import AnalyticsPage from './pages/AnalyticsPage';
import SettingsPage from './pages/SettingsPage';
import ReflectionPage from './pages/ReflectionPage';
import CollaborationPage from './pages/CollaborationPage';
import MemoryExpansionPage from './pages/MemoryExpansionPage';
import { LanguageInsightsPage } from './pages/LanguageInsightsPage';

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
        <Route
          path="/"
          element={
            <PrivateRoute>
              <DashboardPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/capture"
          element={
            <PrivateRoute>
              <CapturePage />
            </PrivateRoute>
          }
        />
        <Route
          path="/moments"
          element={
            <PrivateRoute>
              <MomentsPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/spheres"
          element={
            <PrivateRoute>
              <SpheresPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/search"
          element={
            <PrivateRoute>
              <SearchPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/analytics"
          element={
            <PrivateRoute>
              <AnalyticsPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/settings"
          element={
            <PrivateRoute>
              <SettingsPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/reflection"
          element={
            <PrivateRoute>
              <ReflectionPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/collaboration"
          element={
            <PrivateRoute>
              <CollaborationPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/memory-expansion"
          element={
            <PrivateRoute>
              <MemoryExpansionPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/language-insights"
          element={
            <PrivateRoute>
              <LanguageInsightsPage />
            </PrivateRoute>
          }
        />

        {/* Catch all */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;

