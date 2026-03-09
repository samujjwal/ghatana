import { test, expect } from '@playwright/test';
import { setupTest, teardownTest } from './helpers/test-isolation';

/**
 * Preview Route Tests
 * Tests for canvas preview and presentation mode
 */

test.describe('Preview Route', () => {
  test.describe('Preview Mode Activation', () => {
    test('should navigate to preview from canvas', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/canvas' });

      await page.locator('[data-testid="preview-button"]').click();

      await expect(page).toHaveURL(/\/preview$/);

      await teardownTest(page);
    });

    test('should display canvas in read-only mode', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      // Toolbar should be hidden
      await expect(page.locator('[data-testid="canvas-toolbar"]')).toBeHidden();

      // Editing tools should be disabled
      await expect(page.locator('[data-testid="palette"]')).toBeHidden();

      await teardownTest(page);
    });

    test('should show preview controls', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await expect(
        page.locator('[data-testid="preview-controls"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="fullscreen-button"]')
      ).toBeVisible();
      await expect(page.locator('[data-testid="zoom-controls"]')).toBeVisible();
      await expect(page.locator('[data-testid="exit-preview"]')).toBeVisible();

      await teardownTest(page);
    });
  });

  test.describe('Canvas Rendering', () => {
    test('should render all nodes in preview', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      // Verify nodes are visible
      await expect(page.locator('.react-flow__node')).toHaveCountGreaterThan(0);

      await teardownTest(page);
    });

    test('should render all edges in preview', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await expect(page.locator('.react-flow__edge')).toHaveCountGreaterThan(0);

      await teardownTest(page);
    });

    test('should maintain aspect ratio of canvas', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      const canvas = page.locator('[data-testid="react-flow-wrapper"]');
      const box = await canvas.boundingBox();

      expect(box!.width).toBeGreaterThan(0);
      expect(box!.height).toBeGreaterThan(0);

      await teardownTest(page);
    });

    test('should fit canvas to viewport', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await page.locator('[data-testid="fit-view"]').click();

      // All nodes should be visible
      const nodes = await page.locator('.react-flow__node').all();
      for (const node of nodes) {
        await expect(node).toBeInViewport();
      }

      await teardownTest(page);
    });
  });

  test.describe('Fullscreen Mode', () => {
    test('should enter fullscreen on button click', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await page.locator('[data-testid="fullscreen-button"]').click();

      // Verify fullscreen state
      const isFullscreen = await page.evaluate(
        () => !!document.fullscreenElement
      );
      expect(isFullscreen).toBe(true);

      await teardownTest(page);
    });

    test('should exit fullscreen with Escape key', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await page.locator('[data-testid="fullscreen-button"]').click();
      await page.keyboard.press('Escape');

      const isFullscreen = await page.evaluate(
        () => !!document.fullscreenElement
      );
      expect(isFullscreen).toBe(false);

      await teardownTest(page);
    });

    test('should hide UI controls in fullscreen', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await page.locator('[data-testid="fullscreen-button"]').click();

      // Controls should auto-hide after 3 seconds
      await page.waitForTimeout(3500);

      await expect(page.locator('[data-testid="preview-controls"]')).toHaveCSS(
        'opacity',
        '0'
      );

      await teardownTest(page);
    });

    test('should show controls on mouse move in fullscreen', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await page.locator('[data-testid="fullscreen-button"]').click();
      await page.waitForTimeout(3500);

      // Move mouse to show controls
      await page.mouse.move(100, 100);

      await expect(page.locator('[data-testid="preview-controls"]')).toHaveCSS(
        'opacity',
        '1'
      );

      await teardownTest(page);
    });
  });

  test.describe('Zoom Controls', () => {
    test('should zoom in with plus button', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      const initialZoom = await page
        .locator('[data-testid="zoom-level"]')
        .textContent();

      await page.locator('[data-testid="zoom-in"]').click();

      const finalZoom = await page
        .locator('[data-testid="zoom-level"]')
        .textContent();

      expect(parseFloat(finalZoom!)).toBeGreaterThan(parseFloat(initialZoom!));

      await teardownTest(page);
    });

    test('should zoom out with minus button', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      const initialZoom = await page
        .locator('[data-testid="zoom-level"]')
        .textContent();

      await page.locator('[data-testid="zoom-out"]').click();

      const finalZoom = await page
        .locator('[data-testid="zoom-level"]')
        .textContent();

      expect(parseFloat(finalZoom!)).toBeLessThan(parseFloat(initialZoom!));

      await teardownTest(page);
    });

    test('should reset zoom to 100%', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await page.locator('[data-testid="zoom-in"]').click();
      await page.locator('[data-testid="zoom-in"]').click();

      await page.locator('[data-testid="reset-zoom"]').click();

      const zoom = await page
        .locator('[data-testid="zoom-level"]')
        .textContent();
      expect(zoom).toBe('100%');

      await teardownTest(page);
    });
  });

  test.describe('Phase Navigation', () => {
    test('should show phase navigation controls', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await expect(page.locator('[data-testid="phase-nav"]')).toBeVisible();
      await expect(page.locator('[data-testid="prev-phase"]')).toBeVisible();
      await expect(page.locator('[data-testid="next-phase"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should navigate to next phase', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      const initialPhase = await page
        .locator('[data-testid="current-phase"]')
        .textContent();

      await page.locator('[data-testid="next-phase"]').click();

      const finalPhase = await page
        .locator('[data-testid="current-phase"]')
        .textContent();

      expect(finalPhase).not.toBe(initialPhase);

      await teardownTest(page);
    });

    test('should navigate to previous phase', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await page.locator('[data-testid="next-phase"]').click();

      const phaseAfterNext = await page
        .locator('[data-testid="current-phase"]')
        .textContent();

      await page.locator('[data-testid="prev-phase"]').click();

      const phaseAfterPrev = await page
        .locator('[data-testid="current-phase"]')
        .textContent();

      expect(phaseAfterPrev).not.toBe(phaseAfterNext);

      await teardownTest(page);
    });

    test('should navigate with arrow keys', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      const initialPhase = await page
        .locator('[data-testid="current-phase"]')
        .textContent();

      await page.keyboard.press('ArrowRight');

      const finalPhase = await page
        .locator('[data-testid="current-phase"]')
        .textContent();

      expect(finalPhase).not.toBe(initialPhase);

      await teardownTest(page);
    });

    test('should show phase indicator', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await expect(
        page.locator('[data-testid="phase-indicator"]')
      ).toBeVisible();
      await expect(page.locator('[data-testid="phase-indicator"]')).toHaveText(
        /Phase \d+ of \d+/
      );

      await teardownTest(page);
    });
  });

  test.describe('Auto-Play Slideshow', () => {
    test('should show play/pause button', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await expect(
        page.locator('[data-testid="slideshow-play"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should auto-advance phases when playing', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      const initialPhase = await page
        .locator('[data-testid="current-phase"]')
        .textContent();

      await page.locator('[data-testid="slideshow-play"]').click();

      // Wait for auto-advance (default 5 seconds)
      await page.waitForTimeout(5500);

      const finalPhase = await page
        .locator('[data-testid="current-phase"]')
        .textContent();

      expect(finalPhase).not.toBe(initialPhase);

      await teardownTest(page);
    });

    test('should pause slideshow on user interaction', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await page.locator('[data-testid="slideshow-play"]').click();

      // Interact with canvas
      await page.mouse.click(500, 400);

      // Verify paused
      await expect(
        page.locator('[data-testid="slideshow-play"]')
      ).toBeVisible();
      await expect(
        page.locator('[data-testid="slideshow-pause"]')
      ).toBeHidden();

      await teardownTest(page);
    });
  });

  test.describe('Real-time Updates', () => {
    test('should update preview when canvas changes', async ({
      page,
      context,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      const initialNodeCount = await page.locator('.react-flow__node').count();

      // Simulate canvas update via WebSocket
      await page.evaluate(() => {
        window.dispatchEvent(
          new CustomEvent('canvas:node-added', {
            detail: {
              node: {
                id: 'new-node',
                type: 'api',
                position: { x: 800, y: 400 },
              },
            },
          })
        );
      });

      await page.waitForTimeout(500);

      const finalNodeCount = await page.locator('.react-flow__node').count();

      expect(finalNodeCount).toBe(initialNodeCount + 1);

      await teardownTest(page);
    });

    test('should show live indicator when connected', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await expect(
        page.locator('[data-testid="live-indicator"]')
      ).toBeVisible();
      await expect(page.locator('[data-testid="live-indicator"]')).toHaveText(
        /Live/
      );

      await teardownTest(page);
    });
  });

  test.describe('Exit Preview', () => {
    test('should return to canvas on exit', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await page.locator('[data-testid="exit-preview"]').click();

      await expect(page).toHaveURL(/\/canvas$/);

      await teardownTest(page);
    });

    test('should exit with Escape key', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await page.keyboard.press('Escape');

      await expect(page).toHaveURL(/\/canvas$/);

      await teardownTest(page);
    });
  });

  test.describe('Sharing & Export', () => {
    test('should show share button', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await expect(page.locator('[data-testid="share-preview"]')).toBeVisible();

      await teardownTest(page);
    });

    test('should generate shareable link', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      await page.locator('[data-testid="share-preview"]').click();

      await expect(page.locator('[data-testid="share-modal"]')).toBeVisible();

      const shareLink = await page
        .locator('[data-testid="share-link"]')
        .inputValue();
      expect(shareLink).toContain('/preview/');

      await teardownTest(page);
    });

    test('should export preview as PDF', async ({ page }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      const downloadPromise = page.waitForEvent('download');
      await page.locator('[data-testid="export-pdf"]').click();

      const download = await downloadPromise;
      expect(download.suggestedFilename()).toMatch(/preview.*\.pdf$/);

      await teardownTest(page);
    });
  });

  test.describe('Responsive Behavior', () => {
    test('should adapt controls for mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      // Mobile controls should be simplified
      await expect(
        page.locator('[data-testid="mobile-preview-controls"]')
      ).toBeVisible();

      await teardownTest(page);
    });

    test('should support pinch-to-zoom on touch devices', async ({ page }) => {
      await page.setViewportSize({ width: 768, height: 1024 });
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      const initialZoom = await page
        .locator('[data-testid="zoom-level"]')
        .textContent();

      // Simulate pinch gesture
      await page.evaluate(() => {
        const canvas = document.querySelector(
          '[data-testid="react-flow-wrapper"]'
        );
        canvas?.dispatchEvent(
          new WheelEvent('wheel', { deltaY: -100, ctrlKey: true })
        );
      });

      await page.waitForTimeout(300);

      const finalZoom = await page
        .locator('[data-testid="zoom-level"]')
        .textContent();

      expect(finalZoom).not.toBe(initialZoom);

      await teardownTest(page);
    });
  });

  test.describe('Performance', () => {
    test('should load preview in under 2 seconds', async ({ page }) => {
      const startTime = Date.now();

      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });
      await page.waitForLoadState('networkidle');

      const loadTime = Date.now() - startTime;

      expect(loadTime).toBeLessThan(2000);

      await teardownTest(page);
    });

    test('should maintain smooth transitions between phases', async ({
      page,
    }) => {
      await setupTest(page, { url: '/w/ws-1/p/proj-1/preview' });

      // Measure frame rate during transition
      await page.evaluate(() => {
        (window as unknown).transitionFrames = 0;
        let lastTime = performance.now();

        function countFrames(time: number) {
          if (time - lastTime < 1000) {
            (window as unknown).transitionFrames++;
            requestAnimationFrame(countFrames);
          }
        }

        requestAnimationFrame(countFrames);
      });

      await page.locator('[data-testid="next-phase"]').click();
      await page.waitForTimeout(1000);

      const frameCount = await page.evaluate(
        () => (window as unknown).transitionFrames
      );

      // Should maintain ~60fps
      expect(frameCount).toBeGreaterThan(50);

      await teardownTest(page);
    });
  });
});
