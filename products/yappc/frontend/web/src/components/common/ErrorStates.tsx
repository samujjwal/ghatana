/**
 * Error States Component
 *
 * Provides reusable error state components for different error types.
 * Supports retry actions, error details, and user-friendly error messages.
 *
 * @doc.type component
 * @doc.purpose Reusable error state components
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { AlertCircle, AlertTriangle, RefreshCw, WifiOff, Lock, Shield, Clock, Zap } from 'lucide-react';
import { Typography, Button, Box } from '@ghatana/design-system';
import type { UiState } from '../../lib/stateMachine';

interface ErrorStateProps {
  message?: string;
  details?: string;
  onRetry?: () => void;
  onDismiss?: () => void;
  size?: 'sm' | 'md' | 'lg';
  fullScreen?: boolean;
  className?: string;
}

/**
 * Generic error state with retry action
 */
export function ErrorState({ 
  message = 'An error occurred', 
  details, 
  onRetry, 
  onDismiss,
  size = 'md',
  fullScreen = false,
  className = ''
}: ErrorStateProps) {
  const sizeClasses = {
    sm: 'w-8 h-8',
    md: 'w-12 h-12',
    lg: 'w-16 h-16',
  };

  const containerClass = fullScreen
    ? 'fixed inset-0 flex items-center justify-center bg-white dark:bg-gray-900 z-50'
    : '';

  return (
    <div className={`${containerClass} ${className}`}>
      <Box className="flex flex-col items-center justify-center text-center p-6">
        <AlertCircle className={`${sizeClasses[size]} text-red-500 mb-4`} />
        <Typography className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
          {message}
        </Typography>
        {details && (
          <Typography className="text-sm text-gray-600 dark:text-gray-400 mb-4">
            {details}
          </Typography>
        )}
        <div className="flex gap-2">
          {onRetry && (
            <Button
              size="sm"
              startIcon={<RefreshCw className="w-4 h-4" />}
              onClick={onRetry}
            >
              Retry
            </Button>
          )}
          {onDismiss && (
            <Button size="sm" variant="outlined" onClick={onDismiss}>
              Dismiss
            </Button>
          )}
        </div>
      </Box>
    </div>
  );
}

interface PermissionDeniedProps {
  message?: string;
  onContact?: () => void;
  className?: string;
}

/**
 * Permission denied error state
 */
export function PermissionDenied({ 
  message = 'You do not have permission to access this resource',
  onContact,
  className = ''
}: PermissionDeniedProps) {
  return (
    <div className={`flex flex-col items-center justify-center text-center p-6 ${className}`}>
      <Lock className="w-16 h-16 text-orange-500 mb-4" />
      <Typography className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
        Permission Denied
      </Typography>
      <Typography className="text-sm text-gray-600 dark:text-gray-400 mb-4">
        {message}
      </Typography>
      {onContact && (
        <Button size="sm" onClick={onContact}>
          Contact Administrator
        </Button>
      )}
    </div>
  );
}

interface AuthFailureProps {
  message?: string;
  onLogin?: () => void;
  className?: string;
}

/**
 * Authentication failure error state
 */
export function AuthFailure({ 
  message = 'Your session has expired. Please log in again.',
  onLogin,
  className = ''
}: AuthFailureProps) {
  return (
    <div className={`flex flex-col items-center justify-center text-center p-6 ${className}`}>
      <Shield className="w-16 h-16 text-red-500 mb-4" />
      <Typography className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
        Authentication Failed
      </Typography>
      <Typography className="text-sm text-gray-600 dark:text-gray-400 mb-4">
        {message}
      </Typography>
      {onLogin && (
        <Button size="sm" onClick={onLogin}>
          Log In
        </Button>
      )}
    </div>
  );
}

interface TimeoutProps {
  message?: string;
  onRetry?: () => void;
  className?: string;
}

/**
 * Timeout error state
 */
export function Timeout({ 
  message = 'The request timed out. Please try again.',
  onRetry,
  className = ''
}: TimeoutProps) {
  return (
    <div className={`flex flex-col items-center justify-center text-center p-6 ${className}`}>
      <Clock className="w-16 h-16 text-yellow-500 mb-4" />
      <Typography className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
        Request Timeout
      </Typography>
      <Typography className="text-sm text-gray-600 dark:text-gray-400 mb-4">
        {message}
      </Typography>
      {onRetry && (
        <Button size="sm" startIcon={<RefreshCw className="w-4 h-4" />} onClick={onRetry}>
          Retry
        </Button>
      )}
    </div>
  );
}

interface RateLimitProps {
  message?: string;
  retryAfter?: number;
  onRetry?: () => void;
  className?: string;
}

/**
 * Rate limit error state
 */
export function RateLimit({ 
  message = 'Too many requests. Please wait before trying again.',
  retryAfter,
  onRetry,
  className = ''
}: RateLimitProps) {
  return (
    <div className={`flex flex-col items-center justify-center text-center p-6 ${className}`}>
      <Zap className="w-16 h-16 text-orange-500 mb-4" />
      <Typography className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
        Rate Limit Exceeded
      </Typography>
      <Typography className="text-sm text-gray-600 dark:text-gray-400 mb-4">
        {message}
        {retryAfter && ` Please retry in ${retryAfter} seconds.`}
      </Typography>
      {onRetry && (
        <Button size="sm" startIcon={<RefreshCw className="w-4 h-4" />} onClick={onRetry}>
          Retry
        </Button>
      )}
    </div>
  );
}

interface OfflineProps {
  message?: string;
  onRetry?: () => void;
  className?: string;
}

/**
 * Offline error state
 */
export function Offline({ 
  message = 'You are currently offline. Please check your connection.',
  onRetry,
  className = ''
}: OfflineProps) {
  return (
    <div className={`flex flex-col items-center justify-center text-center p-6 ${className}`}>
      <WifiOff className="w-16 h-16 text-gray-500 mb-4" />
      <Typography className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
        You're Offline
      </Typography>
      <Typography className="text-sm text-gray-600 dark:text-gray-400 mb-4">
        {message}
      </Typography>
      {onRetry && (
        <Button size="sm" startIcon={<RefreshCw className="w-4 h-4" />} onClick={onRetry}>
          Retry Connection
        </Button>
      )}
    </div>
  );
}

interface ServerErrorProps {
  message?: string;
  code?: string;
  onRetry?: () => void;
  className?: string;
}

/**
 * Server error state
 */
export function ServerError({ 
  message = 'An unexpected error occurred on the server.',
  code,
  onRetry,
  className = ''
}: ServerErrorProps) {
  return (
    <div className={`flex flex-col items-center justify-center text-center p-6 ${className}`}>
      <AlertTriangle className="w-16 h-16 text-red-500 mb-4" />
      <Typography className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
        Server Error
      </Typography>
      {code && (
        <Typography className="text-sm font-mono text-gray-600 dark:text-gray-400 mb-2">
          Error Code: {code}
        </Typography>
      )}
      <Typography className="text-sm text-gray-600 dark:text-gray-400 mb-4">
        {message}
      </Typography>
      {onRetry && (
        <Button size="sm" startIcon={<RefreshCw className="w-4 h-4" />} onClick={onRetry}>
          Retry
        </Button>
      )}
    </div>
  );
}

interface ValidationErrorProps {
  message?: string;
  field?: string;
  onFix?: () => void;
  className?: string;
}

/**
 * Validation error state
 */
export function ValidationError({ 
  message = 'The provided data is invalid.',
  field,
  onFix,
  className = ''
}: ValidationErrorProps) {
  return (
    <div className={`flex flex-col items-center justify-center text-center p-6 ${className}`}>
      <AlertCircle className="w-16 h-16 text-red-500 mb-4" />
      <Typography className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
        Validation Error
      </Typography>
      {field && (
        <Typography className="text-sm text-gray-600 dark:text-gray-400 mb-2">
          Field: {field}
        </Typography>
      )}
      <Typography className="text-sm text-gray-600 dark:text-gray-400 mb-4">
        {message}
      </Typography>
      {onFix && (
        <Button size="sm" onClick={onFix}>
          Fix Issue
        </Button>
      )}
    </div>
  );
}

interface ErrorBoundaryStateProps {
  state: UiState;
  message?: string;
  details?: string;
  onRetry?: () => void;
  onLogin?: () => void;
  onContact?: () => void;
  className?: string;
}

/**
 * Error boundary state component that renders the appropriate error state based on UI state
 */
export function ErrorBoundaryState({ 
  state, 
  message, 
  details, 
  onRetry, 
  onLogin,
  onContact,
  className 
}: ErrorBoundaryStateProps) {
  switch (state) {
    case 'permission_denied':
      return <PermissionDenied message={message} onContact={onContact} className={className} />;
    case 'auth_failure':
      return <AuthFailure message={message} onLogin={onLogin} className={className} />;
    case 'timeout':
      return <Timeout message={message} onRetry={onRetry} className={className} />;
    case 'rate_limit':
      return <RateLimit message={message} onRetry={onRetry} className={className} />;
    case 'offline':
      return <Offline message={message} onRetry={onRetry} className={className} />;
    case 'validation_error':
      return <ValidationError message={message} details={details} className={className} />;
    case 'server_error':
      return <ServerError message={message} details={details} onRetry={onRetry} className={className} />;
    default:
      return <ErrorState message={message} details={details} onRetry={onRetry} className={className} />;
  }
}
