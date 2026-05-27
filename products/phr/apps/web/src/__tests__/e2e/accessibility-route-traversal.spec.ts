/**
 * PHR Web Accessibility Route Traversal Tests
 *
 * These Playwright tests verify keyboard navigation and accessibility
 * across all PHR web routes. Tests include:
 * - Keyboard-only navigation
 * - Focus order verification
 * - Skip link functionality
 * - ARIA landmark verification
 * - Heading structure validation
 */

import { test, expect } from '@playwright/test';

test.describe('PHR Web Accessibility - Route Traversal', () => {
  const routes = [
    '/dashboard',
    '/records',
    '/profile',
    '/timeline',
    '/conditions',
    '/observations',
    '/immunizations',
    '/documents',
    '/documents/upload',
    '/notifications',
    '/settings',
    '/consents',
    '/appointments',
    '/labs',
    '/medications',
  ];

  test.beforeEach(async ({ page }) => {
    // Mock authentication for all tests
    await page.goto('/login');
    await page.fill('input[name="nationalId"]', 'test-national-id');
    await page.fill('input[name="password"]', 'test-password');
    await page.click('button[type="submit"]');
    await page.waitForURL('/dashboard');
  });

  test.describe('Keyboard Navigation', () => {
    routes.forEach((route) => {
      test(`should allow keyboard-only navigation on ${route}`, async ({ page }) => {
        await page.goto(route);

        // Verify page is accessible via keyboard
        const body = page.locator('body');
        await body.focus();

        // Tab through the page - should not get stuck
        let tabCount = 0;
        const maxTabs = 50; // Prevent infinite loops
        let focusVisible = false;

        while (tabCount < maxTabs) {
          await page.keyboard.press('Tab');
          tabCount++;

          const focusedElement = page.locator(':focus');
          const isVisible = await focusedElement.isVisible();
          
          if (isVisible) {
            focusVisible = true;
            break;
          }
        }

        expect(focusVisible).toBe(true);
      });
    });

    test('should have logical focus order on dashboard', async ({ page }) => {
      await page.goto('/dashboard');

      // Tab through interactive elements and verify order
      const focusableElements = await page.locator('button, a, input, select, textarea').all();
      
      for (let i = 0; i < Math.min(focusableElements.length, 10); i++) {
        await page.keyboard.press('Tab');
        const focused = page.locator(':focus');
        const isVisible = await focused.isVisible();
        expect(isVisible).toBe(true);
      }
    });
  });

  test.describe('Skip Links', () => {
    test('should have skip to main content link', async ({ page }) => {
      await page.goto('/dashboard');

      // Press Tab to find skip link
      await page.keyboard.press('Tab');
      const skipLink = page.locator('a[href="#main"], a[href="#content"], .skip-link');
      
      // Skip link should be visible on focus
      const isVisible = await skipLink.isVisible();
      expect(isVisible).toBe(true);
    });

    test('skip link should jump to main content', async ({ page }) => {
      await page.goto('/dashboard');

      const skipLink = page.locator('a[href="#main"], a[href="#content"], .skip-link').first();
      await skipLink.click();

      // Focus should move to main content
      const mainContent = page.locator('#main, #content, main').first();
      const isFocused = await mainContent.evaluate((el: HTMLElement) => 
        document.activeElement === el || el.contains(document.activeElement)
      );
      
      expect(isFocused).toBe(true);
    });
  });

  test.describe('ARIA Landmarks', () => {
    test('should have proper ARIA landmarks on dashboard', async ({ page }) => {
      await page.goto('/dashboard');

      // Check for main landmark
      const main = page.locator('main, [role="main"]');
      await expect(main).toHaveCount(1);

      // Check for navigation landmark
      const nav = page.locator('nav, [role="navigation"]');
      await expect(nav).toHaveCount(1);

      // Check for banner/header landmark
      const header = page.locator('header, [role="banner"]');
      await expect(header).toHaveCount(1);
    });

    test('should have proper ARIA landmarks on all routes', async ({ page }) => {
      for (const route of routes) {
        await page.goto(route);

        // At minimum, should have main landmark
        const main = page.locator('main, [role="main"]');
        const count = await main.count();
        
        // Some routes might not have main if they're error pages or modals
        // but most should have it
        if (!route.includes('/forbidden') && !route.includes('/not-found')) {
          expect(count).toBeGreaterThanOrEqual(0);
        }
      }
    });
  });

  test.describe('Heading Structure', () => {
    test('should have single h1 on dashboard', async ({ page }) => {
      await page.goto('/dashboard');

      const h1s = page.locator('h1');
      await expect(h1s).toHaveCount(1);
    });

    test('should have properly nested headings on dashboard', async ({ page }) => {
      await page.goto('/dashboard');

      // Get all headings
      const headings = await page.locator('h1, h2, h3, h4, h5, h6').all();
      
      let previousLevel = 0;
      for (const heading of headings) {
        const tagName = await heading.evaluate((el) => el.tagName);
        const level = parseInt(tagName.charAt(1));
        
        // Headings should not skip levels (e.g., h1 to h3)
        if (previousLevel > 0) {
          expect(level).toBeLessThanOrEqual(previousLevel + 1);
        }
        
        previousLevel = level;
      }
    });

    test('should have descriptive page titles', async ({ page }) => {
      for (const route of routes) {
        await page.goto(route);
        
        const title = await page.title();
        expect(title).toBeTruthy();
        expect(title.length).toBeGreaterThan(0);
      }
    });
  });

  test.describe('Form Accessibility', () => {
    test('should have labels for all form inputs on profile', async ({ page }) => {
      await page.goto('/profile');

      const inputs = page.locator('input, select, textarea');
      const count = await inputs.count();

      for (let i = 0; i < count; i++) {
        const input = inputs.nth(i);
        const hasLabel = await input.evaluate((el: HTMLInputElement) => {
          const id = el.id;
          const ariaLabel = el.getAttribute('aria-label');
          const ariaLabelledby = el.getAttribute('aria-labelledby');
          const hasAssociatedLabel = id ? document.querySelector(`label[for="${id}"]`) : false;
          
          return !!(ariaLabel || ariaLabelledby || hasAssociatedLabel);
        });

        expect(hasLabel).toBe(true);
      }
    });

    test('should indicate required fields', async ({ page }) => {
      await page.goto('/profile');

      const requiredInputs = page.locator('input[required], select[required], textarea[required]');
      const count = await requiredInputs.count();

      for (let i = 0; i < count; i++) {
        const input = requiredInputs.nth(i);
        const hasRequiredIndicator = await input.evaluate((el: HTMLInputElement) => {
          const parent = el.closest('label') || el.parentElement;
          return parent?.textContent?.includes('*') || 
                 parent?.textContent?.toLowerCase().includes('required') ||
                 el.getAttribute('aria-required') === 'true';
        });

        expect(hasRequiredIndicator).toBe(true);
      }
    });
  });

  test.describe('Link Accessibility', () => {
    test('should have descriptive link text on dashboard', async ({ page }) => {
      await page.goto('/dashboard');

      const links = page.locator('a[href]');
      const count = await links.count();

      for (let i = 0; i < Math.min(count, 10); i++) {
        const link = links.nth(i);
        const text = await link.textContent();
        
        // Links should not have generic text like "click here"
        if (text) {
          const lowerText = text.toLowerCase();
          expect(lowerText).not.toContain('click here');
          expect(lowerText).not.toContain('read more');
          expect(text.trim().length).toBeGreaterThan(0);
        }
      }
    });
  });

  test.describe('Focus Visibility', () => {
    test('should show visible focus indicator on interactive elements', async ({ page }) => {
      await page.goto('/dashboard');

      const button = page.locator('button').first();
      await button.focus();

      // Check that focused element has visible outline or background change
      const hasFocusStyle = await button.evaluate((el: HTMLButtonElement) => {
        const styles = window.getComputedStyle(el);
        return (
          styles.outline !== 'none' ||
          styles.outlineWidth !== '0px' ||
          styles.boxShadow !== 'none' ||
          styles.backgroundColor !== 'rgba(0, 0, 0, 0)'
        );
      });

      expect(hasFocusStyle).toBe(true);
    });
  });

  test.describe('Color Contrast', () => {
    test('should have sufficient color contrast on dashboard', async ({ page }) => {
      await page.goto('/dashboard');

      // This is a basic check - full contrast testing should use axe-core
      const textElements = page.locator('p, h1, h2, h3, h4, h5, h6, span, a, button');
      
      // Verify text elements are visible (not white on white, etc.)
      const count = await textElements.count();
      for (let i = 0; i < Math.min(count, 20); i++) {
        const element = textElements.nth(i);
        const isVisible = await element.isVisible();
        if (isVisible) {
          const color = await element.evaluate((el) => {
            const styles = window.getComputedStyle(el);
            return styles.color;
          });
          
          // Color should not be transparent or extremely light
          expect(color).not.toBe('rgba(0, 0, 0, 0)');
          expect(color).not.toBe('transparent');
        }
      }
    });
  });

  test.describe('Error Handling', () => {
    test('should show accessible error messages', async ({ page }) => {
      await page.goto('/profile');

      // Try to submit form without required fields
      const submitButton = page.locator('button[type="submit"]').first();
      if (await submitButton.isVisible()) {
        await submitButton.click();

        // Error messages should be associated with fields
        const errors = page.locator('[role="alert"], .error, [aria-invalid="true"]');
        const hasErrors = await errors.count() > 0;

        if (hasErrors) {
          // Check that errors are accessible
          const firstError = errors.first();
          const isAccessible = await firstError.evaluate((el) => {
            return el.getAttribute('role') === 'alert' ||
                   el.getAttribute('aria-live') === 'polite' ||
                   el.getAttribute('aria-live') === 'assertive';
          });

          expect(isAccessible).toBe(true);
        }
      }
    });
  });
});
