/**
 * E2E authentication helpers aligned to the sessionStorage auth model.
 *
 * All AEP auth state is stored in sessionStorage (not localStorage).
 * These helpers seed/clear auth tokens before page navigation so tests
 * can authenticate without going through the full SSO flow.
 */
import type { Page } from '@playwright/test';

export interface AuthSeed {
  token?: string;
  session?: string;
  tenantId?: string;
}

const DEFAULT_SEED: Required<AuthSeed> = {
  token: 'playwright-jwt-token',
  session: 'playwright-session-token',
  tenantId: 'playwright-tenant',
};

/**
 * Seed sessionStorage with auth tokens before page navigation.
 * Call this before `page.goto()` on protected routes.
 */
export async function seedAuthenticatedSession(
  page: Page,
  seed: AuthSeed = {},
): Promise<void> {
  const { token, session, tenantId } = { ...DEFAULT_SEED, ...seed };
  await page.addInitScript(
    ({ token, session, tenantId }: { token: string; session: string; tenantId: string }) => {
      window.sessionStorage.setItem('aep-token', token);
      window.sessionStorage.setItem('aep-session', session);
      window.sessionStorage.setItem('aep:active-tenant', tenantId);
    },
    { token, session, tenantId },
  );
}

/**
 * Clear all AEP auth state from sessionStorage.
 * Use this to ensure a page starts unauthenticated.
 */
export async function clearAuthenticatedSession(page: Page): Promise<void> {
  await page.addInitScript(() => {
    window.sessionStorage.removeItem('aep-token');
    window.sessionStorage.removeItem('aep-session');
    window.sessionStorage.removeItem('aep:active-tenant');
  });
}

/**
 * Assert that auth tokens are present in sessionStorage after sign-in.
 */
export async function assertSessionStorageAuth(
  page: Page,
  expected: AuthSeed = {},
): Promise<void> {
  const { token, session, tenantId } = { ...DEFAULT_SEED, ...expected };

  const storedToken = await page.evaluate(() => window.sessionStorage.getItem('aep-token'));
  const storedSession = await page.evaluate(() => window.sessionStorage.getItem('aep-session'));
  const storedTenant = await page.evaluate(() => window.sessionStorage.getItem('aep:active-tenant'));

  if (token !== undefined) {
    if (storedToken !== token) {
      throw new Error(`Expected aep-token="${token}", got "${storedToken}"`);
    }
  }
  if (session !== undefined) {
    if (storedSession !== session) {
      throw new Error(`Expected aep-session="${session}", got "${storedSession}"`);
    }
  }
  if (tenantId !== undefined) {
    if (storedTenant !== tenantId) {
      throw new Error(`Expected aep:active-tenant="${tenantId}", got "${storedTenant}"`);
    }
  }
}

/**
 * Suppress Vite error overlays that can interfere with axe and visual assertions.
 */
export async function suppressViteErrorOverlay(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const removeOverlay = () => {
      document.querySelectorAll('vite-error-overlay').forEach((overlay) => overlay.remove());
    };
    removeOverlay();
    new MutationObserver(() => removeOverlay()).observe(document.documentElement, {
      childList: true,
      subtree: true,
    });
  });
}
