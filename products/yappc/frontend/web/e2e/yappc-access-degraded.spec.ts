import { expect, test } from '@playwright/test';

import { PROJECT_ID, bootstrapLifecycleProject, gotoLifecyclePhaseAndWaitFor, setupLifecycleJourneyApi } from './support/lifecycle-fixtures';

test.describe('YAPPC Access and Degraded Journey', () => {
  test('unauthorized user sees access denied state', async ({ page }) => {
    await setupLifecycleJourneyApi(page, { unauthorized: true });
    await page.goto(`/p/${PROJECT_ID}/validate`);
    if (!page.url().includes(`/p/${PROJECT_ID}/validate`)) {
      await page.goto(`/p/${PROJECT_ID}/validate`);
    }
    await expect(page.getByText(/phase access denied/i)).toBeVisible();
  });

  test('dependency degraded states show recovery details', async ({ page }) => {
    await setupLifecycleJourneyApi(page, { degradedDependency: 'DATA_CLOUD' });
    await bootstrapLifecycleProject(page);

    await gotoLifecyclePhaseAndWaitFor(page, 'run', 'run-cockpit');
    await expect(page.getByTestId('phase-degraded-dependency')).toContainText(/DATA_CLOUD/i);
    await expect(page.getByTestId('phase-degraded-recovery')).toContainText(/Recover DATA_CLOUD dependency/i);
  });
});
