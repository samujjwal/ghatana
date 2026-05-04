import { test, expect } from '@playwright/test';
import { loginAs } from './fixtures';

/**
 * Accessibility tests using Axe Playwright (DMOS-P2-004)
 *
 * @doc.type test
 * @doc.purpose Verify UI accessibility using Axe
 * @doc.layer e2e
 */
test.describe('Accessibility @a11y', () => {
  test.beforeEach(async ({ page }) => {
    await page.addScriptTag({
      content: `
        window.addEventListener('load', () => {
          const script = document.createElement('script');
          script.src = 'https://cdnjs.cloudflare.com/ajax/libs/axe-core/4.8.2/axe.min.js';
          script.onload = async () => {
            window.axe = axe;
          };
          document.head.appendChild(script);
        });
      `,
    });
  });

  test('login page has no accessibility violations', async ({ page }) => {
    await page.goto('/login');
    await page.waitForLoadState('networkidle');
    
    // Wait for axe-core to load
    await page.waitForFunction(() => typeof window.axe !== 'undefined');
    
    const violations = await page.evaluate(async () => {
      const results = await window.axe.run();
      return results.violations;
    });
    
    expect(violations).toHaveLength(0);
  });

  test('dashboard has no accessibility violations', async ({ page }) => {
    await loginAs(page);
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
    
    // Wait for axe-core to load
    await page.waitForFunction(() => typeof window.axe !== 'undefined');
    
    const violations = await page.evaluate(async () => {
      const results = await window.axe.run();
      return results.violations;
    });
    
    expect(violations).toHaveLength(0);
  });

  test('approvals page has no accessibility violations', async ({ page }) => {
    await loginAs(page);
    await page.goto('/approvals');
    await page.waitForLoadState('networkidle');
    
    // Wait for axe-core to load
    await page.waitForFunction(() => typeof window.axe !== 'undefined');
    
    const violations = await page.evaluate(async () => {
      const results = await window.axe.run();
      return results.violations;
    });
    
    expect(violations).toHaveLength(0);
  });

  test('strategy page has no accessibility violations', async ({ page }) => {
    await loginAs(page);
    await page.goto('/strategy');
    await page.waitForLoadState('networkidle');
    
    // Wait for axe-core to load
    await page.waitForFunction(() => typeof window.axe !== 'undefined');
    
    const violations = await page.evaluate(async () => {
      const results = await window.axe.run();
      return results.violations;
    });
    
    expect(violations).toHaveLength(0);
  });

  test('content page has no accessibility violations', async ({ page }) => {
    await loginAs(page);
    await page.goto('/content');
    await page.waitForLoadState('networkidle');
    
    // Wait for axe-core to load
    await page.waitForFunction(() => typeof window.axe !== 'undefined');
    
    const violations = await page.evaluate(async () => {
      const results = await window.axe.run();
      return results.violations;
    });
    
    expect(violations).toHaveLength(0);
  });

  test('campaigns page has no accessibility violations', async ({ page }) => {
    await loginAs(page);
    await page.goto('/campaigns');
    await page.waitForLoadState('networkidle');
    
    // Wait for axe-core to load
    await page.waitForFunction(() => typeof window.axe !== 'undefined');
    
    const violations = await page.evaluate(async () => {
      const results = await window.axe.run();
      return results.violations;
    });
    
    expect(violations).toHaveLength(0);
  });

  test('leads page has no accessibility violations', async ({ page }) => {
    await loginAs(page);
    await page.goto('/leads');
    await page.waitForLoadState('networkidle');
    
    // Wait for axe-core to load
    await page.waitForFunction(() => typeof window.axe !== 'undefined');
    
    const violations = await page.evaluate(async () => {
      const results = await window.axe.run();
      return results.violations;
    });
    
    expect(violations).toHaveLength(0);
  });

  test('analytics page has no accessibility violations', async ({ page }) => {
    await loginAs(page);
    await page.goto('/analytics');
    await page.waitForLoadState('networkidle');
    
    // Wait for axe-core to load
    await page.waitForFunction(() => typeof window.axe !== 'undefined');
    
    const violations = await page.evaluate(async () => {
      const results = await window.axe.run();
      return results.violations;
    });
    
    expect(violations).toHaveLength(0);
  });

  test('connectors page has no accessibility violations', async ({ page }) => {
    await loginAs(page);
    await page.goto('/connectors');
    await page.waitForLoadState('networkidle');
    
    // Wait for axe-core to load
    await page.waitForFunction(() => typeof window.axe !== 'undefined');
    
    const violations = await page.evaluate(async () => {
      const results = await window.axe.run();
      return results.violations;
    });
    
    expect(violations).toHaveLength(0);
  });

  test('AI recommendations page has no accessibility violations', async ({ page }) => {
    await loginAs(page);
    await page.goto('/ai-recommendations');
    await page.waitForLoadState('networkidle');
    
    // Wait for axe-core to load
    await page.waitForFunction(() => typeof window.axe !== 'undefined');
    
    const violations = await page.evaluate(async () => {
      const results = await window.axe.run();
      return results.violations;
    });
    
    expect(violations).toHaveLength(0);
  });
});
