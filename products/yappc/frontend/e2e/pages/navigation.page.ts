/**
 * Page Object Model - Navigation Page
 * Encapsulates navigation and common UI interactions
 */

import { Page, Locator, expect } from '@playwright/test';

export class NavigationPage {
  readonly page: Page;

  // Header navigation elements
  readonly logo: Locator;
  readonly userMenu: Locator;
  readonly notificationsButton: Locator;
  readonly searchButton: Locator;
  readonly themeToggle: Locator;

  // Main navigation elements
  readonly dashboardNav: Locator;
  readonly projectsNav: Locator;
  readonly buildsNav: Locator;
  readonly deploymentsNav: Locator;
  readonly teamsNav: Locator;
  readonly settingsNav: Locator;

  // Mobile navigation elements
  readonly mobileMenuButton: Locator;
  readonly mobileDrawer: Locator;
  readonly bottomNavigation: Locator;

  // Breadcrumb navigation
  readonly breadcrumbs: Locator;

  // Search and filters
  readonly globalSearch: Locator;
  readonly quickFilters: Locator;

  constructor(page: Page) {
    this.page = page;

    // Header elements
    this.logo = page.locator('[data-testid="app-logo"]');
    this.userMenu = page.locator('[data-testid="user-menu"]');
    this.notificationsButton = page.locator(
      '[data-testid="notifications-button"]'
    );
    this.searchButton = page.locator('[data-testid="search-button"]');
    this.themeToggle = page.locator('[data-testid="theme-toggle"]');

    // Main navigation
    this.dashboardNav = page.locator('[data-testid="nav-dashboard"]');
    this.projectsNav = page.locator('[data-testid="nav-projects"]');
    this.buildsNav = page.locator('[data-testid="nav-builds"]');
    this.deploymentsNav = page.locator('[data-testid="nav-deployments"]');
    this.teamsNav = page.locator('[data-testid="nav-teams"]');
    this.settingsNav = page.locator('[data-testid="nav-settings"]');

    // Mobile navigation
    this.mobileMenuButton = page.locator('[data-testid="mobile-menu-button"]');
    this.mobileDrawer = page.locator('[data-testid="mobile-drawer"]');
    this.bottomNavigation = page.locator('[data-testid="bottom-navigation"]');

    // Breadcrumbs and search
    this.breadcrumbs = page.locator('[data-testid="breadcrumbs"]');
    this.globalSearch = page.locator('[data-testid="global-search"]');
    this.quickFilters = page.locator('[data-testid="quick-filters"]');
  }

  // Navigation methods
  async clickDashboard() {
    await this.dashboardNav.click();
    await this.page.waitForURL('**/dashboard');
    await expect(this.page.locator('h1')).toContainText(/dashboard/i);
  }

  async clickProjectsNav() {
    await this.projectsNav.click();
    await this.page.waitForURL('**/projects');
    await expect(this.page.locator('h1, h2')).toContainText(/projects/i);
  }

  async clickBuildsNav() {
    await this.buildsNav.click();
    await this.page.waitForURL('**/builds');
    await expect(this.page.locator('h1, h2')).toContainText(/builds/i);
  }

  async clickDeploymentsNav() {
    await this.deploymentsNav.click();
    await this.page.waitForURL('**/deployments');
    await expect(this.page.locator('h1, h2')).toContainText(/deployments/i);
  }

  async clickTeamsNav() {
    await this.teamsNav.click();
    await this.page.waitForURL('**/teams');
    await expect(this.page.locator('h1, h2')).toContainText(/teams/i);
  }

  async clickSettingsNav() {
    await this.settingsNav.click();
    await this.page.waitForURL('**/settings');
    await expect(this.page.locator('h1, h2')).toContainText(/settings/i);
  }

  // Mobile navigation methods
  async openMobileMenu() {
    await this.mobileMenuButton.click();
    await expect(this.mobileDrawer).toBeVisible();
  }

  async closeMobileMenu() {
    await this.page.click('[data-testid="mobile-drawer-close"]');
    await expect(this.mobileDrawer).not.toBeVisible();
  }

  async clickBottomNavItem(
    item: 'projects' | 'dashboard' | 'notifications' | 'profile'
  ) {
    await this.page.click(`[data-testid="bottom-nav-${item}"]`);
  }

  // User menu methods
  async openUserMenu() {
    await this.userMenu.click();
    await expect(
      this.page.locator('[data-testid="user-menu-dropdown"]')
    ).toBeVisible();
  }

  async clickProfile() {
    await this.openUserMenu();
    await this.page.click('[data-testid="user-menu-profile"]');
    await this.page.waitForURL('**/profile');
  }

  async clickAccountSettings() {
    await this.openUserMenu();
    await this.page.click('[data-testid="user-menu-account"]');
    await this.page.waitForURL('**/account');
  }

  async logout() {
    await this.openUserMenu();
    await this.page.click('[data-testid="user-menu-logout"]');
    await this.page.waitForURL('**/auth/login');
    await expect(this.page.locator('[data-testid="login-form"]')).toBeVisible();
  }

  // Theme and preferences
  async toggleTheme() {
    const currentTheme = await this.page.evaluate(() => {
      return document.documentElement.getAttribute('data-theme');
    });

    await this.themeToggle.click();

    // Wait for theme to change
    await this.page.waitForFunction(
      (prevTheme) =>
        document.documentElement.getAttribute('data-theme') !== prevTheme,
      currentTheme
    );

    const newTheme = await this.page.evaluate(() => {
      return document.documentElement.getAttribute('data-theme');
    });

    return newTheme;
  }

  async setTheme(theme: 'light' | 'dark' | 'auto') {
    const currentTheme = await this.page.evaluate(() => {
      return document.documentElement.getAttribute('data-theme');
    });

    if (currentTheme !== theme) {
      await this.themeToggle.click();
      if (theme !== 'light') {
        // If not light, click again for dark or auto
        await this.themeToggle.click();
      }
    }
  }

  // Search and notifications
  async openGlobalSearch() {
    await this.searchButton.click();
    await expect(
      this.page.locator('[data-testid="search-dialog"]')
    ).toBeVisible();
  }

  async searchGlobally(query: string) {
    await this.openGlobalSearch();
    await this.page.fill('[data-testid="search-input"]', query);
    await this.page.keyboard.press('Enter');
    await expect(
      this.page.locator('[data-testid="search-results"]')
    ).toBeVisible();
  }

  async openNotifications() {
    await this.notificationsButton.click();
    await expect(
      this.page.locator('[data-testid="notifications-panel"]')
    ).toBeVisible();
  }

  async getNotificationCount(): Promise<number> {
    const badge = this.page.locator('[data-testid="notifications-badge"]');
    if (await badge.isVisible()) {
      const text = await badge.textContent();
      return parseInt(text || '0');
    }
    return 0;
  }

  async markNotificationAsRead(notificationId: string) {
    await this.openNotifications();
    await this.page.click(
      `[data-testid="notification-${notificationId}"] [data-testid="mark-read"]`
    );
  }

  async markAllNotificationsAsRead() {
    await this.openNotifications();
    await this.page.click('[data-testid="mark-all-read"]');
  }

  // Breadcrumb navigation
  async clickBreadcrumb(level: number) {
    await this.breadcrumbs.locator('a').nth(level).click();
  }

  async getBreadcrumbPath(): Promise<string[]> {
    const items = await this.breadcrumbs.locator('a, span').all();
    const path = [];

    for (const item of items) {
      const text = await item.textContent();
      if (text && text.trim()) {
        path.push(text.trim());
      }
    }

    return path;
  }

  // Quick actions and shortcuts
  async useKeyboardShortcut(shortcut: string) {
    await this.page.keyboard.press(shortcut);
  }

  async openCommandPalette() {
    await this.page.keyboard.press('Control+k');
    await expect(
      this.page.locator('[data-testid="command-palette"]')
    ).toBeVisible();
  }

  async executeCommand(command: string) {
    await this.openCommandPalette();
    await this.page.fill('[data-testid="command-input"]', command);
    await this.page.keyboard.press('Enter');
  }

  // Quick filters
  async applyQuickFilter(filter: string) {
    await this.page.click(`[data-testid="quick-filter-${filter}"]`);
    await this.page.waitForTimeout(500); // Wait for filter to apply
  }

  async clearAllFilters() {
    await this.page.click('[data-testid="clear-filters"]');
    await this.page.waitForTimeout(500);
  }

  // Utility methods
  async waitForNavigation() {
    await this.page.waitForLoadState('networkidle');
  }

  async assertCurrentPage(expectedPath: string) {
    await expect(this.page).toHaveURL(new RegExp(expectedPath));
  }

  async getPageTitle(): Promise<string> {
    return await this.page.title();
  }

  async isLoggedIn(): Promise<boolean> {
    return await this.userMenu.isVisible();
  }

  async getCurrentUser(): Promise<{ name: string; email: string } | null> {
    if (!(await this.isLoggedIn())) {
      return null;
    }

    await this.openUserMenu();
    const name = await this.page
      .locator('[data-testid="user-name"]')
      .textContent();
    const email = await this.page
      .locator('[data-testid="user-email"]')
      .textContent();

    // Close menu
    await this.page.keyboard.press('Escape');

    return {
      name: name?.trim() || '',
      email: email?.trim() || '',
    };
  }

  // Error and loading states
  async waitForLoadingToComplete() {
    await this.page.waitForSelector('[data-testid="loading"], .loading', {
      state: 'detached',
      timeout: 30000,
    });
  }

  async assertNoGlobalErrors() {
    const errorElements = this.page.locator(
      '[data-testid="global-error"], .global-error, [role="alert"][aria-live="assertive"]'
    );
    await expect(errorElements).toHaveCount(0);
  }

  async dismissGlobalError() {
    const dismissButton = this.page.locator(
      '[data-testid="dismiss-error"], .error-dismiss'
    );
    if (await dismissButton.isVisible()) {
      await dismissButton.click();
    }
  }

  // Accessibility helpers
  async checkFocusManagement() {
    const focusedElement = this.page.locator(':focus');
    await expect(focusedElement).toBeVisible();
    return focusedElement;
  }

  async navigateWithKeyboard(
    direction: 'next' | 'previous' | 'first' | 'last'
  ) {
    switch (direction) {
      case 'next':
        await this.page.keyboard.press('Tab');
        break;
      case 'previous':
        await this.page.keyboard.press('Shift+Tab');
        break;
      case 'first':
        await this.page.keyboard.press('Home');
        break;
      case 'last':
        await this.page.keyboard.press('End');
        break;
    }
  }

  async skipToMainContent() {
    const skipLink = this.page.locator('[data-testid="skip-to-content"]');
    if (await skipLink.isVisible()) {
      await skipLink.click();
    } else {
      await this.page.keyboard.press('Tab');
      await this.page.keyboard.press('Enter');
    }
  }
}
