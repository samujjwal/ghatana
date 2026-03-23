import { createLogger } from './utils/logger.js';
const logger = createLogger('main');

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { MinimalThemeProvider } from "./providers/MinimalThemeProvider";
import { App } from "./App";
import "./index.css";

// Simple ErrorBoundary component
class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { hasError: boolean; error?: Error }
> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    logger.error('Error caught by boundary:', { error, errorInfo });
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: '20px', textAlign: 'center' }}>
          <h1>Something went wrong.</h1>
          <p>Please refresh the page and try again.</p>
          <details style={{ marginTop: '20px' }}>
            <summary>Error details</summary>
            <pre style={{ textAlign: 'left', marginTop: '10px' }}>
              {this.state.error?.stack}
            </pre>
          </details>
        </div>
      );
    }

    return this.props.children;
  }
}

// Register service worker for PWA offline support
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', async () => {
    try {
      const registration = await navigator.serviceWorker.register('/sw.js', {
        scope: '/',
      });

      logger.info('Service worker registered:', registration.scope);

      // Handle updates
      registration.addEventListener('updatefound', () => {
        const newWorker = registration.installing;
        if (newWorker) {
          newWorker.addEventListener('statechange', () => {
            if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
              // New content available, notify user
              logger.info('New content available, please refresh.');
            }
          });
        }
      });
    } catch (error) {
      logger.error('Service worker registration failed:', error);
    }
  });
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ErrorBoundary>
      <MinimalThemeProvider storageKey="tutorputor-theme">
        <App />
      </MinimalThemeProvider>
    </ErrorBoundary>
  </StrictMode>
);


