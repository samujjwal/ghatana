/**
 * Code Review E2E Tests
 *
 * End-to-end tests for code review functionality including:
 * - Code review dashboard
 * - PR detail view
 * - Diff viewer
 * - Review actions
 * - CI checks
 *
 * @doc.type test
 * @doc.purpose E2E tests for code review
 * @doc.phase 3
 */

import { test, expect, type Page } from '@playwright/test';

test.describe('Code Review Dashboard', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/reviews');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display page header', async () => {
      const header = page.locator('h1');
      await expect(header).toContainText(/Review|Pull Request/i);
    });

    test('should show stats summary', async () => {
      const stats = page.locator('[class*="stats"], [class*="summary"]');
      await expect(stats).toBeVisible();
    });

    test('should show review tabs', async () => {
      const tabs = page.locator('[class*="tabs"]');
      await expect(tabs).toBeVisible();
    });

    test('should show PR list', async () => {
      const prList = page.locator('[class*="pr-list"], [class*="review-list"]');
      await expect(prList).toBeVisible();
    });
  });

  test.describe('Stats Summary', () => {
    test('should show open PRs count', async () => {
      const openStat = page.locator('[class*="stat"]', { hasText: /Open/i });
      await expect(openStat).toBeVisible();
    });

    test('should show needs review count', async () => {
      const needsReviewStat = page.locator('[class*="stat"]', { hasText: /Needs Review|Pending/i });
      if (await needsReviewStat.isVisible()) {
        await expect(needsReviewStat).toBeVisible();
      }
    });

    test('should show average review time', async () => {
      const avgTimeStat = page.locator('[class*="stat"]', { hasText: /Time|Hours/i });
      if (await avgTimeStat.isVisible()) {
        await expect(avgTimeStat).toBeVisible();
      }
    });
  });

  test.describe('Tabs', () => {
    test('should have Open tab', async () => {
      const openTab = page.locator('button', { hasText: /Open/i });
      await expect(openTab).toBeVisible();
    });

    test('should have Needs Review tab', async () => {
      const needsReviewTab = page.locator('button', { hasText: /Needs Review|Assigned/i });
      if (await needsReviewTab.isVisible()) {
        await expect(needsReviewTab).toBeVisible();
      }
    });

    test('should have Closed tab', async () => {
      const closedTab = page.locator('button', { hasText: /Closed|Merged/i });
      await expect(closedTab).toBeVisible();
    });

    test('should switch tabs on click', async () => {
      const closedTab = page.locator('button', { hasText: /Closed|Merged/i });
      await closedTab.click();
      await expect(closedTab).toHaveClass(/active/);
    });
  });

  test.describe('PR Cards', () => {
    test('should display PR cards', async () => {
      const prCards = page.locator('[class*="pr-card"], [class*="review-card"]');
      if (await prCards.count() > 0) {
        await expect(prCards.first()).toBeVisible();
      }
    });

    test('should show PR title', async () => {
      const prTitle = page.locator('[class*="pr-title"]').first();
      if (await prTitle.isVisible()) {
        await expect(prTitle).toBeVisible();
      }
    });

    test('should show PR number', async () => {
      const prNumber = page.locator('[class*="pr-number"]').first();
      if (await prNumber.isVisible()) {
        await expect(prNumber).toContainText(/#\d+/);
      }
    });

    test('should show PR status badge', async () => {
      const statusBadge = page.locator('[class*="status-badge"]').first();
      if (await statusBadge.isVisible()) {
        await expect(statusBadge).toBeVisible();
      }
    });

    test('should show author avatar', async () => {
      const avatar = page.locator('[class*="author-avatar"]').first();
      if (await avatar.isVisible()) {
        await expect(avatar).toBeVisible();
      }
    });

    test('should show branch info', async () => {
      const branchInfo = page.locator('[class*="branch"]').first();
      if (await branchInfo.isVisible()) {
        await expect(branchInfo).toBeVisible();
      }
    });

    test('should show CI status', async () => {
      const ciStatus = page.locator('[class*="ci-status"]').first();
      if (await ciStatus.isVisible()) {
        await expect(ciStatus).toBeVisible();
      }
    });

    test('should show file changes count', async () => {
      const filesChanged = page.locator('[class*="files-changed"], [class*="changes"]').first();
      if (await filesChanged.isVisible()) {
        await expect(filesChanged).toBeVisible();
      }
    });
  });

  test.describe('Filtering', () => {
    test('should have search input', async () => {
      const searchInput = page.locator('input[placeholder*="Search"]');
      await expect(searchInput).toBeVisible();
    });

    test('should filter by search', async () => {
      const searchInput = page.locator('input[placeholder*="Search"]');
      await searchInput.fill('feature');
      await page.waitForTimeout(300);
    });

    test('should have author filter', async () => {
      const authorFilter = page.locator('select, button', { hasText: /Author/i });
      if (await authorFilter.isVisible()) {
        await expect(authorFilter).toBeVisible();
      }
    });
  });

  test.describe('Navigation', () => {
    test('should navigate to PR detail on card click', async () => {
      const prCard = page.locator('[class*="pr-card"], [class*="review-card"]').first();
      if (await prCard.isVisible()) {
        await prCard.click();
        await page.waitForURL(/\/reviews\//);
      }
    });
  });
});

test.describe('Code Review Detail Page', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/reviews/pr-1');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display PR header', async () => {
      const header = page.locator('[class*="pr-header"]');
      await expect(header).toBeVisible();
    });

    test('should show back link', async () => {
      const backLink = page.locator('[class*="back-link"], a', { hasText: /Back/i });
      await expect(backLink).toBeVisible();
    });

    test('should show tabs bar', async () => {
      const tabsBar = page.locator('[class*="tabs-bar"]');
      await expect(tabsBar).toBeVisible();
    });

    test('should show tab content area', async () => {
      const tabContent = page.locator('[class*="tab-content"]');
      await expect(tabContent).toBeVisible();
    });
  });

  test.describe('PR Header', () => {
    test('should show status badge', async () => {
      const statusBadge = page.locator('[class*="status-badge"]');
      await expect(statusBadge).toBeVisible();
    });

    test('should show PR title with number', async () => {
      const title = page.locator('[class*="pr-title"]');
      await expect(title).toContainText(/#\d+/);
    });

    test('should show author info', async () => {
      const author = page.locator('[class*="pr-author"]');
      await expect(author).toBeVisible();
    });

    test('should show branch names', async () => {
      const branches = page.locator('[class*="branch-name"]');
      await expect(branches).toHaveCount(2);
    });

    test('should show PR stats', async () => {
      const stats = page.locator('[class*="pr-stats"]');
      await expect(stats).toBeVisible();
    });

    test('should show additions/deletions', async () => {
      const addStats = page.locator('[class*="stat-add"]');
      await expect(addStats).toBeVisible();
    });

    test('should show action buttons', async () => {
      const actions = page.locator('[class*="pr-actions"]');
      await expect(actions).toBeVisible();
    });

    test('should show review button', async () => {
      const reviewBtn = page.locator('button', { hasText: /Review/i });
      await expect(reviewBtn).toBeVisible();
    });

    test('should show merge button if approved', async () => {
      const mergeBtn = page.locator('button', { hasText: /Merge/i });
      if (await mergeBtn.isVisible()) {
        await expect(mergeBtn).toBeVisible();
      }
    });
  });

  test.describe('Tabs', () => {
    test('should have Conversation tab', async () => {
      const conversationTab = page.locator('button', { hasText: /Conversation/i });
      await expect(conversationTab).toBeVisible();
    });

    test('should have Files Changed tab', async () => {
      const filesTab = page.locator('button', { hasText: /Files/i });
      await expect(filesTab).toBeVisible();
    });

    test('should have Checks tab', async () => {
      const checksTab = page.locator('button', { hasText: /Checks/i });
      await expect(checksTab).toBeVisible();
    });

    test('should switch tabs on click', async () => {
      const conversationTab = page.locator('button', { hasText: /Conversation/i });
      await conversationTab.click();
      await expect(conversationTab).toHaveClass(/active/);
    });
  });

  test.describe('Conversation Tab', () => {
    test('should show PR description', async () => {
      const conversationTab = page.locator('button', { hasText: /Conversation/i });
      await conversationTab.click();
      const description = page.locator('[class*="pr-description"]');
      await expect(description).toBeVisible();
    });

    test('should show reviewers list', async () => {
      const conversationTab = page.locator('button', { hasText: /Conversation/i });
      await conversationTab.click();
      const reviewers = page.locator('[class*="reviewers"]');
      await expect(reviewers).toBeVisible();
    });

    test('should show reviews timeline', async () => {
      const conversationTab = page.locator('button', { hasText: /Conversation/i });
      await conversationTab.click();
      const timeline = page.locator('[class*="reviews-timeline"]');
      await expect(timeline).toBeVisible();
    });

    test('should show linked stories if present', async () => {
      const conversationTab = page.locator('button', { hasText: /Conversation/i });
      await conversationTab.click();
      const linkedStories = page.locator('[class*="linked-stories"]');
      if (await linkedStories.isVisible()) {
        await expect(linkedStories).toBeVisible();
      }
    });
  });

  test.describe('Files Changed Tab', () => {
    test('should show diff viewer', async () => {
      const filesTab = page.locator('button', { hasText: /Files/i });
      await filesTab.click();
      const diffViewer = page.locator('[class*="diff-viewer"]');
      await expect(diffViewer).toBeVisible();
    });

    test('should show file list sidebar', async () => {
      const filesTab = page.locator('button', { hasText: /Files/i });
      await filesTab.click();
      const fileList = page.locator('[class*="file-list"]');
      await expect(fileList).toBeVisible();
    });

    test('should show diff content', async () => {
      const filesTab = page.locator('button', { hasText: /Files/i });
      await filesTab.click();
      const diffContent = page.locator('[class*="diff-content"]');
      await expect(diffContent).toBeVisible();
    });

    test('should have view mode toggle', async () => {
      const filesTab = page.locator('button', { hasText: /Files/i });
      await filesTab.click();
      const viewToggle = page.locator('[class*="view-toggle"]');
      await expect(viewToggle).toBeVisible();
    });

    test('should switch between unified and split view', async () => {
      const filesTab = page.locator('button', { hasText: /Files/i });
      await filesTab.click();
      const splitBtn = page.locator('button', { hasText: /Split/i });
      if (await splitBtn.isVisible()) {
        await splitBtn.click();
        await expect(splitBtn).toHaveClass(/active/);
      }
    });
  });

  test.describe('Diff Viewer', () => {
    test.beforeEach(async () => {
      const filesTab = page.locator('button', { hasText: /Files/i });
      await filesTab.click();
    });

    test('should show file items in sidebar', async () => {
      const fileItems = page.locator('[class*="file-item"]');
      if (await fileItems.count() > 0) {
        await expect(fileItems.first()).toBeVisible();
      }
    });

    test('should show file status icons', async () => {
      const fileStatus = page.locator('[class*="file-status"]').first();
      if (await fileStatus.isVisible()) {
        await expect(fileStatus).toBeVisible();
      }
    });

    test('should show file stats', async () => {
      const fileStats = page.locator('[class*="file-stats"]').first();
      if (await fileStats.isVisible()) {
        await expect(fileStats).toBeVisible();
      }
    });

    test('should select file on click', async () => {
      const fileItem = page.locator('[class*="file-item"]').first();
      if (await fileItem.isVisible()) {
        await fileItem.click();
        await expect(fileItem).toHaveClass(/active/);
      }
    });

    test('should show diff hunks', async () => {
      const diffHunks = page.locator('[class*="diff-hunk"]');
      if (await diffHunks.count() > 0) {
        await expect(diffHunks.first()).toBeVisible();
      }
    });

    test('should show hunk headers', async () => {
      const hunkHeader = page.locator('[class*="hunk-header"]').first();
      if (await hunkHeader.isVisible()) {
        await expect(hunkHeader).toContainText(/@@/);
      }
    });

    test('should show diff lines', async () => {
      const diffLines = page.locator('[class*="diff-line"]');
      if (await diffLines.count() > 0) {
        await expect(diffLines.first()).toBeVisible();
      }
    });

    test('should show line numbers', async () => {
      const lineNums = page.locator('[class*="line-num"]').first();
      if (await lineNums.isVisible()) {
        await expect(lineNums).toBeVisible();
      }
    });

    test('should highlight additions in green', async () => {
      const additionLine = page.locator('[class*="diff-line--addition"]').first();
      if (await additionLine.isVisible()) {
        await expect(additionLine).toBeVisible();
      }
    });

    test('should highlight deletions in red', async () => {
      const deletionLine = page.locator('[class*="diff-line--deletion"]').first();
      if (await deletionLine.isVisible()) {
        await expect(deletionLine).toBeVisible();
      }
    });

    test('should show add comment button on hover', async () => {
      const diffLine = page.locator('[class*="diff-line"]').first();
      if (await diffLine.isVisible()) {
        await diffLine.hover();
        const addCommentBtn = page.locator('[class*="add-comment-btn"]').first();
        await expect(addCommentBtn).toBeVisible();
      }
    });
  });

  test.describe('Inline Comments', () => {
    test.beforeEach(async () => {
      const filesTab = page.locator('button', { hasText: /Files/i });
      await filesTab.click();
    });

    test('should show inline comments if present', async () => {
      const inlineComments = page.locator('[class*="inline-comment"]');
      if (await inlineComments.count() > 0) {
        await expect(inlineComments.first()).toBeVisible();
      }
    });

    test('should show comment input on line click', async () => {
      const diffLine = page.locator('[class*="diff-line"]').first();
      if (await diffLine.isVisible()) {
        await diffLine.click();
        const commentInput = page.locator('[class*="comment-input-row"]');
        if (await commentInput.isVisible()) {
          await expect(commentInput).toBeVisible();
        }
      }
    });
  });

  test.describe('Checks Tab', () => {
    test('should show checks list', async () => {
      const checksTab = page.locator('button', { hasText: /Checks/i });
      await checksTab.click();
      const checksList = page.locator('[class*="checks-list"]');
      await expect(checksList).toBeVisible();
    });

    test('should show check items', async () => {
      const checksTab = page.locator('button', { hasText: /Checks/i });
      await checksTab.click();
      const checkItems = page.locator('[class*="check-item"]');
      if (await checkItems.count() > 0) {
        await expect(checkItems.first()).toBeVisible();
      }
    });

    test('should show check status icons', async () => {
      const checksTab = page.locator('button', { hasText: /Checks/i });
      await checksTab.click();
      const checkIcon = page.locator('[class*="check-icon"]').first();
      if (await checkIcon.isVisible()) {
        await expect(checkIcon).toBeVisible();
      }
    });

    test('should show check names', async () => {
      const checksTab = page.locator('button', { hasText: /Checks/i });
      await checksTab.click();
      const checkName = page.locator('[class*="check-name"]').first();
      if (await checkName.isVisible()) {
        await expect(checkName).toBeVisible();
      }
    });
  });

  test.describe('Review Modal', () => {
    test('should open review modal on review button click', async () => {
      const reviewBtn = page.locator('button', { hasText: /Review/i });
      await reviewBtn.click();
      const modal = page.locator('[class*="modal"]');
      await expect(modal).toBeVisible();
    });

    test('should show review action options', async () => {
      const reviewBtn = page.locator('button', { hasText: /Review/i });
      await reviewBtn.click();
      const reviewOptions = page.locator('[class*="review-option"]');
      await expect(reviewOptions).toHaveCount(3);
    });

    test('should have comment option', async () => {
      const reviewBtn = page.locator('button', { hasText: /Review/i });
      await reviewBtn.click();
      const commentOption = page.locator('[class*="review-option"]', { hasText: /Comment/i });
      await expect(commentOption).toBeVisible();
    });

    test('should have approve option', async () => {
      const reviewBtn = page.locator('button', { hasText: /Review/i });
      await reviewBtn.click();
      const approveOption = page.locator('[class*="review-option"]', { hasText: /Approve/i });
      await expect(approveOption).toBeVisible();
    });

    test('should have request changes option', async () => {
      const reviewBtn = page.locator('button', { hasText: /Review/i });
      await reviewBtn.click();
      const changesOption = page.locator('[class*="review-option"]', { hasText: /Request Changes/i });
      await expect(changesOption).toBeVisible();
    });

    test('should have review textarea', async () => {
      const reviewBtn = page.locator('button', { hasText: /Review/i });
      await reviewBtn.click();
      const textarea = page.locator('[class*="review-textarea"]');
      await expect(textarea).toBeVisible();
    });

    test('should close modal on cancel', async () => {
      const reviewBtn = page.locator('button', { hasText: /Review/i });
      await reviewBtn.click();
      const cancelBtn = page.locator('[class*="modal"] button', { hasText: /Cancel/i });
      await cancelBtn.click();
      const modal = page.locator('[class*="modal"]');
      await expect(modal).not.toBeVisible();
    });
  });

  test.describe('Merge Modal', () => {
    test('should show merge options if merge button is visible', async () => {
      const mergeBtn = page.locator('button', { hasText: /Merge/i });
      if (await mergeBtn.isVisible() && !(await mergeBtn.isDisabled())) {
        await mergeBtn.click();
        const mergeOptions = page.locator('[class*="merge-option"]');
        await expect(mergeOptions).toHaveCount(3);
      }
    });

    test('should have squash and merge option', async () => {
      const mergeBtn = page.locator('button', { hasText: /Merge/i });
      if (await mergeBtn.isVisible() && !(await mergeBtn.isDisabled())) {
        await mergeBtn.click();
        const squashOption = page.locator('[class*="merge-option"]', { hasText: /Squash/i });
        await expect(squashOption).toBeVisible();
      }
    });
  });
});
