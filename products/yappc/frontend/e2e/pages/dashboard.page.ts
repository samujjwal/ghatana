import { Locator, Page, expect } from '@playwright/test';

export class DashboardPage {
  readonly page: Page;
  readonly heading: Locator;
  readonly workspacesPage: Locator;
  readonly workspaceCards: Locator;

  constructor(page: Page) {
    this.page = page;
    this.heading = page.getByRole('heading', { name: 'Workspaces' });
    this.workspacesPage = page.getByTestId('workspaces-page');
    this.workspaceCards = page.getByTestId('workspace-card');
  }

  async goto(): Promise<void> {
    await this.page.goto('/workspaces');
  }

  async expectLoaded(): Promise<void> {
    await expect(this.page).toHaveURL(/\/workspaces$/);
    await expect(this.workspacesPage).toBeVisible();
    await expect(this.heading).toBeVisible();
  }
}