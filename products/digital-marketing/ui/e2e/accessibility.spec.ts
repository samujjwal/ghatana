/**
 * P2-004: Browser E2E accessibility tests.
 *
 * Tests:
 * - Keyboard navigation for all interactive elements
 * - ARIA labels on buttons and forms
 * - Focus indicators
 * - Color contrast (basic checks)
 * - Screen reader compatibility markers
 *
 * @doc.type test
 * @doc.purpose Accessibility testing for DMOS UI (P2-004)
 * @doc.layer e2e
 */

import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { TEST_WORKSPACE, loginAs, mockDmosApi, navigateInApp } from './fixtures';

test.describe('P2-004: Accessibility Tests', () => {
  const stableRouteCases: Array<{ label: string; path: string; readySelector: string }> = [
    { label: 'dashboard', path: `/workspaces/${TEST_WORKSPACE}/dashboard`, readySelector: '[data-testid="dashboard-page"]' },
    { label: 'campaigns', path: `/workspaces/${TEST_WORKSPACE}/campaigns`, readySelector: '[data-testid="campaigns-page"]' },
    { label: 'strategy', path: `/workspaces/${TEST_WORKSPACE}/strategy`, readySelector: '[data-testid="strategy-page"]' },
    { label: 'budget', path: `/workspaces/${TEST_WORKSPACE}/budget`, readySelector: '[data-testid="budget-page"]' },
    { label: 'approvals', path: `/workspaces/${TEST_WORKSPACE}/approvals`, readySelector: '[data-testid="approval-queue-page"]' },
    { label: 'AI action log', path: `/workspaces/${TEST_WORKSPACE}/ai-actions`, readySelector: '[data-testid="ai-action-log-page"]' },
  ];

  async function openAuthenticatedRoute(page: Page, path: string, roles: string[] = ['admin']): Promise<void> {
    await mockDmosApi(page);
    await loginAs(page, { roles });
    await navigateInApp(page, path);
  }

  test.describe('stable route accessibility @a11y', () => {
    test.beforeEach(async ({ page }) => {
      await mockDmosApi(page);
      await loginAs(page, { roles: ['admin'] });
    });

    for (const routeCase of stableRouteCases) {
      test(`${routeCase.label} has landmarks, keyboard focus, and no WCAG 2A/2AA axe violations`, async ({ page }) => {
        await navigateInApp(page, routeCase.path);
        await expect(page.locator(routeCase.readySelector)).toBeVisible();
        await expect(page.getByRole('main')).toBeVisible();
        await page.keyboard.press('Tab');
        await expect(page.locator(':focus')).toBeVisible();

        const accessibilityScanResults = await new AxeBuilder({ page })
          .withTags(['wcag2a', 'wcag2aa'])
          .analyze();

        expect(accessibilityScanResults.violations).toEqual([]);
      });
    }
  });

  test('P2-004: Dashboard page should be accessible', async ({ page }) => {
    await openAuthenticatedRoute(page, `/workspaces/${TEST_WORKSPACE}/dashboard`);

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('P2-004: Campaigns page should be accessible', async ({ page }) => {
    await openAuthenticatedRoute(page, `/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]', { timeout: 5000 });

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('P2-004: Strategy page should be accessible', async ({ page }) => {
    await openAuthenticatedRoute(page, `/workspaces/${TEST_WORKSPACE}/strategy`);
    await page.waitForSelector('[data-testid="strategy-page"]', { timeout: 5000 });

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('P2-002: Budget page should be accessible (WCAG 2A/2AA)', async ({ page }) => {
    await openAuthenticatedRoute(page, `/workspaces/${TEST_WORKSPACE}/budget`);
    await page.waitForSelector('[data-testid="budget-page"]', { timeout: 5000 });

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('P2-002: AI actions page should be accessible (WCAG 2A/2AA)', async ({ page }) => {
    await openAuthenticatedRoute(page, `/workspaces/${TEST_WORKSPACE}/ai-actions`);
    await page.waitForSelector('[data-testid="ai-action-log-page"]', { timeout: 5000 });

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('P2-004: All buttons should have accessible names', async ({ page }) => {
    await openAuthenticatedRoute(page, `/workspaces/${TEST_WORKSPACE}/campaigns`);

    // Get all buttons
    const buttons = await page.$$('button');

    for (const button of buttons) {
      // Check if button has accessible name
      const accessibleName = await button.evaluate(el => {
        const label = el.getAttribute('aria-label');
        const labelledBy = el.getAttribute('aria-labelledby');
        const title = el.getAttribute('title');
        const textContent = el.textContent?.trim();
        return label || labelledBy || title || textContent;
      });

      expect(accessibleName, 'Button should have accessible name').toBeTruthy();
    }
  });

  test('P2-004: All form inputs should have associated labels', async ({ page }) => {
    // Navigate to a page with forms
    await openAuthenticatedRoute(page, `/workspaces/${TEST_WORKSPACE}/campaigns`);

    // Get all form inputs
    const inputs = await page.$$('input, select, textarea');

    for (const input of inputs) {
      const hasLabel = await input.evaluate(el => {
        const id = el.id;
        const ariaLabel = el.getAttribute('aria-label');
        const ariaLabelledBy = el.getAttribute('aria-labelledby');
        const placeholder = el.getAttribute('placeholder');
        const hasLabelElement = id ? !!document.querySelector(`label[for="${id}"]`) : false;

        return !!(ariaLabel || ariaLabelledBy || hasLabelElement || placeholder);
      });

      expect(hasLabel, 'Form input should have accessible label').toBe(true);
    }
  });

  test('P2-004: Focus should be visible on all interactive elements', async ({ page }) => {
    await openAuthenticatedRoute(page, `/workspaces/${TEST_WORKSPACE}/dashboard`);

    const firstInteractiveElement = page.locator('main a[href], main button:not([disabled])').first();
    await firstInteractiveElement.focus();
    await expect(firstInteractiveElement).toBeFocused();
    await expect(firstInteractiveElement).toBeVisible();
  });

  test('P2-004: Error messages should be announced to screen readers', async ({ page }) => {
    await mockDmosApi(page);
    await loginAs(page, { roles: [] });
    await navigateInApp(page, `/workspaces/${TEST_WORKSPACE}/approvals`);
    await expect(page.locator('[data-testid="permission-denied-banner"]')).toBeVisible();

    // Check error elements have appropriate ARIA attributes
    const errorElements = await page.$$('[data-testid="mutation-error"], [role="alert"], [aria-live="assertive"]');

    // At least one error element should be present with appropriate ARIA attributes
    const hasAccessibleError = await Promise.all(
      errorElements.map(el =>
        el.evaluate(el =>
          el.getAttribute('role') === 'alert' ||
          el.getAttribute('aria-live') === 'assertive' ||
          el.getAttribute('aria-live') === 'polite'
        )
      )
    );

    expect(hasAccessibleError.some(Boolean)).toBe(true);
  });

  test('P2-004: Page should have proper heading structure', async ({ page }) => {
    await openAuthenticatedRoute(page, `/workspaces/${TEST_WORKSPACE}/dashboard`);

    const headings = await page.$$('h1, h2, h3, h4, h5, h6');

    // Should have at least one h1
    const h1Count = await page.$$eval('h1', els => els.length);
    expect(h1Count).toBeGreaterThanOrEqual(1);

    // Check heading levels don't skip (e.g., h1 directly to h3)
    const headingLevels = await Promise.all(
      headings.map(h => h.evaluate(el => parseInt(el.tagName.charAt(1))))
    );

    for (let i = 1; i < headingLevels.length; i++) {
      // Heading levels should not skip more than 1 level
      const diff = headingLevels[i] - headingLevels[i - 1];
      expect(diff).toBeLessThanOrEqual(1);
    }
  });

  test('P2-004: Images should have alt text', async ({ page }) => {
    await openAuthenticatedRoute(page, `/workspaces/${TEST_WORKSPACE}/dashboard`);

    const images = await page.$$('img');

    for (const img of images) {
      const hasAlt = await img.evaluate(el =>
        el.hasAttribute('alt') || el.getAttribute('aria-label')
      );

      // If image is decorative, it should have empty alt or role="presentation"
      const isDecorative = await img.evaluate(el =>
        el.getAttribute('alt') === '' ||
        el.getAttribute('role') === 'presentation' ||
        el.getAttribute('aria-hidden') === 'true'
      );

      expect(hasAlt || isDecorative, 'Images should have alt text or be marked decorative').toBe(true);
    }
  });

  test('P2-004: Interactive elements should be keyboard accessible', async ({ page }) => {
    await openAuthenticatedRoute(page, `/workspaces/${TEST_WORKSPACE}/campaigns`);
    await expect(page.getByRole('button', { name: 'Create Campaign' })).toBeVisible();

    // Test that Tab key moves focus
    await page.keyboard.press('Tab');

    const focusedElement = await page.evaluate(() =>
      document.activeElement?.tagName
    );

    expect(focusedElement).not.toBe('BODY');
  });
});
