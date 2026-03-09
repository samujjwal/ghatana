/**
 * End-to-End Tests for Canvas Refactoring Features
 * Comprehensive testing of Phases 1-3 functionality
 */

import { test, expect, Page } from '@playwright/test';

test.describe('Canvas Refactoring - End-to-End Tests', () => {
  test.describe('Phase 1: Generic Canvas Foundation', () => {
    test('should render GenericCanvas with basic functionality', async ({
      page,
    }) => {
      await page.goto('/canvas/generic-canvas-demo');

      // Wait for canvas to load
      await expect(
        page.locator('[data-testid="generic-canvas"]')
      ).toBeVisible();

      // Verify toolbar is present
      await expect(
        page.locator('[data-testid="canvas-toolbar"]')
      ).toBeVisible();

      // Verify view mode switcher
      await expect(
        page.locator('[data-testid="view-mode-switcher"]')
      ).toBeVisible();

      // Test view mode switching
      await page.locator('[data-testid="view-mode-list"]').click();
      await expect(
        page.locator('[data-testid="canvas-view-list"]')
      ).toBeVisible();

      await page.locator('[data-testid="view-mode-grid"]').click();
      await expect(
        page.locator('[data-testid="canvas-view-grid"]')
      ).toBeVisible();

      await page.locator('[data-testid="view-mode-canvas"]').click();
      await expect(
        page.locator('[data-testid="canvas-view-canvas"]')
      ).toBeVisible();
    });

    test('should handle item creation, update, and deletion', async ({
      page,
    }) => {
      await page.goto('/canvas/generic-canvas-demo');

      // Create new item
      await page.locator('[data-testid="toolbar-add-item"]').click();
      await page.fill('[data-testid="item-label-input"]', 'Test Item');
      await page.locator('[data-testid="create-item-confirm"]').click();

      // Verify item was created
      await expect(
        page.locator('[data-testid="canvas-item"]').last()
      ).toContainText('Test Item');

      // Select and update item
      await page.locator('[data-testid="canvas-item"]').last().click();
      await expect(
        page.locator('[data-testid="selected-item-indicator"]')
      ).toBeVisible();

      // Update item properties
      await page.locator('[data-testid="toolbar-edit-item"]').click();
      await page.fill('[data-testid="item-label-input"]', 'Updated Test Item');
      await page.locator('[data-testid="update-item-confirm"]').click();

      // Verify item was updated
      await expect(
        page.locator('[data-testid="canvas-item"]').last()
      ).toContainText('Updated Test Item');

      // Delete item
      await page.locator('[data-testid="canvas-item"]').last().click();
      await page.locator('[data-testid="toolbar-delete-item"]').click();
      await page.locator('[data-testid="delete-confirm"]').click();

      // Verify item was deleted
      await expect(
        page.locator('[data-testid="canvas-item"]').last()
      ).not.toContainText('Updated Test Item');
    });

    test('should support keyboard shortcuts', async ({ page }) => {
      await page.goto('/canvas/generic-canvas-demo');

      // Create an item first
      await page.locator('[data-testid="toolbar-add-item"]').click();
      await page.fill('[data-testid="item-label-input"]', 'Keyboard Test Item');
      await page.locator('[data-testid="create-item-confirm"]').click();

      // Select item
      await page.locator('[data-testid="canvas-item"]').last().click();

      // Test copy (Ctrl+C)
      await page.keyboard.press('Control+c');

      // Test paste (Ctrl+V)
      await page.keyboard.press('Control+v');

      // Should have duplicated item
      const items = page.locator('[data-testid="canvas-item"]');
      await expect(items).toHaveCount(2);

      // Test delete (Delete key)
      await page.locator('[data-testid="canvas-item"]').last().click();
      await page.keyboard.press('Delete');

      // Should have one item remaining
      await expect(items).toHaveCount(1);

      // Test undo (Ctrl+Z)
      await page.keyboard.press('Control+z');
      await expect(items).toHaveCount(2);

      // Test redo (Ctrl+Y)
      await page.keyboard.press('Control+y');
      await expect(items).toHaveCount(1);
    });
  });

  test.describe('Phase 2: Registry Migration', () => {
    test('should display unified component registry', async ({ page }) => {
      await page.goto('/canvas/registry-demo');

      // Wait for registry to load
      await expect(
        page.locator('[data-testid="unified-registry"]')
      ).toBeVisible();

      // Verify namespaces are displayed
      await expect(
        page.locator('[data-testid="namespace-devsecops"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="namespace-page-designer"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="namespace-shared"]')
      ).toBeVisible();

      // Test namespace filtering
      await page.locator('[data-testid="namespace-filter-devsecops"]').click();
      await expect(
        page.locator('[data-testid="component-phase-card"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="component-security-gate"]')
      ).toBeVisible();

      // Test search functionality
      await page.fill('[data-testid="registry-search"]', 'button');
      await expect(
        page.locator('[data-testid="component-button"]')
      ).toBeVisible();

      // Clear search and test category filtering
      await page.fill('[data-testid="registry-search"]', '');
      await page.selectOption('[data-testid="category-filter"]', 'input');
      await expect(
        page.locator('[data-testid="component-button"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="component-text-field"]')
      ).toBeVisible();
    });

    test('should migrate legacy canvas to use generic foundation', async ({
      page,
    }) => {
      await page.goto('/canvas/migration-demo');

      // Should see side-by-side comparison
      await expect(page.locator('[data-testid="legacy-canvas"]')).toBeVisible();
      await expect(
        page.locator('[data-testid="migrated-canvas"]')
      ).toBeVisible();

      // Test that both canvases have the same items
      const legacyItems = page.locator(
        '[data-testid="legacy-canvas"] [data-testid="canvas-item"]'
      );
      const migratedItems = page.locator(
        '[data-testid="migrated-canvas"] [data-testid="canvas-item"]'
      );

      await expect(legacyItems).toHaveCount(2);
      await expect(migratedItems).toHaveCount(2);

      // Test that migrated canvas has additional features
      await expect(
        page.locator(
          '[data-testid="migrated-canvas"] [data-testid="view-mode-switcher"]'
        )
      ).toBeVisible();
      await expect(
        page.locator(
          '[data-testid="migrated-canvas"] [data-testid="canvas-toolbar"]'
        )
      ).toBeVisible();

      // Test enhanced functionality in migrated canvas
      await page
        .locator(
          '[data-testid="migrated-canvas"] [data-testid="view-mode-list"]'
        )
        .click();
      await expect(
        page.locator(
          '[data-testid="migrated-canvas"] [data-testid="canvas-view-list"]'
        )
      ).toBeVisible();
    });

    test('should execute automated migration workflow', async ({ page }) => {
      await page.goto('/canvas/migration-orchestrator');

      // Start migration process
      await page.locator('[data-testid="start-migration"]').click();

      // Wait for migration steps to complete
      await expect(
        page.locator('[data-testid="migration-step-registry"]')
      ).toHaveClass(/completed/);
      await expect(
        page.locator('[data-testid="migration-step-validation"]')
      ).toHaveClass(/completed/);
      await expect(
        page.locator('[data-testid="migration-step-backup"]')
      ).toHaveClass(/completed/);

      // Verify migration results
      await expect(
        page.locator('[data-testid="migration-status"]')
      ).toContainText('completed successfully');

      // Check migration statistics
      const stats = page.locator('[data-testid="migration-stats"]');
      await expect(stats).toContainText('12 components migrated');
      await expect(stats).toContainText('5 namespaces');
      await expect(stats).toContainText('0 errors');
    });
  });

  test.describe('Phase 3: Performance & Advanced Features', () => {
    test('should handle large datasets with virtual scrolling', async ({
      page,
    }) => {
      await page.goto('/canvas/performance-demo?itemCount=1000');

      // Wait for canvas to load with many items
      await expect(
        page.locator('[data-testid="canvas-container"]')
      ).toBeVisible();

      // Verify virtual scrolling is active
      await expect(
        page.locator('[data-testid="virtual-scroll-indicator"]')
      ).toBeVisible();

      // Check performance metrics
      const metrics = page.locator('[data-testid="performance-metrics"]');
      await expect(metrics).toBeVisible();

      // FPS should be reasonable (>30 fps)
      const fps = await page
        .locator('[data-testid="fps-counter"]')
        .textContent();
      expect(parseInt(fps || '0')).toBeGreaterThan(30);

      // Render time should be acceptable (<50ms)
      const renderTime = await page
        .locator('[data-testid="render-time"]')
        .textContent();
      expect(parseFloat(renderTime?.replace('ms', '') || '100')).toBeLessThan(
        50
      );

      // Test scrolling performance
      await page.locator('[data-testid="canvas-container"]').evaluate((el) => {
        el.scrollTop = 5000; // Scroll down significantly
      });

      // Should still maintain good performance after scrolling
      await page.waitForTimeout(100);
      const fpsAfterScroll = await page
        .locator('[data-testid="fps-counter"]')
        .textContent();
      expect(parseInt(fpsAfterScroll || '0')).toBeGreaterThan(25);
    });

    test('should support real-time collaboration', async ({
      page,
      context,
    }) => {
      // Open collaboration demo in two tabs
      const page1 = page;
      const page2 = await context.newPage();

      await page1.goto('/canvas/collaboration-demo');
      await page2.goto('/canvas/collaboration-demo');

      // Wait for both canvases to load
      await expect(
        page1.locator('[data-testid="collaboration-canvas"]')
      ).toBeVisible();
      await expect(
        page2.locator('[data-testid="collaboration-canvas"]')
      ).toBeVisible();

      // Verify users are connected
      await expect(
        page1.locator('[data-testid="collaborator-list"]')
      ).toContainText('2');
      await expect(
        page2.locator('[data-testid="collaborator-list"]')
      ).toContainText('2');

      // Create item in page1
      await page1.locator('[data-testid="toolbar-add-item"]').click();
      await page1.fill(
        '[data-testid="item-label-input"]',
        'Collaboration Test'
      );
      await page1.locator('[data-testid="create-item-confirm"]').click();

      // Item should appear in page2
      await expect(
        page2.locator('[data-testid="canvas-item"]').last()
      ).toContainText('Collaboration Test');

      // Test cursor synchronization
      await page1
        .locator('[data-testid="canvas-container"]')
        .hover({ position: { x: 100, y: 100 } });
      await expect(page2.locator('[data-testid="user-cursor"]')).toBeVisible();

      // Test selection synchronization
      await page1.locator('[data-testid="canvas-item"]').last().click();
      await expect(
        page2.locator('[data-testid="remote-selection-indicator"]')
      ).toBeVisible();

      await page2.close();
    });

    test('should support advanced undo/redo with branching', async ({
      page,
    }) => {
      await page.goto('/canvas/advanced-history-demo');

      // Create some items to build history
      for (let i = 0; i < 3; i++) {
        await page.locator('[data-testid="toolbar-add-item"]').click();
        await page.fill('[data-testid="item-label-input"]', `Item ${i + 1}`);
        await page.locator('[data-testid="create-item-confirm"]').click();
      }

      // Verify history panel shows entries
      await expect(page.locator('[data-testid="history-panel"]')).toBeVisible();
      const historyEntries = page.locator('[data-testid="history-entry"]');
      await expect(historyEntries).toHaveCount(3);

      // Test undo/redo
      await page.keyboard.press('Control+z');
      const items = page.locator('[data-testid="canvas-item"]');
      await expect(items).toHaveCount(2);

      await page.keyboard.press('Control+y');
      await expect(items).toHaveCount(3);

      // Test branch creation
      await page.locator('[data-testid="create-branch-btn"]').click();
      await page.fill('[data-testid="branch-name-input"]', 'Feature Branch');
      await page.locator('[data-testid="confirm-branch-creation"]').click();

      // Verify branch was created
      await expect(
        page.locator('[data-testid="branch-feature-branch"]')
      ).toBeVisible();

      // Make changes in the branch
      await page.locator('[data-testid="toolbar-add-item"]').click();
      await page.fill('[data-testid="item-label-input"]', 'Branch Item');
      await page.locator('[data-testid="create-item-confirm"]').click();

      // Switch back to main branch
      await page.locator('[data-testid="branch-main"]').click();
      await expect(items).toHaveCount(3); // Should not have branch item

      // Switch back to feature branch
      await page.locator('[data-testid="branch-feature-branch"]').click();
      await expect(items).toHaveCount(4); // Should have branch item
    });

    test('should optimize rendering for viewport culling', async ({ page }) => {
      await page.goto('/canvas/viewport-culling-demo?itemCount=500');

      // Wait for canvas to load
      await expect(
        page.locator('[data-testid="canvas-container"]')
      ).toBeVisible();

      // Verify viewport culling is active
      await expect(page.locator('[data-testid="culling-stats"]')).toBeVisible();

      // Check culling ratio
      const cullingRatio = await page
        .locator('[data-testid="culling-ratio"]')
        .textContent();
      const ratio = parseFloat(cullingRatio?.replace('%', '') || '0') / 100;
      expect(ratio).toBeGreaterThan(0.5); // Should cull at least 50% of items

      // Test panning/zooming maintains performance
      await page.locator('[data-testid="canvas-container"]').evaluate((el) => {
        const canvas = el as HTMLElement;
        canvas.style.transform = 'translate(-1000px, -1000px) scale(0.5)';
      });

      await page.waitForTimeout(100);

      // Performance should still be good after transformation
      const fps = await page
        .locator('[data-testid="fps-counter"]')
        .textContent();
      expect(parseInt(fps || '0')).toBeGreaterThan(25);
    });
  });

  test.describe('Integration Tests', () => {
    test('should integrate all phases seamlessly', async ({ page }) => {
      await page.goto('/canvas/full-integration-demo');

      // Test Phase 1: Generic Canvas Foundation
      await expect(
        page.locator('[data-testid="generic-canvas"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="view-mode-switcher"]')
      ).toBeVisible();

      // Test Phase 2: Registry Integration
      await page.locator('[data-testid="component-palette"]').click();
      await expect(
        page.locator('[data-testid="registry-components"]')
      ).toBeVisible();

      // Add component from registry
      await page.locator('[data-testid="registry-component-button"]').click();
      await page
        .locator('[data-testid="canvas-container"]')
        .click({ position: { x: 200, y: 200 } });

      // Verify component was added
      await expect(
        page.locator('[data-testid="canvas-item"]').last()
      ).toBeVisible();

      // Test Phase 3: Performance Features
      await expect(
        page.locator('[data-testid="performance-monitor"]')
      ).toBeVisible();

      // Test collaboration (if available)
      if (
        await page
          .locator('[data-testid="collaboration-indicator"]')
          .isVisible()
      ) {
        await expect(page.locator('[data-testid="user-list"]')).toBeVisible();
      }

      // Test advanced history
      await page.keyboard.press('Control+z');
      const items = page.locator('[data-testid="canvas-item"]');
      const itemCountAfterUndo = await items.count();

      await page.keyboard.press('Control+y');
      const itemCountAfterRedo = await items.count();

      expect(itemCountAfterRedo).toBe(itemCountAfterUndo + 1);
    });

    test('should maintain performance with all features enabled', async ({
      page,
    }) => {
      await page.goto('/canvas/full-feature-performance-test?itemCount=200');

      // Enable all features
      await page.check('[data-testid="enable-collaboration"]');
      await page.check('[data-testid="enable-advanced-history"]');
      await page.check('[data-testid="enable-performance-monitoring"]');
      await page.check('[data-testid="enable-virtual-scrolling"]');

      // Wait for features to initialize
      await page.waitForTimeout(1000);

      // Perform stress test operations
      for (let i = 0; i < 10; i++) {
        // Add item
        await page.locator('[data-testid="toolbar-add-item"]').click();
        await page.fill(
          '[data-testid="item-label-input"]',
          `Stress Test Item ${i}`
        );
        await page.locator('[data-testid="create-item-confirm"]').click();

        // Move item
        await page
          .locator('[data-testid="canvas-item"]')
          .last()
          .dragTo(page.locator('[data-testid="canvas-container"]'), {
            targetPosition: { x: 100 + i * 50, y: 100 + i * 30 },
          });

        // Update item
        await page.locator('[data-testid="canvas-item"]').last().dblclick();
        await page.fill(
          '[data-testid="item-label-input"]',
          `Updated Item ${i}`
        );
        await page.locator('[data-testid="update-item-confirm"]').click();
      }

      // Check final performance metrics
      const finalFps = await page
        .locator('[data-testid="fps-counter"]')
        .textContent();
      expect(parseInt(finalFps || '0')).toBeGreaterThan(20);

      const finalRenderTime = await page
        .locator('[data-testid="render-time"]')
        .textContent();
      expect(
        parseFloat(finalRenderTime?.replace('ms', '') || '100')
      ).toBeLessThan(100);

      // Verify all items were created and updated correctly
      const finalItems = page.locator('[data-testid="canvas-item"]');
      await expect(finalItems).toHaveCount(10);

      // Check that history recorded all operations
      const historyEntries = page.locator('[data-testid="history-entry"]');
      await expect(historyEntries.count()).toBeGreaterThan(20); // Creates + moves + updates
    });
  });
});

// Helper functions for test setup
async function setupCanvasDemo(
  page: Page,
  demoType: string,
  options: any = {}
) {
  const url = `/canvas/${demoType}?${new URLSearchParams(options).toString()}`;
  await page.goto(url);
  await page.waitForLoadState('networkidle');
}

async function createTestItem(
  page: Page,
  label: string,
  position?: { x: number; y: number }
) {
  await page.locator('[data-testid="toolbar-add-item"]').click();
  await page.fill('[data-testid="item-label-input"]', label);

  if (position) {
    await page.fill('[data-testid="item-x-position"]', position.x.toString());
    await page.fill('[data-testid="item-y-position"]', position.y.toString());
  }

  await page.locator('[data-testid="create-item-confirm"]').click();
}

async function waitForPerformanceStabilization(
  page: Page,
  minFps: number = 30,
  maxTime: number = 5000
) {
  const startTime = Date.now();

  while (Date.now() - startTime < maxTime) {
    const fps = await page.locator('[data-testid="fps-counter"]').textContent();
    if (parseInt(fps || '0') >= minFps) {
      break;
    }
    await page.waitForTimeout(100);
  }
}
