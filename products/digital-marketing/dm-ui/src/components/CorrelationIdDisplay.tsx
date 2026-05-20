import React, { useEffect, useState } from 'react';
import { AlertCircle, CheckCircle, Copy, X } from 'lucide-react';

const CORRELATION_DIAGNOSTIC_EVENT = 'dmos:correlation-diagnostic';

interface CorrelationDiagnostic {
  code: string;
  message: string;
}

function recordCorrelationDiagnostic(diagnostic: CorrelationDiagnostic): void {
  if (typeof window === 'undefined') {
    return;
  }

  window.dispatchEvent(new CustomEvent<CorrelationDiagnostic>(CORRELATION_DIAGNOSTIC_EVENT, { detail: diagnostic }));
}

/**
 * P1-051: Correlation ID Display Component
 *
 * Displays correlation IDs for error tracking and support:
 * - Shows current request correlation ID
 * - Displays error correlation IDs
 * - Allows copying for support tickets
 * - Tracks request history
 * - Links to error details
 */

interface CorrelationIdInfo {
  id: string;
  timestamp: Date;
  type: 'request' | 'error' | 'success';
  endpoint?: string;
  statusCode?: number;
  errorMessage?: string;
}

interface CorrelationIdDisplayProps {
  /** Current correlation ID from the most recent request */
  currentCorrelationId?: string;
  /** Error correlation ID when an error occurs */
  errorCorrelationId?: string;
  /** Error details for display */
  errorDetails?: {
    message: string;
    statusCode?: number;
    endpoint?: string;
  };
  /** Whether to show the correlation ID banner */
  visible?: boolean;
  /** Callback when user dismisses the display */
  onDismiss?: () => void;
  /** Callback when user copies the ID */
  onCopy?: (id: string) => void;
}

/**
 * P1-051: Primary component for displaying correlation IDs
 */
export const CorrelationIdDisplay: React.FC<CorrelationIdDisplayProps> = ({
  currentCorrelationId,
  errorCorrelationId,
  errorDetails,
  visible = true,
  onDismiss,
  onCopy
}) => {
  const [copied, setCopied] = useState(false);
  const [history, setHistory] = useState<CorrelationIdInfo[]>([]);
  const [showHistory, setShowHistory] = useState(false);

  // Track correlation IDs in history
  useEffect(() => {
    if (currentCorrelationId) {
      setHistory(prev => {
        const newEntry: CorrelationIdInfo = {
          id: currentCorrelationId,
          timestamp: new Date(),
          type: errorDetails ? 'error' : 'success',
          endpoint: errorDetails?.endpoint,
          statusCode: errorDetails?.statusCode,
          errorMessage: errorDetails?.message
        };

        // Keep only last 10 entries, avoid duplicates
        const filtered = prev.filter(h => h.id !== currentCorrelationId);
        return [newEntry, ...filtered].slice(0, 10);
      });
    }
  }, [currentCorrelationId, errorDetails]);

  const displayId = errorCorrelationId || currentCorrelationId;

  if (!visible || !displayId) {
    return null;
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(displayId);
      setCopied(true);
      onCopy?.(displayId);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      recordCorrelationDiagnostic({
        code: 'DMOS_CORRELATION_ID_COPY_FAILED',
        message: err instanceof Error ? err.message : 'Failed to copy correlation ID',
      });
    }
  };

  const isError = !!errorDetails || !!errorCorrelationId;

  return (
    <div
      data-testid="correlation-id-display"
      className={`rounded-lg border p-4 mb-4 ${
        isError
          ? 'bg-red-50 border-red-200 text-red-800'
          : 'bg-blue-50 border-blue-200 text-blue-800'
      }`}
      role="alert"
      aria-live="polite"
    >
      <div className="flex items-start gap-3">
        <div className="flex-shrink-0 mt-0.5">
          {isError ? (
            <AlertCircle className="h-5 w-5 text-red-600" aria-hidden="true" />
          ) : (
            <CheckCircle className="h-5 w-5 text-blue-600" aria-hidden="true" />
          )}
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2">
            <h3 className="text-sm font-semibold">
              {isError ? 'Error Reference ID' : 'Request Reference ID'}
            </h3>

            <button
              onClick={onDismiss}
              className="flex-shrink-0 p-1 rounded hover:bg-black/5 transition-colors"
              aria-label="Dismiss"
              data-testid="correlation-id-dismiss"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <p className="text-sm mt-1 opacity-90">
            {isError
              ? 'If you contact support about this error, please provide this reference ID:'
              : 'Request processed successfully. Reference ID:'}
          </p>

          <div className="flex items-center gap-2 mt-2">
            <code
              className={`flex-1 px-3 py-2 rounded text-sm font-mono break-all ${
                isError
                  ? 'bg-red-100 text-red-900'
                  : 'bg-blue-100 text-blue-900'
              }`}
              data-testid="correlation-id-value"
            >
              {displayId}
            </code>

            <button
              onClick={handleCopy}
              className={`flex items-center gap-1.5 px-3 py-2 rounded text-sm font-medium transition-colors ${
                copied
                  ? isError
                    ? 'bg-green-100 text-green-700'
                    : 'bg-green-100 text-green-700'
                  : isError
                    ? 'bg-red-200 text-red-700 hover:bg-red-300'
                    : 'bg-blue-200 text-blue-700 hover:bg-blue-300'
              }`}
              aria-label={copied ? 'Copied!' : 'Copy reference ID'}
              data-testid="correlation-id-copy"
            >
              {copied ? (
                <>
                  <CheckCircle className="h-4 w-4" />
                  <span>Copied!</span>
                </>
              ) : (
                <>
                  <Copy className="h-4 w-4" />
                  <span>Copy</span>
                </>
              )}
            </button>
          </div>

          {errorDetails && (
            <div className="mt-3 text-sm">
              <p className="font-medium">Error Details:</p>
              {errorDetails.statusCode && (
                <p className="opacity-80">Status: {errorDetails.statusCode}</p>
              )}
              {errorDetails.endpoint && (
                <p className="opacity-80">Endpoint: {errorDetails.endpoint}</p>
              )}
              <p className="opacity-80 mt-1">{errorDetails.message}</p>
            </div>
          )}

          {/* History toggle */}
          {history.length > 1 && (
            <button
              onClick={() => setShowHistory(!showHistory)}
              className="mt-3 text-sm underline opacity-75 hover:opacity-100"
              data-testid="correlation-history-toggle"
            >
              {showHistory ? 'Hide' : 'Show'} recent request history ({history.length - 1} more)
            </button>
          )}

          {/* History list */}
          {showHistory && history.length > 1 && (
            <div
              className="mt-3 space-y-2"
              data-testid="correlation-history-list"
            >
              <p className="text-sm font-medium">Recent Requests:</p>
              {history.slice(1).map((item, index) => (
                <div
                  key={item.id}
                  className={`text-xs p-2 rounded ${
                    item.type === 'error'
                      ? 'bg-red-100/50 text-red-800'
                      : 'bg-blue-100/50 text-blue-800'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <code className="font-mono">{item.id}</code>
                    <span className="opacity-75">
                      {item.timestamp.toLocaleTimeString()}
                    </span>
                  </div>
                  {item.endpoint && (
                    <p className="opacity-75 mt-1">{item.endpoint}</p>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

/**
 * P1-051: Hook for managing correlation ID state
 */
export const useCorrelationId = () => {
  const [currentId, setCurrentId] = useState<string | undefined>();
  const [errorId, setErrorId] = useState<string | undefined>();
  const [errorDetails, setErrorDetails] = useState<CorrelationIdDisplayProps['errorDetails']>();
  const [visible, setVisible] = useState(false);

  const setCorrelationId = (id: string) => {
    setCurrentId(id);
    setErrorId(undefined);
    setErrorDetails(undefined);
    setVisible(true);

    // Auto-hide success messages after 5 seconds
    setTimeout(() => {
      setVisible(false);
    }, 5000);
  };

  const setError = (
    id: string,
    details: CorrelationIdDisplayProps['errorDetails']
  ) => {
    setErrorId(id);
    setErrorDetails(details);
    setVisible(true);
  };

  const dismiss = () => {
    setVisible(false);
  };

  const clear = () => {
    setCurrentId(undefined);
    setErrorId(undefined);
    setErrorDetails(undefined);
    setVisible(false);
  };

  return {
    currentId,
    errorId,
    errorDetails,
    visible,
    setCorrelationId,
    setError,
    dismiss,
    clear
  };
};

/**
 * P1-051: Axios interceptor for extracting correlation IDs
 */
export const correlationIdInterceptor = {
  response: {
    onFulfilled: (response: any) => {
      const correlationId = response.headers['x-correlation-id'];
      if (correlationId) {
        // Store for display
        localStorage.setItem('last-correlation-id', correlationId);
      }
      return response;
    },
    onRejected: (error: any) => {
      const correlationId = error.response?.headers?.['x-correlation-id'];
      if (correlationId) {
        localStorage.setItem('last-error-correlation-id', correlationId);
        localStorage.setItem('last-error-details', JSON.stringify({
          statusCode: error.response?.status,
          message: error.message,
          endpoint: error.config?.url
        }));
      }
      return Promise.reject(error);
    }
  }
};

/**
 * P1-051: Error boundary that captures and displays correlation IDs
 */
export class CorrelationIdErrorBoundary extends React.Component<
  { children: React.ReactNode; fallback?: React.ReactNode },
  { hasError: boolean; correlationId?: string; error?: Error }
> {
  constructor(props: { children: React.ReactNode; fallback?: React.ReactNode }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error) {
    const correlationId = localStorage.getItem('last-error-correlation-id') ||
      localStorage.getItem('last-correlation-id');
    return { hasError: true, error, correlationId };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    recordCorrelationDiagnostic({
      code: 'DMOS_CORRELATION_BOUNDARY_ERROR',
      message: `${error.message}${errorInfo.componentStack ? ` ${errorInfo.componentStack}` : ''}`,
    });
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="p-8">
          <CorrelationIdDisplay
            errorCorrelationId={this.state.correlationId}
            errorDetails={{
              message: this.state.error?.message || 'Unknown error',
              statusCode: 500
            }}
            visible={true}
            onDismiss={() => this.setState({ hasError: false })}
          />
          <div className="text-center mt-8">
            <h2 className="text-xl font-bold text-gray-900">Something went wrong</h2>
            <p className="text-gray-600 mt-2">
              Please refresh the page or contact support with the reference ID above.
            </p>
            <button
              onClick={() => window.location.reload()}
              className="mt-4 px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
            >
              Refresh Page
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
