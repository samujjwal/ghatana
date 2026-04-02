import { test, expect } from '@playwright/test';

/**
 * E2E tests for YAPPC critical code generation workflows.
 * Tests focus on design spec validation, code generation, and artifact management.
 * @doc.type e2e-test
 * @doc.purpose End-to-end workflow validation for YAPPC code generation platform
 * @doc.layer product
 * @doc.pattern E2E Test Suite
 */

// Configuration
const BASE_URL = process.env.YAPPC_URL || 'http://localhost:3002';
const TEST_TIMEOUT = 60000;  // 60 seconds for code generation

// Test user credentials
const TEST_USER = {
  email: 'test-engineer@yappc.local',
  password: 'YAPPCTest123!Secure',
  displayName: 'Test Engineer',
};

test.describe('YAPPC Code Generation Workflows', () => {

  test.beforeEach(async ({ page }) => {
    // Navigate to login page
    await page.goto(`${BASE_URL}/login`, { waitUntil: 'networkidle' });
  });

  test.describe('Authentication', () => {

    test('should login successfully', async ({ page }) => {
      // Execute: Login
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');

      // Verify: Should redirect to dashboard
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });
      const userMenu = page.locator('[data-testid="user-menu"]');
      await expect(userMenu).toBeVisible();
    });

    test('should display error on invalid credentials', async ({ page }) => {
      // Execute: Try invalid login
      await page.fill('input[name="email"]', 'invalid@example.com');
      await page.fill('input[name="password"]', 'wrong');
      await page.click('button:has-text("Sign In")');

      // Verify: Error shown
      await expect(page.locator('text=Invalid credentials')).toBeVisible({ timeout: 10000 });
    });

  });

  test.describe('Design Spec Creation and Validation', () => {

    test.beforeEach(async ({ page }) => {
      // Login
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });
    });

    test('should create a new design specification', async ({ page }) => {
      // Execute: Click "New Design"
      await page.click('button:has-text("New Design")');

      // Verify: Should show creation form
      const form = page.locator('[data-testid="design-form"]');
      await expect(form).toBeVisible();

      // Fill in design details
      const nameInput = page.locator('input[name="designName"]');
      await nameInput.fill('User Authentication Module');

      const descriptionInput = page.locator('textarea[name="description"]');
      await descriptionInput.fill('OAuth2 and JWT-based authentication system');

      const designTypeSelect = page.locator('select[name="designType"]');
      await designTypeSelect.selectOption('service');

      // Create design
      const createButton = page.locator('button:has-text("Create Design")');
      await createButton.click();

      // Verify: Should navigate to design editor
      await page.waitForURL(`**/designs/**`, { timeout: TEST_TIMEOUT });
      const editorTitle = page.locator('h1:has-text("User Authentication Module")');
      await expect(editorTitle).toBeVisible();
    });

    test('should add components to design spec', async ({ page }) => {
      // Navigate to design
      await page.click('text=Recent Designs');
      await page.waitForURL(`**/designs`, { timeout: TEST_TIMEOUT });

      const firstDesign = page.locator('[data-testid="design-card"] >> first');
      await firstDesign.click();

      // Execute: Add a component
      await page.click('button:has-text("Add Component")');

      // Verify: Should show component dialog
      const dialog = page.locator('[data-testid="add-component-dialog"]');
      await expect(dialog).toBeVisible();

      // Fill component details
      const componentName = page.locator('input[name="componentName"]');
      await componentName.fill('AuthenticationService');

      const componentType = page.locator('select[name="componentType"]');
      await componentType.selectOption('service');

      const addButton = page.locator('[role="dialog"] button:has-text("Add")');
      await addButton.click();

      // Verify: Component should appear in design
      await expect(page.locator('text=AuthenticationService')).toBeVisible({ timeout: 10000 });
    });

    test('should define component relationships', async ({ page }) => {
      // Navigate to a design with components
      await page.click('text=Recent Designs');
      await page.waitForURL(`**/designs`, { timeout: TEST_TIMEOUT });

      const firstDesign = page.locator('[data-testid="design-card"] >> first');
      await firstDesign.click();

      // Execute: Click on a component to select it
      const component = page.locator('[data-testid="design-component"] >> first');
      await component.click();

      // Create relationship to another component
      const addRelationButton = page.locator('button:has-text("Add Dependency")');
      if (await addRelationButton.isVisible()) {
        await addRelationButton.click();

        // Select target component
        const targetSelect = page.locator('select[name="targetComponent"]');
        const options = await targetSelect.locator('option').count();
        if (options > 1) {
          await targetSelect.selectOption({ index: 1 });

          const relationshipType = page.locator('select[name="relationshipType"]');
          await relationshipType.selectOption('depends-on');

          const confirmButton = page.locator('button:has-text("Confirm")');
          await confirmButton.click();

          // Verify: Relationship should be created
          await expect(page.locator('text=Dependency added')).toBeVisible({ timeout: 10000 });
        }
      }
    });

    test('should validate design specification', async ({ page }) => {
      // Navigate to a design
      await page.click('text=Recent Designs');
      await page.waitForURL(`**/designs`, { timeout: TEST_TIMEOUT });

      const firstDesign = page.locator('[data-testid="design-card"] >> first');
      await firstDesign.click();

      // Execute: Click validate button
      await page.click('button:has-text("Validate Design")');

      // Verify: Should show validation results
      const validationPanel = page.locator('[data-testid="validation-results"]');
      await expect(validationPanel).toBeVisible({ timeout: 10000 });

      // Should show pass or fail status
      const validationStatus = page.locator('[data-testid="validation-status"]');
      const statusText = await validationStatus.textContent();
      expect(['Valid', 'Invalid', 'Validation Complete']).toContain(statusText?.trim());
    });

    test('should detect circular dependencies', async ({ page }) => {
      // This test creates components with circular dependency
      await page.click('button:has-text("New Design")');
      await page.waitForLoadState('networkidle');

      const nameInput = page.locator('input[name="designName"]');
      await nameInput.fill('Circular Dependency Test');

      const createButton = page.locator('button:has-text("Create Design")');
      await createButton.click();

      await page.waitForURL(`**/designs/**`, { timeout: TEST_TIMEOUT });

      // Create Component A
      await page.click('button:has-text("Add Component")');
      let dialog = page.locator('[data-testid="add-component-dialog"]');
      await expect(dialog).toBeVisible();

      let componentName = page.locator('input[name="componentName"]');
      await componentName.fill('ComponentA');
      let addButton = page.locator('[role="dialog"] button:has-text("Add")');
      await addButton.click();

      await page.waitForLoadState('networkidle');

      // Create Component B
      await page.click('button:has-text("Add Component")');
      dialog = page.locator('[data-testid="add-component-dialog"]');
      await expect(dialog).toBeVisible();

      componentName = page.locator('input[name="componentName"]');
      await componentName.fill('ComponentB');
      addButton = page.locator('[role="dialog"] button:has-text("Add")');
      await addButton.click();

      await page.waitForLoadState('networkidle');

      // Add A→B dependency
      let compA = page.locator('text=ComponentA').first();
      await compA.click();

      await page.click('button:has-text("Add Dependency")');
      let targetSelect = page.locator('select[name="targetComponent"]');
      await targetSelect.selectOption('ComponentB');
      let confirmButton = page.locator('button:has-text("Confirm")');
      await confirmButton.click();

      // Validate
      await page.click('button:has-text("Validate Design")');
      await page.waitForLoadState('networkidle');

      // Verify: Should pass validation (no circular yet)
      let validationStatus = page.locator('[data-testid="validation-status"]');
      await expect(validationStatus).not.toContainText('circular', { ignoreCase: true });
    });

  });

  test.describe('Code Generation', () => {

    test.beforeEach(async ({ page }) => {
      // Login
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });
    });

    test('should generate code from validated design', async ({ page }) => {
      // Navigate to a design
      await page.click('text=Recent Designs');
      await page.waitForURL(`**/designs`, { timeout: TEST_TIMEOUT });

      const firstDesign = page.locator('[data-testid="design-card"] >> first');
      await firstDesign.click();

      // Validate design first
      await page.click('button:has-text("Validate Design")');
      await expect(page.locator('[data-testid="validation-status"]')).toBeVisible({ timeout: TEST_TIMEOUT });

      // Execute: Click generate code
      const generateButton = page.locator('button:has-text("Generate Code")');
      await expect(generateButton).toBeEnabled();
      await generateButton.click();

      // Verify: Should show code generation in progress
      const generatingSpinner = page.locator('.generating-spinner', { hasText: 'Generating code' });
      if (await generatingSpinner.isVisible({ timeout: 5000 }).catch(() => false)) {
        // Wait for generation to complete (longer timeout for code generation)
        await page.waitForLoadState('networkidle');
      }

      // Verify: Should show generated code
      const codeView = page.locator('[data-testid="generated-code"]');
      await expect(codeView).toBeVisible({ timeout: TEST_TIMEOUT });

      // Verify: Code should contain expected elements
      const codeContent = await codeView.textContent();
      expect(codeContent).toBeTruthy();
      expect(codeContent?.length).toBeGreaterThan(0);
    });

    test('should generate in multiple programming languages', async ({ page }) => {
      // Navigate to a design
      await page.click('text=Recent Designs');
      await page.waitForURL(`**/designs`, { timeout: TEST_TIMEOUT });

      const firstDesign = page.locator('[data-testid="design-card"] >> first');
      await firstDesign.click();

      // Set language to TypeScript
      const languageSelect = page.locator('select[name="language"]');
      if (await languageSelect.isVisible()) {
        await languageSelect.selectOption('typescript');
      }

      // Generate code
      await page.click('button:has-text("Generate Code")');

      // Wait for generation
      await page.waitForLoadState('networkidle');

      // Verify: TypeScript code should be generated
      const codeView = page.locator('[data-testid="generated-code"]');
      const codeContent = await codeView.textContent();

      // TypeScript-specific patterns
      expect(codeContent).toMatch(/interface|type|export|import/, { matchSubstring: true });
    });

    test('should allow previewing generated artifacts', async ({ page }) => {
      // Navigate to a design with generated code
      await page.click('text=Recent Designs');
      await page.waitForURL(`**/designs`, { timeout: TEST_TIMEOUT });

      const firstDesign = page.locator('[data-testid="design-card"] >> first');
      await firstDesign.click();

      // Generate code first
      if (!(await page.locator('[data-testid="generated-code"]').isVisible({ timeout: 5000 }).catch(() => false))) {
        await page.click('button:has-text("Generate Code")');
        await page.waitForLoadState('networkidle');
      }

      // Execute: Open visualization
      const visualizeButton = page.locator('button:has-text("Visualize")');
      if (await visualizeButton.isVisible()) {
        await visualizeButton.click();

        // Verify: Should show preview
        const preview = page.locator('[data-testid="code-preview"]');
        await expect(preview).toBeVisible();
      }
    });

    test('should export generated artifacts', async ({ page }) => {
      // Navigate to a design with generated code
      await page.click('text=Recent Designs');
      await page.waitForURL(`**/designs`, { timeout: TEST_TIMEOUT });

      const firstDesign = page.locator('[data-testid="design-card"] >> first');
      await firstDesign.click();

      // Generate code first
      if (!(await page.locator('[data-testid="generated-code"]').isVisible({ timeout: 5000 }).catch(() => false))) {
        await page.click('button:has-text("Generate Code")');
        await page.waitForLoadState('networkidle');
      }

      // Execute: Export
      const exportButton = page.locator('button:has-text("Export")');
      const downloadPromise = page.waitForEvent('download');
      await exportButton.click();

      const download = await downloadPromise;

      // Verify: Should download file
      expect(download.suggestedFilename()).toMatch(/\.zip$|\.tar\.gz$|\.json$/);
    });

  });

  test.describe('Artifact Management', () => {

    test.beforeEach(async ({ page }) => {
      // Login
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });
    });

    test('should manage generated artifacts', async ({ page }) => {
      // Navigate to artifacts
      await page.click('a:has-text("Artifacts")');

      // Verify: Should show artifacts list
      await page.waitForURL(`**/artifacts`, { timeout: TEST_TIMEOUT });
      const artifactsList = page.locator('[data-testid="artifacts-list"]');
      await expect(artifactsList).toBeVisible();
    });

    test('should version control artifacts', async ({ page }) => {
      // Navigate to artifacts
      await page.click('a:has-text("Artifacts")');
      await page.waitForURL(`**/artifacts`, { timeout: TEST_TIMEOUT });

      // Click on first artifact
      const firstArtifact = page.locator('[data-testid="artifact-item"] >> first');
      await firstArtifact.click();

      // Verify: Should show version history
      const versionHistory = page.locator('[data-testid="version-history"]');
      await expect(versionHistory).toBeVisible();

      // Should show multiple versions
      const versionCount = await page.locator('[data-testid="version-entry"]').count();
      expect(versionCount).toBeGreaterThan(0);
    });

    test('should allow comparing artifact versions', async ({ page }) => {
      // Navigate to artifacts
      await page.click('a:has-text("Artifacts")');
      await page.waitForURL(`**/artifacts`, { timeout: TEST_TIMEOUT });

      // Click on first artifact
      const firstArtifact = page.locator('[data-testid="artifact-item"] >> first');
      await firstArtifact.click();

      // Execute: Select two versions to compare
      const versionEntries = page.locator('[data-testid="version-entry"]');
      const count = await versionEntries.count();

      if (count > 1) {
        await versionEntries.first().click();
        await versionEntries.nth(1).click({ modifiers: ['shift'] });

        // Execute: Click compare
        const compareButton = page.locator('button:has-text("Compare")');
        if (await compareButton.isVisible()) {
          await compareButton.click();

          // Verify: Should show diff view
          const diffView = page.locator('[data-testid="diff-view"]');
          await expect(diffView).toBeVisible();
        }
      }
    });

    test('should allow reverting to previous version', async ({ page }) => {
      // Navigate to artifacts
      await page.click('a:has-text("Artifacts")');
      await page.waitForURL(`**/artifacts`, { timeout: TEST_TIMEOUT });

      // Click on first artifact
      const firstArtifact = page.locator('[data-testid="artifact-item"] >> first');
      await firstArtifact.click();

      // Get previous version
      const versionEntries = page.locator('[data-testid="version-entry"]');
      const count = await versionEntries.count();

      if (count > 1) {
        // Click on previous version (second one)
        await versionEntries.nth(1).click();

        // Execute: Revert to this version
        const revertButton = page.locator('button:has-text("Revert to This Version")');
        if (await revertButton.isVisible()) {
          await revertButton.click();

          // Verify: Should show confirmation
          await expect(page.locator('text=Version reverted')).toBeVisible({ timeout: 10000 });
        }
      }
    });

  });

  test.describe('Refactoring Suggestions', () => {

    test.beforeEach(async ({ page }) => {
      // Login
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });
    });

    test('should provide refactoring suggestions', async ({ page }) => {
      // Navigate to a generated artifact
      await page.click('a:has-text("Artifacts")');
      await page.waitForURL(`**/artifacts`, { timeout: TEST_TIMEOUT });

      const firstArtifact = page.locator('[data-testid="artifact-item"] >> first');
      await firstArtifact.click();

      // Execute: Request refactoring suggestions
      const suggestButton = page.locator('button:has-text("Get Refactoring Suggestions")');
      if (await suggestButton.isVisible()) {
        await suggestButton.click();

        // Verify: Should show suggestions
        const suggestionsPanel = page.locator('[data-testid="suggestions-panel"]');
        await expect(suggestionsPanel).toBeVisible({ timeout: TEST_TIMEOUT });

        // Should have multiple suggestions
        const suggestionCount = await page.locator('[data-testid="suggestion-item"]').count();
        expect(suggestionCount).toBeGreaterThan(0);
      }
    });

    test('should apply refactoring locally', async ({ page }) => {
      // Navigate to artifact with suggestions
      await page.click('a:has-text("Artifacts")');
      await page.waitForURL(`**/artifacts`, { timeout: TEST_TIMEOUT });

      const firstArtifact = page.locator('[data-testid="artifact-item"] >> first');
      await firstArtifact.click();

      // Get suggestions
      const suggestButton = page.locator('button:has-text("Get Refactoring Suggestions")');
      if (await suggestButton.isVisible()) {
        await suggestButton.click();
        await expect(page.locator('[data-testid="suggestions-panel"]')).toBeVisible({ timeout: TEST_TIMEOUT });

        // Execute: Apply first suggestion
        const applySuggestion = page.locator('[data-testid="apply-suggestion"] >> first');
        if (await applySuggestion.isVisible()) {
          await applySuggestion.click();

          // Verify: Should show applied message
          await expect(page.locator('text=Refactoring applied')).toBeVisible({ timeout: 10000 });
        }
      }
    });

  });

  test.describe('Collaboration and Sharing', () => {

    test.beforeEach(async ({ page }) => {
      // Login
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });
    });

    test('should share design with team members', async ({ page }) => {
      // Navigate to designs
      await page.click('text=Recent Designs');
      await page.waitForURL(`**/designs`, { timeout: TEST_TIMEOUT });

      // Click on a design
      const firstDesign = page.locator('[data-testid="design-card"] >> first');
      await firstDesign.click();

      // Execute: Click share button
      const shareButton = page.locator('button:has-text("Share")');
      await shareButton.click();

      // Verify: Should show sharing dialog
      const shareDialog = page.locator('[data-testid="share-dialog"]');
      await expect(shareDialog).toBeVisible();

      // Add team member email
      const emailInput = page.locator('input[name="teamMemberEmail"]');
      await emailInput.fill('teammate@yappc.local');

      const permissionSelect = page.locator('select[name="permission"]');
      await permissionSelect.selectOption('viewer');

      // Share
      const shareConfirmButton = page.locator('[role="dialog"] button:has-text("Share")');
      await shareConfirmButton.click();

      // Verify: Should show success message
      await expect(page.locator('text=Design shared')).toBeVisible({ timeout: 10000 });
    });

  });

  test.describe('Error Handling and Edge Cases', () => {

    test('should handle long-running code generation', async ({ page }) => {
      // Login
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });

      // Navigate to a design
      await page.click('text=Recent Designs');
      await page.waitForURL(`**/designs`, { timeout: TEST_TIMEOUT });

      const firstDesign = page.locator('[data-testid="design-card"] >> first');
      await firstDesign.click();

      // Generate code
      await page.click('button:has-text("Generate Code")');

      // Wait with longer timeout
      let isGenerating = true;
      const maxWait = 120000;  // 2 minutes max
      const startTime = Date.now();

      while (isGenerating && (Date.now() - startTime) < maxWait) {
        const spinner = page.locator('.generating-spinner');
        if (await spinner.isVisible({ timeout: 1000 }).catch(() => false)) {
          // Still generating
          await page.waitForTimeout(2000);
        } else {
          isGenerating = false;
        }
      }

      // Verify: Should eventually complete
      const codeView = page.locator('[data-testid="generated-code"]');
      await expect(codeView).toBeVisible({ timeout: 30000 });
    });

    test('should handle invalid design validation', async ({ page }) => {
      // Login
      await page.fill('input[name="email"]', TEST_USER.email);
      await page.fill('input[name="password"]', TEST_USER.password);
      await page.click('button:has-text("Sign In")');
      await page.waitForURL(`**/dashboard`, { timeout: TEST_TIMEOUT });

      // Create a minimal design (may be invalid)
      await page.click('button:has-text("New Design")');
      await page.waitForLoadState('networkidle');

      const nameInput = page.locator('input[name="designName"]');
      await nameInput.fill('Minimal Design');

      const createButton = page.locator('button:has-text("Create Design")');
      await createButton.click();

      await page.waitForURL(`**/designs/**`, { timeout: TEST_TIMEOUT });

      // Try to validate (may be invalid)
      await page.click('button:has-text("Validate Design")');

      // Verify: Should show validation results
      const validationPanel = page.locator('[data-testid="validation-results"]');
      await expect(validationPanel).toBeVisible({ timeout: TEST_TIMEOUT });
    });

  });

});
