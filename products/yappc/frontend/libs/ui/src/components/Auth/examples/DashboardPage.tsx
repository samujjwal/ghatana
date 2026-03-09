/**
 * Dashboard Page Example
 * 
 * Production-ready protected dashboard demonstrating route protection,
 * user state management, and secure content display.
 * 
 * @doc.type example
 * @doc.purpose Protected page example
 * @doc.layer ui
 * @doc.pattern Page Component
 */

import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@ghatana/yappc-canvas';
import { ProtectedRoute } from '../ProtectedRoute';
import { useToast } from '../../Toast';
import { Page } from '../../Page/Page';

/**
 * Dashboard page props
 */
export interface DashboardPageProps {
  /**
   * Required user roles to access dashboard
   * @default ['user']
   */
  requiredRoles?: string[];
  
  /**
   * Required permissions to access dashboard
   */
  requiredPermissions?: string[];
  
  /**
   * Custom page title
   * @default 'Dashboard'
   */
  title?: string;
  
  /**
   * Show user welcome message
   * @default true
   */
  showWelcome?: boolean;
}

/**
 * Dashboard Page Component
 * 
 * Protected dashboard page demonstrating authentication state access,
 * role-based content, and user actions.
 * 
 * @example Basic Usage
 * ```tsx
 * <DashboardPage />
 * ```
 * 
 * @example Admin Dashboard
 * ```tsx
 * <DashboardPage
 *   requiredRoles={['admin']}
 *   title="Admin Dashboard"
 * />
 * ```
 * 
 * @example With Router
 * ```tsx
 * <Route
 *   path="/dashboard"
 *   element={<DashboardPage />}
 * />
 * ```
 */
export function DashboardPage({
  requiredRoles = ['user'],
  requiredPermissions,
  title = 'Dashboard',
  showWelcome = true,
}: DashboardPageProps): React.JSX.Element {
  const navigate = useNavigate();
  const { user, logout, isLoading, isAuthenticated } = useAuth();
  const toast = useToast();
  
  /**
   * Handle logout action
   */
  const handleLogout = async () => {
    try {
      await logout();
      toast.success('You have been logged out successfully', {
        title: 'Logged Out',
        duration: 3000,
      });
      navigate('/login', { replace: true });
    } catch (error) {
      toast.error('Failed to logout. Please try again.', {
        title: 'Logout Failed',
        duration: 5000,
      });
    }
  };
  
  /**
   * Dashboard Content
   */
  const DashboardContent = () => (
    <Page
      pageTitle={title}
      subtitle={showWelcome && (user as unknown)?.name ? `Welcome back, ${(user as unknown).name}!` : undefined}
      breadcrumbs={[
        { label: 'Home', href: '/' },
        { label: title },
      ]}
      actions={
        <button
          onClick={handleLogout}
          disabled={isLoading}
          style={{
            padding: '8px 16px',
            background: '#dc2626',
            color: 'white',
            border: 'none',
            borderRadius: '6px',
            fontSize: '14px',
            fontWeight: 600,
            cursor: isLoading ? 'not-allowed' : 'pointer',
            opacity: isLoading ? 0.6 : 1,
          }}
        >
          {isLoading ? 'Logging out...' : 'Logout'}
        </button>
      }
      maxWidth="lg"
      container
      padding={3}
    >
      {/* User Info Card */}
      <div
        style={{
          padding: '24px',
          background: 'white',
          borderRadius: '12px',
          boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
          marginBottom: '24px',
        }}
      >
        <h2
          style={{
            fontSize: '20px',
            fontWeight: 600,
            color: '#1a1a1a',
            margin: '0 0 16px 0',
          }}
        >
          Account Information
        </h2>
        
        <div style={{ display: 'grid', gap: '12px' }}>
          <div>
            <span style={{ fontSize: '14px', color: '#666', fontWeight: 600 }}>
              Name:
            </span>{' '}
            <span style={{ fontSize: '14px', color: '#1a1a1a' }}>
              {(user as unknown)?.name || 'N/A'}
            </span>
          </div>
          
          <div>
            <span style={{ fontSize: '14px', color: '#666', fontWeight: 600 }}>
              Email:
            </span>{' '}
            <span style={{ fontSize: '14px', color: '#1a1a1a' }}>
              {user?.email || 'N/A'}
            </span>
          </div>
          
          {(user as unknown)?.roles && (user as unknown).roles.length > 0 && (
            <div>
              <span style={{ fontSize: '14px', color: '#666', fontWeight: 600 }}>
                Roles:
              </span>{' '}
              <span style={{ fontSize: '14px', color: '#1a1a1a' }}>
                {(user as unknown).roles.join(', ')}
              </span>
            </div>
          )}
          
          {(user as unknown)?.permissions && (user as unknown).permissions.length > 0 && (
            <div>
              <span style={{ fontSize: '14px', color: '#666', fontWeight: 600 }}>
                Permissions:
              </span>{' '}
              <span style={{ fontSize: '14px', color: '#1a1a1a' }}>
                {(user as unknown).permissions.join(', ')}
              </span>
            </div>
          )}
        </div>
      </div>
      
      {/* Quick Actions */}
      <div
        style={{
          padding: '24px',
          background: 'white',
          borderRadius: '12px',
          boxShadow: '0 1px 3px rgba(0, 0, 0, 0.1)',
        }}
      >
        <h2
          style={{
            fontSize: '20px',
            fontWeight: 600,
            color: '#1a1a1a',
            margin: '0 0 16px 0',
          }}
        >
          Quick Actions
        </h2>
        
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '16px',
          }}
        >
          <button
            onClick={() => navigate('/profile')}
            style={{
              padding: '16px',
              background: '#f3f4f6',
              border: '1px solid #e5e7eb',
              borderRadius: '8px',
              fontSize: '14px',
              fontWeight: 600,
              color: '#1a1a1a',
              cursor: 'pointer',
              transition: 'all 0.2s',
            }}
          >
            Edit Profile
          </button>
          
          <button
            onClick={() => navigate('/settings')}
            style={{
              padding: '16px',
              background: '#f3f4f6',
              border: '1px solid #e5e7eb',
              borderRadius: '8px',
              fontSize: '14px',
              fontWeight: 600,
              color: '#1a1a1a',
              cursor: 'pointer',
              transition: 'all 0.2s',
            }}
          >
            Settings
          </button>
          
          <button
            onClick={() => navigate('/help')}
            style={{
              padding: '16px',
              background: '#f3f4f6',
              border: '1px solid #e5e7eb',
              borderRadius: '8px',
              fontSize: '14px',
              fontWeight: 600,
              color: '#1a1a1a',
              cursor: 'pointer',
              transition: 'all 0.2s',
            }}
          >
            Help & Support
          </button>
        </div>
      </div>
      
      {/* Role-Based Content */}
      {(user as unknown)?.roles?.includes('admin') && (
        <div
          style={{
            marginTop: '24px',
            padding: '24px',
            background: '#fef3c7',
            border: '1px solid #f59e0b',
            borderRadius: '12px',
          }}
        >
          <h2
            style={{
              fontSize: '20px',
              fontWeight: 600,
              color: '#92400e',
              margin: '0 0 8px 0',
            }}
          >
            🔐 Admin Access
          </h2>
          <p style={{ fontSize: '14px', color: '#92400e', margin: 0 }}>
            You have administrative privileges. Access the admin panel to manage users,
            settings, and system configuration.
          </p>
          <button
            onClick={() => navigate('/admin')}
            style={{
              marginTop: '16px',
              padding: '8px 16px',
              background: '#f59e0b',
              color: 'white',
              border: 'none',
              borderRadius: '6px',
              fontSize: '14px',
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            Open Admin Panel
          </button>
        </div>
      )}
    </Page>
  );
  
  return (
    <ProtectedRoute
      isAuthenticated={isAuthenticated}
      isLoading={isLoading}
      requiredRoles={requiredRoles}
      requiredPermissions={requiredPermissions}
      userRoles={(user as unknown)?.roles}
      userPermissions={(user as unknown)?.permissions}
      redirectTo="/login"
      unauthorizedRedirectTo="/403"
    >
      <DashboardContent />
    </ProtectedRoute>
  );
}

export default DashboardPage;
