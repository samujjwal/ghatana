/**
 * Authentication Examples Module
 * 
 * Complete collection of production-ready authentication examples including:
 * - Login and registration pages
 * - Protected dashboards
 * - Router configuration
 * - Application setup
 * 
 * @doc.type module
 * @doc.purpose Authentication examples
 * @doc.layer ui
 * @doc.pattern Examples
 */

// =============================================================================
// Page Examples
// =============================================================================

export { LoginPage } from './LoginPage';
export type { LoginPageProps } from './LoginPage';

export { RegisterPage } from './RegisterPage';
export type { RegisterPageProps } from './RegisterPage';

export { DashboardPage } from './DashboardPage';
export type { DashboardPageProps } from './DashboardPage';

// =============================================================================
// Configuration Examples
// =============================================================================

export {
  router,
  routes,
  ProtectedDashboard,
  ProtectedAdminPage,
  ProtectedEditorPage,
} from './RouterExample';

export { App, initializeApp, setupDevEnvironment, setupProductionEnvironment } from './AppExample';
export type { AppProps } from './AppExample';

// =============================================================================
// Usage Documentation
// =============================================================================

/**
 * @example Quick Start - Complete Application
 * 
 * 1. Install dependencies:
 * ```bash
 * pnpm add @ghatana/yappc-ui @ghatana/yappc-canvas @ghatana/yappc-api jotai react-router-dom zod
 * ```
 * 
 * 2. Create main.tsx:
 * ```tsx
 * import { createRoot } from 'react-dom/client';
 * import { App, initializeApp } from '@ghatana/yappc-ui';
 * import './index.css';
 * 
 * initializeApp();
 * 
 * const root = createRoot(document.getElementById('root')!);
 * root.render(<App />);
 * ```
 * 
 * 3. Configure environment (.env):
 * ```bash
 * VITE_API_BASE_URL=http://localhost:3000
 * ```
 * 
 * 4. Start dev server:
 * ```bash
 * pnpm dev
 * ```
 */

/**
 * @example Custom Login Page
 * 
 * ```tsx
 * import { LoginPage } from '@ghatana/yappc-ui';
 * import { Route } from 'react-router-dom';
 * import { MyLogo } from './components/MyLogo';
 * 
 * function Routes() {
 *   return (
 *     <Route
 *       path="/login"
 *       element={
 *         <LoginPage
 *           redirectTo="/app/dashboard"
 *           logo={<MyLogo />}
 *           title="Admin Portal"
 *           subtitle="Sign in to access the admin panel"
 *           showRememberMe={true}
 *         />
 *       }
 *     />
 *   );
 * }
 * ```
 */

/**
 * @example Custom Protected Dashboard
 * 
 * ```tsx
 * import { DashboardPage } from '@ghatana/yappc-ui';
 * import { Route } from 'react-router-dom';
 * 
 * function Routes() {
 *   return (
 *     <Route
 *       path="/dashboard"
 *       element={
 *         <DashboardPage
 *           requiredRoles={['user', 'admin']}
 *           title="My Dashboard"
 *           showWelcome={true}
 *         />
 *       }
 *     />
 *   );
 * }
 * ```
 */

/**
 * @example Using Router Configuration
 * 
 * ```tsx
 * import { RouterProvider } from 'react-router-dom';
 * import { Provider as JotaiProvider } from 'jotai';
 * import { ToastProvider } from '@ghatana/yappc-ui';
 * import { router } from '@ghatana/yappc-ui';
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
 */

/**
 * @example HOC Pattern for Protection
 * 
 * ```tsx
 * import { withProtectedRoute } from '@ghatana/yappc-ui';
 * import { MyDashboard } from './components/MyDashboard';
 * 
 * // Protect with authentication only
 * export const ProtectedDashboard = withProtectedRoute(MyDashboard, {
 *   requiredRoles: ['user'],
 * });
 * 
 * // Protect with admin role
 * export const AdminDashboard = withProtectedRoute(MyDashboard, {
 *   requiredRoles: ['admin'],
 *   unauthorizedRedirectTo: '/403',
 * });
 * 
 * // Protect with permissions
 * export const EditorDashboard = withProtectedRoute(MyDashboard, {
 *   requiredPermissions: ['write', 'edit'],
 * });
 * ```
 */

/**
 * @example Custom Router with Protection
 * 
 * ```tsx
 * import { createBrowserRouter } from 'react-router-dom';
 * import { ProtectedRoute } from '@ghatana/yappc-ui';
 * import { useAuth } from '@ghatana/yappc-canvas';
 * 
 * const AppLayout = () => {
 *   const { user, isAuthenticated, isLoading } = useAuth();
 *   
 *   return (
 *     <ProtectedRoute
 *       isAuthenticated={isAuthenticated}
 *       isLoading={isLoading}
 *       requiredRoles={['user']}
 *       userRoles={user?.roles}
 *     >
 *       <div>
 *         <Sidebar />
 *         <Outlet />
 *       </div>
 *     </ProtectedRoute>
 *   );
 * };
 * 
 * export const router = createBrowserRouter([
 *   { path: '/login', element: <LoginPage /> },
 *   {
 *     path: '/',
 *     element: <AppLayout />,
 *     children: [
 *       { path: 'dashboard', element: <Dashboard /> },
 *       { path: 'profile', element: <Profile /> },
 *     ],
 *   },
 * ]);
 * ```
 */

/**
 * @example Environment Configuration
 * 
 * Create `.env` file:
 * ```bash
 * # Required
 * VITE_API_BASE_URL=https://api.yourapp.com
 * 
 * # Optional
 * VITE_APP_NAME=YourApp
 * VITE_APP_VERSION=1.0.0
 * VITE_ENABLE_ANALYTICS=true
 * VITE_ENABLE_SOCIAL_LOGIN=false
 * VITE_TOKEN_STORAGE=localStorage
 * ```
 * 
 * Access in code:
 * ```tsx
 * const apiUrl = import.meta.env.VITE_API_BASE_URL;
 * const appName = import.meta.env.VITE_APP_NAME;
 * ```
 */

/**
 * @example Testing Setup
 * 
 * ```tsx
 * import { render, screen } from '@testing-library/react';
 * import { Provider as JotaiProvider } from 'jotai';
 * import { ToastProvider } from '@ghatana/yappc-ui';
 * import { LoginPage } from '@ghatana/yappc-ui';
 * 
 * describe('LoginPage', () => {
 *   it('renders login form', () => {
 *     render(
 *       <JotaiProvider>
 *         <ToastProvider>
 *           <LoginPage />
 *         </ToastProvider>
 *       </JotaiProvider>
 *     );
 *     
 *     expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
 *     expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
 *   });
 * });
 * ```
 */

// =============================================================================
// Best Practices
// =============================================================================

/**
 * Best Practices for Authentication Implementation
 * 
 * 1. **State Management**
 *    - Use useAuth hook for authentication state
 *    - Persist tokens securely (httpOnly cookies recommended)
 *    - Clear sensitive data on logout
 * 
 * 2. **Route Protection**
 *    - Always protect sensitive routes with ProtectedRoute
 *    - Use role-based access control (RBAC) for granular permissions
 *    - Provide clear error messages for unauthorized access
 * 
 * 3. **User Experience**
 *    - Show loading states during authentication
 *    - Preserve return path for redirects after login
 *    - Provide clear feedback with toast notifications
 *    - Validate input on client-side before submission
 * 
 * 4. **Security**
 *    - Use HTTPS in production
 *    - Implement CSRF protection
 *    - Add rate limiting for auth endpoints
 *    - Validate all user input
 *    - Use strong password requirements
 *    - Implement session timeout
 * 
 * 5. **Performance**
 *    - Lazy load non-critical routes
 *    - Minimize re-renders with proper memoization
 *    - Use code splitting for large pages
 *    - Cache static assets
 * 
 * 6. **Testing**
 *    - Test authentication flows end-to-end
 *    - Test role-based access control
 *    - Test error scenarios
 *    - Test accessibility
 * 
 * 7. **Monitoring**
 *    - Log authentication events
 *    - Monitor failed login attempts
 *    - Track session duration
 *    - Alert on security anomalies
 */
