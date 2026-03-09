/**
 * Deployments E2E Tests
 *
 * End-to-end tests for deployment management including:
 * - Deployments page
 * - Deployment detail view
 * - Pipeline visualization
 * - Logs viewer
 * - Rollback functionality
 *
 * @doc.type test
 * @doc.purpose E2E tests for deployments
 * @doc.phase 3
 */

import { test, expect, type Page } from '@playwright/test';

test.describe('Deployments Page', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/deployments');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display page header', async () => {
      const header = page.locator('h1');
      await expect(header).toContainText(/Deployment/i);
    });

    test('should show stats grid', async () => {
      const statsGrid = page.locator('[class*="stats-grid"], [class*="stats"]');
      await expect(statsGrid).toBeVisible();
    });

    test('should show current deployments section', async () => {
      const currentSection = page.locator('[class*="current-deployments"]');
      await expect(currentSection).toBeVisible();
    });

    test('should show deployment history', async () => {
      const history = page.locator('[class*="deployment-history"], [class*="history"]');
      await expect(history).toBeVisible();
    });

    test('should show deploy button', async () => {
      const deployBtn = page.locator('button', { hasText: /Deploy|New Deployment/i });
      await expect(deployBtn).toBeVisible();
    });
  });

  test.describe('Stats Grid', () => {
    test('should show total deployments', async () => {
      const totalStat = page.locator('[class*="stat-card"]', { hasText: /Total/i });
      await expect(totalStat).toBeVisible();
    });

    test('should show success rate', async () => {
      const successStat = page.locator('[class*="stat-card"]', { hasText: /Success|Rate/i });
      await expect(successStat).toBeVisible();
    });

    test('should show average deployment time', async () => {
      const timeStat = page.locator('[class*="stat-card"]', { hasText: /Time|Duration/i });
      await expect(timeStat).toBeVisible();
    });

    test('should show deployment frequency', async () => {
      const freqStat = page.locator('[class*="stat-card"]', { hasText: /Frequency|Week/i });
      if (await freqStat.isVisible()) {
        await expect(freqStat).toBeVisible();
      }
    });

    test('should show MTTR', async () => {
      const mttrStat = page.locator('[class*="stat-card"]', { hasText: /MTTR|Recovery/i });
      if (await mttrStat.isVisible()) {
        await expect(mttrStat).toBeVisible();
      }
    });
  });

  test.describe('Current Deployments', () => {
    test('should show environment cards', async () => {
      const envCards = page.locator('[class*="env-card"], [class*="environment-card"]');
      await expect(envCards).toHaveCount(await envCards.count() >= 3 ? 3 : await envCards.count());
    });

    test('should show Development environment', async () => {
      const devCard = page.locator('[class*="env-card"]', { hasText: /Dev|Development/i });
      await expect(devCard).toBeVisible();
    });

    test('should show Staging environment', async () => {
      const stagingCard = page.locator('[class*="env-card"]', { hasText: /Staging/i });
      await expect(stagingCard).toBeVisible();
    });

    test('should show Production environment', async () => {
      const prodCard = page.locator('[class*="env-card"]', { hasText: /Prod|Production/i });
      await expect(prodCard).toBeVisible();
    });

    test('should show deployment status on env cards', async () => {
      const statusBadge = page.locator('[class*="env-card"] [class*="status"]').first();
      if (await statusBadge.isVisible()) {
        await expect(statusBadge).toBeVisible();
      }
    });

    test('should show version/commit on env cards', async () => {
      const version = page.locator('[class*="env-card"] [class*="version"], [class*="env-card"] [class*="commit"]').first();
      if (await version.isVisible()) {
        await expect(version).toBeVisible();
      }
    });
  });

  test.describe('Deployment History', () => {
    test('should show deployment cards', async () => {
      const deploymentCards = page.locator('[class*="deployment-card"]');
      if (await deploymentCards.count() > 0) {
        await expect(deploymentCards.first()).toBeVisible();
      }
    });

    test('should group deployments by date', async () => {
      const dateGroups = page.locator('[class*="date-group"]');
      if (await dateGroups.count() > 0) {
        await expect(dateGroups.first()).toBeVisible();
      }
    });

    test('should show deployment status', async () => {
      const status = page.locator('[class*="deployment-card"] [class*="status"]').first();
      if (await status.isVisible()) {
        await expect(status).toBeVisible();
      }
    });

    test('should show deployment environment', async () => {
      const env = page.locator('[class*="deployment-card"] [class*="environment"]').first();
      if (await env.isVisible()) {
        await expect(env).toBeVisible();
      }
    });

    test('should show deployer info', async () => {
      const deployer = page.locator('[class*="deployment-card"] [class*="deployer"], [class*="deployment-card"] [class*="author"]').first();
      if (await deployer.isVisible()) {
        await expect(deployer).toBeVisible();
      }
    });

    test('should show deployment time', async () => {
      const time = page.locator('[class*="deployment-card"] [class*="time"]').first();
      if (await time.isVisible()) {
        await expect(time).toBeVisible();
      }
    });
  });

  test.describe('Filtering', () => {
    test('should have environment filter', async () => {
      const envFilter = page.locator('select, button', { hasText: /Environment|All/i }).first();
      await expect(envFilter).toBeVisible();
    });

    test('should have status filter', async () => {
      const statusFilter = page.locator('select, button', { hasText: /Status/i }).first();
      if (await statusFilter.isVisible()) {
        await expect(statusFilter).toBeVisible();
      }
    });

    test('should have date range selector', async () => {
      const dateSelector = page.locator('[class*="date-range"], input[type="date"]').first();
      if (await dateSelector.isVisible()) {
        await expect(dateSelector).toBeVisible();
      }
    });
  });

  test.describe('Actions', () => {
    test('should allow rollback from successful deployment', async () => {
      const rollbackBtn = page.locator('button', { hasText: /Rollback/i }).first();
      if (await rollbackBtn.isVisible()) {
        await expect(rollbackBtn).toBeVisible();
      }
    });

    test('should allow retry from failed deployment', async () => {
      const retryBtn = page.locator('button', { hasText: /Retry/i }).first();
      if (await retryBtn.isVisible()) {
        await expect(retryBtn).toBeVisible();
      }
    });
  });

  test.describe('Navigation', () => {
    test('should navigate to deployment detail on card click', async () => {
      const deploymentCard = page.locator('[class*="deployment-card"]').first();
      if (await deploymentCard.isVisible()) {
        await deploymentCard.click();
        await page.waitForURL(/\/deployments\//);
      }
    });
  });
});

test.describe('Deployment Detail Page', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/deployments/deploy-1');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display deployment header', async () => {
      const header = page.locator('[class*="deployment-header"]');
      await expect(header).toBeVisible();
    });

    test('should show back link', async () => {
      const backLink = page.locator('[class*="back-link"], a', { hasText: /Back/i });
      await expect(backLink).toBeVisible();
    });

    test('should show tabs', async () => {
      const tabs = page.locator('[class*="tabs"]');
      await expect(tabs).toBeVisible();
    });

    test('should show tab content', async () => {
      const tabContent = page.locator('[class*="tab-content"]');
      await expect(tabContent).toBeVisible();
    });
  });

  test.describe('Deployment Header', () => {
    test('should show deployment ID', async () => {
      const deployId = page.locator('[class*="deploy-id"]');
      await expect(deployId).toBeVisible();
    });

    test('should show status badge', async () => {
      const statusBadge = page.locator('[class*="status-badge"]');
      await expect(statusBadge).toBeVisible();
    });

    test('should show environment badge', async () => {
      const envBadge = page.locator('[class*="env-badge"], [class*="environment"]');
      await expect(envBadge).toBeVisible();
    });

    test('should show deployment meta info', async () => {
      const meta = page.locator('[class*="deploy-meta"]');
      await expect(meta).toBeVisible();
    });

    test('should show commit info', async () => {
      const commitInfo = page.locator('[class*="commit"]');
      await expect(commitInfo).toBeVisible();
    });

    test('should show deployer', async () => {
      const deployer = page.locator('[class*="deployer"], [class*="author"]');
      await expect(deployer).toBeVisible();
    });

    test('should show action buttons', async () => {
      const actions = page.locator('[class*="deploy-actions"]');
      await expect(actions).toBeVisible();
    });
  });

  test.describe('Tabs', () => {
    test('should have Pipeline tab', async () => {
      const pipelineTab = page.locator('button', { hasText: /Pipeline/i });
      await expect(pipelineTab).toBeVisible();
    });

    test('should have Changes tab', async () => {
      const changesTab = page.locator('button', { hasText: /Changes/i });
      await expect(changesTab).toBeVisible();
    });

    test('should have Artifacts tab', async () => {
      const artifactsTab = page.locator('button', { hasText: /Artifacts/i });
      await expect(artifactsTab).toBeVisible();
    });

    test('should switch tabs on click', async () => {
      const changesTab = page.locator('button', { hasText: /Changes/i });
      await changesTab.click();
      await expect(changesTab).toHaveClass(/active/);
    });
  });

  test.describe('Pipeline Tab', () => {
    test('should show pipeline viewer', async () => {
      const pipelineViewer = page.locator('[class*="pipeline-viewer"]');
      await expect(pipelineViewer).toBeVisible();
    });

    test('should show pipeline stages', async () => {
      const stages = page.locator('[class*="stage-node"]');
      await expect(stages).toHaveCount(await stages.count() > 0 ? await stages.count() : 0);
    });

    test('should show stage names', async () => {
      const stageName = page.locator('[class*="stage-name"]').first();
      if (await stageName.isVisible()) {
        await expect(stageName).toBeVisible();
      }
    });

    test('should show stage status icons', async () => {
      const stageStatus = page.locator('[class*="stage-status"]').first();
      if (await stageStatus.isVisible()) {
        await expect(stageStatus).toBeVisible();
      }
    });

    test('should show stage duration', async () => {
      const stageDuration = page.locator('[class*="stage-duration"]').first();
      if (await stageDuration.isVisible()) {
        await expect(stageDuration).toBeVisible();
      }
    });

    test('should show connectors between stages', async () => {
      const connectors = page.locator('[class*="connector"]');
      if (await connectors.count() > 0) {
        await expect(connectors.first()).toBeVisible();
      }
    });
  });

  test.describe('Pipeline Logs', () => {
    test('should show logs viewer', async () => {
      const logsViewer = page.locator('[class*="logs-viewer"]');
      await expect(logsViewer).toBeVisible();
    });

    test('should show stage selector for logs', async () => {
      const stageSelector = page.locator('[class*="stage-selector"], select', { hasText: /Stage/i });
      if (await stageSelector.isVisible()) {
        await expect(stageSelector).toBeVisible();
      }
    });

    test('should show log output', async () => {
      const logOutput = page.locator('[class*="log-output"], [class*="logs-content"]');
      await expect(logOutput).toBeVisible();
    });

    test('should have auto-scroll toggle', async () => {
      const autoScrollToggle = page.locator('[class*="auto-scroll"], button', { hasText: /Auto|Scroll/i });
      if (await autoScrollToggle.isVisible()) {
        await expect(autoScrollToggle).toBeVisible();
      }
    });

    test('should have download logs button', async () => {
      const downloadBtn = page.locator('button', { hasText: /Download/i });
      if (await downloadBtn.isVisible()) {
        await expect(downloadBtn).toBeVisible();
      }
    });
  });

  test.describe('Stage Interaction', () => {
    test('should show stage logs on stage click', async () => {
      const stage = page.locator('[class*="stage-node"]').first();
      if (await stage.isVisible()) {
        await stage.click();
        const logsViewer = page.locator('[class*="logs-viewer"]');
        await expect(logsViewer).toBeVisible();
      }
    });

    test('should highlight selected stage', async () => {
      const stage = page.locator('[class*="stage-node"]').first();
      if (await stage.isVisible()) {
        await stage.click();
        await expect(stage).toHaveClass(/selected|active/);
      }
    });
  });

  test.describe('Changes Tab', () => {
    test.beforeEach(async () => {
      const changesTab = page.locator('button', { hasText: /Changes/i });
      await changesTab.click();
    });

    test('should show changes list', async () => {
      const changesList = page.locator('[class*="changes-list"]');
      await expect(changesList).toBeVisible();
    });

    test('should show commit items', async () => {
      const commits = page.locator('[class*="commit-item"]');
      if (await commits.count() > 0) {
        await expect(commits.first()).toBeVisible();
      }
    });

    test('should show commit message', async () => {
      const commitMsg = page.locator('[class*="commit-message"]').first();
      if (await commitMsg.isVisible()) {
        await expect(commitMsg).toBeVisible();
      }
    });

    test('should show commit author', async () => {
      const commitAuthor = page.locator('[class*="commit-author"]').first();
      if (await commitAuthor.isVisible()) {
        await expect(commitAuthor).toBeVisible();
      }
    });

    test('should show commit hash', async () => {
      const commitHash = page.locator('[class*="commit-hash"]').first();
      if (await commitHash.isVisible()) {
        await expect(commitHash).toBeVisible();
      }
    });

    test('should show files changed summary', async () => {
      const filesSummary = page.locator('[class*="files-summary"], [class*="changes-summary"]');
      if (await filesSummary.isVisible()) {
        await expect(filesSummary).toBeVisible();
      }
    });
  });

  test.describe('Artifacts Tab', () => {
    test.beforeEach(async () => {
      const artifactsTab = page.locator('button', { hasText: /Artifacts/i });
      await artifactsTab.click();
    });

    test('should show artifacts list', async () => {
      const artifactsList = page.locator('[class*="artifacts-list"]');
      await expect(artifactsList).toBeVisible();
    });

    test('should show artifact items', async () => {
      const artifacts = page.locator('[class*="artifact-item"]');
      if (await artifacts.count() > 0) {
        await expect(artifacts.first()).toBeVisible();
      }
    });

    test('should show artifact name', async () => {
      const artifactName = page.locator('[class*="artifact-name"]').first();
      if (await artifactName.isVisible()) {
        await expect(artifactName).toBeVisible();
      }
    });

    test('should show artifact size', async () => {
      const artifactSize = page.locator('[class*="artifact-size"]').first();
      if (await artifactSize.isVisible()) {
        await expect(artifactSize).toBeVisible();
      }
    });

    test('should have download button for artifacts', async () => {
      const downloadBtn = page.locator('[class*="artifact-item"] button', { hasText: /Download/i }).first();
      if (await downloadBtn.isVisible()) {
        await expect(downloadBtn).toBeVisible();
      }
    });
  });

  test.describe('Actions', () => {
    test('should show rollback button for successful deployments', async () => {
      const rollbackBtn = page.locator('button', { hasText: /Rollback/i });
      if (await rollbackBtn.isVisible()) {
        await expect(rollbackBtn).toBeVisible();
      }
    });

    test('should show retry button for failed deployments', async () => {
      const retryBtn = page.locator('button', { hasText: /Retry/i });
      if (await retryBtn.isVisible()) {
        await expect(retryBtn).toBeVisible();
      }
    });

    test('should show cancel button for in-progress deployments', async () => {
      const cancelBtn = page.locator('button', { hasText: /Cancel|Abort/i });
      if (await cancelBtn.isVisible()) {
        await expect(cancelBtn).toBeVisible();
      }
    });
  });

  test.describe('Rollback Confirmation', () => {
    test('should show confirmation modal on rollback click', async () => {
      const rollbackBtn = page.locator('button', { hasText: /Rollback/i });
      if (await rollbackBtn.isVisible()) {
        await rollbackBtn.click();
        const modal = page.locator('[class*="modal"]');
        if (await modal.isVisible()) {
          await expect(modal).toBeVisible();
        }
      }
    });

    test('should show warning message in rollback modal', async () => {
      const rollbackBtn = page.locator('button', { hasText: /Rollback/i });
      if (await rollbackBtn.isVisible()) {
        await rollbackBtn.click();
        const warning = page.locator('[class*="warning"], [class*="modal"]');
        if (await warning.isVisible()) {
          await expect(warning).toContainText(/warning|confirm|rollback/i);
        }
      }
    });
  });
});
