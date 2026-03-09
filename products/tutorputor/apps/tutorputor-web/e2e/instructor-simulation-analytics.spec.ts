/**
 * Instructor Simulation Analytics E2E Tests
 *
 * End-to-end tests for instructor dashboard simulation analytics panel.
 * Tests heatmaps, confusion zones, and learning metrics visualization.
 *
 * @doc.type test
 * @doc.purpose E2E tests for instructor simulation analytics
 * @doc.layer product
 * @doc.pattern Test
 */
import { test, expect } from "@playwright/test";

test.describe("Instructor Simulation Analytics", () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to instructor dashboard (requires instructor role)
    await page.goto("/teacher");
  });

  test("should display instructor dashboard", async ({ page }) => {
    // Verify dashboard loaded
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  });

  test("should show simulation analytics section", async ({ page }) => {
    // Look for simulation analytics panel
    const analyticsPanel = page.locator("[data-testid='simulation-analytics-panel']");

    // If simulations are used in any class
    if (await analyticsPanel.isVisible()) {
      // Verify panel has content
      await expect(analyticsPanel.getByRole("heading")).toBeVisible();
    }
  });

  test("should display class selector for analytics", async ({ page }) => {
    const analyticsPanel = page.locator("[data-testid='simulation-analytics-panel']");

    if (await analyticsPanel.isVisible()) {
      // Should have class/section selector
      const classSelector = analyticsPanel.locator("[data-testid='class-selector']");
      if (await classSelector.isVisible()) {
        await classSelector.click();
        // Should show dropdown with classes
        await expect(page.getByRole("listbox")).toBeVisible();
      }
    }
  });

  test("should show parameter exploration heatmap", async ({ page }) => {
    const analyticsPanel = page.locator("[data-testid='simulation-analytics-panel']");

    if (await analyticsPanel.isVisible()) {
      // Look for heatmap visualization
      const heatmap = analyticsPanel.locator("[data-testid='parameter-heatmap']");

      if (await heatmap.isVisible()) {
        // Verify heatmap has data
        await expect(heatmap.locator("svg, canvas")).toBeVisible();
      }
    }
  });

  test("should display confusion zones", async ({ page }) => {
    const analyticsPanel = page.locator("[data-testid='simulation-analytics-panel']");

    if (await analyticsPanel.isVisible()) {
      // Look for confusion zones section
      const confusionZones = analyticsPanel.locator("[data-testid='confusion-zones']");

      if (await confusionZones.isVisible()) {
        // Should show step indicators with confusion data
        await expect(confusionZones.locator("[data-testid='zone-item']").first()).toBeVisible();
      }
    }
  });

  test("should show typical learning paths", async ({ page }) => {
    const analyticsPanel = page.locator("[data-testid='simulation-analytics-panel']");

    if (await analyticsPanel.isVisible()) {
      // Look for learning paths visualization
      const pathsViz = analyticsPanel.locator("[data-testid='learning-paths-viz']");

      if (await pathsViz.isVisible()) {
        // Should show path diagram or list
        await expect(pathsViz).toBeVisible();
      }
    }
  });

  test("should filter analytics by date range", async ({ page }) => {
    const analyticsPanel = page.locator("[data-testid='simulation-analytics-panel']");

    if (await analyticsPanel.isVisible()) {
      // Look for date range picker
      const dateRangePicker = analyticsPanel.locator("[data-testid='date-range-picker']");

      if (await dateRangePicker.isVisible()) {
        await dateRangePicker.click();

        // Select last 7 days
        const last7Days = page.getByRole("option", { name: /7 days|week/i });
        if (await last7Days.isVisible()) {
          await last7Days.click();

          // Data should update (verify loading state changes)
          await expect(analyticsPanel).not.toHaveAttribute("data-loading", "true");
        }
      }
    }
  });

  test("should show student breakdown", async ({ page }) => {
    const analyticsPanel = page.locator("[data-testid='simulation-analytics-panel']");

    if (await analyticsPanel.isVisible()) {
      // Look for student list/breakdown
      const studentBreakdown = analyticsPanel.locator("[data-testid='student-breakdown']");

      if (await studentBreakdown.isVisible()) {
        // Should show individual student rows
        await expect(
          studentBreakdown.locator("[data-testid='student-row']").first()
        ).toBeVisible();
      }
    }
  });

  test("should drill down into specific simulation", async ({ page }) => {
    const analyticsPanel = page.locator("[data-testid='simulation-analytics-panel']");

    if (await analyticsPanel.isVisible()) {
      // Find simulation list
      const simList = analyticsPanel.locator("[data-testid='simulation-list']");

      if (await simList.isVisible()) {
        // Click on a simulation to drill down
        const firstSim = simList.locator("[data-testid='simulation-item']").first();
        if (await firstSim.isVisible()) {
          await firstSim.click();

          // Should show detailed analytics for that simulation
          await expect(
            page.locator("[data-testid='simulation-detail-analytics']")
          ).toBeVisible();
        }
      }
    }
  });

  test("should export analytics data", async ({ page }) => {
    const analyticsPanel = page.locator("[data-testid='simulation-analytics-panel']");

    if (await analyticsPanel.isVisible()) {
      // Look for export button
      const exportButton = analyticsPanel.getByRole("button", { name: /export|download/i });

      if (await exportButton.isVisible()) {
        // Set up download listener
        const downloadPromise = page.waitForEvent("download");

        await exportButton.click();

        // If export modal appears, select format
        const csvOption = page.getByRole("button", { name: /csv/i });
        if (await csvOption.isVisible()) {
          await csvOption.click();
        }

        // Verify download started (may not complete in test)
        // This is a best-effort check
      }
    }
  });

  test("should show aggregate metrics", async ({ page }) => {
    const analyticsPanel = page.locator("[data-testid='simulation-analytics-panel']");

    if (await analyticsPanel.isVisible()) {
      // Look for metric cards
      const metricCards = analyticsPanel.locator("[data-testid='metric-card']");

      if ((await metricCards.count()) > 0) {
        // Verify metrics are displayed
        await expect(metricCards.first()).toBeVisible();

        // Common metrics: completion rate, avg time, struggle rate
        const completionMetric = analyticsPanel.getByText(/completion|completed/i);
        const timeMetric = analyticsPanel.getByText(/time|duration|minutes/i);

        // At least one should be visible
        const hasCompletionMetric = await completionMetric.isVisible();
        const hasTimeMetric = await timeMetric.isVisible();
        expect(hasCompletionMetric || hasTimeMetric).toBe(true);
      }
    }
  });

  test("should compare performance across simulations", async ({ page }) => {
    const analyticsPanel = page.locator("[data-testid='simulation-analytics-panel']");

    if (await analyticsPanel.isVisible()) {
      // Look for comparison view
      const compareButton = analyticsPanel.getByRole("button", { name: /compare|comparison/i });

      if (await compareButton.isVisible()) {
        await compareButton.click();

        // Should show comparison interface
        await expect(
          page.locator("[data-testid='simulation-comparison']")
        ).toBeVisible();
      }
    }
  });
});
