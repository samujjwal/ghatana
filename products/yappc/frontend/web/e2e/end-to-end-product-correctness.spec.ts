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
const TENANT_ID = 'tenant-e2e';
const USER_ID = 'user-e2e';

type LifecycleSmokePhase = 'intent' | 'shape' | 'validate' | 'generate' | 'run' | 'observe';

const nextPhaseByPhase: Record<LifecycleSmokePhase, string> = {
  intent: 'shape',
  shape: 'validate',
  validate: 'generate',
  generate: 'run',
  run: 'observe',
  observe: 'learn',
};

const primaryActionByPhase: Record<LifecycleSmokePhase, string> = {
  intent: 'define-requirements',
  shape: 'add-components',
  validate: 'approve-changes',
  generate: 'generate-code',
  run: 'check-readiness',
  observe: 'view-metrics',
};

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

function buildPhasePacket(phase: LifecycleSmokePhase): Record<string, unknown> {
  const upperPhase = phase.toUpperCase();
  const nextPhase = nextPhaseByPhase[phase];
  const actionId = primaryActionByPhase[phase];

  return {
    phase,
    projectId: PROJECT_ID,
    projectName: 'Golden Path Project',
    tenantId: TENANT_ID,
    workspaceId: WORKSPACE_ID,
    workspaceName: 'Golden Workspace',
    lifecyclePhase: upperPhase,
    actor: {
      actorId: USER_ID,
      actorName: 'Operator',
      role: 'ADMIN',
      isOwner: true,
      isAdmin: true,
    },
    tenantTier: 'ENTERPRISE',
    enabledPhaseFlags: [`phase.${phase}.enabled`],
    capabilities: {
      canRead: true,
      canCreate: true,
      canUpdate: true,
      canDelete: false,
      canApprove: true,
      canReject: true,
      canRollback: true,
    },
    blockers: [],
    readiness: {
      canAdvance: true,
      nextPhase,
      missingPrerequisites: [],
      completenessScore: 0.96,
      isDegraded: false,
    },
    requiredArtifacts: [
      {
        artifactId: `${phase}-required-artifact`,
        artifactType: `${upperPhase}_PACKET`,
        title: `${upperPhase} contract`,
        description: `Required ${phase} lifecycle artifact`,
        isComplete: true,
      },
    ],
    completedArtifacts: [
      {
        artifactId: `${phase}-completed-artifact`,
        artifactType: `${upperPhase}_PACKET`,
        version: '1.0.0',
        title: `${upperPhase} evidence bundle`,
        completedAt: '2026-05-26T16:00:00.000Z',
        completedBy: USER_ID,
        evidenceId: `${phase}-evidence`,
      },
    ],
    activityFeed: [
      {
        id: `${phase}-activity`,
        type: 'lifecycle',
        action: `${upperPhase}_READY`,
        summary: `${upperPhase} lifecycle state loaded from deterministic E2E fixture`,
        actor: USER_ID,
        timestamp: '2026-05-26T16:00:00.000Z',
        severity: 'INFO',
      },
    ],
    evidence: [
      {
        id: `${phase}-evidence-record`,
        type: 'artifact',
        title: `${upperPhase} evidence`,
        description: `Evidence proving ${phase} is ready`,
        timestamp: '2026-05-26T16:00:00.000Z',
        metadata: {
          traceId: `trace-${phase}`,
          source: 'playwright-lifecycle-smoke',
        },
        evidenceId: `${phase}-evidence`,
      },
    ],
    governance: [
      {
        id: `${phase}-governance`,
        type: 'policy',
        outcome: 'ALLOW',
        actor: 'policy-engine',
        timestamp: '2026-05-26T16:00:00.000Z',
        metadata: {
          policy: `yappc.${phase}.advance`,
        },
        policyDecisionId: `${phase}-policy`,
      },
    ],
    platformRunStatus: phase === 'observe' ? {
      runId: 'workflow-e2e-1',
      status: 'SUCCEEDED',
      platform: 'kernel',
      startedAt: '2026-05-26T16:00:00.000Z',
      completedAt: '2026-05-26T16:03:00.000Z',
      traceId: 'trace-kernel-handoff',
      evidenceIds: ['kernel-intent-evidence'],
    } : undefined,
    availableActions: [
      {
        actionId,
        label: `phaseAction.${actionId}.label`,
        description: `phaseAction.${actionId}.description`,
        enabled: true,
        requiredPermission: 'project:write',
        parameters: {},
      },
    ],
    dashboardActions: {
      primaryAction: actionId,
      blockedActions: [],
      reviewRequiredActions: phase === 'generate' ? [actionId] : [],
      safeToContinueActions: [actionId],
    },
    healthSignals: {
      preview: {
        isHealthy: true,
        status: 'HEALTHY',
        issues: [],
      },
      generation: {
        isHealthy: true,
        status: 'HEALTHY',
        lastGeneratedAt: '2026-05-26T16:00:00.000Z',
        issues: [],
      },
      runtime: {
        isHealthy: true,
        status: phase === 'observe' ? 'SUCCEEDED' : 'HEALTHY',
        lastDeployedAt: '2026-05-26T16:03:00.000Z',
        issues: [],
      },
    },
    timestamp: new Date('2026-05-26T16:00:00.000Z').getTime(),
    correlationId: `corr-${phase}`,
  };
}

async function setupGoldenPathApi(page: Page): Promise<void> {
  await page.addInitScript(() => {
    document.cookie = 'accessToken=e2e-access-token; path=/; SameSite=Lax';
    window.localStorage.setItem('onboarding_complete', JSON.stringify('true'));
    window.localStorage.setItem('yappc:currentWorkspaceId', JSON.stringify('ws-e2e'));
  });

  await page.route('**/api/auth/login', (route) =>
    fulfillJson(route, {
      user: {
        id: USER_ID,
        email: 'operator@example.com',
        name: 'Operator',
        role: 'ADMIN',
        tenantId: TENANT_ID,
        workspaceIds: [WORKSPACE_ID],
      },
      tokens: {
        accessToken: 'e2e-access-token',
        refreshToken: 'e2e-refresh-token',
        expiresIn: 3600,
      },
    }),
  );
  await page.route('**/api/auth/me', (route) =>
    fulfillJson(route, {
      id: USER_ID,
      email: 'operator@example.com',
      name: 'Operator',
      role: 'ADMIN',
      tenantId: TENANT_ID,
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
  await page.route(/\/api(?:\/api)?\/v1\/phase\/packet(?:\?|$)/, (route) => {
    const requestUrl = new URL(route.request().url());
    const phase = requestUrl.searchParams.get('phase') as LifecycleSmokePhase | null;
    if (!phase || !(phase in nextPhaseByPhase)) {
      return fulfillJson(route, { message: 'Unknown lifecycle smoke phase' }, 400);
    }

    return fulfillJson(route, buildPhasePacket(phase));
  });
  await page.route('**/api/v1/lifecycle/advance', (route) =>
    fulfillJson(route, {
      success: true,
      currentPhase: 'GENERATE',
      errors: [],
    }),
  );
  await page.route('**/api/audit/events', (route) =>
    fulfillJson(route, {
      id: 'audit-e2e-1',
      type: 'phase.action',
      userId: USER_ID,
      projectId: PROJECT_ID,
      flowStage: 'generate',
      phase: 'GENERATE',
      description: 'E2E audit event',
      timestamp: '2026-05-26T16:00:00.000Z',
    }),
  );
  await page.route('**/api/v1/yappc/generate/product-unit-intent', async (route) => {
    const requestBody = await route.request().postDataJSON() as Record<string, unknown>;
    return fulfillJson(route, {
      intentId: 'pui-e2e-1',
      status: 'VALID',
      valid: true,
      validationErrors: [],
      projectId: requestBody['projectId'] ?? PROJECT_ID,
      workspaceId: requestBody['workspaceId'] ?? WORKSPACE_ID,
      tenantId: requestBody['tenantId'] ?? TENANT_ID,
      format: 'json',
      intent: {
        apiVersion: 'kernel.ghatana.com/v1',
        kind: 'ProductUnitIntent',
        metadata: {
          name: 'golden-path-project',
          productUnitId: PROJECT_ID,
          workspaceId: WORKSPACE_ID,
        },
        spec: {
          productUnitKind: 'application',
          lifecycleProfile: 'yappc-production',
          targetProviders: ['aep'],
          surfaces: ['web-ui', 'web-api'],
        },
      },
      traceId: 'trace-kernel-handoff',
      evidenceIds: ['kernel-intent-evidence'],
    });
  });
}

async function selectGoldenProject(page: Page): Promise<void> {
  await expect(page).toHaveURL(/\/workspaces$/);
  await expect(page.getByTestId('workspaces-page')).toBeVisible();
  await page.getByRole('heading', { name: 'Golden Workspace' }).click();

  await expect(page).toHaveURL(/\/projects$/);
  await expect(page.getByText('Golden Path Project')).toBeVisible();
  await page.getByRole('heading', { name: 'Golden Path Project' }).click();
  await expect(page).toHaveURL(new RegExp(`/p/${PROJECT_ID}`));
}

test.describe('End-to-end product correctness golden path', () => {
  test('logs in, selects workspace/project, and exercises generate/run phase actions', async ({ page }) => {
    await setupGoldenPathApi(page);

    await page.goto(`/login?redirectTo=/workspaces`);
    await page.getByTestId('email-input').fill('operator@example.com');
    await page.getByTestId('password-input').fill('CorrectHorseBatteryStaple1!');
    await page.getByTestId('login-submit').click();

    await selectGoldenProject(page);
    await page.goto(`/p/${PROJECT_ID}/generate`);
    await expect(page.getByTestId('generate-cockpit')).toBeVisible();
    await page.getByTestId('generate-code').click();
    await expect(page.getByTestId('phase-action-result')).toContainText('gen-e2e-1');

    await page.goto(`/p/${PROJECT_ID}/run`);
    await expect(page.getByTestId('run-cockpit')).toBeVisible();
    await page.getByTestId('check-readiness').click();
    await expect(page.getByTestId('phase-action-result')).toContainText('workflow-e2e-1');
  });

  test('smokes workspace to Kernel ProductUnitIntent handoff and observe lifecycle', async ({ page }) => {
    await setupGoldenPathApi(page);

    await page.goto('/login?redirectTo=/workspaces');
    await page.getByTestId('email-input').fill('operator@example.com');
    await page.getByTestId('password-input').fill('CorrectHorseBatteryStaple1!');
    await page.getByTestId('login-submit').click();

    await selectGoldenProject(page);

    for (const phase of ['intent', 'shape', 'validate'] satisfies readonly LifecycleSmokePhase[]) {
      await page.goto(`/p/${PROJECT_ID}/${phase}`);
      await expect(page.getByTestId(`${phase}-cockpit`)).toBeVisible();
      await expect(page.getByTestId('phase-packet-summary')).toBeVisible();
      await expect(page.getByTestId('phase-contract-summary')).toBeVisible();
    }

    await page.goto(`/p/${PROJECT_ID}/generate`);
    await expect(page.getByTestId('generate-cockpit')).toBeVisible();
    await page.getByTestId('generate-code').click();
    await expect(page.getByTestId('phase-action-result')).toContainText('gen-e2e-1');

    const kernelIntent = await page.evaluate(async ({ tenantId, workspaceId, projectId }) => {
      const response = await fetch('/api/v1/yappc/generate/product-unit-intent', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': tenantId,
          'X-Workspace-ID': workspaceId,
          'X-Project-ID': projectId,
        },
        body: JSON.stringify({
          tenantId,
          workspaceId,
          projectId,
          productName: 'Golden Path Project',
          provider: 'aep',
          surfaces: ['web-ui', 'web-api'],
          lifecycleProfile: 'yappc-production',
        }),
      });

      if (!response.ok) {
        throw new Error(`Kernel ProductUnitIntent generation failed: ${response.status}`);
      }

      return response.json() as Promise<{
        readonly intentId: string;
        readonly status: string;
        readonly traceId: string;
        readonly evidenceIds: readonly string[];
      }>;
    }, {
      tenantId: TENANT_ID,
      workspaceId: WORKSPACE_ID,
      projectId: PROJECT_ID,
    });

    expect(kernelIntent.intentId).toBe('pui-e2e-1');
    expect(kernelIntent.status).toBe('VALID');
    expect(kernelIntent.traceId).toBe('trace-kernel-handoff');
    expect(kernelIntent.evidenceIds).toContain('kernel-intent-evidence');

    await page.goto(`/p/${PROJECT_ID}/run`);
    await expect(page.getByTestId('run-cockpit')).toBeVisible();
    await page.getByTestId('check-readiness').click();
    await expect(page.getByTestId('phase-action-result')).toContainText('workflow-e2e-1');

    await page.goto(`/p/${PROJECT_ID}/observe`);
    await expect(page.getByTestId('observe-cockpit')).toBeVisible();
    await expect(page.getByTestId('phase-packet-summary')).toContainText('Golden Path Project');
    await expect(page.getByTestId('phase-contract-summary')).toBeVisible();
  });
});
