import { test, expect } from './fixtures';

test.describe('Browser Auth Journey', () => {
  test('signs in through the real login route and stores the canonical auth-session', async ({ authPage, dashboardPage }) => {
    await authPage.login({
      expectedEmail: 'sam@yappc.local',
      expectedPassword: 'secret-password',
      user: {
        id: 'user-1',
        email: 'sam@yappc.local',
        name: 'Sam User',
        role: 'ADMIN',
      },
    });

    const storedSession = await authPage.getStoredSession();

    await dashboardPage.expectLoaded();
    expect(storedSession).not.toBeNull();
    expect(storedSession?.user.email).toBe('sam@yappc.local');
    expect(storedSession?.token).toBe('access-token-1');
    expect(storedSession?.refreshToken).toBe('refresh-token-1');
  });

  test('shows an inline error for rejected credentials without leaving the login page', async ({ authPage, page }) => {
    await authPage.mockFailedLogin();
    await authPage.goto();

    await authPage.emailInput.fill('sam@yappc.local');
    await authPage.passwordInput.fill('wrong-password');
    await authPage.submitButton.click();

    await authPage.expectError('Invalid email or password');
    await expect(page).toHaveURL(/\/login$/);
  });
});