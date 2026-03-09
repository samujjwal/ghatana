/**
 * Complete Router Example
 * 
 * Production-ready router configuration demonstrating authentication flows,
 * protected routes, public routes, and error handling.
 * 
 * @doc.type example
 * @doc.purpose Complete router setup
 * @doc.layer ui
 * @doc.pattern Router Configuration
 */

import { Suspense } from 'react';
import { createBrowserRouter, Navigate, Outlet, RouteObject } from 'react-router-dom';
import { useAuth } from '@ghatana/yappc-canvas';
import { ProtectedRoute, withProtectedRoute } from '../ProtectedRoute';
import { Spinner } from '../../Loading/Spinner';
import { LoginPage } from './LoginPage';
import { RegisterPage } from './RegisterPage';
import { DashboardPage } from './DashboardPage';

// =============================================================================
// Lazy Loaded Pages
// =============================================================================

// Auth Pages (Placeholders - create these in your app)
// const ForgotPasswordPage = lazy(() => import('./ForgotPasswordPage'));
// const ResetPasswordPage = lazy(() => import('./ResetPasswordPage'));
const ForgotPasswordPage = () => <div>Forgot Password Page</div>;
const ResetPasswordPage = () => <div>Reset Password Page</div>;

// Protected Pages (Placeholders - create these in your app)
// const ProfilePage = lazy(() => import('./ProfilePage'));
// const SettingsPage = lazy(() => import('./SettingsPage'));
// const AdminPage = lazy(() => import('./AdminPage'));
const ProfilePage = () => <div>Profile Page</div>;
const SettingsPage = () => <div>Settings Page</div>;
const AdminPage = () => <div>Admin Page</div>;

// Public Pages (Placeholders - create these in your app)
// const HomePage = lazy(() => import('./HomePage'));
// const AboutPage = lazy(() => import('./AboutPage'));
// const PricingPage = lazy(() => import('./PricingPage'));
const HomePage = () => <div>Home Page</div>;
const AboutPage = () => <div>About Page</div>;
const PricingPage = () => <div>Pricing Page</div>;

// Error Pages (Placeholders - create these in your app)
// const NotFoundPage = lazy(() => import('./NotFoundPage'));
// const UnauthorizedPage = lazy(() => import('./UnauthorizedPage'));
const NotFoundPage = () => <div>404 - Not Found</div>;
const UnauthorizedPage = () => <div>403 - Unauthorized</div>;

// =============================================================================
// Loading Components
// =============================================================================

/**
 * Full-screen loading spinner for route transitions
 */
const PageLoader = () => (
  <div
    style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
    }}
  >
    <Spinner size="lg" />
  </div>
);

/**
 * Inline loading spinner for nested routes
 */
const InlineLoader = () => (
  <div
    style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '40px',
    }}
  >
    <Spinner size="md" />
  </div>
);

// =============================================================================
// Layout Components
// =============================================================================

/**
 * Public layout for unauthenticated pages
 */
const PublicLayout = () => (
  <div>
    <header
      style={{
        padding: '16px 24px',
        background: 'white',
        borderBottom: '1px solid #e5e7eb',
      }}
    >
      <nav style={{ display: 'flex', gap: '24px', alignItems: 'center' }}>
        <a href="/" style={{ fontSize: '20px', fontWeight: 700, color: '#667eea' }}>
          YourApp
        </a>
        <div style={{ flex: 1 }} />
        <a href="/login" style={{ color: '#666', textDecoration: 'none' }}>
          Login
        </a>
        <a
          href="/register"
          style={{
            padding: '8px 16px',
            background: '#667eea',
            color: 'white',
            borderRadius: '6px',
            textDecoration: 'none',
          }}
        >
          Sign Up
        </a>
      </nav>
    </header>
    <main>
      <Suspense fallback={<PageLoader />}>
        <Outlet />
      </Suspense>
    </main>
  </div>
);

/**
 * Auth layout for login/register pages
 */
const AuthLayout = () => (
  <Suspense fallback={<PageLoader />}>
    <Outlet />
  </Suspense>
);

/**
 * App layout for authenticated pages
 */
const AppLayout = () => {
  const { user, isAuthenticated, isLoading } = useAuth();
  
  return (
    <ProtectedRoute
      isAuthenticated={isAuthenticated}
      isLoading={isLoading}
      requiredRoles={['user']}
      userRoles={(user as unknown)?.roles}
      redirectTo="/login"
    >
      <div style={{ display: 'flex', minHeight: '100vh' }}>
        {/* Sidebar */}
        <aside
          style={{
            width: '240px',
            background: '#1f2937',
            color: 'white',
            padding: '24px',
          }}
        >
          <div style={{ fontSize: '20px', fontWeight: 700, marginBottom: '32px' }}>
            YourApp
          </div>
          <nav style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <a
              href="/dashboard"
              style={{
                padding: '12px 16px',
                color: 'white',
                textDecoration: 'none',
                borderRadius: '6px',
                background: 'rgba(255, 255, 255, 0.1)',
              }}
            >
              Dashboard
            </a>
            <a
              href="/profile"
              style={{
                padding: '12px 16px',
                color: 'white',
                textDecoration: 'none',
                borderRadius: '6px',
              }}
            >
              Profile
            </a>
            <a
              href="/settings"
              style={{
                padding: '12px 16px',
                color: 'white',
                textDecoration: 'none',
                borderRadius: '6px',
              }}
            >
              Settings
            </a>
            {(user as unknown)?.roles?.includes('admin') && (
              <a
                href="/admin"
                style={{
                  padding: '12px 16px',
                  color: 'white',
                  textDecoration: 'none',
                  borderRadius: '6px',
                }}
              >
                Admin
              </a>
            )}
          </nav>
        </aside>
        
        {/* Main Content */}
        <main style={{ flex: 1, background: '#f9fafb' }}>
          <Suspense fallback={<InlineLoader />}>
            <Outlet />
          </Suspense>
        </main>
      </div>
    </ProtectedRoute>
  );
};

// =============================================================================
// Route Guards
// =============================================================================

/**
 * Redirect authenticated users away from auth pages
 */
const GuestGuard = () => {
  const { isAuthenticated, isLoading } = useAuth();
  
  if (isLoading) {
    return <PageLoader />;
  }
  
  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }
  
  return <Outlet />;
};

// =============================================================================
// Route Configuration
// =============================================================================

/**
 * Complete route configuration with authentication flows
 */
export const routes: RouteObject[] = [
  // Public routes
  {
    path: '/',
    element: <PublicLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'about', element: <AboutPage /> },
      { path: 'pricing', element: <PricingPage /> },
    ],
  },
  
  // Authentication routes (guest only)
  {
    path: '/',
    element: <GuestGuard />,
    children: [
      {
        element: <AuthLayout />,
        children: [
          { path: 'login', element: <LoginPage /> },
          { path: 'register', element: <RegisterPage /> },
          { path: 'forgot-password', element: <ForgotPasswordPage /> },
          { path: 'reset-password', element: <ResetPasswordPage /> },
        ],
      },
    ],
  },
  
  // Protected app routes
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { path: 'dashboard', element: <DashboardPage /> },
      { path: 'profile', element: <ProfilePage /> },
      { path: 'settings', element: <SettingsPage /> },
      
      // Admin routes (requires admin role)
      {
        path: 'admin',
        element: <AdminPage />,
      },
    ],
  },
  
  // Error routes
  { path: '403', element: <UnauthorizedPage /> },
  { path: '*', element: <NotFoundPage /> },
];

/**
 * Create router instance
 */
export const router = createBrowserRouter(routes);

// =============================================================================
// HOC Examples
// =============================================================================

/**
 * Using HOC pattern for route protection
 */

// Basic protection
export const ProtectedDashboard = withProtectedRoute(DashboardPage, {
  requiredRoles: ['user'],
});

// Admin-only page
export const ProtectedAdminPage = withProtectedRoute(AdminPage, {
  requiredRoles: ['admin'],
  unauthorizedRedirectTo: '/403',
});

// Permission-based protection (Placeholder - create EditorPage in your app)
// export const ProtectedEditorPage = withProtectedRoute(lazy(() => import('./EditorPage')), {
export const ProtectedEditorPage = withProtectedRoute(() => <div>Editor Page</div>, {
  requiredPermissions: ['write', 'edit'],
  unauthorizedRedirectTo: '/403',
});

// =============================================================================
// Usage Examples
// =============================================================================

/**
 * @example App Entry Point
 * 
 * ```tsx
 * import { RouterProvider } from 'react-router-dom';
 * import { Provider as JotaiProvider } from 'jotai';
 * import { ToastProvider } from '@ghatana/yappc-ui';
 * import { router } from './router';
 * 
 * function App() {
 *   return (
 *     <JotaiProvider>
 *       <ToastProvider>
 *         <RouterProvider router={router} />
 *       </ToastProvider>
 *     </JotaiProvider>
 *   );
 * }
 * ```
 * 
 * @example Custom Protected Route
 * 
 * ```tsx
 * <Route
 *   path="/premium"
 *   element={
 *     <ProtectedRoute
 *       isAuthenticated={isAuthenticated}
 *       isLoading={isLoading}
 *       requiredRoles={['premium', 'admin']}
 *       userRoles={user?.roles}
 *     >
 *       <PremiumFeaturePage />
 *     </ProtectedRoute>
 *   }
 * />
 * ```
 * 
 * @example Role-Based Navigation
 * 
 * ```tsx
 * function Navigation() {
 *   const { user } = useAuth();
 *   
 *   return (
 *     <nav>
 *       <Link to="/dashboard">Dashboard</Link>
 *       {user?.roles?.includes('admin') && (
 *         <Link to="/admin">Admin Panel</Link>
 *       )}
 *     </nav>
 *   );
 * }
 * ```
 */

export default router;
