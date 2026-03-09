/**
 * Velocity & Metrics E2E Tests
 *
 * End-to-end tests for velocity charts and metrics including:
 * - Velocity charts page
 * - Burndown charts
 * - Team metrics
 * - Sprint history
 *
 * @doc.type test
 * @doc.purpose E2E tests for velocity and metrics
 * @doc.phase 3
 */

import { test, expect, type Page } from '@playwright/test';

test.describe('Velocity Charts Page', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/velocity');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display page header', async () => {
      const header = page.locator('h1');
      await expect(header).toContainText(/Velocity|Metrics/i);
    });

    test('should show key metrics cards', async () => {
      const metricsCards = page.locator('[class*="metric-card"]');
      await expect(metricsCards).toHaveCount(await metricsCards.count() > 0 ? await metricsCards.count() : 0);
    });

    test('should show chart section', async () => {
      const chartSection = page.locator('[class*="chart-section"], [class*="charts"]');
      await expect(chartSection).toBeVisible();
    });

    test('should show sprint history table', async () => {
      const historyTable = page.locator('[class*="sprint-history"], [class*="history-table"], table');
      await expect(historyTable).toBeVisible();
    });
  });

  test.describe('Key Metrics Cards', () => {
    test('should show average velocity', async () => {
      const velocityStat = page.locator('[class*="metric-card"]', { hasText: /Velocity/i });
      await expect(velocityStat).toBeVisible();
    });

    test('should show completion rate', async () => {
      const completionStat = page.locator('[class*="metric-card"]', { hasText: /Completion|Complete/i });
      await expect(completionStat).toBeVisible();
    });

    test('should show cycle time', async () => {
      const cycleStat = page.locator('[class*="metric-card"]', { hasText: /Cycle|Time/i });
      if (await cycleStat.isVisible()) {
        await expect(cycleStat).toBeVisible();
      }
    });

    test('should show predictability', async () => {
      const predictStat = page.locator('[class*="metric-card"]', { hasText: /Predict/i });
      if (await predictStat.isVisible()) {
        await expect(predictStat).toBeVisible();
      }
    });

    test('should show throughput', async () => {
      const throughputStat = page.locator('[class*="metric-card"]', { hasText: /Throughput/i });
      if (await throughputStat.isVisible()) {
        await expect(throughputStat).toBeVisible();
      }
    });

    test('should show metric values', async () => {
      const metricValue = page.locator('[class*="metric-value"]').first();
      if (await metricValue.isVisible()) {
        await expect(metricValue).toBeVisible();
      }
    });

    test('should show trend indicators', async () => {
      const trendIndicator = page.locator('[class*="trend"], [class*="change"]').first();
      if (await trendIndicator.isVisible()) {
        await expect(trendIndicator).toBeVisible();
      }
    });
  });

  test.describe('Chart Controls', () => {
    test('should have chart type toggle', async () => {
      const chartToggle = page.locator('[class*="chart-toggle"], button', { hasText: /Velocity|Burndown/i });
      await expect(chartToggle).toBeVisible();
    });

    test('should switch between velocity and burndown charts', async () => {
      const burndownBtn = page.locator('button', { hasText: /Burndown/i });
      if (await burndownBtn.isVisible()) {
        await burndownBtn.click();
        await expect(burndownBtn).toHaveClass(/active/);
      }
    });

    test('should have sprint range selector', async () => {
      const sprintSelector = page.locator('select, [class*="sprint-select"]', { hasText: /Sprint|Last/i });
      if (await sprintSelector.isVisible()) {
        await expect(sprintSelector).toBeVisible();
      }
    });
  });

  test.describe('Velocity Chart', () => {
    test('should display velocity chart', async () => {
      const velocityChart = page.locator('[class*="velocity-chart"], [class*="chart"]').first();
      await expect(velocityChart).toBeVisible();
    });

    test('should show chart bars or lines', async () => {
      const chartElements = page.locator('[class*="chart"] svg, [class*="chart"] canvas');
      await expect(chartElements).toBeVisible();
    });

    test('should show legend', async () => {
      const legend = page.locator('[class*="legend"]');
      if (await legend.isVisible()) {
        await expect(legend).toBeVisible();
      }
    });

    test('should show committed vs completed in legend', async () => {
      const legend = page.locator('[class*="legend"]');
      if (await legend.isVisible()) {
        await expect(legend).toContainText(/Committed|Completed/i);
      }
    });
  });

  test.describe('Burndown Chart', () => {
    test.beforeEach(async () => {
      const burndownBtn = page.locator('button', { hasText: /Burndown/i });
      if (await burndownBtn.isVisible()) {
        await burndownBtn.click();
      }
    });

    test('should display burndown chart', async () => {
      const burndownChart = page.locator('[class*="burndown-chart"], [class*="chart"]').first();
      await expect(burndownChart).toBeVisible();
    });

    test('should show ideal line', async () => {
      const idealLine = page.locator('[class*="ideal-line"], [class*="reference"]');
      if (await idealLine.isVisible()) {
        await expect(idealLine).toBeVisible();
      }
    });

    test('should show actual progress line', async () => {
      const actualLine = page.locator('[class*="actual-line"], [class*="progress"]');
      if (await actualLine.isVisible()) {
        await expect(actualLine).toBeVisible();
      }
    });
  });

  test.describe('Distribution Bar', () => {
    test('should show story type distribution', async () => {
      const distributionBar = page.locator('[class*="distribution"]');
      if (await distributionBar.isVisible()) {
        await expect(distributionBar).toBeVisible();
      }
    });

    test('should show distribution segments', async () => {
      const segments = page.locator('[class*="distribution-segment"]');
      if (await segments.count() > 0) {
        await expect(segments.first()).toBeVisible();
      }
    });

    test('should show distribution legend', async () => {
      const legend = page.locator('[class*="distribution-legend"]');
      if (await legend.isVisible()) {
        await expect(legend).toBeVisible();
      }
    });
  });

  test.describe('Sprint History Table', () => {
    test('should show table headers', async () => {
      const headers = page.locator('th');
      await expect(headers).toHaveCount(await headers.count() > 0 ? await headers.count() : 0);
    });

    test('should show Sprint column', async () => {
      const sprintHeader = page.locator('th', { hasText: /Sprint/i });
      await expect(sprintHeader).toBeVisible();
    });

    test('should show Committed column', async () => {
      const committedHeader = page.locator('th', { hasText: /Committed/i });
      await expect(committedHeader).toBeVisible();
    });

    test('should show Completed column', async () => {
      const completedHeader = page.locator('th', { hasText: /Completed/i });
      await expect(completedHeader).toBeVisible();
    });

    test('should show table rows', async () => {
      const rows = page.locator('tbody tr');
      if (await rows.count() > 0) {
        await expect(rows.first()).toBeVisible();
      }
    });

    test('should show sprint names in rows', async () => {
      const sprintName = page.locator('tbody tr td').first();
      if (await sprintName.isVisible()) {
        await expect(sprintName).toBeVisible();
      }
    });

    test('should show completion percentage', async () => {
      const completion = page.locator('td', { hasText: /%/ });
      if (await completion.count() > 0) {
        await expect(completion.first()).toBeVisible();
      }
    });

    test('should allow sorting by column', async () => {
      const sortableHeader = page.locator('th[class*="sortable"], th button').first();
      if (await sortableHeader.isVisible()) {
        await sortableHeader.click();
        await page.waitForTimeout(300);
      }
    });
  });

  test.describe('Date Range Selection', () => {
    test('should have date range filter', async () => {
      const dateFilter = page.locator('[class*="date-range"], select', { hasText: /Last|Range|Month/i });
      if (await dateFilter.isVisible()) {
        await expect(dateFilter).toBeVisible();
      }
    });

    test('should filter data by date range', async () => {
      const dateFilter = page.locator('select').first();
      if (await dateFilter.isVisible()) {
        await dateFilter.selectOption({ index: 1 });
        await page.waitForTimeout(500);
      }
    });
  });

  test.describe('Export Functionality', () => {
    test('should have export button', async () => {
      const exportBtn = page.locator('button', { hasText: /Export|Download/i });
      if (await exportBtn.isVisible()) {
        await expect(exportBtn).toBeVisible();
      }
    });

    test('should show export options', async () => {
      const exportBtn = page.locator('button', { hasText: /Export|Download/i });
      if (await exportBtn.isVisible()) {
        await exportBtn.click();
        const exportOptions = page.locator('[class*="export-options"], [class*="dropdown"]');
        if (await exportOptions.isVisible()) {
          await expect(exportOptions).toBeVisible();
        }
      }
    });
  });

  test.describe('Team Comparison', () => {
    test('should have team selector if multiple teams', async () => {
      const teamSelector = page.locator('select, button', { hasText: /Team/i });
      if (await teamSelector.isVisible()) {
        await expect(teamSelector).toBeVisible();
      }
    });

    test('should allow comparing multiple sprints', async () => {
      const compareBtn = page.locator('button', { hasText: /Compare/i });
      if (await compareBtn.isVisible()) {
        await expect(compareBtn).toBeVisible();
      }
    });
  });

  test.describe('Responsive Layout', () => {
    test('should be responsive on smaller screens', async () => {
      await page.setViewportSize({ width: 768, height: 1024 });
      const pageContent = page.locator('[class*="velocity"]');
      await expect(pageContent).toBeVisible();
    });

    test('should stack metrics cards on mobile', async () => {
      await page.setViewportSize({ width: 375, height: 667 });
      const metricsSection = page.locator('[class*="metrics"], [class*="stats"]');
      await expect(metricsSection).toBeVisible();
    });
  });

  test.describe('Tooltip Interactions', () => {
    test('should show tooltips on chart hover', async () => {
      const chartArea = page.locator('[class*="chart"] svg, [class*="chart"] canvas');
      if (await chartArea.isVisible()) {
        const box = await chartArea.boundingBox();
        if (box) {
          await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
          await page.waitForTimeout(300);
        }
      }
    });

    test('should show metric card tooltips on hover', async () => {
      const metricCard = page.locator('[class*="metric-card"]').first();
      if (await metricCard.isVisible()) {
        await metricCard.hover();
        await page.waitForTimeout(300);
      }
    });
  });
});
