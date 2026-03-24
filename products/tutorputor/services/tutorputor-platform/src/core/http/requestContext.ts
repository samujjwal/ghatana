import type { FastifyRequest, FastifyReply } from "fastify";

/** Shape of the JWT payload as decoded by @fastify/jwt into req.user */
interface JwtUser {
  sub?: string;
  userId?: string;
  tenantId?: string;
  role?: string;
  email?: string;
}

/**
 * Extract Tenant ID from JWT claims (preferred) or gateway-injected header.
 * The global JWT preHandler in setup.ts populates req.user before route handlers run.
 */
export function getTenantId(req: FastifyRequest): string {
  const user = (req as FastifyRequest & { user?: JwtUser }).user;
  if (user?.tenantId) return user.tenantId;

  // Fallback: API gateway header for trusted proxy deployments
  const tenantId = req.headers["x-tenant-id"];
  if (!tenantId) {
    throw new Error(
      "Missing tenant context: no JWT tenantId claim or x-tenant-id header",
    );
  }
  return (Array.isArray(tenantId) ? tenantId[0] : tenantId) as string;
}

/**
 * Extract User ID from JWT subject claim (preferred) or gateway-injected header.
 */
export function getUserId(req: FastifyRequest): string {
  const user = (req as FastifyRequest & { user?: JwtUser }).user;
  if (user?.sub) return user.sub;
  if (user?.userId) return user.userId;

  // Fallback: API gateway header for trusted proxy deployments
  const userId = req.headers["x-user-id"];
  if (!userId) {
    throw new Error(
      "Missing user context: no JWT sub claim or x-user-id header",
    );
  }
  return (Array.isArray(userId) ? userId[0] : userId) as string;
}

/**
 * Enforce role authorization.
 * Checks JWT claim `role` first, then x-user-role header.
 * @param req Fastify Request
 * @param allowedRoles Array of allowed roles
 */
export function requireRole(req: FastifyRequest, allowedRoles: string[]) {
  const user = (req as FastifyRequest & { user?: JwtUser }).user;
  if (user?.role && allowedRoles.includes(user.role)) return;

  // Fallback: gateway-injected role header
  const userRole = req.headers["x-user-role"];
  const role = Array.isArray(userRole) ? userRole[0] : userRole;

  if (!role || !allowedRoles.includes(role)) {
    throw new Error(
      `Insufficient permissions. Required one of: ${allowedRoles.join(", ")}`,
    );
  }
}

/**
 * Helper to handle service errors consistently.
 */
export async function respondWithErrors<T>(
  reply: FastifyReply,
  fn: () => Promise<T>,
): Promise<void> {
  try {
    const result = await fn();
    reply.send(result);
  } catch (error) {
    const err = error as any;
    const statusCode = err.statusCode || 500;
    const message = err.message || "Internal Server Error";
    const code = err.code || "INTERNAL_ERROR";

    reply.code(statusCode).send({
      error: message,
      code: code,
    });
  }
}

/**
 * Declarative preHandler factory for role-based access control.
 *
 * Usage in route options:
 *   { preHandler: [roleGuard(['admin', 'superadmin'])] }
 *
 * @param allowedRoles Array of roles that may access the route
 */
export function roleGuard(
  allowedRoles: string[],
): (req: FastifyRequest, reply: FastifyReply) => Promise<void> {
  return async function guardHandler(
    req: FastifyRequest,
    reply: FastifyReply,
  ): Promise<void> {
    try {
      requireRole(req, allowedRoles);
    } catch {
      return reply.code(403).send({
        error: "Forbidden",
        message: `Required role: ${allowedRoles.join(" or ")}`,
      });
    }
  };
}
