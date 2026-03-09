/**
 * E2E Test: Moment Creation Flow (Web)
 * Tests creating text, audio, and image moments on web
 */

import { test, expect, waitForApiResponse, uploadFile } from './fixtures';

test.describe('Moment Creation Flow', () => {
  test.use({ storageState: 'auth.json' });

  test('should create text moment', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    
    await expect(authenticatedPage.getByTestId('moment-editor')).toBeVisible();
    
    // Fill in details
    await authenticatedPage.getByTestId('title-input').fill('My First Moment');
    await authenticatedPage.getByTestId('content-input').fill('This is a test moment with some content.');
    
    // Select emotion
    await authenticatedPage.getByTestId('emotion-happy').click();
    
    // Add tags
    await authenticatedPage.getByTestId('tag-input').fill('test');
    await authenticatedPage.getByTestId('add-tag-button').click();
    
    // Save
    const responsePromise = waitForApiResponse(authenticatedPage, '/api/moments');
    await authenticatedPage.getByTestId('save-button').click();
    await responsePromise;
    
    // Should show success message
    await expect(authenticatedPage.getByText(/moment saved/i)).toBeVisible();
    
    // Should navigate to timeline
    await expect(authenticatedPage).toHaveURL('/', { timeout: 3000 });
  });

  test('should record audio moment', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    await authenticatedPage.getByTestId('audio-tab').click();
    
    // Grant microphone permission (handled by browser context)
    await authenticatedPage.context().grantPermissions(['microphone']);
    
    // Start recording
    await authenticatedPage.getByTestId('record-button').click();
    await expect(authenticatedPage.getByTestId('recording-indicator')).toBeVisible();
    
    // Record for 3 seconds
    await authenticatedPage.waitForTimeout(3000);
    
    // Stop recording
    await authenticatedPage.getByTestId('stop-button').click();
    await expect(authenticatedPage.getByTestId('audio-preview')).toBeVisible();
    
    // Add title
    await authenticatedPage.getByTestId('title-input').fill('Test Audio Moment');
    
    // Save
    await authenticatedPage.getByTestId('save-button').click();
    await expect(authenticatedPage.getByText(/moment saved/i)).toBeVisible({ timeout: 10000 });
  });

  test('should upload image moment', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    await authenticatedPage.getByTestId('image-tab').click();
    
    // Upload image
    await uploadFile(
      authenticatedPage,
      '[data-testid="image-upload-button"]',
      'e2e/fixtures/test-image.jpg'
    );
    
    // Wait for image to load
    await expect(authenticatedPage.getByTestId('image-preview')).toBeVisible();
    
    // Add caption
    await authenticatedPage.getByTestId('title-input').fill('Beautiful sunset');
    
    // Save
    await authenticatedPage.getByTestId('save-button').click();
    await expect(authenticatedPage.getByText(/moment saved/i)).toBeVisible({ timeout: 10000 });
  });

  test('should use rich text editor', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    
    const editor = authenticatedPage.getByTestId('content-input');
    
    // Type content
    await editor.fill('This is some text.');
    
    // Bold text
    await editor.press('Control+A');
    await authenticatedPage.getByTestId('bold-button').click();
    
    // Add heading
    await authenticatedPage.getByTestId('heading-button').click();
    await authenticatedPage.getByTestId('heading-1').click();
    
    await expect(authenticatedPage.locator('h1')).toContainText('This is some text.');
  });

  test('should validate required fields', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    
    // Try to save without title
    await authenticatedPage.getByTestId('save-button').click();
    
    await expect(authenticatedPage.getByText(/title is required/i)).toBeVisible();
  });

  test('should save draft', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    
    await authenticatedPage.getByTestId('title-input').fill('Draft Moment');
    await authenticatedPage.getByTestId('content-input').fill('This is a draft.');
    
    // Save as draft
    await authenticatedPage.getByTestId('save-draft-button').click();
    
    await expect(authenticatedPage.getByText(/draft saved/i)).toBeVisible();
  });

  test('should load draft', async ({ authenticatedPage }) => {
    // Navigate to drafts
    await authenticatedPage.getByTestId('user-menu').click();
    await authenticatedPage.getByTestId('drafts-link').click();
    
    await expect(authenticatedPage.getByTestId('drafts-list')).toBeVisible();
    
    // Open first draft
    await authenticatedPage.getByTestId('draft-item-0').click();
    
    // Should load draft content
    await expect(authenticatedPage.getByTestId('moment-editor')).toBeVisible();
    await expect(authenticatedPage.getByTestId('title-input')).not.toBeEmpty();
  });

  test('should cancel moment creation', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    
    await authenticatedPage.getByTestId('title-input').fill('Test');
    
    // Cancel
    await authenticatedPage.getByTestId('cancel-button').click();
    
    // Confirm discard
    await authenticatedPage.getByRole('button', { name: /discard/i }).click();
    
    // Should return to home
    await expect(authenticatedPage).toHaveURL('/');
  });

  test('should add multiple tags', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    
    // Add first tag
    await authenticatedPage.getByTestId('tag-input').fill('family');
    await authenticatedPage.getByTestId('add-tag-button').click();
    
    // Add second tag
    await authenticatedPage.getByTestId('tag-input').fill('vacation');
    await authenticatedPage.getByTestId('add-tag-button').click();
    
    // Should show both tags
    await expect(authenticatedPage.getByTestId('tag-family')).toBeVisible();
    await expect(authenticatedPage.getByTestId('tag-vacation')).toBeVisible();
  });

  test('should remove tag', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    
    await authenticatedPage.getByTestId('tag-input').fill('test');
    await authenticatedPage.getByTestId('add-tag-button').click();
    
    // Remove tag
    await authenticatedPage.getByTestId('tag-test-remove').click();
    
    await expect(authenticatedPage.getByTestId('tag-test')).not.toBeVisible();
  });

  test('should add location', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    
    await authenticatedPage.getByTestId('add-location-button').click();
    
    // Search for location
    await authenticatedPage.getByTestId('location-search').fill('New York');
    await authenticatedPage.waitForTimeout(1000);
    
    // Select first result
    await authenticatedPage.getByTestId('location-result-0').click();
    
    await expect(authenticatedPage.getByTestId('selected-location')).toContainText('New York');
  });

  test('should set privacy level', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    
    // Change privacy to private
    await authenticatedPage.getByTestId('privacy-select').click();
    await authenticatedPage.getByTestId('privacy-private').click();
    
    await expect(authenticatedPage.getByTestId('privacy-select')).toContainText('Private');
  });
});
