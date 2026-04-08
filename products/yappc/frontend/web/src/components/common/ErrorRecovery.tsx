/**
 * Error Recovery Component
 *
 * Displays error information with recovery actions.
 * Provides graceful degradation UI for various error scenarios.
 *
 * @doc.type component
 * @doc.purpose Error recovery UI with graceful degradation
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useState } from 'react';
import { 
  AlertTriangle as ErrorIcon,
  RefreshCw as RetryIcon,
  WifiOff as OfflineIcon,
  X as CloseIcon,
  CheckCircle as SuccessIcon,
  AlertCircle as WarningIcon,
  Info as InfoIcon,
} from 'lucide-react';
import { Typography, Button, Box, Card, CardContent, CardActions, Alert } from '@ghatana/design-system';
import type { ErrorInfo, ErrorCategory, ErrorSeverity } from '../../hooks/useErrorRecovery';

// ============================================================================
// Types
// ============================================================================

export interface ErrorRecoveryProps {
  errorInfo: ErrorInfo;
  onRetry?: () => void;
  onDismiss?: () => void;
  isRetrying?: boolean;
  retryCount?: number;
  hasPendingOperations?: boolean;
  className?: string;
}

// ============================================================================
// Error Icon Component
// ============================================================================

interface ErrorIconComponentProps {
  category: ErrorCategory;
  severity: ErrorSeverity;
}

function ErrorIconComponent({ category, severity }: ErrorIconComponentProps): ReactNode {
  const getIcon = () => {
    if (category === 'offline') return <OfflineIcon className="w-6 h-6" />;
    if (severity === 'critical') return <ErrorIcon className="w-6 h-6" />;
    if (severity === 'high') return <WarningIcon className="w-6 h-6" />;
    if (severity === 'medium') return <InfoIcon className="w-6 h-6" />;
    return <SuccessIcon className="w-6 h-6" />;
  };

  const getColor = () => {
    if (category === 'offline') return 'text-orange-600 dark:text-orange-400';
    if (severity === 'critical') return 'text-red-600 dark:text-red-400';
    if (severity === 'high') return 'text-orange-600 dark:text-orange-400';
    if (severity === 'medium') return 'text-blue-600 dark:text-blue-400';
    return 'text-green-600 dark:text-green-400';
  };

  return (
    <div className={`${getColor()} p-2 bg-gray-100 dark:bg-gray-800 rounded-full`}>
      {getIcon()}
    </div>
  );
}

// ============================================================================
// Error Recovery Component
// ============================================================================

/**
 * Error Recovery Component
 */
export function ErrorRecovery({
  errorInfo,
  onRetry,
  onDismiss,
  isRetrying = false,
  retryCount = 0,
  hasPendingOperations = false,
  className = '',
}: ErrorRecoveryProps): ReactNode {
  const [showDetails, setShowDetails] = useState(false);

  const getCategoryColor = (category: ErrorCategory): string => {
    switch (category) {
      case 'offline':
        return 'border-orange-300 dark:border-orange-700 bg-orange-50 dark:bg-orange-900/20';
      case 'network':
        return 'border-blue-300 dark:border-blue-700 bg-blue-50 dark:bg-blue-900/20';
      case 'authentication':
      case 'authorization':
        return 'border-red-300 dark:border-red-700 bg-red-50 dark:bg-red-900/20';
      case 'server':
        return 'border-red-300 dark:border-red-700 bg-red-50 dark:bg-red-900/20';
      default:
        return 'border-gray-300 dark:border-gray-700 bg-gray-50 dark:bg-gray-800';
    }
  };

  const getSeverityLabel = (severity: ErrorSeverity): string => {
    switch (severity) {
      case 'critical':
        return 'Critical Error';
      case 'high':
        return 'Error';
      case 'medium':
        return 'Warning';
      case 'low':
        return 'Info';
    }
  };

  return (
    <Card className={`border-2 ${getCategoryColor(errorInfo.category)} ${className}`}>
      <CardContent className="p-4">
        {/* Header */}
        <div className="flex items-start gap-3 mb-3">
          <ErrorIconComponent category={errorInfo.category} severity={errorInfo.severity} />
          <div className="flex-1">
            <div className="flex items-center justify-between">
              <Typography className="font-semibold text-sm">
                {getSeverityLabel(errorInfo.severity)}
              </Typography>
              {onDismiss && (
                <Button
                  size="sm"
                  variant="text"
                  onClick={onDismiss}
                  className="text-gray-400 hover:text-gray-600"
                >
                  <CloseIcon className="w-4 h-4" />
                </Button>
              )}
            </div>
            <Typography className="text-sm text-gray-700 dark:text-gray-300 mt-1">
              {errorInfo.message}
            </Typography>
          </div>
        </div>

        {/* Suggested Action */}
        {errorInfo.suggestedAction && (
          <Alert severity="info" className="mb-3">
            <Typography className="text-sm">{errorInfo.suggestedAction}</Typography>
          </Alert>
        )}

        {/* Error Details */}
        <div className="space-y-2 mb-3">
          <div className="flex items-center gap-2 text-xs text-gray-500">
            <span>Category: {errorInfo.category}</span>
            <span>•</span>
            <span>Time: {new Date(errorInfo.timestamp).toLocaleTimeString()}</span>
          </div>
          {retryCount > 0 && (
            <div className="text-xs text-gray-500">
              Retry attempt {retryCount}
            </div>
          )}
          {hasPendingOperations && (
            <Alert severity="warning" className="text-xs">
              You have pending operations that will sync when you're back online.
            </Alert>
          )}
        </div>

        {/* Actions */}
        <CardActions className="flex gap-2 p-0">
          {errorInfo.retryable && onRetry && (
            <Button
              size="sm"
              onClick={onRetry}
              disabled={isRetrying}
              className="flex items-center gap-2"
            >
              <RetryIcon className={`w-4 h-4 ${isRetrying ? 'animate-spin' : ''}`} />
              {isRetrying ? 'Retrying...' : 'Retry'}
            </Button>
          )}
          {errorInfo.category === 'authentication' && (
            <Button size="sm" variant="outlined">
              Log In
            </Button>
          )}
        </CardActions>
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Offline Banner Component
// ============================================================================

export interface OfflineBannerProps {
  hasPendingOperations?: number;
  onSync?: () => void;
  isSyncing?: boolean;
  className?: string;
}

/**
 * Offline Banner Component
 */
export function OfflineBanner({
  hasPendingOperations = 0,
  onSync,
  isSyncing = false,
  className = '',
}: OfflineBannerProps): ReactNode {
  return (
    <Alert severity="warning" className={`border-2 border-orange-300 dark:border-orange-700 ${className}`}>
      <div className="flex items-center gap-3">
        <OfflineIcon className="w-5 h-5 text-orange-600 dark:text-orange-400" />
        <div className="flex-1">
          <Typography className="font-medium text-sm">
            You're offline
          </Typography>
          {hasPendingOperations > 0 && (
            <Typography className="text-xs text-gray-600 dark:text-gray-400">
              {hasPendingOperations} operation{hasPendingOperations !== 1 ? 's' : ''} pending
            </Typography>
          )}
        </div>
        {hasPendingOperations > 0 && onSync && (
          <Button
            size="sm"
            onClick={onSync}
            disabled={isSyncing}
            className="text-xs"
          >
            {isSyncing ? 'Syncing...' : 'Sync Now'}
          </Button>
        )}
      </div>
    </Alert>
  );
}

// ============================================================================
// Error Boundary Component
// ============================================================================

export interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode;
  onError?: (error: Error, errorInfo: React.ErrorInfo) => void;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

/**
 * Error Boundary Component
 */
export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo): void {
    this.props.onError?.(error, errorInfo);
    console.error('Error caught by boundary:', error, errorInfo);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <Card className="border-2 border-red-300 dark:border-red-700 bg-red-50 dark:bg-red-900/20 m-4">
          <CardContent className="p-6">
            <div className="flex items-center gap-3 mb-4">
              <ErrorIcon className="w-6 h-6 text-red-600 dark:text-red-400" />
              <Typography className="font-semibold">Something went wrong</Typography>
            </div>
            <Typography className="text-sm text-gray-700 dark:text-gray-300 mb-4">
              {this.state.error?.message || 'An unexpected error occurred'}
            </Typography>
            <Button
              size="sm"
              onClick={() => window.location.reload()}
            >
              Reload Page
            </Button>
          </CardContent>
        </Card>
      );
    }

    return this.props.children;
  }
}
