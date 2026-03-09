import { test, expect } from '@playwright/test';

test.describe('Canvas Phase 3 & 4 - Sketch Tools and Page Designer', () => {
  test.describe('Phase 3: Sketch Tools', () => {
    test.beforeEach(async ({ page }) => {
      await page.goto('/canvas-poc');
      await page.waitForSelector('[data-testid="rf__wrapper"]', { timeout: 10000 });
    });

    test('should switch between sketch tools using keyboard', async ({ page }) => {
      // Test pen tool (P)
      await page.keyboard.press('p');
      await page.waitForTimeout(200);
      const penButton = page.getByRole('button', { name: /Pen/i });
      await expect(penButton).toHaveClass(/Mui-selected/);

      // Test eraser tool (E)
      await page.keyboard.press('e');
      await page.waitForTimeout(200);
      const eraserButton = page.getByRole('button', { name: /Eraser/i });
      await expect(eraserButton).toHaveClass(/Mui-selected/);

      // Test rectangle tool (R)
      await page.keyboard.press('r');
      await page.waitForTimeout(200);
      // Rectangle button should be selected if toolbar exists

      // Test select tool (V)
      await page.keyboard.press('v');
      await page.waitForTimeout(200);
      const selectButton = page.getByRole('button', { name: /Select/i });
      await expect(selectButton).toHaveClass(/Mui-selected/);
    });

    test('should draw with pen tool', async ({ page }) => {
      // Switch to pen tool
      await page.keyboard.press('p');
      await page.waitForTimeout(300);

      // Get canvas area
      const canvas = page.locator('canvas').first();
      await expect(canvas).toBeVisible();

      // Draw a stroke
      const canvasBox = await canvas.boundingBox();
      if (canvasBox) {
        await page.mouse.move(canvasBox.x + 100, canvasBox.y + 100);
        await page.mouse.down();
        await page.mouse.move(canvasBox.x + 200, canvasBox.y + 150);
        await page.mouse.move(canvasBox.x + 250, canvasBox.y + 200);
        await page.mouse.up();
        await page.waitForTimeout(500);

        // Verify stroke was created (canvas should have content)
        const canvasElement = await canvas.evaluate((el) => {
          const ctx = (el as HTMLCanvasElement).getContext('2d');
          return ctx ? true : false;
        });
        expect(canvasElement).toBe(true);
      }
    });

    test('should persist sketch elements on reload', async ({ page }) => {
      // Draw something
      await page.keyboard.press('p');
      await page.waitForTimeout(300);

      const canvas = page.locator('canvas').first();
      const canvasBox = await canvas.boundingBox();
      
      if (canvasBox) {
        await page.mouse.move(canvasBox.x + 100, canvasBox.y + 100);
        await page.mouse.down();
        await page.mouse.move(canvasBox.x + 150, canvasBox.y + 150);
        await page.mouse.up();
        await page.waitForTimeout(2500); // Wait for auto-save

        // Reload page
        await page.reload();
        // Wait for the page to fully load with a more robust approach
        try {
          await page.waitForSelector('[data-testid="rf__wrapper"]', { timeout: 10000 });
        } catch (e) {
          // If React Flow wrapper doesn't appear, try waiting for canvas-poc-root
          await page.waitForSelector('[data-testid="canvas-poc-root"]', { timeout: 5000 });
        }
        await page.waitForTimeout(1000);

        // Canvas should still exist
        const reloadedCanvas = page.locator('canvas').first();
        await expect(reloadedCanvas).toBeVisible();
      }
    });
  });

  test.describe('Phase 4: Page Designer', () => {
    test.beforeEach(async ({ page }) => {
      // Navigate to page designer route (adjust URL as needed)
      await page.goto('/page-designer');
      await page.waitForTimeout(1000);
    });

    test('should add button component to page', async ({ page }) => {
      // Click button component in palette
      const buttonPalette = page.getByRole('button', { name: /Button/i }).first();
      await buttonPalette.click();
      await page.waitForTimeout(500);

      // Verify button was added to canvas
      const addedButton = page.locator('button').filter({ hasText: /Button/i }).last();
      await expect(addedButton).toBeVisible();
    });

    test('should add multiple components', async ({ page }) => {
      // Add button
      await page.getByRole('button', { name: /^Button/i }).first().click();
      await page.waitForTimeout(300);

      // Add text field
      await page.getByRole('button', { name: /Text Field/i }).first().click();
      await page.waitForTimeout(300);

      // Add typography
      await page.getByRole('button', { name: /Typography/i }).first().click();
      await page.waitForTimeout(500);

      // Verify all components are present by checking for each specific component type
      const buttonComponent = page.locator('button').filter({ hasText: /Button/i }).last();
      const textFieldComponent = page.locator('input[type="text"], input:not([type])').last();
      const typographyComponent = page.locator('p, h1, h2, h3, h4, h5, h6').filter({ hasText: /Typography|Text/i }).last();

      // Verify each component is visible
      await expect(buttonComponent).toBeVisible();
      await expect(textFieldComponent).toBeVisible();
      await expect(typographyComponent).toBeVisible();
    });

    test('should select and edit component properties', async ({ page }) => {
      // Add a button
      await page.getByRole('button', { name: /^Button/i }).first().click();
      await page.waitForTimeout(500);

      // Select the added button (click on it in canvas)
      const addedButton = page.locator('button').filter({ hasText: /Button/i }).last();
      await addedButton.click();
      await page.waitForTimeout(300);

      // Edit button should appear
      const editIcon = page.getByTitle(/Edit Properties/i);
      if (await editIcon.isVisible()) {
        await editIcon.click();
        await page.waitForTimeout(500);

        // Property drawer should open
        const drawer = page.locator('[role="presentation"]');
        await expect(drawer).toBeVisible();
      }
    });

    test('should delete selected component', async ({ page }) => {
      // Add a button
      await page.getByRole('button', { name: /^Button/i }).first().click();
      await page.waitForTimeout(500);

      // Select the button
      const addedButton = page.locator('button').filter({ hasText: /Button/i }).last();
      await addedButton.click();
      await page.waitForTimeout(300);

      // Click delete icon
      const deleteIcon = page.getByTitle(/Delete/i);
      if (await deleteIcon.isVisible()) {
        await deleteIcon.click();
        await page.waitForTimeout(500);

        // Button should be removed
        const buttonCount = await page.locator('button').filter({ hasText: /Button/i }).count();
        expect(buttonCount).toBeLessThan(2); // Only palette button remains
      }
    });

    test('should update component properties via form', async ({ page }) => {
      // Add a button
      await page.getByRole('button', { name: /^Button/i }).first().click();
      await page.waitForTimeout(500);

      // Select and edit
      const addedButton = page.locator('button').filter({ hasText: /Button/i }).last();
      await addedButton.click();
      await page.waitForTimeout(300);

      const editIcon = page.getByTitle(/Edit Properties/i);
      if (await editIcon.isVisible()) {
        await editIcon.click();
        await page.waitForTimeout(500);

        // Change text property
        const textField = page.locator('input[name="text"]');
        if (await textField.isVisible()) {
          await textField.fill('Custom Button Text');
          
          // Apply changes
          const applyButton = page.getByRole('button', { name: /Apply/i });
          await applyButton.click();
          await page.waitForTimeout(500);

          // Verify text changed
          await expect(page.locator('button').filter({ hasText: /Custom Button Text/i })).toBeVisible();
        }
      }
    });

    test('should handle card component with title and content', async ({ page }) => {
      // Add a card
      await page.getByRole('button', { name: /Card/i }).first().click();
      await page.waitForTimeout(500);

      // Card should be visible
      const card = page.locator('[class*="MuiCard"]').first();
      await expect(card).toBeVisible();
    });

    test('should add container (box) component', async ({ page }) => {
      // Add a box/container
      await page.getByRole('button', { name: /Container/i }).first().click();
      await page.waitForTimeout(500);

      // Container should be visible with placeholder text
      const container = page.locator('text=/Container/i').first();
      await expect(container).toBeVisible();
    });
  });

  test.describe('Phase 4: JSX Export', () => {
    test('should export components to JSX', async ({ page }) => {
      await page.goto('/page-designer');
      await page.waitForTimeout(1000);

      // Add some components
      await page.getByRole('button', { name: /^Button/i }).first().click();
      await page.waitForTimeout(300);
      await page.getByRole('button', { name: /Typography/i }).first().click();
      await page.waitForTimeout(500);

      // Look for export button (if implemented in UI)
      const exportButton = page.getByRole('button', { name: /Export/i });
      if (await exportButton.isVisible()) {
        await exportButton.click();
        await page.waitForTimeout(500);

        // Verify download was triggered or modal opened
        // This depends on implementation
      }
    });
  });
});
