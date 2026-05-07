/**
 * RouteErrorBoundary component.
 *
 * @doc.type component
 * @doc.purpose Displays a formatted error page for React Router route-level errors including 404 and server errors
 * @doc.layer product
 * @doc.pattern Error Boundary
 */
import React from 'react';
import { useRouteError, isRouteErrorResponse } from 'react-router';

export function RouteErrorBoundary(): React.ReactElement {
  const error = useRouteError();
  const isDev = import.meta.env.DEV;

  let title = "Something went wrong";
  let message = "An unexpected error occurred while loading this page.";
  let details = "";

  if (isRouteErrorResponse(error)) {
    title = `${error.status} ${error.statusText}`;
    message = error.data || message;
  } else if (error instanceof Error) {
    message = error.message;
    details = error.stack || "";
  }

  return (
    <div role="alert" className="flex items-center justify-center min-h-screen bg-gray-50 dark:bg-gray-900 p-6 flex-col">
      <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">{title}</h1>
      <p className="text-gray-600 dark:text-gray-400 mb-6 max-w-lg text-center">{message}</p>
      
      {isDev && details && (
        <details className="mb-8 w-full max-w-2xl text-left bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 rounded-lg p-3">
          <summary className="text-sm font-medium text-red-700 dark:text-red-300 cursor-pointer mb-2">
            Developer details
          </summary>
          <pre className="text-xs text-red-600 dark:text-red-400 overflow-auto max-h-48 whitespace-pre-wrap">
            {details}
          </pre>
        </details>
      )}

      <button
        onClick={() => window.location.href = '/'}
        className="px-6 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-medium transition-colors"
      >
        Go to Homepage
      </button>
    </div>
  );
}
