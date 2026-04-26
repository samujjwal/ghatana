/**
 * @group release-gate
 * @tier R-ui
 *
 * Real-backend project CRUD coverage.
 *
 * Runs only when REAL_BACKEND=true.
 */
import { test, expect } from '@playwright/test';

const REAL_BACKEND = process.env.REAL_BACKEND === 'true';

test.skip(!REAL_BACKEND, 'Skipped: REAL_BACKEND=true is required');

const SMOKE_USER = process.env.E2E_SMOKE_USER ?? '';
const SMOKE_PASSWORD = process.env.E2E_SMOKE_PASSWORD ?? '';

type AuthSession = {
  token?: string;
};

type WorkspaceListResponse = {
  workspaces?: Array<{ id: string }>;
};

async function loginAs(page: Parameters<Parameters<typeof test>[1]>[0]['page']): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('email-input').fill(SMOKE_USER);
  await page.getByTestId('password-input').fill(SMOKE_PASSWORD);
  await page.getByTestId('login-submit').click();
  await expect(page).not.toHaveURL(/\/login$/, { timeout: 15_000 });
}

async function readAuthSession(page: Parameters<Parameters<typeof test>[1]>[0]['page']): Promise<AuthSession | null> {
  return page.evaluate(() => {
    const raw = window.localStorage.getItem('auth-session');
    return raw ? (JSON.parse(raw) as AuthSession) : null;
  });
}

async function getToken(page: Parameters<Parameters<typeof test>[1]>[0]['page']): Promise<string> {
  const session = await readAuthSession(page);
  if (!session?.token) {
    throw new Error('No auth token found in browser session');
  }
  return session.token;
}

async function getWorkspaceId(page: Parameters<Parameters<typeof test>[1]>[0]['page']): Promise<string> {
  const token = await getToken(page);
  const workspaceResponse = await page.request.get('/api/workspaces', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(workspaceResponse.ok()).toBeTruthy();

  const body = (await workspaceResponse.json()) as WorkspaceListResponse;
  const workspaceId = body.workspaces?.[0]?.id;
  if (!workspaceId) {
    throw new Error('No workspace available for project CRUD test');
  }

  return workspaceId;
}

test.describe('@release-gate Project CRUD', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('@release-gate — create, read, update, and delete project using real backend', async ({ page }) => {
    await loginAs(page);

    const token = await getToken(page);
    const workspaceId = await getWorkspaceId(page);
    const projectName = `crud-${Date.now()}`;

    await page.goto('/projects');
    await page.getByTestId('create-project-button').click();
    await page.getByTestId('project-name-input').fill(projectName);
    await page.getByTestId('project-description-input').fill('Project CRUD proof');

    const createResponsePromise = page.waitForResponse((response) => {
      return response.request().method() === 'POST' && response.url().includes('/api/projects');
    });

    await page.getByTestId('create-project-submit').click();

    const createResponse = await createResponsePromise;
    expect(createResponse.status()).toBe(201);

    const createBody = (await createResponse.json()) as {
      project?: { id?: string; name?: string };
    };
    const projectId = createBody.project?.id;
    expect(projectId).toBeTruthy();
    expect(createBody.project?.name).toBe(projectName);

    await page.waitForURL(/\/p\/[^/]+\/canvas/, { timeout: 15_000 });

    const listResponse = await page.request.get(`/api/projects?workspaceId=${encodeURIComponent(workspaceId)}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(listResponse.ok()).toBeTruthy();

    const listBody = (await listResponse.json()) as {
      owned?: Array<{ id: string; name: string }>;
    };
    expect(listBody.owned?.some((project) => project.id === projectId)).toBe(true);

    const patchResponse = await page.request.patch(`/api/projects/${projectId}?workspaceId=${encodeURIComponent(workspaceId)}`, {
      headers: {
        Authorization: `Bearer ${token}`,
        'content-type': 'application/json',
      },
      data: {
        description: 'Updated by release-gate CRUD test',
        status: 'ACTIVE',
      },
    });
    expect(patchResponse.ok()).toBeTruthy();

    const deleteResponse = await page.request.delete(`/api/projects/${projectId}?workspaceId=${encodeURIComponent(workspaceId)}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(deleteResponse.status()).toBe(204);

    const afterDeleteResponse = await page.request.get(`/api/projects?workspaceId=${encodeURIComponent(workspaceId)}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(afterDeleteResponse.ok()).toBeTruthy();

    const afterDeleteBody = (await afterDeleteResponse.json()) as {
      owned?: Array<{ id: string }>;
    };
    expect(afterDeleteBody.owned?.some((project) => project.id === projectId)).toBe(false);
  });
});
