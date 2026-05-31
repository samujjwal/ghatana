import { expect, test } from '@playwright/test';

import { PROJECT_ID, bootstrapLifecycleProject, gotoLifecyclePhase, setupLifecycleJourneyApi } from './support/lifecycle-fixtures';

test.describe('YAPPC Intent to Shape Journey', () => {
  test('project opens intent then navigates to shape with backend packet state', async ({ page }) => {
    await setupLifecycleJourneyApi(page);
    await bootstrapLifecycleProject(page);

    await gotoLifecyclePhase(page, 'intent');
    await expect(page.getByTestId('intent-cockpit')).toBeVisible();
    await expect(page.getByTestId('intent-primary-action-card')).toBeVisible();

    await gotoLifecyclePhase(page, 'shape');
    await expect(page.getByTestId('shape-cockpit')).toBeVisible();
    await expect(page.getByTestId('shape-primary-action-card')).toBeVisible();
  });
});
