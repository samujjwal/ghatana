import { Page } from '@playwright/test';

/**
 * E2E Test Isolation Utilities
 *
 * Provides utilities to ensure clean state between tests by:
 * - Clearing localStorage and sessionStorage
 * - Resetting Jotai atoms
 * - Clearing MSW handlers
 * - Resetting canvas state
 */

// Type declarations for test globals
declare global {
  interface Window {
    __JOTAI_STORE__?: Map<unknown, unknown>;
    __TEST_MOCKS__?: Map<string, unknown>;
    __RF_INSTANCE__?: {
      setNodes: (nodes: unknown[]) => void;
      setEdges: (edges: unknown[]) => void;
      fitView: () => void;
    };
    [key: string]: unknown;
  }
}

export interface TestIsolationOptions {
  clearStorage?: boolean;
  resetAtoms?: boolean;
  clearMSW?: boolean;
  resetCanvas?: boolean;
  seedData?: boolean;
}

const DEFAULT_OPTIONS: TestIsolationOptions = {
  clearStorage: true,
  resetAtoms: true,
  clearMSW: true,
  resetCanvas: true,
  seedData: false,
};

/**
 * Clean test state including browser storage, Jotai atoms, and MSW handlers
 */
export async function cleanTestState(page: Page): Promise<void> {
  // Reset MSW handlers first (need page context)
  await clearMSWHandlers(page);

  // Clear browser storage (will skip if on blank page)
  await clearBrowserStorage(page);

  // Clear any pending timers or intervals (only if on a real page)
  try {
    const url = page.url();
    if (url !== 'about:blank' && !url.startsWith('data:')) {
      await page.evaluate(() => {
        // Clear any timers that might be running
        const highestId = setTimeout(() => {}, 0) as unknown;
        const highestIdNum = parseInt(String(highestId), 10);
        if (!isNaN(highestIdNum)) {
          for (let i = 0; i < highestIdNum; i++) {
            clearTimeout(i);
            clearInterval(i);
          }
        }
      });
    }
  } catch (error) {
    console.warn('Failed to clear timers:', error);
  }
}

/**
 * Clears browser storage (localStorage, sessionStorage, indexedDB)
 * Only clears storage if we're on a page that supports it (not about:blank)
 */
async function clearBrowserStorage(page: Page): Promise<void> {
  try {
    // Check if we're on a page that supports storage
    const url = page.url();
    if (url === 'about:blank' || url.startsWith('data:')) {
      return; // Skip storage clearing for blank/data pages
    }

    await page.evaluate(() => {
      // Clear localStorage
      if (typeof window !== 'undefined' && window.localStorage) {
        try {
          window.localStorage.clear();
        } catch (e) {
          // Ignore SecurityError for pages that don't support localStorage
        }
      }

      // Clear sessionStorage
      if (typeof window !== 'undefined' && window.sessionStorage) {
        try {
          window.sessionStorage.clear();
        } catch (e) {
          // Ignore SecurityError for pages that don't support sessionStorage
        }
      }

      // Clear IndexedDB if available
      if (typeof window !== 'undefined' && window.indexedDB) {
        try {
          // Clear common IndexedDB databases
          const dbNames = ['canvas-app', 'react-flow', 'app-data'];
          dbNames.forEach(async (dbName) => {
            try {
              window.indexedDB.deleteDatabase(dbName);
            } catch (e) {
              // Ignore errors for non-existent databases
            }
          });
        } catch (e) {
          // IndexedDB operations may fail in some contexts
        }
      }
    });
  } catch (error) {
    console.warn('Failed to clear browser storage:', error);
  }
}

/**
 * Reset Jotai atoms to their initial state
 */
export async function resetJotaiAtoms(page: Page): Promise<void> {
  await page.evaluate(() => {
    // Reset Jotai store if available
    if (window.__JOTAI_STORE__) {
      window.__JOTAI_STORE__.clear();
    }

    // Clear any canvas-specific atoms
    const atomKeys = [
      'canvasAtom',
      'cameraAtom',
      'selectionAtom',
      'historyAtom',
      'sketchAtom',
      'collaborationAtom',
    ];

    atomKeys.forEach((key) => {
      if (window[key]) {
        delete window[key];
      }
    });
  });
}

/**
 * Clear MSW request handlers and reset mocks
 */
export async function clearMSWHandlers(page: Page): Promise<void> {
  await page.evaluate(() => {
    // Reset MSW handlers if available
    if (
      window.msw &&
      window.msw.worker &&
      'resetHandlers' in window.msw.worker
    ) {
      (window.msw.worker as unknown).resetHandlers();
    }

    // Clear any mock data
    if (window.__TEST_MOCKS__) {
      window.__TEST_MOCKS__.clear();
    }
  });
}

/**
 * Reset canvas-specific state
 */
export async function resetCanvasState(page: Page): Promise<void> {
  await page.evaluate(() => {
    // Clear canvas-specific localStorage keys
    const canvasKeys = [
      'canvas-state',
      'canvas-history',
      'canvas-selection',
      'canvas-viewport',
      'sketch-state',
      'canvas-poc-v1.0.0',
      'canvas-phase1-state',
      'canvas-phase2-state',
    ];

    canvasKeys.forEach((key) => {
      localStorage.removeItem(key);
      sessionStorage.removeItem(key);
    });

    // Reset React Flow instance if available
    if (window.__RF_INSTANCE__) {
      window.__RF_INSTANCE__.setNodes([]);
      window.__RF_INSTANCE__.setEdges([]);
      window.__RF_INSTANCE__.fitView();
    }
  });
}

/**
 * Seed deterministic test data for canvas tests
 */
export async function seedCanvasTestData(
  page: Page,
  scenario: string = 'default'
): Promise<void> {
  const seedData = getTestSeedData(scenario);

  await page.evaluate((data) => {
    // Set localStorage with seed data
    Object.entries(data.localStorage || {}).forEach(([key, value]) => {
      localStorage.setItem(key, JSON.stringify(value));
    });

    // Set sessionStorage with seed data
    Object.entries(data.sessionStorage || {}).forEach(([key, value]) => {
      sessionStorage.setItem(key, JSON.stringify(value));
    });

    // Set global test data if needed
    if (data.globals) {
      Object.assign(window, data.globals);
    }
  }, seedData);
}

/**
 * Get seed data for different test scenarios
 */
function getTestSeedData(scenario: string) {
  const baseData = {
    localStorage: {},
    sessionStorage: {},
    globals: {},
  };

  switch (scenario) {
    case 'canvas-basic':
      return {
        ...baseData,
        localStorage: {
          'canvas-state': {
            elements: [
              {
                id: 'test-node-1',
                kind: 'node',
                type: 'component',
                position: { x: 300, y: 200 },
                size: { width: 150, height: 80 },
                data: { label: 'Test Frontend' },
                style: {},
              },
            ],
            connections: [],
            sketches: [],
          },
        },
      };

    case 'canvas-with-connections':
      return {
        ...baseData,
        localStorage: {
          'canvas-state': {
            elements: [
              {
                id: 'test-node-1',
                kind: 'node',
                type: 'component',
                position: { x: 300, y: 200 },
                size: { width: 150, height: 80 },
                data: { label: 'Frontend' },
                style: {},
              },
              {
                id: 'test-node-2',
                kind: 'node',
                type: 'api',
                position: { x: 600, y: 200 },
                size: { width: 150, height: 80 },
                data: { label: 'Backend' },
                style: {},
              },
            ],
            connections: [
              {
                id: 'test-edge-1',
                source: 'test-node-1',
                target: 'test-node-2',
                sourceHandle: 'right',
                targetHandle: 'left',
              },
            ],
            sketches: [],
          },
        },
      };

    case 'version-control':
      return {
        ...baseData,
        localStorage: {
          'canvas-snapshots': [
            {
              id: 'snapshot-1',
              name: 'Initial Layout',
              timestamp: new Date(Date.now() - 3600000).toISOString(),
              state: {
                elements: [],
                connections: [],
                sketches: [],
              },
            },
          ],
          'canvas-state': {
            elements: [
              {
                id: 'test-node-1',
                kind: 'node',
                type: 'component',
                position: { x: 400, y: 300 },
                size: { width: 150, height: 80 },
                data: { label: 'Modified Component' },
                style: {},
              },
            ],
            connections: [],
            sketches: [],
          },
        },
      };

    default:
      return baseData;
  }
}

/**
 * Wait for canvas to be fully loaded and ready
 */
export async function waitForCanvasReady(
  page: Page,
  timeout: number = 10000
): Promise<void> {
  // Diagnostic: log initial DOM presence flags
  try {
    await page.evaluate(() => {
      // eslint-disable-next-line no-console
      console.debug('[E2E] DOM presence', {
        reactFlowWrapperExists: !!document.querySelector(
          '[data-testid="react-flow-wrapper"]'
        ),
        rfWrapperExists: !!document.querySelector(
          '[data-testid="rf__wrapper"]'
        ),
        controlsExists: !!document.querySelector('.react-flow__controls'),
        dropZoneExists: !!document.querySelector('#canvas-drop-zone'),
      });
    });
  } catch (e) {
    // ignore
  }

  // Wait for React Flow wrapper
  try {
    await page.waitForSelector('[data-testid="react-flow-wrapper"]', {
      timeout,
    });
  } catch (err) {
    // Fallback to alternative selector
    await page.waitForSelector('[data-testid="rf__wrapper"]', { timeout });
  }

  // Wait for basic React Flow components to be ready
  await page.waitForSelector('.react-flow__controls', { timeout });

  // Optionally wait for canvas drop zone (may not exist on all canvas pages)
  try {
    await page.waitForSelector('#canvas-drop-zone', { timeout: 2000 });
  } catch (err) {
    // Canvas drop zone not present, continue anyway
    console.log('Canvas drop zone not found, continuing...');
  }

  // Wait for network to be idle
  await page.waitForLoadState('networkidle');

  // Wait for initial render to complete
  await page.waitForTimeout(500);
}

/**
 * Comprehensive test setup that ensures clean, predictable state
 */
export async function setupTest(
  page: Page,
  options: TestIsolationOptions & { seedScenario?: string; url?: string } = {}
): Promise<void> {
  // Clean state first
  await cleanTestState(page);

  // Navigate to the specified URL or canvas by default
  const targetUrl = options.url || '/canvas';
  await page.goto(targetUrl);

  try {
    await page.evaluate(() => {
      const provider = (window as unknown).mockCollaborationProvider;
      if (provider && typeof provider.resetForTests === 'function') {
        provider.resetForTests();
      }
    });
  } catch (error) {
    console.warn('Failed to reset collaboration provider', error);
  }

  try {
    await page.evaluate(() => {
      (window as unknown).__E2E_TEST_MODE = true;
      (window as unknown).__E2E_TEST_NO_POINTER_BLOCK = true;
      try {
        localStorage.setItem('E2E_DISABLE_OVERLAYS', '1');
      } catch (err) {
        // ignore
      }

      const neutralizeOverlays = () => {
        const selectors = [
          '.MuiModal-backdrop',
          '.MuiBackdrop-root',
          '.MuiDrawer-root',
        ];
        selectors.forEach((selector) => {
          document.querySelectorAll(selector).forEach((el) => {
            const element = el as HTMLElement;
            element.style.pointerEvents = 'none';
            element.style.opacity = '0';
          });
        });

        document.querySelectorAll('[data-testid="page-designer"]').forEach((el) => {
          const element = el as HTMLElement;
          element.style.pointerEvents = 'auto';
          element.style.opacity = '';
        });
      };

      neutralizeOverlays();

      const disablePaletteFocus = () => {
        const palette = document.querySelector('#component-palette, [data-testid="component-palette"]') as HTMLElement | null;
        if (!palette) {
          return;
        }
        palette.setAttribute('data-e2e-no-focus', 'true');
        const focusable = palette.querySelectorAll<HTMLElement>('button, a, input, select, textarea, [tabindex]');
        focusable.forEach((el) => {
          if (!el.hasAttribute('data-e2e-original-tabindex')) {
            const existing = el.getAttribute('tabindex');
            el.setAttribute('data-e2e-original-tabindex', existing ?? '');
          }
          el.setAttribute('tabindex', '-1');
          el.setAttribute('aria-hidden', 'true');
        });
      };

      disablePaletteFocus();

      if (!(window as unknown).__E2E_OVERLAY_OBSERVER) {
        const observer = new MutationObserver(() => {
          neutralizeOverlays();
          disablePaletteFocus();
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });
        (window as unknown).__E2E_OVERLAY_OBSERVER = observer;
      }
    });
  } catch (error) {
    console.warn('Failed to set E2E test mode flag', error);
  }

  // Diagnostic: log localStorage keys and canvas persistence summary so Playwright traces
  // capture what persisted snapshot (if any) the app will attempt to load.
  try {
    await page.evaluate(() => {
      try {
        const keys = Object.keys(localStorage || {});
        // eslint-disable-next-line no-console
        console.debug('[E2E] localStorage keys', keys);

        const persistenceKey = 'yappc-canvas:demo-project:main-canvas';
        const stored = localStorage.getItem(persistenceKey);
        if (stored) {
          try {
            const snapshot = JSON.parse(stored as string);
            // eslint-disable-next-line no-console
            console.debug('[E2E] persistence snapshot summary', {
              key: persistenceKey,
              version: snapshot.version,
              elements: Array.isArray(snapshot.data?.elements)
                ? snapshot.data.elements.length
                : 0,
              connections: Array.isArray(snapshot.data?.connections)
                ? snapshot.data.connections.length
                : 0,
            });
          } catch (e) {
            // eslint-disable-next-line no-console
            console.error('[E2E] failed to parse persistence snapshot', e);
          }
        } else {
          // eslint-disable-next-line no-console
          console.debug('[E2E] persistence key not found', persistenceKey);
        }
      } catch (e) {
        // eslint-disable-next-line no-console
        console.error('[E2E] localStorage inspect error', e);
      }
    });
  } catch (e) {
    // ignore page-level errors during diagnostics
  }

  // Seed data if requested (after navigation)
  if (options.seedData && options.seedScenario) {
    await seedCanvasTestData(page, options.seedScenario);
  }

  // Wait for canvas to be ready (only for canvas pages)
  if (targetUrl.includes('/canvas') || targetUrl === '/') {
    await waitForCanvasReady(page);
  }

  // Defensive test fix: if the component palette overlays interactive canvas areas
  // it can intercept pointer events during automated clicks. For E2E stability
  // temporarily disable pointer-events on the palette container so tests can
  // interact with canvas controls. This is a test-only change and should be
  // removed once the underlying UI stacking/z-index issue is fixed.
  try {
    // Previously we injected a test-only style to disable palette pointer-events
    // to avoid click interception. With the new two-column layout the palette
    // is a dedicated left column and won't overlap the canvas, so no injection
    // is required. Keep this block here as a no-op for older test runs.
  } catch (e) {
    // ignore errors from the diagnostic step
  }
}

/**
 * Test teardown - clean up after test
 */
export async function teardownTest(page: Page): Promise<void> {
  await cleanTestState(page);
  // Remove any test-only injected style used to disable palette pointer-events
  try {
    await page.evaluate(() => {
      const style = document.getElementById(
        'e2e-disable-palette-pointer-events'
      );
      if (style && style.parentNode) style.parentNode.removeChild(style);
    });
  } catch (e) {
    // ignore
  }
}
