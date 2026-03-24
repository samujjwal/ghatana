/**
 * Error Fallback Components
 * 
 * Pre-built fallback components for common error scenarios
 * 
 * @module ui/error
 * @doc.type component
 * @doc.purpose Reusable error fallback UI components
 * @doc.layer ui
 */

import type { ReactNode } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Base error fallback props
 */
export interface ErrorFallbackProps {
  /** Error object */
  error: Error;
  
  /** Reset callback */
  onReset?: () => void;
  
  /** Additional actions */
  actions?: ReactNode;
  
  /** Custom title */
  title?: string;
  
  /** Custom message */
  message?: string;
}

/**
 * Minimal error fallback props
 */
export interface MinimalErrorFallbackProps {
  /** Error message override */
  message?: string;
  
  /** Reset callback */
  onReset?: () => void;
}

/**
 * Full page error fallback props
 */
export interface FullPageErrorFallbackProps extends ErrorFallbackProps {
  /** Whether to show contact support */
  showSupport?: boolean;
  
  /** Support email */
  supportEmail?: string;
}

// ============================================================================
// Minimal Error Fallback
// ============================================================================

/**
 * Minimal error fallback
 * For small components, inline errors
 * 
 * @example
 * <ErrorBoundary fallback={<MinimalErrorFallback />}>
 *   <SmallComponent />
 * </ErrorBoundary>
 */
export function MinimalErrorFallback({
  message = 'Something went wrong',
  onReset,
}: MinimalErrorFallbackProps): React.JSX.Element {
  return (
    <div
      role="alert"
      style={{
        padding: '1rem',
        border: '1px solid #fca5a5',
        borderRadius: '0.375rem',
        backgroundColor: '#fef2f2',
        fontSize: '0.875rem',
      }}
    >
      <span style={{ color: '#991b1b' }}>⚠️ {message}</span>
      {onReset && (
        <button
          onClick={onReset}
          style={{
            marginLeft: '0.5rem',
            padding: '0.25rem 0.5rem',
            backgroundColor: 'transparent',
            color: '#dc2626',
            border: 'none',
            textDecoration: 'underline',
            cursor: 'pointer',
            fontSize: '0.875rem',
          }}
        >
          Try again
        </button>
      )}
    </div>
  );
}

// ============================================================================
// Card Error Fallback
// ============================================================================

/**
 * Card error fallback
 * For card/panel components
 * 
 * @example
 * <ErrorBoundary fallback={<CardErrorFallback />}>
 *   <DashboardCard />
 * </ErrorBoundary>
 */
export function CardErrorFallback({
  error,
  onReset,
  title = 'Failed to load',
  message,
}: ErrorFallbackProps): React.JSX.Element {
  return (
    <div
      role="alert"
      style={{
        padding: '1.5rem',
        border: '1px solid #fca5a5',
        borderRadius: '0.5rem',
        backgroundColor: '#fef2f2',
        textAlign: 'center',
      }}
    >
      <div style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>⚠️</div>
      <h3
        style={{
          margin: '0 0 0.5rem 0',
          fontSize: '1rem',
          color: '#991b1b',
          fontWeight: 600,
        }}
      >
        {title}
      </h3>
      <p style={{ margin: '0 0 1rem 0', color: '#7f1d1d', fontSize: '0.875rem' }}>
        {message || error.message || 'An error occurred while loading this component'}
      </p>
      {onReset && (
        <button
          onClick={onReset}
          style={{
            padding: '0.5rem 1rem',
            backgroundColor: '#dc2626',
            color: '#fff',
            border: 'none',
            borderRadius: '0.375rem',
            fontWeight: 500,
            cursor: 'pointer',
            fontSize: '0.875rem',
          }}
        >
          Retry
        </button>
      )}
    </div>
  );
}

// ============================================================================
// Full Page Error Fallback
// ============================================================================

/**
 * Full page error fallback
 * For entire application/route errors
 * 
 * @example
 * <ErrorBoundary fallback={<FullPageErrorFallback />}>
 *   <App />
 * </ErrorBoundary>
 */
export function FullPageErrorFallback({
  error,
  onReset,
  title = 'Application Error',
  message,
  showSupport = true,
  supportEmail = 'support@yappc.app',
  actions,
}: FullPageErrorFallbackProps): React.JSX.Element {
  return (
    <div
      role="alert"
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#fef2f2',
        padding: '2rem',
      }}
    >
      <div
        style={{
          maxWidth: '600px',
          width: '100%',
          backgroundColor: '#fff',
          border: '2px solid #ef4444',
          borderRadius: '0.75rem',
          padding: '3rem',
          boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)',
        }}
      >
        {/* Icon */}
        <div
          style={{
            width: '4rem',
            height: '4rem',
            margin: '0 auto 1.5rem auto',
            backgroundColor: '#fee2e2',
            borderRadius: '50%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '2rem',
          }}
        >
          ⚠️
        </div>
        
        {/* Title */}
        <h1
          style={{
            margin: '0 0 1rem 0',
            fontSize: '1.875rem',
            color: '#dc2626',
            fontWeight: 700,
            textAlign: 'center',
          }}
        >
          {title}
        </h1>
        
        {/* Message */}
        <p
          style={{
            margin: '0 0 1.5rem 0',
            color: '#7f1d1d',
            fontSize: '1rem',
            textAlign: 'center',
            lineHeight: 1.6,
          }}
        >
          {message || 
            'We encountered an unexpected error. Please try again or contact support if the problem persists.'}
        </p>
        
        {/* Error details */}
        {process.env.NODE_ENV === 'development' && (
          <details style={{ marginBottom: '1.5rem' }}>
            <summary
              style={{
                cursor: 'pointer',
                color: '#991b1b',
                fontWeight: 500,
                fontSize: '0.875rem',
                marginBottom: '0.5rem',
              }}
            >
              Technical Details
            </summary>
            <pre
              style={{
                padding: '1rem',
                backgroundColor: '#fef2f2',
                border: '1px solid #fca5a5',
                borderRadius: '0.375rem',
                overflow: 'auto',
                fontSize: '0.75rem',
                color: '#7f1d1d',
                maxHeight: '150px',
              }}
            >
              {error.message}
              {'\n\n'}
              {error.stack}
            </pre>
          </details>
        )}
        
        {/* Actions */}
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '0.75rem',
            marginBottom: showSupport ? '1.5rem' : 0,
          }}
        >
          {onReset && (
            <button
              onClick={onReset}
              style={{
                width: '100%',
                padding: '0.75rem 1.5rem',
                backgroundColor: '#dc2626',
                color: '#fff',
                border: 'none',
                borderRadius: '0.5rem',
                fontWeight: 600,
                cursor: 'pointer',
                fontSize: '1rem',
              }}
            >
              Try Again
            </button>
          )}
          
          <button
            onClick={() => window.location.href = '/'}
            style={{
              width: '100%',
              padding: '0.75rem 1.5rem',
              backgroundColor: '#fff',
              color: '#dc2626',
              border: '2px solid #dc2626',
              borderRadius: '0.5rem',
              fontWeight: 600,
              cursor: 'pointer',
              fontSize: '1rem',
            }}
          >
            Go to Homepage
          </button>
          
          {actions}
        </div>
        
        {/* Support */}
        {showSupport && (
          <div
            style={{
              paddingTop: '1.5rem',
              borderTop: '1px solid #fca5a5',
              textAlign: 'center',
            }}
          >
            <p style={{ margin: '0 0 0.5rem 0', color: '#7f1d1d', fontSize: '0.875rem' }}>
              Need help? Contact our support team
            </p>
            <a
              href={`mailto:${supportEmail}?subject=Application Error&body=Error: ${encodeURIComponent(error.message)}`}
              style={{
                color: '#dc2626',
                fontWeight: 600,
                fontSize: '0.875rem',
                textDecoration: 'none',
              }}
            >
              {supportEmail}
            </a>
          </div>
        )}
      </div>
    </div>
  );
}

// ============================================================================
// Network Error Fallback
// ============================================================================

/**
 * Network error fallback
 * For API/network errors
 * 
 * @example
 * <ErrorBoundary fallback={<NetworkErrorFallback />}>
 *   <DataComponent />
 * </ErrorBoundary>
 */
export function NetworkErrorFallback({
  error,
  onReset,
  title = 'Connection Error',
  message,
}: ErrorFallbackProps): React.JSX.Element {
  const isOffline = !navigator.onLine;
  
  return (
    <div
      role="alert"
      style={{
        padding: '2rem',
        margin: '2rem',
        border: '2px solid #f59e0b',
        borderRadius: '0.5rem',
        backgroundColor: '#fffbeb',
        textAlign: 'center',
      }}
    >
      <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>
        {isOffline ? '📡' : '🔌'}
      </div>
      
      <h2
        style={{
          margin: '0 0 0.75rem 0',
          fontSize: '1.5rem',
          color: '#b45309',
          fontWeight: 600,
        }}
      >
        {title}
      </h2>
      
      <p style={{ margin: '0 0 1.5rem 0', color: '#78350f', fontSize: '1rem' }}>
        {message || 
          (isOffline 
            ? 'You appear to be offline. Please check your internet connection.'
            : 'Unable to connect to the server. Please try again.')}
      </p>
      
      {process.env.NODE_ENV === 'development' && (
        <p
          style={{
            margin: '0 0 1.5rem 0',
            color: '#92400e',
            fontSize: '0.875rem',
            fontFamily: 'monospace',
          }}
        >
          {error.message}
        </p>
      )}
      
      {onReset && (
        <button
          onClick={onReset}
          disabled={isOffline}
          style={{
            padding: '0.75rem 1.5rem',
            backgroundColor: isOffline ? '#d1d5db' : '#f59e0b',
            color: isOffline ? '#6b7280' : '#fff',
            border: 'none',
            borderRadius: '0.5rem',
            fontWeight: 600,
            cursor: isOffline ? 'not-allowed' : 'pointer',
            fontSize: '1rem',
          }}
        >
          {isOffline ? 'Waiting for connection...' : 'Retry'}
        </button>
      )}
    </div>
  );
}

// ============================================================================
// Not Found Error Fallback
// ============================================================================

/**
 * Not found error fallback
 * For 404/resource not found errors
 * 
 * @example
 * <ErrorBoundary fallback={<NotFoundErrorFallback />}>
 *   <ResourceComponent />
 * </ErrorBoundary>
 */
export function NotFoundErrorFallback({
  title = 'Not Found',
  message = 'The resource you are looking for could not be found.',
}: Omit<ErrorFallbackProps, 'error'>): React.JSX.Element {
  return (
    <div
      role="alert"
      style={{
        padding: '3rem',
        margin: '2rem auto',
        maxWidth: '500px',
        textAlign: 'center',
      }}
    >
      <div style={{ fontSize: '6rem', marginBottom: '1rem' }}>404</div>
      
      <h2
        style={{
          margin: '0 0 1rem 0',
          fontSize: '1.875rem',
          color: '#374151',
          fontWeight: 700,
        }}
      >
        {title}
      </h2>
      
      <p style={{ margin: '0 0 2rem 0', color: '#6b7280', fontSize: '1rem' }}>
        {message}
      </p>
      
      <button
        onClick={() => window.history.back()}
        style={{
          padding: '0.75rem 1.5rem',
          marginRight: '0.75rem',
          backgroundColor: '#fff',
          color: '#374151',
          border: '2px solid #d1d5db',
          borderRadius: '0.5rem',
          fontWeight: 600,
          cursor: 'pointer',
          fontSize: '1rem',
        }}
      >
        Go Back
      </button>
      
      <button
        onClick={() => window.location.href = '/'}
        style={{
          padding: '0.75rem 1.5rem',
          backgroundColor: '#3b82f6',
          color: '#fff',
          border: 'none',
          borderRadius: '0.5rem',
          fontWeight: 600,
          cursor: 'pointer',
          fontSize: '1rem',
        }}
      >
        Go Home
      </button>
    </div>
  );
}
