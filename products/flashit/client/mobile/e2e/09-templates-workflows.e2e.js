/**
 * E2E Test: Templates & Workflows
 * Tests template usage and workflow automation
 */

const { device, element, by, waitFor } = require('detox');
const { loginUser, scrollToElement, takeScreenshot } = require('./helpers/setup');

describe('Templates & Workflows Flow', () => {
  beforeAll(async () => {
    await device.launchApp();
    await loginUser();
  });

  beforeEach(async () => {
    await device.reloadReactNative();
  });

  it('should view template library', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('templates-button')).tap();
    
    await expect(element(by.id('template-library'))).toBeVisible();
    await expect(element(by.id('template-category-gratitude'))).toBeVisible();
    await expect(element(by.id('template-category-goals'))).toBeVisible();
    
    await takeScreenshot('template-library');
  });

  it('should use gratitude template', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('templates-button')).tap();
    
    // Select gratitude template
    await element(by.id('template-gratitude-daily')).tap();
    
    // Should show template form
    await expect(element(by.id('template-form'))).toBeVisible();
    await expect(element(by.id('field-what-are-you-grateful-for'))).toBeVisible();
    
    // Fill template
    await element(by.id('field-what-are-you-grateful-for')).typeText('My family and health');
    await element(by.id('field-why')).typeText('They support me always');
    
    // Save
    await element(by.id('save-button')).tap();
    
    await waitFor(element(by.text('Moment saved successfully')))
      .toBeVisible()
      .withTimeout(5000);
    
    await takeScreenshot('gratitude-template-filled');
  });

  it('should create custom template', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('templates-button')).tap();
    
    // Create new template
    await element(by.id('create-template-button')).tap();
    
    await expect(element(by.id('template-builder'))).toBeVisible();
    
    // Add template details
    await element(by.id('template-name-input')).typeText('My Custom Template');
    await element(by.id('template-description-input')).typeText('For daily check-ins');
    
    // Add fields
    await element(by.id('add-field-button')).tap();
    await element(by.id('field-type-text')).tap();
    await element(by.id('field-label-input')).typeText('How was your day?');
    await element(by.id('confirm-field-button')).tap();
    
    // Save template
    await element(by.id('save-template-button')).tap();
    
    await waitFor(element(by.text('Template created')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should view workflows', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('workflows-button')).tap();
    
    await expect(element(by.id('workflows-screen'))).toBeVisible();
    await expect(element(by.id('workflow-list'))).toBeVisible();
    
    await takeScreenshot('workflows-list');
  });

  it('should create reminder workflow', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('workflows-button')).tap();
    
    await element(by.id('create-workflow-button')).tap();
    
    // Select workflow type
    await element(by.id('workflow-type-reminder')).tap();
    
    // Configure reminder
    await element(by.id('reminder-title-input')).typeText('Daily Reflection');
    await element(by.id('reminder-time-select')).tap();
    await element(by.text('20:00')).tap(); // Select 8 PM
    
    // Select days
    await element(by.id('day-monday')).tap();
    await element(by.id('day-wednesday')).tap();
    await element(by.id('day-friday')).tap();
    
    // Save workflow
    await element(by.id('save-workflow-button')).tap();
    
    await waitFor(element(by.text('Workflow created')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should enable auto-tagging workflow', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('workflows-button')).tap();
    
    // Find auto-tagging workflow
    await scrollToElement('workflow-list', by.id('workflow-auto-tagging'));
    
    // Enable workflow
    await element(by.id('workflow-auto-tagging-toggle')).tap();
    
    await waitFor(element(by.text('Workflow enabled')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should edit workflow', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('workflows-button')).tap();
    
    // Open workflow
    await element(by.id('workflow-item-0')).tap();
    
    // Edit
    await element(by.id('edit-workflow-button')).tap();
    
    // Modify settings
    await element(by.id('reminder-title-input')).clearText();
    await element(by.id('reminder-title-input')).typeText('Updated Reminder');
    
    // Save
    await element(by.id('save-workflow-button')).tap();
    
    await waitFor(element(by.text('Workflow updated')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should delete workflow', async () => {
    await element(by.id('settings-tab')).tap();
    await element(by.id('workflows-button')).tap();
    
    // Swipe to delete
    await element(by.id('workflow-item-0')).swipe('left', 'fast');
    await element(by.id('delete-workflow-button')).tap();
    
    // Confirm
    await element(by.text('Delete')).tap();
    
    await waitFor(element(by.text('Workflow deleted')))
      .toBeVisible()
      .withTimeout(3000);
  });

  it('should apply template tag automatically', async () => {
    // Enable auto-tagging workflow first
    await element(by.id('settings-tab')).tap();
    await element(by.id('workflows-button')).tap();
    await scrollToElement('workflow-list', by.id('workflow-auto-tagging'));
    await element(by.id('workflow-auto-tagging-toggle')).tap();
    await device.pressBack();
    
    // Create moment with keywords that trigger tags
    await element(by.id('capture-tab')).tap();
    await element(by.id('voice-mode-button')).tap();
    await element(by.id('record-button')).tap();
    await new Promise(resolve => setTimeout(resolve, 2000));
    await element(by.id('stop-button')).tap();
    
    await element(by.id('moment-title-input')).typeText('Family vacation planning');
    await element(by.id('save-button')).tap();
    
    // Check that tags were auto-applied
    await waitFor(element(by.text('Moment saved successfully')))
      .toBeVisible()
      .withTimeout(5000);
    
    // View moment to verify tags
    await element(by.id('home-tab')).tap();
    await element(by.id('moment-card-0')).tap();
    
    // Should have "family" and "planning" tags
    await expect(element(by.id('tag-family'))).toBeVisible();
  });

  it('should favorite template', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('templates-button')).tap();
    
    // Favorite a template
    await element(by.id('template-gratitude-daily-favorite')).tap();
    
    // Should appear in favorites section
    await expect(element(by.id('favorites-section'))).toBeVisible();
  });

  it('should share custom template', async () => {
    await element(by.id('capture-tab')).tap();
    await element(by.id('templates-button')).tap();
    
    // Open custom template
    await element(by.id('template-custom-0')).longPress();
    
    // Share
    await element(by.id('share-template-button')).tap();
    
    // Select user
    await element(by.id('user-search-input')).typeText('friend@flashit.app');
    await waitFor(element(by.id('user-result-0'))).toBeVisible().withTimeout(3000);
    await element(by.id('user-result-0')).tap();
    
    await element(by.id('confirm-share-button')).tap();
    
    await waitFor(element(by.text('Template shared')))
      .toBeVisible()
      .withTimeout(3000);
  });
});
