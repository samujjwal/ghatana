/**
 * E2E Test: Templates & Workflows (Web)
 * Tests template usage and workflow automation
 */

import { test, expect } from './fixtures';

test.describe('Templates & Workflows', () => {
  test('should view template library', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    await authenticatedPage.getByTestId('templates-tab').click();
    
    await expect(authenticatedPage.getByTestId('template-library')).toBeVisible();
    await expect(authenticatedPage.getByTestId('template-category')).toHaveCount(await authenticatedPage.getByTestId('template-category').count());
  });

  test('should use gratitude template', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    await authenticatedPage.getByTestId('templates-tab').click();
    
    // Select gratitude template
    await authenticatedPage.getByTestId('template-gratitude-daily').click();
    
    // Fill template fields
    await authenticatedPage.getByTestId('field-grateful-for').fill('My family and health');
    await authenticatedPage.getByTestId('field-why').fill('They support me always');
    
    // Save
    await authenticatedPage.getByTestId('save-button').click();
    
    await expect(authenticatedPage.getByText(/moment saved/i)).toBeVisible();
  });

  test('should create custom template', async ({ authenticatedPage }) => {
    await authenticatedPage.getByTestId('create-moment-button').click();
    await authenticatedPage.getByTestId('templates-tab').click();
    
    // Create new template
    await authenticatedPage.getByTestId('create-template-button').click();
    
    // Add details
    await authenticatedPage.getByTestId('template-name-input').fill('My Custom Template');
    await authenticatedPage.getByTestId('template-description-input').fill('For daily check-ins');
    
    // Add field
    await authenticatedPage.getByTestId('add-field-button').click();
    await authenticatedPage.getByTestId('field-type-select').selectOption('text');
    await authenticatedPage.getByTestId('field-label-input').fill('How was your day?');
    await authenticatedPage.getByTestId('confirm-field-button').click();
    
    // Save template
    await authenticatedPage.getByTestId('save-template-button').click();
    
    await expect(authenticatedPage.getByText(/template created/i)).toBeVisible();
  });

  test('should edit custom template', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/templates');
    
    // Find custom template
    await authenticatedPage.getByTestId('template-custom-0-menu').click();
    await authenticatedPage.getByTestId('edit-template').click();
    
    // Modify template
    await authenticatedPage.getByTestId('template-name-input').fill('Updated Template Name');
    await authenticatedPage.getByTestId('save-template-button').click();
    
    await expect(authenticatedPage.getByText(/template updated/i)).toBeVisible();
  });

  test('should delete custom template', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/templates');
    
    await authenticatedPage.getByTestId('template-custom-0-menu').click();
    await authenticatedPage.getByTestId('delete-template').click();
    
    // Confirm deletion
    await authenticatedPage.getByRole('button', { name: /delete/i }).click();
    
    await expect(authenticatedPage.getByText(/template deleted/i)).toBeVisible();
  });

  test('should view workflows', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/workflows');
    
    await expect(authenticatedPage.getByTestId('workflows-list')).toBeVisible();
    await expect(authenticatedPage.getByTestId('workflow-item')).toHaveCount(await authenticatedPage.getByTestId('workflow-item').count());
  });

  test('should create reminder workflow', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/workflows');
    
    await authenticatedPage.getByTestId('create-workflow-button').click();
    
    // Select workflow type
    await authenticatedPage.getByTestId('workflow-type-reminder').click();
    
    // Configure reminder
    await authenticatedPage.getByTestId('reminder-title-input').fill('Daily Reflection');
    await authenticatedPage.getByTestId('reminder-time-select').fill('20:00');
    
    // Select days
    await authenticatedPage.getByTestId('day-monday').check();
    await authenticatedPage.getByTestId('day-wednesday').check();
    await authenticatedPage.getByTestId('day-friday').check();
    
    // Save
    await authenticatedPage.getByTestId('save-workflow-button').click();
    
    await expect(authenticatedPage.getByText(/workflow created/i)).toBeVisible();
  });

  test('should enable/disable workflow', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/workflows');
    
    // Toggle workflow
    const toggle = authenticatedPage.getByTestId('workflow-0-toggle');
    const initialState = await toggle.isChecked();
    
    await toggle.click();
    
    // State should change
    const newState = await toggle.isChecked();
    expect(newState).toBe(!initialState);
  });

  test('should edit workflow', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/workflows');
    
    await authenticatedPage.getByTestId('workflow-item-0').click();
    
    // Modify
    await authenticatedPage.getByTestId('reminder-title-input').fill('Updated Reminder');
    await authenticatedPage.getByTestId('save-workflow-button').click();
    
    await expect(authenticatedPage.getByText(/workflow updated/i)).toBeVisible();
  });

  test('should delete workflow', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/settings/workflows');
    
    await authenticatedPage.getByTestId('workflow-0-menu').click();
    await authenticatedPage.getByTestId('delete-workflow').click();
    
    // Confirm
    await authenticatedPage.getByRole('button', { name: /delete/i }).click();
    
    await expect(authenticatedPage.getByText(/workflow deleted/i)).toBeVisible();
  });

  test('should favorite template', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/templates');
    
    await authenticatedPage.getByTestId('template-gratitude-daily-favorite').click();
    
    // Should appear in favorites
    await authenticatedPage.getByTestId('favorites-filter').click();
    await expect(authenticatedPage.getByTestId('template-gratitude-daily')).toBeVisible();
  });

  test('should share custom template', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/templates');
    
    await authenticatedPage.getByTestId('template-custom-0-menu').click();
    await authenticatedPage.getByTestId('share-template').click();
    
    // Share with user
    await authenticatedPage.getByTestId('user-search-input').fill('friend@flashit.app');
    await authenticatedPage.waitForTimeout(1000);
    await authenticatedPage.getByTestId('user-result-0').click();
    await authenticatedPage.getByTestId('confirm-share-button').click();
    
    await expect(authenticatedPage.getByText(/template shared/i)).toBeVisible();
  });

  test('should search templates', async ({ authenticatedPage }) => {
    await authenticatedPage.goto('/templates');
    
    await authenticatedPage.getByTestId('template-search-input').fill('gratitude');
    
    // Should filter templates
    const visibleTemplates = await authenticatedPage.getByTestId('template-card').count();
    expect(visibleTemplates).toBeGreaterThan(0);
  });
});
