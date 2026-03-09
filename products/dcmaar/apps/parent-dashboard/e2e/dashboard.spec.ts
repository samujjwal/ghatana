import { test, expect } from '@playwright/test';
import { registerMockRoutes } from './fixtures';

/**
 * E2E tests for dashboard functionality.
 *
 * Tests validate:
 * - Dashboard page loading
 * - All dashboard components render
 * - WebSocket connection status
 * - Component lazy loading
 * - Responsive design
 * - Navigation between dashboard sections
 *
 * @doc.type test-suite
 * @doc.purpose E2E tests for dashboard page and components
 * @doc.layer e2e-testing
 */

// Helper to login before each test
async function login(page: any) {
  await page.goto('/login');
  await page.getByLabel(/email/i).fill('parent@example.com');
  await page.getByLabel(/password/i).fill('password123');
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).toHaveURL('/dashboard');
}

test.describe('Dashboard Page', () => {
  test.beforeEach(async ({ page, context }) => {
    await context.clearCookies();
    await page.goto('/');
    await page.evaluate(() => { try { localStorage.clear(); } catch (e) {} });
    await registerMockRoutes(page);
    await login(page);
  });

  /**
   * Verifies dashboard loads with all main sections.
   *
   * GIVEN: Authenticated user
   * WHEN: Dashboard page loads
   * THEN: All main sections are visible
   */
  test('should display dashboard with all sections', async ({ page }) => {
    // Main heading
    await expect(page.getByText('Guardian Dashboard')).toBeVisible();

    // Dashboard overview section
    await expect(page.getByText('Dashboard Overview')).toBeVisible();

    // Stats cards - check specific card titles and values
    await expect(page.getByText('WebSocket Status')).toBeVisible();
    await expect(page.getByText('User Role')).toBeVisible();
    // Check Status card (shows "Active")
    await expect(page.locator('.bg-purple-50').getByText('Status')).toBeVisible();
    await expect(page.getByText('Active')).toBeVisible();
  });

  /**
   * Verifies lazy-loaded components render.
   *
   * GIVEN: Dashboard page loaded
   * WHEN: Components finish lazy loading
   * THEN: All components are visible
   */
  test('should load all dashboard components', async ({ page }) => {
    // Wait for lazy components to load - check for actual component headings
    await expect(page.getByText('Usage Monitor', { exact: false })).toBeVisible({ timeout: 5000 });
    await expect(page.getByRole('heading', { name: /block event notifications/i })).toBeVisible({ timeout: 5000 });
    await expect(page.getByRole('heading', { name: /policy management/i })).toBeVisible({ timeout: 5000 });
    await expect(page.getByRole('heading', { name: /device management/i })).toBeVisible({ timeout: 5000 });
    await expect(page.getByRole('heading', { name: /analytics & insights/i })).toBeVisible({ timeout: 5000 });
  });

  /**
   * Verifies WebSocket connection indicator.
   *
   * GIVEN: Dashboard loaded
   * WHEN: WebSocket connection established
   * THEN: Connection status displays correctly
   */
  test('should display WebSocket connection status', async ({ page }) => {
    // Check for connection status
    const statusElement = page.locator('text=/Connected|Disconnected/');
    await expect(statusElement).toBeVisible();
  });

  /**
   * Verifies user email displays in header.
   *
   * GIVEN: Authenticated user
   * WHEN: Dashboard loads
   * THEN: User email is visible in navigation
   */
  test('should display user email in navigation', async ({ page }) => {
    await expect(page.getByText('parent@example.com')).toBeVisible();
  });

  /**
   * Verifies logout button functionality.
   *
   * GIVEN: User on dashboard
   * WHEN: Logout button clicked
   * THEN: User redirected to login page
   */
  test('should logout from dashboard', async ({ page }) => {
    await page.getByRole('button', { name: /logout/i }).click();
    
    await expect(page).toHaveURL('/login');
    await expect(page.getByRole('heading', { name: /guardian dashboard/i })).toBeVisible();
  });

  /**
   * Verifies progress indicators display.
   *
   * GIVEN: Dashboard loaded
   * WHEN: Page scrolled to progress section
   * THEN: Week progress checklist is visible
   */
  test('should display week progress indicators', async ({ page }) => {
    await expect(page.getByText('Week 3 Progress')).toBeVisible();
    await expect(page.getByText(/Day 1.*Authentication/)).toBeVisible();
    await expect(page.getByText(/Day 2.*Usage Monitoring/)).toBeVisible();
  });
});

test.describe('Dashboard Responsiveness', () => {
  test.beforeEach(async ({ page, context }) => {
    await context.clearCookies();
    await page.goto('/');
    await page.evaluate(() => { try { localStorage.clear(); } catch (e) {} });
    await registerMockRoutes(page);
    await login(page);
  });

  /**
   * Verifies dashboard is responsive on mobile viewport.
   *
   * GIVEN: Mobile viewport (375px)
   * WHEN: Dashboard loads
   * THEN: Layout adapts to mobile size
   */
  test('should be responsive on mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });

    // Main content should still be visible
    await expect(page.getByText('Guardian Dashboard')).toBeVisible();
    await expect(page.getByRole('button', { name: /logout/i })).toBeVisible();
  });

  /**
   * Verifies dashboard is responsive on tablet viewport.
   *
   * GIVEN: Tablet viewport (768px)
   * WHEN: Dashboard loads
   * THEN: Layout adapts to tablet size
   */
  test('should be responsive on tablet viewport', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });

    // Stats cards should be visible
    await expect(page.getByText('WebSocket Status')).toBeVisible();
    await expect(page.getByText('User Role')).toBeVisible();
  });

  /**
   * Verifies dashboard is responsive on desktop viewport.
   *
   * GIVEN: Desktop viewport (1920px)
   * WHEN: Dashboard loads
   * THEN: Layout utilizes full width
   */
  test('should be responsive on desktop viewport', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });

    // All sections should be visible
    await expect(page.getByText('Dashboard Overview')).toBeVisible();
    await expect(page.getByText('WebSocket Status')).toBeVisible();
  });
});

test.describe('Dashboard Accessibility', () => {
  test.beforeEach(async ({ page, context }) => {
    await context.clearCookies();
    await page.goto('/');
    await page.evaluate(() => { try { localStorage.clear(); } catch (e) {} });
    await registerMockRoutes(page);
    await login(page);
  });

  /**
   * Verifies semantic HTML landmarks.
   *
   * GIVEN: Dashboard loaded
   * WHEN: Checking page structure
   * THEN: Proper landmarks present (nav, main)
   */
  test('should have semantic HTML landmarks', async ({ page }) => {
    const nav = page.locator('nav');
    await expect(nav).toBeVisible();

    const main = page.locator('main');
    await expect(main).toBeVisible();
  });

  /**
   * Verifies heading hierarchy.
   *
   * GIVEN: Dashboard loaded
   * WHEN: Checking headings
   * THEN: Proper h1, h2, h3 structure
   */
  test('should have proper heading hierarchy', async ({ page }) => {
    const h1 = page.locator('h1');
    await expect(h1).toHaveCount(1);
    await expect(h1).toHaveText(/Guardian Dashboard/);

    const h2 = page.locator('h2').first();
    await expect(h2).toBeVisible();
  });

  /**
   * Verifies keyboard navigation works.
   *
   * GIVEN: Dashboard loaded
   * WHEN: Tab key pressed
   * THEN: Focus moves between interactive elements
   */
  test('should support keyboard navigation', async ({ page }) => {
    // Press Tab multiple times to move through focusable elements
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    
    // After tabbing, some interactive element should have focus
    // We can check that focus is not on the body
    const focusedElement = await page.evaluate(() => document.activeElement?.tagName);
    expect(focusedElement).not.toBe('BODY');
  });

  /**
   * Verifies button accessibility.
   *
   * GIVEN: Dashboard loaded
   * WHEN: Checking buttons
   * THEN: All buttons have accessible names
   */
  test('should have accessible buttons', async ({ page }) => {
    const logoutButton = page.getByRole('button', { name: /logout/i });
    await expect(logoutButton).toBeVisible();
    
    const buttonText = await logoutButton.textContent();
    expect(buttonText).toBeTruthy();
  });
});
