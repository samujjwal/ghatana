import type { Meta, StoryObj } from '@storybook/react';
import { BrowserRouter, Routes, Route, Link } from 'react-router-dom';
import { ProtectedRoute, type AuthCheckResult } from './ProtectedRoute';
import { useState } from 'react';

/**
 * ProtectedRoute Component - Route Protection with RBAC/PBAC
 *
 * ## Overview
 * ProtectedRoute provides declarative route protection with injectable authentication checks,
 * role-based access control (RBAC), and permission-based access control (PBAC).
 *
 * ## Features
 * - ✅ Injectable authentication check (sync or async)
 * - ✅ Role-based access control (user needs at least ONE role)
 * - ✅ Permission-based access control (user needs ALL permissions)
 * - ✅ Custom redirect paths
 * - ✅ Custom access denied components
 * - ✅ Authentication failure callbacks
 * - ✅ Full TypeScript type safety
 *
 * ## Usage Examples
 *
 * ### Basic Authentication
 * ```tsx
 * <Route element={<ProtectedRoute isAuthenticated={() => authService.isLoggedIn()} />}>
 *   <Route path="/dashboard" element={<Dashboard />} />
 * </Route>
 * ```
 *
 * ### With Roles (OR Logic)
 * ```tsx
 * <Route
 *   element={
 *     <ProtectedRoute
 *       isAuthenticated={() => ({
 *         isAuthenticated: true,
 *         roles: user.roles,
 *       })}
 *       requiredRoles={['admin', 'moderator']}
 *     />
 *   }
 * >
 *   <Route path="/admin" element={<AdminPanel />} />
 * </Route>
 * ```
 *
 * ### With Permissions (AND Logic)
 * ```tsx
 * <Route
 *   element={
 *     <ProtectedRoute
 *       isAuthenticated={() => ({
 *         isAuthenticated: true,
 *         permissions: user.permissions,
 *       })}
 *       requiredPermissions={['read:users', 'write:users']}
 *     />
 *   }
 * >
 *   <Route path="/users" element={<UserManagement />} />
 * </Route>
 * ```
 *
 * ## Best Practices
 * 1. **Keep auth checks fast** - Use cached values when possible
 * 2. **Use roles for coarse-grained access** - Admin, user, guest
 * 3. **Use permissions for fine-grained access** - read:users, write:posts
 * 4. **Log auth failures** - For security monitoring
 * 5. **Provide clear access denied messages** - Help users understand why access was denied
 */
const meta: Meta<typeof ProtectedRoute> = {
  title: 'Organisms/ProtectedRoute',
  component: ProtectedRoute,
  parameters: {
    layout: 'fullscreen',
    docs: {
      description: {
        component:
          'Generic route protection component with RBAC/PBAC support. Works with any authentication system through dependency injection.',
      },
    },
  },
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof meta>;

// Helper components for stories
const DashboardPage = () => (
  <div style={{ padding: '2rem' }}>
    <h1 style={{ fontSize: '2rem', fontWeight: 'bold', marginBottom: '1rem' }}>
      Dashboard
    </h1>
    <p style={{ color: '#666' }}>
      Welcome to the protected dashboard. You are successfully authenticated!
    </p>
  </div>
);

const AdminPage = () => (
  <div style={{ padding: '2rem' }}>
    <h1 style={{ fontSize: '2rem', fontWeight: 'bold', marginBottom: '1rem' }}>
      Admin Panel
    </h1>
    <p style={{ color: '#666' }}>Admin-only content. You have the admin role!</p>
  </div>
);

const LoginPage = () => (
  <div style={{ padding: '2rem' }}>
    <h1 style={{ fontSize: '2rem', fontWeight: 'bold', marginBottom: '1rem' }}>
      Login Page
    </h1>
    <p style={{ color: '#666' }}>
      You were redirected here because you are not authenticated.
    </p>
  </div>
);

const AccessDeniedPage = () => (
  <div style={{ padding: '2rem', backgroundColor: '#fee', borderRadius: '8px' }}>
    <h1 style={{ fontSize: '2rem', fontWeight: 'bold', marginBottom: '1rem', color: '#c00' }}>
      Access Denied
    </h1>
    <p style={{ color: '#666' }}>
      You don't have the required permissions to access this page.
    </p>
    <button
      style={{
        marginTop: '1rem',
        padding: '0.5rem 1rem',
        backgroundColor: '#3b82f6',
        color: 'white',
        border: 'none',
        borderRadius: '4px',
        cursor: 'pointer',
      }}
      onClick={() => window.history.back()}
    >
      Go Back
    </button>
  </div>
);

/**
 * Authenticated user can access protected routes.
 * This is the most basic usage - simple boolean authentication check.
 */
export const Authenticated: Story = {
  render: () => (
    <BrowserRouter>
      <div style={{ padding: '1rem' }}>
        <nav style={{ marginBottom: '2rem', borderBottom: '1px solid #ccc', paddingBottom: '1rem' }}>
          <Link to="/" style={{ marginRight: '1rem', color: '#3b82f6' }}>Home</Link>
          <Link to="/dashboard" style={{ color: '#3b82f6' }}>Dashboard</Link>
        </nav>
        <Routes>
          <Route path="/" element={<div><h1>Home</h1><p>Public page</p></div>} />
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute isAuthenticated={() => true} />}>
            <Route path="/dashboard" element={<DashboardPage />} />
          </Route>
        </Routes>
      </div>
    </BrowserRouter>
  ),
};

/**
 * Unauthenticated user is redirected to login page.
 */
export const Unauthenticated: Story = {
  render: () => (
    <BrowserRouter>
      <div style={{ padding: '1rem' }}>
        <nav style={{ marginBottom: '2rem', borderBottom: '1px solid #ccc', paddingBottom: '1rem' }}>
          <Link to="/" style={{ marginRight: '1rem', color: '#3b82f6' }}>Home</Link>
          <Link to="/dashboard" style={{ color: '#3b82f6' }}>Dashboard</Link>
        </nav>
        <Routes>
          <Route path="/" element={<div><h1>Home</h1><p>Public page</p></div>} />
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute isAuthenticated={() => false} />}>
            <Route path="/dashboard" element={<DashboardPage />} />
          </Route>
        </Routes>
      </div>
    </BrowserRouter>
  ),
};

/**
 * Role-based access control - user with admin role can access admin page.
 * Roles use OR logic: user needs at least ONE of the required roles.
 */
export const WithRoleAccess: Story = {
  render: () => {
    const authCheck = (): AuthCheckResult => ({
      isAuthenticated: true,
      roles: ['admin', 'user'],
    });

    return (
      <BrowserRouter>
        <div style={{ padding: '1rem' }}>
          <nav style={{ marginBottom: '2rem', borderBottom: '1px solid #ccc', paddingBottom: '1rem' }}>
            <Link to="/" style={{ marginRight: '1rem', color: '#3b82f6' }}>Home</Link>
            <Link to="/admin" style={{ color: '#3b82f6' }}>Admin Panel</Link>
          </nav>
          <div style={{ marginBottom: '1rem', padding: '0.5rem', backgroundColor: '#eff6ff', borderRadius: '4px' }}>
            <strong>Current Roles:</strong> admin, user
          </div>
          <Routes>
            <Route path="/" element={<div><h1>Home</h1><p>Public page</p></div>} />
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                />
              }
            >
              <Route path="/admin" element={<AdminPage />} />
            </Route>
          </Routes>
        </div>
      </BrowserRouter>
    );
  },
};

/**
 * Role-based access denied - user lacks required role.
 */
export const WithoutRoleAccess: Story = {
  render: () => {
    const authCheck = (): AuthCheckResult => ({
      isAuthenticated: true,
      roles: ['user', 'guest'],
    });

    return (
      <BrowserRouter>
        <div style={{ padding: '1rem' }}>
          <nav style={{ marginBottom: '2rem', borderBottom: '1px solid #ccc', paddingBottom: '1rem' }}>
            <Link to="/" style={{ marginRight: '1rem', color: '#3b82f6' }}>Home</Link>
            <Link to="/admin" style={{ color: '#3b82f6' }}>Admin Panel</Link>
          </nav>
          <div style={{ marginBottom: '1rem', padding: '0.5rem', backgroundColor: '#fef3c7', borderRadius: '4px' }}>
            <strong>Current Roles:</strong> user, guest (missing: admin)
          </div>
          <Routes>
            <Route path="/" element={<div><h1>Home</h1><p>Public page</p></div>} />
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                />
              }
            >
              <Route path="/admin" element={<AdminPage />} />
            </Route>
          </Routes>
        </div>
      </BrowserRouter>
    );
  },
};

/**
 * Permission-based access control - user has all required permissions.
 * Permissions use AND logic: user needs ALL required permissions.
 */
export const WithPermissionAccess: Story = {
  render: () => {
    const authCheck = (): AuthCheckResult => ({
      isAuthenticated: true,
      permissions: ['read:users', 'write:users', 'delete:users'],
    });

    return (
      <BrowserRouter>
        <div style={{ padding: '1rem' }}>
          <nav style={{ marginBottom: '2rem', borderBottom: '1px solid #ccc', paddingBottom: '1rem' }}>
            <Link to="/" style={{ marginRight: '1rem', color: '#3b82f6' }}>Home</Link>
            <Link to="/users" style={{ color: '#3b82f6' }}>User Management</Link>
          </nav>
          <div style={{ marginBottom: '1rem', padding: '0.5rem', backgroundColor: '#eff6ff', borderRadius: '4px' }}>
            <strong>Current Permissions:</strong> read:users, write:users, delete:users
          </div>
          <Routes>
            <Route path="/" element={<div><h1>Home</h1><p>Public page</p></div>} />
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredPermissions={['read:users', 'write:users']}
                />
              }
            >
              <Route path="/users" element={
                <div style={{ padding: '2rem' }}>
                  <h1 style={{ fontSize: '2rem', fontWeight: 'bold', marginBottom: '1rem' }}>
                    User Management
                  </h1>
                  <p style={{ color: '#666' }}>You have read and write permissions!</p>
                </div>
              } />
            </Route>
          </Routes>
        </div>
      </BrowserRouter>
    );
  },
};

/**
 * Permission-based access denied - user lacks one required permission.
 */
export const WithoutPermissionAccess: Story = {
  render: () => {
    const authCheck = (): AuthCheckResult => ({
      isAuthenticated: true,
      permissions: ['read:users'],
    });

    return (
      <BrowserRouter>
        <div style={{ padding: '1rem' }}>
          <nav style={{ marginBottom: '2rem', borderBottom: '1px solid #ccc', paddingBottom: '1rem' }}>
            <Link to="/" style={{ marginRight: '1rem', color: '#3b82f6' }}>Home</Link>
            <Link to="/users" style={{ color: '#3b82f6' }}>User Management</Link>
          </nav>
          <div style={{ marginBottom: '1rem', padding: '0.5rem', backgroundColor: '#fef3c7', borderRadius: '4px' }}>
            <strong>Current Permissions:</strong> read:users (missing: write:users)
          </div>
          <Routes>
            <Route path="/" element={<div><h1>Home</h1><p>Public page</p></div>} />
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredPermissions={['read:users', 'write:users']}
                />
              }
            >
              <Route path="/users" element={
                <div style={{ padding: '2rem' }}>
                  <h1>User Management</h1>
                </div>
              } />
            </Route>
          </Routes>
        </div>
      </BrowserRouter>
    );
  },
};

/**
 * Custom access denied component instead of redirect.
 */
export const CustomAccessDenied: Story = {
  render: () => {
    const authCheck = (): AuthCheckResult => ({
      isAuthenticated: true,
      roles: ['user'],
    });

    return (
      <BrowserRouter>
        <div style={{ padding: '1rem' }}>
          <nav style={{ marginBottom: '2rem', borderBottom: '1px solid #ccc', paddingBottom: '1rem' }}>
            <Link to="/" style={{ marginRight: '1rem', color: '#3b82f6' }}>Home</Link>
            <Link to="/admin" style={{ color: '#3b82f6' }}>Admin Panel</Link>
          </nav>
          <Routes>
            <Route path="/" element={<div><h1>Home</h1><p>Public page</p></div>} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                  accessDenied={<AccessDeniedPage />}
                />
              }
            >
              <Route path="/admin" element={<AdminPage />} />
            </Route>
          </Routes>
        </div>
      </BrowserRouter>
    );
  },
};

/**
 * Combined roles and permissions - user must satisfy both checks.
 */
export const CombinedRolesAndPermissions: Story = {
  render: () => {
    const authCheck = (): AuthCheckResult => ({
      isAuthenticated: true,
      roles: ['admin'],
      permissions: ['read:users', 'write:users'],
    });

    return (
      <BrowserRouter>
        <div style={{ padding: '1rem' }}>
          <nav style={{ marginBottom: '2rem', borderBottom: '1px solid #ccc', paddingBottom: '1rem' }}>
            <Link to="/" style={{ marginRight: '1rem', color: '#3b82f6' }}>Home</Link>
            <Link to="/admin" style={{ color: '#3b82f6' }}>Admin Panel</Link>
          </nav>
          <div style={{ marginBottom: '1rem', padding: '0.5rem', backgroundColor: '#eff6ff', borderRadius: '4px' }}>
            <strong>Current Roles:</strong> admin<br />
            <strong>Current Permissions:</strong> read:users, write:users
          </div>
          <Routes>
            <Route path="/" element={<div><h1>Home</h1><p>Public page</p></div>} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                  requiredPermissions={['read:users', 'write:users']}
                />
              }
            >
              <Route path="/admin" element={<AdminPage />} />
            </Route>
          </Routes>
        </div>
      </BrowserRouter>
    );
  },
};

/**
 * Interactive demo - toggle authentication state.
 */
export const InteractiveDemo: Story = {
  render: function InteractiveDemoRender() {
    const [isAuth, setIsAuth] = useState(false);
    const [userRole, setUserRole] = useState<'user' | 'admin'>('user');

    const authCheck = (): AuthCheckResult => ({
      isAuthenticated: isAuth,
      roles: [userRole],
    });

    return (
      <BrowserRouter>
        <div style={{ padding: '1rem' }}>
          <div style={{ marginBottom: '2rem', padding: '1rem', backgroundColor: '#f3f4f6', borderRadius: '8px' }}>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 'bold', marginBottom: '1rem' }}>
              Controls
            </h2>
            <div style={{ display: 'flex', gap: '1rem', marginBottom: '1rem' }}>
              <button
                onClick={() => setIsAuth(!isAuth)}
                style={{
                  padding: '0.5rem 1rem',
                  backgroundColor: isAuth ? '#ef4444' : '#10b981',
                  color: 'white',
                  border: 'none',
                  borderRadius: '4px',
                  cursor: 'pointer',
                }}
              >
                {isAuth ? 'Logout' : 'Login'}
              </button>
              <select
                value={userRole}
                onChange={(e) => setUserRole(e.target.value as 'user' | 'admin')}
                style={{
                  padding: '0.5rem',
                  border: '1px solid #ccc',
                  borderRadius: '4px',
                }}
                disabled={!isAuth}
              >
                <option value="user">User Role</option>
                <option value="admin">Admin Role</option>
              </select>
            </div>
            <div style={{ padding: '0.5rem', backgroundColor: '#eff6ff', borderRadius: '4px' }}>
              <strong>Status:</strong> {isAuth ? 'Authenticated' : 'Not Authenticated'}<br />
              <strong>Role:</strong> {isAuth ? userRole : 'N/A'}
            </div>
          </div>

          <nav style={{ marginBottom: '2rem', borderBottom: '1px solid #ccc', paddingBottom: '1rem' }}>
            <Link to="/" style={{ marginRight: '1rem', color: '#3b82f6' }}>Home</Link>
            <Link to="/dashboard" style={{ marginRight: '1rem', color: '#3b82f6' }}>Dashboard</Link>
            <Link to="/admin" style={{ color: '#3b82f6' }}>Admin Panel</Link>
          </nav>

          <Routes>
            <Route path="/" element={<div><h1>Home</h1><p>Public page - no auth required</p></div>} />
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute isAuthenticated={authCheck} />}>
              <Route path="/dashboard" element={<DashboardPage />} />
            </Route>
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                  accessDenied={<AccessDeniedPage />}
                />
              }
            >
              <Route path="/admin" element={<AdminPage />} />
            </Route>
          </Routes>
        </div>
      </BrowserRouter>
    );
  },
};

/**
 * Multiple protected routes with different requirements.
 */
export const MultipleRoutes: Story = {
  render: () => {
    const authCheck = (): AuthCheckResult => ({
      isAuthenticated: true,
      roles: ['user'],
      permissions: ['read:profile'],
    });

    return (
      <BrowserRouter>
        <div style={{ padding: '1rem' }}>
          <nav style={{ marginBottom: '2rem', borderBottom: '1px solid #ccc', paddingBottom: '1rem' }}>
            <Link to="/" style={{ marginRight: '1rem', color: '#3b82f6' }}>Home</Link>
            <Link to="/dashboard" style={{ marginRight: '1rem', color: '#3b82f6' }}>Dashboard</Link>
            <Link to="/admin" style={{ marginRight: '1rem', color: '#3b82f6' }}>Admin</Link>
            <Link to="/profile" style={{ color: '#3b82f6' }}>Profile</Link>
          </nav>
          <div style={{ marginBottom: '1rem', padding: '0.5rem', backgroundColor: '#eff6ff', borderRadius: '4px' }}>
            <strong>Current User:</strong> Authenticated, Role: user, Permissions: read:profile
          </div>
          <Routes>
            <Route path="/" element={<div><h1>Home</h1><p>Public page</p></div>} />
            <Route path="/login" element={<LoginPage />} />
            
            {/* Basic auth - accessible */}
            <Route element={<ProtectedRoute isAuthenticated={authCheck} />}>
              <Route path="/dashboard" element={<DashboardPage />} />
            </Route>

            {/* Admin role required - not accessible */}
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                  accessDenied={<AccessDeniedPage />}
                />
              }
            >
              <Route path="/admin" element={<AdminPage />} />
            </Route>

            {/* Permission required - accessible */}
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredPermissions={['read:profile']}
                />
              }
            >
              <Route path="/profile" element={
                <div style={{ padding: '2rem' }}>
                  <h1 style={{ fontSize: '2rem', fontWeight: 'bold', marginBottom: '1rem' }}>
                    Profile
                  </h1>
                  <p style={{ color: '#666' }}>You have read:profile permission!</p>
                </div>
              } />
            </Route>
          </Routes>
        </div>
      </BrowserRouter>
    );
  },
};

/**
 * Custom redirect path for unauthenticated users.
 */
export const CustomRedirectPath: Story = {
  render: () => (
    <BrowserRouter>
      <div style={{ padding: '1rem' }}>
        <nav style={{ marginBottom: '2rem', borderBottom: '1px solid #ccc', paddingBottom: '1rem' }}>
          <Link to="/" style={{ marginRight: '1rem', color: '#3b82f6' }}>Home</Link>
          <Link to="/dashboard" style={{ color: '#3b82f6' }}>Dashboard</Link>
        </nav>
        <Routes>
          <Route path="/" element={<div><h1>Home</h1><p>Public page</p></div>} />
          <Route path="/welcome" element={
            <div style={{ padding: '2rem' }}>
              <h1>Welcome!</h1>
              <p>Custom redirect page instead of /login</p>
            </div>
          } />
          <Route
            element={
              <ProtectedRoute
                isAuthenticated={() => false}
                redirectTo="/welcome"
              />
            }
          >
            <Route path="/dashboard" element={<DashboardPage />} />
          </Route>
        </Routes>
      </div>
    </BrowserRouter>
  ),
};

/**
 * Authentication failure callback for logging/analytics.
 */
export const WithAuthFailureCallback: Story = {
  render: () => {
    const [logs, setLogs] = useState<string[]>([]);

    const handleAuthFail = (reason: string) => {
      const timestamp = new Date().toLocaleTimeString();
      setLogs((prev) => [...prev, `[${timestamp}] ${reason}`]);
    };

    const authCheck = (): AuthCheckResult => ({
      isAuthenticated: true,
      roles: ['user'],
    });

    return (
      <BrowserRouter>
        <div style={{ padding: '1rem' }}>
          <div style={{ marginBottom: '2rem', padding: '1rem', backgroundColor: '#f3f4f6', borderRadius: '8px' }}>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>
              Auth Failure Logs
            </h2>
            <div style={{
              padding: '0.5rem',
              backgroundColor: '#1f2937',
              color: '#10b981',
              borderRadius: '4px',
              fontFamily: 'monospace',
              fontSize: '0.875rem',
              maxHeight: '150px',
              overflowY: 'auto',
            }}>
              {logs.length === 0 ? (
                <div>No auth failures yet</div>
              ) : (
                logs.map((log, i) => <div key={i}>{log}</div>)
              )}
            </div>
          </div>

          <nav style={{ marginBottom: '2rem', borderBottom: '1px solid #ccc', paddingBottom: '1rem' }}>
            <Link to="/" style={{ marginRight: '1rem', color: '#3b82f6' }}>Home</Link>
            <Link to="/admin" style={{ color: '#3b82f6' }}>Admin Panel (will fail)</Link>
          </nav>

          <Routes>
            <Route path="/" element={<div><h1>Home</h1><p>Click "Admin Panel" to trigger auth failure</p></div>} />
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                  onAuthFail={handleAuthFail}
                  accessDenied={<AccessDeniedPage />}
                />
              }
            >
              <Route path="/admin" element={<AdminPage />} />
            </Route>
          </Routes>
        </div>
      </BrowserRouter>
    );
  },
};
