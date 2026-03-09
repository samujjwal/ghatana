/**
 * E2E Tests for Canvas Integration
 * 
 * Tests for sketch mode, code associations, and unified canvas interactions
 * 
 * @doc.type test
 * @doc.purpose E2E canvas integration tests
 * @doc.layer product
 * @doc.pattern E2E Test
 */

import { test, expect } from '@playwright/test';

test.describe('Canvas Integration', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/canvas');
        await page.waitForLoadState('networkidle');
    });

    test('sketch mode activates drawing layer', async ({ page }) => {
        // Click sketch mode button
        await page.click('[aria-label="Sketch"]');

        // Verify sketch toolbar appears
        await expect(page.locator('[data-testid="sketch-toolbar"]')).toBeVisible();

        // Verify ReactFlow dims (reduced opacity)
        const reactFlow = page.locator('.react-flow');
        const opacity = await reactFlow.evaluate((el) =>
            window.getComputedStyle(el).opacity
        );
        expect(parseFloat(opacity)).toBeLessThan(1);

        // Verify Konva layer is mounted
        await expect(page.locator('canvas.konvajs-content')).toBeVisible();
    });

    test('can draw freehand with pen tool', async ({ page }) => {
        // Activate sketch mode
        await page.click('[aria-label="Sketch"]');

        // Select pen tool
        await page.click('[aria-label="pen tool"]');

        // Draw on canvas
        const canvas = page.locator('canvas.konvajs-content');
        await canvas.hover();
        await page.mouse.down();
        await page.mouse.move(100, 100);
        await page.mouse.move(200, 150);
        await page.mouse.move(150, 200);
        await page.mouse.up();

        // Verify stroke was created (check canvas data)
        const hasContent = await canvas.evaluate((el) => {
            const ctx = (el as HTMLCanvasElement).getContext('2d');
            if (!ctx) return false;
            const imageData = ctx.getImageData(0, 0, el.width, el.height);
            // Check if any pixel is non-transparent
            return imageData.data.some((_, i) => i % 4 === 3 && imageData.data[i] > 0);
        });

        expect(hasContent).toBe(true);
    });

    test('can draw rectangle shape', async ({ page }) => {
        await page.click('[aria-label="Sketch"]');
        await page.click('[aria-label="rectangle tool"]');

        const canvas = page.locator('canvas.konvajs-content');
        await canvas.click({ position: { x: 100, y: 100 } });
        await page.mouse.move(200, 200);
        await canvas.click({ position: { x: 200, y: 200 } });

        // Verify rectangle shape exists in Konva layer
        const hasShape = await page.evaluate(() => {
            const stage = (window as unknown).__konvaStage;
            if (!stage) return false;
            const layer = stage.getLayers()[0];
            const shapes = layer.getChildren();
            return shapes.some((shape: unknown) => shape.className === 'Rect');
        });

        expect(hasShape).toBe(true);
    });

    test('color picker changes stroke color', async ({ page }) => {
        await page.click('[aria-label="Sketch"]');

        // Open color picker
        await page.click('[aria-label="color picker"]');

        // Select red color
        await page.click('button[style*="rgb(244, 67, 54)"]');

        // Draw a stroke
        const canvas = page.locator('canvas.konvajs-content');
        await canvas.hover();
        await page.mouse.down();
        await page.mouse.move(100, 100);
        await page.mouse.up();

        // Verify stroke color is red
        const strokeColor = await page.evaluate(() => {
            const stage = (window as unknown).__konvaStage;
            const layer = stage?.getLayers()[0];
            const stroke = layer?.getChildren()[0];
            return stroke?.stroke();
        });

        expect(strokeColor).toContain('244');
    });

    test('stroke width slider adjusts line thickness', async ({ page }) => {
        await page.click('[aria-label="Sketch"]');

        // Adjust stroke width slider to 10px
        await page.locator('input[type="range"]').fill('10');

        // Draw a stroke
        const canvas = page.locator('canvas.konvajs-content');
        await canvas.hover();
        await page.mouse.down();
        await page.mouse.move(100, 100);
        await page.mouse.up();

        // Verify stroke width
        const strokeWidth = await page.evaluate(() => {
            const stage = (window as unknown).__konvaStage;
            const layer = stage?.getLayers()[0];
            const stroke = layer?.getChildren()[0];
            return stroke?.strokeWidth();
        });

        expect(strokeWidth).toBe(10);
    });

    test('switching back to navigate mode hides sketch layer', async ({ page }) => {
        // Activate sketch mode
        await page.click('[aria-label="Sketch"]');
        await expect(page.locator('[data-testid="sketch-toolbar"]')).toBeVisible();

        // Switch back to navigate mode
        await page.click('[aria-label="Navigate"]');

        // Verify sketch toolbar is hidden
        await expect(page.locator('[data-testid="sketch-toolbar"]')).not.toBeVisible();

        // Verify ReactFlow is fully opaque
        const reactFlow = page.locator('.react-flow');
        const opacity = await reactFlow.evaluate((el) =>
            window.getComputedStyle(el).opacity
        );
        expect(parseFloat(opacity)).toBe(1);
    });

    test('code association: right-click shows link code menu', async ({ page }) => {
        // Wait for artifacts to load
        await page.waitForSelector('[data-testid="artifact-node"]');

        // Right-click on an artifact node
        await page.locator('[data-testid="artifact-node"]').first().click({ button: 'right' });

        // Verify context menu appears with link options
        await expect(page.locator('text=Link Code Implementation')).toBeVisible();
        await expect(page.locator('text=Link Test Case')).toBeVisible();
        await expect(page.locator('text=Link Documentation')).toBeVisible();
        await expect(page.locator('text=Link Mock')).toBeVisible();
    });

    test('code badge appears on nodes with linked code', async ({ page }) => {
        // Mock artifact with code associations
        await page.route('**/api/artifacts/*/code-associations', async (route) => {
            await route.fulfill({
                status: 200,
                body: JSON.stringify([
                    {
                        id: 'assoc-1',
                        artifactId: 'art-1',
                        codeArtifactId: 'code-1',
                        relationship: 'IMPLEMENTATION',
                        codeArtifact: {
                            id: 'code-1',
                            title: 'UserService.ts',
                            content: 'export class UserService { ... }',
                            type: 'CODE',
                        },
                    },
                ]),
            });
        });

        await page.reload();

        // Verify code badge appears
        await expect(page.locator('[data-testid="code-badge"]')).toBeVisible();

        // Verify badge shows count
        const badgeText = await page.locator('[data-testid="code-badge"]').textContent();
        expect(badgeText).toContain('1');
    });

    test('clicking code badge opens preview popover', async ({ page }) => {
        // Mock code associations
        await page.route('**/api/artifacts/*/code-associations', async (route) => {
            await route.fulfill({
                status: 200,
                body: JSON.stringify([
                    {
                        id: 'assoc-1',
                        artifactId: 'art-1',
                        codeArtifactId: 'code-1',
                        relationship: 'IMPLEMENTATION',
                        codeArtifact: {
                            id: 'code-1',
                            title: 'UserService.ts',
                            content: 'export class UserService {\n  async getUser() { ... }\n}',
                            format: 'typescript',
                            type: 'CODE',
                        },
                    },
                ]),
            });
        });

        await page.reload();

        // Click code badge
        await page.locator('[data-testid="code-badge"]').click();

        // Verify popover appears
        await expect(page.locator('text=Linked Code')).toBeVisible();
        await expect(page.locator('text=UserService.ts')).toBeVisible();

        // Verify code preview shows content
        await expect(page.locator('text=export class UserService')).toBeVisible();

        // Verify relationship chip
        await expect(page.locator('text=IMPLEMENTATION')).toBeVisible();
    });

    test('open full editor button navigates to code', async ({ page }) => {
        // Mock code associations
        await page.route('**/api/artifacts/*/code-associations', async (route) => {
            await route.fulfill({
                status: 200,
                body: JSON.stringify([
                    {
                        id: 'assoc-1',
                        codeArtifactId: 'code-1',
                        codeArtifact: {
                            id: 'code-1',
                            title: 'UserService.ts',
                            content: 'export class UserService {}',
                            type: 'CODE',
                        },
                    },
                ]),
            });
        });

        await page.reload();
        await page.locator('[data-testid="code-badge"]').click();

        // Click "Open Full Editor"
        const navigationPromise = page.waitForNavigation();
        await page.click('text=Open Full Editor');
        await navigationPromise;

        // Verify navigation to code editor
        expect(page.url()).toContain('code-1');
    });

    test('delete association removes it from list', async ({ page }) => {
        let associationDeleted = false;

        // Mock GET associations
        await page.route('**/api/artifacts/*/code-associations', async (route) => {
            if (associationDeleted) {
                await route.fulfill({ status: 200, body: JSON.stringify([]) });
            } else {
                await route.fulfill({
                    status: 200,
                    body: JSON.stringify([
                        {
                            id: 'assoc-1',
                            codeArtifact: { id: 'code-1', title: 'Test.ts', content: '...', type: 'TEST' },
                        },
                    ]),
                });
            }
        });

        // Mock DELETE association
        await page.route('**/api/code-associations/*', async (route) => {
            if (route.request().method() === 'DELETE') {
                associationDeleted = true;
                await route.fulfill({ status: 200, body: JSON.stringify({ success: true }) });
            }
        });

        await page.reload();

        // Open popover and delete
        await page.locator('[data-testid="code-badge"]').click();
        await page.click('[title="Remove association"]');

        // Verify popover closes (no associations left)
        await expect(page.locator('text=Linked Code')).not.toBeVisible();

        // Verify badge disappears
        await expect(page.locator('[data-testid="code-badge"]')).not.toBeVisible();
    });

    test('keyboard shortcuts work in sketch mode', async ({ page }) => {
        await page.click('[aria-label="Sketch"]');

        // Press 'p' for pen tool
        await page.keyboard.press('p');
        const penSelected = await page.locator('[value="pen"]').isChecked();
        expect(penSelected).toBe(true);

        // Press 'r' for rectangle tool
        await page.keyboard.press('r');
        const rectSelected = await page.locator('[value="rect"]').isChecked();
        expect(rectSelected).toBe(true);

        // Press 'Escape' to exit sketch mode
        await page.keyboard.press('Escape');
        await expect(page.locator('[data-testid="sketch-toolbar"]')).not.toBeVisible();
    });
});
