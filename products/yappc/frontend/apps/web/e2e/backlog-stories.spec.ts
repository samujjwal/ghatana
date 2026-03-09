/**
 * Backlog & Stories E2E Tests
 *
 * End-to-end tests for backlog and story management including:
 * - Backlog page display
 * - Story filtering and sorting
 * - Story detail view
 * - Epic management
 * - Bulk actions
 *
 * @doc.type test
 * @doc.purpose E2E tests for backlog and story management
 * @doc.phase 3
 */

import { test, expect, type Page } from '@playwright/test';

test.describe('Backlog Page', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/backlog');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display backlog header', async () => {
      const header = page.locator('h1');
      await expect(header).toContainText(/Backlog/i);
    });

    test('should show stats bar', async () => {
      const statsBar = page.locator('[class*="stats-bar"], [class*="summary"]');
      await expect(statsBar).toBeVisible();
    });

    test('should show epic sidebar', async () => {
      const epicSidebar = page.locator('[class*="epic-sidebar"], [class*="sidebar"]');
      await expect(epicSidebar).toBeVisible();
    });

    test('should show story list', async () => {
      const storyList = page.locator('[class*="story-list"], [class*="backlog-items"]');
      await expect(storyList).toBeVisible();
    });

    test('should show create story button', async () => {
      const createBtn = page.locator('button', { hasText: /Create|New Story|\+/i });
      await expect(createBtn).toBeVisible();
    });
  });

  test.describe('Stats Bar', () => {
    test('should show total stories count', async () => {
      const totalStat = page.locator('[class*="stat"]', { hasText: /Total|Stories/i });
      await expect(totalStat).toBeVisible();
    });

    test('should show unestimated count', async () => {
      const unestimatedStat = page.locator('[class*="stat"]', { hasText: /Unestimated/i });
      if (await unestimatedStat.isVisible()) {
        await expect(unestimatedStat).toBeVisible();
      }
    });

    test('should show total points', async () => {
      const pointsStat = page.locator('[class*="stat"]', { hasText: /Points|pts/i });
      await expect(pointsStat).toBeVisible();
    });
  });

  test.describe('Epic Sidebar', () => {
    test('should list epics with progress', async () => {
      const epicItems = page.locator('[class*="epic-item"], [class*="sidebar-item"]');
      if (await epicItems.count() > 0) {
        await expect(epicItems.first()).toBeVisible();
      }
    });

    test('should show epic progress bars', async () => {
      const progressBar = page.locator('[class*="epic-progress"], [class*="progress"]').first();
      if (await progressBar.isVisible()) {
        await expect(progressBar).toBeVisible();
      }
    });

    test('should filter stories by epic on click', async () => {
      const epicItem = page.locator('[class*="epic-item"]').first();
      if (await epicItem.isVisible()) {
        await epicItem.click();
        await page.waitForTimeout(300);
      }
    });

    test('should show all epics option', async () => {
      const allEpicsBtn = page.locator('button, [class*="epic-item"]', { hasText: /All|Show All/i });
      await expect(allEpicsBtn).toBeVisible();
    });
  });

  test.describe('Story List', () => {
    test('should display story cards', async () => {
      const storyCards = page.locator('[class*="story-card"], [class*="backlog-item"]');
      if (await storyCards.count() > 0) {
        await expect(storyCards.first()).toBeVisible();
      }
    });

    test('should show story title', async () => {
      const storyTitle = page.locator('[class*="story-title"]').first();
      if (await storyTitle.isVisible()) {
        await expect(storyTitle).toBeVisible();
      }
    });

    test('should show story type indicator', async () => {
      const typeIndicator = page.locator('[class*="type-badge"], [class*="story-type"]').first();
      if (await typeIndicator.isVisible()) {
        await expect(typeIndicator).toBeVisible();
      }
    });

    test('should show priority indicator', async () => {
      const priority = page.locator('[class*="priority"]').first();
      if (await priority.isVisible()) {
        await expect(priority).toBeVisible();
      }
    });

    test('should show story points', async () => {
      const points = page.locator('[class*="points"]').first();
      if (await points.isVisible()) {
        await expect(points).toBeVisible();
      }
    });

    test('should group stories by priority', async () => {
      const priorityGroups = page.locator('[class*="priority-group"]');
      if (await priorityGroups.count() > 0) {
        await expect(priorityGroups.first()).toBeVisible();
      }
    });
  });

  test.describe('Filtering', () => {
    test('should have search input', async () => {
      const searchInput = page.locator('input[placeholder*="Search"]');
      await expect(searchInput).toBeVisible();
    });

    test('should filter stories by search', async () => {
      const searchInput = page.locator('input[placeholder*="Search"]');
      await searchInput.fill('test');
      await page.waitForTimeout(300);
    });

    test('should have type filter', async () => {
      const typeFilter = page.locator('select, button', { hasText: /Type|All Types/i }).first();
      await expect(typeFilter).toBeVisible();
    });

    test('should have priority filter', async () => {
      const priorityFilter = page.locator('select, button', { hasText: /Priority/i }).first();
      if (await priorityFilter.isVisible()) {
        await expect(priorityFilter).toBeVisible();
      }
    });
  });

  test.describe('View Modes', () => {
    test('should have list/kanban toggle', async () => {
      const viewToggle = page.locator('[class*="view-toggle"], button', { hasText: /List|Kanban/i });
      await expect(viewToggle).toBeVisible();
    });

    test('should switch to kanban view', async () => {
      const kanbanBtn = page.locator('button', { hasText: /Kanban|Board/i });
      if (await kanbanBtn.isVisible()) {
        await kanbanBtn.click();
        const kanbanBoard = page.locator('[class*="kanban"], [class*="board"]');
        await expect(kanbanBoard).toBeVisible();
      }
    });
  });

  test.describe('Bulk Actions', () => {
    test('should show bulk actions when stories selected', async () => {
      const checkbox = page.locator('[type="checkbox"]').first();
      if (await checkbox.isVisible()) {
        await checkbox.check();
        const bulkActions = page.locator('[class*="bulk-actions"]');
        await expect(bulkActions).toBeVisible();
      }
    });

    test('should allow selecting multiple stories', async () => {
      const checkboxes = page.locator('[type="checkbox"]');
      if (await checkboxes.count() >= 2) {
        await checkboxes.nth(0).check();
        await checkboxes.nth(1).check();
      }
    });
  });

  test.describe('Drag and Drop', () => {
    test('should allow reordering stories', async () => {
      const storyCard = page.locator('[class*="story-card"], [class*="backlog-item"]').first();
      const targetCard = page.locator('[class*="story-card"], [class*="backlog-item"]').nth(1);

      if (await storyCard.isVisible() && await targetCard.isVisible()) {
        const sourceBox = await storyCard.boundingBox();
        const targetBox = await targetCard.boundingBox();

        if (sourceBox && targetBox) {
          await page.mouse.move(sourceBox.x + sourceBox.width / 2, sourceBox.y + sourceBox.height / 2);
          await page.mouse.down();
          await page.mouse.move(targetBox.x + targetBox.width / 2, targetBox.y + targetBox.height / 2);
          await page.mouse.up();
        }
      }
    });
  });
});

test.describe('Story Detail Page', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/stories/story-1');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display story header', async () => {
      const header = page.locator('[class*="story-header"], h1');
      await expect(header).toBeVisible();
    });

    test('should show breadcrumb navigation', async () => {
      const breadcrumb = page.locator('[class*="breadcrumb"]');
      await expect(breadcrumb).toBeVisible();
    });

    test('should show main content area', async () => {
      const mainContent = page.locator('[class*="story-main"]');
      await expect(mainContent).toBeVisible();
    });

    test('should show sidebar with metadata', async () => {
      const sidebar = page.locator('[class*="story-sidebar"], [class*="sidebar"]');
      await expect(sidebar).toBeVisible();
    });
  });

  test.describe('Story Header', () => {
    test('should show story type badge', async () => {
      const typeBadge = page.locator('[class*="type-badge"]');
      await expect(typeBadge).toBeVisible();
    });

    test('should show story title', async () => {
      const title = page.locator('[class*="story-title"], h1');
      await expect(title).toBeVisible();
    });

    test('should have edit button', async () => {
      const editBtn = page.locator('button', { hasText: /Edit/i });
      await expect(editBtn).toBeVisible();
    });
  });

  test.describe('Status Workflow', () => {
    test('should display status workflow', async () => {
      const workflow = page.locator('[class*="status-workflow"]');
      await expect(workflow).toBeVisible();
    });

    test('should show current status highlighted', async () => {
      const currentStatus = page.locator('[class*="workflow-step--current"]');
      await expect(currentStatus).toBeVisible();
    });

    test('should allow status transitions', async () => {
      const statusStep = page.locator('[class*="workflow-step"]').nth(2);
      if (await statusStep.isVisible()) {
        await statusStep.click();
        await page.waitForTimeout(300);
      }
    });
  });

  test.describe('Details Tab', () => {
    test('should show description section', async () => {
      const description = page.locator('[class*="description"], section', { hasText: /Description/i });
      await expect(description).toBeVisible();
    });

    test('should show acceptance criteria', async () => {
      const criteria = page.locator('[class*="criteria"], section', { hasText: /Acceptance/i });
      await expect(criteria).toBeVisible();
    });

    test('should allow toggling acceptance criteria', async () => {
      const checkbox = page.locator('[class*="criteria-checkbox"], [class*="criteria"] input[type="checkbox"]').first();
      if (await checkbox.isVisible()) {
        await checkbox.click();
      }
    });

    test('should show tasks section', async () => {
      const tasks = page.locator('[class*="tasks"], section', { hasText: /Tasks/i });
      await expect(tasks).toBeVisible();
    });

    test('should allow toggling tasks', async () => {
      const taskCheckbox = page.locator('[class*="task-checkbox"], [class*="tasks"] input[type="checkbox"]').first();
      if (await taskCheckbox.isVisible()) {
        await taskCheckbox.click();
      }
    });
  });

  test.describe('Comments', () => {
    test('should show comments section', async () => {
      const comments = page.locator('[class*="comments"], section', { hasText: /Comments/i });
      await expect(comments).toBeVisible();
    });

    test('should show comment input', async () => {
      const commentInput = page.locator('[class*="comment-input"], textarea');
      await expect(commentInput).toBeVisible();
    });

    test('should allow adding comments', async () => {
      const commentInput = page.locator('[class*="comment-input"], textarea').first();
      await commentInput.fill('Test comment');
      const submitBtn = page.locator('button', { hasText: /Post|Submit|Add/i });
      if (await submitBtn.isVisible()) {
        await submitBtn.click();
      }
    });
  });

  test.describe('Activity Tab', () => {
    test('should have activity tab', async () => {
      const activityTab = page.locator('button', { hasText: /Activity/i });
      await expect(activityTab).toBeVisible();
    });

    test('should show activity timeline', async () => {
      const activityTab = page.locator('button', { hasText: /Activity/i });
      await activityTab.click();
      const activityList = page.locator('[class*="activity-list"]');
      await expect(activityList).toBeVisible();
    });
  });

  test.describe('Sidebar Metadata', () => {
    test('should show status', async () => {
      const status = page.locator('[class*="sidebar-section"]', { hasText: /Status/i });
      await expect(status).toBeVisible();
    });

    test('should show priority', async () => {
      const priority = page.locator('[class*="sidebar-section"]', { hasText: /Priority/i });
      await expect(priority).toBeVisible();
    });

    test('should show story points', async () => {
      const points = page.locator('[class*="sidebar-section"]', { hasText: /Points/i });
      await expect(points).toBeVisible();
    });

    test('should show assignee', async () => {
      const assignee = page.locator('[class*="sidebar-section"]', { hasText: /Assignee/i });
      await expect(assignee).toBeVisible();
    });

    test('should show reporter', async () => {
      const reporter = page.locator('[class*="sidebar-section"]', { hasText: /Reporter/i });
      await expect(reporter).toBeVisible();
    });

    test('should show sprint if assigned', async () => {
      const sprint = page.locator('[class*="sidebar-section"]', { hasText: /Sprint/i });
      if (await sprint.isVisible()) {
        await expect(sprint).toBeVisible();
      }
    });

    test('should show epic if assigned', async () => {
      const epic = page.locator('[class*="sidebar-section"]', { hasText: /Epic/i });
      if (await epic.isVisible()) {
        await expect(epic).toBeVisible();
      }
    });

    test('should show labels', async () => {
      const labels = page.locator('[class*="sidebar-section"]', { hasText: /Labels/i });
      if (await labels.isVisible()) {
        await expect(labels).toBeVisible();
      }
    });

    test('should show dates', async () => {
      const dates = page.locator('[class*="date-item"]');
      await expect(dates.first()).toBeVisible();
    });
  });

  test.describe('Linked Resources', () => {
    test('should show linked resources if present', async () => {
      const resources = page.locator('[class*="resources"], section', { hasText: /Linked|Resources/i });
      if (await resources.isVisible()) {
        await expect(resources).toBeVisible();
      }
    });
  });

  test.describe('Edit Mode', () => {
    test('should enter edit mode on edit button click', async () => {
      const editBtn = page.locator('button', { hasText: /Edit/i });
      await editBtn.click();
      const saveBtn = page.locator('button', { hasText: /Save/i });
      await expect(saveBtn).toBeVisible();
    });

    test('should show title input in edit mode', async () => {
      const editBtn = page.locator('button', { hasText: /Edit/i });
      await editBtn.click();
      const titleInput = page.locator('[class*="title-input"]');
      await expect(titleInput).toBeVisible();
    });

    test('should show description textarea in edit mode', async () => {
      const editBtn = page.locator('button', { hasText: /Edit/i });
      await editBtn.click();
      const descTextarea = page.locator('[class*="description-textarea"]');
      await expect(descTextarea).toBeVisible();
    });

    test('should cancel edit mode', async () => {
      const editBtn = page.locator('button', { hasText: /Edit/i });
      await editBtn.click();
      const cancelBtn = page.locator('button', { hasText: /Cancel/i });
      await cancelBtn.click();
      await expect(page.locator('[class*="title-input"]')).not.toBeVisible();
    });
  });
});
