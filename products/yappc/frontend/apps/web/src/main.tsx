import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { ErrorBoundary } from './ErrorBoundary';
import { reportFrontendError } from './observability/errorReporter';
import './index.css';

window.addEventListener('error', (event) => {
  reportFrontendError({
    source: 'window-error',
    message: event.message || 'Unhandled window error',
    stack: event.error instanceof Error ? event.error.stack : undefined,
  });
});

window.addEventListener('unhandledrejection', (event) => {
  const reason: unknown = event.reason;

  if (reason instanceof Error) {
    reportFrontendError({
      source: 'unhandled-rejection',
      message: reason.message,
      stack: reason.stack,
    });
    return;
  }

  reportFrontendError({
    source: 'unhandled-rejection',
    message: typeof reason === 'string' ? reason : 'Unhandled promise rejection',
  });
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </React.StrictMode>
);
