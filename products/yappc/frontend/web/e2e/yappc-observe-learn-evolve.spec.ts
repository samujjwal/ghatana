import { expect, test } from '@playwright/test';

import { bootstrapLifecycleProject, gotoLifecyclePhaseAndWaitFor, setupLifecycleJourneyApi } from './support/lifecycle-fixtures';

test.describe('YAPPC Observe Learn Evolve Journey', () => {
  test.setTimeout(90000);

  test('observe issue surfaces then learn and evolve cockpit states render', async ({ page }) => {
    await setupLifecycleJourneyApi(page, { runFailure: true });
    await bootstrapLifecycleProject(page);

    await gotoLifecyclePhaseAndWaitFor(page, 'observe', 'observe-cockpit');

    await gotoLifecyclePhaseAndWaitFor(page, 'learn', 'learn-cockpit');
    await expect(page.getByTestId('learn-advance-action')).toBeVisible();

    await gotoLifecyclePhaseAndWaitFor(page, 'evolve', 'evolve-cockpit');
    await expect(page.getByTestId('evolve-advance-action')).toBeVisible();
  });
});
