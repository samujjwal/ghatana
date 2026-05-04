/**
 * YAPPC Full-Flow E2E Tests
 *
 * Tests the complete YAPPC lifecycle flow from onboarding through all phases.
 *
 * @doc.type test
 * @doc.purpose YAPPC full-flow E2E tests
 * @doc.layer product
 * @doc.pattern E2E Test
 */

import { test, expect } from '@playwright/test';

test.describe('YAPPC Full Flow', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to the app
    await page.goto('/');
    await page.waitForLoadState('networkidle');
  });

  test('onboarding → workspace → project → intent phase', async ({ page }) => {
    // Step 1: Onboarding flow
    await test.step('complete onboarding', async () => {
      // Check if onboarding is shown
      const onboardingVisible = await page.locator('[data-testid="onboarding-checklist"]').isVisible().catch(() => false);
      
      if (onboardingVisible) {
        // Complete onboarding checklist
        await page.click('[data-testid="onboarding-item-0"]'); // First item
        await page.click('[data-testid="onboarding-item-1"]'); // Second item
        await page.click('[data-testid="complete-onboarding"]'); // Complete button
        
        // Verify onboarding completion
        await expect(page.locator('[data-testid="onboarding-complete"]')).toBeVisible();
      }
    });

    // Step 2: Navigate to workspaces
    await test.step('navigate to workspaces', async () => {
      await page.click('[aria-label="Workspaces"]');
      await expect(page).toHaveURL(/\/workspaces/);
      await expect(page.locator('[data-testid="workspaces-list"]')).toBeVisible();
    });

    // Step 3: Select or create workspace
    await test.step('select workspace', async () => {
      const existingWorkspace = page.locator('[data-testid="workspace-item"]').first();
      
      if (await existingWorkspace.isVisible()) {
        await existingWorkspace.click();
      } else {
        // Create new workspace
        await page.click('[data-testid="create-workspace"]');
        await page.fill('[data-testid="workspace-name-input"]', 'Test Workspace');
        await page.fill('[data-testid="workspace-description-input"]', 'Test workspace for E2E');
        await page.click('[data-testid="save-workspace"]');
      }
      
      await expect(page.locator('[data-testid="workspace-detail"]')).toBeVisible();
    });

    // Step 4: Navigate to projects
    await test.step('navigate to projects', async () => {
      await page.click('[aria-label="Projects"]');
      await expect(page).toHaveURL(/\/projects/);
      await expect(page.locator('[data-testid="projects-list"]')).toBeVisible();
    });

    // Step 5: Select or create project
    await test.step('select project', async () => {
      const existingProject = page.locator('[data-testid="project-item"]').first();
      
      if (await existingProject.isVisible()) {
        await existingProject.click();
      } else {
        // Create new project
        await page.click('[data-testid="create-project"]');
        await page.fill('[data-testid="project-name-input"]', 'Test Project');
        await page.fill('[data-testid="project-description-input"]', 'Test project for E2E');
        await page.click('[data-testid="save-project"]');
      }
      
      await expect(page.locator('[data-testid="project-detail"]')).toBeVisible();
    });

    // Step 6: Navigate to Intent phase
    await test.step('navigate to intent phase', async () => {
      await page.click('[aria-label="Intent"]');
      await expect(page).toHaveURL(/\/intent/);
      await expect(page.locator('[data-testid="intent-cockpit"]')).toBeVisible();
    });

    // Step 7: Verify Intent cockpit elements
    await test.step('verify intent cockpit', async () => {
      await expect(page.locator('[data-testid="phase-purpose"]')).toBeVisible();
      await expect(page.locator('[data-testid="primary-next-action"]')).toBeVisible();
      await expect(page.locator('[data-testid="blockers-panel"]')).toBeVisible();
      await expect(page.locator('[data-testid="evidence-panel"]')).toBeVisible();
    });
  });

  test('shape canvas → create page node → add components → preview', async ({ page }) => {
    // Navigate to Shape phase
    await page.goto('/shape');
    await page.waitForLoadState('networkidle');

    await test.step('verify shape canvas loads', async () => {
      await expect(page.locator('[data-testid="shape-cockpit"]')).toBeVisible();
      await expect(page.locator('[data-testid="canvas-container"]')).toBeVisible();
    });

    await test.step('create page node', async () => {
      await page.click('[data-testid="add-page-node"]');
      await page.fill('[data-testid="page-name-input"]', 'Test Page');
      await page.click('[data-testid="save-page-node"]');
      
      await expect(page.locator('[data-testid="page-node-Test Page"]')).toBeVisible();
    });

    await test.step('add components to page', async () => {
      // Open component palette
      await page.click('[data-testid="component-palette-toggle"]');
      
      // Drag a component to the canvas
      const component = page.locator('[data-testid="component-Button"]').first();
      const canvas = page.locator('[data-testid="canvas-drop-zone"]');
      
      await component.dragTo(canvas);
      
      // Verify component was added
      await expect(page.locator('[data-testid="component-instance-Button"]')).toBeVisible();
    });

    await test.step('open preview', async () => {
      await page.click('[data-testid="open-preview"]');
      await expect(page.locator('[data-testid="preview-iframe"]')).toBeVisible();
    });
  });

  test('validate → approval cockpit', async ({ page }) => {
    // Navigate to Validate phase
    await page.goto('/validate');
    await page.waitForLoadState('networkidle');

    await test.step('verify validate cockpit loads', async () => {
      await expect(page.locator('[data-testid="validate-cockpit"]')).toBeVisible();
    });

    await test.step('check validation status', async () => {
      await expect(page.locator('[data-testid="validation-status"]')).toBeVisible();
      
      const validationStatus = await page.locator('[data-testid="validation-status"]').textContent();
      expect(validationStatus).toMatch(/passed|failed|pending/i);
    });

    await test.step('review approval gates', async () => {
      await expect(page.locator('[data-testid="approval-gates"]')).toBeVisible();
      
      // Check for required approvals
      const requiredApprovals = page.locator('[data-testid="required-approval"]');
      const count = await requiredApprovals.count();
      expect(count).toBeGreaterThan(0);
    });

    await test.step('approve changes', async () => {
      await page.click('[data-testid="approve-changes"]');
      
      // Verify approval confirmation
      await expect(page.locator('[data-testid="approval-confirmed"]')).toBeVisible();
    });
  });

  test('generate → codegen preview', async ({ page }) => {
    // Navigate to Generate phase
    await page.goto('/generate');
    await page.waitForLoadState('networkidle');

    await test.step('verify generate cockpit loads', async () => {
      await expect(page.locator('[data-testid="generate-cockpit"]')).toBeVisible();
    });

    await test.step('request code generation', async () => {
      await page.click('[data-testid="generate-code"]');
      
      // Wait for generation to complete
      await page.waitForSelector('[data-testid="generation-complete"]', { timeout: 30000 });
    });

    await test.step('view codegen preview', async () => {
      await page.click('[data-testid="view-codegen-preview"]');
      await expect(page.locator('[data-testid="codegen-preview-panel"]')).toBeVisible();
    });

    await test.step('verify generated files', async () => {
      await expect(page.locator('[data-testid="generated-file-list"]')).toBeVisible();
      
      const files = page.locator('[data-testid="generated-file"]');
      const count = await files.count();
      expect(count).toBeGreaterThan(0);
    });

    await test.step('check ownership regions', async () => {
      await page.click('[data-testid="show-ownership-regions"]');
      await expect(page.locator('[data-testid="ownership-region-marker"]')).toBeVisible();
    });
  });

  test('run → capability-gated run cockpit', async ({ page }) => {
    // Navigate to Run phase
    await page.goto('/run');
    await page.waitForLoadState('networkidle');

    await test.step('verify run cockpit loads', async () => {
      await expect(page.locator('[data-testid="run-cockpit"]')).toBeVisible();
    });

    await test.step('check capability gates', async () => {
      await expect(page.locator('[data-testid="capability-gates"]')).toBeVisible();
      
      // Check for required capabilities
      const requiredCapabilities = page.locator('[data-testid="required-capability"]');
      const count = await requiredCapabilities.count();
      expect(count).toBeGreaterThan(0);
    });

    await test.step('view run plan', async () => {
      await page.click('[data-testid="view-run-plan"]');
      await expect(page.locator('[data-testid="run-plan-panel"]')).toBeVisible();
    });

    await test.step('check pipeline readiness', async () => {
      await expect(page.locator('[data-testid="pipeline-readiness"]')).toBeVisible();
      
      const readiness = await page.locator('[data-testid="pipeline-readiness"]').textContent();
      expect(readiness).toMatch(/ready|not ready/i);
    });
  });

  test('observe → project preview', async ({ page }) => {
    // Navigate to Observe phase
    await page.goto('/observe');
    await page.waitForLoadState('networkidle');

    await test.step('verify observe cockpit loads', async () => {
      await expect(page.locator('[data-testid="observe-cockpit"]')).toBeVisible();
    });

    await test.step('view project preview', async () => {
      await page.click('[data-testid="view-project-preview"]');
      await expect(page.locator('[data-testid="project-preview-iframe"]')).toBeVisible();
    });

    await test.step('check metrics dashboard', async () => {
      await page.click('[data-testid="metrics-dashboard"]');
      await expect(page.locator('[data-testid="metrics-panel"]')).toBeVisible();
    });

    await test.step('review incidents', async () => {
      await expect(page.locator('[data-testid="incidents-panel"]')).toBeVisible();
    });
  });

  test('learn → retrospective', async ({ page }) => {
    // Navigate to Learn phase
    await page.goto('/learn');
    await page.waitForLoadState('networkidle');

    await test.step('verify learn cockpit loads', async () => {
      await expect(page.locator('[data-testid="learn-cockpit"]')).toBeVisible();
    });

    await test.step('view retrospective', async () => {
      await page.click('[data-testid="view-retrospective"]');
      await expect(page.locator('[data-testid="retrospective-panel"]')).toBeVisible();
    });

    await test.step('capture learnings', async () => {
      await page.click('[data-testid="add-learning"]');
      await page.fill('[data-testid="learning-input"]', 'Test learning from E2E');
      await page.click('[data-testid="save-learning"]');
      
      await expect(page.locator('[data-testid="learning-item"]')).toBeVisible();
    });

    await test.step('identify reusable patterns', async () => {
      await expect(page.locator('[data-testid="reusable-patterns"]')).toBeVisible();
    });
  });

  test('evolve → next cycle plan', async ({ page }) => {
    // Navigate to Evolve phase
    await page.goto('/evolve');
    await page.waitForLoadState('networkidle');

    await test.step('verify evolve cockpit loads', async () => {
      await expect(page.locator('[data-testid="evolve-cockpit"]')).toBeVisible();
    });

    await test.step('view roadmap', async () => {
      await page.click('[data-testid="view-roadmap"]');
      await expect(page.locator('[data-testid="roadmap-panel"]')).toBeVisible();
    });

    await test.step('plan next cycle', async () => {
      await page.click('[data-testid="plan-next-cycle"]');
      await page.fill('[data-testid="cycle-goal-input"]', 'Test cycle goal');
      await page.click('[data-testid="save-cycle-plan"]');
      
      await expect(page.locator('[data-testid="cycle-plan-saved"]')).toBeVisible();
    });

    await test.step('review backlog', async () => {
      await expect(page.locator('[data-testid="backlog-panel"]')).toBeVisible();
    });
  });

  test('complete full lifecycle flow', async ({ page }) => {
    // This test runs through the complete lifecycle in sequence
    // It assumes the user is already logged in and has a workspace/project

    // Start at Intent
    await page.goto('/intent');
    await page.waitForLoadState('networkidle');

    // Navigate through each phase
    const phases = [
      { name: 'Intent', url: '/intent', action: 'define-requirements' },
      { name: 'Shape', url: '/shape', action: 'add-components' },
      { name: 'Validate', url: '/validate', action: 'approve-changes' },
      { name: 'Generate', url: '/generate', action: 'generate-code' },
      { name: 'Run', url: '/run', action: 'check-readiness' },
      { name: 'Observe', url: '/observe', action: 'view-metrics' },
      { name: 'Learn', url: '/learn', action: 'capture-learnings' },
      { name: 'Evolve', url: '/evolve', action: 'plan-next-cycle' },
    ];

    for (const phase of phases) {
      await test.step(`navigate to ${phase.name} phase`, async () => {
        await page.goto(phase.url);
        await page.waitForLoadState('networkidle');
        await expect(page.locator(`[data-testid="${phase.name.toLowerCase()}-cockpit"]`)).toBeVisible();
      });

      await test.step(`perform ${phase.action} in ${phase.name}`, async () => {
        // Perform the primary action for this phase
        const actionButton = page.locator(`[data-testid="${phase.action}"]`);
        if (await actionButton.isVisible()) {
          await actionButton.click();
        }
      });
    }

    // Verify we completed the full cycle
    await expect(page.locator('[data-testid="cycle-complete"]')).toBeVisible();
  });
});

test.describe('YAPPC Full Flow Accessibility', () => {
  test('keyboard navigation through full flow', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Test keyboard navigation to workspaces
    await page.keyboard.press('Tab');
    await page.keyboard.press('Enter');
    
    // Verify keyboard navigation works
    await expect(page).toHaveURL(/\/workspaces/);

    // Continue keyboard navigation through the flow
    await page.keyboard.press('Tab');
    await page.keyboard.press('Enter');
    
    // Navigate to projects
    await expect(page).toHaveURL(/\/projects/);
  });

  test('screen reader compatibility', async ({ page }) => {
    await page.goto('/intent');
    await page.waitForLoadState('networkidle');

    // Check for proper ARIA labels
    const primaryAction = page.locator('[data-testid="primary-next-action"]');
    await expect(primaryAction).toHaveAttribute('aria-label');
    
    // Check for proper role attributes
    const cockpit = page.locator('[data-testid="intent-cockpit"]');
    await expect(cockpit).toHaveAttribute('role');
  });

  test('focus management', async ({ page }) => {
    await page.goto('/shape');
    await page.waitForLoadState('networkidle');

    // Open a dialog
    await page.click('[data-testid="add-page-node"]');
    
    // Verify focus is trapped in dialog
    const dialog = page.locator('[data-testid="dialog"]');
    const focusedElement = await page.evaluate(() => document.activeElement?.tagName);
    
    expect(focusedElement).toBe('INPUT');
  });
});
