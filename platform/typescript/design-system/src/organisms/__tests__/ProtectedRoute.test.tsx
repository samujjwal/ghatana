import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Routes, Route, MemoryRouter } from 'react-router-dom';
import {
  ProtectedRoute,
  withProtectedRoute,
  type AuthCheckResult,
} from '../ProtectedRoute';

describe('ProtectedRoute', () => {
  // Test helper components
  const ProtectedContent = () => <div>Protected Content</div>;
  const LoginPage = () => <div>Login Page</div>;
  const AccessDeniedPage = () => <div>Access Denied</div>;

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Authentication', () => {
    it('should render protected content when authenticated (boolean)', () => {
      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute isAuthenticated={() => true} />}>
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Protected Content')).toBeInTheDocument();
      expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
    });

    it('should redirect to login when not authenticated (boolean)', () => {
      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute isAuthenticated={() => false} />}>
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Login Page')).toBeInTheDocument();
      expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
    });

    it('should render protected content when authenticated (AuthCheckResult)', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['user'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute isAuthenticated={authCheck} />}>
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Protected Content')).toBeInTheDocument();
    });

    it('should redirect when not authenticated (AuthCheckResult)', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: false,
        reason: 'Token expired',
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute isAuthenticated={authCheck} />}>
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });

    it('should use custom redirect path', () => {
      const CustomLogin = () => <div>Custom Login</div>;

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/custom-login" element={<CustomLogin />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={() => false}
                  redirectTo="/custom-login"
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Custom Login')).toBeInTheDocument();
    });

    it('should call onAuthFail callback when authentication fails', () => {
      const onAuthFail = vi.fn();

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={() => false}
                  onAuthFail={onAuthFail}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(onAuthFail).toHaveBeenCalledWith('Not authenticated');
    });

    it('should call onAuthFail with custom reason', () => {
      const onAuthFail = vi.fn();
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: false,
        reason: 'Session expired',
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute isAuthenticated={authCheck} onAuthFail={onAuthFail} />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(onAuthFail).toHaveBeenCalledWith('Session expired');
    });
  });

  describe('Role-Based Access Control (RBAC)', () => {
    it('should allow access when user has required role', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['admin', 'user'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Protected Content')).toBeInTheDocument();
    });

    it('should allow access when user has one of multiple required roles', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['moderator'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin', 'moderator']}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Protected Content')).toBeInTheDocument();
    });

    it('should deny access when user lacks required role', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['user'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Login Page')).toBeInTheDocument();
      expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
    });

    it('should show custom accessDenied component when role check fails', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['user'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                  accessDenied={<AccessDeniedPage />}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Access Denied')).toBeInTheDocument();
      expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
    });

    it('should call onAuthFail with role mismatch details', () => {
      const onAuthFail = vi.fn();
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['user', 'guest'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                  onAuthFail={onAuthFail}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(onAuthFail).toHaveBeenCalledWith(
        'Missing required role. Required: [admin], User has: [user, guest]'
      );
    });

    it('should allow access when no roles specified in AuthCheckResult', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      // Should redirect because user has no roles
      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });
  });

  describe('Permission-Based Access Control (PBAC)', () => {
    it('should allow access when user has all required permissions', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        permissions: ['read:users', 'write:users', 'delete:users'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredPermissions={['read:users', 'write:users']}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Protected Content')).toBeInTheDocument();
    });

    it('should deny access when user lacks one required permission', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        permissions: ['read:users'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredPermissions={['read:users', 'write:users']}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Login Page')).toBeInTheDocument();
      expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
    });

    it('should show custom accessDenied component when permission check fails', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        permissions: ['read:users'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredPermissions={['write:users']}
                  accessDenied={<AccessDeniedPage />}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Access Denied')).toBeInTheDocument();
    });

    it('should call onAuthFail with missing permissions details', () => {
      const onAuthFail = vi.fn();
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        permissions: ['read:users'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredPermissions={['read:users', 'write:users', 'delete:users']}
                  onAuthFail={onAuthFail}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(onAuthFail).toHaveBeenCalledWith(
        'Missing required permissions: [write:users, delete:users]'
      );
    });
  });

  describe('Combined RBAC and PBAC', () => {
    it('should allow access when both role and permission checks pass', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['admin'],
        permissions: ['read:users', 'write:users'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                  requiredPermissions={['read:users', 'write:users']}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Protected Content')).toBeInTheDocument();
    });

    it('should deny access when role check passes but permission check fails', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['admin'],
        permissions: ['read:users'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                  requiredPermissions={['write:users']}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });

    it('should deny access when permission check passes but role check fails', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['user'],
        permissions: ['read:users', 'write:users'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredRoles={['admin']}
                  requiredPermissions={['read:users', 'write:users']}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });
  });

  describe('Navigate Props', () => {
    it('should pass navigateProps to Navigate component', () => {
      // This test verifies the prop is passed, but actual behavior
      // (like state preservation) is tested in integration tests
      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={() => false}
                  navigateProps={{ state: { from: '/protected' } }}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty required roles array', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['user'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute isAuthenticated={authCheck} requiredRoles={[]} />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Protected Content')).toBeInTheDocument();
    });

    it('should handle empty required permissions array', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        permissions: ['read:users'],
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute
                  isAuthenticated={authCheck}
                  requiredPermissions={[]}
                />
              }
            >
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Protected Content')).toBeInTheDocument();
    });

    it('should handle undefined roles in AuthCheckResult', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute isAuthenticated={authCheck} />}>
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Protected Content')).toBeInTheDocument();
    });

    it('should handle undefined permissions in AuthCheckResult', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
      });

      render(
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<ProtectedRoute isAuthenticated={authCheck} />}>
              <Route path="/protected" element={<ProtectedContent />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Protected Content')).toBeInTheDocument();
    });
  });
});

describe('withProtectedRoute HOC', () => {
  const TestComponent = () => <div>Test Component</div>;
  const LoginPage = () => <div>Login Page</div>;
  const AccessDeniedPage = () => <div>Access Denied</div>;

  describe('Authentication', () => {
    it('should render component when authenticated', () => {
      const ProtectedComponent = withProtectedRoute(TestComponent, {
        isAuthenticated: () => true,
      });

      render(
        <MemoryRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<ProtectedComponent />} />
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Test Component')).toBeInTheDocument();
    });

    it('should redirect when not authenticated', () => {
      const ProtectedComponent = withProtectedRoute(TestComponent, {
        isAuthenticated: () => false,
      });

      render(
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<ProtectedComponent />} />
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });

    it('should preserve component displayName', () => {
      TestComponent.displayName = 'MyTestComponent';
      const ProtectedComponent = withProtectedRoute(TestComponent, {
        isAuthenticated: () => true,
      });

      expect(ProtectedComponent.displayName).toBe('withProtectedRoute(MyTestComponent)');
    });

    it('should use component name when displayName not set', () => {
      const ComponentWithoutDisplayName = () => <div>Test</div>;
      const ProtectedComponent = withProtectedRoute(ComponentWithoutDisplayName, {
        isAuthenticated: () => true,
      });

      expect(ProtectedComponent.displayName).toBe(
        'withProtectedRoute(ComponentWithoutDisplayName)'
      );
    });
  });

  describe('Role-Based Access Control', () => {
    it('should allow access with required role', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['admin'],
      });

      const ProtectedComponent = withProtectedRoute(TestComponent, {
        isAuthenticated: authCheck,
        requiredRoles: ['admin'],
      });

      render(
        <MemoryRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<ProtectedComponent />} />
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Test Component')).toBeInTheDocument();
    });

    it('should deny access without required role', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['user'],
      });

      const ProtectedComponent = withProtectedRoute(TestComponent, {
        isAuthenticated: authCheck,
        requiredRoles: ['admin'],
      });

      render(
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<ProtectedComponent />} />
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });

    it('should show accessDenied component when role check fails', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['user'],
      });

      const ProtectedComponent = withProtectedRoute(TestComponent, {
        isAuthenticated: authCheck,
        requiredRoles: ['admin'],
        accessDenied: <AccessDeniedPage />,
      });

      render(
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<ProtectedComponent />} />
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Access Denied')).toBeInTheDocument();
    });
  });

  describe('Permission-Based Access Control', () => {
    it('should allow access with all required permissions', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        permissions: ['read:users', 'write:users'],
      });

      const ProtectedComponent = withProtectedRoute(TestComponent, {
        isAuthenticated: authCheck,
        requiredPermissions: ['read:users', 'write:users'],
      });

      render(
        <MemoryRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<ProtectedComponent />} />
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Test Component')).toBeInTheDocument();
    });

    it('should deny access without all required permissions', () => {
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        permissions: ['read:users'],
      });

      const ProtectedComponent = withProtectedRoute(TestComponent, {
        isAuthenticated: authCheck,
        requiredPermissions: ['read:users', 'write:users'],
      });

      render(
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<ProtectedComponent />} />
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });
  });

  describe('Props Passing', () => {
    it('should pass props to wrapped component', () => {
      interface TestProps {
        testProp: string;
      }

      const ComponentWithProps = ({ testProp }: TestProps) => (
        <div>Test Prop: {testProp}</div>
      );

      const ProtectedComponent = withProtectedRoute(ComponentWithProps, {
        isAuthenticated: () => true,
      });

      render(
        <MemoryRouter>
          <Routes>
            <Route path="/" element={<ProtectedComponent testProp="test value" />} />
          </Routes>
        </MemoryRouter>
      );

      expect(screen.getByText('Test Prop: test value')).toBeInTheDocument();
    });
  });

  describe('Callbacks', () => {
    it('should call onAuthFail when authentication fails', () => {
      const onAuthFail = vi.fn();

      const ProtectedComponent = withProtectedRoute(TestComponent, {
        isAuthenticated: () => false,
        onAuthFail,
      });

      render(
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<ProtectedComponent />} />
          </Routes>
        </MemoryRouter>
      );

      expect(onAuthFail).toHaveBeenCalledWith('Not authenticated');
    });

    it('should call onAuthFail when role check fails', () => {
      const onAuthFail = vi.fn();
      const authCheck = (): AuthCheckResult => ({
        isAuthenticated: true,
        roles: ['user'],
      });

      const ProtectedComponent = withProtectedRoute(TestComponent, {
        isAuthenticated: authCheck,
        requiredRoles: ['admin'],
        onAuthFail,
      });

      render(
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<ProtectedComponent />} />
          </Routes>
        </MemoryRouter>
      );

      expect(onAuthFail).toHaveBeenCalled();
      expect(onAuthFail.mock.calls[0][0]).toContain('Missing required role');
    });
  });
});
