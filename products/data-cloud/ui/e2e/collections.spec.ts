import { test, expect } from '@playwright/test';

/**
 * Data Explorer E2E Tests
 * 
 * Tests the complete data explorer workflow including:
 * - Listing collections
 * - Creating new collections
 * - Viewing collection details
 * - Editing collections
 * - Deleting collections
 * 
 * @doc.type test
 * @doc.purpose E2E tests for data explorer functionality
 * @doc.layer testing
 */

test.describe('Data Explorer', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/data');
  });

  test('should display collections list', async ({ page }) => {
    await expect(page.getByTestId('data-explorer-page')).toBeVisible();
    await expect(page.locator('h1')).toContainText('Data Explorer');
    await expect(page.getByTestId('create-collection-button')).toBeVisible();
  });

  test('should navigate to create collection page', async ({ page }) => {
    // Click "New Collection" button
    await page.getByTestId('create-collection-button').click();
    
    await expect(page).toHaveURL(/\/data\/new/);
    await expect(page.locator('h1')).toContainText('Create New Collection');
  });

  test('should create a new collection', async ({ page }) => {
    await page.goto('/data/new');
    
    // Fill in the form
    await page.getByLabel(/name/i).fill('Test Collection');
    await page.getByLabel(/description/i).fill('This is a test collection');
    
    // Submit the form
    await page.getByRole('button', { name: /create|save/i }).click();
    
    await expect(page).toHaveURL(/\/data$/);
    await expect(page.locator('text=created successfully')).toBeVisible({ timeout: 5000 });
  });

  test('should view collection details', async ({ page }) => {
    // Click on first collection in the list
    const firstCollection = page.getByTestId('collection-item').first();
    await firstCollection.click();
    
    await expect(page).toHaveURL(/\/data\/[^/]+$/);
    await expect(page.locator('text=/Entity Count|Schema Fields|Created At/i')).toBeVisible();
  });

  test('should edit a collection', async ({ page }) => {
    // Navigate to first collection
    const firstCollection = page.getByTestId('collection-item').first();
    await firstCollection.click();
    
    // Click edit button
    await page.getByRole('link', { name: /edit/i }).click();
    
    await expect(page).toHaveURL(/\/data\/[^/]+\/edit/);

    const descriptionField = page.getByLabel(/description/i);
    await descriptionField.clear();
    await descriptionField.fill('Updated description');
    
    // Save changes
    await page.getByRole('button', { name: /save|update/i }).click();
    
    await expect(page.locator('text=updated successfully')).toBeVisible({ timeout: 5000 });
  });

  test('should search collections', async ({ page }) => {
    // Type in search box
    const searchBox = page.getByTestId('collection-search-input');
    if (await searchBox.isVisible()) {
      await searchBox.fill('test');
      
      // Wait for search results
      await page.waitForTimeout(500);
      
      // Verify filtered results
      const collections = page.getByTestId('collection-item');
      await expect(collections.first()).toBeVisible();
    }
  });

  test('should handle empty state', async ({ page }) => {
    // Mock empty response or navigate to empty state
    await page.route('**/api/v1/entities/dc_collections*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ entities: [], count: 0 }),
      });
    });
    
    await page.reload();
    
    // Check for empty state message
    await expect(page.locator('text=/no collections|get started/i')).toBeVisible();
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // Mock API error
    await page.route('**/api/v1/entities/dc_collections*', async (route) => {
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
