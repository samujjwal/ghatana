import { test, expect } from '@playwright/test';

// Basic smoke tests for the TutorPutor Admin console.
// Assumes API gateway is running on :3200 and the admin dev server on :3202.

test('loads dashboard and navigation', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByText('TutorPutor Admin')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Users' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Marketplace' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Templates' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Audit Logs' })).toBeVisible();
});

test('navigates to Marketplace admin page', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('link', { name: 'Marketplace' }).click();

  await expect(
    page.getByText('Marketplace Administration')
  ).toBeVisible();
});

test('navigates to Templates admin page', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('link', { name: 'Templates' }).click();

  await expect(
    page.getByText('Simulation Template Curation')
  ).toBeVisible();
});

test('navigates to Analytics and shows tabs', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('link', { name: 'Analytics' }).click();

  // The Analytics page uses plain buttons as tab controls, not ARIA tab roles.
  await expect(page.getByRole('button', { name: /engagement/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /content/i })).toBeVisible();
});
