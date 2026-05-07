/**
 * Page designer live backend E2E coverage.
 *
 * Exercises the browser-mounted page designer against the scoped page artifact
 * HTTP contract: preview rendering, autosave, conflict surfacing, and reload.
 *
 * @doc.type test
 * @doc.purpose Live browser coverage for page designer save/preview/reload
 * @doc.layer product
 */

import { expect, test, type Page, type Route } from '@playwright/test';

const WORKSPACE_ID = 'ws-page-designer-e2e';
const PROJECT_ID = 'proj-page-designer-e2e';

const project = {
  id: PROJECT_ID,
  name: 'Page Designer Backend Project',
  description: 'E2E fixture for page artifact persistence',
  workspaceId: WORKSPACE_ID,
  ownerWorkspaceId: WORKSPACE_ID,
  lifecyclePhase: 'SHAPE',
  currentPhase: 'SHAPE',
  status: 'ACTIVE',
  role: 'OWNER',
  isOwned: true,
  isDefault: false,
  capabilities: { read: true, create: true, update: true, delete: true, comment: true },
  aiHealthScore: 88,
  aiNextActions: ['Review page artifact persistence'],
  createdAt: '2026-05-07T10:00:00.000Z',
  updatedAt: '2026-05-07T11:00:00.000Z',
};

async function fulfillJson(route: Route, body: unknown, status = 200, headers?: Record<string, string>): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    headers,
    body: JSON.stringify(body),
  });
}

async function setupPageDesignerApi(page: Page): Promise<{
  readonly getSaveCount: () => Promise<number>;
  readonly getReloadCount: () => Promise<number>;
  readonly getSavedHeaders: () => Promise<Record<string, string> | null>;
  readonly getPageArtifactRequests: () => Promise<readonly string[]>;
}> {
  const pageArtifactRequests: string[] = [];

  await page.addInitScript(() => {
    window.localStorage.setItem('onboarding_complete', JSON.stringify('true'));
    window.localStorage.setItem('yappc:currentWorkspaceId', JSON.stringify('ws-page-designer-e2e'));
    window.localStorage.setItem('yappc:e2e:seed-page-designer', 'proj-page-designer-e2e');
    document.cookie = 'accessToken=page-designer-access-token; path=/; SameSite=Lax';
    document.cookie = 'refreshToken=page-designer-refresh-token; path=/; SameSite=Lax';

    const originalFetch = window.fetch.bind(window);
    const backend = {
      saveCount: 0,
      reloadCount: 0,
      savedDocument: null as unknown,
      savedHeaders: null as Record<string, string> | null,
      requests: [] as string[],
    };
    (window as typeof window & { __pageArtifactBackend?: typeof backend }).__pageArtifactBackend = backend;

    window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
      const request = new Request(input, init);
      if (!request.url.includes('/api/v1/page-artifacts/')) {
        return originalFetch(input, init);
      }

      backend.requests.push(`${request.method} ${request.url}`);
      if (request.method === 'PUT') {
        backend.saveCount += 1;
        backend.savedDocument = await request.clone().json();
        backend.savedHeaders = Object.fromEntries(request.headers.entries());

        if (backend.saveCount === 2) {
          return new Response(JSON.stringify({ message: 'remote version is newer' }), {
            status: 409,
            headers: {
              'Content-Type': 'application/json',
              'X-Current-Version': 'remote-version-2',
            },
          });
        }

        return new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      if (request.method === 'GET') {
        backend.reloadCount += 1;
        return new Response(
          JSON.stringify({
            ...(typeof backend.savedDocument === 'object' && backend.savedDocument !== null
              ? backend.savedDocument
              : {}),
            documentId: 'remote-version-2',
            syncStatus: 'synced',
          }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          },
        );
      }

      return originalFetch(input, init);
    };
  });

  page.on('request', (request) => {
    if (request.url().includes('page-artifacts')) {
      pageArtifactRequests.push(`${request.method()} ${request.url()}`);
    }
  });

  await page.route('**/api/auth/login', (route) =>
    fulfillJson(route, {
      user: {
        id: 'user-page-designer-e2e',
        email: 'operator@example.com',
        name: 'Operator',
        role: 'ADMIN',
        tenantId: 'tenant-page-designer-e2e',
      },
      tokens: {
        accessToken: 'page-designer-access-token',
        refreshToken: 'page-designer-refresh-token',
        expiresIn: 3600,
      },
    }),
  );
  await page.route('**/api/auth/me', (route) =>
    fulfillJson(route, {
      id: 'user-page-designer-e2e',
      email: 'operator@example.com',
      name: 'Operator',
      role: 'ADMIN',
      tenantId: 'tenant-page-designer-e2e',
      workspaceIds: [WORKSPACE_ID],
    }),
  );
  await page.route('**/api/auth/validate', (route) => fulfillJson(route, { valid: true }));
  await page.route('**/auth/exchange', (route) =>
    fulfillJson(route, { platformToken: 'platform-token-page-designer-e2e', expiresIn: 3600 }),
  );
  await page.route('**/api/onboarding/status', (route) =>
    fulfillJson(route, { completed: true, primaryPersona: 'developer', activePersonas: ['developer'] }),
  );
  await page.route('**/api/workspaces', (route) =>
    fulfillJson(route, {
      workspaces: [
        {
          id: WORKSPACE_ID,
          name: 'Page Designer Workspace',
          ownerId: 'user-page-designer-e2e',
          role: 'OWNER',
          isOwner: true,
          capabilities: { read: true, create: true, update: true, delete: true, comment: true },
          isDefault: true,
          aiTags: [],
          ownedProjects: [project],
          includedProjects: [],
          createdAt: '2026-05-07T10:00:00.000Z',
          updatedAt: '2026-05-07T11:00:00.000Z',
        },
      ],
    }),
  );
  await page.route(`**/api/workspaces/${WORKSPACE_ID}`, (route) =>
    fulfillJson(route, {
      workspace: {
        id: WORKSPACE_ID,
        name: 'Page Designer Workspace',
        ownerId: 'user-page-designer-e2e',
        role: 'OWNER',
        isOwner: true,
        capabilities: { read: true, create: true, update: true, delete: true, comment: true },
        isDefault: true,
        aiTags: [],
        ownedProjects: [project],
        includedProjects: [],
        createdAt: '2026-05-07T10:00:00.000Z',
        updatedAt: '2026-05-07T11:00:00.000Z',
      },
    }),
  );
  await page.route('**/api/projects**', (route) =>
    fulfillJson(route, { owned: [project], included: [] }),
  );
  await page.route(`**/api/projects/${PROJECT_ID}`, (route) => fulfillJson(route, { project }));
  await page.route(`**/api/projects/${PROJECT_ID}/current`, (route) => fulfillJson(route, project));
  await page.route(`**/api/projects/${PROJECT_ID}/artifacts`, (route) => fulfillJson(route, []));
  await page.route('**/api/artifacts', (route) =>
    fulfillJson(route, {
      id: 'artifact-page-designer-e2e',
      type: 'mockup',
      title: 'New Mockup',
      status: 'draft',
      createdAt: '2026-05-07T11:05:00.000Z',
      updatedAt: '2026-05-07T11:05:00.000Z',
      createdBy: 'developer',
      projectId: PROJECT_ID,
      phase: 'SHAPE',
      flowStage: 'DESIGN',
      version: 1,
    }),
  );
  await page.route('**/api/v1/yappc/preview/sessions', (route) =>
    fulfillJson(route, {
      sessionId: 'preview-session-page-designer-e2e',
      sessionToken: 'preview-token-page-designer-e2e',
      expiresAt: '2026-05-07T12:05:00.000Z',
    }),
  );
  await page.route('**/api/v1/yappc/preview/sessions/validate', (route) =>
    fulfillJson(route, { valid: true }),
  );
  return {
    getSaveCount: () =>
      page.evaluate(() => (window as typeof window & { __pageArtifactBackend?: { saveCount: number } }).__pageArtifactBackend?.saveCount ?? 0),
    getReloadCount: () =>
      page.evaluate(() => (window as typeof window & { __pageArtifactBackend?: { reloadCount: number } }).__pageArtifactBackend?.reloadCount ?? 0),
    getSavedHeaders: () =>
      page.evaluate(() => (window as typeof window & { __pageArtifactBackend?: { savedHeaders: Record<string, string> | null } }).__pageArtifactBackend?.savedHeaders ?? null),
    getPageArtifactRequests: async () => [
      ...pageArtifactRequests,
      ...(await page.evaluate(() => (window as typeof window & { __pageArtifactBackend?: { requests: string[] } }).__pageArtifactBackend?.requests ?? [])),
    ],
  };
}

async function importSemanticPage(page: Page, modelId: string): Promise<void> {
  const buttonId = `${modelId}-button`;
  await page.getByTestId('page-designer-import-btn').evaluate((element) => {
    (element as HTMLButtonElement).click();
  });
  await page.getByTestId('page-designer-import-textarea').fill(
    JSON.stringify({
      id: modelId,
      name: `Imported ${modelId}`,
      pages: [
        {
          id: modelId,
          name: `Imported ${modelId}`,
          serializedBuilderDocument: {
            id: `${modelId}-doc`,
            version: '1',
            name: `Imported ${modelId}`,
            designSystem: {
              id: 'ghatana-ds-v1',
              name: 'Ghatana Design System',
              version: '1.0.0',
              tokenSetIds: [],
              componentContracts: [],
              themeId: 'default',
            },
            rootNodes: [buttonId],
            nodes: {
              [buttonId]: {
                id: buttonId,
                contractName: 'Button',
                props: { children: `Launch ${modelId}` },
                slots: {},
                metadata: { name: `Launch ${modelId}` },
              },
            },
            metadata: {
              createdAt: '2026-05-07T10:00:00.000Z',
              updatedAt: '2026-05-07T10:00:00.000Z',
              author: 'page-designer-e2e',
              dataClassification: 'INTERNAL',
              trustLevel: 'semi-trusted',
            },
          },
        },
      ],
    }),
  );
  await page.getByTestId('page-designer-import-confirm').click();
}

test.describe('Page designer preview/save/reload against backend contract', () => {
  test('autosaves page edits, shows live preview, and reloads after a backend conflict', async ({ page }) => {
    const backend = await setupPageDesignerApi(page);

    await page.goto('/login?redirectTo=/workspaces');
    await page.getByTestId('email-input').fill('operator@example.com');
    await page.getByTestId('password-input').fill('CorrectHorseBatteryStaple1!');
    await page.getByTestId('login-submit').click();

    await expect(page).toHaveURL(/\/workspaces$/);
    await page.goto(`/p/${PROJECT_ID}/canvas`);
    await expect(page.getByTestId('page-designer')).toBeVisible({ timeout: 30000 });
    await expect(page.getByTestId('live-preview-panel')).toBeVisible({ timeout: 30000 });

    await importSemanticPage(page, 'page-designer-e2e-model-1');
    await expect(page.getByTestId('live-preview-iframe')).toBeVisible({ timeout: 30000 });
    await expect.poll(
      async () => (await backend.getPageArtifactRequests()).join('\n') || 'none',
      {
        timeout: 10000,
        message: 'Expected the browser to issue a page-artifact request after importing a semantic page.',
      },
    ).toContain('/api/v1/page-artifacts/');
    await expect.poll(
      () => backend.getSaveCount(),
      {
        timeout: 10000,
        message: 'Expected a page artifact save request after importing a semantic page.',
      },
    ).toBeGreaterThanOrEqual(1);
    await expect.poll(() => backend.getSavedHeaders()).toMatchObject({
      'x-tenant-id': 'tenant-page-designer-e2e',
      'x-workspace-id': WORKSPACE_ID,
      'x-project-id': PROJECT_ID,
    });

    await importSemanticPage(page, 'page-designer-e2e-model-2');
    await expect(page.getByTestId('page-node-sync-status')).toContainText('conflict', { timeout: 10000 });

    await page.getByTestId('page-conflict-reload-remote').click();
    await expect.poll(() => backend.getReloadCount(), { timeout: 10000 }).toBeGreaterThanOrEqual(1);
  });
});
