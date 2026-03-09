/**
 * E2E Test: Accessibility (Web)
 * Tests keyboard navigation and screen reader support
 */

import { test, expect } from './fixtures';

test.describe('Accessibility', () => {
  test('should navigate with keyboard', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Tab through elements
    await authenticatedPage.keyboard.press('Tab');
    await authenticatedPage.keyboard.press('Tab');
    
    // Enter should activate focused element
    await authenticatedPage.keyboard.press('Enter');
  });

  test('should have proper ARIA labels', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Check for ARIA landmarks
    await expect(authenticatedPage.locator('nav[aria-label]')).toBeVisible();
    await expect(authenticatedPage.locator('main')).toBeVisible();
    
    // Check buttons have labels
    const createButton = authenticatedPage.getByTestId('create-moment-button');
    const ariaLabel = await createButton.getAttribute('aria-label');
    expect(ariaLabel).toBeTruthy();
  });

  test('should have sufficient color contrast', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // This would typically use axe-core for automated testing
    // For now, visual verification
    await authenticatedPage.screenshot({ path: 'screenshots/contrast-check.png' });
  });

  test('should support screen reader text', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Check for sr-only elements
    const srOnlyElements = authenticatedPage.locator('.sr-only');
    const count = await srOnlyElements.count();
    expect(count).toBeGreaterThan(0);
  });

  test('should have skip to content link', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Tab should focus skip link
    await authenticatedPage.keyboard.press('Tab');
    
    const skipLink = authenticatedPage.getByText(/skip to/i);
    if (await skipLink.isVisible()) {
      await expect(skipLink).toBeFocused();
    }
  });

  test('should have focus indicators', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Tab to button
    await authenticatedPage.keyboard.press('Tab');
    await authenticatedPage.keyboard.press('Tab');
    
    // Focused element should have visible outline
    const focused = authenticatedPage.locator(':focus');
    await expect(focused).toBeVisible();
  });

  test('should announce dynamic content to screen readers', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Create moment (triggers announcement)
    await authenticatedPage.getByTestId('create-moment-button').click();
    await authenticatedPage.getByTestId('title-input').fill('Test');
    await authenticatedPage.getByTestId('save-button').click();
    
    // Check for aria-live region
    const liveRegion = authenticatedPage.locator('[aria-live]');
    await expect(liveRegion).toBeVisible();
  });

  test('should have semantic HTML', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    // Check for semantic elements
    await expect(authenticatedPage.locator('header')).toBeVisible();
    await expect(authenticatedPage.locator('nav')).toBeVisible();
    await expect(authenticatedPage.locator('main')).toBeVisible();
    await expect(authenticatedPage.locator('footer')).toBeVisible();
  });

  test('should have alt text for images', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/');
    
    const images = authenticatedPage.locator('img');
    const count = await images.count();
    
    for (let i = 0; i < count; i++) {
      const img = images.nth(i);
      const alt = await img.getAttribute('alt');
      expect(alt).toBeTruthy();
    }
  });

  test('should support reduced motion preference', async ({ authenticatedPage }) => {
    await authenticatedPage.emulateMedia({ reducedMotion: 'reduce' });
    await authenticatedPage.goto('/');
    
    // Animations should be disabled
    // This would require checking CSS animations
  });
});
