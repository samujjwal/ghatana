/**
 * Authentication-Aware Error Boundary Component
 * 
 * Enhanced error boundary with authentication context awareness
 * - Detects authentication errors (401, 403)
 * - Redirects to login on auth failures
 * - Preserves return path for post-login redirect
 * - Clears sensitive data on certain errors
 * - Provides auth-specific error messages
 * 
 * @module ui/error
 * @doc.type component
 * @doc.purpose Auth-aware error boundary with smart error handling
 * @doc.layer ui
 */

import { Component, type ErrorInfo, type ReactNode } from 'react';
import { useAtom } from 'jotai';
import { authUserAtom, authTokenAtom } from '@ghatana/yappc-canvas';

// ============================================================================
// Types
// ============================================================================

/**
 * Auth-aware error boundary props
 */
export interface AuthErrorBoundaryProps {
  /** Child components to protect */
  children: ReactNode;
  
  /** Custom fallback UI component */
  fallback?: ReactNode | ((error: Error, errorInfo: ErrorInfo, retry: () => void) => ReactNode);
  
  /** Error callback for logging/reporting */
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  
  /** Error reset callback */
  onReset?: () => void;
  
  /** Authentication error callback */
  onAuthError?: (error: Error) => void;
  
  /** Whether to redirect to login on auth errors */
  redirectOnAuthError?: boolean;
  
  /** Login path for redirect */
  loginPath?: string;
  
  /** Whether to clear auth data on errors */
  clearAuthOnError?: boolean;
  
  /** Whether to show error details (dev mode) */
  showDetails?: boolean;
  
  /** Boundary name for identification */
  boundaryName?: string;
  
  /** Automatic reset after specified milliseconds */
  resetAfter?: number;
  
  /** Reset on navigation (requires location prop) */
  resetOnPropsChange?: boolean;
  
  /** Props to watch for changes (triggers reset) */
  resetKeys?: unknown[];
}

/**
 * Auth error boundary state
 */
interface AuthErrorBoundaryState {
  /** Has error occurred */
  hasError: boolean;
  
  /** Error object */
  error: Error | null;
  
  /** Error info with component stack */
  errorInfo: ErrorInfo | null;
  
  /** Error count (for retry limiting) */
  errorCount: number;
  
  /** Timestamp of last error */
  lastErrorTime: number;
  
  /** Is authentication error */
  isAuthError: boolean;
  
  /** HTTP status code if applicable */
  statusCode?: number;
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Check if error is authentication-related
 * 
 * @param error - Error object
 * @returns True if auth error
 */
function isAuthenticationError(error: Error): boolean {
  const message = error.message.toLowerCase();
  const authKeywords = [
    'unauthorized',
    '401',
    'unauthenticated',
    'not authenticated',
    'authentication failed',
    'invalid token',
    'token expired',
    'session expired',
    'forbidden',
    '403',
    'access denied',
    'permission denied',
  ];
  
  return authKeywords.some(keyword => message.includes(keyword));
}

/**
 * Extract HTTP status code from error
 * 
 * @param error - Error object
 * @returns Status code or undefined
 */
function extractStatusCode(error: Error): number | undefined {
  // Check for status code in error object
  if ('status' in error) {
    return (error as unknown).status;
  }
  if ('statusCode' in error) {
    return (error as unknown).statusCode;
  }
  
  // Try to extract from message
  const message = error.message;
  const match = message.match(/\b(401|403)\b/);
  if (match) {
    return parseInt(match[1], 10);
  }
  
  return undefined;
}

/**
 * Get auth-specific error message
 * 
 * @param error - Error object
 * @param statusCode - HTTP status code
 * @returns User-friendly message
 */
function getAuthErrorMessage(error: Error, statusCode?: number): string {
  if (statusCode === 401 || error.message.includes('401')) {
    return 'Your session has expired. Please log in again.';
  }
  
  if (statusCode === 403 || error.message.includes('403')) {
    return 'You don\'t have permission to access this resource.';
  }
  
  if (error.message.toLowerCase().includes('token expired')) {
    return 'Your session has expired. Please log in again.';
  }
  
  if (error.message.toLowerCase().includes('invalid token')) {
    return 'Your session is invalid. Please log in again.';
  }
  
  return 'Authentication error. Please log in again.';
}

// ============================================================================
// Auth Error Boundary Component
// ============================================================================

/**
 * Authentication-aware error boundary component
 * Catches errors and handles auth-related errors specially
 * 
 * @example
 * <AuthErrorBoundary
 *   redirectOnAuthError={true}
 *   loginPath="/login"
 *   onAuthError={(error) => logAuthError(error)}
 * >
 *   <ProtectedApp />
 * </AuthErrorBoundary>
 */
export class AuthErrorBoundary extends Component<AuthErrorBoundaryProps, AuthErrorBoundaryState> {
  private resetTimeoutId: NodeJS.Timeout | null = null;
  
  constructor(props: AuthErrorBoundaryProps) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      errorCount: 0,
      lastErrorTime: 0,
      isAuthError: false,
    };
  }
  
  /**
   * Derive state from error
   */
  static getDerivedStateFromError(error: Error): Partial<AuthErrorBoundaryState> {
    const isAuthError = isAuthenticationError(error);
    const statusCode = extractStatusCode(error);
    
    return {
      hasError: true,
      error,
      errorCount: 1,
      lastErrorTime: Date.now(),
      isAuthError,
      statusCode,
    };
  }
  
  /**
   * Component did catch
   */
  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // Update state with error info
    this.setState(prevState => ({
      errorInfo,
      errorCount: prevState.errorCount + 1,
    }));
    
    const { isAuthError, statusCode } = this.state;
    
    // Handle authentication errors
    if (isAuthError) {
      this.handleAuthError(error);
    }
    
    // Call error callback if provided
    if (this.props.onError) {
      this.props.onError(error, errorInfo);
    }
    
    // Log error to console in development
    if (process.env.NODE_ENV === 'development') {
      console.error(
        `[${this.props.boundaryName || 'AuthErrorBoundary'}] Error caught:`,
        error,
        errorInfo,
        { isAuthError, statusCode }
      );
    }
    
    // Set automatic reset timer if specified
    if (this.props.resetAfter && this.props.resetAfter > 0) {
      this.resetTimeoutId = setTimeout(() => {
        this.resetErrorBoundary();
      }, this.props.resetAfter);
    }
  }
  
  /**
   * Handle authentication errors
   */
  private handleAuthError = (error: Error): void => {
    const { onAuthError, redirectOnAuthError, loginPath, clearAuthOnError } = this.props;
    
    // Call auth error callback
    if (onAuthError) {
      onAuthError(error);
    }
    
    // Clear auth data if configured
    if (clearAuthOnError) {
      this.clearAuthData();
    }
    
    // Redirect to login if configured
    if (redirectOnAuthError) {
      const path = loginPath || '/login';
      const returnPath = window.location.pathname;
      
      // Preserve return path in sessionStorage
      if (returnPath && returnPath !== path) {
        sessionStorage.setItem('returnPath', returnPath);
      }
      
      // Redirect after a short delay
      setTimeout(() => {
        window.location.href = `${path}?returnUrl=${encodeURIComponent(returnPath)}`;
      }, 1500);
    }
  };
  
  /**
   * Clear authentication data
   */
  private clearAuthData = (): void => {
    // Clear tokens from storage
    localStorage.removeItem('authToken');
    localStorage.removeItem('refreshToken');
    sessionStorage.removeItem('authToken');
    sessionStorage.removeItem('refreshToken');
    
    // Clear user data
    localStorage.removeItem('user');
    sessionStorage.removeItem('user');
  };
  
  /**
   * Component did update
   */
  componentDidUpdate(prevProps: AuthErrorBoundaryProps): void {
    if (
      this.props.resetOnPropsChange &&
      this.props.resetKeys &&
      prevProps.resetKeys
    ) {
      const hasChanges = this.props.resetKeys.some(
        (key, index) => key !== prevProps.resetKeys?.[index]
      );
      
      if (hasChanges && this.state.hasError) {
        this.resetErrorBoundary();
      }
    }
  }
  
  /**
   * Component will unmount
   */
  componentWillUnmount(): void {
    if (this.resetTimeoutId) {
      clearTimeout(this.resetTimeoutId);
    }
  }
  
  /**
   * Reset error boundary
   */
  resetErrorBoundary = (): void => {
    if (this.resetTimeoutId) {
      clearTimeout(this.resetTimeoutId);
      this.resetTimeoutId = null;
    }
    
    // Call reset callback if provided
    if (this.props.onReset) {
      this.props.onReset();
    }
    
    // Reset state
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
      errorCount: 0,
      lastErrorTime: 0,
      isAuthError: false,
      statusCode: undefined,
    });
  };
  
  /**
   * Render
   */
  render(): ReactNode {
    const { hasError, error, errorInfo, errorCount, isAuthError, statusCode } = this.state;
    const { children, fallback, showDetails, boundaryName } = this.props;
    
    if (hasError && error) {
      // Render custom fallback if provided
      if (fallback) {
        if (typeof fallback === 'function') {
          return fallback(error, errorInfo!, this.resetErrorBoundary);
        }
        return fallback;
      }
      
      // Render auth-aware fallback
      return (
        <AuthErrorFallback
          error={error}
          errorInfo={errorInfo}
          errorCount={errorCount}
          isAuthError={isAuthError}
          statusCode={statusCode}
          showDetails={showDetails ?? process.env.NODE_ENV === 'development'}
          boundaryName={boundaryName}
          redirectOnAuthError={this.props.redirectOnAuthError}
          onReset={this.resetErrorBoundary}
        />
      );
    }
    
    return children;
  }
}

// ============================================================================
// Auth Error Fallback Component
// ============================================================================

/**
 * Auth error fallback props
 */
interface AuthErrorFallbackProps {
  error: Error;
  errorInfo: ErrorInfo | null;
  errorCount: number;
  isAuthError: boolean;
  statusCode?: number;
  showDetails: boolean;
  boundaryName?: string;
  redirectOnAuthError?: boolean;
  onReset: () => void;
}

/**
 * Authentication-aware error fallback UI
 */
function AuthErrorFallback({
  error,
  errorInfo,
  errorCount,
  isAuthError,
  statusCode,
  showDetails,
  boundaryName,
  redirectOnAuthError,
  onReset,
}: AuthErrorFallbackProps): React.JSX.Element {
  const authMessage = isAuthError ? getAuthErrorMessage(error, statusCode) : null;
  
  return (
    <div
      role="alert"
      style={{
        padding: '2rem',
        margin: '2rem auto',
        maxWidth: '600px',
        border: `2px solid ${isAuthError ? '#f59e0b' : '#ef4444'}`,
        borderRadius: '0.5rem',
        backgroundColor: isAuthError ? '#fffbeb' : '#fef2f2',
        fontFamily: 'system-ui, sans-serif',
      }}
    >
      {/* Header */}
      <div style={{ marginBottom: '1rem' }}>
        <h2
          style={{
            margin: 0,
            fontSize: '1.5rem',
            color: isAuthError ? '#d97706' : '#dc2626',
            fontWeight: 600,
          }}
        >
          {isAuthError ? '🔒 Authentication Required' : '⚠️ Something went wrong'}
        </h2>
        {boundaryName && (
          <p style={{ margin: '0.5rem 0 0 0', color: '#78350f', fontSize: '0.875rem' }}>
            Error Boundary: {boundaryName}
          </p>
        )}
      </div>
      
      {/* Auth-specific message */}
      {authMessage && (
        <div
          style={{
            padding: '1rem',
            backgroundColor: isAuthError ? '#fef3c7' : '#fee2e2',
            borderRadius: '0.375rem',
            marginBottom: '1rem',
          }}
        >
          <p style={{ margin: 0, color: isAuthError ? '#92400e' : '#991b1b', fontWeight: 500 }}>
            {authMessage}
          </p>
          {redirectOnAuthError && (
            <p style={{ margin: '0.5rem 0 0 0', color: '#78350f', fontSize: '0.875rem' }}>
              Redirecting to login page...
            </p>
          )}
        </div>
      )}
      
      {/* Regular error message */}
      {!authMessage && (
        <div
          style={{
            padding: '1rem',
            backgroundColor: '#fee2e2',
            borderRadius: '0.375rem',
            marginBottom: '1rem',
          }}
        >
          <p style={{ margin: 0, color: '#991b1b', fontWeight: 500 }}>
            {error.message || 'An unexpected error occurred'}
          </p>
        </div>
      )}
      
      {/* Status code badge */}
      {statusCode && (
        <div style={{ marginBottom: '1rem' }}>
          <span
            style={{
              display: 'inline-block',
              padding: '0.25rem 0.75rem',
              backgroundColor: statusCode === 401 ? '#fbbf24' : statusCode === 403 ? '#f59e0b' : '#ef4444',
              color: '#fff',
              borderRadius: '9999px',
              fontSize: '0.75rem',
              fontWeight: 600,
            }}
          >
            HTTP {statusCode}
          </span>
        </div>
      )}
      
      {/* Error details (development only) */}
      {showDetails && !isAuthError && (
        <>
          <details style={{ marginBottom: '1rem' }}>
            <summary
              style={{
                cursor: 'pointer',
                color: '#7f1d1d',
                fontWeight: 500,
                marginBottom: '0.5rem',
              }}
            >
              Error Details
            </summary>
            <pre
              style={{
                padding: '1rem',
                backgroundColor: '#fff',
                border: '1px solid #fca5a5',
                borderRadius: '0.375rem',
                overflow: 'auto',
                fontSize: '0.75rem',
                color: '#7f1d1d',
                maxHeight: '200px',
              }}
            >
              {error.stack}
            </pre>
          </details>
          
          {errorInfo?.componentStack && (
            <details style={{ marginBottom: '1rem' }}>
              <summary
                style={{
                  cursor: 'pointer',
                  color: '#7f1d1d',
                  fontWeight: 500,
                  marginBottom: '0.5rem',
                }}
              >
                Component Stack
              </summary>
              <pre
                style={{
                  padding: '1rem',
                  backgroundColor: '#fff',
                  border: '1px solid #fca5a5',
                  borderRadius: '0.375rem',
                  overflow: 'auto',
                  fontSize: '0.75rem',
                  color: '#7f1d1d',
                  maxHeight: '200px',
                }}
              >
                {errorInfo.componentStack}
              </pre>
            </details>
          )}
        </>
      )}
      
      {/* Actions */}
      <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
        {!redirectOnAuthError && (
          <>
            <button
              onClick={onReset}
              style={{
                padding: '0.5rem 1rem',
                backgroundColor: isAuthError ? '#d97706' : '#dc2626',
                color: '#fff',
                border: 'none',
                borderRadius: '0.375rem',
                fontWeight: 500,
                cursor: 'pointer',
                fontSize: '0.875rem',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = isAuthError ? '#b45309' : '#b91c1c';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = isAuthError ? '#d97706' : '#dc2626';
              }}
            >
              {isAuthError ? 'Go to Login' : 'Try Again'}
            </button>
            
            <button
              onClick={() => window.location.reload()}
              style={{
                padding: '0.5rem 1rem',
                backgroundColor: '#fff',
                color: isAuthError ? '#d97706' : '#dc2626',
                border: `1px solid ${isAuthError ? '#d97706' : '#dc2626'}`,
                borderRadius: '0.375rem',
                fontWeight: 500,
                cursor: 'pointer',
                fontSize: '0.875rem',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = isAuthError ? '#fffbeb' : '#fef2f2';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = '#fff';
              }}
            >
              Reload Page
            </button>
          </>
        )}
        
        {errorCount > 1 && (
          <span style={{ color: '#78350f', fontSize: '0.875rem' }}>
            Error occurred {errorCount} times
          </span>
        )}
      </div>
    </div>
  );
}

/**
 * Hook-based auth error boundary wrapper
 * Provides authentication atoms context
 */
export function AuthErrorBoundaryWithContext({ children, ...props }: AuthErrorBoundaryProps) {
  return (
    <AuthErrorBoundary {...props}>
      {children}
    </AuthErrorBoundary>
  );
}
