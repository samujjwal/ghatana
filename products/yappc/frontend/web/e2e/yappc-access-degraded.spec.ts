import { expect, test } from '@playwright/test';

import { bootstrapLifecycleProject, gotoLifecyclePhase, setupLifecycleJourneyApi } from './support/lifecycle-fixtures';

test.describe('YAPPC Access and Degraded Journey', () => {
  test('unauthorized user sees access denied state', async ({ page }) => {
    await setupLifecycleJourneyApi(page, { unauthorized: true });
    await bootstrapLifecycleProject(page);

    await gotoLifecyclePhase(page, 'validate');
    await expect(page.getByText(/phase access denied/i)).toBeVisible();
  });

  test('dependency degraded states show recovery details', async ({ page }) => {
    await setupLifecycleJourneyApi(page, { degradedDependency: 'DATA_CLOUD' });
    await bootstrapLifecycleProject(page);

    await gotoLifecyclePhase(page, 'run');
    await expect(page.getByTestId('phase-degraded-dependency')).toContainText(/DATA_CLOUD/i);
    await expect(page.getByTestId('phase-degraded-recovery')).toContainText(/Recover DATA_CLOUD dependency/i);
  });
});
