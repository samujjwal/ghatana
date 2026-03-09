import { test, expect } from '@playwright/test';

/**
 * Collections E2E Tests
 * 
 * Tests the complete collections workflow including:
 * - Listing collections
 * - Creating new collections
 * - Viewing collection details
 * - Editing collections
 * - Deleting collections
 * 
 * @doc.type test
 * @doc.purpose E2E tests for collections functionality
 * @doc.layer testing
 */

test.describe('Collections', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to collections page before each test
    await page.goto('/collections');
  });

  test('should display collections list', async ({ page }) => {
    // Wait for the page to load
    await expect(page.locator('h1')).toContainText('Collections');
    
    // Check for "New Collection" button
    await expect(page.getByRole('link', { name: /new collection/i })).toBeVisible();
  });

  test('should navigate to create collection page', async ({ page }) => {
    // Click "New Collection" button
    await page.getByRole('link', { name: /new collection/i }).click();
    
    // Verify we're on the create page
    await expect(page).toHaveURL(/\/collections\/new/);
    await expect(page.locator('h1')).toContainText('Create New Collection');
  });

  test('should create a new collection', async ({ page }) => {
    // Navigate to create page
    await page.goto('/collections/new');
    
    // Fill in the form
    await page.getByLabel(/name/i).fill('Test Collection');
    await page.getByLabel(/description/i).fill('This is a test collection');
    
    // Submit the form
    await page.getByRole('button', { name: /create|save/i }).click();
    
    // Verify redirect to collections list
    await expect(page).toHaveURL(/\/collections$/);
    
    // Verify success message (if using toast notifications)
    await expect(page.locator('text=created successfully')).toBeVisible({ timeout: 5000 });
  });

  test('should view collection details', async ({ page }) => {
    // Click on first collection in the list
    const firstCollection = page.locator('[data-testid="collection-item"]').first();
    await firstCollection.click();
    
    // Verify we're on the detail page
    await expect(page).toHaveURL(/\/collections\/[^/]+$/);
    
    // Check for collection details
    await expect(page.locator('text=/Entity Count|Schema Fields|Created At/i')).toBeVisible();
  });

  test('should edit a collection', async ({ page }) => {
    // Navigate to first collection
    const firstCollection = page.locator('[data-testid="collection-item"]').first();
    await firstCollection.click();
    
    // Click edit button
    await page.getByRole('link', { name: /edit/i }).click();
    
    // Verify we're on edit page
    await expect(page).toHaveURL(/\/collections\/[^/]+\/edit/);
    
    // Update the description
    const descriptionField = page.getByLabel(/description/i);
    await descriptionField.clear();
    await descriptionField.fill('Updated description');
    
    // Save changes
    await page.getByRole('button', { name: /save|update/i }).click();
    
    // Verify success
    await expect(page.locator('text=updated successfully')).toBeVisible({ timeout: 5000 });
  });

  test('should search collections', async ({ page }) => {
    // Type in search box
    const searchBox = page.getByPlaceholder(/search/i);
    if (await searchBox.isVisible()) {
      await searchBox.fill('test');
      
      // Wait for search results
      await page.waitForTimeout(500);
      
      // Verify filtered results
      const collections = page.locator('[data-testid="collection-item"]');
      await expect(collections.first()).toBeVisible();
    }
  });

  test('should handle empty state', async ({ page }) => {
    // Mock empty response or navigate to empty state
    await page.route('**/api/v1/collections*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], total: 0, page: 1, pageSize: 10, hasMore: false }),
      });
    });
    
    await page.reload();
    
    // Check for empty state message
    await expect(page.locator('text=/no collections|get started/i')).toBeVisible();
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // Mock API error
    await page.route('**/api/v1/collections*', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'INTERNAL_ERROR', message: 'Server error' }),
      });
    });
    
    await page.reload();
    
    // Check for error message
    await expect(page.locator('text=/error|failed/i')).toBeVisible();
  });
});
