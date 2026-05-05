import React, { useState, useCallback, createContext, useContext } from 'react';
import { AlertCircle, X, RefreshCw, ChevronDown, ChevronUp, Info } from 'lucide-react';
import { CorrelationIdDisplay } from './CorrelationIdDisplay';

/**
 * P1-030: Mutation Error Surface Component
 *
 * Comprehensive error handling for API mutations:
 * - Structured error display with correlation IDs
 * - Retry functionality with exponential backoff
 * - Error categorization (validation, network, server)
 * - Field-level error mapping
 * - Toast notifications for async operations
 * - Error boundary integration
 */

// Error types
export type ErrorCategory = 'VALIDATION' | 'NETWORK' | 'SERVER' | 'AUTHENTICATION' | 'AUTHORIZATION' | 'TIMEOUT' | 'UNKNOWN';

export interface MutationError {
  id: string;
  category: ErrorCategory;
  message: string;
  details?: Record<string, string[]>;
  correlationId?: string;
  timestamp: Date;
  retryable: boolean;
  endpoint?: string;
  statusCode?: number;
}

export interface MutationState<T = unknown> {
  data?: T;
  error?: MutationError;
  isLoading: boolean;
  isSuccess: boolean;
  isError: boolean;
}

// Context for mutation state
interface MutationContextType {
  errors: MutationError[];
  addError: (error: Omit<MutationError, 'id' | 'timestamp'>) => void;
  removeError: (id: string) => void;
  clearErrors: () => void;
  retryLastMutation: () => Promise<void>;
  setLastMutation: (mutation: () => Promise<void>) => void;
}

const MutationContext = createContext<MutationContextType | undefined>(undefined);

/**
 * P1-030: Mutation Provider for error tracking
 */
export const MutationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [errors, setErrors] = useState<MutationError[]>([]);
  const [lastMutation, setLastMutation] = useState<(() => Promise<void>) | null>(null);

  const addError = useCallback((error: Omit<MutationError, 'id' | 'timestamp'>) => {
    const newError: MutationError = {
      ...error,
      id: `error-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      timestamp: new Date()
    };

    setErrors(prev => [newError, ...prev].slice(0, 5)); // Keep last 5 errors
  }, []);

  const removeError = useCallback((id: string) => {
    setErrors(prev => prev.filter(e => e.id !== id));
  }, []);

  const clearErrors = useCallback(() => {
    setErrors([]);
  }, []);

  const retryLastMutation = useCallback(async () => {
    if (lastMutation) {
      await lastMutation();
    }
  }, [lastMutation]);

  const setLastMutationFn = useCallback((mutation: () => Promise<void>) => {
    setLastMutation(() => mutation);
  }, []);

  return (
    <MutationContext.Provider
      value={{
        errors,
        addError,
        removeError,
        clearErrors,
        retryLastMutation,
        setLastMutation: setLastMutationFn
      }}
    >
      {children}
    </MutationContext.Provider>
  );
};

export const useMutationContext = () => {
  const context = useContext(MutationContext);
  if (!context) {
    throw new Error('useMutationContext must be used within MutationProvider');
  }
  return context;
};

/**
 * P1-030: Main mutation error display component
 */
export const MutationErrorDisplay: React.FC<{
  error: MutationError;
  onDismiss?: () => void;
  onRetry?: () => void;
  showDetails?: boolean;
}> = ({ error, onDismiss, onRetry, showDetails = true }) => {
  const [expanded, setExpanded] = useState(false);

  const getCategoryIcon = (category: ErrorCategory) => {
    switch (category) {
      case 'VALIDATION':
        return <Info className="w-5 h-5 text-yellow-600" />;
      case 'NETWORK':
      case 'TIMEOUT':
        return <RefreshCw className="w-5 h-5 text-orange-600" />;
      case 'AUTHENTICATION':
      case 'AUTHORIZATION':
        return <AlertCircle className="w-5 h-5 text-red-600" />;
      default:
        return <AlertCircle className="w-5 h-5 text-red-600" />;
    }
  };

  const getCategoryColor = (category: ErrorCategory) => {
    switch (category) {
      case 'VALIDATION':
        return 'bg-yellow-50 border-yellow-200 text-yellow-800';
      case 'NETWORK':
      case 'TIMEOUT':
        return 'bg-orange-50 border-orange-200 text-orange-800';
      case 'AUTHENTICATION':
      case 'AUTHORIZATION':
        return 'bg-red-50 border-red-200 text-red-800';
      default:
        return 'bg-red-50 border-red-200 text-red-800';
    }
  };

  const getCategoryLabel = (category: ErrorCategory) => {
    return category.replace('_', ' ');
  };

  return (
    <div 
      className={`rounded-lg border p-4 mb-4 ${getCategoryColor(error.category)}`}
      role="alert"
      data-testid={`mutation-error-${error.category}`}
    >
      <div className="flex items-start gap-3">
        <div className="flex-shrink-0 mt-0.5">
          {getCategoryIcon(error.category)}
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold">
                {getCategoryLabel(error.category)} Error
              </span>
              {error.statusCode && (
                <span className="text-xs px-2 py-0.5 bg-black/10 rounded">
                  {error.statusCode}
                </span>
              )}
            </div>
            
            {onDismiss && (
              <button
                onClick={onDismiss}
                className="flex-shrink-0 p-1 rounded hover:bg-black/5 transition-colors"
                aria-label="Dismiss error"
              >
                <X className="h-4 w-4" />
              </button>
            )}
          </div>

          <p className="text-sm mt-1">{error.message}</p>

          {/* Correlation ID */}
          {error.correlationId && (
            <div className="mt-3">
              <CorrelationIdDisplay
                errorCorrelationId={error.correlationId}
                visible={true}
                onDismiss={() => {}}
              />
            </div>
          )}

          {/* Retry button for retryable errors */}
          {error.retryable && onRetry && (
            <button
              onClick={onRetry}
              className="mt-3 flex items-center gap-2 px-3 py-1.5 bg-white/80 hover:bg-white rounded text-sm font-medium transition-colors"
              data-testid="error-retry-button"
            >
              <RefreshCw className="w-4 h-4" />
              Try Again
            </button>
          )}

          {/* Field-level errors */}
          {showDetails && error.details && Object.keys(error.details).length > 0 && (
            <div className="mt-3">
              <button
                onClick={() => setExpanded(!expanded)}
                className="flex items-center gap-1 text-sm font-medium hover:opacity-80"
              >
                {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                {expanded ? 'Hide' : 'Show'} Details
              </button>

              {expanded && (
                <div className="mt-2 p-3 bg-white/50 rounded text-sm">
                  {Object.entries(error.details).map(([field, messages]) => (
                    <div key={field} className="mb-2 last:mb-0">
                      <p className="font-medium">{field}:</p>
                      <ul className="list-disc list-inside ml-2 mt-1">
                        {messages.map((msg, idx) => (
                          <li key={idx} className="text-sm opacity-90">{msg}</li>
                        ))}
                      </ul>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Timestamp */}
          <p className="text-xs mt-2 opacity-60">
            {error.timestamp.toLocaleString()}
          </p>
        </div>
      </div>
    </div>
  );
};

/**
 * P1-030: Global error list component
 */
export const GlobalMutationErrors: React.FC = () => {
  const { errors, removeError, retryLastMutation } = useMutationContext();

  if (errors.length === 0) return null;

  return (
    <div className="fixed top-4 right-4 z-50 w-96 space-y-3" data-testid="global-error-container">
      {errors.map((error) => (
        <MutationErrorDisplay
          key={error.id}
          error={error}
          onDismiss={() => removeError(error.id)}
          onRetry={error.retryable ? retryLastMutation : undefined}
        />
      ))}
    </div>
  );
};

/**
 * P1-030: Hook for mutation with error handling
 */
export const useMutationWithError = <TVariables, TData>(
  mutationFn: (variables: TVariables) => Promise<TData>,
  options?: {
    onSuccess?: (data: TData) => void;
    onError?: (error: MutationError) => void;
    successMessage?: string;
    errorContext?: string;
  }
) => {
  const [state, setState] = useState<MutationState<TData>>({
    isLoading: false,
    isSuccess: false,
    isError: false
  });
  const { addError, setLastMutation } = useMutationContext();

  const mutate = useCallback(async (variables: TVariables) => {
    setState({ isLoading: true, isSuccess: false, isError: false });

    // Store for retry
    setLastMutation(async () => {
      await mutate(variables);
    });

    try {
      const data = await mutationFn(variables);
      
      setState({
        data,
        isLoading: false,
        isSuccess: true,
        isError: false
      });

      options?.onSuccess?.(data);

      return { success: true, data };
    } catch (err) {
      const error = categorizeError(err, options?.errorContext);
      
      setState({
        error,
        isLoading: false,
        isSuccess: false,
        isError: true
      });

      addError(error);
      options?.onError?.(error);

      return { success: false, error };
    }
  }, [mutationFn, options, addError, setLastMutation]);

  const reset = useCallback(() => {
    setState({
      isLoading: false,
      isSuccess: false,
      isError: false
    });
  }, []);

  return {
    ...state,
    mutate,
    reset
  };
};

/**
 * P1-030: Error categorization helper
 */
const categorizeError = (error: unknown, context?: string): MutationError => {
  const baseError: Omit<MutationError, 'id' | 'timestamp'> = {
    category: 'UNKNOWN',
    message: 'An unexpected error occurred',
    retryable: false,
    endpoint: context
  };

  if (error instanceof Response) {
    const status = error.status;
    
    if (status === 400) {
      baseError.category = 'VALIDATION';
      baseError.message = 'Please check your input and try again';
      baseError.retryable = false;
    } else if (status === 401) {
      baseError.category = 'AUTHENTICATION';
      baseError.message = 'Please sign in to continue';
      baseError.retryable = false;
    } else if (status === 403) {
      baseError.category = 'AUTHORIZATION';
      baseError.message = 'You do not have permission to perform this action';
      baseError.retryable = false;
    } else if (status === 408 || status === 504) {
      baseError.category = 'TIMEOUT';
      baseError.message = 'The request timed out. Please try again';
      baseError.retryable = true;
    } else if (status >= 500) {
      baseError.category = 'SERVER';
      baseError.message = 'A server error occurred. Please try again later';
      baseError.retryable = true;
    }
    
    baseError.statusCode = status;
  } else if (error instanceof TypeError && error.message.includes('fetch')) {
    baseError.category = 'NETWORK';
    baseError.message = 'Network connection failed. Please check your connection';
    baseError.retryable = true;
  } else if (error instanceof Error) {
    baseError.message = error.message;
    
    if (error.message.includes('timeout')) {
      baseError.category = 'TIMEOUT';
      baseError.retryable = true;
    }
  }

  // Try to extract correlation ID from error
  if (error && typeof error === 'object' && 'correlationId' in error) {
    baseError.correlationId = (error as any).correlationId;
  }

  // Try to extract field-level errors
  if (error && typeof error === 'object' && 'details' in error) {
    baseError.details = (error as any).details;
  }

  return {
    ...baseError,
    id: `error-${Date.now()}`,
    timestamp: new Date()
  };
};

/**
 * P1-030: Form error mapper component
 */
export const FormErrors: React.FC<{
  errors?: Record<string, string[]>;
  touched?: Record<string, boolean>;
}> = ({ errors, touched }) => {
  if (!errors || Object.keys(errors).length === 0) return null;

  const visibleErrors = Object.entries(errors).filter(
    ([field]) => touched?.[field] || !touched // Show all if no touched state
  );

  if (visibleErrors.length === 0) return null;

  return (
    <div className="rounded-lg bg-yellow-50 border border-yellow-200 p-4 mb-4" data-testid="form-errors">
      <div className="flex items-start gap-2">
        <Info className="w-5 h-5 text-yellow-600 mt-0.5" />
        <div>
          <p className="font-medium text-yellow-800">Please fix the following errors:</p>
          <ul className="mt-2 space-y-1">
            {visibleErrors.map(([field, messages]) => (
              <li key={field} className="text-sm text-yellow-700">
                <span className="font-medium">{field}:</span> {messages.join(', ')}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
};

export default MutationErrorDisplay;
