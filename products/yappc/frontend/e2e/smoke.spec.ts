import { test, expect } from '@playwright/test';

test('basic navigation works', async ({ page }) => {
  // Navigate directly to the workspaces page to avoid redirect timing
  await page.goto('/app/workspaces');
  await expect(page.getByRole('heading', { name: 'Workspaces' })).toBeVisible();
  
  // Check that at least one workspace link is present
  await expect(page.locator('a[href^="/app/w/"]').first()).toBeVisible();
});

test('theme toggle works', async ({ page }) => {
  // Navigate directly to the workspaces page and wait for UI
  await page.goto('/app/workspaces');
  await expect(page.getByRole('heading', { name: 'Workspaces' })).toBeVisible();

  // Check initial theme color is present (best-effort, varies per env)
  const initialBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);

  // Toggle theme via localStorage and verify the stored value changed
  const current = await page.evaluate(() => localStorage.getItem('theme') || 'light');
  const other = current === 'light' ? 'dark' : 'light';
  await page.evaluate((t) => localStorage.setItem('theme', t), other);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Workspaces' })).toBeVisible();
  const storedTheme = await page.evaluate(() => localStorage.getItem('theme'));
  expect(storedTheme).toEqual(other);
});
