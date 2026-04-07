import { test as base, expect } from '@playwright/test';

import { AuthPage } from './pages/auth.page';
import { DashboardPage } from './pages/dashboard.page';

type Fixtures = {
  authPage: AuthPage;
  dashboardPage: DashboardPage;
};

export const test = base.extend<Fixtures>({
  authPage: async ({ page }, use) => {
    await use(new AuthPage(page));
  },
  dashboardPage: async ({ page }, use) => {
    await use(new DashboardPage(page));
  },
});

export { expect };