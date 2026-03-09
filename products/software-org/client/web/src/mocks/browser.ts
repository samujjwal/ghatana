import { setupWorker } from 'msw/browser';

import { handlers } from './handlers';

const worker = setupWorker(...handlers);

// Internal flag to indicate worker readiness to client code / tests.
declare global {
  interface Window {
    __MSW_ACTIVE__?: boolean;
    __MSW_DEBUG__?: {
      initialized: boolean;
      startTime: number;
      error?: string;
      handlers?: number;
    };
  }
}

/**
 * Start MSW mocks in the browser when enabled via env.
 *
 * Controlled by VITE_MOCK_API to allow seamless switching
 * between mocked and real backend.
 */
export async function setupMocks(): Promise<void> {
  // Only run in the browser and when explicitly enabled
  const isBrowser = typeof window !== 'undefined';
  if (!isBrowser) {
    console.log('[MSW] setupMocks: not in browser environment');
    return;
  }

  // initialize debug object
  try {
    if (typeof window !== 'undefined') {
      (window as any).__MSW_DEBUG__ = (window as any).__MSW_DEBUG__ || { initialized: true, startTime: Date.now(), handlers: 0 };
    }
  } catch (e) {
    // ignore
  }
  // Use direct import.meta.env references so Vite includes these variables in the bundle
  const shouldMock = import.meta.env.VITE_USE_MOCKS === 'true' || import.meta.env.VITE_MOCK_API === 'true';

  if (!shouldMock) {
    try {
      if (typeof window !== 'undefined') {
        // Expose why mocks were not started for diagnostics in headless runs
        (window as any).__MSW_SKIPPED__ = {
          reason: 'shouldMock=false',
          env: {
            VITE_USE_MOCKS: import.meta.env.VITE_USE_MOCKS || null,
            VITE_MOCK_API: import.meta.env.VITE_MOCK_API || null,
          },
        };
        console.debug('[MSW] setupMocks skipped (shouldMock=false)', (window as any).__MSW_SKIPPED__);
      }
    } catch (e) {
      // ignore
    }
    return;
  }

  // Expose a small, intentional snapshot of runtime env for diagnostics
  try {
    if (typeof window !== 'undefined') {
      (window as any).__MSW_RUNTIME_ENV__ = {
        VITE_USE_MOCKS: import.meta.env.VITE_USE_MOCKS || null,
        VITE_MOCK_API: import.meta.env.VITE_MOCK_API || null,
        VITE_API_URL: import.meta.env.VITE_API_URL || null,
      };
    }
  } catch (e) {
    // ignore
  }

  // Check service worker support
  console.log('[MSW] Checking service worker support...');

  // Check for secure context first (Service Workers require HTTPS or localhost)
  if (typeof window !== 'undefined' && !window.isSecureContext) {
    console.error(
      '[MSW] ❌ Service Workers require a secure context (HTTPS or localhost).\n' +
      '      Current context is insecure. Please access the app via localhost or enable HTTPS.'
    );
    // Mark as active to prevent app hanging on waitForWorkerReady
    if (typeof window !== 'undefined') {
      window.__MSW_ACTIVE__ = true;
    }
    return;
  }

  if (!navigator.serviceWorker) {
    console.error(
      '[MSW] ❌ Service Workers are not supported in this browser environment.\n' +
      '      This may happen in private browsing mode, embedded webviews, or if disabled by policy.'
    );
    if (typeof window !== 'undefined') {
      window.__MSW_ACTIVE__ = true; // Mark as active to prevent waiting
    }
    return;
  }

  try {
    console.log('[MSW] Starting service worker with handlers:', handlers.length);
    console.log('[MSW] Service worker URL: /mockServiceWorker.js');

    // Start MSW with minimal config first
    // By default quiet=true so per-request MSW logs are suppressed. To force
    // verbose logs set `VITE_MSW_QUIET=false` when starting the dev server.
    const mswQuiet = import.meta.env.VITE_MSW_QUIET === 'false' ? false : true;
    await worker.start({
      quiet: mswQuiet,
      onUnhandledRequest: (request) => {
        console.warn(`[MSW] ⚠️ Unhandled ${request.method} ${request.url}`);
      },
      serviceWorker: {
        url: '/mockServiceWorker.js',
      },
    });

    console.log('[MSW] ✅ Service worker started successfully');

    // Give the service worker a moment to fully initialize and take control
    await new Promise(resolve => setTimeout(resolve, 1000));

    // Wait for the service worker controller to be set (indicates SW is controlling page)
    let attempts = 0;
    while (!navigator.serviceWorker.controller && attempts < 30) {
      await new Promise(resolve => setTimeout(resolve, 100));
      attempts++;
    }

    if (navigator.serviceWorker.controller) {
      console.log('[MSW] ✅ Service worker is now controlling the page');
    } else {
      console.warn('[MSW] ⚠️ Service worker controller not set after waiting');
    }

    // Mark as active
    if (typeof window !== 'undefined') {
      window.__MSW_ACTIVE__ = true;
      console.log('[MSW] ✅ __MSW_ACTIVE__ = true');
    }

  } catch (error) {
    console.error('[MSW] ❌ worker.start() failed with error:', error);
    console.error('[MSW] Error details:', {
      message: (error as Error).message,
      stack: (error as Error).stack,
      name: (error as Error).name,
    });

    // Try to get more diagnostics
    try {
      const registrations = await navigator.serviceWorker.getRegistrations();
      console.log('[MSW] Active SW registrations:', registrations.length);
      registrations.forEach((reg, i) => {
        console.log(`[MSW] Registration ${i}:`, {
          scope: reg.scope,
          active: !!reg.active,
          installing: !!reg.installing,
          waiting: !!reg.waiting,
        });
      });
    } catch (e) {
      console.error('[MSW] Failed to get registrations:', e);
    }

    // Mark as active anyway to prevent infinite waiting - UI will use fallback data
    if (typeof window !== 'undefined') {
      window.__MSW_ACTIVE__ = true;
      console.warn('[MSW] ⚠️ Marked as active despite error - will use fallback data');
    }
  }
}

/**
 * Wait until the MSW worker has been marked active. Resolves immediately if
 * MSW is not enabled or the flag is already set. Useful for tests to wait
 * for the worker to be ready before navigating.
 */
export async function waitForWorkerReady(timeout = 3000): Promise<boolean> {
  if (typeof window === 'undefined') return false;
  // Use direct import.meta.env references so Vite includes these variables in the bundle
  const shouldMock = import.meta.env.VITE_USE_MOCKS === 'true' || import.meta.env.VITE_MOCK_API === 'true';
  // If the test harness forced mocks on, consider that as a positive signal
  // so waiters don't false-negative. The smoke-test sets `window.__RUNNING_SMOKE_TEST__`
  // and `setupMocks()` may set `__MSW_FORCED__` when honoring that marker.
  const forced = typeof window !== 'undefined' && !!(window as any).__MSW_FORCED__;
  if (!shouldMock && !forced) return false;

  if ((window as any).__MSW_ACTIVE__) return true;

  return await new Promise((resolve) => {
    const start = Date.now();
    const interval = setInterval(() => {
      if ((window as any).__MSW_ACTIVE__) {
        clearInterval(interval);
        resolve(true);
      } else if (Date.now() - start > timeout) {
        clearInterval(interval);
        resolve(false);
      }
    }, 100);
  });
}
