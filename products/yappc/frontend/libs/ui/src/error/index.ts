/**
 * Error Boundary Module
 * 
 * Comprehensive error handling framework with boundaries,
 * fallback components, reporting, and hooks.
 * 
 * @module ui/error
 * @doc.type module
 * @doc.purpose Error handling infrastructure
 * @doc.layer ui
 */

// Error Boundary Component
export { ErrorBoundary } from './ErrorBoundary';
export type { ErrorBoundaryProps } from './ErrorBoundary';

// Auth-Aware Error Boundary
export { AuthErrorBoundary, AuthErrorBoundaryWithContext } from './AuthErrorBoundary';
export type { AuthErrorBoundaryProps } from './AuthErrorBoundary';

// Error Fallback Components
export {
  MinimalErrorFallback,
  CardErrorFallback,
  FullPageErrorFallback,
  NetworkErrorFallback,
  NotFoundErrorFallback,
} from './ErrorFallback';
export type {
  ErrorFallbackProps,
  MinimalErrorFallbackProps,
  FullPageErrorFallbackProps,
} from './ErrorFallback';

// Error Reporting
export {
  ErrorSeverity,
  classifyErrorSeverity,
  buildErrorReport,
  ConsoleErrorReporter,
  RemoteErrorReporter,
  CompositeErrorReporter,
  createDefaultErrorReporter,
  setErrorReporter,
  getErrorReporter,
  reportError,
} from './errorReporter';
export type {
  ErrorContext,
  ErrorReport,
  ErrorReporter,
} from './errorReporter';

// Error Hooks
export {
  useErrorHandler,
  useErrorReset,
  useAsyncError,
} from './hooks';

// ============================================================================
// Usage Examples
// ============================================================================

/**
 * @example Basic Error Boundary
 * 
 * import { ErrorBoundary } from '@ghatana/ui';
import { CardErrorFallback } from '@ghatana/yappc-ui';
 * 
 * function App() {
 *   return (
 *     <ErrorBoundary fallback={<CardErrorFallback />}>
 *       <MyComponent />
 *     </ErrorBoundary>
 *   );
 * }
 */

/**
 * @example Error Boundary with Reporting
 * 
 * import { ErrorBoundary } from '@ghatana/ui';
import { reportError } from '@ghatana/yappc-ui';
 * 
 * function App() {
 *   const handleError = (error: Error, errorInfo: ErrorInfo) => {
 *     reportError(error, errorInfo, {
 *       component: 'App',
 *       route: window.location.pathname,
 *     });
 *   };
 *   
 *   return (
 *     <ErrorBoundary onError={handleError}>
 *       <MyComponent />
 *     </ErrorBoundary>
 *   );
 * }
 */

/**
 * @example Programmatic Error Handling
 * 
 * import { useErrorHandler } from '@ghatana/yappc-ui';
 * 
 * function MyComponent() {
 *   const { error, clearError, tryCatchAsync } = useErrorHandler();
 *   
 *   const loadData = async () => {
 *     const result = await tryCatchAsync(async () => {
 *       const response = await fetch('/api/data');
 *       return response.json();
 *     });
 *     
 *     if (result) {
 *       // Handle success
 *     }
 *   };
 *   
 *   if (error) {
 *     return <ErrorDisplay error={error} onDismiss={clearError} />;
 *   }
 *   
 *   return <button onClick={loadData}>Load Data</button>;
 * }
 */

/**
 * @example Custom Error Reporter
 * 
 * import {
  *   setErrorReporter,
  *   RemoteErrorReporter,
  *   ConsoleErrorReporter,
  *   CompositeErrorReporter 
 *,
} from '@ghatana/yappc-ui';
 * 
 * // Configure custom error reporter at app initialization
 * const reporter = new CompositeErrorReporter([
 *   new ConsoleErrorReporter(),
 *   new RemoteErrorReporter('https://api.example.com/errors', 'api-key'),
 * ]);
 * 
 * setErrorReporter(reporter);
 * 
 * // Set global context
 * reporter.setContext({ version: '1.0.0', environment: 'production' });
 * 
 * // Set user when authenticated
 * reporter.setUser('user-123');
 */

/**
 * @example Error Reset with Keys
 * 
 * import { ErrorBoundary } from '@ghatana/ui';
import { useErrorReset } from '@ghatana/yappc-ui';
 * 
 * function ParentComponent() {
 *   const { resetKey, reset } = useErrorReset();
 *   
 *   return (
 *     <>
 *       <button onClick={reset}>Reset</button>
 *       <ErrorBoundary resetKeys={[resetKey]}>
 *         <ChildComponent />
 *       </ErrorBoundary>
 *     </>
 *   );
 * }
 */

/**
 * @example Async Error in Error Boundary
 * 
 * import { useAsyncError } from '@ghatana/yappc-ui';
 * 
 * function MyComponent() {
 *   const throwError = useAsyncError();
 *   
 *   useEffect(() => {
 *     fetchData().catch(throwError);
 *   }, []);
 *   
 *   return <div>My Component</div>;
 * }
 */
