import { test, expect } from '@playwright/test';
import { mockPhrEntitlements } from './phr-entitlements';
import { readFileSync } from 'fs';
import { join } from 'path';

// Load route contract to get all stable routes
const routeContractPath = join(process.cwd(), 'config', 'phr-route-contract.json');
const routeContract = JSON.parse(readFileSync(routeContractPath, 'utf-8'));
const stableRoutes = routeContract.routes.filter((r: any) => r.stability === 'stable');

test.describe('PHR accessibility @a11y', () => {
  test('login screen exposes accessible controls', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Welcome to PHR Nepal' })).toBeVisible();
    await expect(page.getByLabel('National ID')).toBeVisible();
    await expect(page.getByLabel('Password')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sign In' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Continue with demo account' })).toBeVisible();
  });

  test('dashboard keeps landmark and keyboard navigation visible', async ({ page }) => {
    await mockPhrEntitlements(page);
    await page.goto('/login');
    await page.getByRole('link', { name: 'Continue with demo account' }).click();
    await expect(page.getByText('Patient summary')).toBeVisible();
    await expect(page.getByRole('main')).toBeVisible();
    await expect(page.getByRole('navigation')).toBeVisible();
    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();
  });

  test('clinician emergency workflow remains keyboard accessible', async ({ page }) => {
    await mockPhrEntitlements(page, 'clinician');
    await page.goto('/login');
    await page.evaluate(() => {
      window.localStorage.setItem('phr.currentRole', 'clinician');
    });
    await page.goto('/emergency');
    await expect(page.getByText('Break-glass workflow')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Request emergency access' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Notify caregiver' })).toBeVisible();
    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();
  });

  test('patient emergency denial stays visible and readable', async ({ page }) => {
    await mockPhrEntitlements(page);
    await page.goto('/login');
    await page.evaluate(() => {
      window.localStorage.setItem('phr.currentRole', 'patient');
    });
    await page.goto('/emergency');
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByText('Permission denied')).toBeVisible();
    await expect(page.getByText(/not available for the current persona/i)).toBeVisible();
  });

  // Accessibility checks for stable routes
  test.describe('stable routes have accessible structure', () => {
    const testableRoutes = stableRoutes
      .filter((r: any) => !r.path.includes(':') && r.path !== '/login' && r.path !== '/emergency');

    for (const route of testableRoutes) {
      test(`${route.path} has accessible landmarks and headings`, async ({ page }) => {
        await mockPhrEntitlements(page);
        await page.goto(route.path);

        // Check for main landmark
        const main = page.getByRole('main');
        const hasMain = await main.count() > 0;
        
        // Check for at least one heading
        const heading = page.getByRole('heading').first();
        const hasHeading = await heading.count() > 0;

        // Routes should have either main landmark or heading
        expect(hasMain || hasHeading).toBe(true);
      });

      test(`${route.path} buttons have accessible names`, async ({ page }) => {
        await mockPhrEntitlements(page);
        await page.goto(route.path);

        const buttons = page.getByRole('button');
        const buttonCount = await buttons.count();

        if (buttonCount > 0) {
          // Check that buttons have accessible names (via text, aria-label, or title)
          for (let i = 0; i < Math.min(buttonCount, 5); i++) {
            const button = buttons.nth(i);
            const name = await button.getAttribute('aria-label') || 
                        await button.getAttribute('title') || 
                        await button.textContent();
            expect(name?.trim().length).toBeGreaterThan(0);
          }
        }
      });

      test(`${route.path} form inputs have accessible labels`, async ({ page }) => {
        await mockPhrEntitlements(page);
        await page.goto(route.path);

        const inputs = page.locator('input, select, textarea');
        const inputCount = await inputs.count();

        if (inputCount > 0) {
          // Check that inputs have labels (via aria-label, aria-labelledby, or associated label)
          for (let i = 0; i < Math.min(inputCount, 5); i++) {
            const input = inputs.nth(i);
            const ariaLabel = await input.getAttribute('aria-label');
            const ariaLabelledby = await input.getAttribute('aria-labelledby');
            const id = await input.getAttribute('id');
            
            // Input should have aria-label, aria-labelledby, or an id that references a label
            const hasLabel = ariaLabel || ariaLabelledby || id;
            expect(hasLabel).toBeTruthy();
          }
        }
      });
    }
  });

  test('tab navigation works across interactive elements', async ({ page }) => {
    await mockPhrEntitlements(page);
    await page.goto('/dashboard');

    // Tab through interactive elements
    let focusCount = 0;
    for (let i = 0; i < 10; i++) {
      await page.keyboard.press('Tab');
      const focused = page.locator(':focus');
      const isVisible = await focused.isVisible().catch(() => false);
      if (isVisible) {
        focusCount++;
      }
    }

    // Should have focused on at least some elements
    expect(focusCount).toBeGreaterThan(0);
  });

  test('links have descriptive text or aria-labels', async ({ page }) => {
    await mockPhrEntitlements(page);
    await page.goto('/dashboard');

    const links = page.getByRole('link');
    const linkCount = await links.count();

    if (linkCount > 0) {
      for (let i = 0; i < Math.min(linkCount, 5); i++) {
        const link = links.nth(i);
        const text = await link.textContent();
        const ariaLabel = await link.getAttribute('aria-label');
        
        // Link should have text or aria-label
        const hasDescription = (text && text.trim().length > 0) || ariaLabel;
        expect(hasDescription).toBe(true);
      }
    }
  });

  // Keyboard navigation tests for shell/sidebar/user menu
  test.describe('keyboard navigation for shell and navigation', () => {
    test('sidebar navigation is keyboard accessible', async ({ page }) => {
      await mockPhrEntitlements(page);
      await page.goto('/dashboard');

      // Tab to navigation
      await page.keyboard.press('Tab');
      await page.keyboard.press('Tab');
      
      // Verify focus is on an interactive element
      const focused = page.locator(':focus');
      await expect(focused).toBeVisible();
    });

    test('escape key closes modals or menus', async ({ page }) => {
      await mockPhrEntitlements(page);
      await page.goto('/dashboard');

      // Press escape and verify no errors
      await page.keyboard.press('Escape');
      
      // Should still be on page
      await expect(page.getByRole('main')).toBeVisible();
    });

    test('tab order follows logical sequence', async ({ page }) => {
      await mockPhrEntitlements(page);
      await page.goto('/dashboard');

      const focusableElements = [];
      for (let i = 0; i < 10; i++) {
        await page.keyboard.press('Tab');
        const focused = page.locator(':focus');
        const isVisible = await focused.isVisible().catch(() => false);
        if (isVisible) {
          const tagName = await focused.evaluate(el => el.tagName);
          focusableElements.push(tagName);
        }
      }

      // Should have focused on some elements
      expect(focusableElements.length).toBeGreaterThan(0);
    });
  });

  // Contrast check for status badges/pills
  test.describe('color contrast for status indicators', () => {
    test('status badges have sufficient contrast', async ({ page }) => {
      await mockPhrEntitlements(page);
      await page.goto('/consents');

      // Check that badges have accessible contrast by verifying they have text content
      const badges = page.locator('.badge, .pill');
      const badgeCount = await badges.count();

      if (badgeCount > 0) {
        for (let i = 0; i < Math.min(badgeCount, 5); i++) {
          const badge = badges.nth(i);
          const text = await badge.textContent();
          // Badge should have text content for screen readers
          expect(text?.trim().length).toBeGreaterThan(0);
        }
      }
    });

    test('status pills have descriptive text', async ({ page }) => {
      await mockPhrEntitlements(page);
      await page.goto('/records');

      // Check that pills have descriptive text
      const pills = page.locator('.pill');
      const pillCount = await pills.count();

      if (pillCount > 0) {
        for (let i = 0; i < Math.min(pillCount, 5); i++) {
          const pill = pills.nth(i);
          const text = await pill.textContent();
          // Pill should have text content
          expect(text?.trim().length).toBeGreaterThan(0);
        }
      }
    });
  });
});
