/**
 * End-to-end product correctness golden path.
 *
 * Covers login -> workspace -> project -> phase cockpit -> generate/run backend
 * actions with real browser rendering and API-level route mocks.
 *
 * @doc.type test
 * @doc.purpose Golden path coverage for mounted YAPPC product correctness audit
 * @doc.layer product
 */

import { expect, test, type Page, type Route } from '@playwright/test';

const WORKSPACE_ID = 'ws-e2e';
const PROJECT_ID = 'proj-e2e';

const project = {
  id: PROJECT_ID,
  name: 'Golden Path Project',
  description: 'End-to-end correctness fixture',
  workspaceId: WORKSPACE_ID,
  ownerWorkspaceId: WORKSPACE_ID,
  lifecyclePhase: 'GENERATE',
  currentPhase: 'GENERATE',
  status: 'ACTIVE',
  aiHealthScore: 91,
  aiNextActions: ['Review generated diff'],
  createdAt: '2026-05-06T10:00:00.000Z',
  updatedAt: '2026-05-06T11:00:00.000Z',
  lastActivityAt: '2026-05-06T11:00:00.000Z',
};

async function fulfillJson(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

async function setupGoldenPathApi(page: Page): Promise<void> {
  await page.addInitScript(() => {
    window.localStorage.setItem('onboarding_complete', JSON.stringify('true'));
    window.localStorage.setItem('yappc:currentWorkspaceId', JSON.stringify('ws-e2e'));
  });

  await page.route('**/api/auth/login', (route) =>
    fulfillJson(route, {
      accessToken: 'e2e-access-token',
      refreshToken: 'e2e-refresh-token',
      expiresIn: 3600,
    }),
  );
  await page.route('**/api/auth/me', (route) =>
    fulfillJson(route, {
      id: 'user-e2e',
      email: 'operator@example.com',
      name: 'Operator',
      role: 'ADMIN',
      tenantId: 'tenant-e2e',
      workspaceIds: [WORKSPACE_ID],
    }),
  );
  await page.route('**/api/auth/validate', (route) => fulfillJson(route, { valid: true }));
  await page.route('**/api/onboarding/status', (route) =>
    fulfillJson(route, {
      completed: true,
      primaryPersona: 'developer',
      activePersonas: ['developer'],
    }),
  );
  await page.route('**/api/workspaces', (route) =>
    fulfillJson(route, {
      workspaces: [
        {
          id: WORKSPACE_ID,
          name: 'Golden Workspace',
          description: 'Workspace used by the audit golden path',
          ownerId: 'user-e2e',
          projectCount: 1,
          memberCount: 1,
          createdAt: '2026-05-06T10:00:00.000Z',
          updatedAt: '2026-05-06T11:00:00.000Z',
        },
      ],
    }),
  );
  await page.route(`**/api/workspaces/${WORKSPACE_ID}`, (route) =>
    fulfillJson(route, {
      workspace: {
        id: WORKSPACE_ID,
        name: 'Golden Workspace',
        description: 'Workspace used by the audit golden path',
        ownerId: 'user-e2e',
        ownedProjects: [project],
        includedProjects: [],
        createdAt: '2026-05-06T10:00:00.000Z',
        updatedAt: '2026-05-06T11:00:00.000Z',
      },
    }),
  );
  await page.route(`**/api/projects?workspaceId=${WORKSPACE_ID}`, (route) =>
    fulfillJson(route, { owned: [project], included: [] }),
  );
  await page.route(`**/api/projects/${PROJECT_ID}`, (route) => fulfillJson(route, { project }));
  await page.route(`**/api/projects/${PROJECT_ID}/current`, (route) => fulfillJson(route, project));
  await page.route(`**/api/projects/${PROJECT_ID}/activity`, (route) =>
    fulfillJson(route, {
      projectId: PROJECT_ID,
      activity: [
        {
          id: 'audit-e2e-1',
          source: 'audit',
          action: 'PROJECT_UPDATED',
          summary: 'Golden path project updated',
          timestamp: '2026-05-06T11:05:00.000Z',
          actor: 'user-e2e',
          success: true,
        },
      ],
    }),
  );
  await page.route('**/api/phases/GENERATE/next?projectId=proj-e2e', (route) =>
    fulfillJson(route, {
      projectId: PROJECT_ID,
      currentPhase: 'GENERATE',
      nextPhase: 'RUN',
      canAdvance: true,
      readiness: 95,
      blockers: [],
      requiredArtifacts: ['Generated page bundle', 'Generated test bundle'],
      completedArtifacts: ['Validated shape packet'],
      estimatedReadyIn: 'Ready now',
      estimatedReadyInHours: 0,
      predictionConfidence: 0.9,
      checkedAt: '2026-05-06T11:10:00.000Z',
    }),
  );
  await page.route('**/api/phases/RUN/next?projectId=proj-e2e', (route) =>
    fulfillJson(route, {
      projectId: PROJECT_ID,
      currentPhase: 'RUN',
      nextPhase: 'OBSERVE',
      canAdvance: true,
      readiness: 92,
      blockers: [],
      requiredArtifacts: ['Deployment approval'],
      completedArtifacts: ['Generated bundle'],
      estimatedReadyIn: 'Ready now',
      estimatedReadyInHours: 0,
      predictionConfidence: 0.88,
      checkedAt: '2026-05-06T11:15:00.000Z',
    }),
  );
  await page.route(`**/api/projects/${PROJECT_ID}/artifacts`, (route) => fulfillJson(route, []));
  await page.route(`**/api/projects/${PROJECT_ID}/sprints/current`, (route) => fulfillJson(route, null));
  await page.route(`**/api/projects/${PROJECT_ID}/backlog?limit=20`, (route) => fulfillJson(route, []));
  await page.route(`**/api/projects/${PROJECT_ID}/runs?limit=10`, (route) => fulfillJson(route, []));
  await page.route('**/api/v1/yappc/generate', (route) =>
    fulfillJson(route, { runId: 'gen-e2e-1', status: 'RUNNING', reviewRequired: true }),
  );
  await page.route('**/api/v1/yappc/generate/diff', (route) =>
    fulfillJson(route, { runId: 'gen-e2e-1', status: 'PENDING', diff: { files: [] }, reviewRequired: true }),
  );
  await page.route('**/api/v1/workflows/yappc-run/start', (route) =>
    fulfillJson(route, { runId: 'workflow-e2e-1', templateId: 'yappc-run', status: 'RUNNING' }, 202),
  );
  await page.route('**/api/v1/workflows/workflow-e2e-1/status', (route) =>
    fulfillJson(route, { runId: 'workflow-e2e-1', templateId: 'yappc-run', status: 'RUNNING' }),
  );
}

test.describe('End-to-end product correctness golden path', () => {
  test('logs in, selects workspace/project, and exercises generate/run phase actions', async ({ page }) => {
    await setupGoldenPathApi(page);

    await page.goto(`/login?redirectTo=/workspaces`);
    await page.getByTestId('email-input').fill('operator@example.com');
    await page.getByTestId('password-input').fill('CorrectHorseBatteryStaple1!');
    await page.getByTestId('login-submit').click();

    await expect(page).toHaveURL(/\/workspaces$/);
    await expect(page.getByTestId('workspaces-page')).toBeVisible();
    await page.getByText('Golden Workspace').click();

    await expect(page).toHaveURL(/\/projects$/);
    await expect(page.getByText('Golden Path Project')).toBeVisible();
    await page.getByText('Golden Path Project').click();

    await expect(page).toHaveURL(new RegExp(`/p/${PROJECT_ID}`));
    await page.goto(`/p/${PROJECT_ID}/generate`);
    await expect(page.getByTestId('generate-cockpit')).toBeVisible();
    await page.getByTestId('generate-code').click();
    await expect(page.getByTestId('phase-action-result')).toContainText('gen-e2e-1');

    await page.goto(`/p/${PROJECT_ID}/run`);
    await expect(page.getByTestId('run-cockpit')).toBeVisible();
    await page.getByTestId('check-readiness').click();
    await expect(page.getByTestId('phase-action-result')).toContainText('workflow-e2e-1');
  });
});
