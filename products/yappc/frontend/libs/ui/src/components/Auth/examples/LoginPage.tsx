/**
 * Login Page Example
 * 
 * Production-ready login page demonstrating complete authentication flow
 * with routing, state management, and user experience best practices.
 * 
 * @doc.type example
 * @doc.purpose Authentication page example
 * @doc.layer ui
 * @doc.pattern Page Component
 */

import React, { useEffect } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '@ghatana/yappc-canvas';
import { LoginForm } from '../LoginForm';
import { useToast } from '../../Toast';
import { Page } from '../../Page/Page';

/**
 * Login page props
 */
export interface LoginPageProps {
  /**
   * Redirect path after successful login
   * @default '/dashboard'
   */
  redirectTo?: string;
  
  /**
   * Show remember me option
   * @default true
   */
  showRememberMe?: boolean;
  
  /**
   * Show forgot password link
   * @default true
   */
  showForgotPassword?: boolean;
  
  /**
   * Show sign up link
   * @default true
   */
  showSignUp?: boolean;
  
  /**
   * Custom logo component
   */
  logo?: React.ReactNode;
  
  /**
   * Page title
   * @default 'Welcome Back'
   */
  title?: string;
  
  /**
   * Page subtitle
   * @default 'Sign in to your account to continue'
   */
  subtitle?: string;
}

/**
 * Login Page Component
 * 
 * Complete login page with form, navigation, and state management.
 * Demonstrates production-ready authentication patterns.
 * 
 * @example Basic Usage
 * ```tsx
 * <LoginPage />
 * ```
 * 
 * @example Custom Configuration
 * ```tsx
 * <LoginPage
 *   redirectTo="/admin"
 *   showRememberMe={false}
 *   title="Admin Login"
 *   logo={<CompanyLogo />}
 * />
 * ```
 * 
 * @example With Router
 * ```tsx
 * <Route path="/login" element={<LoginPage />} />
 * ```
 */
export function LoginPage({
  redirectTo = '/dashboard',
  showRememberMe = true,
  showForgotPassword = true,
  showSignUp = true,
  logo,
  title = 'Welcome Back',
  subtitle = 'Sign in to your account to continue',
}: LoginPageProps): React.JSX.Element {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, isLoading } = useAuth();
  const toast = useToast();
  
  // Get the return path from location state or use default
  const from = (location.state as unknown)?.from?.pathname || redirectTo;
  
  // Redirect if already authenticated
  useEffect(() => {
    if (isAuthenticated && !isLoading) {
      navigate(from, { replace: true });
    }
  }, [isAuthenticated, isLoading, navigate, from]);
  
  /**
   * Handle successful login
   */
  const handleSuccess = () => {
    toast.success('Welcome back!', {
      title: 'Login Successful',
      duration: 3000,
    });
    
    // Navigate to return path or dashboard
    navigate(from, { replace: true });
  };
  
  /**
   * Handle login error
   */
  const handleError = (error: Error) => {
    toast.error(error.message || 'An error occurred', {
      title: 'Login Failed',
      duration: 5000,
    });
  };
  
  return (
    <Page
      maxWidth="sm"
      container
      padding={4}
      className="flex items-center justify-center min-h-screen" >
      <div
        style={{ width: '100%',
          maxWidth: '440px',
          padding: '48px',
          background: 'white',
          borderRadius: '16px',
          boxShadow: '0 20px 60px rgba(0, 0, 0, 0.15)' }}
      >
        {/* Logo */}
        {logo && (
          <div
            style={{
              display: 'flex',
              justifyContent: 'center',
              marginBottom: '32px',
            }}
          >
            {logo}
          </div>
        )}
        
        {/* Header */}
        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
          <h1
            style={{
              fontSize: '28px',
              fontWeight: 700,
              color: '#1a1a1a',
              margin: '0 0 8px 0',
            }}
          >
            {title}
          </h1>
          <p
            style={{
              fontSize: '15px',
              color: '#666',
              margin: 0,
            }}
          >
            {subtitle}
          </p>
        </div>
        
        {/* Login Form */}
        <LoginForm
          onSuccess={handleSuccess}
          onError={handleError}
          showRememberMe={showRememberMe}
          showForgotPassword={showForgotPassword}
          showSignUp={showSignUp}
        />
        
        {/* Additional Links */}
        <div
          style={{
            marginTop: '24px',
            paddingTop: '24px',
            borderTop: '1px solid #e5e7eb',
            textAlign: 'center',
          }}
        >
          {showSignUp && (
            <p style={{ fontSize: '14px', color: '#666', margin: 0 }}>
              Don't have an account?{' '}
              <Link
                to="/register"
                style={{
                  color: '#667eea',
                  fontWeight: 600,
                  textDecoration: 'none',
                }}
              >
                Create one now
              </Link>
            </p>
          )}
        </div>
        
        {/* Terms & Privacy */}
        <div
          style={{
            marginTop: '24px',
            textAlign: 'center',
          }}
        >
          <p style={{ fontSize: '12px', color: '#999', margin: 0 }}>
            By continuing, you agree to our{' '}
            <Link
              to="/terms"
              style={{
                color: '#999',
                textDecoration: 'underline',
              }}
            >
              Terms of Service
            </Link>{' '}
            and{' '}
            <Link
              to="/privacy"
              style={{
                color: '#999',
                textDecoration: 'underline',
              }}
            >
              Privacy Policy
            </Link>
          </p>
        </div>
      </div>
    </Page>
  );
}

export default LoginPage;
