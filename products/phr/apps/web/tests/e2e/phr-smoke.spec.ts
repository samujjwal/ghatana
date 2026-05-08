/**
 * PHR web smoke tests — backend-backed.
 *
 * These tests exercise real navigation flows including protected routes,
 * authentication guards, and health check API calls.  They run against a
 * locally running dev server and API (see playwright.config.ts webServer).
 *
 * Anti-theater rule (Section 29/35.3): assertions use real page state, not
 * hard-coded object literals.
 */

import { test, expect } from '@playwright/test';

// ---------------------------------------------------------------------------
// Health check — verifies the web server responds before UI tests run
// ---------------------------------------------------------------------------

test('GET /health returns 200 from dev server', async ({ request }) => {
  const response = await request.get('/health');
  expect(response.status()).toBe(200);
});

// ---------------------------------------------------------------------------
// Authentication flow
// ---------------------------------------------------------------------------

test.describe('PHR authentication flow', () => {
  test('loads login page with visible form fields', async ({ page }) => {
    await page.goto('/login');
    // Title must be visible — proves the page actually rendered
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    // At minimum an email field or username field must exist
    const emailField = page.getByRole('textbox', { name: /email|username|id/i });
    await expect(emailField).toBeVisible();
  });

  test('protected route /records redirects unauthenticated user to login', async ({ page }) => {
    await page.goto('/records');
    // Unauthenticated access must redirect to login — verifies route guards
    await expect(page).toHaveURL(/\/login/);
  });

  test('protected route /appointments redirects unauthenticated user to login', async ({
    page,
  }) => {
    await page.goto('/appointments');
    await expect(page).toHaveURL(/\/login/);
  });

  test('demo path navigates to patient summary page', async ({ page }) => {
    await page.goto('/login');
    // Only click the demo link if it is present (not required in all envs)
    const demoLink = page.getByRole('link', { name: /demo account/i });
    const isDemoVisible = await demoLink.isVisible().catch(() => false);
    if (isDemoVisible) {
      await demoLink.click();
      await expect(page.getByText(/patient summary|dashboard|records/i)).toBeVisible({
        timeout: 10000,
      });
    } else {
      test.skip();
    }
  });
});

// ---------------------------------------------------------------------------
// Navigation structure
// ---------------------------------------------------------------------------

test('/ renders without JavaScript errors', async ({ page }) => {
  const jsErrors: string[] = [];
  page.on('pageerror', (err) => jsErrors.push(err.message));
  await page.goto('/');
  // Give any deferred JS a moment to fail if it's going to
  await page.waitForTimeout(500);
  expect(jsErrors).toHaveLength(0);
});

test('404 page is rendered for unknown routes', async ({ page }) => {
  const response = await page.goto('/this-route-does-not-exist-at-all');
  // Either 404 status or a client-side 404 page must appear
  const is404Status = response?.status() === 404;
  const has404Text = await page.getByText(/not found|404/i).isVisible().catch(() => false);
  expect(is404Status || has404Text).toBe(true);
});
