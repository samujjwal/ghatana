import { test, expect } from '@playwright/test';
import { setupTest, teardownTest } from './helpers/test-isolation';

/**
 * Dashboard Route Tests
 * Tests for the main landing page and workspace selection
 */

test.describe('Dashboard Route', () => {
  test.describe('Guest Landing (Unauthenticated)', () => {
    test('should show welcome message for unauthenticated users', async ({
      page,
    }) => {
      await setupTest(page, { url: '/', skipAuth: true });

      await expect(
        page.locator('[data-testid="welcome-message"]')
      ).toBeVisible();
      await expect(page.locator('[data-testid="welcome-message"]')).toHaveText(
        /Welcome to YAPPC/
      );

      await teardownTest(page);
    });

    test('should show sign in button for guests', async ({ page }) => {
      await setupTest(page, { url: '/', skipAuth: true });

      await expect(
        page.locator('[data-testid="sign-in-button"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="sign-up-button"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should redirect to auth when clicking sign in', async ({ page }) => {
      await setupTest(page, { url: '/', skipAuth: true });

      await page.locator('[data-testid="sign-in-button"]').click();

      await expect(page).toHaveURL(/\/auth\/signin/);

      await teardownTest(page);
    });

    test('should show product features for guests', async ({ page }) => {
      await setupTest(page, { url: '/', skipAuth: true });

      await expect(
        page.locator('[data-testid="feature-canvas"]')
      ).toBeVisible();
      await expect(page.locator('[data-testid="feature-ai"]')).toBeVisible();
      await expect(
        page.locator('[data-testid="feature-collaboration"]')
      ).toBeVisible();

      await teardownTest(page);
    });
  });

  test.describe('Authenticated Dashboard', () => {
    test('should show user profile in header', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await expect(page.locator('[data-testid="user-profile"]')).toBeVisible();
      await expect(page.locator('[data-testid="user-avatar"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should display workspace list', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await expect(
        page.locator('[data-testid="workspace-list"]')
      ).toBeVisible();
      await expect(page.locator('[data-testid="workspace-item"]')).toHaveCount(
        3
      );

      await teardownTest(page);
    });

    test('should show create workspace button', async ({ page }) => {
      await setupTest(page, { url: '/' });

      const createButton = page.locator(
        '[data-testid="create-workspace-button"]'
      );
      await expect(createButton).toBeVisible();
      await expect(createButton).toBeEnabled();

      await teardownTest(page);
    });

    test('should display recent projects', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await expect(
        page.locator('[data-testid="recent-projects"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="recent-project-item"]')
      ).toHaveCount(5);

      await teardownTest(page);
    });

    test('should show activity feed', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await expect(page.locator('[data-testid="activity-feed"]')).toBeVisible();
      await expect(
        page.locator('[data-testid="activity-item"]').first()
      ).toBeVisible();

      await teardownTest(page);
    });
  });

  test.describe('Workspace Selection', () => {
    test('should navigate to workspace on click', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await page.locator('[data-testid="workspace-item"]').first().click();

      await expect(page).toHaveURL(/\/w\/ws-/);

      await teardownTest(page);
    });

    test('should show workspace details on hover', async ({ page }) => {
      await setupTest(page, { url: '/' });

      const workspace = page.locator('[data-testid="workspace-item"]').first();
      await workspace.hover();

      await expect(
        page.locator('[data-testid="workspace-details-tooltip"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should allow creating new workspace', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await page.locator('[data-testid="create-workspace-button"]').click();

      await expect(
        page.locator('[data-testid="create-workspace-modal"]')
      ).toBeVisible();

      await page
        .locator('[data-testid="workspace-name-input"]')
        .fill('Test Workspace');
      await page.locator('[data-testid="create-workspace-submit"]').click();

      await expect(page).toHaveURL(/\/w\/ws-/);

      await teardownTest(page);
    });

    test('should filter workspaces by search', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await page
        .locator('[data-testid="workspace-search"]')
        .fill('Development');

      await expect(page.locator('[data-testid="workspace-item"]')).toHaveCount(
        1
      );

      await teardownTest(page);
    });

    test('should sort workspaces by different criteria', async ({ page }) => {
      await setupTest(page, { url: '/' });

      // Sort by name
      await page.locator('[data-testid="workspace-sort"]').selectOption('name');
      const firstByName = await page
        .locator('[data-testid="workspace-item"]')
        .first()
        .textContent();

      // Sort by recent
      await page
        .locator('[data-testid="workspace-sort"]')
        .selectOption('recent');
      const firstByRecent = await page
        .locator('[data-testid="workspace-item"]')
        .first()
        .textContent();

      expect(firstByName).not.toBe(firstByRecent);

      await teardownTest(page);
    });
  });

  test.describe('Project Navigation', () => {
    test('should navigate to project from recent list', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await page.locator('[data-testid="recent-project-item"]').first().click();

      await expect(page).toHaveURL(/\/w\/ws-.*\/p\/.*/);

      await teardownTest(page);
    });

    test('should show project preview on hover', async ({ page }) => {
      await setupTest(page, { url: '/' });

      const project = page
        .locator('[data-testid="recent-project-item"]')
        .first();
      await project.hover();

      await expect(
        page.locator('[data-testid="project-preview-tooltip"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should display project metadata', async ({ page }) => {
      await setupTest(page, { url: '/' });

      const project = page
        .locator('[data-testid="recent-project-item"]')
        .first();

      await expect(
        project.locator('[data-testid="project-name"]')
      ).toBeVisible();
      await expect(
        project.locator('[data-testid="project-updated"]')
      ).toBeVisible();
      await expect(
        project.locator('[data-testid="project-owner"]')
      ).toBeVisible();

      await teardownTest(page);
    });
  });

  test.describe('Quick Actions', () => {
    test('should show quick action buttons', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await expect(
        page.locator('[data-testid="quick-action-new-project"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="quick-action-templates"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="quick-action-import"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should create new project from quick action', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await page.locator('[data-testid="quick-action-new-project"]').click();

      await expect(
        page.locator('[data-testid="new-project-modal"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should open templates gallery', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await page.locator('[data-testid="quick-action-templates"]').click();

      await expect(
        page.locator('[data-testid="templates-modal"]')
      ).toBeVisible();
      await expect(page.locator('[data-testid="template-card"]')).toHaveCount(
        10
      );

      await teardownTest(page);
    });
  });

  test.describe('Notifications & Updates', () => {
    test('should display notification badge when present', async ({ page }) => {
      await setupTest(page, { url: '/' });

      // Simulate notification
      await page.evaluate(() => {
        window.dispatchEvent(
          new CustomEvent('notification:new', {
            detail: { count: 3 },
          })
        );
      });

      await expect(
        page.locator('[data-testid="notification-badge"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="notification-badge"]')
      ).toHaveText('3');

      await teardownTest(page);
    });

    test('should open notifications panel on bell click', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await page.locator('[data-testid="notification-bell"]').click();

      await expect(
        page.locator('[data-testid="notification-panel"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should mark notification as read', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await page.locator('[data-testid="notification-bell"]').click();

      const notification = page
        .locator('[data-testid="notification-item"]')
        .first();
      await notification.locator('[data-testid="mark-read"]').click();

      await expect(notification).toHaveClass(/read/);

      await teardownTest(page);
    });
  });

  test.describe('Search Functionality', () => {
    test('should show global search bar', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await expect(page.locator('[data-testid="global-search"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should search across workspaces and projects', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await page.locator('[data-testid="global-search"]').fill('API');
      await page.keyboard.press('Enter');

      await expect(
        page.locator('[data-testid="search-results"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="search-result-item"]')
      ).toHaveCountGreaterThan(0);

      await teardownTest(page);
    });

    test('should filter search results by type', async ({ page }) => {
      await setupTest(page, { url: '/' });

      await page.locator('[data-testid="global-search"]').fill('test');
      await page.keyboard.press('Enter');

      await page.locator('[data-testid="filter-projects"]').click();

      const results = await page
        .locator('[data-testid="search-result-item"]')
        .all();
      for (const result of results) {
        await expect(result).toHaveAttribute('data-type', 'project');
      }

      await teardownTest(page);
    });
  });

  test.describe('Responsive Behavior', () => {
    test('should adapt layout for mobile viewport', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await setupTest(page, { url: '/' });

      // Mobile menu should be visible
      await expect(
        page.locator('[data-testid="mobile-menu-button"]')
      ).toBeVisible();

      // Desktop sidebar should be hidden
      await expect(
        page.locator('[data-testid="desktop-sidebar"]')
      ).toBeHidden();

      await teardownTest(page);
    });

    test('should show full layout on desktop', async ({ page }) => {
      await page.setViewportSize({ width: 1920, height: 1080 });
      await setupTest(page, { url: '/' });

      await expect(
        page.locator('[data-testid="desktop-sidebar"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="mobile-menu-button"]')
      ).toBeHidden();

      await teardownTest(page);
    });
  });

  test.describe('Performance', () => {
    test('should load dashboard in under 2 seconds', async ({ page }) => {
      const startTime = Date.now();

      await setupTest(page, { url: '/' });
      await page.waitForLoadState('networkidle');

      const loadTime = Date.now() - startTime;

      expect(loadTime).toBeLessThan(2000);

      await teardownTest(page);
    });

    test('should lazy load activity feed items', async ({ page }) => {
      await setupTest(page, { url: '/' });

      // Scroll to bottom of activity feed
      await page.locator('[data-testid="activity-feed"]').evaluate((el) => {
        el.scrollTop = el.scrollHeight;
      });

      await page.waitForTimeout(500);

      // More items should be loaded
      const itemCount = await page
        .locator('[data-testid="activity-item"]')
        .count();
      expect(itemCount).toBeGreaterThan(10);

      await teardownTest(page);
    });
  });
});
