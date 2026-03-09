/**
 * React Error Boundary Component
 * 
 * Catches JavaScript errors in component tree and displays fallback UI.
 * Essential for AI components and critical UI components.
 * 
 * @doc.type component
 * @doc.purpose Error boundary for React components
 * @doc.layer product
 * @doc.pattern Error Boundary
 */

import React, { Component, ErrorInfo, ReactNode } from 'react';
import { Box, Typography, Button, Surface as Paper, Alert, AlertTitle } from '@ghatana/ui';
import { RefreshCw as RefreshIcon, Bug as BugReportIcon, Home as HomeIcon, ArrowLeft as ArrowBackIcon } from 'lucide-react';

// ============================================================================
// Types
// ============================================================================

interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode;
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  enableReset?: boolean;
  showDetails?: boolean;
  customMessage?: string;
  variant?: 'default' | 'minimal' | 'detailed';
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
  errorId: string;
}

// ============================================================================
// Error Boundary Component
// ============================================================================

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
      errorId: '',
    };
  }

  static getDerivedStateFromError(error: Error): Partial<ErrorBoundaryState> {
    return {
      hasError: true,
      error,
      errorId: `error-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    this.setState({
      error,
      errorInfo,
    });

    // Log error to console in development
    if (process.env.NODE_ENV === 'development') {
      console.error('ErrorBoundary caught an error:', error, errorInfo);
    }

    // Call custom error handler if provided
    if (this.props.onError) {
      this.props.onError(error, errorInfo);
    }

    // Log to external service in production
    if (process.env.NODE_ENV === 'production') {
      this.logErrorToService(error, errorInfo);
    }
  }

  private logErrorToService = (error: Error, errorInfo: ErrorInfo) => {
    // In production, send error to monitoring service
    const errorData = {
      message: error.message,
      stack: error.stack,
      componentStack: errorInfo.componentStack,
      errorId: this.state.errorId,
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      url: window.location.href,
    };

    // Send to error reporting service (Sentry, LogRocket, etc.)
    fetch('/api/errors', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(errorData),
    }).catch(() => {
      // Fallback to console if service fails
      console.error('Failed to log error to service:', errorData);
    });
  };

  private handleReset = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
      errorId: '',
    });
  };

  private handleReload = () => {
    window.location.reload();
  };

  private handleGoHome = () => {
    window.location.href = '/';
  };

  private handleGoBack = () => {
    window.history.back();
  };

  // ============================================================================
  // Render Methods
  // ============================================================================

  private renderDefaultFallback = () => {
    const { variant = 'default', customMessage, enableReset = true, showDetails = false } = this.props;
    const { error, errorInfo, errorId } = this.state;

    if (variant === 'minimal') {
      return (
        <Box
          display="flex"
          flexDirection="column"
          alignItems="center"
          justifyContent="center"
          minHeight="200px"
          p={3}
          textAlign="center"
        >
          <BugReportIcon className="mb-4 text-5xl text-red-600" />
          <Typography as="h6" gutterBottom>
            Something went wrong
          </Typography>
          <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
            {customMessage || 'An unexpected error occurred. Please try again.'}
          </Typography>
          {enableReset && (
            <Button
              variant="solid"
              startIcon={<RefreshIcon />}
              onClick={this.handleReset}
            >
              Try Again
            </Button>
          )}
        </Box>
      );
    }

    if (variant === 'detailed') {
      return (
        <Paper elevation={3} className="p-6 m-4">
          <Alert severity="error" className="mb-4">
            <AlertTitle>Error Occurred</AlertTitle>
            {customMessage || 'An unexpected error occurred while rendering this component.'}
          </Alert>

          <Typography as="h6" gutterBottom>
            Error Details
          </Typography>

          <Box className="mb-4">
            <Typography as="p" className="text-sm" color="text.secondary">
              Error ID: <code>{errorId}</code>
            </Typography>
            <Typography as="p" className="text-sm" color="text.secondary">
              Time: {new Date().toLocaleString()}
            </Typography>
          </Box>

          {showDetails && error && (
            <Box className="mb-4">
              <Typography as="p" className="text-sm font-medium" gutterBottom>
                Error Message:
              </Typography>
              <Paper className="p-4 bg-gray-100 dark:bg-gray-800">
                <Typography as="p" className="text-sm" component="pre" className="text-sm">
                  {error.message}
                </Typography>
              </Paper>

              {error.stack && (
                <>
                  <Typography as="p" className="text-sm font-medium" gutterBottom className="mt-4">
                    Stack Trace:
                  </Typography>
                  <Paper className="p-4 overflow-auto bg-gray-100 dark:bg-gray-800 max-h-[200px]">
                    <Typography as="p" className="text-sm" component="pre" className="text-xs">
                      {error.stack}
                    </Typography>
                  </Paper>
                </>
              )}

              {errorInfo?.componentStack && (
                <>
                  <Typography as="p" className="text-sm font-medium" gutterBottom className="mt-4">
                    Component Stack:
                  </Typography>
                  <Paper className="p-4 overflow-auto bg-gray-100 dark:bg-gray-800 max-h-[200px]">
                    <Typography as="p" className="text-sm" component="pre" className="text-xs">
                      {errorInfo.componentStack}
                    </Typography>
                  </Paper>
                </>
              )}
            </Box>
          )}

          <Box display="flex" gap={2} flexWrap="wrap">
            {enableReset && (
              <Button
                variant="solid"
                startIcon={<RefreshIcon />}
                onClick={this.handleReset}
              >
                Reset Component
              </Button>
            )}
            <Button
              variant="outlined"
              startIcon={<ArrowBackIcon />}
              onClick={this.handleGoBack}
            >
              Go Back
            </Button>
            <Button
              variant="outlined"
              startIcon={<HomeIcon />}
              onClick={this.handleGoHome}
            >
              Go Home
            </Button>
            <Button
              variant="outlined"
              tone="danger"
              startIcon={<RefreshIcon />}
              onClick={this.handleReload}
            >
              Reload Page
            </Button>
          </Box>
        </Paper>
      );
    }

    // Default variant
    return (
      <Box
        display="flex"
        flexDirection="column"
        alignItems="center"
        justifyContent="center"
        minHeight="400px"
        p={3}
        textAlign="center"
      >
        <BugReportIcon className="mb-6 text-[64px] text-red-600" />
        <Typography as="h4" gutterBottom>
          Oops! Something went wrong
        </Typography>
        <Typography as="p" color="text.secondary" className="mb-6 max-w-[600px]">
          {customMessage || 
            'We encountered an unexpected error. Our team has been notified and is working to fix this issue.'}
        </Typography>
        
        <Box display="flex" gap={2} flexWrap="wrap" justifyContent="center">
          {enableReset && (
            <Button
              variant="solid"
              size="lg"
              startIcon={<RefreshIcon />}
              onClick={this.handleReset}
            >
              Try Again
            </Button>
          )}
          <Button
            variant="outlined"
            size="lg"
            startIcon={<HomeIcon />}
            onClick={this.handleGoHome}
          >
            Go to Homepage
          </Button>
        </Box>

        {process.env.NODE_ENV === 'development' && (
          <Box className="mt-8 text-left max-w-[800px]">
            <Typography as="h6" gutterBottom>
              Development Details
            </Typography>
            <Paper className="p-4 bg-gray-100 dark:bg-gray-800">
              <Typography as="p" className="text-sm" component="pre" className="text-sm">
                Error ID: {errorId}
                {error && `\n\nError: ${error.message}`}
                {errorInfo && `\n\nComponent Stack:\n${errorInfo.componentStack}`}
              </Typography>
            </Paper>
          </Box>
        )}
      </Box>
    );
  };

  render() {
    if (this.state.hasError) {
      // Use custom fallback if provided
      if (this.props.fallback) {
        return this.props.fallback;
      }

      // Use default fallback
      return this.renderDefaultFallback();
    }

    return this.props.children;
  }
}

// ============================================================================
// Specialized Error Boundaries
// ============================================================================

/**
 * AI Component Error Boundary
 * 
 * Specialized error boundary for AI-powered components
 * with AI-specific error messages and recovery options.
 */
export const AIErrorBoundary: React.FC<{
  children: ReactNode;
  fallback?: ReactNode;
  operation?: string;
}> = ({ children, fallback, operation = 'AI operation' }) => {
  return (
    <ErrorBoundary
      fallback={fallback}
      customMessage={`The ${operation} encountered an error. This might be due to high demand or a temporary issue with our AI services.`}
      variant="minimal"
      enableReset={true}
      onError={(error, errorInfo) => {
        // Log AI-specific errors
        console.error(`AI Error in ${operation}:`, error, errorInfo);
        
        // Track AI errors for analytics
        if (typeof gtag !== 'undefined') {
          gtag('event', 'ai_error', {
            operation,
            error_message: error.message,
          });
        }
      }}
    >
      {children}
    </ErrorBoundary>
  );
};

/**
 * Canvas Error Boundary
 * 
 * Specialized error boundary for canvas components
 * with canvas-specific error recovery.
 */
export const CanvasErrorBoundary: React.FC<{
  children: ReactNode;
  fallback?: ReactNode;
  canvasMode?: string;
}> = ({ children, fallback, canvasMode }) => {
  return (
    <ErrorBoundary
      fallback={fallback}
      customMessage={`The canvas encountered an error${canvasMode ? ` in ${canvasMode} mode` : ''}. Your work has been saved automatically.`}
      variant="minimal"
      enableReset={true}
      onError={(error, errorInfo) => {
        console.error(`Canvas Error${canvasMode ? ` (${canvasMode})` : ''}:`, error, errorInfo);
      }}
    >
      {children}
    </ErrorBoundary>
  );
};

/**
 * Async Error Boundary
 * 
 * Error boundary specifically for async operations
 * with loading state handling.
 */
export const AsyncErrorBoundary: React.FC<{
  children: ReactNode;
  fallback?: ReactNode;
  operation?: string;
}> = ({ children, fallback, operation = 'operation' }) => {
  return (
    <ErrorBoundary
      fallback={fallback}
      customMessage={`The ${operation} failed to complete. Please check your connection and try again.`}
      variant="minimal"
      enableReset={true}
      showDetails={false}
    >
      {children}
    </ErrorBoundary>
  );
};

// ============================================================================
// Hook for Error Boundary
// ============================================================================

/**
 * Hook to programmatically trigger error boundaries
 */
export const useErrorBoundary = () => {
  const triggerError = (error: Error) => {
    // This will be caught by the nearest error boundary
    throw error;
  };

  const reportError = (error: Error, context?: string) => {
    console.error(`Error reported${context ? ` in ${context}` : ''}:`, error);
    
    // Send to error reporting service
    if (process.env.NODE_ENV === 'production') {
      fetch('/api/errors', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: error.message,
          stack: error.stack,
          context,
          timestamp: new Date().toISOString(),
        }),
      }).catch(() => {
        // Fallback to console
      });
    }
  };

  return { triggerError, reportError };
};
