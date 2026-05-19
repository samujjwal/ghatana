/**
 * Shared E2E test fixtures and mock data.
 *
 * All API responses are routed through page.route() so no real backend is needed.
 */
import type { Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

type ValidRole = 'viewer' | 'brand-manager' | 'marketing-director' | 'exec-sponsor' | 'admin';

interface DmosRouteCapability {
  readonly path: string;
  readonly label: string;
  readonly minimumRole: ValidRole;
  readonly personas?: readonly string[];
  readonly tiers?: readonly string[];
  readonly actions?: readonly string[];
  readonly cards?: readonly string[];
  readonly capabilityKey?: string;
}

export const TEST_WORKSPACE = 'ws-e2e';
export const TEST_TENANT = 'tenant-e2e';
export const TEST_PRINCIPAL = 'user-e2e';
export const TEST_TOKEN = 'e2e-test-token';

const DMOS_ROLE_ORDER: Readonly<Record<ValidRole, number>> = {
  viewer: 0,
  'brand-manager': 1,
  'marketing-director': 2,
  'exec-sponsor': 3,
  admin: 4,
};

const dmosRouteManifest = JSON.parse(
  readFileSync(new URL('../../dm-api/src/main/resources/contracts/dmos-route-capabilities.json', import.meta.url), 'utf8'),
).routes as readonly DmosRouteCapability[];

export const APPROVAL_PENDING = {
  requestId: 'req-e2e-1',
  tenantId: TEST_TENANT,
  workspaceId: TEST_WORKSPACE,
  targetType: 'STRATEGY',
  targetId: 'strategy-e2e-abc',
  description: 'Q3 digital marketing expansion strategy',
  riskLevel: 3,
  requiredApproverRole: 'marketing-director',
  status: 'PENDING',
  submittedAt: '2026-01-10T09:00:00Z',
  submittedBy: 'submitter-1',
  decidedAt: null,
  decidedBy: null,
  comment: null,
  snapshotSummary: 'Q3 digital marketing expansion strategy',
  validationResultId: null,
  snapshotAt: '2026-01-10T08:59:00Z',
};

export const APPROVAL_APPROVED = {
  requestId: 'req-e2e-2',
  tenantId: TEST_TENANT,
  workspaceId: TEST_WORKSPACE,
  targetType: 'BUDGET',
  targetId: 'budget-e2e-xyz',
  description: 'Q3 budget approval',
  riskLevel: 2,
  requiredApproverRole: 'brand-manager',
  status: 'APPROVED',
  submittedAt: '2026-01-08T14:00:00Z',
  submittedBy: 'submitter-2',
  decidedAt: '2026-01-09T10:00:00Z',
  decidedBy: 'reviewer-1',
  comment: 'Looks good.',
  snapshotSummary: 'Q3 budget proposal',
  validationResultId: null,
  snapshotAt: '2026-01-08T13:59:00Z',
};

export const APPROVAL_SNAPSHOT = {
  requestId: 'req-e2e-1',
  targetType: 'STRATEGY',
  targetId: 'strategy-e2e-abc',
  targetWorkspaceId: TEST_WORKSPACE,
  snapshotSummary: 'Q3 digital marketing expansion strategy',
  validationResultId: null,
  riskLevel: 3,
  requiredApproverRole: 'marketing-director',
  snapshotAt: '2026-01-10T08:59:00Z',
};

export const AI_ACTION = {
  actionId: 'ai-e2e-1',
  workspaceId: TEST_WORKSPACE,
  tenantId: TEST_TENANT,
  actionType: 'STRATEGY_GENERATION',
  description: 'Generated Q3 expansion strategy',
  status: 'COMPLETED',
  confidence: 0.87,
  triggeredAt: '2026-01-10T09:00:00Z',
  completedAt: '2026-01-10T09:00:05Z',
};

export const CAMPAIGN = {
  id: 'camp-e2e-1',
  workspaceId: TEST_WORKSPACE,
  name: 'E2E Search Launch',
  type: 'PAID_SEARCH',
  objective: 'LEADS',
  status: 'DRAFT',
  budgetCents: 50000,
  audience: 'SMB owners in California',
  landingPageUrl: 'https://example.com/landing',
  startDate: '2026-05-12',
  endDate: '2026-06-12',
  createdAt: '2026-05-11T12:00:00Z',
  updatedAt: '2026-05-11T12:00:00Z',
};

export const MARKETING_STRATEGY = {
  strategyId: 'strat-e2e-1',
  workspaceId: TEST_WORKSPACE,
  status: 'DRAFT',
  goals: [],
  channelPlans: [],
  budgetCap: 5000,
  rationale: 'E2E strategy fixture',
  assumptions: 'Fixture only',
  measurementPlan: 'Weekly review',
  contentPlan: 'Search landing page copy',
  modelVersion: 'strategy-v1',
  generatedAt: '2026-01-10T10:00:00Z',
  generatedBy: TEST_PRINCIPAL,
  approvedAt: null,
  approvedBy: null,
};

export const BUDGET_RECOMMENDATION = {
  recommendationId: 'budget-e2e-1',
  workspaceId: TEST_WORKSPACE,
  strategyId: MARKETING_STRATEGY.strategyId,
  status: 'DRAFT',
  totalMonthlyCap: 5000,
  changeThresholdPct: 10,
  channelAllocations: [],
  rationale: 'E2E budget fixture',
  assumptions: 'Fixture only',
  modelVersion: 'budget-v1',
  generatedAt: '2026-01-10T10:00:00Z',
  generatedBy: TEST_PRINCIPAL,
  approvedAt: null,
  approvedBy: null,
};

export const ENABLED_CAPABILITIES = {
  workspaceId: TEST_WORKSPACE,
  capabilities: [
    { key: 'dmos.campaigns', enabled: true },
    { key: 'dmos.strategy', enabled: true },
    { key: 'dmos.budget', enabled: true },
    { key: 'dmos.approvals', enabled: true },
    { key: 'dmos.ai_actions', enabled: true },
  ],
  lastUpdated: '2026-01-10T09:00:00Z',
};

function isValidRole(value: string | null | undefined): value is ValidRole {
  return (
    value === 'viewer' ||
    value === 'brand-manager' ||
    value === 'marketing-director' ||
    value === 'exec-sponsor' ||
    value === 'admin'
  );
}

/**
 * Mock all DMOS API routes needed for E2E tests.
 * Call this in beforeEach for tests that need authenticated state.
 */
export async function mockDmosApi(page: Page): Promise<void> {
  let approvalState = { ...APPROVAL_PENDING };

  await page.route('**/v1/route-entitlements**', (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const requestedRole = request.headers()['x-role'] ?? url.searchParams.get('role');
    const role: ValidRole = isValidRole(requestedRole) ? requestedRole : 'viewer';
    const routes = dmosRouteManifest.filter((entry) => {
      const requiredRole = entry.minimumRole as ValidRole | undefined;
      return requiredRole === undefined || DMOS_ROLE_ORDER[role] >= DMOS_ROLE_ORDER[requiredRole];
    });

    return route.fulfill({
      json: {
        product: 'digital-marketing',
        principalId: TEST_PRINCIPAL,
        tenantId: TEST_TENANT,
        role,
        persona: role,
        tier: role === 'viewer' ? 'read-only' : 'workspace',
        routes,
        actions: routes.flatMap((entry) =>
          (entry.actions ?? []).map((action) => ({
            id: action,
            label: action.replace(/-/g, ' '),
            routePath: entry.path,
          })),
        ),
        cards: routes.flatMap((entry) =>
          (entry.cards ?? []).map((card) => ({
            id: card,
            title: card.replace(/-/g, ' '),
            routePath: entry.path,
            surface: 'dashboard',
          })),
        ),
      },
    });
  });

  // Workspace capabilities
  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/capabilities`,
    (route) => route.fulfill({ json: ENABLED_CAPABILITIES }),
  );

  // Approval list
  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/approvals/pending/**`,
    (route) => route.fulfill({ json: { items: [approvalState, APPROVAL_APPROVED] } }),
  );

  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/approvals/pending`,
    (route) => route.fulfill({ json: { items: [approvalState, APPROVAL_APPROVED] } }),
  );

  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/campaigns**`,
    (route) => route.fulfill({ json: { items: [CAMPAIGN], count: 1, offset: 0, limit: 20 } }),
  );

  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/strategy`,
    (route) => route.fulfill({ json: MARKETING_STRATEGY }),
  );

  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/budget-recommendation`,
    (route) => route.fulfill({ json: BUDGET_RECOMMENDATION }),
  );

  // Approval status (single)
  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/approvals/req-e2e-1`,
    (route) => route.fulfill({ json: approvalState }),
  );

  // Approval snapshot
  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/approvals/req-e2e-1/snapshot`,
    (route) => route.fulfill({ json: APPROVAL_SNAPSHOT }),
  );

  // Approve/reject decision
  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/approvals/req-e2e-1/decide`,
    (route) => {
      const bodyText = route.request().postData() ?? '{}';
      const parsed = JSON.parse(bodyText) as { decision?: 'APPROVE' | 'REJECT'; notes?: string };
      const status = parsed.decision === 'REJECT' ? 'REJECTED' : 'APPROVED';
      approvalState = {
        ...approvalState,
        status,
        decidedBy: TEST_PRINCIPAL,
        decidedAt: new Date().toISOString(),
        comment: parsed.notes ?? null,
      };

      return route.fulfill({ json: approvalState });
    },
  );

  // AI action log
  await page.route(
    `**/v1/workspaces/${TEST_WORKSPACE}/ai-actions**`,
    (route) => route.fulfill({ json: { items: [AI_ACTION] } }),
  );
}

/**
 * Fill the login form and submit.
 */
export async function loginAs(
  page: Page,
  opts: {
    token?: string;
    workspaceId?: string;
    tenantId?: string;
    principalId?: string;
    sessionId?: string;
    roles?: string[];
  } = {},
): Promise<void> {
  const {
    token = TEST_TOKEN,
    workspaceId = TEST_WORKSPACE,
    tenantId = TEST_TENANT,
    principalId = TEST_PRINCIPAL,
    sessionId = crypto.randomUUID(),
    roles = [],
  } = opts;

  await page.goto('/login');
  await page.evaluate(
    ({ sessionId, roles }) => {
      window.sessionStorage.setItem('dmos_session_id', sessionId);
      window.sessionStorage.setItem('dmos_roles', JSON.stringify(roles));
    },
    { sessionId, roles },
  );
  await page.fill('[data-testid="login-token"]', token);
  await page.fill('[data-testid="login-workspace-id"]', workspaceId);
  await page.fill('[data-testid="login-tenant-id"]', tenantId);
  await page.fill('[data-testid="login-principal-id"]', principalId);
  await page.click('[data-testid="login-submit"]');
}

/**
 * Navigate inside the already-authenticated SPA without a full document reload.
 *
 * DMOS intentionally keeps bearer tokens in runtime memory only; using page.goto()
 * after login would reload the app and correctly drop that token.
 */
export async function navigateInApp(page: Page, path: string): Promise<void> {
  await page.evaluate((targetPath) => {
    window.history.pushState(null, '', targetPath);
    window.dispatchEvent(new PopStateEvent('popstate'));
  }, path);
}
