import { test, expect } from '@playwright/test';
import { setupTest, teardownTest } from './helpers/test-isolation';

test.describe('Canvas add node flow', () => {
  test.beforeEach(async ({ page }) => {
    await setupTest(page, {
      url: '/canvas-poc',
      seedData: false,
      seedScenario: 'default',
    });
  });

  test.afterEach(async ({ page }) => {
    await teardownTest(page);
  });

  test('click diagnostic Add test node increases node count', async ({
    page,
  }) => {
    const nodesBefore = await page.locator('.react-flow__node').count();

    // Click the Add test node button in the bottom-left panel
    const addBtn = page.getByRole('button', { name: /Add Test Node/i });
    await addBtn.click();

    // Wait a short time for the sync to occur
    await page.waitForTimeout(300);

    const nodesAfter = await page.locator('.react-flow__node').count();

    expect(nodesAfter).toBeGreaterThan(nodesBefore);
  });
});
