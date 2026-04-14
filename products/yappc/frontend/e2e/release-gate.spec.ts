/**
 * @group release-gate
 * @tier R-ui
 *
 * Release-gate browser E2E tests for YAPPC.
 *
 * Every test in this file is tagged `@release-gate` so the authoritative
 * release-gate CI workflow (`.github/workflows/release-gate.yml`) can target
 * them with `--grep "@release-gate"`.
 *
 * IMPORTANT: These tests run against a REAL backend (no route interception).
 * They require the app and API server to be running and accessible at
 * `PLAYWRIGHT_BASE_URL` (default http://localhost:7002).
 *
 * Required environment variables (set in CI via GitHub secrets):
 *   PLAYWRIGHT_BASE_URL  — base URL (default: http://localhost:7002)
 *   E2E_SMOKE_USER       — email of the pre-seeded smoke-test account
 *   E2E_SMOKE_PASSWORD   — password of the pre-seeded smoke-test account
 *
 * Guard: if `REAL_BACKEND=true` is NOT set, all tests are skipped at
 * collection time so they do not pollute mock-backed CI runs.
 *
 * @doc.type test-suite
 * @doc.purpose Browser-level release gate coverage against a live backend
 * @doc.layer application
 * @doc.pattern E2E Release Gate
 */

import { test, expect } from '@playwright/test';

// ─── Guard ────────────────────────────────────────────────────────────────────

const REAL_BACKEND = process.env.REAL_BACKEND === 'true';

test.skip(!REAL_BACKEND, 'Skipped: REAL_BACKEND=true is required');

// ─── Constants ────────────────────────────────────────────────────────────────

const SMOKE_USER = process.env.E2E_SMOKE_USER ?? '';
const SMOKE_PASSWORD = process.env.E2E_SMOKE_PASSWORD ?? '';

// ─── Helpers ─────────────────────────────────────────────────────────────────

async function loginAs(
  page: Parameters<Parameters<typeof test>[1]>[0]['page'],
  email: string,
  password: string,
): Promise<void> {
  await page.goto('/auth/login');
  await page.getByLabel(/email/i).fill(email);
  await page.getByLabel(/password/i).fill(password);
  await page.getByRole('button', { name: /sign in|log in/i }).click();
  // Wait for redirect away from login page
  await expect(page).not.toHaveURL(/\/auth\/login/, { timeout: 15_000 });
}

// ─── Tests ───────────────────────────────────────────────────────────────────

test.describe('@release-gate Real backend smoke tests', () => {
  test.use({ storageState: { cookies: [], origins: [] } }); // Always start clean

  // 1. Login flow ─────────────────────────────────────────────────────────────

  test('@release-gate — login succeeds with valid credentials', async ({
    page,
  }) => {
    await loginAs(page, SMOKE_USER, SMOKE_PASSWORD);

    // After a successful login the user lands on the dashboard or workspaces page
    await expect(
      page.getByRole('navigation').first(),
    ).toBeVisible({ timeout: 10_000 });
  });

  // 2. Workspace dashboard ────────────────────────────────────────────────────

  test('@release-gate — workspace dashboard loads and lists at least one workspace', async ({
    page,
  }) => {
    await loginAs(page, SMOKE_USER, SMOKE_PASSWORD);

    await page.goto('/app/workspaces');
    await expect(
      page.getByRole('heading', { name: /workspace/i, level: 1 }),
    ).toBeVisible({ timeout: 10_000 });

    // At least one workspace link should be visible
    await expect(page.locator('a[href*="/app/w/"]').first()).toBeVisible();
  });

  // 3. Project creation ───────────────────────────────────────────────────────

  test('@release-gate — user can create a project and it appears in the list', async ({
    page,
  }) => {
    await loginAs(page, SMOKE_USER, SMOKE_PASSWORD);

    const projectName = `smoke-${Date.now()}`;

    // Navigate to the workspace page and open the first workspace
    await page.goto('/app/workspaces');
    await page.locator('a[href*="/app/w/"]').first().click();
    await page.waitForURL(/\/app\/w\//);

    // Click "New Project" or equivalent CTA
    const newProjectBtn = page.getByRole('button', {
      name: /new project|create project/i,
    });
    await expect(newProjectBtn).toBeVisible({ timeout: 8_000 });
    await newProjectBtn.click();

    // Fill in the project name
    const nameInput = page.getByLabel(/project name/i);
    await expect(nameInput).toBeVisible({ timeout: 5_000 });
    await nameInput.fill(projectName);
    await page.getByRole('button', { name: /create|save/i }).click();

    // The new project should appear in the project list
    await expect(page.getByText(projectName)).toBeVisible({ timeout: 10_000 });
  });

  // 4. Navigate into a project ────────────────────────────────────────────────

  test('@release-gate — navigating into a project opens the canvas or editor view', async ({
    page,
  }) => {
    await loginAs(page, SMOKE_USER, SMOKE_PASSWORD);

    await page.goto('/app/workspaces');
    await page.locator('a[href*="/app/w/"]').first().click();
    await page.waitForURL(/\/app\/w\//);

    // Click the first project link
    const projectLink = page.locator('a[href*="/projects/"]').first();
    await expect(projectLink).toBeVisible({ timeout: 8_000 });
    await projectLink.click();
    await page.waitForURL(/\/projects\//);

    // Should render the editor or canvas area
    const editorArea =
      page.locator('[data-testid="canvas"]').or(
        page.locator('[data-testid="editor"]'),
      ).or(
        page.getByRole('main'),
      );
    await expect(editorArea.first()).toBeVisible({ timeout: 10_000 });
  });

  // 5. Logout invalidates session ─────────────────────────────────────────────

  test('@release-gate — logout invalidates the session and redirects to login', async ({
    page,
  }) => {
    await loginAs(page, SMOKE_USER, SMOKE_PASSWORD);

    // Open user menu and click sign-out
    const userMenu = page.getByRole('button', {
      name: /account|user menu|profile/i,
    });
    await expect(userMenu).toBeVisible({ timeout: 8_000 });
    await userMenu.click();

    const signOutBtn = page.getByRole('menuitem', {
      name: /sign out|log out/i,
    });
    await expect(signOutBtn).toBeVisible({ timeout: 5_000 });
    await signOutBtn.click();

    // Should redirect to the login page
    await expect(page).toHaveURL(/\/auth\/login/, { timeout: 10_000 });

    // Trying to navigate to a protected route should redirect back to login
    await page.goto('/app/workspaces');
    await expect(page).toHaveURL(/\/auth\/login/, { timeout: 8_000 });
  });
});
