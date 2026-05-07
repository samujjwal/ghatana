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

import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('P2-004: Accessibility Tests', () => {
  const TEST_WORKSPACE = 'test-workspace-123';
  const TEST_TENANT = 'test-tenant';

  async function login(page: any): Promise<void> {
    await page.goto('/login');
    await page.fill('[data-testid="tenant-id-input"]', TEST_TENANT);
    await page.fill('[data-testid="workspace-id-input"]', TEST_WORKSPACE);
    await page.fill('[data-testid="principal-id-input"]', 'test-user');
    await page.fill('[data-testid="session-id-input"]', 'test-session');
    await page.click('[data-testid="login-button"]');
    await page.waitForURL(`**/workspaces/${TEST_WORKSPACE}/dashboard`);
  }

  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('P2-004: Dashboard page should be accessible', async ({ page }) => {
    await page.goto(`/workspaces/${TEST_WORKSPACE}/dashboard`);

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('P2-004: Campaigns page should be accessible', async ({ page }) => {
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.waitForSelector('[data-testid="campaigns-page"]', { timeout: 5000 });

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('P2-004: Strategy page should be accessible', async ({ page }) => {
    await page.goto(`/workspaces/${TEST_WORKSPACE}/strategy`);
    await page.waitForSelector('[data-testid="strategy-page"]', { timeout: 5000 });

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('P2-002: Budget page should be accessible (WCAG 2A/2AA)', async ({ page }) => {
    await page.goto(`/workspaces/${TEST_WORKSPACE}/budget`);
    await page.waitForSelector('[data-testid="budget-page"]', { timeout: 5000 });

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('P2-002: AI actions page should be accessible (WCAG 2A/2AA)', async ({ page }) => {
    await page.goto(`/workspaces/${TEST_WORKSPACE}/ai-actions`);
    await page.waitForSelector('[data-testid="ai-actions-page"]', { timeout: 5000 });

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('P2-004: All buttons should have accessible names', async ({ page }) => {
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);

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
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.click('[data-testid="create-campaign-button"]');

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
    await page.goto(`/workspaces/${TEST_WORKSPACE}/dashboard`);

    // Tab through interactive elements and verify focus visibility
    const interactiveElements = await page.$$('button, a, input, select, [tabindex]:not([tabindex="-1"])');

    for (let i = 0; i < Math.min(interactiveElements.length, 5); i++) {
      await interactiveElements[i].focus();

      const isFocused = await interactiveElements[i].evaluate(el =>
        document.activeElement === el
      );

      expect(isFocused).toBe(true);

      // Check if element has visible focus indicator
      const outline = await interactiveElements[i].evaluate(el =>
        window.getComputedStyle(el).outline
      );

      // Focus indicator should be present (not "0px none" or similar)
      expect(outline).not.toBe('0px none rgb(0, 0, 0)');
    }
  });

  test('P2-004: Error messages should be announced to screen readers', async ({ page }) => {
    // Navigate to campaigns and trigger an error
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);
    await page.click('[data-testid="create-campaign-button"]');

    // Submit empty form to trigger validation error
    await page.click('[data-testid="save-campaign-button"]');

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
    await page.goto(`/workspaces/${TEST_WORKSPACE}/dashboard`);

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
    await page.goto(`/workspaces/${TEST_WORKSPACE}/dashboard`);

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
    await page.goto(`/workspaces/${TEST_WORKSPACE}/campaigns`);

    // Test that Tab key moves focus
    await page.keyboard.press('Tab');

    const focusedElement = await page.evaluate(() =>
      document.activeElement?.tagName
    );

    expect(focusedElement).not.toBe('BODY');
  });
});
