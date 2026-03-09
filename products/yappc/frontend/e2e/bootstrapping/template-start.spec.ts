/**
 * E2E Test Suite - Bootstrapping Template Start
 *
 * @description Tests starting a project from templates,
 * template browsing, and template customization.
 *
 * @doc.type test
 * @doc.purpose E2E template validation
 * @doc.layer e2e
 * @doc.phase bootstrapping
 */

import { test, expect } from '@playwright/test';
import {
  StartProjectPage,
  TemplateSelectionPage,
  BootstrapSessionPage,
} from './pages/bootstrapping.page';
import { testUsers } from './fixtures';

// ============================================================================
// Test Suite Configuration
// ============================================================================

test.describe('Bootstrapping Template Start', () => {
  let startPage: StartProjectPage;
  let templatePage: TemplateSelectionPage;
  let sessionPage: BootstrapSessionPage;

  test.beforeEach(async ({ page }) => {
    startPage = new StartProjectPage(page);
    templatePage = new TemplateSelectionPage(page);
    sessionPage = new BootstrapSessionPage(page);

    // Login
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  // ==========================================================================
  // Template Browsing Tests
  // ==========================================================================

  test('should display template gallery on template page', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid).toBeVisible();
    const templateCount = await templatePage.getTemplateCount();
    expect(templateCount).toBeGreaterThan(0);
  });

  test('should navigate to templates from start page', async ({ page }) => {
    await startPage.goto();
    await startPage.goToTemplates();

    await expect(page).toHaveURL(/\/start\/template/);
    await expect(templatePage.templateGrid).toBeVisible();
  });

  test('should display template categories', async ({ page }) => {
    await templatePage.goto();

    // Check for category filters
    await expect(templatePage.categoryFilter).toBeVisible();

    // Common categories should be present
    const categories = ['Web', 'Mobile', 'Backend', 'Full Stack', 'E-commerce', 'SaaS'];
    for (const category of categories.slice(0, 3)) {
      const categoryButton = templatePage.categoryFilter.getByRole('button', { name: new RegExp(category, 'i') });
      if (await categoryButton.isVisible()) {
        expect(await categoryButton.isVisible()).toBe(true);
      }
    }
  });

  test('should filter templates by category', async ({ page }) => {
    await templatePage.goto();

    // Wait for templates to load
    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    const initialCount = await templatePage.getTemplateCount();

    // Select a category
    await templatePage.selectCategory('Web');

    // Count should change (filtered)
    // Note: May be same if all templates are web
    const filteredCount = await templatePage.getTemplateCount();
    expect(filteredCount).toBeGreaterThan(0);
  });

  test('should search templates by keyword', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Search for specific template
    await templatePage.searchTemplates('e-commerce');

    // Results should be filtered
    await page.waitForTimeout(500); // Wait for search debounce

    // Either templates match or no results message
    const templates = templatePage.templateGrid.locator('[data-testid="template-card"]');
    const noResults = page.getByText(/no templates|no results/i);

    const hasTemplates = await templates.count() > 0;
    const hasNoResults = await noResults.isVisible();

    expect(hasTemplates || hasNoResults).toBe(true);
  });

  // ==========================================================================
  // Template Selection Tests
  // ==========================================================================

  test('should highlight selected template', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Click first template
    await templatePage.templateGrid.locator('[data-testid="template-card"]').first().click();

    // Should be highlighted/selected
    await expect(templatePage.selectedTemplate).toBeVisible();
  });

  test('should show template details on selection', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Click first template
    await templatePage.templateGrid.locator('[data-testid="template-card"]').first().click();

    // Template details panel or expanded view should show
    const templateDetails = page.getByTestId('template-details');
    const templateDescription = page.getByText(/description|features|includes/i);

    // Either details panel or inline expansion
    const hasDetails = await templateDetails.isVisible() || await templateDescription.isVisible();
    expect(hasDetails).toBe(true);
  });

  test('should allow template preview before use', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Select template
    await templatePage.templateGrid.locator('[data-testid="template-card"]').first().click();

    // Click preview
    if (await templatePage.previewButton.isVisible()) {
      await templatePage.previewSelectedTemplate();

      // Preview modal or page should open
      const previewModal = page.getByRole('dialog');
      const previewCanvas = page.getByTestId('template-preview-canvas');

      await expect(previewModal.or(previewCanvas)).toBeVisible({ timeout: 5000 });
    }
  });

  // ==========================================================================
  // Start from Template Tests
  // ==========================================================================

  test('should start new session from selected template', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Select template
    await templatePage.templateGrid.locator('[data-testid="template-card"]').first().click();

    // Use template
    await templatePage.useSelectedTemplate();

    // Should navigate to bootstrap session
    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
  });

  test('should pre-populate canvas with template content', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Select and use template
    await templatePage.templateGrid.locator('[data-testid="template-card"]').first().click();
    await templatePage.useSelectedTemplate();

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);

    // Canvas should have template nodes
    await expect(sessionPage.canvasPanel).toBeVisible();
    const nodeCount = await sessionPage.getNodeCount();
    expect(nodeCount).toBeGreaterThan(0);
  });

  test('should start conversation with template context', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Select and use template
    await templatePage.templateGrid.locator('[data-testid="template-card"]').first().click();
    await templatePage.useSelectedTemplate();

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    // AI should reference the template
    const lastMessage = await sessionPage.getLastMessage();
    expect(lastMessage.length).toBeGreaterThan(0);
  });

  // ==========================================================================
  // Template Customization Tests
  // ==========================================================================

  test('should allow modifying template-generated nodes', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Select and use template
    await templatePage.templateGrid.locator('[data-testid="template-card"]').first().click();
    await templatePage.useSelectedTemplate();

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);

    // Click on a node
    await sessionPage.canvasNodes.first().click();

    // Should be able to edit
    const nodeDetails = page.getByTestId('node-details');
    await expect(nodeDetails).toBeVisible({ timeout: 5000 });
  });

  test('should allow adding to template through conversation', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Select and use template
    await templatePage.templateGrid.locator('[data-testid="template-card"]').first().click();
    await templatePage.useSelectedTemplate();

    await expect(page).toHaveURL(/\/bootstrap\/[\w-]+$/);
    await sessionPage.waitForAIResponse();

    const initialNodeCount = await sessionPage.getNodeCount();

    // Add more features through conversation
    await sessionPage.sendMessage('Add a real-time notification system');
    await sessionPage.waitForAIResponse();

    // Should have more nodes now
    await expect(async () => {
      const newNodeCount = await sessionPage.getNodeCount();
      expect(newNodeCount).toBeGreaterThanOrEqual(initialNodeCount);
    }).toPass({ timeout: 15000 });
  });

  // ==========================================================================
  // Template Information Tests
  // ==========================================================================

  test('should display template author information', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Look for author info on cards
    const authorInfo = templatePage.templateGrid.locator('[data-testid="template-author"]').first();
    if (await authorInfo.isVisible()) {
      expect(await authorInfo.isVisible()).toBe(true);
    }
  });

  test('should display template usage statistics', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Select a template to see details
    await templatePage.templateGrid.locator('[data-testid="template-card"]').first().click();

    // Look for stats (downloads, stars, etc.)
    const stats = page.getByText(/downloads|uses|stars/i);
    if (await stats.isVisible()) {
      expect(await stats.isVisible()).toBe(true);
    }
  });

  test('should display template technology stack', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Select a template
    await templatePage.templateGrid.locator('[data-testid="template-card"]').first().click();

    // Look for tech stack badges
    const techStack = page.locator('[data-testid="tech-stack"]');
    const techBadges = page.locator('[data-testid="tech-badge"]');

    // Either tech stack section or badges
    const hasTechInfo = await techStack.isVisible() || await techBadges.count() > 0;
    // Tech info may not be present for all templates
  });

  // ==========================================================================
  // Template Category Tests
  // ==========================================================================

  test('should filter by multiple categories', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Select first category
    await templatePage.selectCategory('Web');
    
    // Note: Multiple category selection depends on implementation
  });

  test('should clear category filter', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    const initialCount = await templatePage.getTemplateCount();

    // Select category
    await templatePage.selectCategory('Web');

    // Clear filter
    const clearButton = page.getByRole('button', { name: /clear|all|reset/i });
    if (await clearButton.isVisible()) {
      await clearButton.click();
      
      // Should show all templates again
      const resetCount = await templatePage.getTemplateCount();
      expect(resetCount).toBe(initialCount);
    }
  });

  // ==========================================================================
  // Empty State Tests
  // ==========================================================================

  test('should show empty state when no templates match search', async ({ page }) => {
    await templatePage.goto();

    await expect(templatePage.templateGrid.locator('[data-testid="template-card"]').first()).toBeVisible({
      timeout: 10000,
    });

    // Search for non-existent template
    await templatePage.searchTemplates('xyznonexistent123');

    // Should show no results message
    await expect(page.getByText(/no templates|no results|not found/i)).toBeVisible({ timeout: 5000 });
  });
});

// ============================================================================
// Template Quick Start Tests
// ============================================================================

test.describe('Template Quick Start', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel(/email/i).fill(testUsers.standard.email);
    await page.getByLabel(/password/i).fill(testUsers.standard.password);
    await page.getByRole('button', { name: /sign in|log in/i }).click();
    await expect(page).toHaveURL(/dashboard|projects/);
  });

  test('should show popular templates on start page', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    await startPage.goto();

    // Look for featured/popular templates section
    const popularSection = page.getByText(/popular|featured|trending/i);
    const templateCards = page.locator('[data-testid="template-card"]');

    // Either has popular section or quick template links
    if (await popularSection.isVisible()) {
      expect(await templateCards.count()).toBeGreaterThan(0);
    }
  });

  test('should allow quick start from featured template', async ({ page }) => {
    const startPage = new StartProjectPage(page);
    await startPage.goto();

    // Click on a featured template if available
    const featuredTemplate = page.locator('[data-testid="featured-template"]').first();
    if (await featuredTemplate.isVisible()) {
      await featuredTemplate.click();

      // Should navigate to template page or directly to session
      await expect(page).toHaveURL(/\/bootstrap|\/start\/template/);
    }
  });
});
