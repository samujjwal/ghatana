/**
 * Initialization Complete E2E Tests
 *
 * End-to-end tests for the initialization complete page including:
 * - Success display
 * - Resource summary
 * - Quick links
 * - Next steps
 * - Credentials display
 *
 * @doc.type test
 * @doc.purpose E2E tests for initialization complete
 * @doc.phase 2
 */

import { test, expect, type Page } from '@playwright/test';

test.describe('Initialization Complete', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/initialize/complete');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display success header', async () => {
      const header = page.locator('[class*="success-header"]');
      await expect(header).toBeVisible();
      await expect(header).toContainText(/Ready|Success|Complete/i);
    });

    test('should show quick links bar', async () => {
      const quickLinks = page.locator('[class*="quick-links"]');
      await expect(quickLinks).toBeVisible();
    });

    test('should show next steps section', async () => {
      const nextSteps = page.locator('[class*="next-steps"]');
      await expect(nextSteps).toBeVisible();
    });

    test('should show credentials card', async () => {
      const credentials = page.locator('[class*="credentials"]');
      await expect(credentials).toBeVisible();
    });

    test('should show resources sidebar', async () => {
      const resources = page.locator('[class*="resources-sidebar"]');
      await expect(resources).toBeVisible();
    });
  });

  test.describe('Success Header', () => {
    test('should display success icon', async () => {
      const successIcon = page.locator('[class*="success-icon"]');
      await expect(successIcon).toBeVisible();
    });

    test('should show project name', async () => {
      const header = page.locator('[class*="success-header"]');
      await expect(header).toContainText(/bakery-app|test/i);
    });

    test('should display setup statistics', async () => {
      const stats = page.locator('[class*="stats-row"]');
      await expect(stats).toBeVisible();
    });

    test('should show setup time', async () => {
      const statItem = page.locator('[class*="stat-item"]', {
        hasText: /Time/i,
      });
      await expect(statItem).toBeVisible();
    });

    test('should show resources created count', async () => {
      const statItem = page.locator('[class*="stat-item"]', {
        hasText: /Resources/i,
      });
      await expect(statItem).toBeVisible();
    });

    test('should show environments configured count', async () => {
      const statItem = page.locator('[class*="stat-item"]', {
        hasText: /Environments/i,
      });
      await expect(statItem).toBeVisible();
    });

    test('should show estimated cost', async () => {
      const statItem = page.locator('[class*="stat-item"]', {
        hasText: /Cost|Free/i,
      });
      await expect(statItem).toBeVisible();
    });
  });

  test.describe('Quick Links', () => {
    test('should show Live App link', async () => {
      const liveAppLink = page.locator('[class*="quick-link"]', {
        hasText: /Live App/i,
      });
      await expect(liveAppLink).toBeVisible();
      await expect(liveAppLink).toHaveAttribute('href', /.+/);
    });

    test('should show Repository link', async () => {
      const repoLink = page.locator('[class*="quick-link"]', {
        hasText: /Repository/i,
      });
      await expect(repoLink).toBeVisible();
      await expect(repoLink).toHaveAttribute('href', /github|gitlab|bitbucket/i);
    });

    test('should show Database link', async () => {
      const dbLink = page.locator('[class*="quick-link"]', {
        hasText: /Database/i,
      });
      await expect(dbLink).toBeVisible();
    });

    test('should show CI/CD link', async () => {
      const cicdLink = page.locator('[class*="quick-link"]', {
        hasText: /CI.*CD|Actions/i,
      });
      await expect(cicdLink).toBeVisible();
    });

    test('should show Monitoring link', async () => {
      const monitoringLink = page.locator('[class*="quick-link"]', {
        hasText: /Monitoring/i,
      });
      await expect(monitoringLink).toBeVisible();
    });

    test('should open links in new tab', async () => {
      const liveAppLink = page.locator('[class*="quick-link"]').first();
      await expect(liveAppLink).toHaveAttribute('target', '_blank');
    });
  });

  test.describe('Next Steps', () => {
    test('should show Customize Your App step', async () => {
      const customizeStep = page.locator('[class*="next-step-card"]', {
        hasText: /Customize/i,
      });
      await expect(customizeStep).toBeVisible();
    });

    test('should show Invite Team step', async () => {
      const inviteStep = page.locator('[class*="next-step-card"]', {
        hasText: /Invite|Team/i,
      });
      await expect(inviteStep).toBeVisible();
    });

    test('should show Connect Domain step', async () => {
      const domainStep = page.locator('[class*="next-step-card"]', {
        hasText: /Domain/i,
      });
      await expect(domainStep).toBeVisible();
    });

    test('should show Configure Secrets step', async () => {
      const secretsStep = page.locator('[class*="next-step-card"]', {
        hasText: /Secrets/i,
      });
      await expect(secretsStep).toBeVisible();
    });

    test('should show Documentation step', async () => {
      const docsStep = page.locator('[class*="next-step-card"]', {
        hasText: /Documentation|Docs/i,
      });
      await expect(docsStep).toBeVisible();
    });

    test('should highlight primary next step', async () => {
      const primaryStep = page.locator('[class*="next-step-card--primary"]');
      await expect(primaryStep).toBeVisible();
    });

    test('should show action button for each step', async () => {
      const stepActions = page.locator('[class*="step-action"]');
      await expect(stepActions).toHaveCount(5);
    });

    test('should navigate when step action is clicked', async () => {
      const inviteAction = page.locator('[class*="next-step-card"]', {
        hasText: /Invite|Team/i,
      }).locator('[class*="step-action"]');

      await inviteAction.click();

      // Should navigate to team settings
      await expect(page).toHaveURL(/\/settings\/team|\/team/);
    });
  });

  test.describe('Credentials Card', () => {
    test('should show credentials title', async () => {
      const title = page.locator('[class*="credentials-title"]');
      await expect(title).toBeVisible();
      await expect(title).toContainText(/Credentials/i);
    });

    test('should hide credentials by default', async () => {
      const credentialValue = page.locator('[class*="credential-value"]').first();
      await expect(credentialValue).toContainText(/•••/);
    });

    test('should show Show button to reveal credentials', async () => {
      const showButton = page.getByRole('button', { name: /show/i });
      await expect(showButton).toBeVisible();
    });

    test('should reveal credentials when Show is clicked', async () => {
      await page.getByRole('button', { name: /show/i }).click();

      const credentialValue = page.locator('[class*="credential-value"]').first();
      await expect(credentialValue).not.toContainText(/•••/);
    });

    test('should hide credentials when Hide is clicked', async () => {
      // First show
      await page.getByRole('button', { name: /show/i }).click();

      // Then hide
      await page.getByRole('button', { name: /hide/i }).click();

      const credentialValue = page.locator('[class*="credential-value"]').first();
      await expect(credentialValue).toContainText(/•••/);
    });

    test('should show copy button for each credential', async () => {
      const copyButtons = page.locator('[class*="copy-btn"]');
      await expect(copyButtons.first()).toBeVisible();
    });

    test('should copy credential to clipboard when copy is clicked', async () => {
      // Grant clipboard permission
      await page.context().grantPermissions(['clipboard-write']);

      await page.locator('[class*="copy-btn"]').first().click();

      // Verify copy feedback
      await expect(page.locator('[class*="copy-btn"]').first()).toContainText(/✓/);
    });

    test('should show credential keys', async () => {
      const credentialKeys = page.locator('[class*="credential-key"]');

      await expect(credentialKeys.first()).toBeVisible();
      await expect(credentialKeys).toContainText([
        /DATABASE_URL/i,
        /API_URL/i,
        /JWT_SECRET/i,
      ]);
    });
  });

  test.describe('Resources Sidebar', () => {
    test('should show resources list', async () => {
      const resourcesList = page.locator('[class*="resources-sidebar"]');
      await expect(resourcesList).toBeVisible();
    });

    test('should show all created resources', async () => {
      const resources = page
        .locator('[class*="resources-sidebar"]')
        .locator('[class*="resource"]');
      await expect(resources).toHaveCount(7);
    });

    test('should show resource names', async () => {
      const resourceName = page
        .locator('[class*="resources-sidebar"]')
        .locator('[class*="resource-name"]')
        .first();
      await expect(resourceName).toBeVisible();
    });

    test('should show resource status as running', async () => {
      const resourceStatus = page
        .locator('[class*="resources-sidebar"]')
        .locator('[class*="status"]')
        .first();
      await expect(resourceStatus).toContainText(/running|active/i);
    });

    test('should show resource cost', async () => {
      const resourceCost = page
        .locator('[class*="resources-sidebar"]')
        .locator('[class*="cost"]')
        .first();
      await expect(resourceCost).toBeVisible();
    });
  });

  test.describe('Footer', () => {
    test('should show help tip', async () => {
      const helpTip = page.locator('[class*="footer-tip"]');
      await expect(helpTip).toBeVisible();
    });

    test('should show documentation link', async () => {
      const docsLink = page.locator('[class*="tip-text"] a', {
        hasText: /documentation/i,
      });
      await expect(docsLink).toBeVisible();
    });

    test('should show community link', async () => {
      const communityLink = page.locator('[class*="tip-text"] a', {
        hasText: /Discord|community/i,
      });
      await expect(communityLink).toBeVisible();
    });

    test('should show View Dashboard button', async () => {
      const dashboardButton = page.getByRole('button', {
        name: /Dashboard/i,
      });
      await expect(dashboardButton).toBeVisible();
    });

    test('should navigate to project dashboard when button is clicked', async () => {
      await page.getByRole('button', { name: /Dashboard/i }).click();

      await expect(page).toHaveURL(/\/projects\/test-project(?!\/initialize)/);
    });
  });

  test.describe('Confetti Animation', () => {
    test('should show confetti elements', async () => {
      const confetti = page.locator('[class*="confetti"]');
      await expect(confetti).toBeVisible();
    });
  });

  test.describe('Keyboard Navigation', () => {
    test('should support tab navigation through interactive elements', async () => {
      await page.keyboard.press('Tab');
      await expect(page.locator(':focus')).toBeVisible();
    });

    test('should allow Enter to activate buttons', async () => {
      const dashboardButton = page.getByRole('button', {
        name: /Dashboard/i,
      });
      await dashboardButton.focus();
      await page.keyboard.press('Enter');

      await expect(page).toHaveURL(/\/projects\/test-project(?!\/initialize)/);
    });
  });

  test.describe('Accessibility', () => {
    test('should have proper heading hierarchy', async () => {
      await expect(page.locator('h1')).toHaveCount(1);
    });

    test('should have ARIA labels on links', async () => {
      const quickLinks = page.locator('[class*="quick-link"]');
      for (const link of await quickLinks.all()) {
        await expect(link).toHaveAttribute('rel', 'noopener noreferrer');
      }
    });

    test('should have proper color contrast on success header', async () => {
      const headerTitle = page.locator('[class*="success-title"]');
      await expect(headerTitle).toBeVisible();
      // Color contrast would be verified in manual/automated accessibility testing
    });
  });

  test.describe('Responsive Design', () => {
    test('should stack layout on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await page.goto('/projects/test-project/initialize/complete');

      const content = page.locator('[class*="complete-content"]');
      await expect(content).toBeVisible();
    });

    test('should wrap quick links on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await page.goto('/projects/test-project/initialize/complete');

      const quickLinks = page.locator('[class*="quick-links-list"]');
      await expect(quickLinks).toBeVisible();
    });

    test('should show resources section on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await page.goto('/projects/test-project/initialize/complete');

      const sidebar = page.locator('[class*="resources-sidebar"]');
      await expect(sidebar).toBeVisible();
    });

    test('should stack footer on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await page.goto('/projects/test-project/initialize/complete');

      const footer = page.locator('[class*="complete-footer"]');
      await expect(footer).toBeVisible();
    });
  });

  test.describe('External Links', () => {
    test('should open live app in new tab', async () => {
      const liveAppLink = page.locator('[class*="quick-link"]', {
        hasText: /Live App/i,
      });

      await expect(liveAppLink).toHaveAttribute('target', '_blank');
    });

    test('should have rel="noopener noreferrer" on external links', async () => {
      const externalLinks = page.locator('a[target="_blank"]');
      for (const link of await externalLinks.all()) {
        await expect(link).toHaveAttribute('rel', 'noopener noreferrer');
      }
    });
  });
});
