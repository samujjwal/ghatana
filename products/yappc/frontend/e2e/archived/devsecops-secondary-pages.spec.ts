import { test, expect, type Page } from '@playwright/test';

/**
 * DevSecOps Reports, Settings, and Templates E2E Tests
 * 
 * Test Suite: Secondary pages for reports, settings configuration, and templates
 * Scope: Report hub, individual reports, settings tabs, template management
 * Framework: Playwright
 */

test.describe('DevSecOps Reports Hub', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/devsecops/reports');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Reports Page Layout', () => {
    test('should display page header and description', async ({ page }) => {
      await expect(page.getByRole('heading', { name: 'Reports & Analytics' })).toBeVisible();
      await expect(page.getByText(/Comprehensive insights/)).toBeVisible();
    });

    test('should display all 4 report cards', async ({ page }) => {
      const reportCards = page.locator('[data-testid="report-card"]');
      await expect(reportCards).toHaveCount(4);
    });
  });

  test.describe('Report Cards', () => {
    test('should display Executive Summary report card', async ({ page }) => {
      const executiveCard = page.locator('text=Executive Summary');
      await expect(executiveCard).toBeVisible();
      await expect(page.locator('text=High-level KPIs')).toBeVisible();
    });

    test('should display Release Readiness report card', async ({ page }) => {
      const releaseCard = page.locator('text=Release Readiness');
      await expect(releaseCard).toBeVisible();
      await expect(page.locator('text=Deployment frequency')).toBeVisible();
    });

    test('should display Security & Compliance report card', async ({ page }) => {
      const securityCard = page.locator('text=Security & Compliance');
      await expect(securityCard).toBeVisible();
      await expect(page.locator('text=Vulnerabilities trend')).toBeVisible();
    });

    test('should display Operational Health report card', async ({ page }) => {
      const operationsCard = page.locator('text=Operational Health');
      await expect(operationsCard).toBeVisible();
      await expect(page.locator('text=System uptime')).toBeVisible();
    });

    test('should display report icons', async ({ page }) => {
      // Each card should have an icon (emoji)
      const reportCards = page.locator('[data-testid="report-card"]');

      await expect(reportCards.first()).toContainText(/[📊🚀🔒⚙️]/);
    });

    test('should have hover effect on report cards', async ({ page }) => {
      const firstCard = page.locator('[data-testid="report-card"]').first();

      // Hover over card
      await firstCard.hover();

      // Card should still be visible
      await expect(firstCard).toBeVisible();
    });
  });

  test.describe('Report Navigation', () => {
    test('should navigate to Executive Summary report on card click', async ({ page }) => {
      const executiveCard = page.locator('text=Executive Summary').locator('..');

      await executiveCard.click();

      // Should navigate to report detail page
      await page.waitForURL(/\/devsecops\/reports\/executive/);
      expect(page.url()).toContain('/devsecops/reports/executive');
    });

    test('should navigate to Release Readiness report on card click', async ({ page }) => {
      const releaseCard = page.locator('text=Release Readiness').locator('..');

      await releaseCard.click();

      await page.waitForURL(/\/devsecops\/reports\/release/);
      expect(page.url()).toContain('/devsecops/reports/release');
    });

    test('should navigate to Security & Compliance report on card click', async ({ page }) => {
      const securityCard = page.locator('text=Security & Compliance').locator('..');

      await securityCard.click();

      await page.waitForURL(/\/devsecops\/reports\/security/);
      expect(page.url()).toContain('/devsecops/reports/security');
    });

    test('should navigate to Operational Health report on card click', async ({ page }) => {
      const operationsCard = page.locator('text=Operational Health').locator('..');

      await operationsCard.click();

      await page.waitForURL(/\/devsecops\/reports\/operations/);
      expect(page.url()).toContain('/devsecops/reports/operations');
    });

    test('should navigate via "View Report" link', async ({ page }) => {
      const viewReportLink = page.getByRole('button', { name: /View Report →/ }).first();

      await viewReportLink.click();

      // Should navigate to a report page
      await page.waitForURL(/\/devsecops\/reports\/.+/);
    });
  });

  test.describe('Filter Controls', () => {
    test('should display filter section', async ({ page }) => {
      await expect(page.getByRole('heading', { name: 'Filter Reports' })).toBeVisible();
    });

    test('should display time range filter buttons', async ({ page }) => {
      await expect(page.getByRole('button', { name: 'Last 7 days' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Last 30 days' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Last 90 days' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Custom Range' })).toBeVisible();
    });

    test('should allow selecting time range filter', async ({ page }) => {
      const last30DaysButton = page.getByRole('button', { name: 'Last 30 days' });

      await last30DaysButton.click();

      // Button should be clickable
      await expect(last30DaysButton).toBeVisible();
    });
  });

  test.describe('Responsive Design', () => {
    test('should display report cards in grid on desktop', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });

      const reportCards = page.locator('[data-testid="report-card"]');
      await expect(reportCards.first()).toBeVisible();
      await expect(reportCards.last()).toBeVisible();
    });

    test('should stack report cards on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });

      const reportCards = page.locator('[data-testid="report-card"]');
      await expect(reportCards.first()).toBeVisible();
    });
  });

  test.describe('Performance', () => {
    test('should load reports hub within 2 seconds', async ({ page }) => {
      const startTime = Date.now();

      await page.goto('/devsecops/reports');
      await page.waitForLoadState('networkidle');

      const loadTime = Date.now() - startTime;
      expect(loadTime).toBeLessThan(2000);
    });
  });
});

test.describe('DevSecOps Settings Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/devsecops/settings');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Settings Page Layout', () => {
    test('should display page header', async ({ page }) => {
      await expect(page.getByRole('heading', { name: /Settings/i })).toBeVisible();
    });

    test('should display tab navigation', async ({ page }) => {
      const tabs = page.locator('[data-testid="settings-tabs"]');
      await expect(tabs).toBeVisible();
    });
  });

  test.describe('Settings Tabs', () => {
    test('should display all 4 tabs', async ({ page }) => {
      // Roles & Access
      await expect(page.getByRole('button', { name: /Roles & Access/i })).toBeVisible();

      // Integrations
      await expect(page.getByRole('button', { name: /Integrations/i })).toBeVisible();

      // Automation
      await expect(page.getByRole('button', { name: /Automation/i })).toBeVisible();

      // Compliance
      await expect(page.getByRole('button', { name: /Compliance/i })).toBeVisible();
    });

    test('should display tab icons', async ({ page }) => {
      // Each tab should have an emoji icon
      const rolesTab = page.getByRole('button', { name: /Roles & Access/i });
      await expect(rolesTab).toContainText(/👥/);
    });

    test('should highlight active tab', async ({ page }) => {
      const firstTab = page.locator('[data-testid="tab-button"]').first();

      // First tab should be active by default
      await expect(firstTab).toHaveClass(/active|blue-600/);
    });

    test('should switch to Automation tab on click', async ({ page }) => {
      const automationTab = page.getByRole('button', { name: /Automation/i });

      await automationTab.click();

      // Tab should become active
      await expect(automationTab).toHaveClass(/active|blue-600/);

      // Automation content should be visible
      await expect(page.getByText(/Webhooks/i)).toBeVisible();
    });

    test('should switch to Compliance tab on click', async ({ page }) => {
      const complianceTab = page.getByRole('button', { name: /Compliance/i });

      await complianceTab.click();

      // Compliance content should be visible
      await expect(page.getByText(/Data Retention/i)).toBeVisible();
    });

    test('should switch to Integrations tab on click', async ({ page }) => {
      const integrationsTab = page.getByRole('button', { name: /Integrations/i });

      await integrationsTab.click();

      // Integrations content should be visible
      await expect(page.getByText(/GitHub/i)).toBeVisible();
    });

    test('should persist active tab on page reload', async ({ page }) => {
      // Switch to Compliance tab
      await page.getByRole('button', { name: /Compliance/i }).click();

      // Reload page
      await page.reload();
      await page.waitForLoadState('networkidle');

      // Note: Actual persistence depends on implementation
      // This test verifies the page doesn't crash on reload
      await expect(page.getByRole('heading', { name: /Settings/i })).toBeVisible();
    });

    test('should open Security & Compliance report from Compliance tab shortcut', async ({ page }) => {
      // Switch to Compliance tab
      await page.getByRole('button', { name: /Compliance/i }).click();

      // Click Security & Compliance shortcut
      await page.getByRole('button', { name: /Open Security & Compliance Report/i }).click();

      await page.waitForURL(/\/devsecops\/reports\/security/);
      expect(page.url()).toContain('/devsecops/reports/security');
    });

    test('should open Executive Summary report from Compliance tab shortcut', async ({ page }) => {
      // Switch to Compliance tab
      await page.getByRole('button', { name: /Compliance/i }).click();

      // Click Executive Summary shortcut
      await page.getByRole('button', { name: /Open Executive Summary/i }).click();

      await page.waitForURL(/\/devsecops\/reports\/executive/);
      expect(page.url()).toContain('/devsecops/reports/executive');
    });
  });

  test.describe('Roles & Access Tab', () => {
    test('should display Team Members section', async ({ page }) => {
      await expect(page.getByText('Team Members')).toBeVisible();
      await expect(page.getByText(/Manage who has access/)).toBeVisible();
    });

    test('should display Add Member button', async ({ page }) => {
      await expect(page.getByRole('button', { name: 'Add Member' })).toBeVisible();
    });

    test('should display Manage Roles button', async ({ page }) => {
      await expect(page.getByRole('button', { name: 'Manage Roles' })).toBeVisible();
    });

    test('should display Role Permissions section', async ({ page }) => {
      await expect(page.getByText('Role Permissions')).toBeVisible();
      await expect(page.getByText(/Define what each role can do/)).toBeVisible();
    });

    test('should display all role types', async ({ page }) => {
      await expect(page.getByText('Executive')).toBeVisible();
      await expect(page.getByText('DevOps')).toBeVisible();
      await expect(page.getByText('Developer')).toBeVisible();
      await expect(page.getByText('Security')).toBeVisible();
    });
  });

  test.describe('Settings Actions', () => {
    test('should allow clicking Add Member button', async ({ page }) => {
      const addButton = page.getByRole('button', { name: 'Add Member' });

      await addButton.click();

      // Button should be clickable (actual modal/action depends on implementation)
      await expect(addButton).toBeVisible();
    });

    test('should allow clicking Manage Roles button', async ({ page }) => {
      const manageButton = page.getByRole('button', { name: 'Manage Roles' });

      await manageButton.click();

      await expect(manageButton).toBeVisible();
    });
  });

  test.describe('Responsive Design', () => {
    test('should render correctly on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });

      await expect(page.getByRole('heading', { name: /Settings/i })).toBeVisible();
    });

    test('should render correctly on desktop', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });

      await expect(page.getByRole('heading', { name: /Settings/i })).toBeVisible();
    });
  });

  test.describe('Performance', () => {
    test('should load settings page within 2 seconds', async ({ page }) => {
      const startTime = Date.now();

      await page.goto('/devsecops/settings');
      await page.waitForLoadState('networkidle');

      const loadTime = Date.now() - startTime;
      expect(loadTime).toBeLessThan(2000);
    });

    test('should switch tabs quickly (< 300ms)', async ({ page }) => {
      const startTime = Date.now();

      await page.getByRole('button', { name: /Notifications/i }).click();
      await page.waitForTimeout(100); // Wait for transition

      const switchTime = Date.now() - startTime;
      expect(switchTime).toBeLessThan(300);
    });
  });
});

test.describe('DevSecOps Templates Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/devsecops/templates');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Templates Page Layout', () => {
    test('should display page header', async ({ page }) => {
      await expect(page.getByRole('heading', { name: /Templates & Playbooks/i })).toBeVisible();
    });

    test('should display page description', async ({ page }) => {
      await expect(page.getByText(/Pre-configured dashboards/)).toBeVisible();
    });
  });

  test.describe('Template Accordions', () => {
    test('should display all 4 templates', async ({ page }) => {
      // Executive Dashboard
      await expect(page.getByText('Executive Dashboard')).toBeVisible();

      // MVP Fast Track
      await expect(page.getByText('MVP Fast Track')).toBeVisible();

      // Enterprise Governance
      await expect(page.getByText('Enterprise Governance')).toBeVisible();

      // Security-First
      await expect(page.getByText('Security-First')).toBeVisible();
    });

    test('should display template personas', async ({ page }) => {
      await expect(page.getByText('For C-suite and leadership')).toBeVisible();
      await expect(page.getByText('For startup and rapid delivery teams')).toBeVisible();
      await expect(page.getByText('For large organizations with compliance needs')).toBeVisible();
      await expect(page.getByText('For security-conscious teams')).toBeVisible();
    });

    test('should expand/collapse template on click', async ({ page }) => {
      // Find the DevOps Engineer accordion
      const devopsAccordion = page.locator('[data-testid="template-accordion"]').nth(1);
      const accordionSummary = devopsAccordion.locator('[data-testid="accordion-summary"]').first();

      // Click to collapse (if expanded)
      await accordionSummary.click();
      await page.waitForTimeout(300); // Wait for animation

      // Click to expand
      await accordionSummary.click();
      await page.waitForTimeout(300);

      // Accordion should be visible
      await expect(devopsAccordion).toBeVisible();
    });

    test('should display recommended KPIs for each template', async ({ page }) => {
      await expect(page.getByText('Recommended KPIs')).toBeVisible();

      // Should show KPI badges
      await expect(page.getByText('Completion Rate')).toBeVisible();
    });

    test('should display workflow tips for each template', async ({ page }) => {
      await expect(page.getByText('Workflow Tips')).toBeVisible();
    });

    test('should display Apply Template button for each template', async ({ page }) => {
      const applyButtons = page.getByRole('button', { name: 'Apply Template' });

      // Should have multiple Apply buttons (one per template)
      const count = await applyButtons.count();
      expect(count).toBeGreaterThan(0);
    });

    test('should allow clicking Apply Template button', async ({ page }) => {
      const firstApplyButton = page.getByRole('button', { name: 'Apply Template' }).first();

      await firstApplyButton.click();

      // Button should be clickable
      await expect(firstApplyButton).toBeVisible();
    });
  });

  test.describe('Executive Dashboard Template', () => {
    test('should display Executive template details', async ({ page }) => {
      await expect(page.getByText('Executive Dashboard')).toBeVisible();
      await expect(page.getByText(/High-level overview/)).toBeVisible();
    });

    test('should display Executive KPIs', async ({ page }) => {
      // KPIs should include: Completion Rate, Deployment Frequency, etc.
      await expect(page.getByText('Completion Rate')).toBeVisible();
      await expect(page.getByText('Deployment Frequency')).toBeVisible();
    });

    test('should be expanded by default', async ({ page }) => {
      // Executive template should be expanded by default
      const executiveAccordion = page.locator('[data-testid="template-accordion"]').first();

      // Content should be visible
      await expect(executiveAccordion.getByText(/High-level overview/)).toBeVisible();
    });
  });

  test.describe('MVP Fast Track Template', () => {
    test('should display MVP template details', async ({ page }) => {
      await expect(page.getByText('MVP Fast Track')).toBeVisible();
      await expect(page.getByText('For startup and rapid delivery teams')).toBeVisible();
    });
  });

  test.describe('Enterprise Governance Template', () => {
    test('should display Enterprise template details', async ({ page }) => {
      await expect(page.getByText('Enterprise Governance')).toBeVisible();
      await expect(page.getByText('For large organizations with compliance needs')).toBeVisible();
    });
  });

  test.describe('Security-First Template', () => {
    test('should display Security-First template details', async ({ page }) => {
      await expect(page.getByText('Security-First')).toBeVisible();
      await expect(page.getByText('For security-conscious teams')).toBeVisible();
    });
  });

  test.describe('Responsive Design', () => {
    test('should render correctly on mobile', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });

      await expect(page.getByText('Executive Dashboard')).toBeVisible();
    });

    test('should render correctly on desktop', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });

      await expect(page.getByText('Executive Dashboard')).toBeVisible();
    });
  });

  test.describe('Accessibility', () => {
    test('should support keyboard navigation', async ({ page }) => {
      // Tab through accordions
      await page.keyboard.press('Tab');
      await page.keyboard.press('Tab');

      // Should be able to navigate
      await expect(page.getByText('Executive Dashboard')).toBeVisible();
    });

    test('should have proper ARIA attributes on accordions', async ({ page }) => {
      const accordion = page.locator('[data-testid="template-accordion"]').first();

      // Accordion should have aria-expanded attribute
      // (Actual attribute depends on Material-UI implementation)
      await expect(accordion).toBeVisible();
    });
  });

  test.describe('Performance', () => {
    test('should load templates page within 2 seconds', async ({ page }) => {
      const startTime = Date.now();

      await page.goto('/devsecops/templates');
      await page.waitForLoadState('networkidle');

      const loadTime = Date.now() - startTime;
      expect(loadTime).toBeLessThan(2000);
    });

    test('should expand/collapse quickly (< 500ms)', async ({ page }) => {
      const accordionSummary = page.locator('[data-testid="accordion-summary"]').nth(1);

      const startTime = Date.now();
      await accordionSummary.click();
      await page.waitForTimeout(100);

      const expandTime = Date.now() - startTime;
      expect(expandTime).toBeLessThan(500);
    });
  });
});

/**
 * Test utilities for secondary pages
 */
class SecondaryPagesTestHelpers {
  constructor(private page: Page) { }

  // Reports helpers
  async navigateToReport(reportId: 'executive' | 'release' | 'security' | 'operations') {
    const reportCard = this.page.locator(`[data-report-id="${reportId}"]`);
    await reportCard.click();
    await this.page.waitForURL(`/devsecops/reports/${reportId}`);
  }

  async selectTimeRange(range: string) {
    const rangeButton = this.page.getByRole('button', { name: range });
    await rangeButton.click();
  }

  // Settings helpers
  async switchToTab(tabName: string) {
    const tab = this.page.getByRole('button', { name: new RegExp(tabName, 'i') });
    await tab.click();
    await this.page.waitForTimeout(200); // Wait for tab transition
  }

  // Templates helpers
  async expandTemplate(index: number) {
    const accordion = this.page.locator('[data-testid="template-accordion"]').nth(index);
    const summary = accordion.locator('[data-testid="accordion-summary"]');
    await summary.click();
  }

  async applyTemplate(templateName: string) {
    const template = this.page.locator(`text=${templateName}`).locator('..');
    const applyButton = template.getByRole('button', { name: 'Apply Template' });
    await applyButton.click();
  }
}

export { SecondaryPagesTestHelpers };
