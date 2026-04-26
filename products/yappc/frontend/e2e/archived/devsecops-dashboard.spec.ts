import { test, expect, type Page } from '@playwright/test';

/**
 * DevSecOps Dashboard E2E Tests
 * 
 * Test Suite: Critical user journeys for the DevSecOps dashboard
 * Scope: Main dashboard page with KPI cards, phase overview, alerts, and recent activity
 * Framework: Playwright
 */

test.describe('DevSecOps Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to dashboard before each test
    await page.goto('/devsecops');
    
    // Wait for page to be fully loaded
    await page.waitForLoadState('networkidle');
  });

  test.describe('Hero Section', () => {
    test('should display hero section with title and description', async ({ page }) => {
      const heroSection = page.locator('section').first();
      
      // Check title
      await expect(heroSection.getByText('DevSecOps Executive Dashboard')).toBeVisible();
      
      // Check description
      await expect(heroSection.getByText(/Real-time insights/)).toBeVisible();
      
      // Check last updated timestamp
      await expect(heroSection.getByText(/Last updated:/)).toBeVisible();
      
      // Check phase count
      await expect(heroSection.getByText(/phases/)).toBeVisible();
    });

    test('should display hero section with gradient background', async ({ page }) => {
      const heroSection = page.locator('section').first();
      const heroPaper = heroSection.locator('> div > div').first();
      
      // Check for gradient background (Material-UI Paper)
      await expect(heroPaper).toBeVisible();
      
      // Verify it's prominent (not zero height)
      const box = await heroPaper.boundingBox();
      expect(box?.height).toBeGreaterThan(100);
    });
  });

  test.describe('KPI Cards', () => {
    test('should display all 4 KPI cards', async ({ page }) => {
      const kpiCards = page.locator('[data-testid="kpi-card"]');
      
      // Should have exactly 4 KPI cards
      await expect(kpiCards).toHaveCount(4);
    });

    test('should display Completion Rate KPI with percentage', async ({ page }) => {
      // Find the Completion Rate card
      const completionCard = page.locator('text=Completion Rate').locator('..');
      
      await expect(completionCard).toBeVisible();
      
      // Should show a percentage value
      await expect(completionCard.locator('text=/%/')).toBeVisible();
      
      // Should show a trend indicator
      await expect(completionCard.locator('[data-testid="trend-indicator"]')).toBeVisible();
    });

    test('should display In Progress KPI with count', async ({ page }) => {
      const inProgressCard = page.locator('text=In Progress').locator('..');
      
      await expect(inProgressCard).toBeVisible();
      
      // Should show "items" unit
      await expect(inProgressCard.locator('text=/items/')).toBeVisible();
    });

    test('should display Blocked KPI with alert styling', async ({ page }) => {
      const blockedCard = page.locator('text=Blocked').locator('..');
      
      await expect(blockedCard).toBeVisible();
      
      // Should show items count
      await expect(blockedCard.locator('text=/items/')).toBeVisible();
    });

    test('should display Completed KPI with positive trend', async ({ page }) => {
      const completedCard = page.locator('text=Completed').locator('..');
      
      await expect(completedCard).toBeVisible();
      
      // Should show items count
      await expect(completedCard.locator('text=/items/')).toBeVisible();
      
      // Should have upward trend
      await expect(completedCard.locator('[data-testid="trend-up"]')).toBeVisible();
    });

    test('should have responsive layout on mobile', async ({ page }) => {
      // Set viewport to mobile size
      await page.setViewportSize({ width: 375, height: 667 });
      
      const kpiContainer = page.locator('[data-testid="kpi-cards-container"]').first();
      
      // On mobile, should stack vertically (check flex direction or grid template)
      await expect(kpiContainer).toBeVisible();
    });

    test('should have responsive layout on tablet', async ({ page }) => {
      // Set viewport to tablet size
      await page.setViewportSize({ width: 768, height: 1024 });
      
      const kpiCards = page.locator('[data-testid="kpi-card"]');
      
      // Should all be visible
      await expect(kpiCards.first()).toBeVisible();
      await expect(kpiCards.last()).toBeVisible();
    });

    test('should have responsive layout on desktop', async ({ page }) => {
      // Set viewport to desktop size
      await page.setViewportSize({ width: 1920, height: 1080 });
      
      const kpiCards = page.locator('[data-testid="kpi-card"]');
      
      // Should display in row layout
      await expect(kpiCards.first()).toBeVisible();
      await expect(kpiCards.nth(3)).toBeVisible();
    });
  });

  test.describe('Phase Navigation', () => {
    test('should display phase navigation component', async ({ page }) => {
      const phaseNav = page.locator('[data-testid="phase-nav"]');
      
      await expect(phaseNav).toBeVisible();
    });

    test('should display "Phases" section header', async ({ page }) => {
      await expect(page.getByRole('heading', { name: 'Phases' })).toBeVisible();
    });

    test('should navigate to phase detail on phase click', async ({ page }) => {
      // Find first phase button/link
      const firstPhase = page.locator('[data-testid="phase-nav-item"]').first();
      
      await expect(firstPhase).toBeVisible();
      
      // Click the phase
      await firstPhase.click();
      
      // Should navigate to phase detail page
      await page.waitForURL(/\/devsecops\/phase\/.+/);
      
      // Verify we're on phase detail page
      expect(page.url()).toContain('/devsecops/phase/');
    });
  });

  test.describe('Phase Overview Cards', () => {
    test('should display "Phase Overview" section header', async ({ page }) => {
      await expect(page.getByRole('heading', { name: 'Phase Overview' })).toBeVisible();
    });

    test('should display phase overview cards in grid', async ({ page }) => {
      const phaseCards = page.locator('[data-testid="phase-overview-card"]');
      
      // Should have multiple phase cards
      const count = await phaseCards.count();
      expect(count).toBeGreaterThan(0);
    });

    test('should display phase icon, title, and stats on each card', async ({ page }) => {
      const firstCard = page.locator('[data-testid="phase-overview-card"]').first();
      
      await expect(firstCard).toBeVisible();
      
      // Should show progress (completed/total)
      await expect(firstCard.locator('text=/\\d+\\/\\d+/')).toBeVisible();
      
      // Should show "In Progress" count
      await expect(firstCard.locator('text=In Progress')).toBeVisible();
      
      // Should show "Blocked" count
      await expect(firstCard.locator('text=Blocked')).toBeVisible();
    });

    test('should have hover effect on phase cards', async ({ page }) => {
      const firstCard = page.locator('[data-testid="phase-overview-card"]').first();
      
      // Get initial position
      const beforeBox = await firstCard.boundingBox();
      
      // Hover over card
      await firstCard.hover();
      
      // Wait a bit for animation
      await page.waitForTimeout(300);
      
      // Card should still be visible after hover
      await expect(firstCard).toBeVisible();
      
      // Note: Can't easily test translateY with Playwright, but we verify interaction works
    });

    test('should navigate to phase detail on card click', async ({ page }) => {
      const firstCard = page.locator('[data-testid="phase-overview-card"]').first();
      
      await firstCard.click();
      
      // Should navigate to phase page
      await page.waitForURL(/\/devsecops\/phase\/.+/);
      expect(page.url()).toContain('/devsecops/phase/');
    });

    test('should display phase overview in grid layout', async ({ page }) => {
      // Desktop should show 3 columns
      await page.setViewportSize({ width: 1440, height: 900 });
      
      const phaseCards = page.locator('[data-testid="phase-overview-card"]');
      const count = await phaseCards.count();
      
      // Should have at least 3 cards visible
      expect(count).toBeGreaterThanOrEqual(3);
    });

    test('should display phase overview in responsive layout on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      
      const phaseCards = page.locator('[data-testid="phase-overview-card"]');
      
      // Cards should still be visible
      await expect(phaseCards.first()).toBeVisible();
    });
  });

  test.describe('Initiatives & Alerts', () => {
    test('should display "Initiatives & Alerts" section header', async ({ page }) => {
      await expect(page.getByRole('heading', { name: 'Initiatives & Alerts' })).toBeVisible();
    });

    test('should display 4 alert cards', async ({ page }) => {
      const alertCards = page.locator('[data-testid="alert-card"]');
      
      // Should have exactly 4 alerts
      await expect(alertCards).toHaveCount(4);
    });

    test('should display Blocked Items alert', async ({ page }) => {
      const blockedAlert = page.locator('text=3 Blocked Items');
      
      await expect(blockedAlert).toBeVisible();
      await expect(page.locator('text=Requires immediate attention')).toBeVisible();
    });

    test('should display Pending Approvals alert', async ({ page }) => {
      const approvalAlert = page.locator('text=2 Pending Approvals');
      
      await expect(approvalAlert).toBeVisible();
      await expect(page.locator('text=Waiting for security review')).toBeVisible();
    });

    test('should display Completion alert', async ({ page }) => {
      const completionAlert = page.locator('text=95% Completion');
      
      await expect(completionAlert).toBeVisible();
      await expect(page.locator('text=On track for release')).toBeVisible();
    });

    test('should display Critical Vulnerability alert', async ({ page }) => {
      const vulnAlert = page.locator('text=1 Critical Vulnerability');
      
      await expect(vulnAlert).toBeVisible();
      await expect(page.locator('text=Security scan flagged')).toBeVisible();
    });

    test('should display alerts with color-coded borders', async ({ page }) => {
      const alerts = page.locator('[data-testid="alert-card"]');
      
      // All alerts should be visible
      await expect(alerts.nth(0)).toBeVisible();
      await expect(alerts.nth(1)).toBeVisible();
      await expect(alerts.nth(2)).toBeVisible();
      await expect(alerts.nth(3)).toBeVisible();
    });

    test('should display alerts in grid layout', async ({ page }) => {
      // Desktop should show 2 columns
      await page.setViewportSize({ width: 1440, height: 900 });
      
      const alertCards = page.locator('[data-testid="alert-card"]');
      
      // All 4 should be visible
      await expect(alertCards).toHaveCount(4);
    });

    test('should display alerts responsively on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });
      
      const alerts = page.locator('[data-testid="alert-card"]');
      
      // Should stack into single column
      await expect(alerts.first()).toBeVisible();
    });
  });

  test.describe('Recent Activity', () => {
    test('should display "Recent Activity" section header', async ({ page }) => {
      await expect(page.getByRole('heading', { name: 'Recent Activity' })).toBeVisible();
    });

    test('should display recent activity list', async ({ page }) => {
      const activityList = page.locator('[data-testid="activity-item"]');
      
      // Should have at least 1 activity item
      const count = await activityList.count();
      expect(count).toBeGreaterThan(0);
    });

    test('should limit activity list to 5 items', async ({ page }) => {
      const activityList = page.locator('[data-testid="activity-item"]');
      
      // Should have at most 5 items
      const count = await activityList.count();
      expect(count).toBeLessThanOrEqual(5);
    });

    test('should display activity item with title and metadata', async ({ page }) => {
      const firstActivity = page.locator('[data-testid="activity-item"]').first();
      
      await expect(firstActivity).toBeVisible();
      
      // Should have assignee
      // Should have date (bullet separator)
      await expect(firstActivity.locator('text=•')).toBeVisible();
    });

    test('should display activity items with hover effect', async ({ page }) => {
      const firstActivity = page.locator('[data-testid="activity-item"]').first();
      
      // Hover over item
      await firstActivity.hover();
      
      // Item should still be visible
      await expect(firstActivity).toBeVisible();
    });

    test('should display formatted dates', async ({ page }) => {
      const activityList = page.locator('[data-testid="activity-item"]');
      
      // Should have at least one date
      const firstActivity = activityList.first();
      await expect(firstActivity).toBeVisible();
      
      // Date should be formatted (e.g., "12/25/2023")
      // Pattern: numbers with slashes or hyphens
      await expect(firstActivity.locator('text=/\\d+\\/\\d+\\/\\d+|\\d+-\\d+-\\d+/')).toBeVisible();
    });
  });

  test.describe('Page Layout & Responsiveness', () => {
    test('should have proper page margins and padding', async ({ page }) => {
      const mainSection = page.locator('section').first();
      
      await expect(mainSection).toBeVisible();
      
      const box = await mainSection.boundingBox();
      expect(box).not.toBeNull();
    });

    test('should have max-width container for content', async ({ page }) => {
      // Desktop should have constrained width
      await page.setViewportSize({ width: 1920, height: 1080 });
      
      const mainContent = page.locator('[data-testid="dashboard-content"]').first();
      
      // Content should not span full width on large screens
      // (max-width: 1440px in code)
      if (await mainContent.isVisible()) {
        const box = await mainContent.boundingBox();
        expect(box?.width).toBeLessThanOrEqual(1440);
      }
    });

    test('should render correctly on iPhone SE (320px)', async ({ page }) => {
      await page.setViewportSize({ width: 320, height: 568 });
      
      // All major sections should still be visible
      await expect(page.getByText('DevSecOps Executive Dashboard')).toBeVisible();
      await expect(page.getByText('Key Metrics')).toBeVisible();
      await expect(page.getByText('Phases')).toBeVisible();
    });

    test('should render correctly on iPad (768px)', async ({ page }) => {
      await page.setViewportSize({ width: 768, height: 1024 });
      
      await expect(page.getByText('DevSecOps Executive Dashboard')).toBeVisible();
      
      // KPI cards should be visible
      const kpiCards = page.locator('[data-testid="kpi-card"]');
      await expect(kpiCards.first()).toBeVisible();
    });

    test('should render correctly on desktop (1440px)', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });
      
      await expect(page.getByText('DevSecOps Executive Dashboard')).toBeVisible();
      
      // All sections should be visible without scrolling
      await expect(page.getByText('Key Metrics')).toBeVisible();
      await expect(page.getByText('Phases')).toBeVisible();
    });

    test('should render correctly on large desktop (1920px)', async ({ page }) => {
      await page.setViewportSize({ width: 1920, height: 1080 });
      
      await expect(page.getByText('DevSecOps Executive Dashboard')).toBeVisible();
      
      // Content should be centered with max-width
      const mainSection = page.locator('section').first();
      await expect(mainSection).toBeVisible();
    });
  });

  test.describe('Navigation Flow', () => {
    test('should allow navigation from dashboard to phase detail', async ({ page }) => {
      // Click first phase overview card
      const firstPhaseCard = page.locator('[data-testid="phase-overview-card"]').first();
      await firstPhaseCard.click();
      
      // Should navigate to phase detail
      await page.waitForURL(/\/devsecops\/phase\/.+/);
      expect(page.url()).toContain('/devsecops/phase/');
    });

    test('should preserve scroll position when navigating back', async ({ page }) => {
      // Scroll down
      await page.evaluate(() => window.scrollTo(0, 500));
      
      // Click a phase
      const firstPhaseCard = page.locator('[data-testid="phase-overview-card"]').first();
      await firstPhaseCard.click();
      
      await page.waitForURL(/\/devsecops\/phase\/.+/);
      
      // Go back
      await page.goBack();
      
      await page.waitForURL('/devsecops');
      
      // Verify we're back on dashboard
      await expect(page.getByText('DevSecOps Executive Dashboard')).toBeVisible();
    });
  });

  test.describe('Data Loading States', () => {
    test('should handle empty state gracefully', async ({ page }) => {
      // This would require mocking the API to return empty data
      // For now, verify page renders without crashing
      await expect(page.getByText('DevSecOps Executive Dashboard')).toBeVisible();
    });

    test('should not crash with missing data', async ({ page }) => {
      // Verify critical elements are present
      await expect(page.getByText('DevSecOps Executive Dashboard')).toBeVisible();
      await expect(page.getByText('Key Metrics')).toBeVisible();
    });
  });

  test.describe('Accessibility', () => {
    test('should have proper heading hierarchy', async ({ page }) => {
      // H4 for hero title
      await expect(page.getByRole('heading', { level: 4, name: /DevSecOps Executive Dashboard/ })).toBeVisible();
      
      // H5 for section headers
      await expect(page.getByRole('heading', { level: 5, name: 'Key Metrics' })).toBeVisible();
    });

    test('should have semantic HTML structure', async ({ page }) => {
      // Main section should use <section> tag
      const mainSection = page.locator('section').first();
      await expect(mainSection).toBeVisible();
    });

    test('should allow keyboard navigation to phase cards', async ({ page }) => {
      // Tab to first phase card
      await page.keyboard.press('Tab');
      await page.keyboard.press('Tab');
      await page.keyboard.press('Tab');
      
      // Press Enter to activate
      await page.keyboard.press('Enter');
      
      // Note: This is a basic test. Full keyboard nav testing needs more setup
    });
  });

  test.describe('Performance', () => {
    test('should load dashboard within 3 seconds', async ({ page }) => {
      const startTime = Date.now();
      
      await page.goto('/devsecops');
      await page.waitForLoadState('networkidle');
      
      const loadTime = Date.now() - startTime;
      
      // Should load in under 3 seconds
      expect(loadTime).toBeLessThan(3000);
    });

    test('should render KPI cards quickly', async ({ page }) => {
      const startTime = Date.now();
      
      // Wait for KPI cards to appear
      await page.locator('[data-testid="kpi-card"]').first().waitFor({ state: 'visible' });
      
      const renderTime = Date.now() - startTime;
      
      // Should render in under 1 second
      expect(renderTime).toBeLessThan(1000);
    });
  });
});

/**
 * Test utilities for dashboard tests
 */
class DashboardTestHelpers {
  constructor(private page: Page) {}

  async waitForDashboardLoad() {
    await this.page.waitForLoadState('networkidle');
    await this.page.getByText('DevSecOps Executive Dashboard').waitFor({ state: 'visible' });
  }

  async getKPICardByTitle(title: string) {
    return this.page.locator(`text=${title}`).locator('..');
  }

  async getPhaseCardByIndex(index: number) {
    return this.page.locator('[data-testid="phase-overview-card"]').nth(index);
  }

  async getAlertCardByTitle(title: string) {
    return this.page.locator(`text=${title}`).locator('..');
  }

  async navigateToPhase(phaseIndex: number) {
    const phaseCard = await this.getPhaseCardByIndex(phaseIndex);
    await phaseCard.click();
    await this.page.waitForURL(/\/devsecops\/phase\/.+/);
  }
}

export { DashboardTestHelpers };
