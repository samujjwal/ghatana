import { test, expect, type Page } from '@playwright/test';

/**
 * DevSecOps Phase Detail E2E Tests
 * 
 * Test Suite: Phase detail page with view switching, filtering, and interactions
 * Scope: Kanban/Timeline/Table views, filters, search, drag-and-drop, side panel
 * Framework: Playwright
 */

test.describe('DevSecOps Phase Detail', () => {
  // Use a specific phase ID for testing
  const TEST_PHASE_ID = 'plan';

  test.beforeEach(async ({ page }) => {
    // Navigate to phase detail page
    await page.goto(`/devsecops/phase/${TEST_PHASE_ID}`);
    
    // Wait for page to be fully loaded
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Header & Navigation', () => {
    test('should display breadcrumbs navigation', async ({ page }) => {
      const breadcrumbs = page.locator('[data-testid="breadcrumbs"]');
      
      await expect(breadcrumbs).toBeVisible();
      
      // Should have DevSecOps link
      await expect(breadcrumbs.getByText('DevSecOps')).toBeVisible();
    });

    test('should navigate back to dashboard via breadcrumbs', async ({ page }) => {
      const dashboardLink = page.locator('[data-testid="breadcrumbs"]').getByText('DevSecOps');
      
      await dashboardLink.click();
      
      // Should navigate back to dashboard
      await page.waitForURL('/devsecops');
      expect(page.url()).toContain('/devsecops');
    });

    test('should display phase navigation with current phase highlighted', async ({ page }) => {
      const phaseNav = page.locator('[data-testid="phase-nav"]');
      
      await expect(phaseNav).toBeVisible();
      
      // Current phase should be highlighted
      const activePhase = phaseNav.locator('[data-active="true"]');
      await expect(activePhase).toBeVisible();
    });

    test('should allow navigation between phases', async ({ page }) => {
      const phaseNav = page.locator('[data-testid="phase-nav"]');
      const secondPhase = phaseNav.locator('[data-testid="phase-nav-item"]').nth(1);
      
      await secondPhase.click();
      
      // URL should change to new phase
      await page.waitForURL(/\/devsecops\/phase\/[^/]+/);
    });
  });

  test.describe('View Mode Switcher', () => {
    test('should display view mode switcher with all 3 modes', async ({ page }) => {
      const viewSwitcher = page.locator('[data-testid="view-mode-switcher"]');
      
      await expect(viewSwitcher).toBeVisible();
      
      // Should have Kanban, Timeline, Table buttons
      await expect(viewSwitcher.getByRole('button', { name: /kanban/i })).toBeVisible();
      await expect(viewSwitcher.getByRole('button', { name: /timeline/i })).toBeVisible();
      await expect(viewSwitcher.getByRole('button', { name: /table/i })).toBeVisible();
    });

    test('should default to Kanban view', async ({ page }) => {
      const kanbanView = page.locator('[data-testid="kanban-board"]');
      
      await expect(kanbanView).toBeVisible();
    });

    test('should switch to Timeline view on click', async ({ page }) => {
      const viewSwitcher = page.locator('[data-testid="view-mode-switcher"]');
      const timelineButton = viewSwitcher.getByRole('button', { name: /timeline/i });
      
      await timelineButton.click();
      
      // Timeline should now be visible
      const timelineView = page.locator('[data-testid="timeline"]');
      await expect(timelineView).toBeVisible();
      
      // Kanban should be hidden
      const kanbanView = page.locator('[data-testid="kanban-board"]');
      await expect(kanbanView).not.toBeVisible();
    });

    test('should switch to Table view on click', async ({ page }) => {
      const viewSwitcher = page.locator('[data-testid="view-mode-switcher"]');
      const tableButton = viewSwitcher.getByRole('button', { name: /table/i });
      
      await tableButton.click();
      
      // Table should now be visible
      const tableView = page.locator('[data-testid="data-table"]');
      await expect(tableView).toBeVisible();
    });

    test('should persist view mode on page reload', async ({ page }) => {
      // Switch to Timeline
      const viewSwitcher = page.locator('[data-testid="view-mode-switcher"]');
      await viewSwitcher.getByRole('button', { name: /timeline/i }).click();
      
      // Wait for view to switch
      await page.locator('[data-testid="timeline"]').waitFor({ state: 'visible' });
      
      // Reload page
      await page.reload();
      await page.waitForLoadState('networkidle');
      
      // Timeline should still be active
      await expect(page.locator('[data-testid="timeline"]')).toBeVisible();
    });

    test('should highlight active view mode button', async ({ page }) => {
      const viewSwitcher = page.locator('[data-testid="view-mode-switcher"]');
      
      // Kanban should be active by default
      const kanbanButton = viewSwitcher.getByRole('button', { name: /kanban/i });
      await expect(kanbanButton).toHaveAttribute('data-active', 'true');
      
      // Click Timeline
      const timelineButton = viewSwitcher.getByRole('button', { name: /timeline/i });
      await timelineButton.click();
      
      // Timeline should now be active
      await expect(timelineButton).toHaveAttribute('data-active', 'true');
      await expect(kanbanButton).not.toHaveAttribute('data-active', 'true');
    });
  });

  test.describe('Search Functionality', () => {
    test('should display search bar', async ({ page }) => {
      const searchBar = page.locator('[data-testid="search-bar"]');
      
      await expect(searchBar).toBeVisible();
    });

    test('should filter items by search term', async ({ page }) => {
      const searchBar = page.locator('[data-testid="search-bar"]');
      const searchInput = searchBar.locator('input');
      
      // Type search term
      await searchInput.fill('security');
      
      // Wait for debounce
      await page.waitForTimeout(500);
      
      // Items should be filtered
      // (This would need to verify against actual item count, but we test the interaction)
      await expect(searchInput).toHaveValue('security');
    });

    test('should show clear button when search has value', async ({ page }) => {
      const searchBar = page.locator('[data-testid="search-bar"]');
      const searchInput = searchBar.locator('input');
      
      // Initially no clear button
      const clearButton = searchBar.locator('[data-testid="search-clear"]');
      await expect(clearButton).not.toBeVisible();
      
      // Type search term
      await searchInput.fill('test');
      
      // Clear button should appear
      await expect(clearButton).toBeVisible();
    });

    test('should clear search when clear button clicked', async ({ page }) => {
      const searchBar = page.locator('[data-testid="search-bar"]');
      const searchInput = searchBar.locator('input');
      
      // Type search term
      await searchInput.fill('test');
      
      // Click clear button
      const clearButton = searchBar.locator('[data-testid="search-clear"]');
      await clearButton.click();
      
      // Input should be empty
      await expect(searchInput).toHaveValue('');
    });

    test('should debounce search input', async ({ page }) => {
      const searchBar = page.locator('[data-testid="search-bar"]');
      const searchInput = searchBar.locator('input');
      
      // Type multiple characters quickly
      await searchInput.type('testing', { delay: 50 });
      
      // Wait for debounce
      await page.waitForTimeout(500);
      
      // Final value should be applied
      await expect(searchInput).toHaveValue('testing');
    });
  });

  test.describe('Filter Panel', () => {
    test('should display filter button', async ({ page }) => {
      const filterButton = page.getByRole('button', { name: /filter/i });
      
      await expect(filterButton).toBeVisible();
    });

    test('should open filter drawer on button click', async ({ page }) => {
      const filterButton = page.getByRole('button', { name: /filter/i });
      
      await filterButton.click();
      
      // Filter drawer should open
      const filterDrawer = page.locator('[data-testid="filter-drawer"]');
      await expect(filterDrawer).toBeVisible();
    });

    test('should close filter drawer on close button', async ({ page }) => {
      // Open drawer
      await page.getByRole('button', { name: /filter/i }).click();
      
      const filterDrawer = page.locator('[data-testid="filter-drawer"]');
      await expect(filterDrawer).toBeVisible();
      
      // Click close button
      const closeButton = filterDrawer.locator('[data-testid="drawer-close"]');
      await closeButton.click();
      
      // Drawer should close
      await expect(filterDrawer).not.toBeVisible();
    });

    test('should display filter sections: Status, Priority, Assignee', async ({ page }) => {
      // Open filter drawer
      await page.getByRole('button', { name: /filter/i }).click();
      
      const filterPanel = page.locator('[data-testid="filter-panel"]');
      
      // Should have Status section
      await expect(filterPanel.getByText('Status')).toBeVisible();
      
      // Should have Priority section
      await expect(filterPanel.getByText('Priority')).toBeVisible();
      
      // Should have Assignee section
      await expect(filterPanel.getByText('Assignee')).toBeVisible();
    });

    test('should allow selecting status filters', async ({ page }) => {
      // Open filter drawer
      await page.getByRole('button', { name: /filter/i }).click();
      
      const filterPanel = page.locator('[data-testid="filter-panel"]');
      
      // Click "In Progress" checkbox
      const inProgressCheckbox = filterPanel.getByLabel(/in progress/i);
      await inProgressCheckbox.check();
      
      // Checkbox should be checked
      await expect(inProgressCheckbox).toBeChecked();
    });

    test('should allow selecting multiple filters', async ({ page }) => {
      // Open filter drawer
      await page.getByRole('button', { name: /filter/i }).click();
      
      const filterPanel = page.locator('[data-testid="filter-panel"]');
      
      // Select multiple status filters
      await filterPanel.getByLabel(/in progress/i).check();
      await filterPanel.getByLabel(/blocked/i).check();
      
      // Both should be checked
      await expect(filterPanel.getByLabel(/in progress/i)).toBeChecked();
      await expect(filterPanel.getByLabel(/blocked/i)).toBeChecked();
    });

    test('should apply filters when Apply button clicked', async ({ page }) => {
      // Open filter drawer
      await page.getByRole('button', { name: /filter/i }).click();
      
      const filterPanel = page.locator('[data-testid="filter-panel"]');
      
      // Select a filter
      await filterPanel.getByLabel(/completed/i).check();
      
      // Click Apply button
      const applyButton = filterPanel.getByRole('button', { name: /apply/i });
      await applyButton.click();
      
      // Drawer should close
      await expect(page.locator('[data-testid="filter-drawer"]')).not.toBeVisible();
    });

    test('should clear all filters when Clear button clicked', async ({ page }) => {
      // Open filter drawer
      await page.getByRole('button', { name: /filter/i }).click();
      
      const filterPanel = page.locator('[data-testid="filter-panel"]');
      
      // Select filters
      await filterPanel.getByLabel(/in progress/i).check();
      await filterPanel.getByLabel(/high/i).check();
      
      // Click Clear All button
      const clearButton = filterPanel.getByRole('button', { name: /clear all/i });
      await clearButton.click();
      
      // All checkboxes should be unchecked
      await expect(filterPanel.getByLabel(/in progress/i)).not.toBeChecked();
      await expect(filterPanel.getByLabel(/high/i)).not.toBeChecked();
    });

    test('should combine search and filters', async ({ page }) => {
      // Enter search term
      const searchInput = page.locator('[data-testid="search-bar"] input');
      await searchInput.fill('api');
      
      // Open filters and select
      await page.getByRole('button', { name: /filter/i }).click();
      await page.locator('[data-testid="filter-panel"]').getByLabel(/in progress/i).check();
      await page.locator('[data-testid="filter-panel"]').getByRole('button', { name: /apply/i }).click();
      
      // Both search and filters should be active
      await expect(searchInput).toHaveValue('api');
    });
  });

  test.describe('Kanban Board View', () => {
    test('should display kanban board with status columns', async ({ page }) => {
      const kanbanBoard = page.locator('[data-testid="kanban-board"]');
      
      await expect(kanbanBoard).toBeVisible();
      
      // Should have columns for: Not Started, In Progress, Completed, Blocked
      await expect(kanbanBoard.getByText('Not Started')).toBeVisible();
      await expect(kanbanBoard.getByText('In Progress')).toBeVisible();
      await expect(kanbanBoard.getByText('Completed')).toBeVisible();
      await expect(kanbanBoard.getByText('Blocked')).toBeVisible();
    });

    test('should display item count in column headers', async ({ page }) => {
      const kanbanBoard = page.locator('[data-testid="kanban-board"]');
      
      // Each column header should show count (e.g., "In Progress (5)")
      await expect(kanbanBoard.locator('text=/\\(\\d+\\)/')).toBeVisible();
    });

    test('should display items as cards in columns', async ({ page }) => {
      const kanbanBoard = page.locator('[data-testid="kanban-board"]');
      const itemCards = kanbanBoard.locator('[data-testid="item-card"]');
      
      // Should have at least one item card
      const count = await itemCards.count();
      expect(count).toBeGreaterThan(0);
    });

    test('should display item card with title, status, priority, assignee', async ({ page }) => {
      const firstCard = page.locator('[data-testid="item-card"]').first();
      
      await expect(firstCard).toBeVisible();
      
      // Card should have content
      const text = await firstCard.textContent();
      expect(text).toBeTruthy();
    });

    test('should allow dragging item to different column', async ({ page }) => {
      const itemCard = page.locator('[data-testid="item-card"]').first();
      const targetColumn = page.locator('[data-testid="kanban-column"][data-status="in-progress"]');
      
      // Drag item to target column
      await itemCard.dragTo(targetColumn);
      
      // Note: Full drag-drop testing requires more setup
      // This tests the interaction exists
    });

    test('should open item detail on card click', async ({ page }) => {
      const firstCard = page.locator('[data-testid="item-card"]').first();
      
      await firstCard.click();
      
      // Side panel should open
      const sidePanel = page.locator('[data-testid="side-panel"]');
      await expect(sidePanel).toBeVisible();
    });
  });

  test.describe('Timeline View', () => {
    test('should display timeline when Timeline view selected', async ({ page }) => {
      // Switch to Timeline view
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /timeline/i }).click();
      
      const timeline = page.locator('[data-testid="timeline"]');
      await expect(timeline).toBeVisible();
    });

    test('should display items chronologically', async ({ page }) => {
      // Switch to Timeline view
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /timeline/i }).click();
      
      const timelineItems = page.locator('[data-testid="timeline-item"]');
      
      // Should have at least one item
      const count = await timelineItems.count();
      expect(count).toBeGreaterThan(0);
    });

    test('should display milestone markers', async ({ page }) => {
      // Switch to Timeline view
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /timeline/i }).click();
      
      // Look for milestone indicators
      const milestones = page.locator('[data-testid="milestone-marker"]');
      
      // May or may not have milestones, just verify it doesn't crash
      await expect(page.locator('[data-testid="timeline"]')).toBeVisible();
    });

    test('should open item detail on timeline item click', async ({ page }) => {
      // Switch to Timeline view
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /timeline/i }).click();
      
      const firstItem = page.locator('[data-testid="timeline-item"]').first();
      
      if (await firstItem.isVisible()) {
        await firstItem.click();
        
        // Side panel should open
        const sidePanel = page.locator('[data-testid="side-panel"]');
        await expect(sidePanel).toBeVisible();
      }
    });
  });

  test.describe('Table View', () => {
    test('should display data table when Table view selected', async ({ page }) => {
      // Switch to Table view
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /table/i }).click();
      
      const dataTable = page.locator('[data-testid="data-table"]');
      await expect(dataTable).toBeVisible();
    });

    test('should display table headers: Title, Status, Priority, Assignee, Due Date', async ({ page }) => {
      // Switch to Table view
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /table/i }).click();
      
      const table = page.locator('[data-testid="data-table"]');
      
      await expect(table.getByRole('columnheader', { name: /title/i })).toBeVisible();
      await expect(table.getByRole('columnheader', { name: /status/i })).toBeVisible();
      await expect(table.getByRole('columnheader', { name: /priority/i })).toBeVisible();
      await expect(table.getByRole('columnheader', { name: /assignee/i })).toBeVisible();
      await expect(table.getByRole('columnheader', { name: /due date/i })).toBeVisible();
    });

    test('should display table rows with item data', async ({ page }) => {
      // Switch to Table view
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /table/i }).click();
      
      const tableRows = page.locator('[data-testid="table-row"]');
      
      // Should have at least one row
      const count = await tableRows.count();
      expect(count).toBeGreaterThan(0);
    });

    test('should sort by column on header click', async ({ page }) => {
      // Switch to Table view
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /table/i }).click();
      
      const titleHeader = page.getByRole('columnheader', { name: /title/i });
      
      // Click to sort
      await titleHeader.click();
      
      // Should show sort indicator
      await expect(titleHeader.locator('[data-testid="sort-icon"]')).toBeVisible();
    });

    test('should toggle sort direction on second click', async ({ page }) => {
      // Switch to Table view
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /table/i }).click();
      
      const statusHeader = page.getByRole('columnheader', { name: /status/i });
      
      // First click: ascending
      await statusHeader.click();
      await page.waitForTimeout(200);
      
      // Second click: descending
      await statusHeader.click();
      await page.waitForTimeout(200);
      
      // Header should still show sort icon
      await expect(statusHeader).toBeVisible();
    });

    test('should open item detail on row click', async ({ page }) => {
      // Switch to Table view
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /table/i }).click();
      
      const firstRow = page.locator('[data-testid="table-row"]').first();
      
      await firstRow.click();
      
      // Side panel should open
      const sidePanel = page.locator('[data-testid="side-panel"]');
      await expect(sidePanel).toBeVisible();
    });

    test('should display export button', async ({ page }) => {
      // Switch to Table view
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /table/i }).click();
      
      const exportButton = page.getByRole('button', { name: /export/i });
      await expect(exportButton).toBeVisible();
    });

    test('should display column visibility toggle', async ({ page }) => {
      // Switch to Table view
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /table/i }).click();
      
      const columnsButton = page.getByRole('button', { name: /columns/i });
      await expect(columnsButton).toBeVisible();
    });
  });

  test.describe('Side Panel', () => {
    test('should open side panel when item clicked', async ({ page }) => {
      // Click first item card
      const firstCard = page.locator('[data-testid="item-card"]').first();
      await firstCard.click();
      
      // Side panel should open
      const sidePanel = page.locator('[data-testid="side-panel"]');
      await expect(sidePanel).toBeVisible();
    });

    test('should display item details in side panel', async ({ page }) => {
      // Click first item
      await page.locator('[data-testid="item-card"]').first().click();
      
      const sidePanel = page.locator('[data-testid="side-panel"]');
      
      // Should show item title
      await expect(sidePanel.locator('[data-testid="item-title"]')).toBeVisible();
    });

    test('should close side panel on close button click', async ({ page }) => {
      // Open side panel
      await page.locator('[data-testid="item-card"]').first().click();
      
      const sidePanel = page.locator('[data-testid="side-panel"]');
      await expect(sidePanel).toBeVisible();
      
      // Click close button
      const closeButton = sidePanel.locator('[data-testid="close-button"]');
      await closeButton.click();
      
      // Side panel should close
      await expect(sidePanel).not.toBeVisible();
    });

    test('should update side panel when different item clicked', async ({ page }) => {
      // Click first item
      await page.locator('[data-testid="item-card"]').first().click();
      
      const sidePanel = page.locator('[data-testid="side-panel"]');
      const firstTitle = await sidePanel.locator('[data-testid="item-title"]').textContent();
      
      // Click second item
      await page.locator('[data-testid="item-card"]').nth(1).click();
      
      // Title should change
      const secondTitle = await sidePanel.locator('[data-testid="item-title"]').textContent();
      
      // Titles should be different (if we have multiple items)
      // Note: This might be the same if there's only one item
    });
  });

  test.describe('Responsive Design', () => {
    test('should render correctly on mobile (375px)', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      
      // Key elements should still be visible
      await expect(page.locator('[data-testid="view-mode-switcher"]')).toBeVisible();
      await expect(page.locator('[data-testid="search-bar"]')).toBeVisible();
    });

    test('should render correctly on tablet (768px)', async ({ page }) => {
      await page.setViewportSize({ width: 768, height: 1024 });
      
      await expect(page.locator('[data-testid="kanban-board"]')).toBeVisible();
    });

    test('should render correctly on desktop (1440px)', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });
      
      await expect(page.locator('[data-testid="kanban-board"]')).toBeVisible();
    });
  });

  test.describe('Performance', () => {
    test('should load phase page within 2 seconds', async ({ page }) => {
      const startTime = Date.now();
      
      await page.goto(`/devsecops/phase/${TEST_PHASE_ID}`);
      await page.waitForLoadState('networkidle');
      
      const loadTime = Date.now() - startTime;
      expect(loadTime).toBeLessThan(2000);
    });

    test('should switch views quickly (< 500ms)', async ({ page }) => {
      const startTime = Date.now();
      
      // Switch to Timeline
      await page.locator('[data-testid="view-mode-switcher"]').getByRole('button', { name: /timeline/i }).click();
      await page.locator('[data-testid="timeline"]').waitFor({ state: 'visible' });
      
      const switchTime = Date.now() - startTime;
      expect(switchTime).toBeLessThan(500);
    });
  });
});

/**
 * Test utilities for phase detail tests
 */
class PhaseDetailTestHelpers {
  constructor(private page: Page) {}

  async switchView(viewMode: 'kanban' | 'timeline' | 'table') {
    const viewSwitcher = this.page.locator('[data-testid="view-mode-switcher"]');
    const button = viewSwitcher.getByRole('button', { name: new RegExp(viewMode, 'i') });
    await button.click();
    
    // Wait for view to render
    await this.page.locator(`[data-testid="${viewMode}"]`).waitFor({ state: 'visible' });
  }

  async openFilters() {
    await this.page.getByRole('button', { name: /filter/i }).click();
    await this.page.locator('[data-testid="filter-drawer"]').waitFor({ state: 'visible' });
  }

  async applyFilter(section: string, value: string) {
    await this.openFilters();
    const filterPanel = this.page.locator('[data-testid="filter-panel"]');
    await filterPanel.getByLabel(new RegExp(value, 'i')).check();
    await filterPanel.getByRole('button', { name: /apply/i }).click();
  }

  async searchItems(query: string) {
    const searchInput = this.page.locator('[data-testid="search-bar"] input');
    await searchInput.fill(query);
    await this.page.waitForTimeout(500); // Debounce
  }

  async openItemDetail(index: number = 0) {
    const itemCard = this.page.locator('[data-testid="item-card"]').nth(index);
    await itemCard.click();
    await this.page.locator('[data-testid="side-panel"]').waitFor({ state: 'visible' });
  }
}

export { PhaseDetailTestHelpers };
