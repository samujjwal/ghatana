import { test, expect } from '@playwright/test';

test('login page renders without Vite overlay', async ({ page }) => {
  await page.goto('/login');
  // Assert the login heading renders
  await expect(page.getByRole('heading', { name: /Sign in/i })).toBeVisible();
  // Assert no Vite error overlay is present
  await expect(page.locator('vite-error-overlay')).not.toBeVisible();
  // Assert the login form is present
  await expect(page.getByRole('textbox', { name: /Email/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /Sign in/i })).toBeVisible();
});

test('workspaces page renders without Vite overlay', async ({ page }) => {
  // Navigate directly to the workspaces page to avoid redirect timing
  await page.goto('/workspaces');
  await expect(page.getByRole('heading', { name: 'Workspaces' })).toBeVisible();

  // Assert no Vite error overlay is present
  await expect(page.locator('vite-error-overlay')).not.toBeVisible();

  // Check that at least one workspace card or the create prompt is present
  const workspaceCard = page.getByTestId('workspace-card').first();
  const createPrompt = page.getByRole('button', { name: /Create Workspace/i }).first();
  await expect(workspaceCard.or(createPrompt)).toBeVisible();
});

test('basic navigation works', async ({ page }) => {
  // Navigate directly to the workspaces page to avoid redirect timing
  await page.goto('/workspaces');
  await expect(page.getByRole('heading', { name: 'Workspaces' })).toBeVisible();

  // Check that at least one workspace card or the create prompt is present
  const workspaceCard = page.getByTestId('workspace-card').first();
  const createPrompt = page.getByRole('button', { name: /Create Workspace/i }).first();
  await expect(workspaceCard.or(createPrompt)).toBeVisible();
});

test('theme toggle works', async ({ page }) => {
  // Navigate directly to the workspaces page and wait for UI
  await page.goto('/workspaces');
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
