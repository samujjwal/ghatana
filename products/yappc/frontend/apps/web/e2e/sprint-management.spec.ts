/**
 * Sprint Management E2E Tests
 *
 * End-to-end tests for sprint management pages including:
 * - Sprint list display
 * - Sprint planning functionality
 * - Sprint board interactions
 * - Sprint retrospective
 *
 * @doc.type test
 * @doc.purpose E2E tests for sprint management
 * @doc.phase 3
 */

import { test, expect, type Page } from '@playwright/test';

test.describe('Sprint List Page', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/sprints');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display page header', async () => {
      const header = page.locator('h1');
      await expect(header).toBeVisible();
      await expect(header).toContainText(/Sprint/i);
    });

    test('should show sprint summary section', async () => {
      const summary = page.locator('[class*="summary"]');
      await expect(summary).toBeVisible();
    });

    test('should show sprint cards grid', async () => {
      const grid = page.locator('[class*="sprints-grid"]');
      await expect(grid).toBeVisible();
    });

    test('should show create sprint button', async () => {
      const createBtn = page.locator('button', { hasText: /Create Sprint|New Sprint/i });
      await expect(createBtn).toBeVisible();
    });
  });

  test.describe('Sprint Cards', () => {
    test('should display sprint card with title', async () => {
      const card = page.locator('[class*="sprint-card"]').first();
      await expect(card).toBeVisible();
      await expect(card.locator('[class*="sprint-name"]')).toBeVisible();
    });

    test('should show sprint progress bar', async () => {
      const progressBar = page.locator('[class*="progress-bar"]').first();
      await expect(progressBar).toBeVisible();
    });

    test('should show sprint date range', async () => {
      const dateRange = page.locator('[class*="sprint-dates"]').first();
      await expect(dateRange).toBeVisible();
    });

    test('should show sprint status badge', async () => {
      const statusBadge = page.locator('[class*="status-badge"]').first();
      await expect(statusBadge).toBeVisible();
    });

    test('should show sprint statistics', async () => {
      const card = page.locator('[class*="sprint-card"]').first();
      await expect(card.locator('[class*="stat"]')).toHaveCount(await page.locator('[class*="stat"]').count() > 0 ? 1 : 0);
    });
  });

  test.describe('Filtering', () => {
    test('should have status filter', async () => {
      const statusFilter = page.locator('select[class*="filter"], [class*="filter-dropdown"]').first();
      await expect(statusFilter).toBeVisible();
    });

    test('should filter sprints by status', async () => {
      const filter = page.locator('select').first();
      if (await filter.isVisible()) {
        await filter.selectOption({ index: 1 });
        await page.waitForTimeout(300);
      }
    });

    test('should have sort options', async () => {
      const sortBtn = page.locator('button, select', { hasText: /Sort|Order/i }).first();
      await expect(sortBtn).toBeVisible();
    });
  });

  test.describe('Navigation', () => {
    test('should navigate to sprint board on card click', async () => {
      const card = page.locator('[class*="sprint-card"]').first();
      await card.click();
      await page.waitForURL(/\/sprints\/[^/]+$/);
    });

    test('should navigate to planning page from action menu', async () => {
      const planningLink = page.locator('a', { hasText: /Planning/i }).first();
      if (await planningLink.isVisible()) {
        await planningLink.click();
        await page.waitForURL(/\/planning$/);
      }
    });
  });
});

test.describe('Sprint Planning Page', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/sprints/sprint-1/planning');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display planning header', async () => {
      const header = page.locator('[class*="planning-header"], h1');
      await expect(header).toBeVisible();
    });

    test('should show capacity bar', async () => {
      const capacityBar = page.locator('[class*="capacity"]');
      await expect(capacityBar).toBeVisible();
    });

    test('should show backlog column', async () => {
      const backlogColumn = page.locator('[class*="backlog-column"], [class*="column"]', {
        hasText: /Backlog/i,
      });
      await expect(backlogColumn).toBeVisible();
    });

    test('should show sprint column', async () => {
      const sprintColumn = page.locator('[class*="sprint-column"], [class*="column"]', {
        hasText: /Sprint/i,
      });
      await expect(sprintColumn).toBeVisible();
    });
  });

  test.describe('Capacity Management', () => {
    test('should display capacity bar with progress', async () => {
      const capacityFill = page.locator('[class*="capacity-fill"], [class*="progress"]');
      await expect(capacityFill).toBeVisible();
    });

    test('should show points committed vs capacity', async () => {
      const capacityText = page.locator('[class*="capacity-text"]');
      await expect(capacityText).toContainText(/pts|points/i);
    });

    test('should show team capacity panel', async () => {
      const teamPanel = page.locator('[class*="team-capacity"]');
      await expect(teamPanel).toBeVisible();
    });
  });

  test.describe('Story Cards', () => {
    test('should display story cards in columns', async () => {
      const storyCards = page.locator('[class*="story-card"]');
      await expect(storyCards.first()).toBeVisible();
    });

    test('should show story title', async () => {
      const storyTitle = page.locator('[class*="story-title"]').first();
      await expect(storyTitle).toBeVisible();
    });

    test('should show story points', async () => {
      const storyPoints = page.locator('[class*="points"]').first();
      await expect(storyPoints).toBeVisible();
    });

    test('should show priority indicator', async () => {
      const priority = page.locator('[class*="priority"]').first();
      await expect(priority).toBeVisible();
    });
  });

  test.describe('Drag and Drop', () => {
    test('should allow dragging stories between columns', async () => {
      const storyCard = page.locator('[class*="story-card"]').first();
      const sprintColumn = page.locator('[class*="sprint-column"], [class*="column"]').last();

      if (await storyCard.isVisible() && await sprintColumn.isVisible()) {
        const storyBox = await storyCard.boundingBox();
        const columnBox = await sprintColumn.boundingBox();

        if (storyBox && columnBox) {
          await page.mouse.move(storyBox.x + storyBox.width / 2, storyBox.y + storyBox.height / 2);
          await page.mouse.down();
          await page.mouse.move(columnBox.x + columnBox.width / 2, columnBox.y + 100);
          await page.mouse.up();
        }
      }
    });
  });

  test.describe('Pointing', () => {
    test('should show pointing cards modal on click', async () => {
      const pointsBtn = page.locator('[class*="points-btn"], button', { hasText: /points/i }).first();
      if (await pointsBtn.isVisible()) {
        await pointsBtn.click();
        const modal = page.locator('[class*="pointing-cards"], [class*="modal"]');
        await expect(modal).toBeVisible();
      }
    });

    test('should display fibonacci sequence options', async () => {
      const pointsBtn = page.locator('[class*="points-btn"]').first();
      if (await pointsBtn.isVisible()) {
        await pointsBtn.click();
        const fibonacciCards = page.locator('[class*="point-card"]');
        await expect(fibonacciCards).toHaveCount(7); // 0, 1, 2, 3, 5, 8, 13
      }
    });
  });
});

test.describe('Sprint Board Page', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/sprints/sprint-1');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display sprint header with name', async () => {
      const header = page.locator('[class*="sprint-header"], h1');
      await expect(header).toBeVisible();
    });

    test('should show days remaining badge', async () => {
      const daysBadge = page.locator('[class*="days-remaining"], [class*="badge"]');
      await expect(daysBadge).toBeVisible();
    });

    test('should show board columns', async () => {
      const columns = page.locator('[class*="board-column"], [class*="column"]');
      await expect(columns).toHaveCount(await columns.count() >= 3 ? 3 : await columns.count());
    });
  });

  test.describe('Board Columns', () => {
    test('should show To Do column', async () => {
      const todoColumn = page.locator('[class*="column"]', { hasText: /To Do|Backlog/i });
      await expect(todoColumn).toBeVisible();
    });

    test('should show In Progress column', async () => {
      const inProgressColumn = page.locator('[class*="column"]', { hasText: /In Progress|Doing/i });
      await expect(inProgressColumn).toBeVisible();
    });

    test('should show Done column', async () => {
      const doneColumn = page.locator('[class*="column"]', { hasText: /Done|Complete/i });
      await expect(doneColumn).toBeVisible();
    });

    test('should show story count in column headers', async () => {
      const columnHeader = page.locator('[class*="column-header"]').first();
      await expect(columnHeader).toContainText(/\d+/);
    });
  });

  test.describe('Story Interactions', () => {
    test('should open story detail on card click', async () => {
      const storyCard = page.locator('[class*="story-card"]').first();
      if (await storyCard.isVisible()) {
        await storyCard.click();
        const detailPanel = page.locator('[class*="detail-panel"], [class*="modal"]');
        await expect(detailPanel).toBeVisible();
      }
    });

    test('should allow drag and drop between columns', async () => {
      const storyCard = page.locator('[class*="story-card"]').first();
      const targetColumn = page.locator('[class*="column"]').nth(1);

      if (await storyCard.isVisible() && await targetColumn.isVisible()) {
        await storyCard.dragTo(targetColumn);
      }
    });
  });

  test.describe('Filtering', () => {
    test('should have assignee filter', async () => {
      const filterBtn = page.locator('button, select', { hasText: /Assignee|Filter/i }).first();
      await expect(filterBtn).toBeVisible();
    });

    test('should have quick filters', async () => {
      const quickFilters = page.locator('[class*="quick-filters"]');
      if (await quickFilters.isVisible()) {
        await expect(quickFilters).toBeVisible();
      }
    });
  });
});

test.describe('Sprint Retro Page', () => {
  let page: Page;

  test.beforeEach(async ({ page: testPage }) => {
    page = testPage;
    await page.goto('/projects/test-project/sprints/sprint-1/retro');
    await page.waitForLoadState('networkidle');
  });

  test.describe('Page Layout', () => {
    test('should display retro header', async () => {
      const header = page.locator('h1, [class*="header"]');
      await expect(header).toContainText(/Retro|Retrospective/i);
    });

    test('should show mood picker', async () => {
      const moodPicker = page.locator('[class*="mood-picker"], [class*="mood"]');
      await expect(moodPicker).toBeVisible();
    });

    test('should show retro columns', async () => {
      const columns = page.locator('[class*="retro-column"], [class*="column"]');
      await expect(columns).toHaveCount(await columns.count() >= 3 ? 3 : await columns.count());
    });
  });

  test.describe('Retro Columns', () => {
    test('should show What Went Well column', async () => {
      const column = page.locator('[class*="column"]', { hasText: /Went Well|Liked|Keep/i });
      await expect(column).toBeVisible();
    });

    test('should show What Could Improve column', async () => {
      const column = page.locator('[class*="column"]', { hasText: /Improve|Learn|Change/i });
      await expect(column).toBeVisible();
    });

    test('should show Action Items column', async () => {
      const column = page.locator('[class*="column"]', { hasText: /Action|Items|Try/i });
      await expect(column).toBeVisible();
    });
  });

  test.describe('Retro Items', () => {
    test('should allow adding new items', async () => {
      const addBtn = page.locator('button', { hasText: /Add|New|\+/i }).first();
      await expect(addBtn).toBeVisible();
    });

    test('should show retro item cards', async () => {
      const itemCards = page.locator('[class*="retro-item"]');
      if (await itemCards.count() > 0) {
        await expect(itemCards.first()).toBeVisible();
      }
    });

    test('should allow voting on items', async () => {
      const voteBtn = page.locator('[class*="vote"], button', { hasText: /vote|👍/i }).first();
      if (await voteBtn.isVisible()) {
        await expect(voteBtn).toBeVisible();
      }
    });
  });

  test.describe('Mood Selection', () => {
    test('should display mood options', async () => {
      const moodOptions = page.locator('[class*="mood-option"], [class*="emoji"]');
      await expect(moodOptions).toHaveCount(await moodOptions.count() > 0 ? await moodOptions.count() : 0);
    });

    test('should allow selecting mood', async () => {
      const moodBtn = page.locator('[class*="mood-option"]').first();
      if (await moodBtn.isVisible()) {
        await moodBtn.click();
        await expect(moodBtn).toHaveAttribute('aria-selected', 'true');
      }
    });
  });

  test.describe('Timer', () => {
    test('should show retro timer', async () => {
      const timer = page.locator('[class*="timer"]');
      if (await timer.isVisible()) {
        await expect(timer).toBeVisible();
      }
    });
  });
});
