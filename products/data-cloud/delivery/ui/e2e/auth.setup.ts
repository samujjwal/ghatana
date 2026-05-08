/**
 * Auth setup for Playwright authenticated project.
 *
 * Runs once before the 'authenticated' project.  Signs in via the Data Cloud
 * login page (or MSW-stubbed endpoint when running locally without a live
 * backend) and persists the browser storage state to `e2e/.auth/user.json`.
 *
 * Individual tests that depend on authentication should NOT repeat this flow —
 * they declare `project: 'authenticated'` and receive the pre-logged-in context.
 *
 * Environment variables:
 *   E2E_AUTH_USER     — test-account email   (default: testuser@example.local)
 *   E2E_AUTH_PASSWORD — test-account password (default: testpassword123)
 *
 * @doc.type setup
 * @doc.purpose Playwright auth state bootstrap for authenticated E2E tests
 * @doc.layer testing
 */

import { test as setup } from '@playwright/test';
import path from 'node:path';

const AUTH_STATE_FILE = path.join(__dirname, '.auth', 'user.json');

setup('authenticate', async ({ page }) => {
  const authUser = process.env['E2E_AUTH_USER'] ?? 'testuser@example.local';
  const authPassword = process.env['E2E_AUTH_PASSWORD'] ?? 'testpassword123';

  // Navigate to the login page.
  await page.goto('/login');

  // Fill credentials and submit.
  await page.getByLabel(/email/i).fill(authUser);
  await page.getByLabel(/password/i).fill(authPassword);
  await page.getByRole('button', { name: /sign in|log in/i }).click();

  // Wait until the authenticated shell is visible (any nav or dashboard element).
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), {
    timeout: 15_000,
  });

  // Persist the full browser storage state (cookies + localStorage).
  await page.context().storageState({ path: AUTH_STATE_FILE });
});
