/**
 * E2E tests – Activity Feed
 *
 * Covers: feed visible on workspace/project pages, items display action + actor,
 * severity chips (info / warn / error), empty state message, feed updates after
 * a state-changing action.
 *
 * All tests are skipped (`test.skip`) because the activity feed requires a
 * live API with seeded data. Enable when the route is reachable in CI.
 */

import { test, expect, type Page } from '@playwright/test';

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:7002';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Navigate to the collaboration / activity page for a workspace. */
async function gotoActivityPage(page: Page, workspaceId = 'demo-workspace-1'): Promise<void> {
  await page.goto(`${BASE_URL}/app/workspaces/${workspaceId}/activity`);
  await page.waitForLoadState('networkidle');
}

/** Navigate to the project index which may embed an activity feed. */
async function gotoProjectIndex(page: Page, projectId = 'demo-project-1'): Promise<void> {
  await page.goto(`${BASE_URL}/app/project/${projectId}`);
  await page.waitForLoadState('networkidle');
}

// ---------------------------------------------------------------------------
// Suites
// ---------------------------------------------------------------------------

test.describe('Activity Feed – workspace level', () => {
  test.skip(true, 'Activity route not yet deployed to CI environment');

  test('activity feed heading is visible', async ({ page }) => {
    await gotoActivityPage(page);
    await expect(page.getByText('Activity Feed')).toBeVisible();
  });

  test('shows empty-state message when no activity exists', async ({ page }) => {
    await gotoActivityPage(page);
    // Assumes fresh / empty workspace — adjust seed if needed
    const empty = page.getByText(/no activity yet/i);
    await expect(empty).toBeVisible();
  });

  test('shows activity items with action and actor', async ({ page }) => {
    await gotoActivityPage(page);
    // Wait for at least one activity card
    const cards = page.locator('[data-testid^="activity-item-"]');
    await expect(cards.first()).toBeVisible({ timeout: 10_000 });

    const firstCard = cards.first();
    // Both action and actor text should be inside the card
    await expect(firstCard.locator('[data-testid="activity-action"]')).toBeVisible();
    await expect(firstCard.locator('[data-testid="activity-actor"]')).toBeVisible();
  });

  test('shows severity chip for each activity item', async ({ page }) => {
    await gotoActivityPage(page);
    const severityChips = page.locator('[data-testid="activity-severity"]');
    const count = await severityChips.count();
    expect(count).toBeGreaterThan(0);
  });

  test('info severity chip has blue styling', async ({ page }) => {
    await gotoActivityPage(page);
    const infoChip = page.locator('[data-testid="activity-severity"][data-severity="info"]').first();
    await expect(infoChip).toBeVisible();
    // Verify class or colour indicates info
    await expect(infoChip).toHaveClass(/blue/);
  });

  test('error severity chip has red styling', async ({ page }) => {
    await gotoActivityPage(page);
    const errorChip = page.locator('[data-testid="activity-severity"][data-severity="error"]').first();
    if (await errorChip.isVisible()) {
      await expect(errorChip).toHaveClass(/red/);
    }
  });
});

test.describe('Activity Feed – project page embed', () => {
  test.skip(true, 'Activity feed embed not yet deployed to CI environment');

  test('project index page contains activity section', async ({ page }) => {
    await gotoProjectIndex(page);
    // Activity Feed heading should appear somewhere on the project page
    await expect(page.getByText('Activity Feed')).toBeVisible({ timeout: 10_000 });
  });

  test('activity feed updates after a requirement is submitted', async ({ page }) => {
    await gotoProjectIndex(page);

    // Create a requirement via the UI
    const captureBtn = page.getByRole('button', { name: /new requirement/i });
    if (await captureBtn.isVisible()) {
      await captureBtn.click();
      const titleInput = page.getByLabel(/title/i);
      await titleInput.fill('E2E activity test requirement');
      await page.getByRole('button', { name: /submit/i }).click();
      await page.waitForLoadState('networkidle');
    }

    // Activity feed should now show the new event
    const activityItems = page.locator('[data-testid^="activity-item-"]');
    await expect(activityItems.first()).toBeVisible({ timeout: 15_000 });
  });
});
