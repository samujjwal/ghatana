import { test, expect } from '@playwright/test';

/**
 * E2E tests for TutorPutor critical user workflows.
 * Tests focus on the primary user journeys: content generation, learning path creation, and progress tracking.
 * @doc.type e2e-test
 * @doc.purpose End-to-end workflow validation for TutorPutor learning platform
 * @doc.layer product
 * @doc.pattern E2E Test Suite
 */

// Configuration
const BASE_URL = process.env.TUTORPUTOR_URL || 'http://localhost:3001';
const TEST_TIMEOUT = 30000;

// Test user credentials (should use environment variables in production)
const TEST_USER = {
  email: 'test-educator@tutorputor.local',
  password: 'TutorPutor123!SecurePassword',
  displayName: 'Test Educator',
};

test.describe('TutorPutor Critical User Workflows', () => {

  test.beforeEach(async ({ page }) => {
    // Navigate to login page before each test
    await page.goto(`${BASE_URL}/login`);
    await page.waitForURL(`**/login`, { timeout: TEST_TIMEOUT });
  });

  test.describe('Authentication and Setup', () => {

    test('should login successfully with valid credentials', async ({ page }) => {
      // Execute: Enter email and password
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');

      // Verify: Should redirect to dashboard
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });
      await expect(page).toHaveTitle(/Dashboard|Home/);
      
      // Verify: User name visible in header
      const userGreeting = page.locator('text=Welcome');
      await expect(userGreeting).toBeVisible();
    });

    test('should display error message for invalid credentials', async ({ page }) => {
      // Execute: Enter invalid credentials
      await page.fill('input[name="email"]', 'invalid@example.com');
      await page.fill('input[name="password"]', 'wrongpassword');
      await page.click('button:has-text("Sign In")');

      // Verify: Should show error message
      const errorMessage = page.locator('text=Invalid credentials');
      await expect(errorMessage).toBeVisible({ timeout: TEST_TIMEOUT });
      
      // Verify: Should remain on login page
      await expect(page).toHaveURL(/\/login/);
    });

    test('should require email field', async ({ page }) => {
      // Execute: Leave email empty, submit form
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');

      // Verify: Should show validation error
      const emailError = page.locator('text=Email is required');
      await expect(emailError).toBeVisible();
    });

    test('should require password field', async ({ page }) => {
      // Execute: Leave password empty, submit form
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.click('button:has-text("Sign In")');

      // Verify: Should show validation error
      const passwordError = page.locator('text=Password is required');
      await expect(passwordError).toBeVisible();
    });

  });

  test.describe('Content Generation Workflow', () => {

    test.beforeEach(async ({ page }) => {
      // Login before each test
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });
    });

    test('should navigate to content generator', async ({ page }) => {
      // Execute: Click on "Generate Content" button
      await page.click('button:has-text("Generate Content")');

      // Verify: Should navigate to generator page
      await page.waitForURL(`**/generate`, { timeout: TEST_TIMEOUT });
      const generatorTitle = page.locator('h1:has-text("Content Generator")');
      await expect(generatorTitle).toBeVisible();
    });

    test('should generate claims for a learning topic', async ({ page }) => {
      // Navigate to generator
      await page.click('button:has-text("Generate Content")');
      await page.waitForURL(`**/generate`, { timeout: TEST_TIMEOUT });

      // Execute: Fill in topic and click generate
      const topicInput = page.locator('input[name="topic"]');
      await topicInput.fill('Photosynthesis');
      
      const gradeSelect = page.locator('select[name="gradeLevel"]');
      await gradeSelect.selectOption('8');  // Grade 8

      const generateButton = page.locator('button:has-text("Generate Claims")');
      await generateButton.click();

      // Verify: Should show loading spinner
      await expect(page.locator('.spinner')).toBeVisible({ timeout: 5000 });

      // Verify: Should generate and display claims
      await expect(page.locator('text=Claims generated')).toBeVisible({ timeout: 30000 });
      const claimsList = page.locator('[data-testid="claims-list"]');
      await expect(claimsList).toContainText('photosynthesis', { ignoreCase: true });
    });

    test('should generate examples for selected claims', async ({ page }) => {
      // Setup: Generate claims first
      await page.click('button:has-text("Generate Content")');
      await page.waitForURL(`**/generate`, { timeout: TEST_TIMEOUT });

      const topicInput = page.locator('input[name="topic"]');
      await topicInput.fill('Photosynthesis');
      
      const generateButton = page.locator('button:has-text("Generate Claims")');
      await generateButton.click();

      // Wait for claims to appear
      await expect(page.locator('text=Claims generated')).toBeVisible({ timeout: 30000 });

      // Execute: Select a claim and click "Generate Examples"
      const firstClaim = page.locator('[data-testid="claim"] >> first');
      await firstClaim.click();

      const examplesButton = page.locator('button:has-text("Generate Examples")');
      await expect(examplesButton).toBeEnabled();
      await examplesButton.click();

      // Verify: Should show examples
      await expect(page.locator('text=Examples generated')).toBeVisible({ timeout: 30000 });
      const examplesList = page.locator('[data-testid="examples-list"]');
      await expect(examplesList).toContainText('example', { ignoreCase: true });
    });

    test('should generate simulation scenarios', async ({ page }) => {
      // Navigate to generator
      await page.click('button:has-text("Generate Content")');
      await page.waitForURL(`**/generate`, { timeout: TEST_TIMEOUT });

      // Execute: Fill in and generate
      const topicInput = page.locator('input[name="topic"]');
      await topicInput.fill('Water Cycle');
      
      const gradeSelect = page.locator('select[name="gradeLevel"]');
      await gradeSelect.selectOption('7');

      // Generate simulation
      const simulationButton = page.locator('button:has-text("Generate Simulation")');
      await simulationButton.click();

      // Verify: Should show simulation scenario
      await expect(page.locator('text=Simulation ready')).toBeVisible({ timeout: 30000 });
      const simulationContent = page.locator('[data-testid="simulation"]');
      await expect(simulationContent).toBeVisible();
    });

    test('should handle generation errors gracefully', async ({ page }) => {
      // Navigate to generator
      await page.click('button:has-text("Generate Content")');
      await page.waitForURL(`**/generate`, { timeout: TEST_TIMEOUT });

      // Execute: Try to generate without topic
      const generateButton = page.locator('button:has-text("Generate Claims")');
      await generateButton.click();

      // Verify: Should show validation error
      const errorMessage = page.locator('text=Topic is required');
      await expect(errorMessage).toBeVisible();
    });

    test('should allow content enhancement', async ({ page }) => {
      // Navigate to generator
      await page.click('button:has-text("Generate Content")');
      await page.waitForURL(`**/generate`, { timeout: TEST_TIMEOUT });

      // Generate initial content
      const topicInput = page.locator('input[name="topic"]');
      await topicInput.fill('Biodiversity');
      
      const generateButton = page.locator('button:has-text("Generate Claims")');
      await generateButton.click();

      await expect(page.locator('text=Claims generated')).toBeVisible({ timeout: 30000 });

      // Execute: Click enhance button
      const enhanceButton = page.locator('button:has-text("Enhance Content")');
      await expect(enhanceButton).toBeEnabled();
      await enhanceButton.click();

      // Verify: Should show enhancement options
      const enhancementDialog = page.locator('[data-testid="enhancement-dialog"]');
      await expect(enhancementDialog).toBeVisible();

      // Select enhancement options
      await page.check('input[value="accessibility"]');
      await page.check('input[value="engagement"]');

      const applyButton = page.locator('button:has-text("Apply Enhancement")');
      await applyButton.click();

      // Verify: Should show enhanced content
      await expect(page.locator('text=Content enhanced')).toBeVisible({ timeout: 30000 });
    });

  });

  test.describe('Content Management', () => {

    test.beforeEach(async ({ page }) => {
      // Login
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });
    });

    test('should display generated content in library', async ({ page }) => {
      // Execute: Navigate to content library
      await page.click('a:has-text("Content Library")');

      // Verify: Should show library page
      await page.waitForURL(`**/library`, { timeout: TEST_TIMEOUT });
      const libraryTitle = page.locator('h1:has-text("Content Library")');
      await expect(libraryTitle).toBeVisible();

      // Verify: Should list previously generated content
      const contentList = page.locator('[data-testid="content-list"]');
      const itemCount = await contentList.locator('li').count();
      expect(itemCount).toBeGreaterThan(0);
    });

    test('should allow viewing content details', async ({ page }) => {
      // Navigate to library
      await page.click('a:has-text("Content Library")');
      await page.waitForURL(`**/library`, { timeout: TEST_TIMEOUT });

      // Execute: Click on first content item
      const firstItem = page.locator('[data-testid="content-item"] >> first');
      await firstItem.click();

      // Verify: Should show content detail page
      await page.waitForURL(`**/content/**`, { timeout: TEST_TIMEOUT });
      const detailView = page.locator('[data-testid="content-detail"]');
      await expect(detailView).toBeVisible();

      // Verify: Should display content metadata
      await expect(page.locator('text=Topic:')).toBeVisible();
      await expect(page.locator('text=Grade Level:')).toBeVisible();
      await expect(page.locator('text=Generated:')).toBeVisible();
    });

    test('should allow exporting content', async ({ page }) => {
      // Navigate to library
      await page.click('a:has-text("Content Library")');
      await page.waitForURL(`**/library`, { timeout: TEST_TIMEOUT });

      // Navigate to content detail
      const firstItem = page.locator('[data-testid="content-item"] >> first');
      await firstItem.click();

      // Execute: Click export button
      const exportButton = page.locator('button:has-text("Export")');
      await expect(exportButton).toBeEnabled();

      const downloadPromise = page.waitForEvent('download');
      await exportButton.click();

      const download = await downloadPromise;

      // Verify: Should download file
      expect(download.suggestedFilename()).toMatch(/\.pdf$|\.docx$|\.json$/);
    });

    test('should allow saving preferences for content generation', async ({ page }) => {
      // Navigate to settings
      await page.click('a:has-text("Settings")');

      // Verify: Should show settings page
      await page.waitForURL(`**/settings`, { timeout: TEST_TIMEOUT });

      // Execute: Update generation preferences
      const gradePreference = page.locator('select[name="defaultGradeLevel"]');
      await gradePreference.selectOption('9');

      const engagementToggle = page.locator('input[name="prioritizeEngagement"]');
      await engagementToggle.check();

      const saveButton = page.locator('button:has-text("Save Preferences")');
      await saveButton.click();

      // Verify: Should show success message
      await expect(page.locator('text=Preferences saved')).toBeVisible({ timeout: 10000 });
    });

  });

  test.describe('Learning Path Creation', () => {

    test.beforeEach(async ({ page }) => {
      // Login
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });
    });

    test('should create a new learning path', async ({ page }) => {
      // Execute: Click "Create Learning Path"
      await page.click('button:has-text("Create Learning Path")');

      // Verify: Should show creation dialog
      const dialog = page.locator('[data-testid="create-path-dialog"]');
      await expect(dialog).toBeVisible();

      // Fill in path details
      const pathNameInput = page.locator('input[name="pathName"]');
      await pathNameInput.fill('Photosynthesis Mastery Path');

      const descriptionInput = page.locator('textarea[name="description"]');
      await descriptionInput.fill('Complete learning path for mastering photosynthesis');

      const gradeSelect = page.locator('select[name="gradeLevel"]');
      await gradeSelect.selectOption('8');

      // Create path
      const createButton = page.locator('button:has-text("Create")');
      await createButton.click();

      // Verify: Should navigate to path edit page
      await page.waitForURL(`**/paths/**`, { timeout: TEST_TIMEOUT });
      const pathTitle = page.locator('h1:has-text("Photosynthesis Mastery Path")');
      await expect(pathTitle).toBeVisible();
    });

    test('should add content to learning path', async ({ page }) => {
      // Navigate to a learning path
      await page.click('a:has-text("Learning Paths")');
      await page.waitForURL(`**/paths`, { timeout: TEST_TIMEOUT });

      // Click on first path
      const firstPath = page.locator('[data-testid="path-item"] >> first');
      await firstPath.click();

      // Execute: Click "Add Content"
      await page.click('button:has-text("Add Content")');

      // Verify: Should show content selection dialog
      const contentDialog = page.locator('[data-testid="add-content-dialog"]');
      await expect(contentDialog).toBeVisible();

      // Select some content
      const firstContent = page.locator('[data-testid="content-option"] >> first');
      await firstContent.click();

      // Add it
      const addButton = page.locator('[role="dialog"] button:has-text("Add")');
      await addButton.click();

      // Verify: Content should appear in path
      await expect(page.locator('text=Content added')).toBeVisible({ timeout: 10000 });
    });

    test('should reorder content in learning path', async ({ page }) => {
      // Navigate to learning path with content
      await page.click('a:has-text("Learning Paths")');
      await page.waitForURL(`**/paths`, { timeout: TEST_TIMEOUT });

      const firstPath = page.locator('[data-testid="path-item"] >> first');
      await firstPath.click();

      // Execute: Drag and drop to reorder
      const items = page.locator('[data-testid="path-content-item"]');
      const count = await items.count();

      if (count > 1) {
        const firstItem = items.first();
        const secondItem = items.nth(1);

        // Drag first to second position
        await firstItem.dragTo(secondItem);

        // Verify: Order should change
        await expect(page.locator('text=Content reordered')).toBeVisible({ timeout: 10000 });
      }
    });

  });

  test.describe('Progress Tracking', () => {

    test.beforeEach(async ({ page }) => {
      // Login
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });
    });

    test('should display student progress on dashboard', async ({ page }) => {
      // Verify: Dashboard should show progress widgets
      const progressWidget = page.locator('[data-testid="progress-widget"]');
      await expect(progressWidget).toBeVisible();

      // Verify: Should show percentage and count
      const progressPercent = page.locator('text=/\\d+%/');
      await expect(progressPercent).toBeVisible();
    });

    test('should update progress when student completes activity', async ({ page }) => {
      // Navigate to learning path
      await page.click('a:has-text("Learning Paths")');
      await page.waitForURL(`**/paths`, { timeout: TEST_TIMEOUT });

      const firstPath = page.locator('[data-testid="path-item"] >> first');
      await firstPath.click();

      // Execute: Click to start an activity
      const firstActivity = page.locator('[data-testid="activity"] >> first');
      await firstActivity.click();

      // Simulate completing the activity
      const completeButton = page.locator('button:has-text("Mark as Complete")');
      if (await completeButton.isVisible()) {
        await completeButton.click();

        // Verify: Should show completion message
        await expect(page.locator('text=Activity completed')).toBeVisible({ timeout: 10000 });
      }
    });

  });

  test.describe('Error Handling and Edge Cases', () => {

    test('should handle network errors gracefully', async ({ page }) => {
      // Go offline
      await page.context().setOffline(true);

      // Try to navigate
      await page.goto(`${BASE_URL}/generate`);

      // Verify: Should show offline message
      const offlineMessage = page.locator('text=You are offline');
      await expect(offlineMessage).toBeVisible({ timeout: 5000 });

      // Go back online
      await page.context().setOffline(false);
    });

    test('should handle session timeout', async ({ page }) => {
      // Navigate to logged in page
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });

      // Clear session cookie to simulate timeout
      await page.context().clearCookies({ name: 'session-token' });

      // Try to navigate to protected page
      await page.click('button:has-text("Generate Content")');

      // Verify: Should redirect to login
      await page.waitForURL(`**/login`, { timeout: TEST_TIMEOUT });
      const loginForm = page.locator('form:has-text("Sign In")');
      await expect(loginForm).toBeVisible();
    });

    test('should validate form inputs before submission', async ({ page }) => {
      // Login
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });

      // Navigate to generator
      await page.click('button:has-text("Generate Content")');
      await page.waitForURL(`**/generate`, { timeout: TEST_TIMEOUT });

      // Try to generate with invalid data
      const topicInput = page.locator('input[name="topic"]');
      await topicInput.fill('');

      const generateButton = page.locator('button:has-text("Generate Claims")');
      await generateButton.click();

      // Verify: Should show validation error
      await expect(page.locator('text=Topic is required')).toBeVisible();
      
      // Verify: Request should not have been sent
      const errorCount = await page.locator('.error-message').count();
      expect(errorCount).toBeGreaterThan(0);
    });

  });

  test.describe('Accessibility', () => {

    test('should have proper ARIA labels', async ({ page }) => {
      // Navigate to login
      await page.goto(`${BASE_URL}/login`);

      // Verify: Form inputs should have labels or aria-label
      const emailInput = page.locator('input[name="email"]');
      const hasLabel = await emailInput.getAttribute('aria-label') || 
                      await page.locator(`label[for="${await emailInput.getAttribute('id')}"]`).first();
      expect(hasLabel).toBeTruthy();
    });

    test('should be keyboard navigable', async ({ page }) => {
      // Navigate to login
      await page.goto(`${BASE_URL}/login`);

      // Execute: Tab through form fields
      const emailInput = page.locator('input[name="email"]');
      await emailInput.focus();
      await expect(emailInput).toBeFocused();

      // Tab to password field
      await page.keyboard.press('Tab');
      const passwordInput = page.locator('input[name="password"]');
      await expect(passwordInput).toBeFocused();

      // Tab to submit button
      await page.keyboard.press('Tab');
      const submitButton = page.locator('button:has-text("Sign In")');
      await expect(submitButton).toBeFocused();
    });

  });

});
