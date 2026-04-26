import { test, expect } from '../fixtures';

test.describe('Auth Flow Hardening', () => {
  test('preserves redirect targets after session timeout re-authentication', async ({ authPage, page }) => {
    await authPage.mockSuccessfulLogin({
      expectedEmail: 'sam@yappc.local',
      expectedPassword: 'secret-password',
      user: {
        id: 'user-1',
        email: 'sam@yappc.local',
        name: 'Sam User',
        role: 'EDITOR',
      },
    });

    await authPage.goto({ sessionExpired: 'true', redirectTo: '/projects' });
    await authPage.expectSessionExpiredBanner();

    await authPage.emailInput.fill('sam@yappc.local');
    await authPage.passwordInput.fill('secret-password');
    await authPage.submitButton.click();

    await expect(page).toHaveURL(/\/projects$/);
  });

  test('hydrates an existing stored session without any demo shortcut', async ({ authPage, dashboardPage }) => {
    await authPage.seedAuthenticatedSession({
      user: {
        id: 'user-2',
        email: 'owner@yappc.local',
        name: 'Owner User',
        role: 'OWNER',
      },
    });

    await dashboardPage.goto();
    await dashboardPage.expectLoaded();

    const storedSession = await authPage.getStoredSession();
    expect(storedSession?.user.email).toBe('owner@yappc.local');
    expect(storedSession?.permissions).toContain('*');
  });
});