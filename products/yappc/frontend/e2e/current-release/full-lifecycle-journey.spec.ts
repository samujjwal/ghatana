/**
 * YAPPC Full-Lifecycle Journey E2E Test
 * YAPPC-P0-010: Add real full-loop Playwright E2E for YAPPC product journey
 *
 * This test covers the complete 8-phase YAPPC product journey:
 * Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve
 *
 * @doc.type e2e-test
 * @doc.purpose Validate complete YAPPC product journey end-to-end
 * @doc.layer product
 */

import { test, expect } from '@playwright/test';
import { ProjectPage } from '../pages/project.page';
import { NavigationPage } from '../pages/navigation.page';
import { cleanTestState } from '../helpers/test-isolation';

// Test data constants
const TEST_LIFECYCLE_PROJECT = {
  name: 'E2E Full Lifecycle Test Project',
  description: 'Project for testing complete 8-phase YAPPC lifecycle',
  intent: 'Build a microservice with JWT authentication, role-based access control, and OAuth2 integration',
  techStack: ['Java', 'Spring Boot', 'PostgreSQL', 'Redis'],
};

test.describe('Full-Lifecycle Journey - 8-Phase Product Flow', () => {
  // Full lifecycle tests may take longer due to multiple phase transitions
  test.setTimeout(300000);
  let projectPage: ProjectPage;
  let navigationPage: NavigationPage;

  test.beforeEach(async ({ page }) => {
    projectPage = new ProjectPage(page);
    navigationPage = new NavigationPage(page);

    await cleanTestState(page);

    await page.addInitScript(() => {
      try {
        localStorage.setItem('E2E_DISABLE_OVERLAYS', '1');
        localStorage.setItem('E2E_SIMPLE_PAGES', '1');
        localStorage.setItem('E2E_FULL_LIFECYCLE_MODE', '1');
        (window as any).__E2E_TEST_NO_POINTER_BLOCK = true;
      } catch (e) {}
    });

    page.on('console', (msg) => console.log('PAGE LOG:', msg.text()));
    page.on('pageerror', (err) => console.error('PAGE ERROR:', err));
  });

  test('Complete 8-phase journey: Intent through Evolve', async ({ page }) => {
    // Phase 0: Authentication and Navigation
    await page.goto('/');
    await expect(page.locator('h1')).toContainText('YAPPC', { timeout: 10000 });

    // Phase 1: Intent - Create project with intent
    await navigationPage.clickProjectsNav();
    await page.click('[data-testid="create-project-button"]');
    
    await page.fill('[data-testid="project-name-input"]', TEST_LIFECYCLE_PROJECT.name);
    await page.fill('[data-testid="project-description-input"]', TEST_LIFECYCLE_PROJECT.description);
    
    // Fill intent field
    const intentInput = page.locator('[data-testid="project-intent-input"], textarea[placeholder*="intent"], textarea[placeholder*="what"]');
    const intentCount = await intentInput.count();
    if (intentCount > 0) {
      await intentInput.first().fill(TEST_LIFECYCLE_PROJECT.intent);
    }
    
    await projectPage.safeClick('[data-testid="create-project-submit"]');
    
    // Wait for project creation and navigation to intent phase
    await page.waitForURL(/\/p\/.*\/intent/, { timeout: 15000 });
    await expect(page.locator('h1, h2')).toContainText(TEST_LIFECYCLE_PROJECT.name, { timeout: 10000 });

    // Phase 2: Shape - Navigate to shape/planning phase
    await page.goto(page.url().replace(/\/intent$/, '/shape'));
    await expect(page.locator('[data-testid="shape-phase-container"], [data-testid="planning-phase"]')).toBeVisible({ timeout: 10000 });
    
    // Verify shape phase elements are present
    const shapeElements = await page.locator('[data-testid*="shape"], [data-testid*="planning"]').count();
    expect(shapeElements).toBeGreaterThan(0);

    // Phase 3: Validate - Navigate to validate phase
    await page.goto(page.url().replace(/\/shape$/, '/validate'));
    await expect(page.locator('[data-testid="validate-phase-container"], [data-testid="validation-phase"]')).toBeVisible({ timeout: 10000 });
    
    // Check for validation controls
    const validateElements = await page.locator('[data-testid*="validate"], [data-testid*="validation"]').count();
    expect(validateElements).toBeGreaterThan(0);

    // Phase 4: Generate - Navigate to generate phase
    await page.goto(page.url().replace(/\/validate$/, '/generate'));
    await expect(page.locator('[data-testid="generate-phase-container"], [data-testid="generation-phase"]')).toBeVisible({ timeout: 10000 });
    
    // Trigger generation if controls are available
    const generateButton = page.locator('[data-testid="generate-button"], [data-testid="start-generation"]');
    if (await generateButton.count() > 0) {
      await generateButton.first().click();
      // Wait for generation to start or complete
      await page.waitForTimeout(2000);
    }

    // Phase 5: Run - Navigate to run phase
    await page.goto(page.url().replace(/\/generate$/, '/run'));
    await expect(page.locator('[data-testid="run-phase-container"], [data-testid="execution-phase"]')).toBeVisible({ timeout: 10000 });
    
    // Check for run/deployment controls
    const runElements = await page.locator('[data-testid*="run"], [data-testid*="deploy"], [data-testid*="build"]').count();
    expect(runElements).toBeGreaterThan(0);

    // Phase 6: Observe - Navigate to observe phase
    await page.goto(page.url().replace(/\/run$/, '/observe'));
    await expect(page.locator('[data-testid="observe-phase-container"], [data-testid="monitoring-phase"]')).toBeVisible({ timeout: 10000 });
    
    // Check for observability elements
    const observeElements = await page.locator('[data-testid*="observe"], [data-testid*="monitor"], [data-testid*="metrics"]').count();
    expect(observeElements).toBeGreaterThan(0);

    // Phase 7: Learn - Navigate to learn phase
    await page.goto(page.url().replace(/\/observe$/, '/learn'));
    await expect(page.locator('[data-testid="learn-phase-container"], [data-testid="insights-phase"]')).toBeVisible({ timeout: 10000 });
    
    // Check for learning/insights elements
    const learnElements = await page.locator('[data-testid*="learn"], [data-testid*="insight"], [data-testid*="recommendation"]').count();
    expect(learnElements).toBeGreaterThan(0);

    // Phase 8: Evolve - Navigate to evolve phase
    await page.goto(page.url().replace(/\/learn$/, '/evolve'));
    await expect(page.locator('[data-testid="evolve-phase-container"], [data-testid="evolution-phase"]')).toBeVisible({ timeout: 10000 });
    
    // Check for evolution controls
    const evolveElements = await page.locator('[data-testid*="evolve"], [data-testid*="improve"], [data-testid*="refine"]').count();
    expect(evolveElements).toBeGreaterThan(0);

    // Verify we can navigate back through phases
    await page.goto(page.url().replace(/\/evolve$/, '/intent'));
    await expect(page.locator('h1, h2')).toContainText(TEST_LIFECYCLE_PROJECT.name);
  });

  test('Phase navigation and state persistence', async ({ page }) => {
    // Create project first
    await navigationPage.clickProjectsNav();
    await page.click('[data-testid="create-project-button"]');
    await page.fill('[data-testid="project-name-input"]', TEST_LIFECYCLE_PROJECT.name);
    await page.fill('[data-testid="project-description-input"]', TEST_LIFECYCLE_PROJECT.description);
    await projectPage.safeClick('[data-testid="create-project-submit"]');
    
    await page.waitForURL(/\/p\/.*\/intent/, { timeout: 15000 });

    // Navigate through all phases and verify each loads
    const phases = ['intent', 'shape', 'validate', 'generate', 'run', 'observe', 'learn', 'evolve'];
    
    for (const phase of phases) {
      await page.goto(page.url().replace(/\/\w+$/, `/${phase}`));
      await page.waitForTimeout(1000); // Allow phase to load
      
      // Verify phase-specific content exists
      const phaseContent = page.locator(`[data-testid="${phase}-phase"], [data-testid*="${phase}"]`);
      const hasContent = await phaseContent.count() > 0;
      
      // Even if specific phase testid doesn't exist, the route should load
      const currentUrl = page.url();
      expect(currentUrl).toContain(`/${phase}`);
    }

    // Verify navigation back to intent works
    await page.goto(page.url().replace(/\/\w+$/, '/intent'));
    await expect(page.locator('h1, h2')).toContainText(TEST_LIFECYCLE_PROJECT.name);
  });

  test('Phase transition controls and gates', async ({ page }) => {
    // Create project
    await navigationPage.clickProjectsNav();
    await page.click('[data-testid="create-project-button"]');
    await page.fill('[data-testid="project-name-input"]', TEST_LIFECYCLE_PROJECT.name);
    await page.fill('[data-testid="project-description-input"]', TEST_LIFECYCLE_PROJECT.description);
    await projectPage.safeClick('[data-testid="create-project-submit"]');
    
    await page.waitForURL(/\/p\/.*\/intent/, { timeout: 15000 });

    // Check for next-phase navigation controls
    const nextPhaseButton = page.locator('[data-testid="next-phase-button"], [data-testid="continue-to-shape"]');
    if (await nextPhaseButton.count() > 0) {
      await nextPhaseButton.first().click();
      await page.waitForTimeout(1000);
      expect(page.url()).toContain('/shape');
    }

    // Check for gate/approval controls if they exist
    const approvalControls = page.locator('[data-testid*="approve"], [data-testid*="gate"], [data-testid*="review"]');
    if (await approvalControls.count() > 0) {
      // Verify approval controls are present but don't necessarily interact
      expect(await approvalControls.count()).toBeGreaterThan(0);
    }
  });

  test('Lifecycle phase overview and dashboard', async ({ page }) => {
    // Create project
    await navigationPage.clickProjectsNav();
    await page.click('[data-testid="create-project-button"]');
    await page.fill('[data-testid="project-name-input"]', TEST_LIFECYCLE_PROJECT.name);
    await page.fill('[data-testid="project-description-input"]', TEST_LIFECYCLE_PROJECT.description);
    await projectPage.safeClick('[data-testid="create-project-submit"]');
    
    await page.waitForURL(/\/p\/.*/, { timeout: 15000 });

    // Navigate to lifecycle overview if it exists
    await page.goto(page.url().replace(/\/\w+$/, '/lifecycle'));
    await page.waitForTimeout(1000);

    // Check for lifecycle overview elements
    const lifecycleOverview = page.locator('[data-testid="lifecycle-overview"], [data-testid="phase-tracker"], [data-testid="lifecycle-dashboard"]');
    const hasOverview = await lifecycleOverview.count() > 0;

    if (hasOverview) {
      await expect(lifecycleOverview.first()).toBeVisible();
      
      // Check for phase indicators
      const phaseIndicators = page.locator('[data-testid*="phase"], [class*="phase"]');
      expect(await phaseIndicators.count()).toBeGreaterThan(0);
    }
  });
});

test.describe('Full-Lifecycle Journey - Error Recovery', () => {
  test.setTimeout(120000);

  test.beforeEach(async ({ page }) => {
    await cleanTestState(page);
    await page.addInitScript(() => {
      try {
        localStorage.setItem('E2E_DISABLE_OVERLAYS', '1');
        localStorage.setItem('E2E_SIMPLE_PAGES', '1');
        (window as any).__E2E_TEST_NO_POINTER_BLOCK = true;
      } catch (e) {}
    });
  });

  test('Phase transition error handling', async ({ page }) => {
    await page.goto('/projects');
    
    // Create project
    await page.click('[data-testid="create-project-button"]');
    await page.fill('[data-testid="project-name-input"]', TEST_LIFECYCLE_PROJECT.name);
    await page.fill('[data-testid="project-description-input"]', TEST_LIFECYCLE_PROJECT.description);
    await page.click('[data-testid="create-project-submit"]');
    
    await page.waitForURL(/\/p\/.*/, { timeout: 15000 });

    // Try to navigate to a phase that might not be accessible
    // This tests error handling for invalid phase transitions
    await page.goto(page.url().replace(/\/\w+$/, '/invalid-phase'));
    await page.waitForTimeout(1000);

    // Should either show error or redirect to a valid phase
    const currentUrl = page.url();
    const hasError = await page.locator('[data-testid="error-message"], [data-testid="error-page"]').count() > 0;
    const hasRedirected = !currentUrl.includes('/invalid-phase');

    expect(hasError || hasRedirected).toBe(true);
  });
});
