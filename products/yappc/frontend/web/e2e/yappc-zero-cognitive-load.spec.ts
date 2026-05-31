import { expect, test } from '@playwright/test';

import { bootstrapLifecycleProject, gotoLifecyclePhase, setupLifecycleJourneyApi } from './support/lifecycle-fixtures';

test.describe('YAPPC Zero Cognitive Load Journey', () => {
  test('first viewport exposes current state primary action and blocker/recovery context', async ({ page }) => {
    await setupLifecycleJourneyApi(page, { blockedValidate: true });
    await bootstrapLifecycleProject(page);

    await gotoLifecyclePhase(page, 'validate');
    await expect(page.getByTestId('phase-contract-summary')).toBeVisible();
    await expect(page.getByTestId('validate-primary-action-card')).toBeVisible();
    await expect(page.getByTestId('phase-current-readiness')).toContainText(/blocked|cannot advance/i);
  });
});
