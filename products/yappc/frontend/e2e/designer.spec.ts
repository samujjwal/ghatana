/**
 * E2E Test Suite: Designer Component
 * 
 * Comprehensive end-to-end tests for Designer functionality covering:
 * - Design creation and editing
 * - Component palette interactions
 * - Properties panel management
 * - Multi-page navigation
 * - Layer management
 * - Collaboration features
 */

import { test, expect, Page } from '@playwright/test';

const STORYBOOK_URL = 'http://localhost:6006';
const DESIGNER_STORY = `${STORYBOOK_URL}/iframe.html?id=designer--basic&viewMode=story`;
const DESIGNER_MULTI_PAGE = `${STORYBOOK_URL}/iframe.html?id=designer--multi-page&viewMode=story`;

/**
 * Suite 1: Designer Component Palette
 */
test.describe('Designer Component Palette', () => {
  test('should display component palette', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const palette = page.locator('[data-testid="component-palette"]');
    await expect(palette).toBeVisible();
  });

  test('should search components in palette', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const searchInput = page.locator('[data-testid="palette-search"]');
    
    await searchInput.fill('Button');
    
    const results = page.locator('[data-testid^="palette-component-"]');
    const count = await results.count();
    expect(count).toBeGreaterThan(0);
  });

  test('should drag component to canvas', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const component = page.locator('[data-testid="palette-component-button"]').first();
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Drag component to canvas
    await component.dragTo(canvas);
    
    // Verify component added
    const addedComponent = canvas.locator('[data-testid^="component-instance-"]');
    await expect(addedComponent).toBeVisible();
  });

  test('should filter components by category', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const categoryFilter = page.locator('[data-testid="category-filter-inputs"]');
    
    await categoryFilter.click();
    
    const componentsShown = page.locator('[data-testid^="palette-component-"]');
    const count = await componentsShown.count();
    
    expect(count).toBeGreaterThan(0);
  });

  test('should show component preview on hover', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const component = page.locator('[data-testid="palette-component-button"]').first();
    
    await component.hover();
    
    const preview = page.locator('[data-testid="component-preview"]');
    await expect(preview).toBeVisible();
  });
});

/**
 * Suite 2: Designer Properties Panel
 */
test.describe('Designer Properties Panel', () => {
  test('should display properties panel', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const propertiesPanel = page.locator('[data-testid="properties-panel"]');
    await expect(propertiesPanel).toBeVisible();
  });

  test('should update component style properties', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add component
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    await palette.dragTo(canvas);
    
    const component = canvas.locator('[data-testid^="component-instance-"]').first();
    await component.click();
    
    // Update width
    const widthInput = page.locator('[data-testid="property-width"]');
    await widthInput.clear();
    await widthInput.fill('300');
    await widthInput.press('Enter');
    
    // Verify property updated
    const style = await component.getAttribute('style');
    expect(style).toContain('width');
  });

  test('should update component text content', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add text component
    const palette = page.locator('[data-testid="palette-component-text"]').first();
    await palette.dragTo(canvas);
    
    const component = canvas.locator('[data-testid^="component-instance-"]').first();
    await component.click();
    
    // Update text
    const textInput = page.locator('[data-testid="property-text"]');
    await textInput.clear();
    await textInput.fill('Hello Designer');
    await textInput.press('Enter');
    
    // Verify text updated
    const text = await component.textContent();
    expect(text).toContain('Hello Designer');
  });

  test('should change component color', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add component
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    await palette.dragTo(canvas);
    
    const component = canvas.locator('[data-testid^="component-instance-"]').first();
    await component.click();
    
    // Open color picker
    const colorButton = page.locator('[data-testid="property-color"]');
    await colorButton.click();
    
    // Select color
    const colorOption = page.locator('[data-testid="color-red"]');
    await colorOption.click();
    
    // Verify color changed
    const bgColor = await component.evaluate((el) => {
      return window.getComputedStyle(el).backgroundColor;
    });
    
    expect(bgColor).toBeTruthy();
  });
});

/**
 * Suite 3: Designer Layers Panel
 */
test.describe('Designer Layers Panel', () => {
  test('should display layers panel', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const layersPanel = page.locator('[data-testid="layers-panel"]');
    await expect(layersPanel).toBeVisible();
  });

  test('should add layers as components are added', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add 3 components
    for (let i = 0; i < 3; i++) {
      const palette = page.locator('[data-testid="palette-component-button"]').nth(0);
      await palette.dragTo(canvas);
    }
    
    // Verify layers created
    const layers = page.locator('[data-testid^="layer-"]');
    await expect(layers).toHaveCount(3);
  });

  test('should select component by clicking layer', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add components
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    await palette.dragTo(canvas);
    await palette.dragTo(canvas);
    
    // Click layer in layers panel
    const layer = page.locator('[data-testid^="layer-"]').nth(0);
    await layer.click();
    
    // Verify component selected on canvas
    const selectedComponent = canvas.locator('[data-testid^="component-instance-"].selected');
    await expect(selectedComponent).toBeVisible();
  });

  test('should reorder layers via drag', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add 2 components
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    await palette.dragTo(canvas);
    await palette.dragTo(canvas);
    
    // Get initial order
    const layers = page.locator('[data-testid^="layer-"]');
    const firstLayer = layers.nth(0);
    const secondLayer = layers.nth(1);
    
    const firstText = await firstLayer.textContent();
    
    // Drag to reorder
    await firstLayer.dragTo(secondLayer);
    
    // Verify order changed
    const newFirstText = await firstLayer.textContent();
    expect(newFirstText).not.toBe(firstText);
  });

  test('should toggle layer visibility', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add component
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    await palette.dragTo(canvas);
    
    const layer = page.locator('[data-testid^="layer-"]').first();
    const visibilityToggle = layer.locator('[data-testid="layer-visibility-toggle"]');
    
    // Toggle visibility
    await visibilityToggle.click();
    
    // Verify component hidden
    const component = canvas.locator('[data-testid^="component-instance-"]').first();
    await expect(component).not.toBeVisible();
    
    // Toggle back
    await visibilityToggle.click();
    await expect(component).toBeVisible();
  });
});

/**
 * Suite 4: Designer Multi-Page Navigation
 */
test.describe('Designer Multi-Page Navigation', () => {
  test('should display page list', async ({ page }) => {
    await page.goto(DESIGNER_MULTI_PAGE);
    const pageList = page.locator('[data-testid="page-list"]');
    await expect(pageList).toBeVisible();
  });

  test('should create new page', async ({ page }) => {
    await page.goto(DESIGNER_MULTI_PAGE);
    const addPageBtn = page.locator('[data-testid="add-page-button"]');
    
    // Get initial page count
    const pages = page.locator('[data-testid^="page-item-"]');
    const initialCount = await pages.count();
    
    // Add page
    await addPageBtn.click();
    
    // Verify page added
    await expect(pages).toHaveCount(initialCount + 1);
  });

  test('should switch between pages', async ({ page }) => {
    await page.goto(DESIGNER_MULTI_PAGE);
    const pages = page.locator('[data-testid^="page-item-"]');
    
    // Get page names
    const page1 = pages.nth(0);
    const page2 = pages.nth(1);
    
    const page1Name = await page1.textContent();
    const page2Name = await page2.textContent();
    
    // Switch to page 2
    await page2.click();
    
    // Verify page changed
    const activePage = page.locator('[data-testid^="page-item-"].active');
    const activeName = await activePage.textContent();
    
    expect(activeName).toBe(page2Name);
  });

  test('should delete page', async ({ page }) => {
    await page.goto(DESIGNER_MULTI_PAGE);
    const pages = page.locator('[data-testid^="page-item-"]');
    const initialCount = await pages.count();
    
    // Right-click page for context menu
    const lastPage = pages.nth(initialCount - 1);
    await lastPage.click({ button: 'right' });
    
    // Click delete
    const deleteOption = page.locator('[data-testid="context-delete"]');
    await deleteOption.click();
    
    // Confirm deletion
    const confirmBtn = page.locator('[data-testid="confirm-delete"]');
    await confirmBtn.click();
    
    // Verify page deleted
    await expect(pages).toHaveCount(initialCount - 1);
  });

  test('should rename page', async ({ page }) => {
    await page.goto(DESIGNER_MULTI_PAGE);
    const pageItem = page.locator('[data-testid^="page-item-"]').first();
    
    // Double-click to rename
    await pageItem.dblclick();
    
    // Type new name
    const nameInput = pageItem.locator('input');
    await nameInput.fill('New Page Name');
    await nameInput.press('Enter');
    
    // Verify name changed
    const newName = await pageItem.textContent();
    expect(newName).toContain('New Page Name');
  });
});

/**
 * Suite 5: Designer Keyboard Shortcuts
 */
test.describe('Designer Keyboard Shortcuts', () => {
  test('should duplicate component with Ctrl+D', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add component
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    await palette.dragTo(canvas);
    
    const component = canvas.locator('[data-testid^="component-instance-"]').first();
    await component.click();
    
    // Duplicate
    await page.keyboard.press('Control+D');
    
    // Verify duplicated
    const components = canvas.locator('[data-testid^="component-instance-"]');
    await expect(components).toHaveCount(2);
  });

  test('should delete component with Delete key', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add component
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    await palette.dragTo(canvas);
    
    const component = canvas.locator('[data-testid^="component-instance-"]').first();
    await component.click();
    
    // Delete
    await page.keyboard.press('Delete');
    
    // Verify deleted
    const components = canvas.locator('[data-testid^="component-instance-"]');
    await expect(components).toHaveCount(0);
  });

  test('should select all with Ctrl+A', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add 3 components
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    for (let i = 0; i < 3; i++) {
      await palette.dragTo(canvas);
    }
    
    // Select all
    await canvas.click();
    await page.keyboard.press('Control+A');
    
    // Verify all selected
    const selectedComponents = canvas.locator('[data-testid^="component-instance-"].selected');
    await expect(selectedComponents).toHaveCount(3);
  });
});

/**
 * Suite 6: Designer Component Master
 */
test.describe('Designer Component Master', () => {
  test('should create master component', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add component
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    await palette.dragTo(canvas);
    
    const component = canvas.locator('[data-testid^="component-instance-"]').first();
    await component.click();
    
    // Create master
    await page.keyboard.press('Control+K');
    
    // Verify master created
    const masterComponent = canvas.locator('[data-testid^="component-instance-"].master');
    await expect(masterComponent).toBeVisible();
  });

  test('should create component instance from master', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add and make master
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    await palette.dragTo(canvas);
    
    const component = canvas.locator('[data-testid^="component-instance-"]').first();
    await component.click();
    await page.keyboard.press('Control+K');
    
    // Duplicate (creates instance)
    await page.keyboard.press('Control+D');
    
    // Both should exist
    const instances = canvas.locator('[data-testid^="component-instance-"]');
    await expect(instances).toHaveCount(2);
  });
});

/**
 * Suite 7: Designer Alignment & Distribution
 */
test.describe('Designer Alignment & Distribution', () => {
  test('should align components to left', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add 2 components
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    await palette.dragTo(canvas);
    await palette.dragTo(canvas);
    
    // Get positions before
    const components = canvas.locator('[data-testid^="component-instance-"]');
    const box1Before = await components.nth(0).boundingBox();
    const box2Before = await components.nth(1).boundingBox();
    
    // Select both
    await components.nth(0).click();
    await components.nth(1).click({ modifiers: ['Shift'] });
    
    // Align left
    const alignLeftBtn = page.locator('[data-testid="align-left"]');
    await alignLeftBtn.click();
    
    // Verify alignment
    const box1After = await components.nth(0).boundingBox();
    const box2After = await components.nth(1).boundingBox();
    
    expect(box1After?.x).toBe(box2After?.x);
  });

  test('should distribute components vertically', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add 3 components
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    for (let i = 0; i < 3; i++) {
      await palette.dragTo(canvas);
    }
    
    // Select all
    await canvas.click();
    await page.keyboard.press('Control+A');
    
    // Distribute
    const distributeBtn = page.locator('[data-testid="distribute-vertical"]');
    await distributeBtn.click();
    
    // Verify distribution
    const components = canvas.locator('[data-testid^="component-instance-"]');
    const boxes = [];
    for (let i = 0; i < 3; i++) {
      boxes.push(await components.nth(i).boundingBox());
    }
    
    const spacing1 = boxes[1]?.y! - (boxes[0]?.y! + boxes[0]?.height!);
    const spacing2 = boxes[2]?.y! - (boxes[1]?.y! + boxes[1]?.height!);
    
    expect(Math.abs(spacing1 - spacing2)).toBeLessThan(5); // Allow 5px variance
  });
});

/**
 * Suite 8: Designer Responsive Design
 */
test.describe('Designer Responsive Design', () => {
  test('should show breakpoint controls', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const breakpointControls = page.locator('[data-testid="breakpoint-controls"]');
    await expect(breakpointControls).toBeVisible();
  });

  test('should switch between breakpoints', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const breakpointSelect = page.locator('[data-testid="breakpoint-select"]');
    
    // Switch to tablet
    await breakpointSelect.selectOption('tablet');
    
    // Verify viewport changed
    const viewport = page.locator('[data-testid="designer-viewport"]');
    const width = await viewport.evaluate((el) => el.clientWidth);
    
    expect(width).toBeLessThan(1024); // Tablet width
  });
});

/**
 * Suite 9: Designer Undo/Redo
 */
test.describe('Designer Undo/Redo', () => {
  test('should undo component addition', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add component
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    await palette.dragTo(canvas);
    
    let components = canvas.locator('[data-testid^="component-instance-"]');
    await expect(components).toHaveCount(1);
    
    // Undo
    await page.keyboard.press('Control+Z');
    
    components = canvas.locator('[data-testid^="component-instance-"]');
    await expect(components).toHaveCount(0);
  });

  test('should redo after undo', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    const canvas = page.locator('[data-testid="designer-canvas"]');
    
    // Add, undo, redo
    const palette = page.locator('[data-testid="palette-component-button"]').first();
    await palette.dragTo(canvas);
    await page.keyboard.press('Control+Z');
    await page.keyboard.press('Control+Y');
    
    const components = canvas.locator('[data-testid^="component-instance-"]');
    await expect(components).toHaveCount(1);
  });
});

/**
 * Suite 10: Designer Export/Download
 */
test.describe('Designer Export/Download', () => {
  test('should export design as PNG', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    
    const exportBtn = page.locator('[data-testid="export-button"]');
    await exportBtn.click();
    
    // Start waiting for download
    const downloadPromise = page.waitForEvent('download');
    
    const exportPngBtn = page.locator('[data-testid="export-png"]');
    await exportPngBtn.click();
    
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toContain('.png');
  });

  test('should export design as JSON', async ({ page }) => {
    await page.goto(DESIGNER_STORY);
    
    const exportBtn = page.locator('[data-testid="export-button"]');
    await exportBtn.click();
    
    // Start waiting for download
    const downloadPromise = page.waitForEvent('download');
    
    const exportJsonBtn = page.locator('[data-testid="export-json"]');
    await exportJsonBtn.click();
    
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toContain('.json');
  });
});
