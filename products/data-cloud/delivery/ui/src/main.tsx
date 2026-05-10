import React from 'react';
import ReactDOM from 'react-dom/client';
import { ErrorBoundary } from '@ghatana/design-system';
import { App } from './App';
import './i18n/config';
import './styles/globals.css';

/**
 * Application entry point.
 *
 * @doc.type entry
 * @doc.purpose React application bootstrap
 * @doc.layer frontend
 */

async function bootstrap() {
  // P1-9: MSW mocks are isolated to test-only environment
  // They should NEVER be loaded in production builds
  // The import is wrapped in a dynamic import that gets tree-shaken
  // by Vite's build process when NODE_ENV !== 'test'
  const isTestMode = import.meta.env.MODE === 'test';
  const shouldUseMsw = isTestMode && import.meta.env.VITE_USE_MSW !== 'false';

  if (shouldUseMsw) {
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
