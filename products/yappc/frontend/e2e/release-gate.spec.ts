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

type StoredSession = {
  token?: string;
  refreshToken?: string;
  expiresAt?: string;
};

type WorkspaceListResponse = {
  workspaces?: Array<{ id: string }>;
};

// ─── Helpers ─────────────────────────────────────────────────────────────────

async function loginAs(
  page: Parameters<Parameters<typeof test>[1]>[0]['page'],
  email: string,
  password: string,
): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('email-input').fill(email);
  await page.getByTestId('password-input').fill(password);
  await page.getByTestId('login-submit').click();
  // Wait for redirect away from login page
  await expect(page).not.toHaveURL(/\/login$/, { timeout: 15_000 });
}

async function readStoredSession(
  page: Parameters<Parameters<typeof test>[1]>[0]['page'],
): Promise<StoredSession | null> {
  return page.evaluate(() => {
    const raw = window.localStorage.getItem('auth-session');
    return raw ? (JSON.parse(raw) as StoredSession) : null;
  });
}

async function writeStoredSession(
  page: Parameters<Parameters<typeof test>[1]>[0]['page'],
  session: StoredSession,
): Promise<void> {
  await page.evaluate((nextSession) => {
    window.localStorage.setItem('auth-session', JSON.stringify(nextSession));
  }, session);
}

async function getAccessToken(
  page: Parameters<Parameters<typeof test>[1]>[0]['page'],
): Promise<string> {
  const session = await readStoredSession(page);
  if (!session?.token) {
    throw new Error('Missing access token in auth-session localStorage entry');
  }

  return session.token;
}

async function getFirstWorkspaceId(
  page: Parameters<Parameters<typeof test>[1]>[0]['page'],
): Promise<string> {
  const token = await getAccessToken(page);
  const response = await page.request.get('/api/workspaces', {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  expect(response.ok()).toBeTruthy();
  const body = (await response.json()) as WorkspaceListResponse;
  const workspaceId = body.workspaces?.[0]?.id;
  if (!workspaceId) {
    throw new Error('No workspace available for release-gate CRUD checks');
  }

  return workspaceId;
}

async function logoutFromUserMenu(
  page: Parameters<Parameters<typeof test>[1]>[0]['page'],
): Promise<void> {
  await page.getByLabel('User menu').click();
  await page.getByRole('menuitem', { name: /logout/i }).click();
  await expect(page).toHaveURL(/\/login(?:\?|$)/, { timeout: 15_000 });
}

// ─── Tests ───────────────────────────────────────────────────────────────────

test.describe('@release-gate Real backend smoke tests', () => {
  test.use({ storageState: { cookies: [], origins: [] } }); // Always start clean

  // 1. Login flow ─────────────────────────────────────────────────────────────

  test('@release-gate — login succeeds with valid credentials', async ({
    page,
  }) => {
    await loginAs(page, SMOKE_USER, SMOKE_PASSWORD);

    await expect(page).toHaveURL(/\/(workspaces|projects)$/);
    await expect(page.locator('[data-testid="workspaces-page"], h1')).toBeVisible({ timeout: 10_000 });
  });

  // 2. Workspace dashboard ────────────────────────────────────────────────────

  test('@release-gate — workspace dashboard loads and lists at least one workspace', async ({
    page,
  }) => {
    await loginAs(page, SMOKE_USER, SMOKE_PASSWORD);

    await page.goto('/workspaces');
    await expect(page.getByTestId('workspaces-page')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole('heading', { name: /workspaces/i, level: 1 })).toBeVisible();
    await expect(page.locator('[data-testid="workspace-card"]').first()).toBeVisible();
  });

  // 3. Project creation ───────────────────────────────────────────────────────

  test('@release-gate — user can create a project, reload, sign out, and still see it after re-authentication', async ({
    page,
  }) => {
    await loginAs(page, SMOKE_USER, SMOKE_PASSWORD);

    const workspaceId = await getFirstWorkspaceId(page);
    const projectName = `smoke-${Date.now()}`;

    const projectCreateRequestPromise = page.waitForRequest((request) => {
      return (
        request.method() === 'POST' &&
        request.url().includes('/api/projects')
      );
    });

    const projectCreateResponsePromise = page.waitForResponse((response) => {
      return (
        response.request().method() === 'POST' &&
        response.url().includes('/api/projects')
      );
    });

    await page.goto('/projects');
    await expect(page.getByRole('heading', { name: /projects/i, level: 1 })).toBeVisible({ timeout: 10_000 });

    await page.getByTestId('create-project-button').click();
    await page.getByTestId('project-name-input').fill(projectName);
    await page.getByTestId('project-description-input').fill('Release-gate browser proof');
    await page.getByTestId('create-project-submit').click();

    const projectCreateRequest = await projectCreateRequestPromise;
    expect(projectCreateRequest.postDataJSON()).toMatchObject({
      name: projectName,
      workspaceId,
    });

    const projectCreateResponse = await projectCreateResponsePromise;
    expect(projectCreateResponse.ok()).toBeTruthy();

    const projectCreateBody = (await projectCreateResponse.json()) as {
      project?: { id?: string };
    };
    expect(projectCreateBody.project?.id).toBeTruthy();
    expect(projectCreateBody.project?.id?.startsWith('temp-')).toBeFalsy();

    await page.waitForURL(/\/p\/[^/]+\/canvas/, { timeout: 15_000 });

    await page.goto('/projects');
    await expect(page.getByText(projectName)).toBeVisible({ timeout: 10_000 });

    await page.reload();
    await expect(page.getByText(projectName)).toBeVisible({ timeout: 10_000 });

    await logoutFromUserMenu(page);
    await loginAs(page, SMOKE_USER, SMOKE_PASSWORD);
    await page.goto('/projects');
    await expect(page.getByText(projectName)).toBeVisible({ timeout: 10_000 });
  });

  // 4. Navigate into a project ────────────────────────────────────────────────

  test('@release-gate — navigating into a project opens the active canvas view', async ({
    page,
  }) => {
    await loginAs(page, SMOKE_USER, SMOKE_PASSWORD);

    await page.goto('/projects');
    await page.locator('main .grid button, main tbody tr').first().click();
    await page.waitForURL(/\/p\/[^/]+\/(canvas)?/, { timeout: 15_000 });
    await expect(page.getByRole('main')).toBeVisible({ timeout: 10_000 });
  });

  // 5. Token refresh recovery ────────────────────────────────────────────────

  test('@release-gate — expired access tokens are recovered through the refresh token on the active app shell', async ({
    page,
  }) => {
    await loginAs(page, SMOKE_USER, SMOKE_PASSWORD);
    await page.goto('/projects');
    await expect(page.getByRole('heading', { name: /projects/i, level: 1 })).toBeVisible({ timeout: 10_000 });

    const originalSession = await readStoredSession(page);
    expect(originalSession?.token).toBeTruthy();
    expect(originalSession?.refreshToken).toBeTruthy();

    await writeStoredSession(page, {
      ...originalSession,
      token: 'expired-access-token',
      expiresAt: new Date(Date.now() - 60_000).toISOString(),
    });

    const refreshResponsePromise = page.waitForResponse((response) => {
      return response.request().method() === 'POST' && response.url().includes('/api/auth/refresh');
    });

    await page.reload();

    const refreshResponse = await refreshResponsePromise;
    expect(refreshResponse.ok()).toBeTruthy();
    await expect(page).toHaveURL(/\/projects$/, { timeout: 15_000 });
    await expect(page.getByRole('heading', { name: /projects/i, level: 1 })).toBeVisible({ timeout: 10_000 });

    const refreshedSession = await readStoredSession(page);
    expect(refreshedSession?.token).toBeTruthy();
    expect(refreshedSession?.token).not.toBe('expired-access-token');
    expect(refreshedSession?.refreshToken).toBeTruthy();
  });

  // 6. Logout invalidates session ─────────────────────────────────────────────

  test('@release-gate — logout invalidates the session and redirects to login', async ({
    page,
  }) => {
    await loginAs(page, SMOKE_USER, SMOKE_PASSWORD);

    expect(await readStoredSession(page)).not.toBeNull();

    await logoutFromUserMenu(page);

    await expect(page.getByTestId('login-form')).toBeVisible({ timeout: 10_000 });
    await expect(await readStoredSession(page)).toBeNull();
  });
});
