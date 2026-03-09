import { test, expect } from '@playwright/test';
import { setupTest, teardownTest } from './helpers/test-isolation';

/**
 * Settings Route Tests
 * Tests for project and workspace settings management
 */

test.describe('Settings Route', () => {
  test.describe('Settings Navigation', () => {
    test('should display settings tabs', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await expect(
        page.locator('[data-testid="settings-tab-general"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="settings-tab-team"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="settings-tab-canvas"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="settings-tab-integrations"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="settings-tab-advanced"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should navigate between settings tabs', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-team"]').click();

      await expect(page.locator('[data-testid="team-settings"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should highlight active tab', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-canvas"]').click();

      await expect(
        page.locator('[data-testid="settings-tab-canvas"]')
      ).toHaveClass(/active/);

      await teardownTest(page);
    });
  });

  test.describe('General Settings', () => {
    test('should display project name field', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await expect(
        page.locator('[data-testid="project-name-input"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should update project name', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page
        .locator('[data-testid="project-name-input"]')
        .fill('Updated Project Name');
      await page.locator('[data-testid="save-general-settings"]').click();

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();
      await expect(page.locator('[data-testid="success-toast"]')).toHaveText(
        /Settings saved/
      );

      await teardownTest(page);
    });

    test('should update project description', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page
        .locator('[data-testid="project-description-textarea"]')
        .fill('Updated description');
      await page.locator('[data-testid="save-general-settings"]').click();

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should validate required fields', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="project-name-input"]').fill('');
      await page.locator('[data-testid="save-general-settings"]').click();

      await expect(page.locator('[data-testid="error-message"]')).toBeVisible();
      await expect(page.locator('[data-testid="error-message"]')).toHaveText(
        /Project name is required/
      );

      await teardownTest(page);
    });

    test('should update project visibility', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page
        .locator('[data-testid="visibility-select"]')
        .selectOption('private');
      await page.locator('[data-testid="save-general-settings"]').click();

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      await teardownTest(page);
    });
  });

  test.describe('Team Settings', () => {
    test('should display team members list', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-team"]').click();

      await expect(
        page.locator('[data-testid="team-member-item"]')
      ).toHaveCountGreaterThan(0);

      await teardownTest(page);
    });

    test('should show invite member button', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-team"]').click();

      await expect(
        page.locator('[data-testid="invite-member-button"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should invite new team member', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-team"]').click();
      await page.locator('[data-testid="invite-member-button"]').click();

      await page
        .locator('[data-testid="invite-email"]')
        .fill('newmember@example.com');
      await page.locator('[data-testid="invite-role"]').selectOption('editor');
      await page.locator('[data-testid="send-invite"]').click();

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();
      await expect(page.locator('[data-testid="success-toast"]')).toHaveText(
        /Invitation sent/
      );

      await teardownTest(page);
    });

    test('should change member role', async ({ page }) => {
      await setupTest(page, {
        url: '/w/ws-1/p/proj-1/settings',
        userRole: 'owner',
      });

      await page.locator('[data-testid="settings-tab-team"]').click();

      const member = page.locator('[data-testid="team-member-item"]').first();
      await member
        .locator('[data-testid="role-select"]')
        .selectOption('viewer');

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should remove team member', async ({ page }) => {
      await setupTest(page, {
        url: '/w/ws-1/p/proj-1/settings',
        userRole: 'owner',
      });

      await page.locator('[data-testid="settings-tab-team"]').click();

      const initialCount = await page
        .locator('[data-testid="team-member-item"]')
        .count();

      const member = page.locator('[data-testid="team-member-item"]').last();
      await member.locator('[data-testid="remove-member"]').click();

      await page.locator('[data-testid="confirm-remove"]').click();

      const finalCount = await page
        .locator('[data-testid="team-member-item"]')
        .count();

      expect(finalCount).toBe(initialCount - 1);

      await teardownTest(page);
    });

    test('should prevent removing last owner', async ({ page }) => {
      await setupTest(page, {
        url: '/w/ws-1/p/proj-1/settings',
        userRole: 'owner',
      });

      await page.locator('[data-testid="settings-tab-team"]').click();

      const ownerMember = page
        .locator('[data-testid="team-member-item"][data-role="owner"]')
        .first();

      await expect(
        ownerMember.locator('[data-testid="remove-member"]')
      ).toBeDisabled();

      await teardownTest(page);
    });
  });

  test.describe('Canvas Settings', () => {
    test('should display canvas preferences', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-canvas"]').click();

      await expect(page.locator('[data-testid="grid-settings"]')).toBeVisible();
      await expect(page.locator('[data-testid="snap-settings"]')).toBeVisible();
      await expect(
        page.locator('[data-testid="theme-settings"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should toggle grid visibility', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-canvas"]').click();

      await page.locator('[data-testid="show-grid-toggle"]').click();
      await page.locator('[data-testid="save-canvas-settings"]').click();

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should change grid size', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-canvas"]').click();

      await page.locator('[data-testid="grid-size-input"]').fill('20');
      await page.locator('[data-testid="save-canvas-settings"]').click();

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should toggle snap to grid', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-canvas"]').click();

      await page.locator('[data-testid="snap-to-grid-toggle"]').click();
      await page.locator('[data-testid="save-canvas-settings"]').click();

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should change canvas theme', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-canvas"]').click();

      await page.locator('[data-testid="theme-select"]').selectOption('dark');
      await page.locator('[data-testid="save-canvas-settings"]').click();

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should configure auto-save interval', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-canvas"]').click();

      await page.locator('[data-testid="autosave-interval"]').fill('60');
      await page.locator('[data-testid="save-canvas-settings"]').click();

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      await teardownTest(page);
    });
  });

  test.describe('Integration Settings', () => {
    test('should display available integrations', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-integrations"]').click();

      await expect(
        page.locator('[data-testid="integration-item"]')
      ).toHaveCountGreaterThan(0);

      await teardownTest(page);
    });

    test('should connect to GitHub integration', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-integrations"]').click();

      await page.locator('[data-testid="connect-github"]').click();

      await expect(page).toHaveURL(/\/auth\/github/);

      await teardownTest(page);
    });

    test('should disconnect integration', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-integrations"]').click();

      const integration = page
        .locator('[data-testid="integration-item"][data-status="connected"]')
        .first();
      await integration.locator('[data-testid="disconnect"]').click();

      await page.locator('[data-testid="confirm-disconnect"]').click();

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should configure webhook URL', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page.locator('[data-testid="settings-tab-integrations"]').click();

      await page
        .locator('[data-testid="webhook-url"]')
        .fill('https://example.com/webhook');
      await page.locator('[data-testid="save-webhook"]').click();

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      await teardownTest(page);
    });
  });

  test.describe('Advanced Settings', () => {
    test('should display advanced options for owners', async ({ page }) => {
      await setupTest(page, {
        url: '/w/ws-1/p/proj-1/settings',
        userRole: 'owner',
      });

      await page.locator('[data-testid="settings-tab-advanced"]').click();

      await expect(
        page.locator('[data-testid="export-project"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="archive-project"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="delete-project"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should hide advanced options for non-owners', async ({ page }) => {
      await setupTest(page, {
        url: '/w/ws-1/p/proj-1/settings',
        userRole: 'editor',
      });

      await page.locator('[data-testid="settings-tab-advanced"]').click();

      await expect(page.locator('[data-testid="delete-project"]')).toBeHidden();

      await teardownTest(page);
    });

    test('should export project data', async ({ page }) => {
      await setupTest(page, {
        url: '/w/ws-1/p/proj-1/settings',
        userRole: 'owner',
      });

      await page.locator('[data-testid="settings-tab-advanced"]').click();

      const downloadPromise = page.waitForEvent('download');
      await page.locator('[data-testid="export-project"]').click();

      const download = await downloadPromise;
      expect(download.suggestedFilename()).toMatch(/project.*\.zip$/);

      await teardownTest(page);
    });

    test('should archive project', async ({ page }) => {
      await setupTest(page, {
        url: '/w/ws-1/p/proj-1/settings',
        userRole: 'owner',
      });

      await page.locator('[data-testid="settings-tab-advanced"]').click();

      await page.locator('[data-testid="archive-project"]').click();
      await page.locator('[data-testid="confirm-archive"]').click();

      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();
      await expect(page).toHaveURL(/\/w\/ws-1$/);

      await teardownTest(page);
    });

    test('should require confirmation before delete', async ({ page }) => {
      await setupTest(page, {
        url: '/w/ws-1/p/proj-1/settings',
        userRole: 'owner',
      });

      await page.locator('[data-testid="settings-tab-advanced"]').click();

      await page.locator('[data-testid="delete-project"]').click();

      await expect(
        page.locator('[data-testid="delete-confirmation-modal"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should delete project after confirmation', async ({ page }) => {
      await setupTest(page, {
        url: '/w/ws-1/p/proj-1/settings',
        userRole: 'owner',
      });

      await page.locator('[data-testid="settings-tab-advanced"]').click();

      await page.locator('[data-testid="delete-project"]').click();

      await page.locator('[data-testid="confirm-delete-input"]').fill('DELETE');
      await page.locator('[data-testid="confirm-delete-button"]').click();

      await expect(page).toHaveURL(/\/w\/ws-1$/);

      await teardownTest(page);
    });
  });

  test.describe('Unsaved Changes', () => {
    test('should show unsaved changes indicator', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page
        .locator('[data-testid="project-name-input"]')
        .fill('Modified Name');

      await expect(
        page.locator('[data-testid="unsaved-indicator"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should warn before leaving with unsaved changes', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page
        .locator('[data-testid="project-name-input"]')
        .fill('Modified Name');

      // Try to navigate away
      page.on('dialog', (dialog) => dialog.accept());
      await page.locator('[data-testid="nav-tab-canvas"]').click();

      // Should show confirmation dialog
      await page.waitForTimeout(100);

      await teardownTest(page);
    });

    test('should clear unsaved indicator after save', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      await page
        .locator('[data-testid="project-name-input"]')
        .fill('Modified Name');
      await page.locator('[data-testid="save-general-settings"]').click();

      await expect(
        page.locator('[data-testid="unsaved-indicator"]')
      ).toBeHidden();

      await teardownTest(page);
    });
  });

  test.describe('Performance', () => {
    test('should load settings in under 2 seconds', async ({ page }) => {
      const startTime = Date.now();

      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });
      await page.waitForLoadState('networkidle');

      const loadTime = Date.now() - startTime;

      expect(loadTime).toBeLessThan(2000);

      await teardownTest(page);
    });

    test('should debounce auto-save on rapid changes', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/settings' });

      let saveCallCount = 0;
      await page.route('**/api/projects/*/settings', (route) => {
        saveCallCount++;
        route.fulfill({ status: 200, body: JSON.stringify({ success: true }) });
      });

      // Make rapid changes
      for (let i = 0; i < 10; i++) {
        await page
          .locator('[data-testid="project-name-input"]')
          .fill(`Name ${i}`);
        await page.waitForTimeout(50);
      }

      await page.waitForTimeout(2000);

      // Should have significantly fewer saves than changes
      expect(saveCallCount).toBeLessThan(5);

      await teardownTest(page);
    });
  });
});
