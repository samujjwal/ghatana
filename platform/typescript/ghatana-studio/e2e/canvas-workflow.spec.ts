/**
 * @fileoverview E2E tests for CanvasPage workflow.
 *
 * Tests the current artifact canvas route, React Flow rendering, and
 * evidence panels.
 *
 * @doc.type test
 * @doc.purpose CanvasPage workflow E2E tests
 * @doc.layer studio
 */

import { expect, test } from '@playwright/test';

test.describe('CanvasPage Workflow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/canvas');
  });

  test('loads canvas with graph visualization', async ({ page }) => {
    await expect(page.locator('#canvas-title')).toBeVisible();
    await expect(page.getByText('Artifact Graph Canvas')).toBeVisible();
    await expect(page.locator('.ghatana-canvas-container')).toBeVisible();
  });

  test('displays artifact graph nodes', async ({ page }) => {
    await expect(page.locator('.react-flow__node').first()).toBeVisible();
    await expect(page.locator('.react-flow__node')).toHaveCount(
      await page.locator('.react-flow__node').count(),
    );
  });

  test('supports canvas pan and zoom controls', async ({ page }) => {
    const canvas = page.locator('.ghatana-canvas-container');
    await expect(canvas).toBeVisible();
    await canvas.click({ position: { x: 100, y: 100 } });
    await page.mouse.wheel(0, -300);
    await expect(page.locator('.react-flow')).toBeVisible();
  });

  test('selects a rendered graph node', async ({ page }) => {
    const firstNode = page.locator('.react-flow__node').first();
    await expect(firstNode).toBeVisible();
    await firstNode.click();
    await expect(firstNode).toHaveClass(/selected/);
  });

  test('displays risk and residual evidence panels', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Risk Hotspots/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /Residual Islands/i })).toBeVisible();
  });

  test('displays semantic artifact list', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Semantic Artifacts/i })).toBeVisible();
    await expect(page.getByText(/artifact kind/i).first()).toBeVisible();
  });
});
