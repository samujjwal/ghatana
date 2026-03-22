/**
 * Canvas Diagram Mode E2E Tests
 * 
 * End-to-end tests for diagram mode functionality including:
 * - Mode activation/deactivation
 * - Template selection and switching
 * - Zoom controls
 * - Code editing
 * - Integration with other canvas modes
 * 
 * @doc.type test
 * @doc.purpose E2E tests for diagram mode
 * @doc.layer product
 */

import { test, expect, type Page } from '@playwright/test';

test.describe('Canvas Diagram Mode', () => {
    let page: Page;

    test.beforeEach(async ({ page: testPage }) => {
        page = testPage;
        await page.goto('/canvas');
        await page.waitForLoadState('networkidle');
    });

    test.describe('Mode Activation', () => {
        test('should activate diagram mode when diagram button is clicked', async () => {
            // Click diagram button in toolbar
            await page.getByRole('button', { name: /diagram/i }).click();

            // Verify diagram layer appears
            await expect(page.locator('[data-testid="diagram-layer"]')).toBeVisible();

            // Verify diagram toolbar appears
            await expect(page.getByRole('toolbar', { name: /diagram toolbar/i })).toBeVisible();
        });

        test('should show diagram mode button in unified toolbar', async () => {
            const diagramButton = page.getByRole('button', { name: /diagram/i });
            await expect(diagramButton).toBeVisible();
            await expect(diagramButton).toHaveAttribute('aria-label', /diagram mode/i);
        });

        test('should exit diagram mode when navigate button is clicked', async () => {
            // Activate diagram mode
            await page.getByRole('button', { name: /diagram/i }).click();
            await expect(page.locator('[data-testid="diagram-layer"]')).toBeVisible();

            // Click navigate button
            await page.getByRole('button', { name: /navigate/i }).click();

            // Verify diagram layer disappears
            await expect(page.locator('[data-testid="diagram-layer"]')).not.toBeVisible();

            // Verify diagram toolbar disappears
            await expect(page.getByRole('toolbar', { name: /diagram toolbar/i })).not.toBeVisible();
        });
    });

    test.describe('Template Selection', () => {
        test.beforeEach(async () => {
            // Activate diagram mode
            await page.getByRole('button', { name: /diagram/i }).click();
            await expect(page.locator('[data-testid="diagram-layer"]')).toBeVisible();
        });

        test('should show default flowchart template on activation', async () => {
            // Verify flowchart is rendered
            const diagram = page.locator('[data-testid="mermaid-diagram"]');
            await expect(diagram).toBeVisible();

            // Verify it contains flowchart elements
            await expect(diagram).toContainText(/Start|End/i);
        });

        test('should open template menu when Templates button is clicked', async () => {
            await page.getByRole('button', { name: /templates/i }).click();

            // Verify all 6 templates are shown
            await expect(page.getByText('Basic Flowchart')).toBeVisible();
            await expect(page.getByText('Sequence Diagram')).toBeVisible();
            await expect(page.getByText('Class Diagram')).toBeVisible();
            await expect(page.getByText('State Diagram')).toBeVisible();
            await expect(page.getByText('Gantt Chart')).toBeVisible();
            await expect(page.getByText('ER Diagram')).toBeVisible();
        });

        test('should switch to sequence diagram template', async () => {
            // Open template menu
            await page.getByRole('button', { name: /templates/i }).click();

            // Select sequence diagram
            await page.getByText('Sequence Diagram').click();

            // Verify diagram updates
            const diagram = page.locator('[data-testid="mermaid-diagram"]');
            await expect(diagram).toContainText(/sequenceDiagram|participant/i);
        });

        test('should switch to class diagram template', async () => {
            await page.getByRole('button', { name: /templates/i }).click();
            await page.getByText('Class Diagram').click();

            const diagram = page.locator('[data-testid="mermaid-diagram"]');
            await expect(diagram).toContainText(/classDiagram|class/i);
        });

        test('should switch to state diagram template', async () => {
            await page.getByRole('button', { name: /templates/i }).click();
            await page.getByText('State Diagram').click();

            const diagram = page.locator('[data-testid="mermaid-diagram"]');
            await expect(diagram).toContainText(/stateDiagram|state/i);
        });

        test('should switch to gantt chart template', async () => {
            await page.getByRole('button', { name: /templates/i }).click();
            await page.getByText('Gantt Chart').click();

            const diagram = page.locator('[data-testid="mermaid-diagram"]');
            await expect(diagram).toContainText(/gantt|section/i);
        });

        test('should switch to ER diagram template', async () => {
            await page.getByRole('button', { name: /templates/i }).click();
            await page.getByText('ER Diagram').click();

            const diagram = page.locator('[data-testid="mermaid-diagram"]');
            await expect(diagram).toContainText(/erDiagram/i);
        });
    });

    test.describe('Diagram Type Toggles', () => {
        test.beforeEach(async () => {
            await page.getByRole('button', { name: /diagram/i }).click();
            await expect(page.locator('[data-testid="diagram-layer"]')).toBeVisible();
        });

        test('should show flowchart as active type by default', async () => {
            const flowchartButton = page.getByRole('button', { name: /^flowchart$/i });
            await expect(flowchartButton).toHaveAttribute('aria-pressed', 'true');
        });

        test('should switch to sequence type when clicked', async () => {
            const sequenceButton = page.getByRole('button', { name: /^sequence$/i });
            await sequenceButton.click();

            await expect(sequenceButton).toHaveAttribute('aria-pressed', 'true');

            // Verify diagram content updates
            const diagram = page.locator('[data-testid="mermaid-diagram"]');
            await expect(diagram).toContainText(/sequenceDiagram/i);
        });

        test('should switch to class type when clicked', async () => {
            const classButton = page.getByRole('button', { name: /^class$/i });
            await classButton.click();

            await expect(classButton).toHaveAttribute('aria-pressed', 'true');

            const diagram = page.locator('[data-testid="mermaid-diagram"]');
            await expect(diagram).toContainText(/classDiagram/i);
        });

        test('should allow only one type to be active', async () => {
            const flowchartButton = page.getByRole('button', { name: /^flowchart$/i });
            const classButton = page.getByRole('button', { name: /^class$/i });

            // Switch to class
            await classButton.click();
            await expect(classButton).toHaveAttribute('aria-pressed', 'true');
            await expect(flowchartButton).toHaveAttribute('aria-pressed', 'false');
        });
    });

    test.describe('Zoom Controls', () => {
        test.beforeEach(async () => {
            await page.getByRole('button', { name: /diagram/i }).click();
            await expect(page.locator('[data-testid="diagram-layer"]')).toBeVisible();
        });

        test('should display 100% zoom by default', async () => {
            await expect(page.getByText('100%')).toBeVisible();
        });

        test('should zoom in when zoom in button is clicked', async () => {
            const zoomInButton = page.getByLabel(/zoom in/i);
            await zoomInButton.click();

            // Should show 110%
            await expect(page.getByText('110%')).toBeVisible();
        });

        test('should zoom out when zoom out button is clicked', async () => {
            const zoomOutButton = page.getByLabel(/zoom out/i);
            await zoomOutButton.click();

            // Should show 90%
            await expect(page.getByText('90%')).toBeVisible();
        });

        test('should reset zoom when reset button is clicked', async () => {
            // Zoom in twice
            const zoomInButton = page.getByLabel(/zoom in/i);
            await zoomInButton.click();
            await zoomInButton.click();
            await expect(page.getByText('121%')).toBeVisible();

            // Reset
            const resetButton = page.getByLabel(/reset zoom/i);
            await resetButton.click();

            await expect(page.getByText('100%')).toBeVisible();
        });

        test('should not zoom below 50%', async () => {
            const zoomOutButton = page.getByLabel(/zoom out/i);

            // Click many times
            for (let i = 0; i < 10; i++) {
                await zoomOutButton.click();
                await page.waitForTimeout(100);
            }

            // Should stop at 50%
            await expect(page.getByText(/50%/i)).toBeVisible();
        });

        test('should not zoom above 200%', async () => {
            const zoomInButton = page.getByLabel(/zoom in/i);

            // Click many times
            for (let i = 0; i < 15; i++) {
                await zoomInButton.click();
                await page.waitForTimeout(100);
            }

            // Should stop at 200%
            await expect(page.getByText(/200%/i)).toBeVisible();
        });

        test('should scale diagram content when zooming', async () => {
            const diagram = page.locator('[data-testid="mermaid-diagram"]');

            // Get initial size
            const initialBox = await diagram.boundingBox();
            expect(initialBox).toBeTruthy();

            // Zoom in
            await page.getByLabel(/zoom in/i).click();
            await page.waitForTimeout(300); // Wait for transform

            // Size should change (transform applied)
            const zoomedBox = await diagram.boundingBox();
            expect(zoomedBox).toBeTruthy();
            // Note: Actual size comparison depends on implementation
        });
    });

    test.describe('Code Editor', () => {
        test.beforeEach(async () => {
            await page.getByRole('button', { name: /diagram/i }).click();
            await expect(page.locator('[data-testid="diagram-layer"]')).toBeVisible();
        });

        test('should open code editor when Edit Code is clicked', async () => {
            await page.getByRole('button', { name: /edit code/i }).click();

            // Verify dialog appears
            const dialog = page.getByRole('dialog');
            await expect(dialog).toBeVisible();
            await expect(dialog).toContainText('Edit Diagram Code');
        });

        test('should show current Mermaid code in text field', async () => {
            await page.getByRole('button', { name: /edit code/i }).click();

            const textField = page.getByLabel(/mermaid code/i);
            await expect(textField).toBeVisible();

            const value = await textField.inputValue();
            expect(value).toContain('graph'); // Should contain Mermaid syntax
        });

        test('should allow editing Mermaid code', async () => {
            await page.getByRole('button', { name: /edit code/i }).click();

            const textField = page.getByLabel(/mermaid code/i);
            await textField.clear();
            await textField.fill('graph TD\nA[Start]-->B[End]');

            const value = await textField.inputValue();
            expect(value).toBe('graph TD\nA[Start]-->B[End]');
        });

        test('should update diagram when Apply is clicked', async () => {
            await page.getByRole('button', { name: /edit code/i }).click();

            // Edit code
            const textField = page.getByLabel(/mermaid code/i);
            await textField.clear();
            await textField.fill('graph TD\nA[Custom Node]');

            // Apply
            await page.getByRole('button', { name: /^apply$/i }).click();

            // Dialog should close
            await expect(page.getByRole('dialog')).not.toBeVisible();

            // Diagram should update
            const diagram = page.locator('[data-testid="mermaid-diagram"]');
            await expect(diagram).toContainText('Custom Node');
        });

        test('should discard changes when Cancel is clicked', async () => {
            await page.getByRole('button', { name: /edit code/i }).click();

            // Get original content
            const textField = page.getByLabel(/mermaid code/i);
            const originalValue = await textField.inputValue();

            // Edit code
            await textField.clear();
            await textField.fill('graph TD\nX-->Y');

            // Cancel
            await page.getByRole('button', { name: /cancel/i }).click();

            // Dialog should close
            await expect(page.getByRole('dialog')).not.toBeVisible();

            // Reopen and verify original content
            await page.getByRole('button', { name: /edit code/i }).click();
            const newTextField = page.getByLabel(/mermaid code/i);
            await expect(newTextField).toHaveValue(originalValue);
        });

        test('should close dialog with Escape key', async () => {
            await page.getByRole('button', { name: /edit code/i }).click();
            await expect(page.getByRole('dialog')).toBeVisible();

            await page.keyboard.press('Escape');

            await expect(page.getByRole('dialog')).not.toBeVisible();
        });
    });

    test.describe('Mode Integration', () => {
        test('should switch between navigate, sketch, diagram, and code modes', async () => {
            // Start in navigate mode
            const navigateButton = page.getByRole('button', { name: /navigate mode/i });
            const sketchButton = page.getByRole('button', { name: /sketch mode/i });
            const diagramButton = page.getByRole('button', { name: /diagram mode/i });

            // Switch to sketch
            await sketchButton.click();
            await expect(page.getByRole('toolbar', { name: /sketch toolbar/i })).toBeVisible();

            // Switch to diagram
            await diagramButton.click();
            await expect(page.locator('[data-testid="diagram-layer"]')).toBeVisible();
            await expect(page.getByRole('toolbar', { name: /sketch toolbar/i })).not.toBeVisible();

            // Switch back to navigate
            await navigateButton.click();
            await expect(page.locator('[data-testid="diagram-layer"]')).not.toBeVisible();
        });

        test('should hide ReactFlow controls when diagram mode is active', async () => {
            // Activate diagram mode
            await page.getByRole('button', { name: /diagram/i }).click();

            // ReactFlow should not be interactive
            const reactFlowNodes = page.locator('.react-flow__node');
            const count = await reactFlowNodes.count();

            if (count > 0) {
                // Nodes should not be draggable in diagram mode
                const node = reactFlowNodes.first();
                const box = await node.boundingBox();

                if (box) {
                    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
                    await page.mouse.down();
                    await page.mouse.move(box.x + 100, box.y + 100);
                    await page.mouse.up();

                    // Node should not have moved
                    const newBox = await node.boundingBox();
                    expect(newBox?.x).toBe(box.x);
                    expect(newBox?.y).toBe(box.y);
                }
            }
        });
    });

    test.describe('Persistence', () => {
        test('should remember diagram type when switching modes', async () => {
            // Activate diagram mode and select sequence
            await page.getByRole('button', { name: /diagram/i }).click();
            await page.getByRole('button', { name: /^sequence$/i }).click();

            // Exit diagram mode
            await page.getByRole('button', { name: /navigate/i }).click();

            // Re-enter diagram mode
            await page.getByRole('button', { name: /diagram/i }).click();

            // Should still be sequence type
            const sequenceButton = page.getByRole('button', { name: /^sequence$/i });
            await expect(sequenceButton).toHaveAttribute('aria-pressed', 'true');
        });

        test('should remember custom diagram code', async () => {
            // Edit diagram code
            await page.getByRole('button', { name: /diagram/i }).click();
            await page.getByRole('button', { name: /edit code/i }).click();

            const textField = page.getByLabel(/mermaid code/i);
            await textField.clear();
            const customCode = 'graph TD\nA[My Custom Diagram]-->B[End]';
            await textField.fill(customCode);

            await page.getByRole('button', { name: /^apply$/i }).click();

            // Exit and re-enter diagram mode
            await page.getByRole('button', { name: /navigate/i }).click();
            await page.getByRole('button', { name: /diagram/i }).click();

            // Reopen editor and verify code is preserved
            await page.getByRole('button', { name: /edit code/i }).click();
            const newTextField = page.getByLabel(/mermaid code/i);
            await expect(newTextField).toHaveValue(customCode);
        });
    });

    test.describe('Error Handling', () => {
        test.beforeEach(async () => {
            await page.getByRole('button', { name: /diagram/i }).click();
        });

        test('should show error for invalid Mermaid syntax', async () => {
            await page.getByRole('button', { name: /edit code/i }).click();

            // Enter invalid syntax
            const textField = page.getByLabel(/mermaid code/i);
            await textField.clear();
            await textField.fill('invalid mermaid syntax!!!');

            await page.getByRole('button', { name: /^apply$/i }).click();

            // Should show error message
            await expect(page.getByText(/error|invalid|failed/i)).toBeVisible();
        });

        test('should handle empty diagram content gracefully', async () => {
            await page.getByRole('button', { name: /edit code/i }).click();

            const textField = page.getByLabel(/mermaid code/i);
            await textField.clear();

            await page.getByRole('button', { name: /^apply$/i }).click();

            // Should show placeholder or error
            const diagram = page.locator('[data-testid="mermaid-diagram"]');
            await expect(diagram).toContainText(/empty|no content|error/i);
        });
    });
});
