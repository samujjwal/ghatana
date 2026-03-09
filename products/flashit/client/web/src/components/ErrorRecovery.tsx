/**
 * Error Recovery Component for Flashit Web
 * Provides user-friendly error recovery options
 *
 * @doc.type component
 * @doc.purpose Error recovery UI with retry and fallback options
 * @doc.layer product
 * @doc.pattern RecoveryComponent
 */

import React, { useState, useCallback, useEffect } from 'react';

// ============================================================================
// Types & Interfaces
// ============================================================================

export type ErrorType =
  | 'network'
  | 'server'
  | 'authentication'
  | 'not_found'
  | 'validation'
  | 'unknown';

export interface ErrorRecoveryProps {
  error: Error;
  type?: ErrorType;
  onRetry?: () => void | Promise<void>;
  onGoBack?: () => void;
  onReportError?: (error: Error) => void;
  showDetails?: boolean;
  retryCount?: number;
  maxRetries?: number;
}

// ============================================================================
// Error Type Detection
// ============================================================================

function detectErrorType(error: Error): ErrorType {
  const message = error.message.toLowerCase();
  
  if (message.includes('network') || message.includes('fetch failed')) {
    return 'network';
  }
  if (message.includes('unauthorized') || message.includes('authentication')) {
    return 'authentication';
  }
  if (message.includes('not found') || message.includes('404')) {
    return 'not_found';
  }
  if (message.includes('validation') || message.includes('invalid')) {
    return 'validation';
  }
  if (message.includes('server') || message.includes('500')) {
    return 'server';
  }
  
  return 'unknown';
}

// ============================================================================
// Error Messages
// ============================================================================

const ERROR_MESSAGES: Record<ErrorType, { title: string; description: string; icon: string }> = {
  network: {
    title: 'Connection Problem',
    description: "We couldn't connect to our servers. Please check your internet connection and try again.",
    icon: '📡',
  },
  server: {
    title: 'Server Error',
    description: "Something went wrong on our end. We're working to fix it. Please try again in a moment.",
    icon: '🔧',
  },
  authentication: {
    title: 'Authentication Required',
    description: 'Your session has expired or you need to log in to access this content.',
    icon: '🔒',
  },
  not_found: {
    title: 'Not Found',
    description: "We couldn't find what you're looking for. It may have been moved or deleted.",
    icon: '🔍',
  },
  validation: {
    title: 'Invalid Input',
    description: 'Some of the information provided is invalid. Please check and try again.',
    icon: '⚠️',
  },
  unknown: {
    title: 'Unexpected Error',
    description: 'Something unexpected happened. Please try again or contact support if the problem persists.',
    icon: '❌',
  },
};

// ============================================================================
// Error Recovery Component
// ============================================================================

/**
 * ErrorRecovery provides user-friendly error handling
 */
export function ErrorRecovery({
  error,
  type: providedType,
  onRetry,
  onGoBack,
  onReportError,
  showDetails = false,
  retryCount = 0,
  maxRetries = 3,
}: ErrorRecoveryProps): JSX.Element {
  const [isRetrying, setIsRetrying] = useState(false);
  const [showStack, setShowStack] = useState(false);
  const [hasReported, setHasReported] = useState(false);

  const errorType = providedType || detectErrorType(error);
  const errorInfo = ERROR_MESSAGES[errorType];
  const canRetry = onRetry && retryCount < maxRetries;

  const handleRetry = useCallback(async () => {
    if (!onRetry || isRetrying) return;

    setIsRetrying(true);
    try {
      await onRetry();
    } catch (err) {
      console.error('Retry failed:', err);
    } finally {
      setIsRetrying(false);
    }
  }, [onRetry, isRetrying]);

  const handleReport = useCallback(() => {
    if (onReportError && !hasReported) {
      onReportError(error);
      setHasReported(true);
    }
  }, [onReportError, error, hasReported]);

  // Auto-retry for network errors
  useEffect(() => {
    if (errorType === 'network' && canRetry && retryCount === 0) {
      const timer = setTimeout(() => {
        handleRetry();
      }, 2000);
      return () => clearTimeout(timer);
    }
  }, [errorType, canRetry, retryCount, handleRetry]);

  return (
    <div className="error-recovery">
      <div className="error-icon">{errorInfo.icon}</div>
      
      <div className="error-content">
        <h2 className="error-title">{errorInfo.title}</h2>
        <p className="error-description">{errorInfo.description}</p>

        {showDetails && (
          <div className="error-details">
            <p className="error-message">{error.message}</p>
            
            {error.stack && (
              <div className="error-stack-container">
                <button
                  onClick={() => setShowStack(!showStack)}
                  className="error-stack-toggle"
                >
                  {showStack ? 'Hide' : 'Show'} Technical Details
                </button>
                
                {showStack && (
                  <pre className="error-stack">{error.stack}</pre>
                )}
              </div>
            )}
          </div>
        )}

        <div className="error-actions">
          {canRetry && (
            <button
              onClick={handleRetry}
              disabled={isRetrying}
              className="btn-primary"
            >
              {isRetrying ? (
                <>
                  <span className="spinner" />
                  Retrying...
                </>
              ) : (
                <>Try Again {retryCount > 0 && `(${retryCount}/${maxRetries})`}</>
              )}
            </button>
          )}

          {onGoBack && (
            <button onClick={onGoBack} className="btn-secondary">
              Go Back
            </button>
          )}

          {onReportError && (
            <button
              onClick={handleReport}
              disabled={hasReported}
              className="btn-tertiary"
            >
              {hasReported ? '✓ Reported' : 'Report Issue'}
            </button>
          )}
        </div>

        {errorType === 'network' && (
          <div className="error-suggestions">
            <h4>Troubleshooting tips:</h4>
            <ul>
              <li>Check your internet connection</li>
              <li>Try refreshing the page</li>
              <li>Disable VPN or proxy if enabled</li>
              <li>Clear your browser cache</li>
            </ul>
          </div>
        )}

        {errorType === 'authentication' && (
          <div className="error-suggestions">
            <a href="/login" className="auth-link">
              → Log in again
            </a>
          </div>
        )}
      </div>

      <style jsx>{`
        .error-recovery {
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          padding: 3rem 1.5rem;
          max-width: 600px;
          margin: 0 auto;
          text-align: center;
        }

        .error-icon {
          font-size: 4rem;
          margin-bottom: 1.5rem;
          animation: bounce 1s ease-in-out;
        }

        @keyframes bounce {
          0%, 100% { transform: translateY(0); }
          50% { transform: translateY(-10px); }
        }

        .error-content {
          width: 100%;
        }

        .error-title {
          font-size: 1.75rem;
          font-weight: 600;
          color: #1f2937;
          margin: 0 0 0.75rem 0;
        }

        .error-description {
          font-size: 1.05rem;
          color: #6b7280;
          line-height: 1.6;
          margin: 0 0 2rem 0;
        }

        .error-details {
          background: #f9fafb;
          border-radius: 8px;
          padding: 1rem;
          margin-bottom: 2rem;
          text-align: left;
        }

        .error-message {
          font-family: 'Courier New', monospace;
          font-size: 0.9rem;
          color: #dc2626;
          margin: 0 0 0.5rem 0;
        }

        .error-stack-container {
          margin-top: 1rem;
        }

        .error-stack-toggle {
          background: none;
          border: none;
          color: #6366f1;
          cursor: pointer;
          font-size: 0.875rem;
          padding: 0;
          text-decoration: underline;
        }

        .error-stack {
          background: #1f2937;
          color: #e5e7eb;
          padding: 1rem;
          border-radius: 4px;
          overflow-x: auto;
          font-size: 0.75rem;
          line-height: 1.4;
          margin-top: 0.5rem;
        }

        .error-actions {
          display: flex;
          flex-wrap: wrap;
          gap: 0.75rem;
          justify-content: center;
          margin-bottom: 2rem;
        }

        .btn-primary, .btn-secondary, .btn-tertiary {
          padding: 0.75rem 1.5rem;
          border-radius: 8px;
          font-size: 1rem;
          font-weight: 500;
          cursor: pointer;
          transition: all 0.2s;
          border: none;
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .btn-primary {
          background: #6366f1;
          color: white;
        }

        .btn-primary:hover:not(:disabled) {
          background: #4f46e5;
          transform: translateY(-1px);
          box-shadow: 0 4px 6px rgba(99, 102, 241, 0.3);
        }

        .btn-primary:disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }

        .btn-secondary {
          background: white;
          color: #6366f1;
          border: 2px solid #6366f1;
        }

        .btn-secondary:hover {
          background: #f5f7ff;
        }

        .btn-tertiary {
          background: transparent;
          color: #6b7280;
          border: 1px solid #d1d5db;
        }

        .btn-tertiary:hover:not(:disabled) {
          background: #f9fafb;
          border-color: #9ca3af;
        }

        .btn-tertiary:disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }

        .spinner {
          display: inline-block;
          width: 1rem;
          height: 1rem;
          border: 2px solid rgba(255, 255, 255, 0.3);
          border-top-color: white;
          border-radius: 50%;
          animation: spin 0.6s linear infinite;
        }

        @keyframes spin {
          to { transform: rotate(360deg); }
        }

        .error-suggestions {
          background: #eff6ff;
          border: 1px solid #dbeafe;
          border-radius: 8px;
          padding: 1rem;
          text-align: left;
        }

        .error-suggestions h4 {
          font-size: 0.95rem;
          font-weight: 600;
          color: #1e40af;
          margin: 0 0 0.75rem 0;
        }

        .error-suggestions ul {
          margin: 0;
          padding-left: 1.25rem;
          color: #3b82f6;
        }

        .error-suggestions li {
          font-size: 0.9rem;
          margin-bottom: 0.25rem;
        }

        .auth-link {
          display: inline-block;
          color: #6366f1;
          text-decoration: none;
          font-weight: 500;
          font-size: 1.05rem;
          padding: 0.5rem 0;
        }

        .auth-link:hover {
          text-decoration: underline;
        }

        @media (max-width: 640px) {
          .error-recovery {
            padding: 2rem 1rem;
          }

          .error-icon {
            font-size: 3rem;
          }

          .error-title {
            font-size: 1.5rem;
          }

          .error-actions {
            flex-direction: column;
            width: 100%;
          }

          .btn-primary, .btn-secondary, .btn-tertiary {
            width: 100%;
            justify-content: center;
          }
        }
      `}</style>
    </div>
  );
}

export default ErrorRecovery;
