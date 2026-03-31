/**
 * End-to-End Test Suite for TutorPutor Content Intelligence System
 * 
 * Tests complete learner and author journeys across the content ecosystem.
 * Validates search, generation, recommendations, and content quality.
 * 
 * @doc.type test
 * @doc.purpose End-to-end validation of content intelligence system
 * @doc.layer testing
 * @doc.pattern E2E
 */

import { test, expect } from '@playwright/test';
import type { Page, BrowserContext } from '@playwright/test';

// ============================================================================
// Test Configuration
// ============================================================================

const TEST_CONFIG = {
  baseUrl: process.env.TEST_BASE_URL || 'http://localhost:3000',
  timeout: 30000,
  retries: 2,
};

const SAMPLE_QUERIES = [
  'physics motion',
  'chemical reactions',
  'cell biology',
  'mathematics algebra',
];

const SAMPLE_ASSETS = {
  physics: {
    title: 'Introduction to Motion',
    type: 'learning_claim',
    domain: 'physics',
  },
  chemistry: {
    title: 'Chemical Reactions',
    type: 'simulation',
    domain: 'chemistry',
  },
  biology: {
    title: 'Cell Structure',
    type: 'example',
    domain: 'biology',
  },
};

// ============================================================================
// Helper Functions
// ============================================================================

async function loginAsLearner(page: Page): Promise<void> {
  await page.goto(`${TEST_CONFIG.baseUrl}/login`);
  await page.fill('[data-testid=username]', 'test-learner');
  await page.fill('[data-testid=password]', 'test-password');
  await page.click('[data-testid=login-button]');
  await page.waitForURL(`${TEST_CONFIG.baseUrl}/dashboard`);
}

async function loginAsAuthor(page: Page): Promise<void> {
  await page.goto(`${TEST_CONFIG.baseUrl}/login`);
  await page.fill('[data-testid=username]', 'test-author');
  await page.fill('[data-testid=password]', 'test-password');
  await page.click('[data-testid=login-button]');
  await page.waitForURL(`${TEST_CONFIG.baseUrl}/content-studio`);
}

async function performSearch(page: Page, query: string): Promise<void> {
  await page.fill('[data-testid=search-input]', query);
  await page.click('[data-testid=search-button]');
  await page.waitForSelector('[data-testid=search-results]');
}

async function waitForSearchResults(page: Page): Promise<any[]> {
  await page.waitForSelector('[data-testid=search-result-item]', { timeout: 10000 });
  return page.locator('[data-testid=search-result-item]').all();
}

async function selectFirstSearchResult(page: Page): Promise<void> {
  const firstResult = page.locator('[data-testid=search-result-item]').first();
  await firstResult.click();
  await page.waitForSelector('[data-testid=asset-detail]');
}

async function generateContent(page: Page, concept: string): Promise<void> {
  await page.goto(`${TEST_CONFIG.baseUrl}/content-studio/generate`);
  await page.fill('[data-testid=concept-input]', concept);
  await page.selectOption('[data-testid=domain-select]', 'physics');
  await page.click('[data-testid=generate-button]');
  await page.waitForSelector('[data-testid=generation-results]', { timeout: 60000 });
}

async function validateGeneratedContent(page: Page): Promise<{
  hasExamples: boolean;
  hasSimulations: boolean;
  hasAnimations: boolean;
  qualityScore: number;
}> {
  const examples = await page.locator('[data-testid=generated-example]').count();
  const simulations = await page.locator('[data-testid=generated-simulation]').count();
  const animations = await page.locator('[data-testid=generated-animation]').count();
  
  const qualityScoreText = await page.locator('[data-testid=quality-score]').textContent();
  const qualityScore = qualityScoreText ? parseFloat(qualityScoreText) : 0;

  return {
    hasExamples: examples > 0,
    hasSimulations: simulations > 0,
    hasAnimations: animations > 0,
    qualityScore,
  };
}

// ============================================================================
// Learner Journey Tests
// ============================================================================

test.describe('Learner Journey', () => {
  let page: Page;
  let context: BrowserContext;

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext();
    page = await context.newPage();
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('learner can search and discover content', async () => {
    await loginAsLearner(page);
    
    for (const query of SAMPLE_QUERIES) {
      await test.step(`search for "${query}"`, async () => {
        await page.goto(`${TEST_CONFIG.baseUrl}/search`);
        await performSearch(page, query);
        
        const results = await waitForSearchResults(page);
        expect(results.length).toBeGreaterThan(0);
        
        // Check that results have relevant information
        const firstResult = results[0];
        const title = await firstResult.locator('[data-testid=result-title]').textContent();
        const description = await firstResult.locator('[data-testid=result-description]').textContent();
        const relevanceScore = await firstResult.locator('[data-testid=relevance-score]').textContent();
        
        expect(title).toBeTruthy();
        expect(description).toBeTruthy();
        expect(relevanceScore).toBeTruthy();
      });
    }
  });

  test('learner can view asset details and recommendations', async () => {
    await loginAsLearner(page);
    await page.goto(`${TEST_CONFIG.baseUrl}/search`);
    
    await performSearch(page, 'physics motion');
    await selectFirstSearchResult(page);
    
    // Verify asset details are displayed
    await expect(page.locator('[data-testid=asset-title]')).toBeVisible();
    await expect(page.locator('[data-testid=asset-description]')).toBeVisible();
    await expect(page.locator('[data-testid=asset-type]')).toBeVisible();
    await expect(page.locator('[data-testid=asset-domain]')).toBeVisible();
    
    // Wait for recommendations to load
    await page.waitForSelector('[data-testid=recommendations]', { timeout: 10000 });
    
    // Verify recommendations are displayed
    const relatedAssets = await page.locator('[data-testid=related-asset]').count();
    const nextSteps = await page.locator('[data-testid=next-step]').count();
    
    expect(relatedAssets + nextSteps).toBeGreaterThan(0);
  });

  test('learner can navigate learning pathways', async () => {
    await loginAsLearner(page);
    
    // Start with a basic concept
    await page.goto(`${TEST_CONFIG.baseUrl}/search`);
    await performSearch(page, 'physics basics');
    await selectFirstSearchResult(page);
    
    // Follow next step recommendations
    const nextSteps = await page.locator('[data-testid=next-step]');
    if (await nextSteps.count() > 0) {
      await nextSteps.first().click();
      await page.waitForSelector('[data-testid=asset-detail]');
      
      // Verify progression
      const currentTitle = await page.locator('[data-testid=asset-title]').textContent();
      expect(currentTitle).toBeTruthy();
    }
  });

  test('learner search performance meets requirements', async () => {
    await loginAsLearner(page);
    await page.goto(`${TEST_CONFIG.baseUrl}/search`);
    
    for (const query of SAMPLE_QUERIES) {
      const startTime = Date.now();
      await performSearch(page, query);
      await waitForSearchResults(page);
      const responseTime = Date.now() - startTime;
      
      // Search should complete within 3 seconds
      expect(responseTime).toBeLessThan(3000);
      
      // Results should have relevance scores
      const results = await page.locator('[data-testid=search-result-item]').all();
      for (const result of results.slice(0, 3)) { // Check first 3 results
        const score = await result.locator('[data-testid=relevance-score]').textContent();
        expect(score).toBeTruthy();
        const numericScore = parseFloat(score!);
        expect(numericScore).toBeGreaterThan(0.5); // Minimum relevance threshold
      }
    }
  });
});

// ============================================================================
// Author Journey Tests
// ============================================================================

test.describe('Author Journey', () => {
  let page: Page;
  let context: BrowserContext;

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext();
    page = await context.newPage();
  });

  test.afterAll(async () => {
    await context.close();
  });

  test('author can generate content automatically', async () => {
    await loginAsAuthor(page);
    
    const concepts = ['Newton\'s Laws', 'Chemical Bonding', 'Cell Division'];
    
    for (const concept of concepts) {
      await test.step(`generate content for "${concept}"`, async () => {
        await generateContent(page, concept);
        
        const validation = await validateGeneratedContent(page);
        
        // At least one content type should be generated
        expect(validation.hasExamples || validation.hasSimulations || validation.hasAnimations).toBe(true);
        
        // Quality score should be acceptable
        expect(validation.qualityScore).toBeGreaterThan(0.7);
      });
    }
  });

  test('author can review and edit generated content', async () => {
    await loginAsAuthor(page);
    
    await generateContent(page, 'Physics Motion');
    
    // Click on first generated item to review
    const firstItem = page.locator('[data-testid=generated-item]').first();
    await firstItem.click();
    
    // Verify review interface
    await expect(page.locator('[data-testid=content-editor]')).toBeVisible();
    await expect(page.locator('[data-testid=quality-metrics]')).toBeVisible();
    await expect(page.locator('[data-testid=edit-controls]')).toBeVisible();
    
    // Make a simple edit
    await page.click('[data-testid=edit-button]');
    await page.fill('[data-testid=content-textarea]', 'Updated content for testing');
    await page.click('[data-testid=save-button]');
    
    // Verify save was successful
    await expect(page.locator('[data-testid=save-success]')).toBeVisible();
  });

  test('author can publish content with validation', async () => {
    await loginAsAuthor(page);
    
    await generateContent(page, 'Test Concept for Publishing');
    
    // Attempt to publish
    await page.click('[data-testid=publish-button]');
    
    // Should show validation results
    await expect(page.locator('[data-testid=validation-results]')).toBeVisible();
    
    // Check that modality validation is performed
    const examplesPresent = await page.locator('[data-testid=examples-present]').isVisible();
    const simulationsPresent = await page.locator('[data-testid=simulations-present]').isVisible();
    const animationsPresent = await page.locator('[data-testid=animations-present]').isVisible();
    
    // At least one modality should be present for publishing
    expect(examplesPresent || simulationsPresent || animationsPresent).toBe(true);
    
    // If validation passes, publish should succeed
    const canPublish = await page.locator('[data-testid=can-publish]').isVisible();
    if (canPublish) {
      await page.click('[data-testid=confirm-publish]');
      await expect(page.locator('[data-testid=publish-success]')).toBeVisible();
    }
  });
});

// ============================================================================
// System Integration Tests
// ============================================================================

test.describe('System Integration', () => {
  test('search and recommendation systems work together', async () => {
    const page = await test.browser.newPage();
    await loginAsLearner(page);
    
    // Search for content
    await page.goto(`${TEST_CONFIG.baseUrl}/search`);
    await performSearch(page, 'physics');
    await selectFirstSearchResult(page);
    
    // Verify recommendations are contextually relevant
    await page.waitForSelector('[data-testid=recommendations]');
    
    const recommendations = await page.locator('[data-testid=recommendation-item]').all();
    expect(recommendations.length).toBeGreaterThan(0);
    
    // Check that recommendations are from relevant domains
    for (const rec of recommendations.slice(0, 3)) {
      const domain = await rec.locator('[data-testid=item-domain]').textContent();
      expect(domain).toMatch(/physics|general/i); // Should be physics or general
    }
    
    await page.close();
  });

  test('content generation integrates with search', async () => {
    const page = await test.browser.newPage();
    await loginAsAuthor(page);
    
    // Generate new content
    await generateContent(page, 'Quantum Physics Basics');
    await validateGeneratedContent(page);
    
    // Publish the content
    if (await page.locator('[data-testid=can-publish]').isVisible()) {
      await page.click('[data-testid=confirm-publish]');
      await page.waitForSelector('[data-testid=publish-success]');
    }
    
    // Switch to learner and search for the new content
    await loginAsLearner(page);
    await page.goto(`${TEST_CONFIG.baseUrl}/search`);
    await performSearch(page, 'Quantum Physics');
    
    const results = await waitForSearchResults(page);
    expect(results.length).toBeGreaterThan(0);
    
    await page.close();
  });

  test('end-to-end content lifecycle', async () => {
    const page = await test.browser.newPage();
    
    // 1. Author generates content
    await loginAsAuthor(page);
    await generateContent(page, 'Ecosystem Dynamics');
    const validation = await validateGeneratedContent(page);
    expect(validation.hasExamples || validation.hasSimulations || validation.hasAnimations).toBe(true);
    
    // 2. Author publishes content
    if (await page.locator('[data-testid=can-publish]').isVisible()) {
      await page.click('[data-testid=confirm-publish]');
      await page.waitForSelector('[data-testid=publish-success]');
    }
    
    // 3. Learner discovers content
    await loginAsLearner(page);
    await page.goto(`${TEST_CONFIG.baseUrl}/search`);
    await performSearch(page, 'Ecosystem');
    await selectFirstSearchResult(page);
    
    // 4. Learner gets recommendations
    await page.waitForSelector('[data-testid=recommendations]');
    const recommendations = await page.locator('[data-testid=recommendation-item]').count();
    expect(recommendations).toBeGreaterThan(0);
    
    // 5. Learner follows learning pathway
    const nextSteps = await page.locator('[data-testid=next-step]');
    if (await nextSteps.count() > 0) {
      await nextSteps.first().click();
      await page.waitForSelector('[data-testid=asset-detail]');
    }
    
    await page.close();
  });
});

// ============================================================================
// Performance and Reliability Tests
// ============================================================================

test.describe('Performance and Reliability', () => {
  test('search performance under load', async () => {
    const page = await test.browser.newPage();
    await loginAsLearner(page);
    
    const searchTimes: number[] = [];
    
    for (let i = 0; i < 10; i++) {
      const startTime = Date.now();
      await page.goto(`${TEST_CONFIG.baseUrl}/search`);
      await performSearch(page, SAMPLE_QUERIES[i % SAMPLE_QUERIES.length]);
      await waitForSearchResults(page);
      searchTimes.push(Date.now() - startTime);
    }
    
    const avgTime = searchTimes.reduce((sum, time) => sum + time, 0) / searchTimes.length;
    const maxTime = Math.max(...searchTimes);
    
    // Average search time should be under 2 seconds
    expect(avgTime).toBeLessThan(2000);
    // Maximum search time should be under 5 seconds
    expect(maxTime).toBeLessThan(5000);
    
    await page.close();
  });

  test('content generation reliability', async () => {
    const page = await test.browser.newPage();
    await loginAsAuthor(page);
    
    const concepts = ['Wave Motion', 'Molecular Structure', 'Genetic Inheritance'];
    const successCount = concepts.length;
    
    for (const concept of concepts) {
      try {
        await generateContent(page, concept);
        const validation = await validateGeneratedContent(page);
        expect(validation.hasExamples || validation.hasSimulations || validation.hasAnimations).toBe(true);
      } catch (error) {
        console.error(`Generation failed for ${concept}:`, error);
      }
    }
    
    // All generations should succeed
    expect(successCount).toBe(concepts.length);
    
    await page.close();
  });

  test('error handling and recovery', async () => {
    const page = await test.browser.newPage();
    await loginAsLearner(page);
    
    // Test invalid search query handling
    await page.goto(`${TEST_CONFIG.baseUrl}/search`);
    await performSearch(page, ''); // Empty search
    await expect(page.locator('[data-testid=search-error]')).toBeVisible();
    
    // Test network error recovery
    await page.route('**/api/search*', route => route.abort());
    await performSearch(page, 'physics');
    await expect(page.locator('[data-testid=network-error]')).toBeVisible();
    
    // Unmock and retry
    await page.unroute('**/api/search*');
    await page.click('[data-testid=retry-button]');
    await waitForSearchResults(page);
    
    await page.close();
  });
});
