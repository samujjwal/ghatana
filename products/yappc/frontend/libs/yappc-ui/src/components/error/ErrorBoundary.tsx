/**
 * Error Boundary Component
 * 
 * Production-grade error boundary with fallback UI,
 * error reporting, and recovery mechanisms.
 * 
 * @module ui/error
 * @doc.type component
 * @doc.purpose Error boundary for React component trees
 * @doc.layer ui
 */

import { Component, type ErrorInfo, type ReactNode } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Error boundary props
 */
export interface ErrorBoundaryProps {
  /** Child components to protect */
  children: ReactNode;
  
  /** Custom fallback UI component */
  fallback?: ReactNode | ((error: Error, errorInfo: ErrorInfo, retry: () => void) => ReactNode);
  
  /** Error callback for logging/reporting */
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  
  /** Error reset callback */
  onReset?: () => void;
  
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
 * Error boundary state
 */
interface ErrorBoundaryState {
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
}

// ============================================================================
// Error Boundary Component
// ============================================================================

/**
 * Error boundary component
 * Catches errors in child component tree and displays fallback UI
 * 
 * @example
 * <ErrorBoundary
 *   fallback={<ErrorFallback />}
 *   onError={(error) => logError(error)}
 * >
 *   <App />
 * </ErrorBoundary>
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  private resetTimeoutId: NodeJS.Timeout | null = null;
  
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      errorCount: 0,
      lastErrorTime: 0,
    };
  }
  
  /**
   * Derive state from error
   * Called when error is thrown
   */
  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return {
      hasError: true,
      error,
      errorCount: 1,
      lastErrorTime: Date.now(),
    };
  }
  
  /**
   * Component did catch
   * Called after error is caught
   */
  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // Update state with error info
    this.setState(prevState => ({
      errorInfo,
      errorCount: prevState.errorCount + 1,
    }));
    
    // Call error callback if provided
    if (this.props.onError) {
      this.props.onError(error, errorInfo);
    }
    
    // Log error to console in development
    if (process.env.NODE_ENV === 'development') {
      console.error('Error Boundary caught error:', error, errorInfo);
    }
    
    // Set automatic reset timer if specified
    if (this.props.resetAfter && this.props.resetAfter > 0) {
      this.resetTimeoutId = setTimeout(() => {
        this.resetErrorBoundary();
      }, this.props.resetAfter);
    }
  }
  
  /**
   * Component did update
   * Reset error boundary when resetKeys change
   */
  componentDidUpdate(prevProps: ErrorBoundaryProps): void {
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
   * Clear timer
   */
  componentWillUnmount(): void {
    if (this.resetTimeoutId) {
      clearTimeout(this.resetTimeoutId);
    }
  }
  
  /**
   * Reset error boundary
   * Clears error state and allows re-rendering
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
    });
  };
  
  /**
   * Render
   */
  render(): ReactNode {
    const { hasError, error, errorInfo, errorCount } = this.state;
    const { children, fallback, showDetails, boundaryName } = this.props;
    
    if (hasError && error) {
      // Render custom fallback if provided
      if (fallback) {
        if (typeof fallback === 'function') {
          return fallback(error, errorInfo!, this.resetErrorBoundary);
        }
        return fallback;
      }
      
      // Render default fallback
      return (
        <DefaultErrorFallback
          error={error}
          errorInfo={errorInfo}
          errorCount={errorCount}
          showDetails={showDetails ?? process.env.NODE_ENV === 'development'}
          boundaryName={boundaryName}
          onReset={this.resetErrorBoundary}
        />
      );
    }
    
    return children;
  }
}

// ============================================================================
// Default Fallback Component
// ============================================================================

/**
 * Default error fallback props
 */
interface DefaultErrorFallbackProps {
  error: Error;
  errorInfo: ErrorInfo | null;
  errorCount: number;
  showDetails: boolean;
  boundaryName?: string;
  onReset: () => void;
}

/**
 * Default error fallback UI
 */
function DefaultErrorFallback({
  error,
  errorInfo,
  errorCount,
  showDetails,
  boundaryName,
  onReset,
}: DefaultErrorFallbackProps): React.JSX.Element {
  return (
    <div
      role="alert"
      style={{
        padding: '2rem',
        margin: '2rem',
        border: '2px solid #ef4444',
        borderRadius: '0.5rem',
        backgroundColor: '#fef2f2',
        fontFamily: 'system-ui, sans-serif',
      }}
    >
      {/* Header */}
      <div style={{ marginBottom: '1rem' }}>
        <h2
          style={{
            margin: 0,
            fontSize: '1.5rem',
            color: '#dc2626',
            fontWeight: 600,
          }}
        >
          ⚠️ Something went wrong
        </h2>
        {boundaryName && (
          <p style={{ margin: '0.5rem 0 0 0', color: '#7f1d1d', fontSize: '0.875rem' }}>
            Error Boundary: {boundaryName}
          </p>
        )}
      </div>
      
      {/* Error message */}
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
      
      {/* Error details (development only) */}
      {showDetails && (
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
      <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
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
          onMouseEnter={(e) => {
            e.currentTarget.style.backgroundColor = '#b91c1c';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.backgroundColor = '#dc2626';
          }}
        >
          Try Again
        </button>
        
        <button
          onClick={() => window.location.reload()}
          style={{
            padding: '0.5rem 1rem',
            backgroundColor: '#fff',
            color: '#dc2626',
            border: '1px solid #dc2626',
            borderRadius: '0.375rem',
            fontWeight: 500,
            cursor: 'pointer',
            fontSize: '0.875rem',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.backgroundColor = '#fef2f2';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.backgroundColor = '#fff';
          }}
        >
          Reload Page
        </button>
        
        {errorCount > 1 && (
          <span style={{ color: '#7f1d1d', fontSize: '0.875rem' }}>
            Error occurred {errorCount} times
          </span>
        )}
      </div>
    </div>
  );
}
