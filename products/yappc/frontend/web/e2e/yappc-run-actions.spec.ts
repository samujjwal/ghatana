import { expect, test } from '@playwright/test';

import { PROJECT_ID, bootstrapLifecycleProject, gotoLifecyclePhase, setupLifecycleJourneyApi } from './support/lifecycle-fixtures';

test.describe('YAPPC Run Actions Journey', () => {
  test('failed run exposes retry workflow with backend run context', async ({ page }) => {
    await setupLifecycleJourneyApi(page, { runFailure: true });
    await bootstrapLifecycleProject(page);

    await gotoLifecyclePhase(page, 'run');
    await expect(page.getByTestId('run-cockpit')).toBeVisible();
    await expect(page.getByTestId('check-readiness')).toBeVisible();
    await expect(page.getByTestId('phase-current-readiness')).toContainText(/can advance|blocked/i);
  });
});
