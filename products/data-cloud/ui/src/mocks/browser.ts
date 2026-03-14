/**
 * MSW Browser Worker Setup
 *
 * <p>Initializes Mock Service Worker for development in the browser.
 * Import and call {@link startMswBrowser} in your application entry point
 * when running in development mode.
 *
 * @doc.type config
 * @doc.purpose MSW browser service worker setup for development
 * @doc.layer frontend
 */

import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

/**
 * MSW service worker instance configured with all application handlers.
 * Re-exported for advanced handler overrides in individual components.
 */
export const worker = setupWorker(...handlers);

/**
 * Starts the MSW service worker in the browser.
 * No-op (and logs a warning) if called outside a browser context.
 */
export async function startMswBrowser(): Promise<void> {
  await worker.start({
    // Suppress "unhandled request" warnings for static assets and HMR
    onUnhandledRequest(request, print) {
      const url = new URL(request.url);
      // Ignore non-API requests and Vite HMR traffic
      if (!url.pathname.startsWith('/api/')) {
        return;
      }
      print.warning();
    },
    serviceWorker: {
      url: '/mockServiceWorker.js',
    },
  });
}
