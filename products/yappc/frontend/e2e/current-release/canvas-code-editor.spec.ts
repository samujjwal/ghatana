import { expect, test } from '@playwright/test';

/**
 * Canvas Code Editor E2E tests
 *
 * Verifies the CodeEditorCanvas renders correctly when canvas mode is set to
 * 'code' and abstraction level is 'code'. Uses the same API-mocking and
 * localStorage-seeding pattern as other lifecycle/canvas tests so no backend
 * is required.
 */
test.beforeEach(async ({ page }) => {
  // Mock workspace and onboarding APIs so the shell resolves immediately.
  await page.route('**/api/workspaces', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    })
  );
  await page.route('**/api/workspaces/**', (route) =>
    route.fulfill({ status: 404, body: 'Not found' })
  );
  await page.route('**/api/onboarding/status', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ completed: true }),
    })
  );

  await page.addInitScript(() => {
    try {
      // Disable shell overlays that would block navigation
      localStorage.setItem('E2E_DISABLE_OVERLAYS', '1');
      localStorage.setItem('E2E_SIMPLE_PAGES', '1');
      localStorage.setItem('onboarding_complete', '"true"');

      // Pre-set canvas mode to 'code' and level to 'code' so the
      // CodeEditorCanvas renders immediately without toolbar interaction.
      // These are the atomWithStorage keys used by canvasModeAtom and
      // abstractionLevelAtom (toolbarAtom.ts).
      localStorage.setItem('canvas-toolbar-mode', '"code"');
      localStorage.setItem('canvas-toolbar-level', '"code"');

      (window as unknown as { __E2E_TEST_NO_POINTER_BLOCK?: boolean }).__E2E_TEST_NO_POINTER_BLOCK = true;
    } catch {
      // no-op for storage-restricted environments
    }
  });
});

test('canvas code editor mode renders editor surface', async ({ page }) => {
  await page.goto('/p/proj-1/canvas', { waitUntil: 'networkidle' });

  // The CodeEditorCanvas wraps content in data-testid="code-editor-canvas-content"
  await expect(page.getByTestId('code-editor-canvas-content')).toBeVisible();

  // All three mode tabs should be visible
  await expect(page.getByTestId('code-editor-mode-editor')).toBeVisible();
  await expect(page.getByTestId('code-editor-mode-diff')).toBeVisible();
  await expect(page.getByTestId('code-editor-mode-visual')).toBeVisible();
});

test('canvas code editor mode tabs switch correctly', async ({ page }) => {
  await page.goto('/p/proj-1/canvas', { waitUntil: 'networkidle' });

  const editorContent = page.getByTestId('code-editor-canvas-content');
  await expect(editorContent).toBeVisible();

  // Default mode is 'editor' — editor tab is active
  const editorTab = page.getByTestId('code-editor-mode-editor');
  await expect(editorTab).toBeVisible();

  // Switch to diff mode
  const diffTab = page.getByTestId('code-editor-mode-diff');
  await diffTab.click();
  await expect(editorContent).toBeVisible();

  // Switch to visual mode
  const visualTab = page.getByTestId('code-editor-mode-visual');
  await visualTab.click();
  await expect(editorContent).toBeVisible();

  // Switch back to editor mode
  await editorTab.click();
  await expect(editorContent).toBeVisible();
});

test('canvas code editor clear action is available', async ({ page }) => {
  await page.goto('/p/proj-1/canvas', { waitUntil: 'networkidle' });

  await expect(page.getByTestId('code-editor-canvas-content')).toBeVisible();
  // The clear control should be present in the editor surface
  await expect(page.getByTestId('code-editor-clear')).toBeVisible();
});
