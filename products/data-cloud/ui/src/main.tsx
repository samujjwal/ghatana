import React from 'react';
import ReactDOM from 'react-dom/client';
import { ErrorBoundary } from '@ghatana/design-system';
import { App } from './App';
import './styles/globals.css';

/**
 * Application entry point.
 *
 * @doc.type entry
 * @doc.purpose React application bootstrap
 * @doc.layer frontend
 */

async function bootstrap() {
  // Enable MSW in development so the UI works without a running backend.
  // The worker is tree-shaken out of production builds (`import.meta.env.DEV`
  // is replaced with `false` by Vite at build time).
  if (import.meta.env.DEV) {
    const { startMswBrowser } = await import('./mocks/browser');
    await startMswBrowser();
  }

  const root = document.getElementById('root');

  if (!root) {
    throw new Error('Root element not found');
  }

  ReactDOM.createRoot(root).render(
    <React.StrictMode>
      <ErrorBoundary>
        <App />
      </ErrorBoundary>
    </React.StrictMode>
  );
}

bootstrap();
