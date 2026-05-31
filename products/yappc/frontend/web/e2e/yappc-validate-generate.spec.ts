import { expect, test } from '@playwright/test';

import { PROJECT_ID, bootstrapLifecycleProject, gotoLifecyclePhaseAndWaitFor, setupLifecycleJourneyApi } from './support/lifecycle-fixtures';

test.describe('YAPPC Validate to Generate Journey', () => {
  test('validate blocked state and generate assurance state are rendered', async ({ page }) => {
    await setupLifecycleJourneyApi(page, { blockedValidate: true });
    await bootstrapLifecycleProject(page);

    await gotoLifecyclePhaseAndWaitFor(page, 'validate', 'validate-cockpit');
    await expect(page.getByTestId('validate-cockpit')).toBeVisible();
    await expect(page.getByTestId('phase-current-readiness')).toContainText(/blocked|cannot advance/i);

    await gotoLifecyclePhaseAndWaitFor(page, 'generate', 'generate-cockpit');
    await expect(page.getByTestId('generate-cockpit')).toBeVisible();
    await expect(page.getByTestId('generate-primary-action-card')).toBeVisible();
  });
});
