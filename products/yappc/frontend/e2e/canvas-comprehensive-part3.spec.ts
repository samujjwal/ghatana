import { test, expect, type Page } from '@playwright/test';
import { setupTest, teardownTest } from './helpers/test-isolation';

/**
 * Canvas Comprehensive Tests - Part 3
 * Export/Import, Collaboration, Keyboard Shortcuts, Performance
 */

test.describe('Canvas - Advanced Features & Performance', () => {
  // ============================================
  // SECTION 7: EXPORT & IMPORT (5 tests)
  // ============================================

  test.describe('Export & Import', () => {
    test('should export canvas as PNG', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add some nodes
      await addNode(page, 'api', { x: 300, y: 200 });
      await addNode(page, 'data', { x: 500, y: 300 });

      // Start download
      const downloadPromise = page.waitForEvent('download');
      await page.locator('[data-testid="toolbar-export"]').click();
      await page.locator('[data-testid="export-png"]').click();

      const download = await downloadPromise;

      // Verify file downloaded
      expect(download.suggestedFilename()).toMatch(/canvas.*\.png$/);

      await teardownTest(page);
    });

    test('should export canvas as SVG', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await addNode(page, 'api', { x: 300, y: 200 });
      await addNode(page, 'data', { x: 500, y: 300 });

      const downloadPromise = page.waitForEvent('download');
      await page.locator('[data-testid="toolbar-export"]').click();
      await page.locator('[data-testid="export-svg"]').click();

      const download = await downloadPromise;

      expect(download.suggestedFilename()).toMatch(/canvas.*\.svg$/);

      await teardownTest(page);
    });

    test('should export canvas as JSON', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await addNode(page, 'api', { x: 300, y: 200 });
      await addNode(page, 'data', { x: 500, y: 300 });

      // Create edge between nodes
      await page
        .locator('[data-node-id="node-1"]')
        .locator('.source-handle')
        .dragTo(
          page.locator('[data-node-id="node-2"]').locator('.target-handle')
        );

      const downloadPromise = page.waitForEvent('download');
      await page.locator('[data-testid="toolbar-export"]').click();
      await page.locator('[data-testid="export-json"]').click();

      const download = await downloadPromise;

      expect(download.suggestedFilename()).toMatch(/canvas.*\.json$/);

      // Verify JSON content
      const path = await download.path();
      const fs = require('fs');
      const content = fs.readFileSync(path, 'utf8');
      const json = JSON.parse(content);

      expect(json.nodes).toHaveLength(2);
      expect(json.edges).toHaveLength(1);

      await teardownTest(page);
    });

    test('should import canvas from JSON', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Create JSON data to import
      const canvasData = {
        nodes: [
          {
            id: 'node-1',
            type: 'api',
            position: { x: 100, y: 100 },
            data: { label: 'API 1' },
          },
          {
            id: 'node-2',
            type: 'data',
            position: { x: 400, y: 200 },
            data: { label: 'DB' },
          },
        ],
        edges: [{ id: 'edge-1', source: 'node-1', target: 'node-2' }],
      };

      // Upload JSON file
      await page.locator('[data-testid="toolbar-import"]').click();

      const fileChooserPromise = page.waitForEvent('filechooser');
      await page.locator('[data-testid="import-json"]').click();
      const fileChooser = await fileChooserPromise;

      // Create temp file
      const fs = require('fs');
      const path = require('path');
      const tmpFile = path.join(__dirname, 'temp-canvas.json');
      fs.writeFileSync(tmpFile, JSON.stringify(canvasData));

      await fileChooser.setFiles(tmpFile);

      // Wait for import to complete
      await page.waitForTimeout(500);

      // Verify nodes imported
      await expect(page.locator('.react-flow__node')).toHaveCount(2);
      await expect(page.locator('.react-flow__edge')).toHaveCount(1);

      // Cleanup
      fs.unlinkSync(tmpFile);

      await teardownTest(page);
    });

    test('should handle invalid JSON import gracefully', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await page.locator('[data-testid="toolbar-import"]').click();

      const fileChooserPromise = page.waitForEvent('filechooser');
      await page.locator('[data-testid="import-json"]').click();
      const fileChooser = await fileChooserPromise;

      // Create invalid JSON file
      const fs = require('fs');
      const path = require('path');
      const tmpFile = path.join(__dirname, 'temp-invalid.json');
      fs.writeFileSync(tmpFile, '{ invalid json }');

      await fileChooser.setFiles(tmpFile);

      // Verify error message shown
      await expect(page.locator('[data-testid="error-toast"]')).toBeVisible();
      await expect(page.locator('[data-testid="error-toast"]')).toHaveText(
        /Invalid JSON/
      );

      // Cleanup
      fs.unlinkSync(tmpFile);

      await teardownTest(page);
    });
  });

  // ============================================
  // SECTION 8: COLLABORATION (7 tests)
  // ============================================

  test.describe('Collaboration', () => {
    test('should show other users cursors', async ({ page, context }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Simulate another user connecting (via WebSocket)
      await page.evaluate(() => {
        window.dispatchEvent(
          new CustomEvent('collaboration:user-joined', {
            detail: {
              userId: 'user-2',
              name: 'Alice',
              cursor: { x: 500, y: 300 },
            },
          })
        );
      });

      // Verify cursor shown
      await expect(
        page.locator('[data-testid="remote-cursor-user-2"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="remote-cursor-user-2"]')
      ).toHaveText(/Alice/);

      await teardownTest(page);
    });

    test('should show other users selections', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await addNode(page, 'api', { x: 300, y: 200 });

      // Simulate another user selecting node
      await page.evaluate(() => {
        window.dispatchEvent(
          new CustomEvent('collaboration:node-selected', {
            detail: { userId: 'user-2', name: 'Bob', nodeId: 'node-1' },
          })
        );
      });

      // Verify selection indicator shown with user color
      await expect(
        page.locator('[data-node-id="node-1"]').locator('.remote-selection')
      ).toBeVisible();
      await expect(
        page.locator('[data-node-id="node-1"]').locator('.remote-selection')
      ).toHaveAttribute('data-user', 'user-2');

      await teardownTest(page);
    });

    test('should sync node additions from other users', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Simulate another user adding node
      await page.evaluate(() => {
        window.dispatchEvent(
          new CustomEvent('collaboration:node-added', {
            detail: {
              userId: 'user-2',
              node: {
                id: 'node-remote-1',
                type: 'data',
                position: { x: 500, y: 400 },
                data: { label: 'Remote DB' },
              },
            },
          })
        );
      });

      // Wait for sync
      await page.waitForTimeout(200);

      // Verify node appears
      await expect(
        page.locator('[data-node-id="node-remote-1"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should sync node movements from other users', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await addNode(page, 'api', { x: 300, y: 200 });

      const initialBox = await page
        .locator('[data-node-id="node-1"]')
        .boundingBox();

      // Simulate another user moving node
      await page.evaluate(() => {
        window.dispatchEvent(
          new CustomEvent('collaboration:node-moved', {
            detail: {
              userId: 'user-2',
              nodeId: 'node-1',
              position: { x: 600, y: 500 },
            },
          })
        );
      });

      await page.waitForTimeout(200);

      const finalBox = await page
        .locator('[data-node-id="node-1"]')
        .boundingBox();

      // Verify position changed
      expect(finalBox?.x).not.toBe(initialBox?.x);
      expect(finalBox?.y).not.toBe(initialBox?.y);

      await teardownTest(page);
    });

    test('should handle simultaneous edits without conflicts', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await addNode(page, 'api', { x: 300, y: 200 });
      await addNode(page, 'data', { x: 500, y: 300 });

      // Both users edit different nodes simultaneously
      await Promise.all([
        // Local user moves node-1
        page
          .locator('[data-node-id="node-1"]')
          .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
            targetPosition: { x: 400, y: 300 },
          }),
        // Remote user moves node-2 (simulated)
        page.evaluate(() => {
          window.dispatchEvent(
            new CustomEvent('collaboration:node-moved', {
              detail: {
                userId: 'user-2',
                nodeId: 'node-2',
                position: { x: 700, y: 400 },
              },
            })
          );
        }),
      ]);

      await page.waitForTimeout(300);

      // Verify both changes applied
      const node1 = await page.locator('[data-node-id="node-1"]').boundingBox();
      const node2 = await page.locator('[data-node-id="node-2"]').boundingBox();

      expect(node1?.x).toBeCloseTo(400, 0);
      expect(node2?.x).toBeCloseTo(700, 0);

      await teardownTest(page);
    });

    test('should show conflict resolution when editing same node', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await addNode(page, 'api', { x: 300, y: 200 });

      // Select node locally
      await page.locator('[data-node-id="node-1"]').click();

      // Remote user also selects same node
      await page.evaluate(() => {
        window.dispatchEvent(
          new CustomEvent('collaboration:node-selected', {
            detail: { userId: 'user-2', name: 'Charlie', nodeId: 'node-1' },
          })
        );
      });

      // Verify conflict indicator shown
      await expect(
        page.locator('[data-testid="conflict-indicator"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="conflict-indicator"]')
      ).toHaveText(/Charlie is also editing/);

      await teardownTest(page);
    });

    test('should reconnect WebSocket after disconnection', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Simulate WebSocket disconnection
      await page.evaluate(() => {
        window.dispatchEvent(new Event('collaboration:disconnected'));
      });

      // Verify reconnecting indicator
      await expect(
        page.locator('[data-testid="connection-status"]')
      ).toHaveText(/Reconnecting/);

      // Simulate reconnection
      await page.waitForTimeout(1000);
      await page.evaluate(() => {
        window.dispatchEvent(new Event('collaboration:connected'));
      });

      // Verify connected status
      await expect(
        page.locator('[data-testid="connection-status"]')
      ).toHaveText(/Connected/);

      await teardownTest(page);
    });
  });

  // ============================================
  // SECTION 9: KEYBOARD SHORTCUTS (10 tests)
  // ============================================

  test.describe('Keyboard Shortcuts', () => {
    test('should undo with Ctrl+Z / Cmd+Z', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await addNode(page, 'api', { x: 300, y: 200 });
      await expect(page.locator('.react-flow__node')).toHaveCount(1);

      // Undo
      await page.keyboard.press('Control+z');

      await expect(page.locator('.react-flow__node')).toHaveCount(0);

      await teardownTest(page);
    });

    test('should redo with Ctrl+Shift+Z / Cmd+Shift+Z', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await addNode(page, 'api', { x: 300, y: 200 });
      await page.keyboard.press('Control+z'); // Undo
      await expect(page.locator('.react-flow__node')).toHaveCount(0);

      // Redo
      await page.keyboard.press('Control+Shift+z');

      await expect(page.locator('.react-flow__node')).toHaveCount(1);

      await teardownTest(page);
    });

    test('should select all with Ctrl+A / Cmd+A', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await addNode(page, 'api', { x: 300, y: 200 });
      await addNode(page, 'data', { x: 500, y: 300 });
      await addNode(page, 'component', { x: 700, y: 400 });

      // Select all
      await page.keyboard.press('Control+a');

      await expect(page.locator('.react-flow__node.selected')).toHaveCount(3);

      await teardownTest(page);
    });

    test('should delete selected nodes with Delete/Backspace', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await addNode(page, 'api', { x: 300, y: 200 });
      await addNode(page, 'data', { x: 500, y: 300 });

      await page.keyboard.press('Control+a');
      await page.keyboard.press('Delete');

      await expect(page.locator('.react-flow__node')).toHaveCount(0);

      await teardownTest(page);
    });

    test('should group with Ctrl+G / Cmd+G', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await addNode(page, 'api', { x: 300, y: 200 });
      await addNode(page, 'data', { x: 500, y: 300 });

      await page.keyboard.press('Control+a');
      await page.keyboard.press('Control+g');

      // Verify group created
      await expect(page.locator('[data-node-type="group"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should ungroup with Ctrl+Shift+G / Cmd+Shift+G', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await addNode(page, 'api', { x: 300, y: 200 });
      await addNode(page, 'data', { x: 500, y: 300 });

      await page.keyboard.press('Control+a');
      await page.keyboard.press('Control+g'); // Group

      await page.locator('[data-node-type="group"]').click();
      await page.keyboard.press('Control+Shift+g'); // Ungroup

      // Verify nodes ungrouped
      await expect(page.locator('[data-node-type="group"]')).toHaveCount(0);
      await expect(page.locator('.react-flow__node')).toHaveCount(2);

      await teardownTest(page);
    });

    test('should open command palette with Ctrl+K / Cmd+K', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await page.keyboard.press('Control+k');

      await expect(
        page.locator('[data-testid="command-palette"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should zoom in with Ctrl+Plus / Cmd+Plus', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      const initialZoom = await page
        .locator('[data-testid="zoom-level"]')
        .textContent();

      await page.keyboard.press('Control+=');

      const finalZoom = await page
        .locator('[data-testid="zoom-level"]')
        .textContent();

      expect(parseFloat(finalZoom!)).toBeGreaterThan(parseFloat(initialZoom!));

      await teardownTest(page);
    });

    test('should zoom out with Ctrl+Minus / Cmd+Minus', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      const initialZoom = await page
        .locator('[data-testid="zoom-level"]')
        .textContent();

      await page.keyboard.press('Control+-');

      const finalZoom = await page
        .locator('[data-testid="zoom-level"]')
        .textContent();

      expect(parseFloat(finalZoom!)).toBeLessThan(parseFloat(initialZoom!));

      await teardownTest(page);
    });

    test('should reset zoom with Ctrl+0 / Cmd+0', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Zoom in first
      await page.keyboard.press('Control+=');
      await page.keyboard.press('Control+=');

      // Reset zoom
      await page.keyboard.press('Control+0');

      const zoom = await page
        .locator('[data-testid="zoom-level"]')
        .textContent();

      expect(zoom).toBe('100%');

      await teardownTest(page);
    });
  });

  // ============================================
  // SECTION 10: PERFORMANCE (5 tests)
  // ============================================

  test.describe('Performance', () => {
    test('should handle 1000 nodes without lag', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      const startTime = Date.now();

      // Add 1000 nodes
      await page.evaluate(() => {
        const nodes = [];
        for (let i = 0; i < 1000; i++) {
          nodes.push({
            id: `node-${i}`,
            type: 'api',
            position: {
              x: (i % 50) * 150,
              y: Math.floor(i / 50) * 150,
            },
            data: { label: `Node ${i}` },
          });
        }

        window.dispatchEvent(
          new CustomEvent('canvas:bulk-add-nodes', {
            detail: { nodes },
          })
        );
      });

      await page.waitForTimeout(1000);

      const loadTime = Date.now() - startTime;

      // Verify all nodes rendered
      await expect(page.locator('.react-flow__node')).toHaveCount(1000);

      // Load time should be under 5 seconds
      expect(loadTime).toBeLessThan(5000);

      await teardownTest(page);
    });

    test('should load canvas in under 3 seconds', async ({ page }) => {
      const startTime = Date.now();

      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Wait for canvas to be fully loaded
      await page.waitForSelector('[data-testid="react-flow-wrapper"]');
      await page.waitForLoadState('networkidle');

      const loadTime = Date.now() - startTime;

      expect(loadTime).toBeLessThan(3000);

      await teardownTest(page);
    });

    test('should maintain 60fps while panning', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Add nodes to make panning visible
      for (let i = 0; i < 50; i++) {
        await addNode(page, 'api', {
          x: (i % 10) * 200,
          y: Math.floor(i / 10) * 200,
        });
      }

      // Start performance measurement
      await page.evaluate(() => {
        (window as unknown).perfData = { frames: 0, dropped: 0 };
        let lastTime = performance.now();

        function measureFrame(time: number) {
          const delta = time - lastTime;
          (window as unknown).perfData.frames++;
          if (delta > 16.67) {
            // >16.67ms = dropped frame at 60fps
            (window as unknown).perfData.dropped++;
          }
          lastTime = time;
          requestAnimationFrame(measureFrame);
        }

        requestAnimationFrame(measureFrame);
      });

      // Pan canvas
      await page.mouse.move(500, 400);
      await page.mouse.down();
      await page.mouse.move(300, 200, { steps: 50 });
      await page.mouse.up();

      await page.waitForTimeout(500);

      // Check performance
      const perfData = await page.evaluate(() => (window as unknown).perfData);
      const dropRate = perfData.dropped / perfData.frames;

      // Less than 10% dropped frames
      expect(dropRate).toBeLessThan(0.1);

      await teardownTest(page);
    });

    test('should maintain 60fps while zooming', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      for (let i = 0; i < 50; i++) {
        await addNode(page, 'api', {
          x: (i % 10) * 200,
          y: Math.floor(i / 10) * 200,
        });
      }

      await page.evaluate(() => {
        (window as unknown).perfData = { frames: 0, dropped: 0 };
        let lastTime = performance.now();

        function measureFrame(time: number) {
          const delta = time - lastTime;
          (window as unknown).perfData.frames++;
          if (delta > 16.67) {
            (window as unknown).perfData.dropped++;
          }
          lastTime = time;
          requestAnimationFrame(measureFrame);
        }

        requestAnimationFrame(measureFrame);
      });

      // Zoom in and out
      for (let i = 0; i < 10; i++) {
        await page.keyboard.press('Control+=');
        await page.waitForTimeout(50);
      }
      for (let i = 0; i < 10; i++) {
        await page.keyboard.press('Control+-');
        await page.waitForTimeout(50);
      }

      const perfData = await page.evaluate(() => (window as unknown).perfData);
      const dropRate = perfData.dropped / perfData.frames;

      expect(dropRate).toBeLessThan(0.1);

      await teardownTest(page);
    });

    test('should debounce auto-save to prevent excessive API calls', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      // Track API calls
      let saveCallCount = 0;
      await page.route('**/api/canvas/save', (route) => {
        saveCallCount++;
        route.fulfill({ status: 200, body: JSON.stringify({ success: true }) });
      });

      // Make rapid changes
      for (let i = 0; i < 20; i++) {
        await addNode(page, 'api', { x: i * 50, y: 200 });
        await page.waitForTimeout(50);
      }

      // Wait for debounce to settle
      await page.waitForTimeout(2000);

      // Should have significantly fewer save calls than changes
      expect(saveCallCount).toBeLessThan(10);
      expect(saveCallCount).toBeGreaterThan(0);

      await teardownTest(page);
    });
  });
});

// Helper functions
function addNode(page: Page, type: string, position: { x: number; y: number }) {
  return page
    .locator(`[data-testid="palette-item-${type}"]`)
    .dragTo(page.locator('[data-testid="react-flow-wrapper"]'), {
      targetPosition: position,
    });
}
