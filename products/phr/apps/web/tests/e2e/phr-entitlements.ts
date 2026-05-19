import type { Page } from '@playwright/test';
import { PHR_ROLE_ORDER, phrRouteContracts } from '../../src/phrRouteContracts';
import type { PhrRole } from '../../src/auth/PhrAccessContext';

function isPhrRole(value: string | null): value is PhrRole {
  return value === 'patient' || value === 'caregiver' || value === 'clinician' || value === 'admin';
}

export async function mockPhrEntitlements(page: Page, fallbackRole: PhrRole = 'patient'): Promise<void> {
  await page.route('**/route-entitlements**', async (route) => {
    const request = route.request();
    const requestedRole = request.headers()['x-role'] ?? new URL(request.url()).searchParams.get('role');
    const role = isPhrRole(requestedRole) ? requestedRole : fallbackRole;
    const routes = phrRouteContracts.filter((entry) => {
      const requiredRole = entry.minimumRole as PhrRole | undefined;
      return requiredRole === undefined || PHR_ROLE_ORDER[role] >= PHR_ROLE_ORDER[requiredRole];
    });

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        product: 'phr',
        principalId: 'e2e-principal',
        tenantId: 'e2e-tenant',
        role,
        persona: role,
        tier: role === 'clinician' || role === 'admin' ? 'clinical' : 'core',
        routes,
        actions: routes.flatMap((entry) =>
          (entry.actions ?? []).map((action) => ({
            id: action,
            label: action.replace(/-/g, ' '),
            routePath: entry.path,
          })),
        ),
        cards: routes.flatMap((entry) =>
          (entry.cards ?? []).map((card) => ({
            id: card,
            title: card.replace(/-/g, ' '),
            routePath: entry.path,
            surface: 'dashboard',
          })),
        ),
      }),
    });
  });
}
