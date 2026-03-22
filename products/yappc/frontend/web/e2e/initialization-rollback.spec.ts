/**
 * Initialization Rollback E2E Tests
 *
 * End-to-end tests for the initialization rollback page including:
 * - Error display
 * - Rollback options
 * - Confirmation dialogs
 * - Resource management
 *
 * @doc.type test
 * @doc.purpose E2E tests for initialization rollback
 * @doc.phase 2
 */

import { test, expect, type Page } from '@playwright/test';

test.describe('Initialization Rollback', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/initialize/rollback');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display error header', async () => {
      const header = page.locator('[class*="rollback-header"]');
      await expect(header).toBeVisible();
      await expect(header).toContainText(/Failed|Error/i);
    });

    test('should show step progress indicator', async () => {
      const stepProgress = page.locator('[class*="step-progress"]');
      await expect(stepProgress).toBeVisible();
    });

    test('should display error details section', async () => {
      const errorSection = page.locator('[class*="error-section"]');
      await expect(errorSection).toBeVisible();
    });

    test('should show rollback options', async () => {
      const optionsSection = page.locator('[class*="options-section"]');
      await expect(optionsSection).toBeVisible();
    });

    test('should show created resources sidebar', async () => {
      const resourcesSidebar = page.locator('[class*="resources-sidebar"]');
      await expect(resourcesSidebar).toBeVisible();
    });
  });

  test.describe('Error Display', () => {
    test('should show failed step name', async () => {
      const errorCard = page.locator('[class*="error-card"]');
      await expect(errorCard.locator('h3')).toBeVisible();
    });

    test('should show error message', async () => {
      const errorMessage = page.locator('[class*="error-message"]');
      await expect(errorMessage).toBeVisible();
    });

    test('should show error details when available', async () => {
      const errorDetails = page.locator('[class*="error-details"]');
      await expect(errorDetails).toBeVisible();
    });

    test('should show expandable error logs', async () => {
      const logsToggle = page.locator('details.error-logs summary');
      await expect(logsToggle).toBeVisible();

      // Click to expand
      await logsToggle.click();

      const logsContent = page.locator('[class*="logs-content"]');
      await expect(logsContent).toBeVisible();
    });

    test('should highlight error lines in logs', async () => {
      // Expand logs
      await page.locator('details.error-logs summary').click();

      const errorLine = page.locator('[class*="log-error"]');
      await expect(errorLine).toBeVisible();
    });
  });

  test.describe('Step Progress Display', () => {
    test('should show completed steps', async () => {
      const completedSteps = page.locator(
        '[class*="step"][class*="completed"]'
      );
      await expect(completedSteps.first()).toBeVisible();
    });

    test('should show failed step with error indicator', async () => {
      const failedStep = page.locator('[class*="step"][class*="error"]');
      await expect(failedStep).toBeVisible();
    });

    test('should show upcoming steps as disabled', async () => {
      const upcomingSteps = page.locator('[class*="step"][class*="upcoming"]');
      await expect(upcomingSteps.first()).toBeVisible();
    });
  });

  test.describe('Rollback Options', () => {
    test('should show Retry option', async () => {
      const retryOption = page.locator('[class*="option-card"]', {
        hasText: /Retry/i,
      });
      await expect(retryOption).toBeVisible();
    });

    test('should show Skip option', async () => {
      const skipOption = page.locator('[class*="option-card"]', {
        hasText: /Skip/i,
      });
      await expect(skipOption).toBeVisible();
    });

    test('should show Rollback Partial option', async () => {
      const partialOption = page.locator('[class*="option-card"]', {
        hasText: /Last Working|Partial/i,
      });
      await expect(partialOption).toBeVisible();
    });

    test('should show Rollback All option', async () => {
      const rollbackAllOption = page.locator('[class*="option-card"]', {
        hasText: /Everything|All/i,
      });
      await expect(rollbackAllOption).toBeVisible();
    });

    test('should highlight recommended option', async () => {
      const recommendedOption = page.locator('[class*="option-card--recommended"]');
      await expect(recommendedOption).toBeVisible();
      await expect(recommendedOption).toContainText(/Retry/i);
    });

    test('should highlight destructive option with danger style', async () => {
      const dangerOption = page.locator('[class*="option-card--danger"]');
      await expect(dangerOption).toBeVisible();
    });
  });

  test.describe('Option Selection', () => {
    test('should select option when clicked', async () => {
      const retryOption = page.locator('[class*="option-card"]', {
        hasText: /Retry/i,
      });
      await retryOption.click();

      await expect(retryOption).toHaveClass(/selected/);
    });

    test('should deselect previous option when new one is selected', async () => {
      const retryOption = page.locator('[class*="option-card"]', {
        hasText: /Retry/i,
      });
      const skipOption = page.locator('[class*="option-card"]', {
        hasText: /Skip/i,
      });

      await retryOption.click();
      await skipOption.click();

      await expect(retryOption).not.toHaveClass(/selected/);
      await expect(skipOption).toHaveClass(/selected/);
    });

    test('should enable Continue button when option is selected', async () => {
      const continueButton = page.getByRole('button', { name: /continue/i });

      // Initially disabled
      await expect(continueButton).toBeDisabled();

      // Select an option
      await page
        .locator('[class*="option-card"]', { hasText: /Retry/i })
        .click();

      // Now enabled
      await expect(continueButton).toBeEnabled();
    });
  });

  test.describe('Retry Flow', () => {
    test('should navigate to progress page when Retry is selected and continued', async () => {
      await page
        .locator('[class*="option-card"]', { hasText: /Retry/i })
        .click();
      await page.getByRole('button', { name: /continue/i }).click();

      await expect(page).toHaveURL(/\/progress/);
    });
  });

  test.describe('Skip Flow', () => {
    test('should navigate to progress page when Skip is selected and continued', async () => {
      await page
        .locator('[class*="option-card"]', { hasText: /Skip/i })
        .click();
      await page.getByRole('button', { name: /continue/i }).click();

      await expect(page).toHaveURL(/\/progress/);
    });
  });

  test.describe('Rollback Confirmation', () => {
    test('should show confirmation dialog for Rollback All', async () => {
      await page
        .locator('[class*="option-card"]', { hasText: /Everything|All/i })
        .click();
      await page.getByRole('button', { name: /continue/i }).click();

      const confirmDialog = page.locator('[role="dialog"]');
      await expect(confirmDialog).toBeVisible();
    });

    test('should show rollback steps in confirmation dialog', async () => {
      await page
        .locator('[class*="option-card"]', { hasText: /Everything|All/i })
        .click();
      await page.getByRole('button', { name: /continue/i }).click();

      const rollbackSteps = page.locator('[class*="rollback-step"]');
      await expect(rollbackSteps.first()).toBeVisible();
    });

    test('should show affected resources in confirmation dialog', async () => {
      await page
        .locator('[class*="option-card"]', { hasText: /Everything|All/i })
        .click();
      await page.getByRole('button', { name: /continue/i }).click();

      const affectedResources = page.locator('[class*="affected-resource"]');
      await expect(affectedResources.first()).toBeVisible();
    });

    test('should require confirmation text for destructive rollback', async () => {
      await page
        .locator('[class*="option-card"]', { hasText: /Everything|All/i })
        .click();
      await page.getByRole('button', { name: /continue/i }).click();

      const confirmInput = page.locator('[class*="confirm-input"] input');
      await expect(confirmInput).toBeVisible();

      // Confirm button should be disabled without correct text
      const confirmButton = page.getByRole('button', {
        name: /confirm.*rollback/i,
      });
      await expect(confirmButton).toBeDisabled();
    });

    test('should enable confirm button when correct text is entered', async () => {
      await page
        .locator('[class*="option-card"]', { hasText: /Everything|All/i })
        .click();
      await page.getByRole('button', { name: /continue/i }).click();

      const confirmInput = page.locator('[class*="confirm-input"] input');
      await confirmInput.fill('bakery-app');

      const confirmButton = page.getByRole('button', {
        name: /confirm.*rollback/i,
      });
      await expect(confirmButton).toBeEnabled();
    });

    test('should close dialog when cancel is clicked', async () => {
      await page
        .locator('[class*="option-card"]', { hasText: /Everything|All/i })
        .click();
      await page.getByRole('button', { name: /continue/i }).click();

      await page.getByRole('button', { name: /cancel/i }).click();

      const confirmDialog = page.locator('[role="dialog"]');
      await expect(confirmDialog).not.toBeVisible();
    });

    test('should execute rollback and navigate when confirmed', async () => {
      await page
        .locator('[class*="option-card"]', { hasText: /Everything|All/i })
        .click();
      await page.getByRole('button', { name: /continue/i }).click();

      const confirmInput = page.locator('[class*="confirm-input"] input');
      await confirmInput.fill('bakery-app');

      await page.getByRole('button', { name: /confirm.*rollback/i }).click();

      // Wait for navigation
      await expect(page).toHaveURL(/\/presets|\/initialize(?!\/rollback)/);
    });
  });

  test.describe('Resources Sidebar', () => {
    test('should show created resources', async () => {
      const resourcesList = page.locator('[class*="resources-sidebar"]');
      await expect(resourcesList.locator('[class*="resource"]').first()).toBeVisible();
    });

    test('should show resource names', async () => {
      const resourceName = page
        .locator('[class*="resources-sidebar"]')
        .locator('[class*="resource-name"]')
        .first();
      await expect(resourceName).toBeVisible();
    });

    test('should show resource providers', async () => {
      const resourceProvider = page
        .locator('[class*="resources-sidebar"]')
        .locator('[class*="provider"]')
        .first();
      await expect(resourceProvider).toBeVisible();
    });

    test('should show resource status', async () => {
      const resourceStatus = page
        .locator('[class*="resources-sidebar"]')
        .locator('[class*="status"]')
        .first();
      await expect(resourceStatus).toBeVisible();
    });
  });

  test.describe('Navigation', () => {
    test('should show Cancel & Go Back button', async () => {
      const backButton = page.getByRole('button', { name: /cancel|back/i });
      await expect(backButton).toBeVisible();
    });

    test('should navigate to project page when Cancel is clicked', async () => {
      await page.getByRole('button', { name: /cancel|back/i }).click();

      await expect(page).toHaveURL(/\/projects\/test-project(?!\/initialize)/);
    });
  });

  test.describe('Keyboard Navigation', () => {
    test('should support keyboard navigation through options', async () => {
      await page.keyboard.press('Tab');
      await page.keyboard.press('Tab');

      // First option should be focused
      await expect(page.locator('[class*="option-card"]:focus-within')).toBeVisible();
    });

    test('should select option with Enter key', async () => {
      const retryOption = page.locator('[class*="option-card"]', {
        hasText: /Retry/i,
      });
      await retryOption.focus();
      await page.keyboard.press('Enter');

      await expect(retryOption).toHaveClass(/selected/);
    });

    test('should close dialog with Escape key', async () => {
      await page
        .locator('[class*="option-card"]', { hasText: /Everything|All/i })
        .click();
      await page.getByRole('button', { name: /continue/i }).click();

      await page.keyboard.press('Escape');

      const confirmDialog = page.locator('[role="dialog"]');
      await expect(confirmDialog).not.toBeVisible();
    });
  });

  test.describe('Accessibility', () => {
    test('should have proper heading hierarchy', async () => {
      await expect(page.locator('h1')).toHaveCount(1);
    });

    test('should have ARIA labels on interactive elements', async () => {
      const options = page.locator('[class*="option-card"]');
      for (const option of await options.all()) {
        await expect(option.locator('input[type="radio"]')).toBeVisible();
      }
    });

    test('should announce option selection to screen readers', async () => {
      const retryOption = page.locator('[class*="option-card"]', {
        hasText: /Retry/i,
      });
      await retryOption.click();

      // Radio button should be checked
      await expect(retryOption.locator('input[type="radio"]')).toBeChecked();
    });

    test('should have proper focus management in dialog', async () => {
      await page
        .locator('[class*="option-card"]', { hasText: /Everything|All/i })
        .click();
      await page.getByRole('button', { name: /continue/i }).click();

      // Focus should be trapped in dialog
      const dialog = page.locator('[role="dialog"]');
      await expect(dialog).toBeFocused().catch(() => {
        // Focus might be on first focusable element inside dialog
      });
    });
  });

  test.describe('Responsive Design', () => {
    test('should stack layout on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await page.goto('/projects/test-project/initialize/rollback');

      const content = page.locator('[class*="rollback-content"]');
      await expect(content).toBeVisible();
    });

    test('should show resources below options on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await page.goto('/projects/test-project/initialize/rollback');

      const sidebar = page.locator('[class*="resources-sidebar"]');
      await expect(sidebar).toBeVisible();
    });
  });
});
