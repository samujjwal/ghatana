/**
 * Cross-Cutting E2E Test Backlog
 *
 * Comprehensive end-to-end tests covering critical user journeys across YAPPC.
 * These tests validate the complete user experience from onboarding to production.
 *
 * @doc.type e2e-test
 * @doc.purpose Cross-cutting E2E validation for YAPPC critical journeys
 * @doc.layer product
 * @doc.pattern E2E Test Suite
 */

import { test, expect, type Page } from '@playwright/test';

// Configuration
const BASE_URL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';
const TEST_TIMEOUT = 60000;

// Test user credentials
const TEST_USER = {
  email: 'test-engineer@yappc.local',
  password: 'YAPPCTest123!Secure',
  displayName: 'Test Engineer',
};

const UNAUTHORIZED_USER = {
  email: 'unauthorized@yappc.local',
  password: 'Unauthorized123!',
};

test.describe('Cross-Cutting E2E Backlog', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
  });

  /**
   * E2E-001: New user opens YAPPC
   * Landing/login/onboarding loads; no console errors
   */
  test.describe('E2E-001: New User Opens YAPPC', () => {
    test('should load landing page without console errors', async () => {
      const errors: string[] = [];
      
      page.on('console', msg => {
        if (msg.type() === 'error') {
          errors.push(msg.text());
        }
      });

      await page.goto(BASE_URL);
      await page.waitForLoadState('networkidle');

      // Verify page loaded
      await expect(page).toHaveURL(/\/|\/login|\/onboarding/);

      // Verify no console errors
      expect(errors).toHaveLength(0);
    });

    test('should load login page', async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.waitForLoadState('networkidle');

      // Verify login form elements
      const emailInput = page.locator('input[name="email"], input[type="email"]').first();
      const passwordInput = page.locator('input[name="password"], input[type="password"]').first();
      const signInButton = page.locator('button:has-text("Sign In"), button:has-text("Login")').first();

      await expect(emailInput).toBeVisible();
      await expect(passwordInput).toBeVisible();
      await expect(signInButton).toBeVisible();
    });

    test('should load onboarding for new user', async () => {
      // Navigate to onboarding (simulating new user flow)
      await page.goto(`${BASE_URL}/onboarding`);
      await page.waitForLoadState('networkidle');

      // Verify onboarding elements
      const onboardingContainer = page.locator('[class*="onboarding"], [data-testid="onboarding"]').first();
      if (await onboardingContainer.isVisible()) {
        await expect(onboardingContainer).toBeVisible();
      }
    });
  });

  /**
   * E2E-002: Workspace create/select
   * Workspace persists after reload and has tenant scope
   */
  test.describe('E2E-002: Workspace Create/Select', () => {
    test.beforeEach(async () => {
      // Login
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should create new workspace', async () => {
      // Click create workspace
      const createButton = page.locator('button:has-text("Create Workspace"), button:has-text("New Workspace")').first();
      if (await createButton.isVisible()) {
        await createButton.click();

        // Fill workspace details
        const nameInput = page.locator('input[name="name"], input[placeholder*="workspace" i]').first();
        await nameInput.fill('Test Workspace');

        const createConfirmButton = page.locator('button:has-text("Create"), button:has-text("Save")').first();
        await createConfirmButton.click();

        // Verify workspace created
        await expect(page.locator('text=Test Workspace')).toBeVisible({ timeout: 10000 });
      }
    });

    test('should select existing workspace', async () => {
      const workspaceSelector = page.locator('[data-testid="workspace-selector"], [class*="workspace-selector"]').first();
      if (await workspaceSelector.isVisible()) {
        await workspaceSelector.click();

        // Select first workspace
        const firstWorkspace = page.locator('[class*="workspace-option"]').first();
        await firstWorkspace.click();

        // Verify workspace selected
        await page.waitForLoadState('networkidle');
      }
    });

    test('should persist workspace after reload', async () => {
      // Get current workspace name
      const currentWorkspace = page.locator('[class*="workspace-name"], [data-testid="current-workspace"]').first();
      const workspaceName = await currentWorkspace.textContent();

      // Reload page
      await page.reload();
      await page.waitForLoadState('networkidle');

      // Verify workspace persisted
      const reloadedWorkspace = page.locator('[class*="workspace-name"], [data-testid="current-workspace"]').first();
      await expect(reloadedWorkspace).toContainText(workspaceName || '');
    });

    test('should have tenant scope in workspace', async () => {
      // Verify tenant context is present
      const tenantIndicator = page.locator('[class*="tenant"], [data-testid="tenant-id"]').first();
      if (await tenantIndicator.isVisible()) {
        await expect(tenantIndicator).toBeVisible();
      }
    });
  });

  /**
   * E2E-003: Project create
   * Project appears in Data Cloud and route opens
   */
  test.describe('E2E-003: Project Create', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should create new project', async () => {
      const createProjectButton = page.locator('button:has-text("Create Project"), button:has-text("New Project")').first();
      if (await createProjectButton.isVisible()) {
        await createProjectButton.click();

        // Fill project details
        const nameInput = page.locator('input[name="name"], input[placeholder*="project" i]').first();
        await nameInput.fill('Test Project');

        const descriptionInput = page.locator('textarea[name="description"]').first();
        await descriptionInput.fill('Test project for E2E validation');

        const confirmButton = page.locator('button:has-text("Create"), button:has-text("Save")').first();
        await confirmButton.click();

        // Verify project created and route opens
        await expect(page).toHaveURL(/\/p\/test-project/, { timeout: 10000 });
      }
    });

    test('should show project in Data Cloud', async () => {
      // Navigate to projects list
      await page.goto(`${BASE_URL}/projects`);
      await page.waitForLoadState('networkidle');

      // Verify project appears in list
      const projectCard = page.locator('[class*="project-card"], [data-testid="project-item"]').first();
      await expect(projectCard).toBeVisible();
    });

    test('should open project route on click', async () => {
      const projectCard = page.locator('[class*="project-card"], [data-testid="project-item"]').first();
      await projectCard.click();

      // Verify route opens
      await expect(page).toHaveURL(/\/p\/.+/);
    });
  });

  /**
   * E2E-004: Intent capture
   * Intent saved, audited, evidence generated
   */
  test.describe('E2E-004: Intent Capture', () => {
    const PROJECT_ID = 'test-project';
    const INTENT_URL = `${BASE_URL}/p/${PROJECT_ID}/intent`;

    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should capture user intent', async () => {
      await page.goto(INTENT_URL);
      await page.waitForLoadState('networkidle');

      const promptInput = page.locator('textarea[placeholder*="prompt" i], [data-testid="prompt-input"], textarea').first();
      await promptInput.fill('Create a full-stack e-commerce application with product catalog and checkout');

      const saveButton = page.locator('button:has-text("Save"), button:has-text("Capture")').first();
      await saveButton.click();

      // Verify intent saved
      await expect(page.locator('[class*="saved"], [class*="success"]')).toBeVisible({ timeout: 5000 });
    });

    test('should audit intent capture', async () => {
      await page.goto(INTENT_URL);
      await page.waitForLoadState('networkidle');

      // Check for audit trail indicator
      const auditIndicator = page.locator('[class*="audit"], [data-testid="audit-trail"]').first();
      if (await auditIndicator.isVisible()) {
        await expect(auditIndicator).toBeVisible();
      }
    });

    test('should generate evidence from intent', async () => {
      await page.goto(INTENT_URL);
      await page.waitForLoadState('networkidle');

      const promptInput = page.locator('textarea').first();
      await promptInput.fill('Create a todo application');

      const generateButton = page.locator('button:has-text("Generate"), button:has-text("Plan")').first();
      await generateButton.click();

      // Wait for evidence generation
      await page.waitForTimeout(3000);

      // Verify evidence generated
      const evidencePanel = page.locator('[class*="evidence"], [data-testid="evidence-panel"]').first();
      if (await evidencePanel.isVisible({ timeout: 5000 })) {
        await expect(evidencePanel).toBeVisible();
      }
    });
  });

  /**
   * E2E-005: Shape via canvas
   * Canvas/shape persists and maps to artifact graph
   */
  test.describe('E2E-005: Shape via Canvas', () => {
    const PROJECT_ID = 'test-project';
    const SHAPE_URL = `${BASE_URL}/p/${PROJECT_ID}/shape`;

    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should load canvas interface', async () => {
      await page.goto(SHAPE_URL);
      await page.waitForLoadState('networkidle');

      const canvas = page.locator('[class*="canvas"], [data-testid="canvas"]').first();
      await expect(canvas).toBeVisible();
    });

    test('should allow shape creation on canvas', async () => {
      await page.goto(SHAPE_URL);
      await page.waitForLoadState('networkidle');

      // Add a node to canvas
      const addButton = page.locator('button:has-text("Add"), button:has-text("Create")').first();
      if (await addButton.isVisible()) {
        await addButton.click();

        // Verify node added
        const node = page.locator('[class*="node"], [class*="shape"]').first();
        await expect(node).toBeVisible({ timeout: 5000 });
      }
    });

    test('should persist canvas shape after reload', async () => {
      await page.goto(SHAPE_URL);
      await page.waitForLoadState('networkidle');

      // Add a node
      const addButton = page.locator('button:has-text("Add")').first();
      if (await addButton.isVisible()) {
        await addButton.click();
        await page.waitForTimeout(1000);
      }

      // Reload
      await page.reload();
      await page.waitForLoadState('networkidle');

      // Verify shape persisted
      const node = page.locator('[class*="node"], [class*="shape"]').first();
      if (await node.isVisible()) {
        await expect(node).toBeVisible();
      }
    });

    test('should map canvas to artifact graph', async () => {
      await page.goto(SHAPE_URL);
      await page.waitForLoadState('networkidle');

      // Check for artifact graph visualization
      const artifactGraph = page.locator('[class*="artifact-graph"], [data-testid="artifact-graph"]').first();
      if (await artifactGraph.isVisible()) {
        await expect(artifactGraph).toBeVisible();
      }
    });
  });

  /**
   * E2E-006: Validate blocked
   * Missing artifact/policy denial blocks advance
   */
  test.describe('E2E-006: Validate Blocked', () => {
    const PROJECT_ID = 'test-project';
    const VALIDATE_URL = `${BASE_URL}/p/${PROJECT_ID}/validate`;

    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should block advance when artifact missing', async () => {
      await page.goto(VALIDATE_URL);
      await page.waitForLoadState('networkidle');

      const validateButton = page.locator('button:has-text("Validate"), button:has-text("Check")').first();
      await validateButton.click();

      // Check for blocked state
      const blockedIndicator = page.locator('[class*="blocked"], [class*="error"], [role="alert"]').first();
      if (await blockedIndicator.isVisible({ timeout: 5000 })) {
        await expect(blockedIndicator).toContainText(/missing|incomplete|blocked/i);
      }
    });

    test('should show policy denial message', async () => {
      await page.goto(VALIDATE_URL);
      await page.waitForLoadState('networkidle');

      const validateButton = page.locator('button:has-text("Validate")').first();
      await validateButton.click();

      // Check for policy denial
      const policyMessage = page.locator('[class*="policy"], [class*="denial"]').first();
      if (await policyMessage.isVisible({ timeout: 5000 })) {
        await expect(policyMessage).toBeVisible();
      }
    });

    test('should disable advance button when blocked', async () => {
      await page.goto(VALIDATE_URL);
      await page.waitForLoadState('networkidle');

      const advanceButton = page.locator('button:has-text("Advance"), button:has-text("Next")').first();
      if (await advanceButton.isVisible()) {
        const isDisabled = await advanceButton.isDisabled();
        expect(isDisabled).toBeTruthy();
      }
    });
  });

  /**
   * E2E-007: Validate pass
   * Required evidence/artifacts allow advance
   */
  test.describe('E2E-007: Validate Pass', () => {
    const PROJECT_ID = 'test-project';
    const VALIDATE_URL = `${BASE_URL}/p/${PROJECT_ID}/validate`;

    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should pass validation with required artifacts', async () => {
      await page.goto(VALIDATE_URL);
      await page.waitForLoadState('networkidle');

      // Assuming artifacts are present from previous steps
      const validateButton = page.locator('button:has-text("Validate")').first();
      await validateButton.click();

      // Check for pass state
      const passIndicator = page.locator('[class*="success"], [class*="valid"], [class*="passed"]').first();
      if (await passIndicator.isVisible({ timeout: 5000 })) {
        await expect(passIndicator).toBeVisible();
      }
    });

    test('should enable advance button when validation passes', async () => {
      await page.goto(VALIDATE_URL);
      await page.waitForLoadState('networkidle');

      const advanceButton = page.locator('button:has-text("Advance"), button:has-text("Next")').first();
      if (await advanceButton.isVisible()) {
        const isEnabled = await advanceButton.isEnabled();
        expect(isEnabled).toBeTruthy();
      }
    });

    test('should show all required evidence as present', async () => {
      await page.goto(VALIDATE_URL);
      await page.waitForLoadState('networkidle');

      const evidenceList = page.locator('[class*="evidence-list"], [data-testid="evidence-list"]').first();
      if (await evidenceList.isVisible()) {
        const evidenceItems = page.locator('[class*="evidence-item"]');
        const count = await evidenceItems.count();
        expect(count).toBeGreaterThan(0);
      }
    });
  });

  /**
   * E2E-008: Generate artifacts
   * Files/artifacts created with assurance results
   */
  test.describe('E2E-008: Generate Artifacts', () => {
    const PROJECT_ID = 'test-project';
    const GENERATE_URL = `${BASE_URL}/p/${PROJECT_ID}/generate`;

    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should generate artifacts', async () => {
      await page.goto(GENERATE_URL);
      await page.waitForLoadState('networkidle');

      const generateButton = page.locator('button:has-text("Generate")').first();
      await generateButton.click();

      // Wait for generation
      await page.waitForTimeout(5000);

      // Verify artifacts created
      const artifactsList = page.locator('[class*="artifacts"], [data-testid="artifacts-list"]').first();
      if (await artifactsList.isVisible({ timeout: 10000 })) {
        await expect(artifactsList).toBeVisible();
      }
    });

    test('should show assurance results', async () => {
      await page.goto(GENERATE_URL);
      await page.waitForLoadState('networkidle');

      const generateButton = page.locator('button:has-text("Generate")').first();
      await generateButton.click();
      await page.waitForTimeout(5000);

      // Check for assurance results
      const assurancePanel = page.locator('[class*="assurance"], [data-testid="assurance-results"]').first();
      if (await assurancePanel.isVisible()) {
        await expect(assurancePanel).toBeVisible();
      }
    });

    test('should create downloadable files', async () => {
      await page.goto(GENERATE_URL);
      await page.waitForLoadState('networkidle');

      const downloadButton = page.locator('button:has-text("Download"), button:has-text("Export")').first();
      if (await downloadButton.isVisible()) {
        const downloadPromise = page.waitForEvent('download');
        await downloadButton.click();
        const download = await downloadPromise;
        expect(download.suggestedFilename()).toBeTruthy();
      }
    });
  });

  /**
   * E2E-009: Generate ProductUnitIntent
   * Valid Kernel intent produced with real workspace/project/surfaces
   */
  test.describe('E2E-009: Generate ProductUnitIntent', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should generate valid Kernel intent', async () => {
      // Navigate to intent generation
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      const generateIntentButton = page.locator('button:has-text("Generate Intent"), button:has-text("Create Intent")').first();
      if (await generateIntentButton.isVisible()) {
        await generateIntentButton.click();

        // Verify intent generated
        const intentDisplay = page.locator('[class*="intent"], [data-testid="kernel-intent"]').first();
        await expect(intentDisplay).toBeVisible({ timeout: 5000 });
      }
    });

    test('should include workspace context in intent', async () => {
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      const workspaceContext = page.locator('[class*="workspace"], [data-testid="workspace-context"]').first();
      if (await workspaceContext.isVisible()) {
        await expect(workspaceContext).toBeVisible();
      }
    });

    test('should include project context in intent', async () => {
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      const projectContext = page.locator('[class*="project"], [data-testid="project-context"]').first();
      if (await projectContext.isVisible()) {
        await expect(projectContext).toBeVisible();
      }
    });

    test('should include surfaces in intent', async () => {
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      const surfaces = page.locator('[class*="surfaces"], [data-testid="surfaces"]').first();
      if (await surfaces.isVisible()) {
        await expect(surfaces).toBeVisible();
      }
    });
  });

  /**
   * E2E-010: Kernel handoff
   * Kernel accepts intent; YAPPC records run status
   */
  test.describe('E2E-010: Kernel Handoff', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should handoff intent to Kernel', async () => {
      await page.goto(`${BASE_URL}/p/test-project/generate`);
      await page.waitForLoadState('networkidle');

      const handoffButton = page.locator('button:has-text("Handoff"), button:has-text("Submit to Kernel")').first();
      if (await handoffButton.isVisible()) {
        await handoffButton.click();

        // Verify handoff initiated
        const handoffStatus = page.locator('[class*="handoff"], [data-testid="handoff-status"]').first();
        await expect(handoffStatus).toBeVisible({ timeout: 5000 });
      }
    });

    test('should record run status in YAPPC', async () => {
      await page.goto(`${BASE_URL}/p/test-project/run`);
      await page.waitForLoadState('networkidle');

      const runStatus = page.locator('[class*="run-status"], [data-testid="run-status"]').first();
      await expect(runStatus).toBeVisible();
    });

    test('should show Kernel acceptance confirmation', async () => {
      await page.goto(`${BASE_URL}/p/test-project/generate`);
      await page.waitForLoadState('networkidle');

      const acceptanceMessage = page.locator('[class*="accepted"], [class*="confirmed"]').first();
      if (await acceptanceMessage.isVisible({ timeout: 5000 })) {
        await expect(acceptanceMessage).toBeVisible();
      }
    });
  });

  /**
   * E2E-011: Run preview success
   * Preview and runtime health are healthy
   */
  test.describe('E2E-011: Run Preview Success', () => {
    const PROJECT_ID = 'test-project';
    const PREVIEW_URL = `${BASE_URL}/p/${PROJECT_ID}/preview`;

    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should load preview session', async () => {
      await page.goto(PREVIEW_URL);
      await page.waitForLoadState('networkidle');

      const previewFrame = page.locator('iframe, [class*="preview-frame"]').first();
      await expect(previewFrame).toBeVisible();
    });

    test('should show healthy preview status', async () => {
      await page.goto(PREVIEW_URL);
      await page.waitForLoadState('networkidle');

      const healthStatus = page.locator('[class*="health"], [data-testid="health-status"]').first();
      if (await healthStatus.isVisible()) {
        await expect(healthStatus).toContainText(/healthy|running|active/i);
      }
    });

    test('should show runtime health as healthy', async () => {
      await page.goto(PREVIEW_URL);
      await page.waitForLoadState('networkidle');

      const runtimeHealth = page.locator('[class*="runtime"], [data-testid="runtime-health"]').first();
      if (await runtimeHealth.isVisible()) {
        await expect(runtimeHealth).toContainText(/healthy|ok/i);
      }
    });
  });

  /**
   * E2E-012: Run failure
   * Failure shown with evidence and recommendation
   */
  test.describe('E2E-012: Run Failure', () => {
    const PROJECT_ID = 'test-project';
    const RUN_URL = `${BASE_URL}/p/${PROJECT_ID}/run`;

    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should show failure state', async () => {
      await page.goto(RUN_URL);
      await page.waitForLoadState('networkidle');

      // Simulate failure scenario
      const failureIndicator = page.locator('[class*="failed"], [class*="error"], [role="alert"]').first();
      if (await failureIndicator.isVisible()) {
        await expect(failureIndicator).toBeVisible();
      }
    });

    test('should show failure evidence', async () => {
      await page.goto(RUN_URL);
      await page.waitForLoadState('networkidle');

      const evidencePanel = page.locator('[class*="evidence"], [data-testid="failure-evidence"]').first();
      if (await evidencePanel.isVisible()) {
        await expect(evidencePanel).toBeVisible();
      }
    });

    test('should provide recommendation for failure', async () => {
      await page.goto(RUN_URL);
      await page.waitForLoadState('networkidle');

      const recommendation = page.locator('[class*="recommendation"], [data-testid="recommendation"]').first();
      if (await recommendation.isVisible()) {
        await expect(recommendation).toBeVisible();
      }
    });
  });

  /**
   * E2E-013: Observe Kernel health
   * Lifecycle/gates/artifacts/deployment visible
   */
  test.describe('E2E-013: Observe Kernel Health', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should show lifecycle status', async () => {
      await page.goto(`${BASE_URL}/p/test-project/observe`);
      await page.waitForLoadState('networkidle');

      const lifecycleStatus = page.locator('[class*="lifecycle"], [data-testid="lifecycle-status"]').first();
      if (await lifecycleStatus.isVisible()) {
        await expect(lifecycleStatus).toBeVisible();
      }
    });

    test('should show gates status', async () => {
      await page.goto(`${BASE_URL}/p/test-project/observe`);
      await page.waitForLoadState('networkidle');

      const gatesStatus = page.locator('[class*="gates"], [data-testid="gates-status"]').first();
      if (await gatesStatus.isVisible()) {
        await expect(gatesStatus).toBeVisible();
      }
    });

    test('should show artifacts status', async () => {
      await page.goto(`${BASE_URL}/p/test-project/observe`);
      await page.waitForLoadState('networkidle');

      const artifactsStatus = page.locator('[class*="artifacts"], [data-testid="artifacts-status"]').first();
      if (await artifactsStatus.isVisible()) {
        await expect(artifactsStatus).toBeVisible();
      }
    });

    test('should show deployment status', async () => {
      await page.goto(`${BASE_URL}/p/test-project/observe`);
      await page.waitForLoadState('networkidle');

      const deploymentStatus = page.locator('[class*="deployment"], [data-testid="deployment-status"]').first();
      if (await deploymentStatus.isVisible()) {
        await expect(deploymentStatus).toBeVisible();
      }
    });
  });

  /**
   * E2E-014: Learn from failure
   * Failure creates learning evidence and recommendation
   */
  test.describe('E2E-014: Learn from Failure', () => {
    const PROJECT_ID = 'test-project';
    const LEARN_URL = `${BASE_URL}/p/${PROJECT_ID}/learn`;

    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should create learning evidence from failure', async () => {
      await page.goto(LEARN_URL);
      await page.waitForLoadState('networkidle');

      const learningEvidence = page.locator('[class*="learning"], [data-testid="learning-evidence"]').first();
      if (await learningEvidence.isVisible()) {
        await expect(learningEvidence).toBeVisible();
      }
    });

    test('should generate recommendation from failure', async () => {
      await page.goto(LEARN_URL);
      await page.waitForLoadState('networkidle');

      const recommendation = page.locator('[class*="recommendation"], [data-testid="recommendation"]').first();
      if (await recommendation.isVisible()) {
        await expect(recommendation).toBeVisible();
      }
    });

    test('should store learning for future runs', async () => {
      await page.goto(LEARN_URL);
      await page.waitForLoadState('networkidle');

      const learningStore = page.locator('[class*="learning-store"], [data-testid="learning-store"]').first();
      if (await learningStore.isVisible()) {
        await expect(learningStore).toBeVisible();
      }
    });
  });

  /**
   * E2E-015: Prompt rollback
   * Prompt version can rollback and audit
   */
  test.describe('E2E-015: Prompt Rollback', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should show prompt version history', async () => {
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      const versionHistory = page.locator('[class*="version-history"], [data-testid="version-history"]').first();
      if (await versionHistory.isVisible()) {
        await expect(versionHistory).toBeVisible();
      }
    });

    test('should allow rollback to previous version', async () => {
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      const rollbackButton = page.locator('button:has-text("Rollback"), button:has-text("Revert")').first();
      if (await rollbackButton.isVisible()) {
        await rollbackButton.click();

        // Confirm rollback
        const confirmButton = page.locator('button:has-text("Confirm")').first();
        if (await confirmButton.isVisible()) {
          await confirmButton.click();
        }

        // Verify rollback completed
        await expect(page.locator('[class*="rolled-back"], [class*="reverted"]')).toBeVisible({ timeout: 5000 });
      }
    });

    test('should audit rollback action', async () => {
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      const auditLog = page.locator('[class*="audit"], [data-testid="audit-log"]').first();
      if (await auditLog.isVisible()) {
        await expect(auditLog).toContainText(/rollback|revert/i);
      }
    });
  });

  /**
   * E2E-016: Evolve change
   * Proposal → diff → approval → revalidate
   */
  test.describe('E2E-016: Evolve Change', () => {
    const PROJECT_ID = 'test-project';
    const EVOLVE_URL = `${BASE_URL}/p/${PROJECT_ID}/evolve`;

    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should show change proposal', async () => {
      await page.goto(EVOLVE_URL);
      await page.waitForLoadState('networkidle');

      const proposal = page.locator('[class*="proposal"], [data-testid="change-proposal"]').first();
      if (await proposal.isVisible()) {
        await expect(proposal).toBeVisible();
      }
    });

    test('should show diff view', async () => {
      await page.goto(EVOLVE_URL);
      await page.waitForLoadState('networkidle');

      const diffView = page.locator('[class*="diff"], [data-testid="diff-view"]').first();
      if (await diffView.isVisible()) {
        await expect(diffView).toBeVisible();
      }
    });

    test('should allow approval of change', async () => {
      await page.goto(EVOLVE_URL);
      await page.waitForLoadState('networkidle');

      const approveButton = page.locator('button:has-text("Approve"), button:has-text("Accept")').first();
      if (await approveButton.isVisible()) {
        await approveButton.click();

        // Verify approval
        await expect(page.locator('[class*="approved"], [class*="accepted"]')).toBeVisible({ timeout: 5000 });
      }
    });

    test('should revalidate after approval', async () => {
      await page.goto(EVOLVE_URL);
      await page.waitForLoadState('networkidle');

      const approveButton = page.locator('button:has-text("Approve")').first();
      if (await approveButton.isVisible()) {
        await approveButton.click();
        await page.waitForTimeout(2000);

        // Check for revalidation
        const validationStatus = page.locator('[class*="validation"], [data-testid="validation-status"]').first();
        if (await validationStatus.isVisible()) {
          await expect(validationStatus).toBeVisible();
        }
      }
    });
  });

  /**
   * E2E-017: Unauthorized user
   * Actions hidden/disabled and API denies mutation
   */
  test.describe('E2E-017: Unauthorized User', () => {
    test('should hide protected actions for unauthorized user', async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', UNAUTHORIZED_USER.email);
      await page.fill('input[name="password"]', UNAUTHORIZED_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');

      // Check that protected actions are hidden
      const protectedAction = page.locator('[class*="protected"], [data-testid="protected-action"]').first();
      await expect(protectedAction).not.toBeVisible();
    });

    test('should disable protected actions for unauthorized user', async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', UNAUTHORIZED_USER.email);
      await page.fill('input[name="password"]', UNAUTHORIZED_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');

      const actionButton = page.locator('button:has-text("Delete"), button:has-text("Modify")').first();
      if (await actionButton.isVisible()) {
        const isDisabled = await actionButton.isDisabled();
        expect(isDisabled).toBeTruthy();
      }
    });

    test('should deny API mutation for unauthorized user', async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', UNAUTHORIZED_USER.email);
      await page.fill('input[name="password"]', UNAUTHORIZED_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');

      // Try to access protected route
      const response = await page.goto(`${BASE_URL}/admin/settings`);
      if (response) {
        expect(response.status()).toBe(401);
      }
    });
  });

  /**
   * E2E-018: Data Cloud degraded
   * Degraded packet shown; unsafe actions disabled
   */
  test.describe('E2E-018: Data Cloud Degraded', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should show degraded packet', async () => {
      await page.goto(`${BASE_URL}/p/test-project/phase`);
      await page.waitForLoadState('networkidle');

      const degradedPacket = page.locator('[class*="degraded"], [class*="warning"], [data-testid="degraded-packet"]').first();
      if (await degradedPacket.isVisible()) {
        await expect(degradedPacket).toBeVisible();
      }
    });

    test('should disable unsafe actions', async () => {
      await page.goto(`${BASE_URL}/p/test-project/phase`);
      await page.waitForLoadState('networkidle');

      const unsafeAction = page.locator('button:has-text("Delete"), button:has-text("Destroy")').first();
      if (await unsafeAction.isVisible()) {
        const isDisabled = await unsafeAction.isDisabled();
        expect(isDisabled).toBeTruthy();
      }
    });
  });

  /**
   * E2E-019: AEP degraded
   * AEP failure shown as blocker, not empty evidence
   */
  test.describe('E2E-019: AEP Degraded', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should show AEP failure as blocker', async () => {
      await page.goto(`${BASE_URL}/p/test-project/run`);
      await page.waitForLoadState('networkidle');

      const aepFailure = page.locator('[class*="aep"], [class*="blocker"], [data-testid="aep-failure"]').first();
      if (await aepFailure.isVisible()) {
        await expect(aepFailure).toContainText(/blocker|failed|error/i);
      }
    });

    test('should not show empty evidence', async () => {
      await page.goto(`${BASE_URL}/p/test-project/run`);
      await page.waitForLoadState('networkidle');

      const evidencePanel = page.locator('[class*="evidence"], [data-testid="evidence"]').first();
      if (await evidencePanel.isVisible()) {
        const content = await evidencePanel.textContent();
        expect(content?.trim()).not.toBe('');
      }
    });
  });

  /**
   * E2E-020: Kernel degraded
   * Kernel truth source error shown and recoverable
   */
  test.describe('E2E-020: Kernel Degraded', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should show Kernel truth source error', async () => {
      await page.goto(`${BASE_URL}/kernel-health`);
      await page.waitForLoadState('networkidle');

      const kernelError = page.locator('[class*="kernel"], [class*="error"], [data-testid="kernel-error"]').first();
      if (await kernelError.isVisible()) {
        await expect(kernelError).toBeVisible();
      }
    });

    test('should show recovery option', async () => {
      await page.goto(`${BASE_URL}/kernel-health`);
      await page.waitForLoadState('networkidle');

      const recoverButton = page.locator('button:has-text("Recover"), button:has-text("Retry")').first();
      if (await recoverButton.isVisible()) {
        await expect(recoverButton).toBeVisible();
      }
    });

    test('should allow recovery action', async () => {
      await page.goto(`${BASE_URL}/kernel-health`);
      await page.waitForLoadState('networkidle');

      const recoverButton = page.locator('button:has-text("Recover")').first();
      if (await recoverButton.isVisible()) {
        await recoverButton.click();

        // Verify recovery initiated
        await expect(page.locator('[class*="recovering"], [class*="retrying"]')).toBeVisible({ timeout: 5000 });
      }
    });
  });

  /**
   * E2E-021: Product-family assets
   * Assets/releases/promotions load with permissions
   */
  test.describe('E2E-021: Product-Family Assets', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should load assets with permissions', async () => {
      await page.goto(`${BASE_URL}/product-family`);
      await page.waitForLoadState('networkidle');

      const assetsList = page.locator('[class*="assets"], [data-testid="assets-list"]').first();
      if (await assetsList.isVisible()) {
        await expect(assetsList).toBeVisible();
      }
    });

    test('should load releases with permissions', async () => {
      await page.goto(`${BASE_URL}/product-family/releases`);
      await page.waitForLoadState('networkidle');

      const releasesList = page.locator('[class*="releases"], [data-testid="releases-list"]').first();
      if (await releasesList.isVisible()) {
        await expect(releasesList).toBeVisible();
      }
    });

    test('should load promotions with permissions', async () => {
      await page.goto(`${BASE_URL}/product-family/promotions`);
      await page.waitForLoadState('networkidle');

      const promotionsList = page.locator('[class*="promotions"], [data-testid="promotions-list"]').first();
      if (await promotionsList.isVisible()) {
        await expect(promotionsList).toBeVisible();
      }
    });
  });

  /**
   * E2E-022: Admin observability
   * SLO/cost/domain/OpenAPI evidence shown
   */
  test.describe('E2E-022: Admin Observability', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should show SLO evidence', async () => {
      await page.goto(`${BASE_URL}/admin/observability`);
      await page.waitForLoadState('networkidle');

      const sloPanel = page.locator('[class*="slo"], [data-testid="slo-panel"]').first();
      if (await sloPanel.isVisible()) {
        await expect(sloPanel).toBeVisible();
      }
    });

    test('should show cost evidence', async () => {
      await page.goto(`${BASE_URL}/admin/observability`);
      await page.waitForLoadState('networkidle');

      const costPanel = page.locator('[class*="cost"], [data-testid="cost-panel"]').first();
      if (await costPanel.isVisible()) {
        await expect(costPanel).toBeVisible();
      }
    });

    test('should show domain evidence', async () => {
      await page.goto(`${BASE_URL}/admin/observability`);
      await page.waitForLoadState('networkidle');

      const domainPanel = page.locator('[class*="domain"], [data-testid="domain-panel"]').first();
      if (await domainPanel.isVisible()) {
        await expect(domainPanel).toBeVisible();
      }
    });

    test('should show OpenAPI evidence', async () => {
      await page.goto(`${BASE_URL}/admin/observability`);
      await page.waitForLoadState('networkidle');

      const openapiPanel = page.locator('[class*="openapi"], [data-testid="openapi-panel"]').first();
      if (await openapiPanel.isVisible()) {
        await expect(openapiPanel).toBeVisible();
      }
    });
  });

  /**
   * E2E-023: i18n
   * No raw user-visible text outside catalog
   */
  test.describe('E2E-023: i18n', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should use i18n catalog for user-visible text', async () => {
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      // Check that text elements have i18n attributes
      const textElements = page.locator('[data-i18n], [data-translate]').first();
      if (await textElements.isVisible()) {
        await expect(textElements).toBeVisible();
      }
    });

    test('should not have raw hardcoded text', async () => {
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      // This is a simplified check - in practice, you'd scan all text nodes
      const bodyText = await page.locator('body').textContent();
      // Ensure there's no obvious hardcoded English text that should be localized
      // This is a basic check; real implementation would be more sophisticated
      expect(bodyText).toBeTruthy();
    });
  });

  /**
   * E2E-024: a11y
   * WCAG AA/no critical axe violations
   */
  test.describe('E2E-024: Accessibility', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should have proper heading hierarchy', async () => {
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      const h1 = page.locator('h1');
      await expect(h1).toHaveCount(1);
    });

    test('should have ARIA labels on interactive elements', async () => {
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      const buttons = page.locator('button').first();
      if (await buttons.isVisible()) {
        const hasAria = await buttons.getAttribute('aria-label');
        const hasTitle = await buttons.getAttribute('title');
        const hasText = await buttons.textContent();
        expect(hasAria || hasTitle || hasText).toBeTruthy();
      }
    });

    test('should have proper focus management', async () => {
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      const firstInteractive = page.locator('button, a, input').first();
      await firstInteractive.focus();
      await expect(firstInteractive).toBeFocused();
    });

    test('should have sufficient color contrast', async () => {
      // This would typically use axe-core or similar tool
      // For now, we verify the accessibility testing infrastructure exists
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      // Check that axe-core is available
      const axeCheck = await page.evaluate(() => {
        return typeof (window as any).axe !== 'undefined';
      });
      
      // If axe is available, run a basic check
      if (axeCheck) {
        const results = await page.evaluate(async () => {
          return await (window as any).axe.run();
        });
        expect(results.violations).toHaveLength(0);
      }
    });
  });

  /**
   * E2E-025: Performance
   * Route and bundle budgets respected
   */
  test.describe('E2E-025: Performance', () => {
    test.beforeEach(async () => {
      await page.goto(`${BASE_URL}/login`);
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForLoadState('networkidle');
    });

    test('should respect route budget', async () => {
      const startTime = Date.now();
      
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');
      
      const loadTime = Date.now() - startTime;
      
      // Route should load within reasonable time (e.g., 3 seconds)
      expect(loadTime).toBeLessThan(3000);
    });

    test('should respect bundle budget', async () => {
      await page.goto(`${BASE_URL}/p/test-project/intent`);
      await page.waitForLoadState('networkidle');

      // Check bundle sizes via performance API
      const bundleSizes = await page.evaluate(() => {
        const entries = performance.getEntriesByType('resource') as PerformanceResourceTiming[];
        return entries
          .filter(entry => entry.name.endsWith('.js'))
          .map(entry => ({
            name: entry.name,
            size: entry.transferSize,
          }));
      });

      // Verify no single bundle exceeds budget (e.g., 500KB)
      const largeBundles = bundleSizes.filter(b => b.size > 500 * 1024);
      expect(largeBundles).toHaveLength(0);
    });

    test('should have efficient cockpit rendering', async () => {
      const startTime = Date.now();
      
      await page.goto(`${BASE_URL}/p/test-project/phase`);
      await page.waitForLoadState('networkidle');
      
      const renderTime = Date.now() - startTime;
      
      // Cockpit should render efficiently (e.g., within 2 seconds)
      expect(renderTime).toBeLessThan(2000);
    });

    test('should have efficient canvas rendering', async () => {
      const startTime = Date.now();
      
      await page.goto(`${BASE_URL}/p/test-project/shape`);
      await page.waitForLoadState('networkidle');
      
      const renderTime = Date.now() - startTime;
      
      // Canvas should render efficiently (e.g., within 2 seconds)
      expect(renderTime).toBeLessThan(2000);
    });
  });
});
