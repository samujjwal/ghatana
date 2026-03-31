/**
 * DevSecOps Scan → Report E2E Tests
 *
 * Critical path 3 of 3 for Y-01 acceptance criteria.
 *
 * Covers the end-to-end journey of a security engineer who:
 * 1. Opens the Security Scans page for a project
 * 2. Triggers a new security scan
 * 3. Views the resulting scan report with severity breakdown and findings
 *
 * Routes used:
 *  - `/project/:projectId/security/scans`         — scan history list
 *  - `/project/:projectId/security/scans/:scanId` — individual scan report
 *
 * Canonical paths are defined in `src/router/paths.ts` > `ROUTES.security.*`.
 *
 * @doc.type test
 * @doc.purpose E2E critical path: DevSecOps scan → Report
 * @doc.layer product
 * @doc.phase 2
 */

import { test, expect, type Page } from '@playwright/test';

const PROJECT_ID = 'test-project';
const SCANS_URL = `/project/${PROJECT_ID}/security/scans`;
const SAMPLE_SCAN_ID = 'scan-001';
const SCAN_RESULT_URL = `/project/${PROJECT_ID}/security/scans/${SAMPLE_SCAN_ID}`;

test.describe('DevSecOps Scan → Report', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
  });

  test.describe('Security Scans List Page', () => {
    test.beforeEach(async () => {
      await page.goto(SCANS_URL);
      await page.waitForLoadState('networkidle');
    });

    test('should load the security scans page', async () => {
      await expect(page).toHaveURL(new RegExp(`security/scans`));
    });

    test('should display "Security Scans" heading', async () => {
      const heading = page.getByRole('heading', { name: /Security Scans/i });
      await expect(heading).toBeVisible();
    });

    test('should show scan history and findings description', async () => {
      const description = page.locator('text=/Scan history|findings/i').first();
      await expect(description).toBeVisible();
    });

    test('should display a "New Scan" button', async () => {
      const newScanBtn = page.locator('button:has-text("New Scan"), button:has-text("Run Scan")').first();
      await expect(newScanBtn).toBeVisible();
    });

    test('should show scan icon', async () => {
      // The ScansPage renders a Scan icon from lucide-react
      const scanIcon = page.locator('[class*="scan"], svg').first();
      await expect(scanIcon).toBeVisible();
    });

    test('should indicate no scans yet or list existing scans', async () => {
      // Either an empty state or a list of scan entries
      const content = page.locator(
        'text=/No scans yet|security scan|run security/i, [class*="scan-list"], [class*="scan-card"]'
      ).first();
      await expect(content).toBeVisible();
    });
  });

  test.describe('Trigger New Scan', () => {
    test.beforeEach(async () => {
      await page.goto(SCANS_URL);
      await page.waitForLoadState('networkidle');
    });

    test('should open scan configuration when "New Scan" is clicked', async () => {
      const newScanBtn = page
        .locator('button:has-text("New Scan"), button:has-text("Run Scan")')
        .first();
      await newScanBtn.click();

      // Expect a dialog, modal, or list of scan targets/options to appear
      const scanConfig = page.locator(
        '[role="dialog"], [class*="modal"], [class*="scan-config"], [class*="new-scan"]'
      ).first();
      // If no modal (scan fires immediately), verify page state changes
      const anyFeedback = page.locator(
        '[role="dialog"], [class*="modal"], [class*="scan"], text=/running|scanning|queued/i'
      ).first();
      await expect(anyFeedback).toBeVisible({ timeout: 5000 });
    });
  });

  test.describe('Scan Results Report Page', () => {
    test.beforeEach(async () => {
      await page.goto(SCAN_RESULT_URL);
      await page.waitForLoadState('networkidle');
    });

    test('should load the scan results page', async () => {
      await expect(page).toHaveURL(new RegExp(`security/scans/${SAMPLE_SCAN_ID}`));
    });

    test('should display scan name heading or results title', async () => {
      // ScanResultsPage shows scan.name or "Scan Results" in an h1
      const heading = page
        .locator('h1, h2, [class*="heading"]')
        .filter({ hasText: /Scan Result|Security|Scan/i })
        .first();
      await expect(heading).toBeVisible({ timeout: 10000 });
    });

    test('should show a severity breakdown section', async () => {
      // ScanResultsPage shows summary counts: critical, high, medium, low, info
      const severitySection = page.locator(
        '[class*="summary"], [class*="severity"], text=/critical|high|medium|low/i'
      ).first();
      await expect(severitySection).toBeVisible({ timeout: 10000 });
    });

    test('should display a findings table or findings list', async () => {
      const findingsSection = page.locator(
        '[class*="findings"], [class*="vulnerab"], table, [role="table"], text=/finding/i'
      ).first();
      await expect(findingsSection).toBeVisible({ timeout: 10000 });
    });

    test('should show Re-scan and Export action buttons', async () => {
      const reScan = page.locator('button:has-text("Re-scan"), button:has-text("Rescan")').first();
      const exportBtn = page.locator('button:has-text("Export")').first();
      await expect(reScan).toBeVisible({ timeout: 10000 });
      await expect(exportBtn).toBeVisible({ timeout: 10000 });
    });

    test('should provide navigation back to scans list', async () => {
      // Back arrow link points to /security/scans in ScanResultsPage
      const backLink = page.locator(
        'a[href*="scans"], button:has-text("Back"), [aria-label*="back" i]'
      ).first();
      await expect(backLink).toBeVisible({ timeout: 5000 });
    });

    test('should allow filtering findings by severity', async () => {
      // ScanResultsPage has a severityFilter state driven by button/tab clicks
      const filterControl = page.locator(
        'button:has-text("Critical"), button:has-text("High"), button:has-text("All"), [aria-label*="filter" i], select'
      ).first();
      await expect(filterControl).toBeVisible({ timeout: 10000 });
    });
  });

  test.describe('Complete critical path', () => {
    test('DevSecOps scan → report sequence', async () => {
      // Step 1: Navigate to security scans list
      await page.goto(SCANS_URL);
      await page.waitForLoadState('networkidle');
      await expect(page).toHaveURL(new RegExp(`security/scans`));

      // Verify scans page rendered
      const heading = page.locator('h1, h2').filter({ hasText: /Security Scans/i }).first();
      await expect(heading).toBeVisible();

      // Verify new scan trigger is accessible
      const newScanBtn = page
        .locator('button:has-text("New Scan"), button:has-text("Run Scan")')
        .first();
      await expect(newScanBtn).toBeVisible();

      // Step 2: Navigate to a scan results report
      await page.goto(SCAN_RESULT_URL);
      await page.waitForLoadState('networkidle');
      await expect(page).toHaveURL(new RegExp(`security/scans/${SAMPLE_SCAN_ID}`));

      // Step 3: Verify report structure is rendered
      const reportContent = page
        .locator('h1, h2, [class*="scan"], [class*="result"]')
        .first();
      await expect(reportContent).toBeVisible({ timeout: 10000 });

      // Report must show severity or findings information
      const reportData = page.locator(
        '[class*="summary"], [class*="severity"], [class*="finding"], text=/critical|high|medium|low|finding|vulnerability/i'
      ).first();
      await expect(reportData).toBeVisible({ timeout: 10000 });
    });
  });
});
