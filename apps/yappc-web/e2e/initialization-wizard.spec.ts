/**
 * Initialization Wizard E2E Tests
 *
 * End-to-end tests for the initialization wizard page including:
 * - Step navigation
 * - Form validation
 * - Configuration persistence
 * - Provider selection
 * - Cost estimation
 *
 * @doc.type test
 * @doc.purpose E2E tests for initialization wizard
 * @doc.phase 2
 */

import { test, expect, type Page } from '@playwright/test';

test.describe('Initialization Wizard', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/initialize');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display wizard header with project name', async () => {
      const header = page.locator('[class*="wizard-header"]');
      await expect(header).toBeVisible();
      await expect(header).toContainText(/Initialize/i);
    });

    test('should show step progress indicator', async () => {
      const stepProgress = page.locator('[class*="step-progress"]');
      await expect(stepProgress).toBeVisible();
    });

    test('should display cost estimator sidebar', async () => {
      const costSidebar = page.locator('[class*="cost-sidebar"]');
      await expect(costSidebar).toBeVisible();
      await expect(costSidebar).toContainText(/Cost/i);
    });

    test('should show navigation buttons', async () => {
      const prevButton = page.getByRole('button', { name: /previous|back/i });
      const nextButton = page.getByRole('button', { name: /next|continue/i });

      await expect(prevButton).toBeVisible();
      await expect(nextButton).toBeVisible();
    });
  });

  test.describe('Step Navigation', () => {
    test('should start at first step (Repository)', async () => {
      const stepIndicator = page.locator('[class*="step-progress"]');
      await expect(stepIndicator).toContainText(/Repository/i);
    });

    test('should navigate to next step when Continue is clicked', async () => {
      // Fill required fields in step 1
      await page.fill('[name="projectName"]', 'test-app');
      await page.fill('[name="repoName"]', 'test-app');

      // Click continue
      await page.getByRole('button', { name: /next|continue/i }).click();

      // Verify we're on step 2
      await expect(page.locator('[class*="hosting"]')).toBeVisible();
    });

    test('should navigate back when Previous is clicked', async () => {
      // Move to step 2
      await page.fill('[name="projectName"]', 'test-app');
      await page.getByRole('button', { name: /next|continue/i }).click();

      // Click previous
      await page.getByRole('button', { name: /previous|back/i }).click();

      // Verify we're back on step 1
      await expect(page.locator('[name="projectName"]')).toBeVisible();
    });

    test('should allow direct step navigation via progress indicator', async () => {
      // Fill step 1 and complete it
      await page.fill('[name="projectName"]', 'test-app');
      await page.getByRole('button', { name: /next|continue/i }).click();

      // Click on step 1 in progress indicator
      await page.locator('[class*="step-progress"] button').first().click();

      // Verify we're back on step 1
      await expect(page.locator('[name="projectName"]')).toHaveValue('test-app');
    });
  });

  test.describe('Repository Configuration', () => {
    test('should show repository provider selection', async () => {
      const githubOption = page.locator('[data-provider="github"]');
      const gitlabOption = page.locator('[data-provider="gitlab"]');
      const bitbucketOption = page.locator('[data-provider="bitbucket"]');

      await expect(githubOption).toBeVisible();
      await expect(gitlabOption).toBeVisible();
      await expect(bitbucketOption).toBeVisible();
    });

    test('should validate repository name format', async () => {
      await page.fill('[name="repoName"]', 'Invalid Repo Name!');
      await page.getByRole('button', { name: /next|continue/i }).click();

      // Should show validation error
      await expect(page.locator('[class*="error"]')).toContainText(/invalid/i);
    });

    test('should auto-suggest repository name from project name', async () => {
      await page.fill('[name="projectName"]', 'My Awesome App');

      // Repository name should be auto-filled
      await expect(page.locator('[name="repoName"]')).toHaveValue(/my-awesome-app/i);
    });
  });

  test.describe('Hosting Configuration', () => {
    test.beforeEach(async () => {
      // Complete step 1
      await page.fill('[name="projectName"]', 'test-app');
      await page.fill('[name="repoName"]', 'test-app');
      await page.getByRole('button', { name: /next|continue/i }).click();
    });

    test('should show frontend hosting providers', async () => {
      const vercelOption = page.locator('[data-provider="vercel"]');
      const netlifyOption = page.locator('[data-provider="netlify"]');

      await expect(vercelOption).toBeVisible();
      await expect(netlifyOption).toBeVisible();
    });

    test('should show backend hosting providers', async () => {
      const railwayOption = page.locator('[data-provider="railway"]');
      const renderOption = page.locator('[data-provider="render"]');

      await expect(railwayOption).toBeVisible();
      await expect(renderOption).toBeVisible();
    });

    test('should update cost estimate when provider is selected', async () => {
      const initialCost = await page.locator('[class*="cost-total"]').textContent();

      // Select a paid provider
      await page.locator('[data-provider="railway"]').click();

      const updatedCost = await page.locator('[class*="cost-total"]').textContent();

      // Cost should have changed
      expect(updatedCost).not.toBe(initialCost);
    });
  });

  test.describe('Infrastructure Configuration', () => {
    test.beforeEach(async () => {
      // Complete steps 1 & 2
      await page.fill('[name="projectName"]', 'test-app');
      await page.getByRole('button', { name: /next|continue/i }).click();
      await page.getByRole('button', { name: /next|continue/i }).click();
    });

    test('should show database provider selection', async () => {
      const supabaseOption = page.locator('[data-provider="supabase"]');
      const planetscaleOption = page.locator('[data-provider="planetscale"]');

      await expect(supabaseOption).toBeVisible();
      await expect(planetscaleOption).toBeVisible();
    });

    test('should show region selection', async () => {
      const regionSelect = page.locator('[name="region"]');
      await expect(regionSelect).toBeVisible();
    });

    test('should show environment configuration', async () => {
      const envTabs = page.locator('[class*="environment-tabs"]');
      await expect(envTabs).toBeVisible();
      await expect(envTabs).toContainText(/Development/i);
      await expect(envTabs).toContainText(/Staging/i);
      await expect(envTabs).toContainText(/Production/i);
    });
  });

  test.describe('CI/CD Configuration', () => {
    test.beforeEach(async () => {
      // Complete steps 1, 2 & 3
      await page.fill('[name="projectName"]', 'test-app');
      await page.getByRole('button', { name: /next|continue/i }).click();
      await page.getByRole('button', { name: /next|continue/i }).click();
      await page.getByRole('button', { name: /next|continue/i }).click();
    });

    test('should show CI/CD provider selection', async () => {
      const githubActionsOption = page.locator('[data-provider="github-actions"]');
      await expect(githubActionsOption).toBeVisible();
    });

    test('should show pipeline configuration options', async () => {
      await expect(page.locator('[name="enableTests"]')).toBeVisible();
      await expect(page.locator('[name="enableLinting"]')).toBeVisible();
      await expect(page.locator('[name="enableSecurity"]')).toBeVisible();
    });

    test('should show branch configuration', async () => {
      await expect(page.locator('[name="mainBranch"]')).toBeVisible();
      await expect(page.locator('[name="deployBranches"]')).toBeVisible();
    });
  });

  test.describe('Monitoring Configuration', () => {
    test.beforeEach(async () => {
      // Complete steps 1-4
      await page.fill('[name="projectName"]', 'test-app');
      await page.getByRole('button', { name: /next|continue/i }).click();
      await page.getByRole('button', { name: /next|continue/i }).click();
      await page.getByRole('button', { name: /next|continue/i }).click();
      await page.getByRole('button', { name: /next|continue/i }).click();
    });

    test('should show monitoring provider selection', async () => {
      const grafanaOption = page.locator('[data-provider="grafana"]');
      const datadogOption = page.locator('[data-provider="datadog"]');

      await expect(grafanaOption).toBeVisible();
      await expect(datadogOption).toBeVisible();
    });

    test('should show alerting configuration', async () => {
      await expect(page.locator('[name="enableAlerts"]')).toBeVisible();
      await expect(page.locator('[name="alertEmail"]')).toBeVisible();
    });
  });

  test.describe('Team Configuration', () => {
    test.beforeEach(async () => {
      // Complete steps 1-5
      await page.fill('[name="projectName"]', 'test-app');
      for (let i = 0; i < 5; i++) {
        await page.getByRole('button', { name: /next|continue/i }).click();
      }
    });

    test('should show team member invitation form', async () => {
      await expect(page.locator('[name="teamEmail"]')).toBeVisible();
      await expect(page.locator('[name="teamRole"]')).toBeVisible();
    });

    test('should allow adding multiple team members', async () => {
      await page.fill('[name="teamEmail"]', 'user1@example.com');
      await page.selectOption('[name="teamRole"]', 'developer');
      await page.getByRole('button', { name: /add|invite/i }).click();

      // Verify member was added
      await expect(page.locator('[class*="team-member"]')).toContainText('user1@example.com');
    });

    test('should show review button on final step', async () => {
      await expect(
        page.getByRole('button', { name: /review|initialize/i })
      ).toBeVisible();
    });
  });

  test.describe('Cost Estimation', () => {
    test('should show initial cost estimate of $0 for free tier', async () => {
      const costDisplay = page.locator('[class*="cost-total"]');
      await expect(costDisplay).toContainText(/\$0|Free/i);
    });

    test('should update cost when selecting paid options', async () => {
      // Select paid backend hosting
      await page.fill('[name="projectName"]', 'test-app');
      await page.getByRole('button', { name: /next|continue/i }).click();

      // Select premium provider
      await page.locator('[data-tier="pro"]').first().click();

      // Verify cost increased
      const costDisplay = page.locator('[class*="cost-total"]');
      await expect(costDisplay).not.toContainText(/\$0/);
    });

    test('should show cost breakdown by category', async () => {
      const costBreakdown = page.locator('[class*="cost-breakdown"]');

      await expect(costBreakdown).toContainText(/Hosting/i);
      await expect(costBreakdown).toContainText(/Database/i);
      await expect(costBreakdown).toContainText(/Storage/i);
    });
  });

  test.describe('Form Validation', () => {
    test('should prevent navigation with invalid form', async () => {
      // Don't fill required fields
      await page.getByRole('button', { name: /next|continue/i }).click();

      // Should show validation errors
      await expect(page.locator('[class*="error"]')).toBeVisible();

      // Should stay on current step
      await expect(page.locator('[name="projectName"]')).toBeVisible();
    });

    test('should show inline validation errors', async () => {
      await page.fill('[name="projectName"]', '');
      await page.locator('[name="projectName"]').blur();

      // Should show required error
      await expect(page.locator('[class*="error"]').first()).toContainText(
        /required/i
      );
    });
  });

  test.describe('Keyboard Navigation', () => {
    test('should support tab navigation through form fields', async () => {
      await page.keyboard.press('Tab');
      await expect(page.locator(':focus')).toBeVisible();
    });

    test('should support Enter to proceed to next step', async () => {
      await page.fill('[name="projectName"]', 'test-app');
      await page.keyboard.press('Enter');

      // Should move to next step
      await expect(page.locator('[class*="hosting"]')).toBeVisible();
    });
  });

  test.describe('Accessibility', () => {
    test('should have proper ARIA labels on form fields', async () => {
      const projectNameInput = page.locator('[name="projectName"]');
      await expect(projectNameInput).toHaveAttribute('aria-label');
    });

    test('should announce step changes to screen readers', async () => {
      const ariaLive = page.locator('[aria-live="polite"]');
      await expect(ariaLive).toBeVisible();
    });

    test('should have proper focus management between steps', async () => {
      await page.fill('[name="projectName"]', 'test-app');
      await page.getByRole('button', { name: /next|continue/i }).click();

      // Focus should be on first interactive element of new step
      await expect(page.locator(':focus')).toBeVisible();
    });
  });
});
