/**
 * E2E tests – Progressive Disclosure / Advanced Settings
 *
 * Covers: agent advanced settings accordion hidden by default, toggle reveals
 * advanced fields, settings persist on form save, feature-flagged sections
 * visible only when enabled, project settings advanced metadata toggle.
 *
 * All tests are skipped (`test.skip`) because the corresponding routes may
 * not be deployed in CI. Enable them as each route is wired to the running app.
 */

import { test, expect, type Page } from '@playwright/test';

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:7002';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Navigate to a project's settings page. */
async function gotoProjectSettings(page: Page, projectId = 'demo-project-1'): Promise<void> {
  await page.goto(`${BASE_URL}/app/project/${projectId}/settings`);
  await page.waitForLoadState('networkidle');
}

/** Navigate to a hypothetical agent configuration page. */
async function gotoAgentConfig(page: Page, agentId = 'demo-agent-1'): Promise<void> {
  await page.goto(`${BASE_URL}/app/agents/${agentId}/config`);
  await page.waitForLoadState('networkidle');
}

// ---------------------------------------------------------------------------
// Suite: Project settings progressive disclosure
// ---------------------------------------------------------------------------

test.describe('Project settings – progressive disclosure', () => {
  test.skip(true, 'Route not yet deployed to CI environment');

  test('advanced metadata section is hidden by default', async ({ page }) => {
    await gotoProjectSettings(page);
    const advancedMetadata = page.getByTestId('project-advanced-metadata');
    await expect(advancedMetadata).not.toBeVisible();
  });

  test('clicking toggle reveals advanced metadata', async ({ page }) => {
    await gotoProjectSettings(page);
    const toggle = page.getByTestId('project-advanced-metadata-toggle');
    await toggle.click();
    const advancedMetadata = page.getByTestId('project-advanced-metadata');
    await expect(advancedMetadata).toBeVisible();
  });

  test('clicking toggle again hides advanced metadata', async ({ page }) => {
    await gotoProjectSettings(page);
    const toggle = page.getByTestId('project-advanced-metadata-toggle');
    await toggle.click(); // open
    await toggle.click(); // close
    const advancedMetadata = page.getByTestId('project-advanced-metadata');
    await expect(advancedMetadata).not.toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Suite: Agent advanced settings accordion
// ---------------------------------------------------------------------------

test.describe('Agent advanced settings – progressive disclosure', () => {
  test.skip(true, 'Agent config route not yet deployed to CI environment');

  test('advanced settings accordion is collapsed on first visit', async ({ page }) => {
    await gotoAgentConfig(page);
    // Model selector is inside the accordion — should not be visible
    const modelSelect = page.getByTestId('agent-model-select');
    await expect(modelSelect).not.toBeVisible();
  });

  test('clicking accordion reveals advanced fields', async ({ page }) => {
    await gotoAgentConfig(page);
    const accordionToggle = page.getByText('Advanced settings');
    await accordionToggle.click();

    await expect(page.getByTestId('agent-model-select')).toBeVisible();
    await expect(page.getByTestId('agent-temperature-input')).toBeVisible();
    await expect(page.getByTestId('agent-max-tokens-input')).toBeVisible();
    await expect(page.getByTestId('agent-timeout-input')).toBeVisible();
  });

  test('basic settings (name, description) are always visible', async ({ page }) => {
    await gotoAgentConfig(page);
    await expect(page.getByTestId('agent-name-input')).toBeVisible();
    await expect(page.getByTestId('agent-description-input')).toBeVisible();
  });

  test('changing model and saving persists the selection', async ({ page }) => {
    await gotoAgentConfig(page);
    const accordionToggle = page.getByText('Advanced settings');
    await accordionToggle.click();

    const modelSelect = page.getByTestId('agent-model-select');
    await modelSelect.selectOption('claude-3-5-sonnet');

    // Submit the form
    const saveButton = page.getByRole('button', { name: /save/i });
    await saveButton.click();
    await page.waitForLoadState('networkidle');

    // Reload and verify persistence
    await page.reload();
    await page.waitForLoadState('networkidle');
    await accordionToggle.click();
    await expect(page.getByTestId('agent-model-select')).toHaveValue('claude-3-5-sonnet');
  });

  test('planning section absent when AGENT_ORCHESTRATION flag is off', async ({ page }) => {
    // Ensure flag is off (default dev environment)
    await gotoAgentConfig(page);
    const accordionToggle = page.getByText('Advanced settings');
    await accordionToggle.click();

    await expect(page.getByTestId('agent-planning-section')).not.toBeVisible();
  });

  test('memory mode radio group visible after accordion open', async ({ page }) => {
    await gotoAgentConfig(page);
    const accordionToggle = page.getByText('Advanced settings');
    await accordionToggle.click();

    await expect(page.getByTestId('agent-memory-none')).toBeVisible();
    await expect(page.getByTestId('agent-memory-session')).toBeVisible();
    await expect(page.getByTestId('agent-memory-persistent')).toBeVisible();
  });

  test('selecting memory mode updates aria-checked state', async ({ page }) => {
    await gotoAgentConfig(page);
    const accordionToggle = page.getByText('Advanced settings');
    await accordionToggle.click();

    const persistentBtn = page.getByTestId('agent-memory-persistent');
    await persistentBtn.click();
    await expect(persistentBtn).toHaveAttribute('aria-checked', 'true');

    const noneBtn = page.getByTestId('agent-memory-none');
    await expect(noneBtn).toHaveAttribute('aria-checked', 'false');
  });
});
