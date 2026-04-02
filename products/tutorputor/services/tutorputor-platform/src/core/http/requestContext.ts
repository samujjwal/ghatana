import type { FastifyRequest, FastifyReply } from "fastify";

type HttpError = Error & { statusCode: number; code: string };

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
    throw createHttpError(
      401,
      "UNAUTHORIZED",
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
    throw createHttpError(
      401,
      "UNAUTHORIZED",
      "Missing user context: no JWT sub claim or x-user-id header",
    );
  }
  return (Array.isArray(userId) ? userId[0] : userId) as string;
}

/**
 * Extract the caller's role from JWT claims or a trusted proxy header.
 */
export function getUserRole(req: FastifyRequest): string | null {
  const user = (req as FastifyRequest & { user?: JwtUser }).user;
  if (user?.role) return user.role;

  const userRole = req.headers["x-user-role"];
  if (!userRole) {
    return null;
  }

  return (Array.isArray(userRole) ? userRole[0] : userRole) as string;
}

/**
 * Enforce role authorization.
 * Checks JWT claim `role` first, then x-user-role header.
 * @param req Fastify Request
 * @param allowedRoles Array of allowed roles
 */
export function requireRole(req: FastifyRequest, allowedRoles: string[]) {
  const role = getUserRole(req);

  if (!role || !allowedRoles.includes(role)) {
    throw createHttpError(
      403,
      "FORBIDDEN",
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
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const err = error as Record<string, unknown>;
    const statusCode =
      typeof err.statusCode === "number" ? err.statusCode : 500;
    const message =
      typeof err.message === "string"
        ? err.message
        : "Internal Server Error";
    const code = typeof err.code === "string" ? err.code : "INTERNAL_ERROR";

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

export function requireOwnership(
  ownerId: string,
  currentUserId: string,
  message: string,
): void {
  if (ownerId !== currentUserId) {
    throw createHttpError(403, "FORBIDDEN", message);
  }
}

export function requireSelfOrRole(
  req: FastifyRequest,
  targetUserId: string,
  allowedRoles: string[],
  message = "You are not allowed to access this user's resource",
): void {
  const currentUserId = getUserId(req);

  if (currentUserId === targetUserId) {
    return;
  }

  const role = getUserRole(req);
  if (role && allowedRoles.includes(role)) {
    return;
  }

  throw createHttpError(403, "FORBIDDEN", message);
}

export function requireTenantAccess(
  resourceTenantId: string,
  currentTenantId: string,
  message = "Resource is not accessible for the current tenant",
): void {
  if (resourceTenantId !== currentTenantId) {
    throw createHttpError(403, "FORBIDDEN", message);
  }
}

export function createHttpError(
  statusCode: number,
  code: string,
  message: string,
): HttpError {
  const error = new Error(message) as HttpError;
  error.statusCode = statusCode;
  error.code = code;
  return error;
}
