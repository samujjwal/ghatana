import { expect, Page } from '@playwright/test';

type Role = 'VIEWER' | 'EDITOR' | 'ADMIN' | 'OWNER';

export type MockAuthUser = {
  id: string;
  email: string;
  name: string;
  role: Role;
};

export type MockAuthOptions = {
  user?: MockAuthUser;
  accessToken?: string;
  refreshToken?: string;
  expiresIn?: number;
  expectedEmail?: string;
  expectedPassword?: string;
};

export type StoredSession = {
  user: {
    id: string;
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    role: 'admin' | 'user' | 'viewer';
    permissions: string[];
    createdAt: string;
    updatedAt: string;
  };
  token: string;
  refreshToken: string;
  expiresAt: string;
  permissions: string[];
};

const defaultUser: MockAuthUser = {
  id: 'user-1',
  email: 'test@example.com',
  name: 'Test User',
  role: 'ADMIN',
};

async function seedAuthenticatedClientState(page: Page): Promise<void> {
  await page.addInitScript(() => {
    window.localStorage.setItem('onboarding_complete', 'true');
  });
}

function buildStoredSession(options: MockAuthOptions = {}): StoredSession {
  const user = options.user ?? defaultUser;
  const [firstName, ...lastNameParts] = user.name.split(/\s+/).filter(Boolean);
  const role = user.role === 'VIEWER' ? 'viewer' : user.role === 'EDITOR' ? 'user' : 'admin';
  const permissions = role === 'admin'
    ? ['*']
    : role === 'user'
      ? ['workspace:read', 'workspace:write', 'project:read', 'project:write']
      : ['workspace:read', 'project:read'];

  return {
    user: {
      id: user.id,
      username: user.email,
      email: user.email,
      firstName: firstName ?? user.email,
      lastName: lastNameParts.join(' '),
      role,
      permissions,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    token: options.accessToken ?? 'access-token-1',
    refreshToken: options.refreshToken ?? 'refresh-token-1',
    expiresAt: new Date(Date.now() + (options.expiresIn ?? 900) * 1000).toISOString(),
    permissions,
  };
}

export async function mockSuccessfulAuthApis(page: Page, options: MockAuthOptions = {}): Promise<void> {
  const session = buildStoredSession(options);
  const apiUser = options.user ?? defaultUser;

  await page.route('**/api/auth/login', async (route) => {
    const body = route.request().postDataJSON() as { email?: string; password?: string } | null;

    if (options.expectedEmail) {
      expect(body?.email).toBe(options.expectedEmail);
    }
    if (options.expectedPassword) {
      expect(body?.password).toBe(options.expectedPassword);
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        user: apiUser,
        tokens: {
          accessToken: session.token,
          refreshToken: session.refreshToken,
          expiresIn: options.expiresIn ?? 900,
        },
      }),
    });
  });

  await page.route('**/api/auth/me', async (route) => {
    const authorization = route.request().headers().authorization;
    if (authorization !== `Bearer ${session.token}`) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Unauthorized' }),
      });
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: apiUser.id,
        firstName: session.user.firstName,
        lastName: session.user.lastName,
        email: apiUser.email,
        role: apiUser.role,
        tenantId: 'tenant-1',
        workspaceIds: ['ws-1'],
      }),
    });
  });
}

export async function mockFailedLogin(page: Page, message = 'Invalid email or password'): Promise<void> {
  await page.route('**/api/auth/login', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ error: message }),
    });
  });
}

export async function seedStoredSession(page: Page, options: MockAuthOptions = {}): Promise<StoredSession> {
  const session = buildStoredSession(options);
  await seedAuthenticatedClientState(page);
  await page.addInitScript((serializedSession) => {
    window.localStorage.setItem('auth-session', serializedSession);
  }, JSON.stringify(session));
  return session;
}

export async function readStoredSession(page: Page): Promise<StoredSession | null> {
  return page.evaluate(() => {
    const raw = window.localStorage.getItem('auth-session');
    return raw ? JSON.parse(raw) : null;
  });
}

export async function loginThroughUi(page: Page, options: MockAuthOptions = {}): Promise<void> {
  await mockSuccessfulAuthApis(page, options);
  await seedAuthenticatedClientState(page);
  await page.goto('/login');
  await page.getByTestId('email-input').fill(options.expectedEmail ?? defaultUser.email);
  await page.getByTestId('password-input').fill(options.expectedPassword ?? 'password');
  await page.getByTestId('login-submit').click();
  await expect(page).toHaveURL(/\/workspaces$/);
  await expect(page.getByTestId('workspaces-page')).toBeVisible();
}