import type { Page, Route } from '@playwright/test';

export const WORKSPACE_ID = 'ws-e2e';
export const PROJECT_ID = 'proj-e2e';
export const TENANT_ID = 'tenant-e2e';
export const USER_ID = 'user-e2e';

export type LifecyclePhase =
  | 'intent'
  | 'shape'
  | 'validate'
  | 'generate'
  | 'run'
  | 'observe'
  | 'learn'
  | 'evolve';

type DegradedDependency = 'DATA_CLOUD' | 'AEP' | 'KERNEL';

interface FixtureOptions {
  readonly blockedValidate?: boolean;
  readonly runFailure?: boolean;
  readonly unauthorized?: boolean;
  readonly degradedDependency?: DegradedDependency;
}

const nextPhaseByPhase: Record<LifecyclePhase, string> = {
  intent: 'shape',
  shape: 'validate',
  validate: 'generate',
  generate: 'run',
  run: 'observe',
  observe: 'learn',
  learn: 'evolve',
  evolve: 'intent',
};

const primaryActionByPhase: Record<LifecyclePhase, string> = {
  intent: 'define-requirements',
  shape: 'add-components',
  validate: 'approve-changes',
  generate: 'generate-code',
  run: 'check-readiness',
  observe: 'view-metrics',
  learn: 'approve-learning',
  evolve: 'approve-evolution',
};

export async function fulfillJson(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

function buildPhasePacket(phase: LifecyclePhase, options: FixtureOptions): Record<string, unknown> {
  const upperPhase = phase.toUpperCase();
  const actionId = primaryActionByPhase[phase];
  const blocked = phase === 'validate' && options.blockedValidate;
  const degraded = options.degradedDependency !== undefined;
  const unauthorized = options.unauthorized === true;
  const runFailed = phase === 'run' && options.runFailure;

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
      role: unauthorized ? 'VIEWER' : 'ADMIN',
      isOwner: !unauthorized,
      isAdmin: !unauthorized,
    },
    tenantTier: 'ENTERPRISE',
    enabledPhaseFlags: [`phase.${phase}.enabled`],
    capabilities: {
      canRead: !unauthorized,
      canCreate: !unauthorized,
      canUpdate: !unauthorized,
      canDelete: false,
      canApprove: !unauthorized,
      canReject: !unauthorized,
      canRollback: !unauthorized,
    },
    blockers: blocked
      ? [
          {
            id: 'validate-blocker-1',
            type: 'CRITERION',
            title: 'Policy approval required',
            description: 'Policy denial blocks phase advancement.',
            severity: 'CRITICAL',
            resourceId: 'policy-approval',
            resolvable: true,
          },
        ]
      : [],
    readiness: {
      canAdvance: !blocked && !degraded && !unauthorized,
      nextPhase: nextPhaseByPhase[phase],
      missingPrerequisites: blocked ? ['Policy approval'] : degraded ? ['Dependency recovery'] : [],
      completenessScore: blocked ? 0.53 : degraded ? 0.62 : 0.95,
      isDegraded: degraded,
      estimatedReadyIn: blocked ? '~1 day' : 'Ready now',
      estimatedReadyInHours: blocked ? 24 : 0,
      predictionConfidence: blocked ? 0.61 : 0.92,
    },
    requiredArtifacts: [
      {
        artifactId: `${phase}-required-artifact`,
        artifactType: `${upperPhase}_PACKET`,
        title: `${upperPhase} contract`,
        description: `Required ${phase} lifecycle artifact`,
        isComplete: !blocked,
      },
    ],
    completedArtifacts: blocked
      ? []
      : [
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
        summary: `${upperPhase} lifecycle fixture loaded`,
        actor: USER_ID,
        timestamp: '2026-05-26T16:00:00.000Z',
        severity: blocked ? 'WARNING' : 'INFO',
      },
    ],
    evidence: [
      {
        id: `${phase}-evidence-record`,
        type: blocked ? 'SYSTEM_DEGRADED' : 'artifact',
        title: `${upperPhase} evidence`,
        description: blocked
          ? `${upperPhase} evidence unavailable due to policy denial`
          : `Evidence proving ${phase} is ready`,
        timestamp: '2026-05-26T16:00:00.000Z',
        metadata: {
          traceId: `trace-${phase}`,
          source: 'playwright-round7',
        },
        evidenceId: `${phase}-evidence`,
      },
    ],
    governance: [
      {
        id: `${phase}-governance`,
        type: blocked ? 'POLICY_DENIAL' : 'POLICY_APPROVAL',
        outcome: blocked ? 'DENIED' : 'APPROVED',
        actor: 'policy-engine',
        timestamp: '2026-05-26T16:00:00.000Z',
        metadata: {
          policy: `yappc.${phase}.advance`,
          actionId: 'phase.advance',
        },
        policyDecisionId: `${phase}-policy`,
      },
    ],
    platformRunStatus:
      phase === 'run' || phase === 'observe'
        ? {
            runId: 'workflow-e2e-1',
            status: runFailed ? 'FAILED' : 'SUCCEEDED',
            platform: 'kernel',
            startedAt: '2026-05-26T16:00:00.000Z',
            completedAt: '2026-05-26T16:03:00.000Z',
            traceId: 'trace-kernel-handoff',
            evidenceIds: ['kernel-intent-evidence'],
            rollbackTarget: runFailed ? 'release-2026.05.25' : '',
            promoteTarget: runFailed ? '' : 'production',
          }
        : undefined,
    availableActions: [
      {
        actionId,
        label: `phaseAction.${actionId}.label`,
        description: `phaseAction.${actionId}.description`,
        enabled: !blocked && !degraded && !unauthorized,
        requiredPermission: 'project:write',
        category: 'phase-transition',
        severity: 'high',
        confirmationRequired: true,
        idempotencyKey: `${phase}-primary`,
        auditType: `phase.${phase}.primary.requested`,
        targetType: 'server',
        serverOperation: phase === 'run' && runFailed ? 'run.retry' : 'phase.advance',
        parameters:
          phase === 'run'
            ? {
                runId: 'workflow-e2e-1',
                rollbackTarget: 'release-2026.05.25',
                targetEnvironment: 'staging',
              }
            : {},
      },
    ],
    dashboardActions: {
      primaryAction: actionId,
      blockedActions: blocked || degraded || unauthorized ? [actionId] : [],
      reviewRequiredActions: phase === 'generate' ? [actionId] : [],
      safeToContinueActions: blocked || degraded || unauthorized ? [] : [actionId],
    },
    phasePanels: [
      {
        phase,
        status: blocked ? 'blocked' : degraded ? 'degraded' : 'healthy',
        summary: `${upperPhase} backend panel`,
        recommendation:
          phase === 'learn'
            ? 'Approve learning recommendation'
            : phase === 'evolve'
              ? 'Approve evolution proposal'
              : 'Proceed with lifecycle action',
        owner: 'YAPPC',
        confidence: blocked ? 0.54 : 0.92,
        supportTrace: `backend:${phase}-model`,
        cards: [
          {
            id: `${phase}-state`,
            title: `${upperPhase} state`,
            detail: blocked ? 'Blocked by policy' : 'Ready',
            status: blocked ? 'blocked' : 'ready',
            trace: `card:${phase}:state`,
            metadata: {},
          },
        ],
      },
    ],
    healthSignals: {
      preview: {
        isHealthy: !degraded,
        status: degraded ? 'degraded' : 'healthy',
        issues: degraded ? ['Dependency unavailable'] : [],
      },
      generation: {
        isHealthy: !degraded,
        status: degraded ? 'degraded' : 'healthy',
        issues: degraded ? ['Dependency unavailable'] : [],
      },
      runtime: {
        isHealthy: !degraded && !runFailed,
        status: runFailed ? 'failed' : degraded ? 'degraded' : 'healthy',
        issues: runFailed ? ['Runtime run failed'] : degraded ? ['Dependency unavailable'] : [],
      },
      agentGovernance: {
        isHealthy: !degraded,
        status: degraded ? 'degraded' : 'healthy',
        governanceState: phase === 'learn' ? 'approval-required' : 'approved',
        learningLevel: phase === 'learn' ? 'adaptive' : 'none',
        evidenceIds: ['learning-evidence-1'],
        issues: degraded ? ['Agent governance source unavailable'] : [],
      },
    },
    degradedDetails: degraded
      ? {
          dependency: options.degradedDependency,
          reason: `${options.degradedDependency}_DEPENDENCY_UNAVAILABLE`,
          truthSource: `${String(options.degradedDependency).toLowerCase()}_truth`,
          recoveryAction: `Recover ${options.degradedDependency} dependency before mutating lifecycle state.`,
          impactedFeatures: ['phase-advance', 'run-actions'],
        }
      : undefined,
    timestamp: new Date('2026-05-26T16:00:00.000Z').getTime(),
    correlationId: `corr-${phase}`,
  };
}

export async function setupLifecycleJourneyApi(page: Page, options: FixtureOptions = {}): Promise<void> {
  const project = {
    id: PROJECT_ID,
    name: 'Golden Path Project',
    description: 'Round 7 lifecycle journey fixture',
    workspaceId: WORKSPACE_ID,
    ownerWorkspaceId: WORKSPACE_ID,
    lifecyclePhase: 'GENERATE',
    currentPhase: 'GENERATE',
    status: 'ACTIVE',
    aiHealthScore: 92,
    aiNextActions: ['Review generated diff'],
    createdAt: '2026-05-06T10:00:00.000Z',
    updatedAt: '2026-05-06T11:00:00.000Z',
    lastActivityAt: '2026-05-06T11:00:00.000Z',
  };

  await page.addInitScript(() => {
    document.cookie = 'accessToken=e2e-access-token; path=/; SameSite=Lax';
    window.localStorage.setItem('onboarding_complete', JSON.stringify('true'));
    window.localStorage.setItem('yappc:currentWorkspaceId', JSON.stringify('ws-e2e'));
  });

  await page.route(/\/api(?:\/api)?\/auth\/validate(?:\?|$)/, (route) =>
    fulfillJson(route, { valid: true }),
  );
  await page.route(/\/api(?:\/api)?\/auth\/me(?:\?|$)/, (route) =>
    fulfillJson(route, {
      id: USER_ID,
      email: 'operator@example.com',
      name: 'Operator',
      role: options.unauthorized ? 'VIEWER' : 'ADMIN',
      tenantId: TENANT_ID,
      workspaceIds: [WORKSPACE_ID],
    }),
  );
  await page.route(/\/api(?:\/api)?\/onboarding\/status(?:\?|$)/, (route) =>
    fulfillJson(route, {
      completed: true,
      primaryPersona: 'developer',
      activePersonas: ['developer'],
    }),
  );
  await page.route(/\/api(?:\/api)?\/workspaces(?:\?|$)/, (route) =>
    fulfillJson(route, {
      workspaces: [
        {
          id: WORKSPACE_ID,
          name: 'Golden Workspace',
          description: 'Workspace used by round7 lifecycle fixtures',
          ownerId: USER_ID,
          projectCount: 1,
          memberCount: 1,
          createdAt: '2026-05-06T10:00:00.000Z',
          updatedAt: '2026-05-06T11:00:00.000Z',
        },
      ],
    }),
  );
  await page.route(new RegExp(`/api(?:/api)?/workspaces/${WORKSPACE_ID}(?:\\?|$)`), (route) =>
    fulfillJson(route, {
      workspace: {
        id: WORKSPACE_ID,
        name: 'Golden Workspace',
        description: 'Workspace used by round7 lifecycle fixtures',
        ownerId: USER_ID,
        ownedProjects: [project],
        includedProjects: [],
        createdAt: '2026-05-06T10:00:00.000Z',
        updatedAt: '2026-05-06T11:00:00.000Z',
      },
    }),
  );
  await page.route(new RegExp(`/api(?:/api)?/projects\\?workspaceId=${WORKSPACE_ID}$`), (route) =>
    fulfillJson(route, { owned: [project], included: [] }),
  );
  await page.route(new RegExp(`/api(?:/api)?/projects/${PROJECT_ID}(?:\\?|$)`), (route) =>
    fulfillJson(route, { project }),
  );
  await page.route(new RegExp(`/api(?:/api)?/projects/${PROJECT_ID}/current(?:\\?|$)`), (route) =>
    fulfillJson(route, project),
  );
  await page.route(new RegExp(`/api(?:/api)?/projects/${PROJECT_ID}/activity(?:\\?|$)`), (route) =>
    fulfillJson(route, {
      projectId: PROJECT_ID,
      activity: [
        {
          id: 'audit-e2e-1',
          source: 'audit',
          action: 'PROJECT_UPDATED',
          summary: 'Lifecycle fixture project updated',
          timestamp: '2026-05-06T11:05:00.000Z',
          actor: USER_ID,
          success: true,
        },
      ],
    }),
  );
  await page.route(new RegExp(`/api(?:/api)?/projects/${PROJECT_ID}/artifacts(?:\\?|$)`), (route) =>
    fulfillJson(route, []),
  );
  await page.route(new RegExp(`/api(?:/api)?/projects/${PROJECT_ID}/runs\\?limit=10$`), (route) =>
    fulfillJson(route, [
      {
        runId: 'workflow-e2e-1',
        status: options.runFailure ? 'FAILED' : 'SUCCEEDED',
        phase: 'run',
      },
    ]),
  );

  await page.route(/\/api(?:\/api)?\/v1\/phase\/packet(?:\?|$)/, (route) => {
    const requestUrl = new URL(route.request().url());
    const phase = requestUrl.searchParams.get('phase') as LifecyclePhase | null;
    if (!phase || !(phase in nextPhaseByPhase)) {
      return fulfillJson(route, { message: 'Unknown lifecycle phase' }, 400);
    }
    return fulfillJson(route, buildPhasePacket(phase, options));
  });

  await page.route('**/api/v1/lifecycle/advance', (route) =>
    fulfillJson(route, { success: true, currentPhase: 'GENERATE', errors: [] }),
  );
  await page.route('**/api/v1/yappc/generate/product-unit-intent', (route) =>
    fulfillJson(route, {
      intentId: 'pui-e2e-1',
      status: 'VALID',
      valid: true,
      validationErrors: [],
      projectId: PROJECT_ID,
      workspaceId: WORKSPACE_ID,
      tenantId: TENANT_ID,
      traceId: 'trace-kernel-handoff',
      evidenceIds: ['kernel-intent-evidence'],
    }),
  );
  await page.route('**/api/v1/workflows/yappc-run/start', (route) =>
    fulfillJson(route, { runId: 'workflow-e2e-1', templateId: 'yappc-run', status: 'RUNNING' }, 202),
  );
  await page.route('**/api/v1/workflows/workflow-e2e-1/status', (route) =>
    fulfillJson(route, { runId: 'workflow-e2e-1', templateId: 'yappc-run', status: 'RUNNING' }),
  );
}

export async function bootstrapLifecycleProject(page: Page): Promise<void> {
  await page.goto('/workspaces');
  await page.getByRole('heading', { name: /Workspaces/i }).first().waitFor();

  const workspaceOpenButton = page.getByRole('button', { name: /^Open$/ }).first();
  if (await workspaceOpenButton.isVisible()) {
    await workspaceOpenButton.click();
  } else {
    await page.getByRole('heading', { name: 'Golden Workspace' }).click();
  }

  try {
    await page.waitForURL(/\/projects$/, { timeout: 5000 });
  } catch {
    await page.goto(`/workspaces/${WORKSPACE_ID}/projects`);
    await page.waitForURL(/\/projects$/);
  }

  await page.goto(`/p/${PROJECT_ID}`);
  try {
    await page.waitForURL(new RegExp(`/p/${PROJECT_ID}`), { timeout: 5000 });
  } catch {
    await page.goto(`/workspaces/${WORKSPACE_ID}/projects`);
    await page.waitForURL(/\/projects$/);
    await page.goto(`/p/${PROJECT_ID}`);
    await page.waitForURL(new RegExp(`/p/${PROJECT_ID}`));
  }
}

export async function gotoLifecyclePhase(page: Page, phase: LifecyclePhase): Promise<void> {
  const targetPath = `/p/${PROJECT_ID}/${phase}`;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await page.goto(targetPath);
    if (page.url().includes(targetPath)) {
      return;
    }
    await bootstrapLifecycleProject(page);
  }

  throw new Error(`Unable to navigate to lifecycle phase route: ${targetPath}`);
}
