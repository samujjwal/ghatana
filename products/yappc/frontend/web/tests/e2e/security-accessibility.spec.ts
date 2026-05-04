/**
 * Security and Accessibility E2E Tests
 *
 * Tests for security vulnerabilities and accessibility compliance.
 *
 * @doc.type test
 * @doc.purpose Security and accessibility E2E tests
 * @doc.layer product
 * @doc.pattern E2E Test
 */

import { test, expect } from '@playwright/test';

test.describe('Security Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');
  });

  test('preview spoofed origin is rejected', async ({ page }) => {
    // Navigate to a page with preview
    await page.goto('/shape');
    await page.waitForLoadState('networkidle');

    // Open preview
    await page.click('[data-testid="open-preview"]');
    
    // Get the preview iframe
    const iframeElement = await page.locator('[data-testid="preview-iframe"]').elementHandle();
    const contentFrame = await iframeElement?.contentFrame();
    
    if (contentFrame) {
      // Try to send a message from a spoofed origin
      await contentFrame.evaluate(() => {
        window.postMessage(
          { type: 'TEST_MESSAGE', source: 'spoofed-origin' },
          '*'
        );
      });

      // Verify the message was rejected (no response or error logged)
      const logs = await page.evaluate(() => {
        return (window as unknown).__previewSecurityLogs || [];
      });

      expect(logs).toContainEqual(
        expect.objectContaining({
          type: 'MESSAGE_REJECTED',
          reason: expect.stringContaining('origin'),
        })
      );
    }
  });

  test('unknown message type is rejected', async ({ page }) => {
    await page.goto('/shape');
    await page.waitForLoadState('networkidle');

    await page.click('[data-testid="open-preview"]');
    const iframeElement = await page.locator('[data-testid="preview-iframe"]').elementHandle();
    const contentFrame = await iframeElement?.contentFrame();

    if (contentFrame) {
      // Send unknown message type
      await contentFrame.evaluate(() => {
        window.postMessage({ type: 'UNKNOWN_TYPE' }, '*');
      });

      // Verify rejection
      const logs = await page.evaluate(() => {
        return (window as unknown).__previewSecurityLogs || [];
      });

      expect(logs).toContainEqual(
        expect.objectContaining({
          type: 'MESSAGE_REJECTED',
          reason: expect.stringContaining('unknown type'),
        })
      );
    }
  });

  test('invalid session is rejected', async ({ page }) => {
    await page.goto('/preview/builder?session=invalid-session-id');
    await page.waitForLoadState('networkidle');

    // Verify session validation failed
    await expect(page.locator('[data-testid="session-invalid"]')).toBeVisible();
    await expect(page.locator('text=Invalid or expired session')).toBeVisible();
  });

  test('unsafe plugin is rejected', async ({ page }) => {
    // Mock an unsafe plugin
    await page.route('**/api/plugins/validate', async (route) => {
      await route.fulfill({
        status: 200,
        body: JSON.stringify({
          valid: false,
          reason: 'Plugin requires elevated permissions not approved',
        }),
      });
    });

    await page.goto('/shape');
    await page.waitForLoadState('networkidle');

    // Try to load an unsafe plugin
    await page.evaluate(() => {
      if ((window as unknown).__loadPlugin) {
        (window as unknown).__loadPlugin('unsafe-plugin-id');
      }
    });

    // Verify plugin was rejected
    await expect(page.locator('[data-testid="plugin-rejected"]')).toBeVisible();
  });

  test('cross-tenant artifact load is denied', async ({ page }) => {
    // Mock cross-tenant request
    await page.route('**/api/artifacts/other-tenant/*', async (route) => {
      await route.fulfill({
        status: 403,
        body: JSON.stringify({
          error: 'Cross-tenant access denied',
        }),
      });
    });

    await page.goto('/artifacts/other-tenant/artifact-123');
    await page.waitForLoadState('networkidle');

    // Verify access denied
    await expect(page.locator('[data-testid="access-denied"]')).toBeVisible();
    await expect(page.locator('text=Cross-tenant access denied')).toBeVisible();
  });

  test('CSP headers are present', async ({ page }) => {
    const response = await page.goto('/');
    const cspHeader = await response?.headerValue('Content-Security-Policy');
    
    expect(cspHeader).toBeDefined();
    expect(cspHeader).toContain('default-src');
    expect(cspHeader).toContain('script-src');
    expect(cspHeader).toContain('connect-src');
  });

  test('iframe sandbox attributes are present', async ({ page }) => {
    await page.goto('/shape');
    await page.waitForLoadState('networkidle');

    await page.click('[data-testid="open-preview"]');
    
    const previewFrame = page.locator('[data-testid="preview-iframe"]');
    const sandbox = await previewFrame.getAttribute('sandbox');
    
    expect(sandbox).toBeDefined();
    expect(sandbox).toContain('allow-scripts');
    expect(sandbox).toContain('allow-same-origin');
    expect(sandbox).not.toContain('allow-top-navigation');
  });
});

test.describe('Accessibility Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');
  });

  test('keyboard-only onboarding', async ({ page }) => {
    // Navigate through onboarding using only keyboard
    await page.keyboard.press('Tab');
    await page.keyboard.press('Enter');
    
    // Complete onboarding steps
    await page.keyboard.press('Tab');
    await page.keyboard.press('Space'); // Check first item
    await page.keyboard.press('Tab');
    await page.keyboard.press('Space'); // Check second item
    await page.keyboard.press('Tab');
    await page.keyboard.press('Enter'); // Complete
    
    // Verify onboarding completed
    await expect(page.locator('[data-testid="onboarding-complete"]')).toBeVisible();
  });

  test('keyboard-only command palette', async ({ page }) => {
    // Open command palette with keyboard shortcut
    await page.keyboard.press('Control+Shift+P');
    
    // Verify command palette opened
    await expect(page.locator('[data-testid="command-palette"]')).toBeVisible();
    
    // Navigate commands with keyboard
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
    
    // Verify command executed
    const currentUrl = page.url();
    expect(currentUrl).not.toBe('/');
  });

  test('keyboard-only phase navigation', async ({ page }) => {
    // Navigate to phases using keyboard
    await page.keyboard.press('Tab'); // Focus on first nav item
    await page.keyboard.press('Enter'); // Navigate to first phase
    
    // Use arrow keys to navigate phases
    await page.keyboard.press('ArrowRight');
    await page.keyboard.press('Enter');
    
    // Verify phase changed
    await expect(page.url()).toMatch(/\/(shape|validate|generate)/);
  });

  test('keyboard-only page builder selection and inspector', async ({ page }) => {
    await page.goto('/shape');
    await page.waitForLoadState('networkidle');

    // Select component with keyboard
    await page.keyboard.press('Tab');
    await page.keyboard.press('Enter'); // Select first component
    
    // Verify component selected
    await expect(page.locator('[data-testid="component-selected"]')).toBeVisible();
    
    // Open inspector with keyboard
    await page.keyboard.press('Control+I');
    
    // Verify inspector opened
    await expect(page.locator('[data-testid="property-inspector"]')).toBeVisible();
    
    // Navigate inspector fields with keyboard
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.type('test value');
    await page.keyboard.press('Enter');
  });

  test('focus trap in dialogs', async ({ page }) => {
    await page.goto('/shape');
    await page.waitForLoadState('networkidle');

    // Open a dialog
    await page.click('[data-testid="add-page-node"]');
    
    // Verify focus is in dialog
    const focusedElement = await page.evaluate(() => document.activeElement?.tagName);
    expect(focusedElement).toBe('INPUT');
    
    // Try to tab out of dialog
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    
    // Verify focus is still in dialog
    const stillInDialog = await page.evaluate(() => {
      const dialog = document.querySelector('[data-testid="dialog"]');
      const active = document.activeElement;
      return dialog?.contains(active);
    });
    
    expect(stillInDialog).toBe(true);
    
    // Close dialog with Escape
    await page.keyboard.press('Escape');
    
    // Verify dialog closed
    await expect(page.locator('[data-testid="dialog"]')).not.toBeVisible();
  });

  test('ARIA labels for icon buttons', async ({ page }) => {
    // Find all icon buttons
    const iconButtons = page.locator('button[aria-label]');
    const count = await iconButtons.count();
    
    // Verify all icon buttons have aria-label
    expect(count).toBeGreaterThan(0);
    
    // Verify aria-label is not empty
    for (let i = 0; i < count; i++) {
      const label = await iconButtons.nth(i).getAttribute('aria-label');
      expect(label).toBeTruthy();
      expect(label?.length).toBeGreaterThan(0);
    }
  });

  test('color contrast for statuses', async ({ page }) => {
    await page.goto('/validate');
    await page.waitForLoadState('networkidle');

    // Check status badges
    const statusBadges = page.locator('[data-testid^="status-badge-"]');
    const count = await statusBadges.count();
    
    for (let i = 0; i < count; i++) {
      const badge = statusBadges.nth(i);
      
      // Get computed colors
      const backgroundColor = await badge.evaluate((el) => {
        const styles = window.getComputedStyle(el);
        return styles.backgroundColor;
      });
      
      const color = await badge.evaluate((el) => {
        const styles = window.getComputedStyle(el);
        return styles.color;
      });
      
      // Verify colors are not transparent or invalid
      expect(backgroundColor).not.toBe('transparent');
      expect(backgroundColor).not.toBe('rgba(0, 0, 0, 0)');
      expect(color).not.toBe('transparent');
    }
  });

  test('skip link exists and works', async ({ page }) => {
    await page.goto('/');
    
    // Check for skip link
    const skipLink = page.locator('[data-testid="skip-link"]');
    
    if (await skipLink.isVisible()) {
      // Press Tab to focus skip link
      await page.keyboard.press('Tab');
      
      // Verify skip link is focused
      const focused = await skipLink.evaluate((el) => document.activeElement === el);
      expect(focused).toBe(true);
      
      // Press Enter to skip
      await page.keyboard.press('Enter');
      
      // Verify focus moved to main content
      const mainFocused = await page.evaluate(() => {
        const main = document.querySelector('main');
        return document.activeElement === main || main?.contains(document.activeElement);
      });
      
      expect(mainFocused).toBe(true);
    }
  });

  test('form fields have associated labels', async ({ page }) => {
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    // Check all input fields
    const inputs = page.locator('input[type="text"], input[type="email"], input[type="password"]');
    const count = await inputs.count();
    
    for (let i = 0; i < count; i++) {
      const input = inputs.nth(i);
      const id = await input.getAttribute('id');
      
      if (id) {
        // Check for associated label
        const label = page.locator(`label[for="${id}"]`);
        await expect(label).toBeVisible();
      } else {
        // Check for aria-label
        const ariaLabel = await input.getAttribute('aria-label');
        expect(ariaLabel).toBeTruthy();
      }
    }
  });

  test('headings form logical hierarchy', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Get all headings
    const headings = await page.evaluate(() => {
      const elements = document.querySelectorAll('h1, h2, h3, h4, h5, h6');
      return Array.from(elements).map(el => ({
        tag: el.tagName,
        text: el.textContent?.trim(),
      }));
    });

    // Verify heading hierarchy is logical (no skipped levels)
    for (let i = 1; i < headings.length; i++) {
      const currentLevel = parseInt(headings[i].tag.charAt(1));
      const previousLevel = parseInt(headings[i - 1].tag.charAt(1));
      
      // Allow same level or one level down (e.g., h2 to h3 is ok, h2 to h4 is not)
      const levelDiff = currentLevel - previousLevel;
      expect(levelDiff).toBeLessThanOrEqual(1);
    }
  });

  test('images have alt text', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Get all images
    const images = page.locator('img');
    const count = await images.count();
    
    for (let i = 0; i < count; i++) {
      const img = images.nth(i);
      const alt = await img.getAttribute('alt');
      
      // Decorative images should have empty alt, others should have descriptive alt
      const role = await img.getAttribute('role');
      if (role === 'presentation' || role === 'none') {
        expect(alt).toBe('');
      } else {
        expect(alt).toBeTruthy();
        expect(alt?.length).toBeGreaterThan(0);
      }
    }
  });

  test('landmark regions are present', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');

    // Check for landmark regions
    const landmarks = await page.evaluate(() => {
      return {
        main: !!document.querySelector('main'),
        header: !!document.querySelector('header'),
        nav: !!document.querySelector('nav'),
        footer: !!document.querySelector('footer'),
      };
    });

    expect(landmarks.main).toBe(true);
    expect(landmarks.header).toBe(true);
    expect(landmarks.nav).toBe(true);
  });
});

test.describe('Accessibility - Screen Reader Compatibility', () => {
  test('live regions announce changes', async ({ page }) => {
    await page.goto('/shape');
    await page.waitForLoadState('networkidle');

    // Trigger a change that should be announced
    await page.click('[data-testid="add-page-node"]');
    await page.fill('[data-testid="page-name-input"]', 'Test Page');
    await page.click('[data-testid="save-page-node"]');
    
    // Check for live region
    const liveRegion = page.locator('[aria-live="polite"], [aria-live="assertive"]');
    await expect(liveRegion).toBeVisible();
  });

  test('dialog has proper ARIA attributes', async ({ page }) => {
    await page.goto('/shape');
    await page.waitForLoadState('networkidle');

    await page.click('[data-testid="add-page-node"]');
    
    const dialog = page.locator('[data-testid="dialog"]');
    
    // Check ARIA attributes
    await expect(dialog).toHaveAttribute('role', 'dialog');
    await expect(dialog).toHaveAttribute('aria-modal', 'true');
    
    // Check for aria-labelledby or aria-label
    const labelledBy = await dialog.getAttribute('aria-labelledby');
    const ariaLabel = await dialog.getAttribute('aria-label');
    
    expect(labelledBy || ariaLabel).toBeTruthy();
  });

  test('progress indicators are accessible', async ({ page }) => {
    await page.goto('/generate');
    await page.waitForLoadState('networkidle');

    await page.click('[data-testid="generate-code"]');
    
    // Wait for progress indicator
    const progress = page.locator('[role="progressbar"]');
    
    if (await progress.isVisible()) {
      // Check for aria attributes
      await expect(progress).toHaveAttribute('aria-valuenow');
      await expect(progress).toHaveAttribute('aria-valuemin');
      await expect(progress).toHaveAttribute('aria-valuemax');
    }
  });

  test('error messages are associated with inputs', async ({ page }) => {
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    // Submit empty form to trigger validation
    await page.click('[data-testid="login-submit"]');
    
    // Check for error messages
    const errorMessages = page.locator('[data-testid="login-error"]');
    
    if (await errorMessages.isVisible()) {
      // Check for aria-live or role="alert"
      const hasAriaLive = await errorMessages.getAttribute('aria-live');
      const hasRole = await errorMessages.getAttribute('role');
      
      expect(hasAriaLive || hasRole).toBeTruthy();
    }
  });
});
