/**
 * Register Page Example
 * 
 * Production-ready registration page with validation,
 * password strength, and terms acceptance.
 * 
 * @doc.type example
 * @doc.purpose Registration page example
 * @doc.layer ui
 * @doc.pattern Page Component
 */

import React, { useEffect } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '@ghatana/yappc-canvas';
import { RegisterForm } from '../RegisterForm';
import { useToast } from '../../Toast';
import { Page } from '../../Page/Page';

/**
 * Register page props
 */
export interface RegisterPageProps {
  /**
   * Redirect path after successful registration
   * @default '/dashboard'
   */
  redirectTo?: string;
  
  /**
   * Show terms and conditions checkbox
   * @default true
   */
  showTerms?: boolean;
  
  /**
   * Show sign in link
   * @default true
   */
  showSignIn?: boolean;
  
  /**
   * Minimum password length
   * @default 8
   */
  minPasswordLength?: number;
  
  /**
   * Custom logo component
   */
  logo?: React.ReactNode;
  
  /**
   * Page title
   * @default 'Create Account'
   */
  title?: string;
  
  /**
   * Page subtitle
   * @default 'Get started with your free account'
   */
  subtitle?: string;
  
  /**
   * Auto-login after registration
   * @default true
   */
  autoLogin?: boolean;
}

/**
 * Register Page Component
 * 
 * Complete registration page with form validation, password strength,
 * terms acceptance, and auto-login functionality.
 * 
 * @example Basic Usage
 * ```tsx
 * <RegisterPage />
 * ```
 * 
 * @example Custom Configuration
 * ```tsx
 * <RegisterPage
 *   redirectTo="/onboarding"
 *   autoLogin={false}
 *   minPasswordLength={12}
 *   logo={<CompanyLogo />}
 * />
 * ```
 */
export function RegisterPage({
  redirectTo = '/dashboard',
  showTerms = true,
  showSignIn = true,
  minPasswordLength = 8,
  logo,
  title = 'Create Account',
  subtitle = 'Get started with your free account',
  autoLogin = true,
}: RegisterPageProps): React.JSX.Element {
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
   * Handle successful registration
   */
  const handleSuccess = () => {
    if (autoLogin) {
      toast.success('Account created successfully! Welcome aboard!', {
        title: 'Registration Successful',
        duration: 3000,
      });
      navigate(from, { replace: true });
    } else {
      toast.success('Account created successfully! Please check your email to verify.', {
        title: 'Registration Successful',
        duration: 5000,
      });
      navigate('/login', { replace: true });
    }
  };
  
  /**
   * Handle registration error
   */
  const handleError = (error: Error) => {
    toast.error(error.message || 'An error occurred', {
      title: 'Registration Failed',
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
        style={{
          width: '100%',
          maxWidth: '480px',
          padding: '48px',
          background: 'white',
          borderRadius: '16px',
          boxShadow: '0 20px 60px rgba(0, 0, 0, 0.15)',
        }}
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
        
        {/* Register Form */}
        <RegisterForm
          onSuccess={handleSuccess}
          onError={handleError}
          showTerms={showTerms}
          showSignIn={showSignIn}
          minPasswordLength={minPasswordLength}
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
          {showSignIn && (
            <p style={{ fontSize: '14px', color: '#666', margin: 0 }}>
              Already have an account?{' '}
              <Link
                to="/login"
                style={{
                  color: '#667eea',
                  fontWeight: 600,
                  textDecoration: 'none',
                }}
              >
                Sign in
              </Link>
            </p>
          )}
        </div>
        
        {/* Features */}
        <div
          style={{
            marginTop: '32px',
            padding: '16px',
            background: '#f9fafb',
            borderRadius: '8px',
          }}
        >
          <p
            style={{
              fontSize: '12px',
              fontWeight: 600,
              color: '#666',
              margin: '0 0 8px 0',
              textTransform: 'uppercase',
              letterSpacing: '0.5px',
            }}
          >
            What you get:
          </p>
          <ul
            style={{
              margin: 0,
              padding: '0 0 0 20px',
              fontSize: '14px',
              color: '#1a1a1a',
            }}
          >
            <li style={{ marginBottom: '4px' }}>
              ✓ Free account with full access
            </li>
            <li style={{ marginBottom: '4px' }}>
              ✓ Secure data encryption
            </li>
            <li style={{ marginBottom: '4px' }}>
              ✓ 24/7 customer support
            </li>
            <li>✓ No credit card required</li>
          </ul>
        </div>
      </div>
    </Page>
  );
}

export default RegisterPage;
