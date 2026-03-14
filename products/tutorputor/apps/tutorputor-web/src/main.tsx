import { createLogger } from '../utils/logger.js';
const logger = createLogger('main');

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ErrorBoundary } from '@ghatana/design-system';
import { MinimalThemeProvider } from "./providers/MinimalThemeProvider";
import { App } from "./App";
import "./index.css";

// Register service worker for PWA offline support
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  window.addEventListener('load', async () => {
    try {
      const registration = await navigator.serviceWorker.register('/sw.js', {
        scope: '/',
      });

      logger.info({}, 'Service worker registered:', registration.scope);

      // Handle updates
      registration.addEventListener('updatefound', () => {
        const newWorker = registration.installing;
        if (newWorker) {
          newWorker.addEventListener('statechange', () => {
            if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
              // New content available, notify user
              logger.info({}, 'New content available, please refresh.');
            }
          });
        }
      });
    } catch (error) {
      logger.error({}, 'Service worker registration failed:', error);
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


