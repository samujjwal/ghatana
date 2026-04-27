/**
 * E2E Test Suite - Requirement Capture Automation
 *
 * Covers AI-assisted requirement capture, automation trigger feedback,
 * and orchestration submission on approval.
 *
 * @doc.type e2e-spec
 * @doc.purpose Validate requirement capture automation journey
 * @doc.layer product
 * @doc.pattern Playwright E2E
 */

import { expect, test } from '@playwright/test';

const PROJECT_ID = 'e2e-project-requirement-capture';

const mockRecommendations = [
  {
    id: 'rec-auth-1',
    type: 'task',
    title: 'Capture authentication requirement baseline',
    description: 'Define acceptance criteria for login, logout, and session renewal.',
    confidence: 0.92,
    phase: 'REQUIREMENTS',
    flowStage: 1,
    persona: 'ProductOwner',
    priority: 'HIGH',
    actionable: true,
  },
  {
    id: 'rec-risk-2',
    type: 'insight',
    title: 'Add threat-model evidence for auth flow',
    description: 'Attach risk assessment artifacts before approval transition.',
    confidence: 0.84,
    phase: 'REQUIREMENTS',
    flowStage: 1,
    persona: 'SecurityLead',
    priority: 'MEDIUM',
    actionable: true,
  },
];

const mockNextTask = {
  id: 'task-req-capture-1',
  title: 'Generate acceptance criteria draft',
  description: 'Use AI to generate requirement acceptance criteria from captured context.',
  phase: 'REQUIREMENTS',
  flowStage: 1,
  persona: 'ProductOwner',
  priority: 'high',
  status: 'pending',
};

const mockAutomationPlan = {
  projectId: PROJECT_ID,
  currentPhase: 'REQUIREMENTS',
  nextPhase: 'DESIGN',
  canAutoAdvance: true,
  readiness: 78,
  blockers: [],
  estimatedReadyIn: '2h',
  estimatedReadyInHours: 2,
  predictionConfidence: 0.81,
  decisionSupport: {
    defaults: {
      approvalMode: 'auto_with_audit',
      riskTolerance: 'medium',
      validationDepth: 'standard',
      targetEnvironment: 'staging',
      ownerRole: 'product_owner',
    },
    suggestions: [
      {
        id: 'suggestion-1',
        title: 'Attach requirement rationale',
        reasoning: 'Improves downstream traceability for review handoff.',
        impact: 'medium',
      },
    ],
    progressiveDisclosure: {
      primaryActions: ['Apply guided promotion'],
      secondaryActions: ['Show details'],
    },
  },
  execution: null,
  generatedAt: '2026-04-27T10:00:00.000Z',
};

test.describe('Requirement Capture Automation', () => {
  let orchestrationCalls = 0;

  test.beforeEach(async ({ page }) => {
    orchestrationCalls = 0;

    await page.route('**/api/workspaces', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    );

    await page.route('**/api/onboarding/status', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ completed: true }),
      })
    );

    await page.route(`**/api/projects/${PROJECT_ID}/ai/recommendations`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockRecommendations),
      })
    );

    await page.route(`**/api/projects/${PROJECT_ID}/ai/insights`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    );

    await page.route(`**/api/projects/${PROJECT_ID}/tasks/next**`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockNextTask),
      })
    );

    await page.route('**/api/tasks/task-req-capture-1/execute', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          taskId: 'task-req-capture-1',
          status: 'queued',
          message: 'Task queued for execution. CI/CD adapter not yet connected.',
        }),
      })
    );

    await page.route(`**/api/projects/${PROJECT_ID}/automation/plan`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockAutomationPlan),
      })
    );

    await page.route('**/api/devsecops/anomaly-alerts', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    );

    await page.route('**/api/v1/agents/requirement-orchestration-agent/execute', (route) => {
      orchestrationCalls += 1;
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          runId: 'run-requirement-1',
          agentId: 'requirement-orchestration-agent',
          status: 'QUEUED',
          createdAt: '2026-04-27T10:10:00.000Z',
        }),
      });
    });

    await page.addInitScript(() => {
      try {
        localStorage.setItem('E2E_DISABLE_OVERLAYS', '1');
        localStorage.setItem('E2E_SIMPLE_PAGES', '1');
        localStorage.setItem('onboarding_complete', '"true"');
        (window as unknown as { __E2E_TEST_NO_POINTER_BLOCK?: boolean }).__E2E_TEST_NO_POINTER_BLOCK = true;
      } catch {
        // no-op
      }
    });
  });

  test('renders AI-assisted requirement capture context', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const recommendationsCard = page.getByTestId('lifecycle-recommendations-card');
    await expect(recommendationsCard).toBeVisible({ timeout: 10_000 });
    await expect(recommendationsCard.getByText('Capture authentication requirement baseline')).toBeVisible();

    const requirementsCard = page.getByTestId('lifecycle-requirements-card');
    await expect(requirementsCard).toBeVisible({ timeout: 10_000 });
    await expect(requirementsCard.getByText('Capture authentication requirement baseline')).toBeVisible();
  });

  test('automation trigger shows queued feedback', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const automationCard = page.getByTestId('lifecycle-workflow-automation-card');
    await expect(automationCard).toBeVisible({ timeout: 10_000 });

    await automationCard.getByTestId('workflow-automation-trigger').click();

    await expect(automationCard.getByTestId('workflow-automation-feedback')).toContainText(
      'Task queued for execution. CI/CD adapter not yet connected.'
    );
  });

  test('approval transition triggers orchestration submission', async ({ page }) => {
    await page.goto(`/p/${PROJECT_ID}/lifecycle`, { waitUntil: 'networkidle' });

    const approvalCard = page.getByTestId('lifecycle-approval-card');
    await expect(approvalCard).toBeVisible({ timeout: 10_000 });

    const approveButton = approvalCard.getByRole('button', { name: /approve/i }).first();
    await expect(approveButton).toBeVisible();
    await approveButton.click();

    await expect(approvalCard.getByText('APPROVED').first()).toBeVisible({ timeout: 5_000 });
    await expect.poll(() => orchestrationCalls).toBe(1);
  });
});
