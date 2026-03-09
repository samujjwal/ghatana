import { lazy, Suspense, useEffect } from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { ProtectedRoute } from '@ghatana/ui';
import { performanceMonitor } from './utils/performance';
import { ErrorBoundary } from '@ghatana/ui';
import { captureException } from './config/sentry';
import { useAtomValue } from 'jotai';
import { isAuthenticatedAtom, userAtom } from './stores/authStore';
import { authService } from './services/auth.service';
import { RoleContext, ROLE_CONFIG, type UserRole } from '@ghatana/dcmaar-dashboard-core';

// Lazy load pages for code splitting
const Login = lazy(() => import('./pages/Login').then(m => ({ default: m.Login })));
const Register = lazy(() => import('./pages/Register').then(m => ({ default: m.Register })));
const Dashboard = lazy(() => import('./pages/Dashboard').then(m => ({ default: m.Dashboard })));

// Loading fallback component
function PageLoader() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="text-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-indigo-600 mx-auto"></div>
        <p className="mt-4 text-gray-600">Loading...</p>
      </div>
    </div>
  );
}

function App() {
  // Auth check for protected routes
  const isAuth = useAtomValue(isAuthenticatedAtom);
  const user = useAtomValue(userAtom);
  const checkAuth = () => isAuth || authService.isAuthenticated();

  const userRole = (user?.role as UserRole) || 'parent';
  const roleConfig = ROLE_CONFIG[userRole];

  useEffect(() => {
    // Log performance metrics after initial load (development only)
    if (import.meta.env.DEV) {
      const timer = setTimeout(() => {
        performanceMonitor.logMetrics();

        // Check performance budgets
        const budgetCheck = performanceMonitor.checkBudget({
          timeToInteractive: 3000, // 3 seconds
          largestContentfulPaint: 2500, // 2.5 seconds
          totalJSSize: 500 * 1024 // 500KB
        });

        if (!budgetCheck.passed) {
          console.warn('⚠️ Performance budget violations:');
          budgetCheck.violations.forEach(v => console.warn('  -', v));
        } else {
          console.log('✅ All performance budgets met!');
        }
      }, 2000);

      return () => clearTimeout(timer);
    }
  }, []);

  // Runtime diagnostics for auth + routing
  try {
    console.log('[Guardian] App render', {
      path: window.location.pathname,
      isAuthAtom: isAuth,
      hasToken: authService.isAuthenticated(),
      userEmail: user?.email ?? null,
      userRole,
    });
  } catch {
    // window may not be available in some environments; ignore
  }

  return (
    <RoleContext.Provider value={roleConfig}>
      <ErrorBoundary
        onError={(errorContext) => {
          // Send error to Sentry
          captureException(errorContext.error, {
            componentStack: errorContext.errorInfo.componentStack,
            errorBoundary: true,
            ...errorContext.context,
          });
        }}
      >
        <Suspense fallback={<PageLoader />}>
          <RouterProvider
            router={createBrowserRouter([
              { path: '/login', element: <Login /> },
              { path: '/register', element: <Register /> },
              {
                path: '/',
                element: (
                  <ProtectedRoute
                    isAuthenticated={checkAuth}
                    onAuthFail={(reason) => {
                      console.warn('[Guardian] ProtectedRoute auth failed', {
                        reason,
                        path: window.location.pathname,
                      });
                    }}
                  />
                ),
                children: [
                  { index: true, element: <Dashboard /> },
                  { path: 'dashboard', element: <Dashboard /> },
                ],
              },
            ])}
          />
        </Suspense>
      </ErrorBoundary>
    </RoleContext.Provider>
  );
}

export default App;
