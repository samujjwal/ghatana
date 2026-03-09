/**
 * E2E Page Objects - Sprint Board Page
 *
 * @description Page object model for the sprint board (kanban).
 */

import { Page, Locator, expect } from '@playwright/test';

export class SprintBoardPage {
  readonly page: Page;
  
  // Header
  readonly sprintSelector: Locator;
  readonly filterPanel: Locator;
  readonly searchInput: Locator;
  readonly viewToggle: Locator;
  readonly addTaskButton: Locator;
  
  // Columns
  readonly todoColumn: Locator;
  readonly inProgressColumn: Locator;
  readonly reviewColumn: Locator;
  readonly doneColumn: Locator;
  
  // Tasks
  readonly taskCards: Locator;

  constructor(page: Page) {
    this.page = page;
    
    // Header
    this.sprintSelector = page.getByTestId('sprint-selector');
    this.filterPanel = page.getByTestId('filter-panel');
    this.searchInput = page.getByPlaceholder(/search tasks/i);
    this.viewToggle = page.getByTestId('view-toggle');
    this.addTaskButton = page.getByRole('button', { name: /add task|new task/i });
    
    // Columns
    this.todoColumn = page.getByTestId('column-todo');
    this.inProgressColumn = page.getByTestId('column-in-progress');
    this.reviewColumn = page.getByTestId('column-review');
    this.doneColumn = page.getByTestId('column-done');
    
    // Tasks
    this.taskCards = page.getByTestId('task-card');
  }

  async goto(projectId: string) {
    await this.page.goto(`/projects/${projectId}/development/sprints`);
  }

  async expectLoaded() {
    await expect(this.todoColumn).toBeVisible();
    await expect(this.inProgressColumn).toBeVisible();
  }

  async selectSprint(sprintName: string) {
    await this.sprintSelector.click();
    await this.page.getByRole('option', { name: sprintName }).click();
  }

  async searchTasks(query: string) {
    await this.searchInput.fill(query);
  }

  async addTask(data: { title: string; description?: string; priority?: string }) {
    await this.addTaskButton.click();
    
    const dialog = this.page.getByRole('dialog');
    await dialog.getByLabel(/title/i).fill(data.title);
    
    if (data.description) {
      await dialog.getByLabel(/description/i).fill(data.description);
    }
    
    if (data.priority) {
      await dialog.getByLabel(/priority/i).selectOption(data.priority);
    }
    
    await dialog.getByRole('button', { name: /create|save/i }).click();
  }

  async dragTask(taskTitle: string, fromColumn: string, toColumn: string) {
    const task = this.page.getByText(taskTitle);
    const targetColumn = this.page.getByTestId(`column-${toColumn}`);
    
    await task.dragTo(targetColumn);
  }

  async openTask(taskTitle: string) {
    await this.taskCards.filter({ hasText: taskTitle }).click();
  }

  async getTaskCountByColumn(): Promise<Record<string, number>> {
    return {
      todo: await this.todoColumn.locator('[data-testid="task-card"]').count(),
      inProgress: await this.inProgressColumn.locator('[data-testid="task-card"]').count(),
      review: await this.reviewColumn.locator('[data-testid="task-card"]').count(),
      done: await this.doneColumn.locator('[data-testid="task-card"]').count(),
    };
  }

  async filterByAssignee(assigneeName: string) {
    await this.filterPanel.getByRole('button', { name: /assignee/i }).click();
    await this.page.getByRole('option', { name: assigneeName }).click();
  }

  async filterByPriority(priority: string) {
    await this.filterPanel.getByRole('button', { name: /priority/i }).click();
    await this.page.getByRole('option', { name: priority }).click();
  }

  async clearFilters() {
    await this.filterPanel.getByRole('button', { name: /clear/i }).click();
  }
}

export class TaskDetailPage {
  readonly page: Page;
  
  // Header
  readonly taskTitle: Locator;
  readonly taskId: Locator;
  readonly statusBadge: Locator;
  readonly closeButton: Locator;
  
  // Details
  readonly description: Locator;
  readonly assignee: Locator;
  readonly priority: Locator;
  readonly storyPoints: Locator;
  readonly labels: Locator;
  readonly dueDate: Locator;
  
  // Actions
  readonly editButton: Locator;
  readonly commentInput: Locator;
  readonly postCommentButton: Locator;
  readonly comments: Locator;
  
  // Activity
  readonly activityTab: Locator;
  readonly activityItems: Locator;

  constructor(page: Page) {
    this.page = page;
    
    const panel = page.getByRole('dialog');
    
    // Header
    this.taskTitle = panel.getByTestId('task-title');
    this.taskId = panel.getByTestId('task-id');
    this.statusBadge = panel.getByTestId('status-badge');
    this.closeButton = panel.getByRole('button', { name: /close/i });
    
    // Details
    this.description = panel.getByTestId('task-description');
    this.assignee = panel.getByTestId('task-assignee');
    this.priority = panel.getByTestId('task-priority');
    this.storyPoints = panel.getByTestId('story-points');
    this.labels = panel.getByTestId('task-labels');
    this.dueDate = panel.getByTestId('due-date');
    
    // Actions
    this.editButton = panel.getByRole('button', { name: /edit/i });
    this.commentInput = panel.getByPlaceholder(/add a comment/i);
    this.postCommentButton = panel.getByRole('button', { name: /post|send/i });
    this.comments = panel.getByTestId('comment');
    
    // Activity
    this.activityTab = panel.getByRole('tab', { name: /activity/i });
    this.activityItems = panel.getByTestId('activity-item');
  }

  async expectOpen() {
    await expect(this.taskTitle).toBeVisible();
  }

  async close() {
    await this.closeButton.click();
  }

  async addComment(comment: string) {
    await this.commentInput.fill(comment);
    await this.postCommentButton.click();
    await expect(this.comments.last()).toContainText(comment);
  }

  async changeStatus(newStatus: string) {
    await this.statusBadge.click();
    await this.page.getByRole('option', { name: newStatus }).click();
  }

  async assignTo(userName: string) {
    await this.assignee.click();
    await this.page.getByRole('option', { name: userName }).click();
  }

  async viewActivity() {
    await this.activityTab.click();
  }
}
