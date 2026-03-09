import { test, expect } from '@playwright/test';
import { setupTest, teardownTest } from './helpers/test-isolation';

/**
 * Project Index Route Tests
 * Tests for project overview and navigation within a project
 */

test.describe('Project Index Route', () => {
  test.describe('Project Overview', () => {
    test('should display project title and metadata', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await expect(page.locator('[data-testid="project-title"]')).toBeVisible();
      await expect(
        page.locator('[data-testid="project-description"]')
      ).toBeVisible();
      await expect(page.locator('[data-testid="project-owner"]')).toBeVisible();
      await expect(
        page.locator('[data-testid="project-created"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="project-updated"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should allow editing project title', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="edit-project-title"]').click();

      const titleInput = page.locator('[data-testid="project-title-input"]');
      await titleInput.fill('Updated Project Title');
      await page.keyboard.press('Enter');

      await expect(page.locator('[data-testid="project-title"]')).toHaveText(
        'Updated Project Title'
      );

      await teardownTest(page);
    });

    test('should allow editing project description', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="edit-project-description"]').click();

      const descInput = page.locator(
        '[data-testid="project-description-input"]'
      );
      await descInput.fill('Updated description with new details');
      await page.locator('[data-testid="save-description"]').click();

      await expect(
        page.locator('[data-testid="project-description"]')
      ).toHaveText(/Updated description/);

      await teardownTest(page);
    });
  });

  test.describe('Project Statistics', () => {
    test('should display project statistics cards', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await expect(page.locator('[data-testid="stat-nodes"]')).toBeVisible();
      await expect(page.locator('[data-testid="stat-edges"]')).toBeVisible();
      await expect(page.locator('[data-testid="stat-phases"]')).toBeVisible();
      await expect(
        page.locator('[data-testid="stat-team-members"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should show accurate node count', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      const nodeCount = await page
        .locator('[data-testid="stat-nodes"]')
        .locator('.count')
        .textContent();

      expect(parseInt(nodeCount!)).toBeGreaterThanOrEqual(0);

      await teardownTest(page);
    });

    test('should display completion percentage', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await expect(
        page.locator('[data-testid="project-completion"]')
      ).toBeVisible();

      const percentage = await page
        .locator('[data-testid="completion-percentage"]')
        .textContent();
      expect(percentage).toMatch(/\d+%/);

      await teardownTest(page);
    });
  });

  test.describe('Navigation Tabs', () => {
    test('should show main navigation tabs', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await expect(
        page.locator('[data-testid="nav-tab-canvas"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="nav-tab-phases"]')
      ).toBeVisible();
      await expect(page.locator('[data-testid="nav-tab-team"]')).toBeVisible();
      await expect(
        page.locator('[data-testid="nav-tab-settings"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should navigate to canvas tab', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="nav-tab-canvas"]').click();

      await expect(page).toHaveURL(/\/canvas$/);

      await teardownTest(page);
    });

    test('should navigate to phases tab', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="nav-tab-phases"]').click();

      await expect(page).toHaveURL(/\/phases$/);

      await teardownTest(page);
    });

    test('should navigate to team tab', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="nav-tab-team"]').click();

      await expect(page).toHaveURL(/\/team$/);

      await teardownTest(page);
    });

    test('should highlight active tab', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="nav-tab-canvas"]').click();

      await expect(page.locator('[data-testid="nav-tab-canvas"]')).toHaveClass(
        /active/
      );

      await teardownTest(page);
    });
  });

  test.describe('Phase Overview', () => {
    test('should display phase cards', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await expect(
        page.locator('[data-testid="phase-overview"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="phase-card"]')
      ).toHaveCountGreaterThan(0);

      await teardownTest(page);
    });

    test('should show phase progress indicators', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      const firstPhase = page.locator('[data-testid="phase-card"]').first();

      await expect(
        firstPhase.locator('[data-testid="phase-progress"]')
      ).toBeVisible();
      await expect(
        firstPhase.locator('[data-testid="phase-node-count"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should navigate to phase on card click', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="phase-card"]').first().click();

      await expect(page).toHaveURL(/\/canvas#phase-/);

      await teardownTest(page);
    });
  });

  test.describe('Team Members', () => {
    test('should display team member avatars', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await expect(page.locator('[data-testid="team-members"]')).toBeVisible();
      await expect(
        page.locator('[data-testid="team-member-avatar"]')
      ).toHaveCountGreaterThan(0);

      await teardownTest(page);
    });

    test('should show team member details on hover', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      const avatar = page.locator('[data-testid="team-member-avatar"]').first();
      await avatar.hover();

      await expect(
        page.locator('[data-testid="member-tooltip"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should show invite team member button', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await expect(
        page.locator('[data-testid="invite-member-button"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should open invite modal on button click', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="invite-member-button"]').click();

      await expect(
        page.locator('[data-testid="invite-member-modal"]')
      ).toBeVisible();

      await teardownTest(page);
    });
  });

  test.describe('Recent Activity', () => {
    test('should display recent activity timeline', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await expect(
        page.locator('[data-testid="recent-activity"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="activity-item"]')
      ).toHaveCountGreaterThan(0);

      await teardownTest(page);
    });

    test('should show activity timestamps', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      const activity = page.locator('[data-testid="activity-item"]').first();

      await expect(
        activity.locator('[data-testid="activity-timestamp"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should filter activity by type', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page
        .locator('[data-testid="activity-filter"]')
        .selectOption('canvas');

      const activities = await page
        .locator('[data-testid="activity-item"]')
        .all();
      for (const activity of activities) {
        await expect(activity).toHaveAttribute('data-type', 'canvas');
      }

      await teardownTest(page);
    });
  });

  test.describe('Quick Actions', () => {
    test('should show quick action menu', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await expect(page.locator('[data-testid="quick-actions"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should open canvas from quick action', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="quick-action-open-canvas"]').click();

      await expect(page).toHaveURL(/\/canvas$/);

      await teardownTest(page);
    });

    test('should export project from quick action', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      const downloadPromise = page.waitForEvent('download');
      await page.locator('[data-testid="quick-action-export"]').click();

      const download = await downloadPromise;
      expect(download.suggestedFilename()).toMatch(/project.*\.json$/);

      await teardownTest(page);
    });

    test('should duplicate project', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="quick-action-duplicate"]').click();

      await expect(
        page.locator('[data-testid="duplicate-modal"]')
      ).toBeVisible();

      await page
        .locator('[data-testid="duplicate-project-name"]')
        .fill('Project Copy');
      await page.locator('[data-testid="confirm-duplicate"]').click();

      await expect(page).toHaveURL(/\/p\/proj-\d+$/);

      await teardownTest(page);
    });
  });

  test.describe('Project Settings Access', () => {
    test('should navigate to settings from gear icon', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="project-settings-button"]').click();

      await expect(page).toHaveURL(/\/settings$/);

      await teardownTest(page);
    });

    test('should show delete project option for owners', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1', userRole: 'owner' });

      await page.locator('[data-testid="project-menu"]').click();

      await expect(
        page.locator('[data-testid="delete-project"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should hide delete option for non-owners', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1', userRole: 'viewer' });

      await page.locator('[data-testid="project-menu"]').click();

      await expect(page.locator('[data-testid="delete-project"]')).toBeHidden();

      await teardownTest(page);
    });
  });

  test.describe('Breadcrumb Navigation', () => {
    test('should display breadcrumb trail', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await expect(page.locator('[data-testid="breadcrumb"]')).toBeVisible();
      await expect(
        page.locator('[data-testid="breadcrumb-workspace"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="breadcrumb-project"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should navigate to workspace from breadcrumb', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="breadcrumb-workspace"]').click();

      await expect(page).toHaveURL(/\/w\/ws-1$/);

      await teardownTest(page);
    });

    test('should navigate to dashboard from breadcrumb', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1' });

      await page.locator('[data-testid="breadcrumb-home"]').click();

      await expect(page).toHaveURL(/\/$/);

      await teardownTest(page);
    });
  });

  test.describe('Loading States', () => {
    test('should show loading skeleton while fetching data', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1', delay: 1000 });

      await expect(
        page.locator('[data-testid="project-skeleton"]')
      ).toBeVisible();

      await page.waitForTimeout(1100);

      await expect(
        page.locator('[data-testid="project-skeleton"]')
      ).toBeHidden();

      await teardownTest(page);
    });

    test('should handle project not found', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/nonexistent' });

      await expect(
        page.locator('[data-testid="not-found-message"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="not-found-message"]')
      ).toHaveText(/Project not found/);

      await teardownTest(page);
    });
  });

  test.describe('Performance', () => {
    test('should load project index in under 2 seconds', async ({ page }) => {
      const startTime = Date.now();

      await setupTest(page, { url: '/w/ws-1/p/proj-1' });
      await page.waitForLoadState('networkidle');

      const loadTime = Date.now() - startTime;

      expect(loadTime).toBeLessThan(2000);

      await teardownTest(page);
    });
  });
});
