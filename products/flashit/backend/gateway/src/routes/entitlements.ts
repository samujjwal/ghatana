/**
 * FlashIt route/content entitlement API.
 *
 * @doc.type route
 * @doc.purpose Returns ProductRouteEntitlement-shaped route/action/card metadata
 *              based on server-derived identity. The JWT is verified and the
 *              principal's role and tier are resolved from the database —
 *              client-supplied headers are never trusted for security decisions.
 * @doc.layer product
 * @doc.pattern APIRoute
 */

import { FastifyInstance, FastifyRequest } from 'fastify';
import type { JwtPayload } from '../lib/auth.js';
import { prisma } from '../lib/prisma.js';

type FlashItRole = 'member' | 'premium' | 'admin';

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
  route('/moments', 'Moments', 'member', ['view-moments', 'delete-moment', 'update-moment'], ['moment-list']),
  route('/spheres', 'Spheres', 'member', ['manage-spheres', 'create-sphere', 'delete-sphere'], ['sphere-overview']),
  route('/search', 'Search', 'member', ['search-memories'], ['search-results']),
  route('/analytics', 'Analytics', 'premium', ['view-analytics', 'export-analytics'], ['meaning-metrics']),
  route('/reflection', 'Reflection', 'member', ['review-reflection'], ['reflection-prompts']),
  route('/collaboration', 'Collaboration', 'premium', ['share-memory', 'invite-collaborator'], ['shared-reviews']),
  route('/settings', 'Settings', 'member', ['manage-settings', 'update-privacy', 'delete-account'], ['privacy-controls']),
  route('/billing', 'Billing', 'member', ['view-billing', 'manage-subscription'], ['billing-summary']),
  route('/api-keys', 'API Keys', 'member', ['manage-api-keys', 'create-api-key', 'revoke-api-key'], ['api-key-list']),
  route('/notifications', 'Notifications', 'member', ['view-notifications', 'dismiss-notification'], ['notification-list']),
  route('/templates', 'Templates', 'member', ['view-templates', 'create-template'], ['template-list']),
  route('/knowledge-graph', 'Knowledge Graph', 'premium', ['view-knowledge-graph'], ['knowledge-graph-view']),
  route('/memory-expansion', 'Memory Expansion', 'premium', ['view-memory-expansion'], ['memory-expansion-view']),
  route('/admin', 'Admin', 'admin', ['manage-users', 'view-system-stats', 'manage-system'], ['admin-dashboard']),
] as const;

export default async function entitlementRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.get('/route-entitlements', {
    onRequest: [fastify.authenticate],
  }, async (request: FastifyRequest) => {
    const jwtUser = request.user as JwtPayload;

    // Resolve role and tier from server — never trust client headers
    const userRecord = await prisma.user.findUnique({
      where: { id: jwtUser.userId },
      select: {
        role: true,
        subscriptionTier: true,
        deletedAt: true,
      },
    });

    if (!userRecord || userRecord.deletedAt) {
      return request.server.httpErrors.gone('Account not found or has been deleted');
    }

    const role = mapDbRoleToFlashItRole(userRecord.role, userRecord.subscriptionTier);
    const allowedRoutes = routes.filter((candidate) => roleOrder[role] >= roleOrder[candidate.minimumRole]);

    return {
      product: 'flashit',
      principalId: jwtUser.userId,
      role,
      tier: userRecord.subscriptionTier,
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
    tiers: ['free', 'core', 'premium'],
    actions,
    cards,
  };
}

function mapDbRoleToFlashItRole(dbRole: string, subscriptionTier: string): FlashItRole {
  if (dbRole === 'ADMIN') return 'admin';
  if (subscriptionTier === 'premium' || subscriptionTier === 'pro') return 'premium';
  return 'member';
}

function labelFromId(value: string): string {
  return value
    .split('-')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}
