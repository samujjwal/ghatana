import { test, expect } from '@playwright/test';
import { registerMockRoutes } from './fixtures';

/**
 * Debug test to investigate lazy loading issues
 */
test.describe('Debug Lazy Loading', () => {
  test('should log component loading', async ({ page }) => {
    // Capture console logs and errors
    page.on('console', msg => console.log('BROWSER LOG:', msg.text()));
    page.on('pageerror', err => console.log('BROWSER ERROR:', err));
    
  // Register mocked API routes before navigation
  await registerMockRoutes(page);

  // Navigate and login
  await page.goto('/login');
    await page.getByLabel(/email/i).fill('parent@example.com');
    await page.getByLabel(/password/i).fill('password123');
    await page.getByRole('button', { name: /sign in/i }).click();
    await expect(page).toHaveURL('/dashboard');
    
    console.log('✅ Logged in, on dashboard');
    
    // Wait for page to settle
    await page.waitForLoadState('networkidle');
    console.log('✅ Network idle');
    
    // Check what's on the page
    const html = await page.content();
    console.log('Page has Dashboard Overview:', html.includes('Dashboard Overview'));
    console.log('Page has UsageMonitor:', html.includes('Usage Monitor'));
    console.log('Page has BlockNotifications:', html.includes('Block Event'));
    console.log('Page has PolicyManagement:', html.includes('Policy Management'));
    console.log('Page has DeviceManagement:', html.includes('Device Management'));
    console.log('Page has Analytics:', html.includes('Analytics & Insights'));
    console.log('Page length:', html.length, 'chars');
    
    // Check for Suspense fallbacks (ComponentLoader)
    const loaders = await page.locator('[role="status"]').count();
    console.log(`Found ${loaders} loading indicators`);
    
    // Scroll to bottom
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    console.log('✅ Scrolled to bottom');
    
    // Wait a bit
    await page.waitForTimeout(5000);
    
    // Check again
    const html2 = await page.content();
    console.log('\nAfter waiting 5s:');
    console.log('Page has PolicyManagement:', html2.includes('Policy Management'));
    console.log('Page has DeviceManagement:', html2.includes('Device Management'));
    console.log('Page has Analytics:', html2.includes('Analytics & Insights'));
    
    // Check for loaders again
    const loaders2 = await page.locator('[role="status"]').count();
    console.log(`Found ${loaders2} loading indicators`);
    
    // List all h2 headings
    const headings = await page.locator('h2').allTextContents();
    console.log('\nAll h2 headings on page:', headings);
  });
});
