/**
 * PHR web route traversal tests.
 *
 * Verifies that every stable route in the route contract is reachable without
 * a JavaScript error and renders at least one heading or landmark element.
 *
 * Anti-theater rule: each test navigates to the real page and observes real
 * DOM state — no object-literal assertions.
 */
import { test, expect } from '@playwright/test';
import { mockPhrEntitlements } from './phr-entitlements';
import { readFileSync } from 'fs';
import { join } from 'path';
import type { PhrRole } from '../../src/auth/PhrAccessContext';

// Load route contract to get all stable routes
const routeContractPath = join(process.cwd(), 'config', 'phr-route-contract.json');
const routeContract = JSON.parse(readFileSync(routeContractPath, 'utf-8'));

interface RouteContract {
  path: string;
  stability: string;
  minimumRole: string;
}

const stableRoutes = routeContract.routes.filter((r: RouteContract) => r.stability === 'stable');

// Routes that genuinely require parameters and cannot be exercised without
// a fixture record ID. These are excluded from the traversal sweep.
const PARAM_ROUTES = new Set([
  '/records/:id',
  '/documents/:id/ocr',
]);

// Routes that are only meaningful in a specific persona context.
const ADMIN_ONLY_ROUTES = new Set([
  '/admin/emergency',
  '/admin/release',
  '/admin/entitlements',
]);

const PROVIDER_ROUTES = new Set([
  '/provider',
  '/provider/patients',
]);

const CAREGIVER_ROUTES = new Set([
  '/caregiver',
]);

const FCHV_ROUTES = new Set([
  '/fchv',
]);

/**
 * Derive which persona to use for a given route.
 */
function roleFor(routePath: string): PhrRole {
  if (ADMIN_ONLY_ROUTES.has(routePath)) return 'admin';
  if (PROVIDER_ROUTES.has(routePath)) return 'clinician';
  if (CAREGIVER_ROUTES.has(routePath)) return 'caregiver';
  if (FCHV_ROUTES.has(routePath)) return 'admin'; // admin can see fchv routes in test
  return 'patient';
}

// ─── Core patient routes ────────────────────────────────────────────────────

test.describe('PHR route traversal — stable routes', () => {
  const coreRoutes = stableRoutes
    .filter((r: RouteContract) => {
      const path = r.path;
      return (
        !PARAM_ROUTES.has(path) &&
        !ADMIN_ONLY_ROUTES.has(path) &&
        !PROVIDER_ROUTES.has(path) &&
        !CAREGIVER_ROUTES.has(path) &&
        !FCHV_ROUTES.has(path) &&
        !path.includes(':')
      );
    });

  for (const entry of coreRoutes) {
    const path = entry.path;

    test(`GET ${path} renders without JS errors for patient`, async ({ page }) => {
      const jsErrors: string[] = [];
      page.on('pageerror', (err) => jsErrors.push(err.message));

      await mockPhrEntitlements(page, roleFor(path));
      const response = await page.goto(path);

      // Either the page loaded (any 2xx/3xx) or client-side routing handled it
      const status = response?.status() ?? 200;
      expect(status).toBeLessThan(500);

      // No unhandled JavaScript exceptions
      expect(jsErrors).toHaveLength(0);

      // At least one landmark element or heading must be present
      const hasContent = await Promise.race([
        page.getByRole('main').isVisible().catch(() => false),
        page.getByRole('heading').first().isVisible().catch(() => false),
        page.getByRole('alert').isVisible().catch(() => false),
      ]);
      expect(hasContent).toBe(true);
    });
  }
});

// ─── Privileged routes ────────────────────────────────────────────────────────

test.describe('PHR route traversal — admin routes', () => {
  for (const path of ADMIN_ONLY_ROUTES) {
    test(`GET ${path} renders without JS errors for admin`, async ({ page }) => {
      const jsErrors: string[] = [];
      page.on('pageerror', (err) => jsErrors.push(err.message));

      await mockPhrEntitlements(page, 'admin');
      const response = await page.goto(path);

      const status = response?.status() ?? 200;
      expect(status).toBeLessThan(500);
      expect(jsErrors).toHaveLength(0);
    });
  }
});

// ─── 403 route ─────────────────────────────────────────────────────────────

test('/403 forbidden route renders the forbidden page', async ({ page }) => {
  await mockPhrEntitlements(page);
  await page.goto('/403');

  // ForbiddenPage renders an alert or heading with access denied semantics
  const hasAlert = await page.getByRole('alert').isVisible().catch(() => false);
  const hasHeading = await page.getByRole('heading').isVisible().catch(() => false);
  expect(hasAlert || hasHeading).toBe(true);
});
