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

import { FastifyInstance, FastifyRequest } from "fastify";
import type { JwtPayload } from "../lib/auth.js";
import { prisma } from "../lib/prisma.js";
import {
  flashItRouteContracts,
  type FlashItRole,
} from "@flashit/shared/contracts/routes";

const roleOrder: Readonly<Record<FlashItRole, number>> = {
  member: 0,
  premium: 1,
  admin: 2,
};

export default async function entitlementRoutes(
  fastify: FastifyInstance,
): Promise<void> {
  fastify.get(
    "/route-entitlements",
    {
      onRequest: [fastify.authenticate],
    },
    async (request: FastifyRequest) => {
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
        return request.server.httpErrors.gone(
          "Account not found or has been deleted",
        );
      }

      const role = mapDbRoleToFlashItRole(
        userRecord.role,
        userRecord.subscriptionTier,
      );
      const allowedRoutes = flashItRouteContracts.filter(
        (candidate) => roleOrder[role] >= roleOrder[candidate.minimumRole],
      );
      const routeEntitlements = allowedRoutes.map(
        ({ minimumRole: _minimumRole, ...candidate }) => candidate,
      );

      return {
        product: "flashit",
        principalId: jwtUser.userId,
        tenantId: jwtUser.userId,
        correlationId: headerValue(request.headers["x-correlation-id"]),
        role,
        tier: userRecord.subscriptionTier,
        routes: routeEntitlements,
        actions: routeEntitlements.flatMap((candidate) =>
          candidate.actions.map((actionId) => ({
            id: actionId,
            label: labelFromId(actionId),
            routePath: candidate.path,
          })),
        ),
        cards: routeEntitlements.flatMap((candidate) =>
          candidate.cards.map((cardId) => ({
            id: cardId,
            title: labelFromId(cardId),
            routePath: candidate.path,
            surface: "dashboard",
          })),
        ),
      };
    },
  );
}

function mapDbRoleToFlashItRole(
  dbRole: string,
  subscriptionTier: string,
): FlashItRole {
  if (dbRole === "ADMIN") return "admin";
  if (subscriptionTier === "premium" || subscriptionTier === "pro")
    return "premium";
  return "member";
}

function labelFromId(value: string): string {
  return value
    .split("-")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function headerValue(value: string | string[] | undefined): string | undefined {
  if (Array.isArray(value)) {
    return value[0];
  }
  return value;
}
