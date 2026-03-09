/**
 * E2E Page Objects - Dashboard Page
 *
 * @description Page object model for the main dashboard.
 */

import { Page, Locator, expect } from '@playwright/test';

export class DashboardPage {
  readonly page: Page;
  
  // Header
  readonly header: Locator;
  readonly userMenu: Locator;
  readonly searchButton: Locator;
  readonly notificationsButton: Locator;
  readonly newProjectButton: Locator;
  
  // Sidebar
  readonly sidebar: Locator;
  readonly navItems: Locator;
  
  // Main content
  readonly welcomeMessage: Locator;
  readonly statsCards: Locator;
  readonly projectsList: Locator;
  readonly activityFeed: Locator;
  readonly quickActions: Locator;

  constructor(page: Page) {
    this.page = page;
    
    // Header
    this.header = page.getByRole('banner');
    this.userMenu = page.getByTestId('user-menu');
    this.searchButton = page.getByRole('button', { name: /search/i });
    this.notificationsButton = page.getByRole('button', { name: /notifications/i });
    this.newProjectButton = page.getByRole('button', { name: /new project/i });
    
    // Sidebar
    this.sidebar = page.getByRole('navigation');
    this.navItems = page.getByRole('navigation').getByRole('link');
    
    // Main content
    this.welcomeMessage = page.getByText(/good (morning|afternoon|evening)/i);
    this.statsCards = page.getByTestId('stats-card');
    this.projectsList = page.getByTestId('projects-list');
    this.activityFeed = page.getByTestId('activity-feed');
    this.quickActions = page.getByTestId('quick-actions');
  }

  async goto() {
    await this.page.goto('/dashboard');
  }

  async expectLoaded() {
    await expect(this.header).toBeVisible();
    await expect(this.sidebar).toBeVisible();
  }

  async openUserMenu() {
    await this.userMenu.click();
  }

  async logout() {
    await this.openUserMenu();
    await this.page.getByRole('menuitem', { name: /sign out|logout/i }).click();
  }

  async openSearch() {
    await this.searchButton.click();
  }

  async createNewProject() {
    await this.newProjectButton.click();
    await expect(this.page).toHaveURL(/projects\/new|start-project/);
  }

  async navigateTo(section: string) {
    await this.sidebar.getByRole('link', { name: new RegExp(section, 'i') }).click();
  }

  async getProjectCards() {
    return this.projectsList.getByTestId('project-card').all();
  }

  async openProject(projectName: string) {
    await this.projectsList.getByText(projectName).click();
  }

  async getStats() {
    const cards = await this.statsCards.all();
    const stats: Record<string, string> = {};
    
    for (const card of cards) {
      const label = await card.getByTestId('stat-label').textContent();
      const value = await card.getByTestId('stat-value').textContent();
      if (label && value) {
        stats[label] = value;
      }
    }
    
    return stats;
  }
}
