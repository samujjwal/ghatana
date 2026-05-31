import { expect, test, type Request } from '@playwright/test';

import { PROJECT_ID, bootstrapLifecycleProject, gotoLifecyclePhase, setupLifecycleJourneyApi } from './support/lifecycle-fixtures';

test.describe('YAPPC Kernel Handoff Journey', () => {
  test('generate flow calls ProductUnitIntent handoff and run status appears in run/observe', async ({ page }) => {
    let handoffRequest: Request | null = null;
    await setupLifecycleJourneyApi(page);
    await bootstrapLifecycleProject(page);

    await page.route(/\/api(?:\/api)?\/v1\/yappc\/generate\/product-unit-intent(?:\?|$)/, async (route) => {
      handoffRequest = route.request();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          intentId: 'pui-e2e-1',
          status: 'VALID',
          projectId: PROJECT_ID,
          traceId: 'trace-kernel-handoff',
          evidenceIds: ['kernel-intent-evidence'],
        }),
      });
    });

    await gotoLifecyclePhase(page, 'generate');
    await page.evaluate(async (projectId) => {
      await fetch('/api/v1/yappc/generate/product-unit-intent', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'content-type': 'application/json',
        },
        body: JSON.stringify({
          projectId,
          workspaceId: 'ws-e2e',
          tenantId: 'tenant-e2e',
        }),
      });
    }, PROJECT_ID);

    await expect.poll(() => handoffRequest !== null).toBe(true);

    await gotoLifecyclePhase(page, 'run');
    await expect(page.getByTestId('run-cockpit')).toBeVisible();

    await gotoLifecyclePhase(page, 'observe');
    await expect(page.getByTestId('observe-cockpit')).toBeVisible();
  });
});
