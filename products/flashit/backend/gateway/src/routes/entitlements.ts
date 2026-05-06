/**
 * FlashIt route/content entitlement API.
 *
 * @doc.type route
 * @doc.purpose Returns ProductRouteEntitlement-shaped route/action/card metadata
 * @doc.layer product
 * @doc.pattern APIRoute
 */

import { FastifyInstance, FastifyRequest } from 'fastify';

type FlashItRole = 'member' | 'premium' | 'admin';

interface EntitlementHeaders {
  readonly 'x-principal-id'?: string;
  readonly 'x-tenant-id'?: string;
  readonly 'x-role'?: string;
  readonly 'x-persona'?: string;
  readonly 'x-tier'?: string;
}

interface EntitledRoute {
  readonly path: string;
  readonly label: string;
  readonly minimumRole: FlashItRole;
  readonly personas: readonly string[];
  readonly tiers: readonly string[];
  readonly actions: readonly string[];
  readonly cards: readonly string[];
}

const roleOrder: Readonly<Record<FlashItRole, number>> = {
  member: 0,
  premium: 1,
  admin: 2,
};

const routes: readonly EntitledRoute[] = [
  route('/', 'Dashboard', 'member', ['view-dashboard'], ['weekly-activity']),
  route('/capture', 'Capture', 'member', ['capture-moment'], ['capture-prompt']),
  route('/moments', 'Moments', 'member', ['view-moments'], ['moment-list']),
  route('/spheres', 'Spheres', 'member', ['manage-spheres'], ['sphere-overview']),
  route('/search', 'Search', 'member', ['search-memories'], ['search-results']),
  route('/analytics', 'Analytics', 'premium', ['view-analytics'], ['meaning-metrics']),
  route('/reflection', 'Reflection', 'member', ['review-reflection'], ['reflection-prompts']),
  route('/collaboration', 'Collaboration', 'premium', ['share-memory'], ['shared-reviews']),
  route('/settings', 'Settings', 'member', ['manage-settings'], ['privacy-controls']),
] as const;

export default async function entitlementRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.get('/route-entitlements', async (request: FastifyRequest<{ Headers: EntitlementHeaders }>) => {
    const role = resolveRole(request.headers['x-role']);
    const allowedRoutes = routes.filter((candidate) => roleOrder[role] >= roleOrder[candidate.minimumRole]);

    return {
      product: 'flashit',
      principalId: request.headers['x-principal-id'] ?? 'anonymous',
      tenantId: request.headers['x-tenant-id'] ?? 'default',
      role,
      persona: request.headers['x-persona'] ?? 'reflector',
      tier: request.headers['x-tier'] ?? (role === 'member' ? 'core' : 'premium'),
      routes: allowedRoutes,
      actions: allowedRoutes.flatMap((candidate) =>
        candidate.actions.map((actionId) => ({
          id: actionId,
          label: labelFromId(actionId),
          routePath: candidate.path,
        })),
      ),
      cards: allowedRoutes.flatMap((candidate) =>
        candidate.cards.map((cardId) => ({
          id: cardId,
          title: labelFromId(cardId),
          routePath: candidate.path,
          surface: 'dashboard',
        })),
      ),
    };
  });
}

function route(
  path: string,
  label: string,
  minimumRole: FlashItRole,
  actions: readonly string[],
  cards: readonly string[],
): EntitledRoute {
  return {
    path,
    label,
    minimumRole,
    personas: ['reflector', 'creator', 'caregiver'],
    tiers: ['core', 'premium'],
    actions,
    cards,
  };
}

function resolveRole(value: string | undefined): FlashItRole {
  return value === 'premium' || value === 'admin' ? value : 'member';
}

function labelFromId(value: string): string {
  return value
    .split('-')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}
