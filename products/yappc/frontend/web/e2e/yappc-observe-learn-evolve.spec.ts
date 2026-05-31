import { expect, test } from '@playwright/test';

import { PROJECT_ID, bootstrapLifecycleProject, gotoLifecyclePhase, setupLifecycleJourneyApi } from './support/lifecycle-fixtures';

test.describe('YAPPC Observe Learn Evolve Journey', () => {
  test('observe issue surfaces then learn and evolve cockpit states render', async ({ page }) => {
    await setupLifecycleJourneyApi(page, { runFailure: true });
    await bootstrapLifecycleProject(page);

    await gotoLifecyclePhase(page, 'observe');
    await expect(page.getByTestId('observe-cockpit')).toBeVisible();

    await gotoLifecyclePhase(page, 'learn');
    await expect(page.getByTestId('learn-cockpit')).toBeVisible();
    await expect(page.getByTestId('learn-advance-action')).toBeVisible();

    await gotoLifecyclePhase(page, 'evolve');
    await expect(page.getByTestId('evolve-cockpit')).toBeVisible();
    await expect(page.getByTestId('evolve-advance-action')).toBeVisible();
  });
});
