import { chromium, FullConfig } from '@playwright/test';
import fs from 'fs';
// import local node seed helper
import { ensureE2EMocks } from './seed-mocks';

// Global setup for Playwright tests. This script will run once before the test suite
// when PLAYWRIGHT_ENABLE_CANVAS=true. It attempts to seed demo data required by
// heavy canvas/diagram e2e specs by navigating to the app and invoking the UI
// "seed" controls. If those are not available it falls back to injecting seed
// data via a window hook if the app exposes one.

async function seedCanvas(baseURL: string) {
  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    console.log('[global-setup] Preparing localStorage seeds for canvas/diagram');

    // Small deterministic seed payload for Canvas PoC persistence
    const pocSnapshot = {
      version: '1.0.0',
      updatedAt: new Date().toISOString(),
      checksum: 'c1',
      canvas: {
        elements: [
          {
            id: 'node-1',
            kind: 'node',
            type: 'component',
            position: { x: 300, y: 200 },
            size: { width: 150, height: 80 },
            data: { label: 'Frontend App' },
            style: {},
          },
          {
            id: 'node-2',
            kind: 'node',
            type: 'api',
            position: { x: 600, y: 200 },
            size: { width: 150, height: 80 },
            data: { label: 'API Gateway' },
            style: {},
          },
          {
            id: 'node-3',
            kind: 'node',
            type: 'data',
            position: { x: 900, y: 200 },
            size: { width: 150, height: 80 },
            data: { label: 'Database' },
            style: {},
          },
        ],
        connections: [
          { id: 'conn-1', source: 'node-1', target: 'node-2', type: 'default', data: {} },
        ],
        viewport: { x: 0, y: 0, zoom: 1 },
        metadata: { title: 'GlobalSeed', description: 'Seed from global-setup' },
      },
      viewport: { x: 0, y: 0, zoom: 1 },
    };

    // Diagram JSON seed (uses DiagramPersistence localStorage key pattern)
    const diagramNames = ['Default', 'Demo', 'demo-project', 'project-demo', 'Main'];
    const diagramJson = JSON.stringify({ nodes: [
      { id: 'n1', data: { label: 'Frontend App' }, position: { x: 200, y: 200 } },
      { id: 'n2', data: { label: 'API Gateway' }, position: { x: 400, y: 200 } },
    ], edges: [{ id: 'e1', source: 'n1', target: 'n2' }] });

    // Open base URL to ensure same-origin localStorage access
    await page.goto(baseURL, { waitUntil: 'domcontentloaded', timeout: 30000 });

    // Set localStorage entries before navigating to canvas pages
    await page.evaluate(({ pocKey, pocValue, diagNames, diagValue }) => {
      try {
        // Core PoC key
        localStorage.setItem(pocKey, JSON.stringify(pocValue));

        // Also write a few diagram name variants that the app may look for
        diagNames.forEach((name: string) => {
          try { localStorage.setItem(`diagram-${name}`, diagValue); } catch (e) { /* ignore */ }
        });

        // Extra canvas/legacy keys
        try { localStorage.setItem('yappc:canvas', JSON.stringify(pocValue.canvas)); } catch (e) {}
        try { localStorage.setItem('canvas-seed', JSON.stringify(pocValue.canvas)); } catch (e) {}

        // Helpful E2E flags used by some routes
        localStorage.setItem('E2E_SIMPLE_PAGES', '1');
        localStorage.setItem('E2E_DISABLE_OVERLAYS', '1');
        // Hint to the app that we created demo workspace/project for e2e runs
        try {
          localStorage.setItem('e2e_created_project', JSON.stringify({ workspaceId: 'ws-1', projectId: 'proj-1' }));
        } catch (e) {
          // ignore
        }
        // Also write full mock arrays that the browser MSW-resolvers can prefer
        try {
          localStorage.setItem('e2e:mockWorkspaces', JSON.stringify([
            { id: 'ws-1', name: 'E2E Workspace (ws-1)', description: 'Seeded workspace', ownerId: 'u-e2e', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
          ]));
          localStorage.setItem('e2e:mockProjects', JSON.stringify([
            { id: 'proj-1', workspaceId: 'ws-1', name: 'E2E Project (proj-1)', description: 'Seeded project', type: 'UI', targets: ['web'], status: 'active', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
          ]));
        } catch (e) {
          // ignore
        }
        // Enable verbose test logger in the app so CanvasFlow can emit helpful console traces
        try {
          // @ts-ignore - test-only global
          (window as unknown).__TEST_LOGGER_ENABLED__ = true;
        } catch (e) {}
        console.log('[global-setup] localStorage seeded (multiple keys)');
        return true;
      } catch (e) {
        console.error('[global-setup] localStorage set failed', e);
        return false;
      }
    }, {
      pocKey: 'yappc:poc:canvas',
      pocValue: pocSnapshot,
      diagNames: diagramNames,
      diagValue: diagramJson,
    });

    // Small delay to ensure storage is ready
    await page.waitForTimeout(200);

    // Persist context storage (localStorage/cookies) so Playwright test contexts can reuse it
    try {
      // If dev server exposes seed endpoint, POST the seeded data so server can serve it to browser resolvers
      try {
        const seedPayload = {
          workspaces: [{ id: 'ws-1', name: 'E2E Workspace (ws-1)' }],
          projects: [{ id: 'proj-1', workspaceId: 'ws-1', name: 'E2E Project (proj-1)' }],
        };
        // Use absolute URL for the request and send JSON so the Vite dev server plugin can parse it
        const seedUrl = baseURL.replace(/\/$/, '') + '/__e2e__/seed';
        try {
          await page.request.post(seedUrl, {
            data: JSON.stringify(seedPayload),
            headers: { 'content-type': 'application/json' },
            timeout: 5000,
          });
          console.log('[global-setup] posted seed payload to dev server');

          // Force a GET to /index.html so the Vite dev plugin has a chance to inject inline seed
          try {
            const indexUrl = baseURL.replace(/\/$/, '') + '/index.html';
            await page.request.get(indexUrl, { timeout: 3000 }).catch(() => {});
            // give the browser a moment to run any injected script
            await page.waitForTimeout(150);
          } catch (e) {
            // ignore
          }
        } catch (innerErr) {
          console.warn('[global-setup] dev server seed POST failed or not available', innerErr);
        }
      } catch (e) {
        // ignore if server not available
        console.warn('[global-setup] dev server seed POST failed or not available', e);
      }

      const storagePath = './e2e/playwright-storage-state.json';
      await context.storageState({ path: storagePath });
      console.log('[global-setup] persisted storageState to', storagePath);
    } catch (e) {
      console.warn('[global-setup] failed to persist storage state', e);
    }

    // Attach console and page error listeners to capture runtime failures for diagnostics
    const consoleMessages: Array<{ type: string; text: string }> = [];
    page.on('console', (msg) => {
      try { consoleMessages.push({ type: msg.type(), text: msg.text() }); } catch (e) {}
    });
    const pageErrors: Array<string> = [];
    page.on('pageerror', (err) => { try { pageErrors.push(String(err)); } catch (e) {} });

    // Navigate to baseURL to prime the dev client and allow any inline seed injection to run
    try {
      await page.goto(baseURL, { waitUntil: 'networkidle', timeout: 90000 });
      await page.waitForTimeout(300);
    } catch (e) {
      // ignore, we'll attempt to warm routes individually
    }

    // Helper: verify the dev-server/MSW/Apollo stack sees the seeded project via GraphQL
    async function verifySeededGraphQL(): Promise<boolean> {
      try {
        const graphqlUrl = baseURL.replace(/\/$/, '') + '/graphql';
        const query = `query ListProjects { projects { id workspaceId name } }`;
        const res = await page.request.post(graphqlUrl, { data: JSON.stringify({ query }), headers: { 'content-type': 'application/json' }, timeout: 5000 });
        if (!res) return false;
        const json = await res.json().catch(() => null);
        if (!json) return false;
        const data = json.data || json;
        const projects = data.projects || (Array.isArray(json) ? json : null);
        if (Array.isArray(projects)) {
          const found = projects.find((p: unknown) => p && p.id === 'proj-1');
          if (found) {
            console.log('[global-setup] GraphQL verification: found seeded project proj-1');
            return true;
          }
        }
        return false;
      } catch (e) {
        return false;
      }
    }

    // Helper to attempt navigation + selector wait with retries
    async function tryWarmRoute(
      url: string,
      selector: string | string[],
      attempts = 5,
      waitForSelectorTimeout = 45000
    ) {
      let lastErr: any = null;
      const selectors = Array.isArray(selector) ? selector : [selector];

      for (let i = 0; i < attempts; i++) {
        try {
          // Give more time for slow CI/dev machines
          await page.goto(url, { waitUntil: 'networkidle', timeout: 120000 });

          // Wait for at least one GraphQL response (if the page issues GraphQL) to indicate the app booted
          try {
            await page.waitForResponse(
              (r) => r.url().includes('/graphql') && r.status() === 200,
              { timeout: 15000 }
            );
            console.log('[global-setup] observed graphql response while warming', url);
          } catch (e) {
            // Not fatal — some pages may not hit graphql immediately
          }

          // Try each selector in order; succeed if any becomes visible
          for (const s of selectors) {
            try {
              await page.waitForSelector(s, { timeout: waitForSelectorTimeout });
              console.log('[global-setup] warmed route (selector matched) ', url, s);
              return true;
            } catch (selErr) {
              // continue to next selector
            }
          }

          throw new Error(`no selector matched after navigation to ${url}`);
        } catch (err) {
          lastErr = err;
          const errMsg = err && (err as unknown).message ? (err as unknown).message : String(err);
          console.warn(`[global-setup] attempt ${i + 1}/${attempts} failed for ${url}:`, errMsg);
          try {
            // Try a reload before the next attempt to trigger another hydration
            await page.reload({ waitUntil: 'networkidle', timeout: 45000 }).catch(() => {});
          } catch (e) {
            // ignore
          }
          // small backoff
          await page.waitForTimeout(1000 + i * 1000);
        }
      }

      console.warn('[global-setup] failed to warm route after retries', url, lastErr);

      // Diagnostic dump to help investigate persistent failures in CI
      try {
        const screenshotPath = './e2e/global-setup-failure.png';
        await page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => {});
        console.warn('[global-setup] wrote diagnostic screenshot to', screenshotPath);
        const htmlPath = './e2e/global-setup-failure.html';
        const html = await page.content();
        try { fs.writeFileSync(htmlPath, html, 'utf8'); } catch (e) {}
        console.warn('[global-setup] wrote diagnostic html to', htmlPath);
      } catch (e) {
        // ignore diagnostic failures
      }

      // also write console and page errors to a JSON file for easier inspection
      try {
        const logsPath = './e2e/global-setup-logs.json';
        fs.writeFileSync(logsPath, JSON.stringify({ console: consoleMessages, pageErrors }, null, 2), 'utf8');
        console.warn('[global-setup] wrote diagnostic logs to', logsPath);
      } catch (e) {
        // ignore
      }

      return false;
    }

    // Now navigate to canvas and canvas-poc to ensure they render seeded state
    try {
      const pocUrl = baseURL.replace(/\/$/, '') + '/canvas-poc';
      // Try multiple selectors (data-testid, generic react-flow classes, or the heading text)
      const pocSelectors = [
        '[data-testid="rf__wrapper"]',
        'body [data-testid="canvas-poc-root"]',
        '.react-flow',
        '.react-flow__node',
        'text=Canvas Phase 0 PoC',
      ];
      const warmed = await tryWarmRoute(pocUrl, pocSelectors, 5, 45000);
      if (!warmed) {
        // As a last-ditch fallback, consider the route warmed if GraphQL returns the seeded project
        const graphqlOk = await verifySeededGraphQL();
        if (graphqlOk) {
          console.log('[global-setup] GraphQL indicates seeded project is available; treating /canvas-poc as warmed');
        } else {
          console.warn('[global-setup] canvas-poc did not warm after retries and GraphQL check');
        }
      }
    } catch (err) {
      console.warn('[global-setup] canvas-poc warming errored', err);
    }

    try {
      const canvasUrl = baseURL.replace(/\/$/, '') + '/canvas';
      const canvasSelectors = ['[data-testid="react-flow-wrapper"]', '[data-testid="rf__wrapper"]', '.react-flow', '.react-flow__node'];
      const warmedCanvas = await tryWarmRoute(canvasUrl, canvasSelectors, 5, 45000);
      if (!warmedCanvas) {
        const graphqlOk = await verifySeededGraphQL();
        if (graphqlOk) {
          console.log('[global-setup] GraphQL indicates seeded project is available; treating /canvas as warmed');
        } else {
          console.warn('[global-setup] canvas did not warm after retries and GraphQL check');
        }
      }
    } catch (err) {
      console.warn('[global-setup] canvas warming errored', err);
    }

    // Also warm the canonical seeded project canvas route (some e2e specs use /w/ws-1/p/proj-1/canvas)
    try {
      const seededRoute = baseURL.replace(/\/$/, '') + '/w/ws-1/p/proj-1/canvas';
      await page.goto(seededRoute, { waitUntil: 'networkidle', timeout: 60000 });
      // Wait a bit longer here; some environments need more time to hydrate MSW and GraphQL
      await page.waitForSelector('[data-testid="react-flow-wrapper"]', { timeout: 20000 });
      console.log('[global-setup] seeded project canvas route loaded after seeding', seededRoute);
    } catch (err) {
      console.warn('[global-setup] seeded project canvas route did not render after seeding', err);
    }

    // Debug: attempt to GET the dev-server mock-data.json to confirm server received the POST
    try {
      const seedJsonUrl = baseURL.replace(/\/$/, '') + '/__e2e__/mock-data.json';
      const res = await page.request.get(seedJsonUrl, { timeout: 3000 });
      const text = await res.text();
      console.log('[global-setup] dev-server mock-data.json content:', text);
    } catch (err) {
      console.warn('[global-setup] could not GET dev-server mock-data.json', err);
    }

  } catch (err) {
    console.error('[global-setup] Error during seeding:', err);
  } finally {
    await context.close();
    await browser.close();
  }
}

export default async function globalSetup(config: FullConfig) {
  const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? config.projects?.[0]?.use?.baseURL ?? 'http://localhost:5173';
  console.log('[global-setup] PLAYWRIGHT_ENABLE_CANVAS=', process.env.PLAYWRIGHT_ENABLE_CANVAS);
  if (process.env.PLAYWRIGHT_ENABLE_CANVAS) {
    try {
      console.log('[global-setup] Ensuring node-side mocks for e2e ids');
      // Attempt to ensure ws-1/proj-1 exist in mocks resolvers used by MSW
      try { ensureE2EMocks(); } catch (e) { console.warn('[global-setup] ensureE2EMocks failed', e); }
    } catch (e) {
      console.warn('[global-setup] Node-side seeding skipped or failed', e);
    }
    await seedCanvas(baseURL as string);
  } else {
    console.log('[global-setup] Skipping canvas seeding (PLAYWRIGHT_ENABLE_CANVAS not set)');
  }
}
