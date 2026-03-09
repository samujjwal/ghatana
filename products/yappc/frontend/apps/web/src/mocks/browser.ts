import { setupWorker } from 'msw/browser';

import { handlers } from './handlers';

// This configures a Service Worker with the given request handlers.
const worker = setupWorker(...handlers);

/**
 * Start the MSW worker ONLY for E2E testing or when explicitly enabled.
 * 
 * In development mode, we now use real API calls to the backend (port 7001/7003).
 * MSW should only be used for:
 * - E2E tests (VITE_ENABLE_MSW=true)
 * - Storybook (handled separately)
 * 
 * To enable MSW in development for debugging:
 * - Set VITE_ENABLE_MSW=true in .env.development
 */
export async function setupMocks() {
  // Only start MSW if explicitly enabled via environment variable
  const enableMsw = import.meta.env.VITE_ENABLE_MSW === 'true';
  
  if (!enableMsw) {
    console.log('[MSW] Mock service worker disabled - using real API');
    return;
  }

  try {
    await worker.start({
      onUnhandledRequest: 'bypass',
      serviceWorker: {
        url: '/mockServiceWorker.js',
      },
    });
    console.log('[MSW] Mock service worker started');
  } catch (error) {
    console.error('Error starting MSW:', error);
  }
}
